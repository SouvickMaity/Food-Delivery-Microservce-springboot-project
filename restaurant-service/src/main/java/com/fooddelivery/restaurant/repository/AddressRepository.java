package com.fooddelivery.restaurant.repository;

import com.fooddelivery.restaurant.model.Address;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends MongoRepository<Address, String> {
    List<Address> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Address> findByIdAndUserId(String id, String userId);
}
