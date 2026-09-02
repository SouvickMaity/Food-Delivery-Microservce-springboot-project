package com.fooddelivery.restaurant.repository;

import com.fooddelivery.restaurant.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends MongoRepository<Cart, String> {
    List<Cart> findByUserId(String userId);
    Optional<Cart> findByUserIdAndRestaurantIdNot(String userId, String restaurantId);
    Optional<Cart> findByUserIdAndRestaurantIdAndItemId(String userId, String restaurantId, String itemId);
    Optional<Cart> findByUserIdAndItemId(String userId, String itemId);
    void deleteByUserId(String userId);
    void deleteByUserIdAndItemId(String userId, String itemId);
}
