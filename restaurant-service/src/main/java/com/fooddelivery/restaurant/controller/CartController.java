package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.model.Cart;
import com.fooddelivery.restaurant.model.MenuItem;
import com.fooddelivery.restaurant.model.Restaurant;
import com.fooddelivery.restaurant.repository.CartRepository;
import com.fooddelivery.restaurant.repository.MenuItemRepository;
import com.fooddelivery.restaurant.repository.RestaurantRepository;
import com.fooddelivery.restaurant.security.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartRepository cartRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final AuthHelper authHelper;

    public CartController(CartRepository cartRepository, MenuItemRepository menuItemRepository,
                           RestaurantRepository restaurantRepository, AuthHelper authHelper) {
        this.cartRepository = cartRepository;
        this.menuItemRepository = menuItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.authHelper = authHelper;
    }

    // POST /api/cart/add (isAuth)
    @PostMapping("/add")
    public ResponseEntity<?> addToCart(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);

            String userId = String.valueOf(user.get("_id"));

            String restaurantId = String.valueOf(body.get("restaurantId"));
            String itemId = String.valueOf(body.get("itemId"));

            if (!ObjectId.isValid(restaurantId) || !ObjectId.isValid(itemId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid restaurant and item id"));
            }

            Optional<Cart> otherRestaurantCart = cartRepository.findByUserIdAndRestaurantIdNot(userId, restaurantId);
            if (otherRestaurantCart.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "You can order from only one restaurant at a time. Please clear your cart first to add items from this restaurant."));
            }

            Optional<Cart> existing = cartRepository.findByUserIdAndRestaurantIdAndItemId(userId, restaurantId, itemId);
            Cart cartItem;
            if (existing.isPresent()) {
                cartItem = existing.get();
                cartItem.setQuauntity(cartItem.getQuauntity() + 1);
            } else {
                cartItem = new Cart();
                cartItem.setUserId(userId);
                cartItem.setRestaurantId(restaurantId);
                cartItem.setItemId(itemId);
                cartItem.setQuauntity(1);
                cartItem.setCreatedAt(Instant.now());
            }
            cartItem.setUpdatedAt(Instant.now());
            cartRepository.save(cartItem);

            return ResponseEntity.ok(Map.of("message", "Item added to cart", "cart", cartItem));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/cart/all (isAuth)
    @GetMapping("/all")
    public ResponseEntity<?> fetchMyCart(HttpServletRequest request) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            List<Cart> cartItems = cartRepository.findByUserId(userId);

            double subtotal = 0;
            int cartLength = 0;

            List<Map<String, Object>> enriched = new ArrayList<>();

            for (Cart cartItem : cartItems) {
                Optional<MenuItem> itemOpt = menuItemRepository.findById(cartItem.getItemId());
                Optional<Restaurant> restOpt = restaurantRepository.findById(cartItem.getRestaurantId());

                if (itemOpt.isEmpty()) continue;
                MenuItem item = itemOpt.get();

                subtotal += item.getPrice() * cartItem.getQuauntity();
                cartLength += cartItem.getQuauntity();

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("_id", cartItem.getId());
                row.put("userId", cartItem.getUserId());
                row.put("quauntity", cartItem.getQuauntity());
                row.put("itemId", item);
                restOpt.ifPresent(r -> row.put("restaurantId", r));
                enriched.add(row);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "cartLength", cartLength,
                    "subtotal", subtotal,
                    "cart", enriched
            ));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/cart/inc (isAuth)
    @PutMapping("/inc")
    public ResponseEntity<?> incrementCartItem(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));
            String itemId = body.get("itemId") != null ? String.valueOf(body.get("itemId")) : null;
            System.out.print(body);
            if (itemId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid request"));
            }

            Optional<Cart> cartOpt = cartRepository.findByUserIdAndItemId(userId, itemId);
            if (cartOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Item not found"));
            }

            Cart cartItem = cartOpt.get();
            cartItem.setQuauntity(cartItem.getQuauntity() + 1);
            cartItem.setUpdatedAt(Instant.now());
            cartRepository.save(cartItem);

            return ResponseEntity.ok(Map.of("message", "Quantity increased", "cartItem", cartItem));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/cart/dec (isAuth)
    @PutMapping("/dec")
    public ResponseEntity<?> decrementCartItem(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));
            String itemId = body.get("itemId") != null ? String.valueOf(body.get("itemId")) : null;

            if (itemId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid request"));
            }

            Optional<Cart> cartOpt = cartRepository.findByUserIdAndItemId(userId, itemId);
            if (cartOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Item not found"));
            }

            Cart cartItem = cartOpt.get();

            if (cartItem.getQuauntity() == 1) {
                cartRepository.deleteByUserIdAndItemId(userId, itemId);
                return ResponseEntity.ok(Map.of("message", "Item removed from cart"));
            }

            cartItem.setQuauntity(cartItem.getQuauntity() - 1);
            cartItem.setUpdatedAt(Instant.now());
            cartRepository.save(cartItem);

            return ResponseEntity.ok(Map.of("message", "Quantity decreased", "cartItem", cartItem));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // DELETE /api/cart/clear (isAuth)
    @DeleteMapping("/clear")
    public ResponseEntity<?> clearCart(HttpServletRequest request) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            cartRepository.deleteByUserId(userId);

            return ResponseEntity.ok(Map.of("message", "Cart cleared successfully"));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }
}
