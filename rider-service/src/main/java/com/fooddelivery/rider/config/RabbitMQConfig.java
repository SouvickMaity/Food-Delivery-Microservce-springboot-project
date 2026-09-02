package com.fooddelivery.rider.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.queues.rider}")
    private String riderQueueName;

    @Value("${app.queues.order-ready}")
    private String orderReadyQueueName;

    @Bean
    public Queue riderQueue() {
        return new Queue(riderQueueName, true);
    }

    @Bean
    public Queue orderReadyQueue() {
        return new Queue(orderReadyQueueName, true);
    }
}
