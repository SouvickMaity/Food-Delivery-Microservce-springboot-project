package com.fooddelivery.utils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentProducerService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.payment-queue}")
    private String paymentQueue;

    public PaymentProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentSuccess(Map<String, Object> payload) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "PAYMENT_SUCCESS");
        event.put("data", payload);

        byte[] body = objectMapper.writeValueAsBytes(event);

        MessageProperties props = new MessageProperties();
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        rabbitTemplate.send(paymentQueue,
                new org.springframework.amqp.core.Message(body, props));
    }
}
