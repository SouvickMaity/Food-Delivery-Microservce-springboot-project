package com.fooddelivery.rider.repository;

import com.fooddelivery.rider.model.Rider;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RiderRepository extends MongoRepository<Rider, String> {
    Optional<Rider> findByUserId(String userId);
    Optional<Rider> findByUserIdAndIsAvailble(String userId, boolean isAvailble);
    Optional<Rider> findByUserIdAndIsVerified(String userId, boolean isVerified);
}
