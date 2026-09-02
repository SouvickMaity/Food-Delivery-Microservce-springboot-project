package com.fooddelivery.restaurant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class RealtimeClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.realtime-service-url}")
    private String realtimeServiceUrl;

    @Value("${app.internal-service-key}")
    private String internalServiceKey;

    public void emit(String event, String room, Object payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-internal-key", internalServiceKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "event", event,
                    "room", room,
                    "payload", payload == null ? Map.of() : payload
            );

            restTemplate.postForObject(
                    realtimeServiceUrl + "/api/v1/internal/emit",
                    new HttpEntity<>(body, headers),
                    String.class
            );
        } catch (Exception e) {
            // Mirrors JS behavior: emit failures are not fatal to the main flow, just log.
            System.err.println("Realtime emit failed: " + e.getMessage());
        }
    }
}
