package com.fooddelivery.rider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class RestaurantServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.restaurant-service-url}")
    private String restaurantServiceUrl;

    @Value("${app.internal-service-key}")
    private String internalServiceKey;

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-internal-key", internalServiceKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public Map<?, ?> assignRider(Map<String, Object> body) {
        var entity = new HttpEntity<>(body, internalHeaders());
        return restTemplate.exchange(
                restaurantServiceUrl + "/api/order/assign/rider",
                HttpMethod.PUT, entity, Map.class
        ).getBody();
    }

    public Map<?, ?> fetchCurrentOrder(String riderId) {
        var entity = new HttpEntity<>(internalHeaders());
        return restTemplate.exchange(
                restaurantServiceUrl + "/api/order/current/rider?riderId=" + riderId,
                HttpMethod.GET, entity, Map.class
        ).getBody();
    }

    public Map<?, ?> updateOrderStatus(Map<String, Object> body) {
        var entity = new HttpEntity<>(body, internalHeaders());
        return restTemplate.exchange(
                restaurantServiceUrl + "/api/order/update/status/rider",
                HttpMethod.PUT, entity, Map.class
        ).getBody();
    }
}
