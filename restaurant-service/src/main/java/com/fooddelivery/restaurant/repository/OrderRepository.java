package com.fooddelivery.restaurant.repository;

import com.fooddelivery.restaurant.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByRestaurantIdAndPaymentStatusOrderByCreatedAtDesc(String restaurantId, String paymentStatus);
    List<Order> findByUserIdAndPaymentStatusOrderByCreatedAtDesc(String userId, String paymentStatus);
    Optional<Order> findByRiderIdAndStatusNot(String riderId, String status);
}
