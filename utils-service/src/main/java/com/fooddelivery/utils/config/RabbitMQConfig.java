package com.fooddelivery.utils.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.payment-queue}")
    private String paymentQueue;

    @Bean
    public Queue paymentQueue() {
        // durable: true (matches amqplib assertQueue({ durable: true }))
        return new Queue(paymentQueue, true);
    }
}
