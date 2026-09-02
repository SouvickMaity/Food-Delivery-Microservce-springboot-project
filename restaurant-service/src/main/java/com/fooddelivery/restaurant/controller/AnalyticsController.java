package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.model.Restaurant;
import com.fooddelivery.restaurant.repository.RestaurantRepository;
import com.fooddelivery.restaurant.security.AuthHelper;
import com.mongodb.client.MongoCollection;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final RestaurantRepository restaurantRepository;
    private final AuthHelper authHelper;
    private final MongoTemplate mongoTemplate;

    public AnalyticsController(RestaurantRepository restaurantRepository, AuthHelper authHelper, MongoTemplate mongoTemplate) {
        this.restaurantRepository = restaurantRepository;
        this.authHelper = authHelper;
        this.mongoTemplate = mongoTemplate;
    }

    // GET /api/analytics/{restaurantId} (isAuth, isSeller)
    @GetMapping("/{restaurantId}")
    public ResponseEntity<?> getRestaurantAnalytics(HttpServletRequest request, @PathVariable String restaurantId) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Restaurant not found"));
            }

            Restaurant restaurant = restaurantOpt.get();
            if (!restaurant.getOwnerId().equals(String.valueOf(user.get("_id")))) {
                return ResponseEntity.status(403).body(Map.of("success", false,
                        "message", "You are not authorized to access this analytics."));
            }

            LocalDate today = LocalDate.now();
            Date startOfToday = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date startOfMonth = Date.from(today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

            List<Document> pipeline = List.of(
                    new Document("$match", new Document("restaurantId", restaurantId)
                            .append("status", "delivered").append("paymentStatus", "paid")),
                    new Document("$facet", new Document()
                            .append("totalSales", List.of(
                                    new Document("$group", new Document("_id", null)
                                            .append("totalSales", new Document("$sum", "$totalAmount")))))
                            .append("todaySales", List.of(
                                    new Document("$match", new Document("createdAt", new Document("$gte", startOfToday))),
                                    new Document("$group", new Document("_id", null)
                                            .append("todaySales", new Document("$sum", "$totalAmount")))))
                            .append("monthlySales", List.of(
                                    new Document("$match", new Document("createdAt", new Document("$gte", startOfMonth))),
                                    new Document("$group", new Document("_id", null)
                                            .append("monthlySales", new Document("$sum", "$totalAmount")))))
                            .append("ordersToday", List.of(
                                    new Document("$match", new Document("createdAt", new Document("$gte", startOfToday))),
                                    new Document("$count", "ordersToday")))
                            .append("topSellingItems", List.of(
                                    new Document("$unwind", "$items"),
                                    new Document("$group", new Document("_id", "$items.name")
                                            .append("sold", new Document("$sum", "$items.quauntity"))),
                                    new Document("$sort", new Document("sold", -1)),
                                    new Document("$limit", 5),
                                    new Document("$project", new Document("_id", 0)
                                            .append("name", "$_id").append("sold", 1))))
                    )
            );

            MongoCollection<Document> orders = mongoTemplate.getCollection("orders");
            List<Document> analytics = new ArrayList<>();
            orders.aggregate(pipeline).into(analytics);

            Document data = analytics.get(0);

            List<Document> totalSalesList = (List<Document>) data.get("totalSales");
            List<Document> todaySalesList = (List<Document>) data.get("todaySales");
            List<Document> monthlySalesList = (List<Document>) data.get("monthlySales");
            List<Document> ordersTodayList = (List<Document>) data.get("ordersToday");
            List<Document> topSellingItems = (List<Document>) data.get("topSellingItems");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalSales", totalSalesList.isEmpty() ? 0 : totalSalesList.get(0).get("totalSales"));
            result.put("todaySales", todaySalesList.isEmpty() ? 0 : todaySalesList.get(0).get("todaySales"));
            result.put("monthlySales", monthlySalesList.isEmpty() ? 0 : monthlySalesList.get(0).get("monthlySales"));
            result.put("ordersToday", ordersTodayList.isEmpty() ? 0 : ordersTodayList.get(0).get("ordersToday"));
            result.put("topSellingItems", topSellingItems);

            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", error.getMessage()));
        }
    }
}
