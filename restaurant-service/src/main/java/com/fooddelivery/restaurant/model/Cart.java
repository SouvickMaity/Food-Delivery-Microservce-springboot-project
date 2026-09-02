package com.fooddelivery.restaurant.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "carts")
@CompoundIndex(name = "user_restaurant_item_unique", def = "{'userId': 1, 'restaurantId': 1, 'itemId': 1}", unique = true)
public class Cart {

    @Id
    private String id;

    private String userId;
    private String restaurantId;
    private String itemId;

    private int quauntity = 1;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
