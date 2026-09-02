package com.example.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SocketEventHandler {

    private final SocketIOServer server;
    private final JwtUtil jwtUtil;

    public SocketEventHandler(SocketIOServer server, JwtUtil jwtUtil) {
        this.server = server;
        this.jwtUtil = jwtUtil;
    }

    @PostConstruct
    private void registerListeners() {

        // Connection
        server.addConnectListener(this::onConnect);

        // Disconnect
        server.addDisconnectListener(this::onDisconnect);

        // Order room
        server.addEventListener(
                "join:order",
                Map.class,
                this::joinOrder
        );

        server.addEventListener(
                "leave:order",
                Map.class,
                this::leaveOrder
        );

        // Rider realtime location
        server.addEventListener(
                "rider:location",
                Map.class,
                this::riderLocation
        );
    }

    // =========================================================
    // CONNECT
    // =========================================================

    private void onConnect(SocketIOClient client) {

        String token =
                client.getHandshakeData()
                        .getSingleUrlParam("token");

        Claims claims;

        try {
            claims = jwtUtil.verifyAndDecode(token);
        } catch (Exception e) {
            client.disconnect();
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> user =
                claims.get("user", Map.class);

        if (user == null) {
            client.disconnect();
            return;
        }

        // Store user information in socket
        client.set("user", user);

        String userId =
                String.valueOf(user.get("_id"));

        Object restaurantId =
                user.get("restaurantId");

        // Existing user room
        client.joinRoom("user:" + userId);

        // Existing restaurant room
        if (restaurantId != null) {
            client.joinRoom(
                    "restaurant:" + restaurantId
            );
        }

        System.out.println(
                "Decoded User: " + user
        );

        System.out.println(
                "User connected: " + userId
        );

        System.out.println(
                "Socket rooms: " + client.getAllRooms()
        );
    }

    // =========================================================
    // JOIN ORDER ROOM
    // =========================================================

    private void joinOrder(
            SocketIOClient client,
            Map<String, Object> data,
            com.corundumstudio.socketio.AckRequest ackRequest
    ) {

        if (data == null || data.get("orderId") == null) {
            System.out.println(
                    "❌ join:order -> orderId missing"
            );
            return;
        }

        String orderId =
                String.valueOf(data.get("orderId"));

        String room =
                "order:" + orderId;

        client.joinRoom(room);

        Map<String, Object> user =
                client.get("user");

        String userId =
                user != null
                        ? String.valueOf(user.get("_id"))
                        : "unknown";

        System.out.println(
                "👤 User " + userId
                        + " joined " + room
        );

        System.out.println(
                "Rooms: " + client.getAllRooms()
        );
    }

    // =========================================================
    // LEAVE ORDER ROOM
    // =========================================================

    private void leaveOrder(
            SocketIOClient client,
            Map<String, Object> data,
            com.corundumstudio.socketio.AckRequest ackRequest
    ) {

        if (data == null || data.get("orderId") == null) {
            return;
        }

        String orderId =
                String.valueOf(data.get("orderId"));

        String room =
                "order:" + orderId;

        client.leaveRoom(room);

        System.out.println(
                "👋 User left " + room
        );
    }

    // =========================================================
    // RIDER LOCATION
    // =========================================================

    private void riderLocation(
            SocketIOClient client,
            Map<String, Object> data,
            com.corundumstudio.socketio.AckRequest ackRequest
    ) {

        if (data == null) {
            return;
        }

        Object orderIdObj =
                data.get("orderId");

        Object latitudeObj =
                data.get("latitude");

        Object longitudeObj =
                data.get("longitude");

        if (orderIdObj == null
                || latitudeObj == null
                || longitudeObj == null) {

            System.out.println(
                    "❌ Invalid rider location data"
            );

            return;
        }

        String orderId =
                String.valueOf(orderIdObj);

        double latitude =
                Double.parseDouble(
                        String.valueOf(latitudeObj)
                );

        double longitude =
                Double.parseDouble(
                        String.valueOf(longitudeObj)
                );

        String room =
                "order:" + orderId;

        Map<String, Object> location =
                Map.of(
                        "orderId", orderId,
                        "latitude", latitude,
                        "longitude", longitude
                );

        Map<String, Object> user =
                client.get("user");

        String riderId =
                user != null
                        ? String.valueOf(user.get("_id"))
                        : "unknown";

        System.out.println(
                "📍 Rider " + riderId
                        + " location: "
                        + latitude + ", "
                        + longitude
                        + " → " + room
        );

        // Broadcast location to everyone in this order room
        server.getRoomOperations(room)
                .sendEvent(
                        "rider:location",
                        location
                );
    }

    // =========================================================
    // DISCONNECT
    // =========================================================

    private void onDisconnect(SocketIOClient client) {

        Map<String, Object> user =
                client.get("user");

        String userId =
                user != null
                        ? String.valueOf(user.get("_id"))
                        : client.getSessionId().toString();

        System.out.println(
                "User disconnected: " + userId
        );
    }
}

