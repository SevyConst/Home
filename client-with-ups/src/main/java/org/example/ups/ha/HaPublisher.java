package org.example.ups.ha;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.ups.config.UpsClientConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HaPublisher implements Runnable {

    private final URI webhookUri;

    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final int initBackoffMilliseconds;

    @Getter
    private final BlockingQueue<String> payloadBlockingQueueOneElement = new ArrayBlockingQueue<>(1);

    private int backoffMilliseconds;
    private Optional<String> previousPayloadOptional = Optional.empty();

    /**
     * How many deliveries have failed since the last one Home Assistant accepted
     */
    private long failedDeliveriesInARow;

    public HaPublisher(
            UpsClientConfig upsClientConfig
    ) {
        this.webhookUri = upsClientConfig.haWebhookUri();
        this.requestTimeout = upsClientConfig.haRequestTimeout();
        this.initBackoffMilliseconds = upsClientConfig.haInitBackoffMilliseconds();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(upsClientConfig.haConnectTimeout())
                .build();

        backoffMilliseconds = initBackoffMilliseconds;
    }

    @Override
    public void run() {
        while (true) {
            try {
                publishOnce();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void publishOnce() throws InterruptedException {
        String payload = payloadBlockingQueueOneElement.poll(backoffMilliseconds, TimeUnit.MILLISECONDS);
        if (null == payload && previousPayloadOptional.isPresent()) {
            if (post(previousPayloadOptional.get())) {
                previousPayloadOptional = Optional.empty();
                backoffMilliseconds = initBackoffMilliseconds;
            } else {
                backoffMilliseconds = backoffMilliseconds * 2;
            }
        }

        if (null != payload) {
            if (post(payload)) {
                previousPayloadOptional = Optional.empty();
            } else {
                previousPayloadOptional = Optional.of(payload);
            }
            backoffMilliseconds = initBackoffMilliseconds;
        }
    }

    private boolean post(String json) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(webhookUri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status / 100 == 2) {
                noteDeliverySucceeded();
                return true;
            }

            noteDeliveryFailed("it answered " + status);
            return false;
        } catch (IOException e) {
            noteDeliveryFailed("cannot reach it at " + webhookUri, e);
            return false;
        }
    }

    /** A Home Assistant that answered, which says everything there is to say by itself. */
    private void noteDeliveryFailed(String detail) {
        noteDeliveryFailed(detail, null);
    }

    /**
     * The throwable is what carries the reason when there was no answer at all, and it has to be
     * logged rather than flattened into the message: {@code ConnectException} — a refused port,
     * the commonest way this fails — has no message of its own, so a line built from
     * {@code getMessage()} ends in the word "null" and names no cause at all.
     */
    private void noteDeliveryFailed(String detail, IOException cause) {
        failedDeliveriesInARow++;
        if (failedDeliveriesInARow == 1) {
            log.warn("Could not deliver to Home Assistant, will retry: {}", detail, cause);
        } else {
            log.debug(
                    "Home Assistant still has not taken a payload, {} attempts in a row: {}",
                    failedDeliveriesInARow,
                    detail,
                    cause
            );
        }
    }

    private void noteDeliverySucceeded() {
        if (failedDeliveriesInARow > 0) {
            log.warn(
                    "Home Assistant is taking payloads again after {} failed attempts",
                    failedDeliveriesInARow
            );
            failedDeliveriesInARow = 0;
        }
    }

    public void submit(String payload) throws InterruptedException {
        payloadBlockingQueueOneElement.poll();
        payloadBlockingQueueOneElement.put(payload);
    }
}
