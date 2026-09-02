package com.fooddelivery.restaurant.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.queues.payment}")
    private String paymentQueueName;

    @Value("${app.queues.rider}")
    private String riderQueueName;

    @Value("${app.queues.order-ready}")
    private String orderReadyQueueName;

    @Bean
    public Queue paymentQueue() {
        return new Queue(paymentQueueName, true);
    }

    @Bean
    public Queue riderQueue() {
        return new Queue(riderQueueName, true);
    }

    @Bean
    public Queue orderReadyQueue() {
        return new Queue(orderReadyQueueName, true);
    }
}
