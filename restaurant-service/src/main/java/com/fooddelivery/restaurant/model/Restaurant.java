package com.fooddelivery.restaurant.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "restaurants")
public class Restaurant {

    @Id
    private String id;

    private String name;
    private String description;
    private String image;
    private String ownerId;
    private Long phone;
    private boolean isVerified;

    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private AutoLocation autoLocation;

    private boolean isOpen = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    public static class AutoLocation {
        private String type = "Point";
        private double[] coordinates; // [longitude, latitude]
        private String formattedAddress;
    }
}

