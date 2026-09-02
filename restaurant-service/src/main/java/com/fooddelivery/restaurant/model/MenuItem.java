package com.fooddelivery.restaurant.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "menuitems")
public class MenuItem {

    @Id
    private String id;

    @Indexed
    private ObjectId restaurantId;

    private String name;
    private String description;
    private Double price;
    private String image;
    private Boolean available = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
