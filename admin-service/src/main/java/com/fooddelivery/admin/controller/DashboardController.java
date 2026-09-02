package com.fooddelivery.admin.controller;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class DashboardController {

    private final MongoTemplate mongoTemplate;

    public DashboardController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // GET /api/v1/admin/dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats() {
        try {
            long totalUsers = mongoTemplate.count(
                    Query.query(Criteria.where("role").is("customer")), "users");
            long totalRestaurants = mongoTemplate.count(new Query(), "restaurants");
            long totalRiders = mongoTemplate.count(new Query(), "riders");
            long totalOrders = mongoTemplate.count(new Query(), "orders");
            long activeRestaurants = mongoTemplate.count(
                    Query.query(Criteria.where("isVerified").is(true).and("isOpen").is(true)), "restaurants");
            long onlineRiders = mongoTemplate.count(
                    Query.query(Criteria.where("isOnline").is(true)), "riders");

            MongoCollection<Document> orders = mongoTemplate.getCollection("orders");

            List<Document> revenuePipeline = List.of(
                    new Document("$match", new Document("status", "delivered").append("paymentStatus", "paid")),
                    new Document("$group", new Document("_id", null)
                            .append("totalPlatformRevenue", new Document("$sum", "$platfromFee")))
            );

            List<Document> revenueResult = new ArrayList<>();
            orders.aggregate(revenuePipeline).into(revenueResult);

            Object platformRevenue = revenueResult.isEmpty() ? 0 : revenueResult.get(0).get("totalPlatformRevenue");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalUsers", totalUsers);
            data.put("totalRestaurants", totalRestaurants);
            data.put("totalRiders", totalRiders);
            data.put("totalOrders", totalOrders);
            data.put("platformRevenue", platformRevenue);
            data.put("activeRestaurants", activeRestaurants);
            data.put("onlineRiders", onlineRiders);

            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to fetch dashboard statistics"));
        }
    }

    // GET /api/v1/admin/revenue  (last 7 days)
    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenueAnalytics() {
        try {
            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(6);
            Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

            MongoCollection<Document> orders = mongoTemplate.getCollection("orders");

            List<Document> pipeline = List.of(
                    new Document("$match", new Document("status", "delivered")
                            .append("paymentStatus", "paid")
                            .append("createdAt", new Document("$gte", Date.from(startInstant)))),
                    new Document("$group", new Document("_id",
                            new Document("year", new Document("$year", "$createdAt"))
                                    .append("month", new Document("$month", "$createdAt"))
                                    .append("day", new Document("$dayOfMonth", "$createdAt")))
                            .append("revenue", new Document("$sum", "$platfromFee"))),
                    new Document("$sort", new Document("_id.year", 1).append("_id.month", 1).append("_id.day", 1))
            );

            List<Document> revenue = new ArrayList<>();
            orders.aggregate(pipeline).into(revenue);

            List<Map<String, Object>> graphData = new ArrayList<>();

            for (int i = 0; i < 7; i++) {
                LocalDate current = startDate.plusDays(i);

                Optional<Document> found = revenue.stream().filter(item -> {
                    Document id = (Document) item.get("_id");
                    return id.getInteger("year") == current.getYear()
                            && id.getInteger("month") == current.getMonthValue()
                            && id.getInteger("day") == current.getDayOfMonth();
                }).findFirst();

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("date", current.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                entry.put("revenue", found.map(d -> d.get("revenue")).orElse(0));

                graphData.add(entry);
            }

            return ResponseEntity.ok(Map.of("success", true, "data", graphData));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to fetch revenue analytics"));
        }
    }
}
