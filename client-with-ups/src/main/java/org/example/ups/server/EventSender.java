package org.example.ups.server;

import kotlinx.serialization.json.Json;
import lombok.extern.slf4j.Slf4j;
import model.EventRequest;
import org.example.ups.config.UpsClientConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
public class EventSender {

    private final URI serverUri;
    private final Duration serverRequestTimeout;
    private final HttpClient httpClient;

    public EventSender(UpsClientConfig upsClientConfig) {
        this.serverUri = upsClientConfig.serverUri();
        this.serverRequestTimeout = upsClientConfig.serverRequestTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(upsClientConfig.serverConnectTimeout())
                .build();
    }

    public boolean send(EventRequest eventRequest) throws InterruptedException {
        String json = Json.Default.encodeToString(EventRequest.Companion.serializer(), eventRequest);
        log.info("Sending {}", json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(serverUri)
                .timeout(serverRequestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.error("Failed to send {}", json, e);
            return false;
        }

        if (response.statusCode() / 100 != 2) {
            log.error(
                    "Http response code: {}, response body: {}",
                    response.statusCode(),
                    response.body()
            );
            return false;
        }

        log.info(
                "Http response code: {}, response body: {}",
                response.statusCode(),
                response.body()
        );
        return true;
    }
}
