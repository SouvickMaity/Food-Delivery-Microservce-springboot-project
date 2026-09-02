package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.model.Restaurant;
import com.fooddelivery.restaurant.repository.RestaurantRepository;
import com.fooddelivery.restaurant.security.AuthHelper;
import com.fooddelivery.restaurant.security.JwtUtil;
import com.fooddelivery.restaurant.service.UploadClient;
import com.mongodb.client.MongoCollection;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/restaurant")
public class RestaurantController {

    private final RestaurantRepository restaurantRepository;
    private final AuthHelper authHelper;
    private final UploadClient uploadClient;
    private final MongoTemplate mongoTemplate;
    private final JwtUtil jwtUtil;

    public RestaurantController(RestaurantRepository restaurantRepository, AuthHelper authHelper,
                                 UploadClient uploadClient, MongoTemplate mongoTemplate, JwtUtil jwtUtil) {
        this.restaurantRepository = restaurantRepository;
        this.authHelper = authHelper;
        this.uploadClient = uploadClient;
        this.mongoTemplate = mongoTemplate;
        this.jwtUtil = jwtUtil;
    }

    // POST /api/restaurant/new  (isAuth, isSeller, multipart "file")
    @PostMapping(value = "/new", consumes = "multipart/form-data")
    public ResponseEntity<?> addRestraunt(HttpServletRequest request,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam String name,
                                           @RequestParam(required = false) String description,
                                           @RequestParam Double latitude,
                                           @RequestParam Double longitude,
                                           @RequestParam(required = false) String formattedAddress,
                                           @RequestParam(required = false) Long phone) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            String ownerId = String.valueOf(user.get("_id"));

            if (restaurantRepository.findByOwnerId(ownerId).isPresent()) {
                return ResponseEntity.status(400).body(Map.of("message", "You already have a restaurant"));
            }

            if (name == null || latitude == null || longitude == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Please give all details"));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Please give image"));
            }

            String imageUrl = uploadClient.uploadAndGetUrl(file);

            Restaurant restaurant = new Restaurant();
            restaurant.setName(name);
            restaurant.setDescription(description);
            restaurant.setPhone(phone);
            restaurant.setImage(imageUrl);
            restaurant.setOwnerId(ownerId);
            restaurant.setVerified(false);
            restaurant.setOpen(false);

            Restaurant.AutoLocation loc = new Restaurant.AutoLocation();
            loc.setCoordinates(new double[]{longitude, latitude});
            loc.setFormattedAddress(formattedAddress);
            restaurant.setAutoLocation(loc);

            restaurant.setCreatedAt(Instant.now());
            restaurant.setUpdatedAt(Instant.now());

            restaurantRepository.save(restaurant);

            return ResponseEntity.status(201).body(Map.of(
                    "message", "Restaurant created successfully",
                    "restaurant", restaurant
            ));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/restaurant/my  (isAuth, isSeller)
    @GetMapping("/my")
    public ResponseEntity<?> fetchMyRestaurant(HttpServletRequest request) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            String ownerId = String.valueOf(user.get("_id"));
            Optional<Restaurant> restaurantOpt = restaurantRepository.findByOwnerId(ownerId);

            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("message", "No Restaurant found"));
            }

            Restaurant restaurant = restaurantOpt.get();

            if (user.get("restaurantId") == null) {
                Map<String, Object> updatedUser = new LinkedHashMap<>(user);
                updatedUser.put("restaurantId", restaurant.getId());

                String token = jwtUtil.generateToken(updatedUser);

                return ResponseEntity.ok(Map.of("restaurant", restaurant, "token", token));
            }

            return ResponseEntity.ok(Map.of("restaurant", restaurant));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/restaurant/status (isAuth, isSeller)
    @PutMapping("/status")
    public ResponseEntity<?> updateStatusRestaurant(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            Object statusObj = body.get("status");
            if (!(statusObj instanceof Boolean status)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Status must be boolean"));
            }

            Optional<Restaurant> restaurantOpt = restaurantRepository.findByOwnerId(String.valueOf(user.get("_id")));
            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Restaurant not found"));
            }

            Restaurant restaurant = restaurantOpt.get();
            restaurant.setOpen(status);
            restaurant.setUpdatedAt(Instant.now());
            restaurantRepository.save(restaurant);

            return ResponseEntity.ok(Map.of("message", "Restaurant status Updated", "restaurant", restaurant));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/restaurant/edit (isAuth, isSeller)
    @PutMapping("/edit")
    public ResponseEntity<?> updateRestaurant(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            Optional<Restaurant> restaurantOpt = restaurantRepository.findByOwnerId(String.valueOf(user.get("_id")));
            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Restaurant not found"));
            }

            Restaurant restaurant = restaurantOpt.get();
            if (body.get("name") != null) restaurant.setName(String.valueOf(body.get("name")));
            if (body.get("description") != null) restaurant.setDescription(String.valueOf(body.get("description")));
            restaurant.setUpdatedAt(Instant.now());
            restaurantRepository.save(restaurant);

            return ResponseEntity.ok(Map.of("message", "Restaurant Updated", "restaurant", restaurant));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/restaurant/all (isAuth) - geoNear nearby search
    @GetMapping("/all")
    public ResponseEntity<?> getNearbyRestaurant(
            HttpServletRequest request,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "5000") Double radius,
            @RequestParam(defaultValue = "") String search) {

        try {

            authHelper.requireAuth(request);

            Document query = new Document("isVerified", true);

            if (search != null && !search.isBlank()) {
                query.append(
                        "name",
                        new Document("$regex", search)
                                .append("$options", "i")
                );
            }

            Document geoNear = new Document(
                    "$geoNear",
                    new Document()
                            .append(
                                    "near",
                                    new Document("type", "Point")
                                            .append(
                                                    "coordinates",
                                                    List.of(longitude, latitude)
                                            )
                            )
                            .append("distanceField", "distance")
                            .append("maxDistance", radius)
                            .append("spherical", true)
                            .append("query", query)
            );

            Document sort = new Document(
                    "$sort",
                    new Document("isOpen", -1)
                            .append("distance", 1)
            );

            Document addFields = new Document(
                    "$addFields",
                    new Document(
                            "distanceKm",
                            new Document(
                                    "$round",
                                    List.of(
                                            new Document(
                                                    "$divide",
                                                    List.of("$distance", 1000)
                                            ),
                                            2
                                    )
                            )
                    )
            );

            MongoCollection<Document> collection =
                    mongoTemplate.getCollection("restaurants");

            List<Document> restaurants = new ArrayList<>();

            collection.aggregate(
                    List.of(
                            geoNear,
                            sort,
                            addFields
                    )
            ).into(restaurants);

            // Convert MongoDB ObjectId -> String
            List<Map<String, Object>> responseRestaurants = new ArrayList<>();

            for (Document restaurant : restaurants) {

                Map<String, Object> map = new LinkedHashMap<>();

                ObjectId objectId = restaurant.getObjectId("_id");

                if (objectId != null) {
                    map.put("_id", objectId.toHexString());
                }

                restaurant.forEach((key, value) -> {
                    if (!key.equals("_id")) {
                        map.put(key, value);
                    }
                });

                responseRestaurants.add(map);
            }

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "count", responseRestaurants.size(),
                            "restaurants", responseRestaurants
                    )
            );

        } catch (AuthHelper.AuthException e) {

            throw e;

        } catch (Exception error) {

            error.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "success", false,
                                    "message", "Internal Server Error"
                            )
                    );
        }
    }


    // GET /api/restaurant/{id} (isAuth)
    @GetMapping("/{id}")
    public ResponseEntity<?> fetchSingleRestaurant(HttpServletRequest request, @PathVariable String id) {
        try {
            authHelper.requireAuth(request);

            Optional<Restaurant> restaurantOpt = restaurantRepository.findById(id);
            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Restaurant not found"));
            }

            return ResponseEntity.ok(restaurantOpt.get());
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }
}


