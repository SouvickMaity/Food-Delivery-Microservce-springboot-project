package com.fooddelivery.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
public class LocationController {

    private final RestTemplate restTemplate = new RestTemplate();

    // GET /api/location/reverse?lat=..&lon=..
    @GetMapping("/api/location/reverse")
    public ResponseEntity<?> reverseGeocode(@RequestParam String lat, @RequestParam String lon) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/reverse")
                    .queryParam("format", "json")
                    .queryParam("lat", lat)
                    .queryParam("lon", lon)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "FoodDeliveryApp/1.0");

            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            return ResponseEntity.ok(resp.getBody());
        } catch (Exception err) {
            err.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Location fetch failed"));
        }
    }
}
