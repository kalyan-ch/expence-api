package com.expence.expenceApi.controller;

import com.expence.expenceApi.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.BooleanSupplier;

/** 204 once Discord has the message, 409 with no webhook set, 502 if Discord turned it down. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/test")
    public ResponseEntity<?> test() {
        return sent(notificationService::sendTest);
    }

    @PostMapping("/digest")
    public ResponseEntity<?> digest() {
        return sent(notificationService::sendDigestNow);
    }

    private static ResponseEntity<?> sent(BooleanSupplier send) {
        return send.getAsBoolean() ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Discord didn't accept the message. Check the webhook URL."));
    }
}
