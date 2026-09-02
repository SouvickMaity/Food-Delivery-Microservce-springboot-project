package com.fooddelivery.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class MenuItemResponse {

    private String id;
    private String restaurantId;
    private String name;
    private String description;
    private Double price;
    private String image;
    private Boolean available;
    private Instant createdAt;
    private Instant updatedAt;
}