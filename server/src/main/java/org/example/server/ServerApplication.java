package org.example.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods
public class ServerApplication {

    static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
