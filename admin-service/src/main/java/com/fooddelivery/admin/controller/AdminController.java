package com.fooddelivery.admin.controller;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final MongoTemplate mongoTemplate;

    public AdminController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // GET /api/v1/admin/restaurant/pending
    @GetMapping("/admin/restaurant/pending")
    public ResponseEntity<?> getPendingRestaurant() {
        try {
            List<Document> restaurants = mongoTemplate.find(
                    Query.query(Criteria.where("isVerified").is(false)), Document.class, "restaurants");

            return ResponseEntity.ok(Map.of("count", restaurants.size(), "restaurants", restaurants));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/v1/admin/rider/pending
    @GetMapping("/admin/rider/pending")
    public ResponseEntity<?> getPendingRiders() {
        try {
            List<Document> riders = mongoTemplate.find(
                    Query.query(Criteria.where("isVerified").is(false)), Document.class, "riders");

            return ResponseEntity.ok(Map.of("count", riders.size(), "riders", riders));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PATCH /api/v1/verify/restaurant/{id}
    @PatchMapping("/verify/restaurant/{id}")
    public ResponseEntity<?> verifyRestaurant(@PathVariable String id) {
        return verifyDoc(id, "restaurants", "Restaurant");
    }

    // PATCH /api/v1/verify/rider/{id}
    @PatchMapping("/verify/rider/{id}")
    public ResponseEntity<?> verifyRider(@PathVariable String id) {
        return verifyDoc(id, "riders", "Rider");
    }

    private ResponseEntity<?> verifyDoc(String id, String collection, String label) {
        try {
            if (!ObjectId.isValid(id)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid object id"));
            }

            Update update = new Update()
                    .set("isVerified", true)
                    .set("updatedAt", Instant.now());

            var result = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(new ObjectId(id))),
                    update, collection);

            if (result.getMatchedCount() == 0) {
                return ResponseEntity.status(404).body(Map.of("message", label + " not found"));
            }

            return ResponseEntity.ok(Map.of("message", label + " verified successfully"));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }
}
