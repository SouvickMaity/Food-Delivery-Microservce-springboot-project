package com.fooddelivery.restaurant.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    private String userId;
    private String restaurantId;
    private String restaurantName;

    private String riderId;
    private String riderName;
    private Long riderPhone;
    private double riderAmount;
    private double distance;

    private List<OrderItem> items;

    private Double subtotal;
    private Double deliveryFee;
    private Double platfromFee;
    private Double totalAmount;

    private String addressId;
    private DeliveryAddress deliveryAddress;

    /** placed | accepted | preparing | ready_for_rider | rider_assigned | picked_up | delivered | cancelled */
    private String status = "placed";

    /** stripe */
    private String paymentMethod;

    /** pending | paid | failed */
    private String paymentStatus = "pending";

    @Indexed(name = "expiresAt_ttl", expireAfterSeconds = 0)
    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    public static class OrderItem {
        private String itemId;
        private String name;
        private Double price;
        private Integer quauntity;
    }

    @Data
    public static class DeliveryAddress {
        private String fromattedAddress;
        private Long mobile;
        private Double latitude;
        private Double longitude;
    }
}
