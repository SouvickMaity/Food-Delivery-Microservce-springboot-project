package com.fooddelivery.rider.controller;

import com.fooddelivery.rider.model.Rider;
import com.fooddelivery.rider.repository.RiderRepository;
import com.fooddelivery.rider.security.AuthHelper;
import com.fooddelivery.rider.service.RestaurantServiceClient;
import com.fooddelivery.rider.service.UploadClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/rider")
public class RiderController {

    private final RiderRepository riderRepository;
    private final AuthHelper authHelper;
    private final UploadClient uploadClient;
    private final RestaurantServiceClient restaurantServiceClient;


    public RiderController(RiderRepository riderRepository, AuthHelper authHelper,
                            UploadClient uploadClient, RestaurantServiceClient restaurantServiceClient) {
        this.riderRepository = riderRepository;
        this.authHelper = authHelper;
        this.uploadClient = uploadClient;
        this.restaurantServiceClient = restaurantServiceClient;
    }

    // POST /api/rider/new (isAuth, multipart "file")
    @PostMapping(value = "/new", consumes = "multipart/form-data")
    public ResponseEntity<?> addRiderProfile(HttpServletRequest request,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam String phoneNumber,
                                              @RequestParam String aadharNumber,
                                              @RequestParam String drivingLicenseNumber,
                                              @RequestParam Double latitude,
                                              @RequestParam Double longitude) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);

            if (!"rider".equals(user.get("role"))) {
                return ResponseEntity.status(403).body(Map.of("message", "Only riders can create rider profile"));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Rider Image is required"));
            }

            String imageUrl = uploadClient.uploadAndGetUrl(file);

            if (phoneNumber == null || aadharNumber == null || drivingLicenseNumber == null
                    || latitude == null || longitude == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "All fields are required"));
            }

            String userId = String.valueOf(user.get("_id"));

            if (riderRepository.findByUserId(userId).isPresent()) {
                return ResponseEntity.status(400).body(Map.of("message", "Rider profile already exists"));
            }

            Rider rider = new Rider();
            rider.setUserId(userId);
            rider.setPicture(imageUrl);
            rider.setPhoneNumber(phoneNumber);
            rider.setAadharNumber(aadharNumber);
            rider.setDrivingLicenseNumber(drivingLicenseNumber);
            rider.setLocation(new GeoJsonPoint(longitude, latitude));
            rider.setAvailble(false);
            rider.setVerified(false);
            rider.setCreatedAt(Instant.now());
            rider.setUpdatedAt(Instant.now());

            riderRepository.save(rider);

            return ResponseEntity.status(201).body(Map.of(
                    "message", "Rider profile created successfully",
                    "riderProfile", rider
            ));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/rider/myprofile (isAuth)
    @GetMapping("/myprofile")
    public ResponseEntity<?> fetchMyProfile(HttpServletRequest request) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            Optional<Rider> rider = riderRepository.findByUserId(userId);
            return ResponseEntity.ok(rider.orElse(null));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PATCH /api/rider/toggle (isAuth)
    @PatchMapping("/toggle")
    public ResponseEntity<?> toggleRiderAvailablity(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);

            if (!"rider".equals(user.get("role"))) {
                return ResponseEntity.status(403).body(Map.of("message", "Only riders can create rider profile"));
            }

            Object availObj = body.get("isAvailble");
            if (!(availObj instanceof Boolean isAvailble)) {
                return ResponseEntity.badRequest().body(Map.of("message", "isAvailble must be boolean"));
            }

            Object latObj = body.get("latitude");
            Object lonObj = body.get("longitude");
            if (latObj == null || lonObj == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Location is required"));
            }

            String userId = String.valueOf(user.get("_id"));
            Optional<Rider> riderOpt = riderRepository.findByUserId(userId);

            if (riderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Rider profile not found"));
            }

            Rider rider = riderOpt.get();

            if (isAvailble && !rider.isVerified()) {
                return ResponseEntity.status(403).body(Map.of("message", "Rider is not verified"));
            }

            rider.setAvailble(isAvailble);
            rider.setLocation(new GeoJsonPoint(
                    Double.parseDouble(String.valueOf(lonObj)),
                    Double.parseDouble(String.valueOf(latObj))
            ));
            rider.setLastActiveAt(Instant.now());
            rider.setUpdatedAt(Instant.now());
            riderRepository.save(rider);

            return ResponseEntity.ok(Map.of(
                    "message", isAvailble ? "Rider is now online" : "Rider is now offline",
                    "rider", rider
            ));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // POST /api/rider/accept/{orderId} (isAuth)
    @PostMapping("/accept/{orderId}")
    public ResponseEntity<?> acceptOrder(HttpServletRequest request, @PathVariable String orderId) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String riderUserId = String.valueOf(user.get("_id"));

            Optional<Rider> riderOpt = riderRepository.findByUserIdAndIsAvailble(riderUserId, true);
            if (riderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Rider not found"));
            }
            Rider rider = riderOpt.get();

            try {
                Map<String, Object> assignBody = Map.of(
                        "orderId", orderId,
                        "riderId", rider.getId(),
                        "riderUserId", rider.getUserId(),
                        "riderName", rider.getPicture(),
                        "riderPhone", rider.getPhoneNumber()
                );

                Map<?, ?> data = restaurantServiceClient.assignRider(assignBody);

                if (data != null && Boolean.TRUE.equals(data.get("success"))) {
                    rider.setAvailble(false);
                    rider.setUpdatedAt(Instant.now());
                    riderRepository.save(rider);

                    return ResponseEntity.ok(Map.of("message", "Order accepted"));
                }

                return ResponseEntity.badRequest().body(Map.of("message", "Failed to accept order"));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Order already taken"));
            }
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/rider/order/current (isAuth)
    @GetMapping("/order/current")
    public ResponseEntity<?> fetchMyCurrentOrder(HttpServletRequest request) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String riderUserId = String.valueOf(user.get("_id"));

            Optional<Rider> riderOpt = riderRepository.findByUserIdAndIsVerified(riderUserId, true);
            if (riderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Rider not found"));
            }
            Rider rider = riderOpt.get();

            try {
                Map<?, ?> data = restaurantServiceClient.fetchCurrentOrder(rider.getId());
                return ResponseEntity.ok(Map.of("order", data));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("message", "Failed to fetch current order"));
            }
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error to get order"));
        }
    }

    // PUT /api/rider/order/update/{orderId} (isAuth)
    @PutMapping("/order/update/{orderId}")
    public ResponseEntity<?> updateOrderStatus(HttpServletRequest request, @PathVariable String orderId) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            Optional<Rider> riderOpt = riderRepository.findByUserId(userId);
            if (riderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Please Login"));
            }

            try {
                Map<?, ?> data = restaurantServiceClient.updateOrderStatus(Map.of("orderId", orderId));
                return ResponseEntity.ok(Map.of("message", data != null ? data.get("message") : null));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("message", "Failed to update order status"));
            }
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }
}
