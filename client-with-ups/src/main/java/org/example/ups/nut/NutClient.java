package org.example.ups.nut;

import lombok.extern.slf4j.Slf4j;
import org.example.ups.config.UpsClientConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Speaks the upsd network protocol
 */
@Slf4j
public class NutClient {

    private static final double SECONDS_PER_MINUTE = 60.0;

    /**
     * How many of the first successful reads are logged variable by variable, so a fresh
     * installation shows what this UPS actually reports before the log goes quiet.
     */
    private static final int LOGGED_READS = 10;

    private final String host;
    private final int port;
    private final String upsName;

    private final int connectTimeoutMilliseconds;

    private final int readTimeoutMilliseconds;

    private Socket socket;
    private BufferedReader reader;
    private Writer writer;

    /** Whether the current run of failed reads has already been reported. */
    private boolean readFailureReported;

    /** How many successful reads have been logged in full so far. */
    private int loggedReads;

    public NutClient(UpsClientConfig upsClientConfig) {
        this.host = upsClientConfig.nutHost();
        this.port = upsClientConfig.nutPort();
        this.upsName = upsClientConfig.nutUpsName();
        this.connectTimeoutMilliseconds = upsClientConfig.nutConnectTimeoutMilliseconds();
        this.readTimeoutMilliseconds = upsClientConfig.nutReadTimeoutMilliseconds();
    }

    public Optional<UpsSnapshot> tryRead() {
        try {
            UpsSnapshot snapshot = read();
            readFailureReported = false;
            return Optional.of(snapshot);
        } catch (IOException e) {
            dropConnection();

            if (!readFailureReported) {
                readFailureReported = true;
                log.warn("Could not read the UPS", e);
            }

            return Optional.empty();
        }
    }

    private UpsSnapshot read() throws IOException {
        connectIfNeeded();

        writer.write("LIST VAR " + upsName + "\n");
        writer.flush();

        String begin = readLine();
        if (begin.startsWith("ERR ")) {
            throw new IOException("upsd rejected LIST VAR: " + begin);
        }
        if (!begin.equals("BEGIN LIST VAR " + upsName)) {
            throw new IOException("unexpected upsd reply: " + begin);
        }

        String prefix = "VAR " + upsName + " ";
        String end = "END LIST VAR " + upsName;
        boolean logging = loggedReads < LOGGED_READS;
        StringBuilder logged = new StringBuilder();
        UpsSnapshot snapshot = new UpsSnapshot();
        for (String line = readLine(); !line.equals(end); line = readLine()) {
            if (!line.startsWith(prefix)) {
                throw new IOException("unexpected line in LIST VAR: " + line);
            }
            String rest = line.substring(prefix.length());
            int space = rest.indexOf(' ');
            if (space < 0) {
                throw new IOException("malformed VAR line: " + line);
            }
            String name = rest.substring(0, space);
            String value = unescape(rest.substring(space + 1));
            if (logging) {
                logged.append("\n    ").append(name).append(" = ").append(value);
            }

            switch (name) {
                case "ups.status" -> snapshot.setStatus(UpsStatus.from(value));
                case "input.voltage" -> snapshot.setInputVoltage(number(value));
                case "output.voltage" -> snapshot.setOutputVoltage(number(value));
                case "input.frequency" -> snapshot.setInputFrequency(number(value));
                case "ups.load" -> snapshot.setLoadPercent(number(value));
                case "battery.charge" -> snapshot.setBatteryCharge(number(value));
                case "battery.runtime" -> snapshot.setBatteryRuntimeMinutes(minutes(number(value)));
                case "battery.voltage" -> snapshot.setBatteryVoltage(number(value));
                case "ups.beeper.status" -> snapshot.setBeeper(text(value));
                case "ups.test.result" -> snapshot.setTestResult(text(value));
            }
        }

        if (logging) {
            loggedReads++;
            log.info("Read {} of {} from the UPS:{}", loggedReads, LOGGED_READS, logged);
        }
        return snapshot;
    }

    private static OptionalDouble minutes(OptionalDouble seconds) {
        return seconds.isPresent()
                ? OptionalDouble.of(seconds.getAsDouble() / SECONDS_PER_MINUTE)
                : OptionalDouble.empty();
    }

    private static OptionalDouble number(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed)
                    ? OptionalDouble.of(parsed)
                    : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    private static Optional<String> text(String value) {
        return Optional.of(value.trim()).filter(trimmed -> !trimmed.isEmpty());
    }

    private String readLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("upsd closed the connection");
        }
        return line;
    }

    /**
     * Drops the backslash before whatever character follows it, the way NUT's own client parser
     * does, rather than before a fixed pair. upsd quotes every value and escapes {@code "},
     * {@code \} and {@code #}; the {@code #} is NUT's own addition, documented in neither
     * RFC 9271 nor net-protocol.txt, so a client that trusted the spec would leave a stray
     * backslash in it.
     */
    private static String unescape(String quoted) throws IOException {
        if (quoted.length() < 2
                || quoted.charAt(0) != '"'
                || quoted.charAt(quoted.length() - 1) != '"') {
            throw new IOException("value is not quoted: " + quoted);
        }

        String body = quoted.substring(1, quoted.length() - 1);
        StringBuilder result = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                result.append(body.charAt(++i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private void connectIfNeeded() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed() && writer != null) {
            return;
        }
        dropConnection();

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMilliseconds);
        socket.setSoTimeout(readTimeoutMilliseconds);
        reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
        writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    /**
     * The socket owns the connection: closing it also closes its input and output streams, so the
     * reader and the writer are only dropped, never closed. Closing the writer would flush first,
     * and this runs on an already broken connection, so that flush would just throw again. Nothing
     * is lost by skipping it: the only write is flushed right away in {@link #read()}.
     * <p>
     * Nulling the fields is what makes {@link #connectIfNeeded()} reconnect instead of reusing
     * wrappers over a closed socket.
     */
    private void dropConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            log.debug("Failed to close the upsd socket", e);
        }
        socket = null;
        writer = null;
    }
}
