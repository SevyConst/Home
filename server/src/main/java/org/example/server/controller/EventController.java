package org.example.server.controller;

import lombok.RequiredArgsConstructor;
import model.EventRequest;
import org.example.server.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;

    @PostMapping(path = "/events")
    public void receive(@RequestBody EventRequest request) {
        log.info("Received request: {}", request);
        eventService.processEvents(request);
    }
}
