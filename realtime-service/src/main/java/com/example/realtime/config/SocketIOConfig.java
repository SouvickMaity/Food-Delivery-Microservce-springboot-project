package com.example.realtime.config;

import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.realtime.socket.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Equivalent of `new Server(server, { cors: { origin: "*" } })` plus the
 * `io.use((socket, next) => {...})` JWT auth middleware.
 *
 * IMPORTANT: the original client sends the token via
 * `socket.handshake.auth.token`. netty-socketio's AuthorizationListener only
 * has access to the HTTP handshake (query params / headers), not the
 * socket.io-client `auth` payload. Update the client to send the token as a
 * query param instead:
 *
 *   io(url, { query: { token } })
 */
@Configuration
public class SocketIOConfig {

    @Value("${socketio.port}")
    private int port;

    private final JwtUtil jwtUtil;

    public SocketIOConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(port);

        // equivalent of cors: { origin: "*" }
        config.setOrigin("*");

        config.setAuthorizationListener(new AuthorizationListener() {
            @Override
            public AuthorizationResult getAuthorizationResult(HandshakeData handshakeData) {
                String token = handshakeData.getSingleUrlParam("token");

                if (token == null || token.isBlank()) {
                    return AuthorizationResult.FAILED_AUTHORIZATION;
                }

                try {
                    jwtUtil.verifyAndDecode(token);
                    return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;
                } catch (Exception e) {
                    System.out.println("\u274C Socket auth failed: " + e.getMessage());
                    return AuthorizationResult.FAILED_AUTHORIZATION;
                }
            }
        });

        return new SocketIOServer(config);
    }
}
