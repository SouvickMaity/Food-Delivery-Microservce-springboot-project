package com.fooddelivery.rider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.rider.model.Rider;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderReadyConsumer {

    private final MongoTemplate mongoTemplate;
    private final RealtimeClient realtimeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderReadyConsumer(MongoTemplate mongoTemplate, RealtimeClient realtimeClient) {
        this.mongoTemplate = mongoTemplate;
        this.realtimeClient = realtimeClient;
    }

    // Listens on the order-ready queue declared in RabbitMQConfig (mirrors config/orderReady.consumer.js)
    @RabbitListener(queues = "${app.queues.order-ready}")
    public void handleOrderReadyEvent(byte[] body) {
        try {
            JsonNode event = objectMapper.readTree(body);

            if (!"ORDER_READY_FOR_RIDER".equals(event.path("type").asText())) {
                return;
            }

            JsonNode data = event.path("data");
            String orderId = data.path("orderId").asText(null);
            String restaurantId = data.path("restaurantId").asText(null);
            JsonNode location = data.path("location");
            JsonNode coords = location.path("coordinates");

            double longitude = coords.get(0).asDouble();
            double latitude = coords.get(1).asDouble();

            // Mirrors Rider.find({ isAvailble: true, isVerified: true, location: { $near: { $geometry: location, $maxDistance: 500 } } })
            // With a GeoJsonPoint field + 2dsphere index, Spring Data interprets maxDistance in meters here.
            Criteria criteria = Criteria.where("isAvailble").is(true)
                    .and("isVerified").is(true)
                    .and("location").nearSphere(new Point(longitude, latitude))
                    .maxDistance(500);

            Query query = new Query(criteria);
            List<Rider> riders = mongoTemplate.find(query, Rider.class);

            if (riders.isEmpty()) {
                return;
            }

            for (Rider rider : riders) {
                try {
                    realtimeClient.emit("order:available", "user:" + rider.getUserId(),
                            java.util.Map.of("orderId", orderId, "restaurantId", restaurantId));
                } catch (Exception e) {
                    System.err.println("Failed to notify rider " + rider.getUserId());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

