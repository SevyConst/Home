package org.example.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.EventRequest;
import org.example.server.service.EventService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping(path = "/events")
    public void receive(@RequestBody EventRequest request) {
        log.info("Received request: {}", request);
        eventService.processEvents(request);
    }
}
