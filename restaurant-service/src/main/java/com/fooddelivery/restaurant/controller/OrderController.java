package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.model.*;
import com.fooddelivery.restaurant.repository.*;
import com.fooddelivery.restaurant.security.AuthHelper;
import com.fooddelivery.restaurant.service.OrderPublisherService;
import com.fooddelivery.restaurant.service.RealtimeClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private static final List<String> ALLOWED_STATUSES = List.of("accepted", "preparing", "ready_for_rider");

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final AuthHelper authHelper;
    private final OrderPublisherService orderPublisherService;
    private final RealtimeClient realtimeClient;

    public OrderController(OrderRepository orderRepository, AddressRepository addressRepository,
                            CartRepository cartRepository, RestaurantRepository restaurantRepository,
                            MenuItemRepository menuItemRepository, AuthHelper authHelper,
                            OrderPublisherService orderPublisherService, RealtimeClient realtimeClient) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.cartRepository = cartRepository;
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.authHelper = authHelper;
        this.orderPublisherService = orderPublisherService;
        this.realtimeClient = realtimeClient;
    }

    // GET /api/order/myorder (isAuth)
    @GetMapping("/myorder")
    public ResponseEntity<?> getMyOrders(HttpServletRequest request) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            List<Order> orders = orderRepository.findByUserIdAndPaymentStatusOrderByCreatedAtDesc(
                    String.valueOf(user.get("_id")), "paid");
            return ResponseEntity.ok(Map.of("orders", orders));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/order/{id} (isAuth)
    @GetMapping("/{id}")
    public ResponseEntity<?> fetchSingleOrder(HttpServletRequest request, @PathVariable String id) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);

            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
            }

            Order order = orderOpt.get();
            if (!order.getUserId().equals(String.valueOf(user.get("_id")))) {
                return ResponseEntity.status(401).body(Map.of("message", "You are not allowed to view this order"));
            }

            return ResponseEntity.ok(order);
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // POST /api/order/new (isAuth)
    @PostMapping("/new")
    public ResponseEntity<?> createOrder(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            String paymentMethod = body.get("paymentMethod") != null ? String.valueOf(body.get("paymentMethod")) : null;
            String addressId = body.get("addressId") != null ? String.valueOf(body.get("addressId")) : null;

            if (addressId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Address is required"));
            }

            Optional<Address> addressOpt = addressRepository.findByIdAndUserId(addressId, userId);
            if (addressOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Address Not found"));
            }
            Address address = addressOpt.get();

            List<Cart> cartItems = cartRepository.findByUserId(userId);
            if (cartItems.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cart is empty"));
            }

            Cart firstCartItem = cartItems.get(0);
            String restaurantId = firstCartItem.getRestaurantId();

            Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "No restaurant with this id"));
            }
            Restaurant restaurant = restaurantOpt.get();

            if (!restaurant.isOpen()) {
                return ResponseEntity.status(404).body(Map.of("message", "Sorry this restaurant is closed for now"));
            }

            double[] addrCoords = address.getLocation().getCoordinates() != null
                    ? new double[]{address.getLocation().getX(), address.getLocation().getY()} : new double[]{0, 0};
            double[] restCoords = restaurant.getAutoLocation().getCoordinates();

            double distance = getDistanceKm(addrCoords[1], addrCoords[0], restCoords[1], restCoords[0]);

            double subtotal = 0;
            List<Order.OrderItem> orderItems = new ArrayList<>();

            for (Cart cart : cartItems) {
                Optional<MenuItem> itemOpt = menuItemRepository.findById(cart.getItemId());
                if (itemOpt.isEmpty()) {
                    throw new IllegalStateException("Invalid cart item");
                }
                MenuItem item = itemOpt.get();

                double itemTotal = item.getPrice() * cart.getQuauntity();
                subtotal += itemTotal;

                Order.OrderItem oi = new Order.OrderItem();
                oi.setItemId(item.getId());
                oi.setName(item.getName());
                oi.setPrice(item.getPrice());
                oi.setQuauntity(cart.getQuauntity());
                orderItems.add(oi);
            }

            double deliveryFee = subtotal < 250 ? 49 : 0;
            double platfromFee = 7;
            double totalAmount = subtotal + deliveryFee + platfromFee;

            Instant expiresAt = Instant.now().plusSeconds(15 * 60);

            double riderAmount = Math.ceil(distance) * 17;

            Order order = new Order();
            order.setUserId(userId);
            order.setRestaurantId(restaurantId);
            order.setRestaurantName(restaurant.getName());
            order.setRiderId(null);
            order.setDistance(distance);
            order.setRiderAmount(riderAmount);
            order.setItems(orderItems);
            order.setSubtotal(subtotal);
            order.setDeliveryFee(deliveryFee);
            order.setPlatfromFee(platfromFee);
            order.setTotalAmount(totalAmount);
            order.setAddressId(address.getId());

            Order.DeliveryAddress da = new Order.DeliveryAddress();
            da.setFromattedAddress(address.getFormattedAddress());
            da.setMobile(address.getMobile());
            da.setLatitude(addrCoords[1]);
            da.setLongitude(addrCoords[0]);
            order.setDeliveryAddress(da);

            order.setPaymentMethod(paymentMethod);
            order.setPaymentStatus("pending");
            order.setStatus("placed");
            order.setExpiresAt(expiresAt);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());

            orderRepository.save(order);

            cartRepository.deleteByUserId(userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Order created successfully",
                    "orderId", order.getId(),
                    "amount", totalAmount
            ));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/order/payment/{id}  (internal key, no isAuth)
    @GetMapping("/payment/{id}")
    public ResponseEntity<?> fetchOrderForPayment(HttpServletRequest request, @PathVariable String id) {
        try {
            authHelper.requireInternalKey(request);

            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
            }

            Order order = orderOpt.get();
            if (!"pending".equals(order.getPaymentStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Order already paid"));
            }

            return ResponseEntity.ok(Map.of(
                    "orderId", order.getId(),
                    "amount", order.getTotalAmount(),
                    "currency", "INR"
            ));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/order/restaurant/{restaurantId} (isAuth, isSeller)
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<?> fetchRestaurantOrders(HttpServletRequest request, @PathVariable String restaurantId,
                                                    @RequestParam(required = false) Integer limit) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            List<Order> orders = orderRepository.findByRestaurantIdAndPaymentStatusOrderByCreatedAtDesc(restaurantId, "paid");

            if (limit != null && limit > 0 && orders.size() > limit) {
                orders = orders.subList(0, limit);
            }

            return ResponseEntity.ok(Map.of("success", true, "count", orders.size(), "orders", orders));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/order/{orderId} (isAuth, isSeller)
    @PutMapping("/{orderId}")
    public ResponseEntity<?> updateOrderStatus(HttpServletRequest request, @PathVariable String orderId,
                                                @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            authHelper.requireSeller(user);

            String status = String.valueOf(body.get("status"));
            if (!ALLOWED_STATUSES.contains(status)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid order status"));
            }

            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
            }
            Order order = orderOpt.get();

            if (!"paid".equals(order.getPaymentStatus())) {
                return ResponseEntity.status(404).body(Map.of("message", "Order not completed"));
            }

            Optional<Restaurant> restaurantOpt = restaurantRepository.findById(order.getRestaurantId());
            if (restaurantOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Restaurant not found"));
            }
            Restaurant restaurant = restaurantOpt.get();

            if (!restaurant.getOwnerId().equals(String.valueOf(user.get("_id")))) {
                return ResponseEntity.status(401).body(Map.of("message", "You are not allowed to update this order"));
            }

            order.setStatus(status);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            realtimeClient.emit("order:update", "user:" + order.getUserId(),
                    Map.of("orderId", order.getId(), "status", order.getStatus()));

            if ("ready_for_rider".equals(status)) {
                Map<String, Object> eventData = new LinkedHashMap<>();
                eventData.put("orderId", order.getId());
                eventData.put("restaurantId", restaurant.getId());
                eventData.put("location", Map.of(
                        "type", restaurant.getAutoLocation().getType(),
                        "coordinates", restaurant.getAutoLocation().getCoordinates()
                ));
                orderPublisherService.publishEvent("ORDER_READY_FOR_RIDER", eventData);
            }

            return ResponseEntity.ok(Map.of("message", "Order status updated successfully", "order", order));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/order/assign/rider (internal key, no isAuth)
    @PutMapping("/assign/rider")
    public ResponseEntity<?> assignRiderToOrder(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            authHelper.requireInternalKey(request);

            String orderId = String.valueOf(body.get("orderId"));
            String riderId = String.valueOf(body.get("riderId"));
            String riderName = body.get("riderName") != null ? String.valueOf(body.get("riderName")) : null;
            String riderPhone = body.get("riderPhone") != null ? String.valueOf(body.get("riderPhone")) : null;

            Optional<Order> activeForRider = orderRepository.findByRiderIdAndStatusNot(riderId, "delivered");
            if (activeForRider.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "You already have an order"));
            }

            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
            }
            Order order = orderOpt.get();

            if (order.getRiderId() != null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Order already taken"));
            }

            order.setRiderId(riderId);
            order.setRiderName(riderName);
            order.setRiderPhone(riderPhone != null ? Long.valueOf(riderPhone) : null);
            order.setStatus("rider_assigned");
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            realtimeClient.emit("order:rider_assigned", "user:" + order.getUserId(), order);
            realtimeClient.emit("order:rider_assigned", "restaurant:" + order.getRestaurantId(), order);

            return ResponseEntity.ok(Map.of("message", "Rider Assigned Successfully", "success", true, "order", order));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/order/current/rider (internal key, no isAuth)
    @GetMapping("/current/rider")
    public ResponseEntity<?> getCurrentOrderForRider(HttpServletRequest request, @RequestParam(required = false) String riderId) {
        try {
            authHelper.requireInternalKey(request);

            if (riderId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Rider id is required"));
            }

            Optional<Order> orderOpt = orderRepository.findByRiderIdAndStatusNot(riderId, "delivered");
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
            }

            Order order = orderOpt.get();
            Map<String, Object> response = toMapWithPopulatedRestaurant(order);

            return ResponseEntity.ok(response);
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/order/update/status/rider (internal key, no isAuth)
    @PutMapping("/update/status/rider")
    public ResponseEntity<?> updateOrderStatusRider(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            authHelper.requireInternalKey(request);

            String orderId = String.valueOf(body.get("orderId"));
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
            }
            Order order = orderOpt.get();

            if ("rider_assigned".equals(order.getStatus())) {
                order.setStatus("picked_up");
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);

                realtimeClient.emit("order:rider_assigned", "restaurant:" + order.getRestaurantId(), order);
                realtimeClient.emit("order:rider_assigned", "user:" + order.getUserId(), order);

                return ResponseEntity.ok(Map.of("message", "Order updated Successfully"));
            }

            if ("picked_up".equals(order.getStatus())) {
                order.setStatus("delivered");
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);

                realtimeClient.emit("order:rider_assigned", "restaurant:" + order.getRestaurantId(), order);
                realtimeClient.emit("order:rider_assigned", "user:" + order.getUserId(), order);

                return ResponseEntity.ok(Map.of("message", "Order updated Successfully"));
            }

            return ResponseEntity.badRequest().body(Map.of("message", "Invalid order status"));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    private Map<String, Object> toMapWithPopulatedRestaurant(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_id", order.getId());
        map.put("userId", order.getUserId());
        map.put("restaurantName", order.getRestaurantName());
        map.put("riderId", order.getRiderId());
        map.put("riderName", order.getRiderName());
        map.put("riderPhone", order.getRiderPhone());
        map.put("riderAmount", order.getRiderAmount());
        map.put("distance", order.getDistance());
        map.put("items", order.getItems());
        map.put("subtotal", order.getSubtotal());
        map.put("deliveryFee", order.getDeliveryFee());
        map.put("platfromFee", order.getPlatfromFee());
        map.put("totalAmount", order.getTotalAmount());
        map.put("addressId", order.getAddressId());
        map.put("deliveryAddress", order.getDeliveryAddress());
        map.put("status", order.getStatus());
        map.put("paymentMethod", order.getPaymentMethod());
        map.put("paymentStatus", order.getPaymentStatus());
        map.put("createdAt", order.getCreatedAt());
        map.put("updatedAt", order.getUpdatedAt());
        restaurantRepository.findById(order.getRestaurantId()).ifPresentOrElse(
                r -> map.put("restaurantId", r),
                () -> map.put("restaurantId", order.getRestaurantId())
        );
        return map;
    }

    private double getDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(r * c * 100.0) / 100.0;
    }
}
