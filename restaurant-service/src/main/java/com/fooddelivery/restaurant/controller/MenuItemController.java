package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.model.MenuItem;
import com.fooddelivery.restaurant.model.Restaurant;
import com.fooddelivery.restaurant.repository.MenuItemRepository;
import com.fooddelivery.restaurant.repository.RestaurantRepository;
import com.fooddelivery.restaurant.security.AuthHelper;
import com.fooddelivery.restaurant.service.UploadClient;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/item")
public class MenuItemController {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final AuthHelper authHelper;
    private final UploadClient uploadClient;

    public MenuItemController(
            MenuItemRepository menuItemRepository,
            RestaurantRepository restaurantRepository,
            AuthHelper authHelper,
            UploadClient uploadClient) {

        this.menuItemRepository = menuItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.authHelper = authHelper;
        this.uploadClient = uploadClient;
    }

    @PostMapping(value = "/new", consumes = "multipart/form-data")
    public ResponseEntity<?> addMenuItem(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam Double price) {

        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            Optional<Restaurant> restaurantOpt =
                    restaurantRepository.findByOwnerId(
                            String.valueOf(user.get("_id"))
                    );

            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(Map.of("message", "NO Restaurant found"));
            }

            if (name == null || price == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Name and price are required"));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Please give image"));
            }

            String imageUrl = uploadClient.uploadAndGetUrl(file);

            MenuItem item = new MenuItem();

            item.setName(name);
            item.setDescription(description);
            item.setPrice(price);

            // String -> ObjectId
            item.setRestaurantId(
                    new ObjectId(restaurantOpt.get().getId())
            );

            item.setImage(imageUrl);
            item.setCreatedAt(Instant.now());
            item.setUpdatedAt(Instant.now());

            menuItemRepository.save(item);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Item Added Successfully",
                            "item", item
                    )
            );

        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();

            return ResponseEntity.status(500)
                    .body(Map.of("message", "Internal Server Error"));
        }
    }

    @GetMapping("/all/{id}")
    public ResponseEntity<?> getAllItems(
            HttpServletRequest request,
            @PathVariable String id) {

        try {
            authHelper.requireAuth(request);

            ObjectId restaurantId = new ObjectId(id);

            List<MenuItem> items =
                    menuItemRepository.findByRestaurantId(restaurantId);

            List<MenuItemResponse> response = items.stream()
                    .map(item -> new MenuItemResponse(
                            item.getId(),
                            item.getRestaurantId() != null
                                    ? item.getRestaurantId().toHexString()
                                    : null,
                            item.getName(),
                            item.getDescription(),
                            item.getPrice(),
                            item.getImage(),
                            item.getAvailable(),
                            item.getCreatedAt(),
                            item.getUpdatedAt()
                    ))
                    .toList();

            return ResponseEntity.ok(response);

        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();

            return ResponseEntity.status(500)
                    .body(Map.of("message", "Internal Server Error"));
        }
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<?> deleteMenuItem(
            HttpServletRequest request,
            @PathVariable String itemId) {

        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            Optional<MenuItem> itemOpt =
                    menuItemRepository.findById(itemId);

            if (itemOpt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(Map.of("message", "No item found"));
            }

            MenuItem item = itemOpt.get();

            Optional<Restaurant> restaurantOpt =
                    restaurantRepository.findByOwnerId(
                            String.valueOf(user.get("_id"))
                    );

            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(Map.of("message", "NO Restaurant found"));
            }

            ObjectId restaurantId =
                    new ObjectId(restaurantOpt.get().getId());

            if (!restaurantId.equals(item.getRestaurantId())) {
                return ResponseEntity.status(404)
                        .body(Map.of("message", "NO Restaurant found"));
            }

            menuItemRepository.delete(item);

            return ResponseEntity.ok(
                    Map.of("message", "Menu item deleted successfully")
            );

        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();

            return ResponseEntity.status(500)
                    .body(Map.of("message", "Internal Server Error"));
        }
    }

    @PutMapping("/status/{itemId}")
    public ResponseEntity<?> toggleMenuItemAvailability(
            HttpServletRequest request,
            @PathVariable String itemId) {

        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            Optional<MenuItem> itemOpt =
                    menuItemRepository.findById(itemId);

            if (itemOpt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(Map.of("message", "No item found"));
            }

            MenuItem item = itemOpt.get();

            Optional<Restaurant> restaurantOpt =
                    restaurantRepository.findByOwnerId(
                            String.valueOf(user.get("_id"))
                    );

            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(Map.of("message", "NO Restaurant found"));
            }

            ObjectId restaurantId =
                    new ObjectId(restaurantOpt.get().getId());

            if (!restaurantId.equals(item.getRestaurantId())) {
                return ResponseEntity.status(404)
                        .body(Map.of("message", "NO Restaurant found"));
            }

            item.setAvailable(!item.getAvailable());
            item.setUpdatedAt(Instant.now());

            menuItemRepository.save(item);

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "Item Marked as " +
                                    (item.getAvailable()
                                            ? "available"
                                            : "unavailable"),
                            "item",
                            item
                    )
            );

        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();

            return ResponseEntity.status(500)
                    .body(Map.of("message", "Internal Server Error"));
        }
    }
}

