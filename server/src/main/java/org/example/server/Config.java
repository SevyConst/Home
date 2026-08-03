package org.example.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class Config {

    // The server runs abroad, but messages must show the time of the zone the devices live in.
    // Pinning the clock to that zone keeps every ZonedDateTime in the service on one scale.
    @Bean
    public Clock clock(@Value("${app.display-zone}") String displayZone) {
        return Clock.system(ZoneId.of(displayZone));
    }
}
