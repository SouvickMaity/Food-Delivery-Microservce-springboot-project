package com.example.realtime.controller;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.realtime.dto.EmitRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Equivalent of routes/internal.js -> router.post("/emit", ...)
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    private final SocketIOServer socketIOServer;

    @Value("${internal.service.key}")
    private String internalServiceKey;

    public InternalController(SocketIOServer socketIOServer) {
        this.socketIOServer = socketIOServer;
    }

    @PostMapping("/emit")
    public ResponseEntity<Map<String, Object>> emit(
            @RequestHeader(value = "x-internal-key", required = false) String internalKey,
            @RequestBody EmitRequest request
    ) {
        if (internalKey == null || !internalKey.equals(internalServiceKey)) {
            return ResponseEntity.status(403).body(Map.of("message", "Forbidden"));
        }

        if (request.getEvent() == null || request.getRoom() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "event and room are required"));
        }

        socketIOServer.getRoomOperations(request.getRoom())
                .sendEvent(request.getEvent(), request.getPayload() != null ? request.getPayload() : Map.of());

        return ResponseEntity.ok(Map.of("success", true));
    }
}
