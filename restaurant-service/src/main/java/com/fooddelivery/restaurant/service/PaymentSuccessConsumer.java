package com.fooddelivery.restaurant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.restaurant.model.Order;
import com.fooddelivery.restaurant.repository.OrderRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class PaymentSuccessConsumer {

    private final OrderRepository orderRepository;
    private final RealtimeClient realtimeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentSuccessConsumer(OrderRepository orderRepository, RealtimeClient realtimeClient) {
        this.orderRepository = orderRepository;
        this.realtimeClient = realtimeClient;
    }

    // Listens on the payment queue declared in RabbitMQConfig (mirrors consumers/payment.consumer.js)
    @RabbitListener(queues = "${app.queues.payment}")
    public void handlePaymentEvent(byte[] body) {
        try {
            JsonNode event = objectMapper.readTree(body);

            if (!"PAYMENT_SUCCESS".equals(event.path("type").asText())) {
                return;
            }

            JsonNode data = event.path("data");
            String orderId = data.path("orderId").asText(null);
            String paymentId = data.path("paymentId").asText(null);

            if (orderId == null) return;

            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) return;

            Order order = orderOpt.get();

            if ("paid".equals(order.getPaymentStatus())) {
                return; // already processed, avoid double-processing on redelivery
            }

            order.setPaymentStatus("paid");
            order.setStatus("accepted");
            order.setExpiresAt(null);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            realtimeClient.emit("order:new", "restaurant:" + order.getRestaurantId(), order);
            realtimeClient.emit("order:update", "user:" + order.getUserId(),
                    java.util.Map.of("orderId", order.getId(), "status", order.getStatus(), "paymentId", paymentId));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
