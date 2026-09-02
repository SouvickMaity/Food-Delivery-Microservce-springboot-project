package com.fooddelivery.utils.controller;

import com.fooddelivery.utils.service.PaymentProducerService;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentProducerService paymentProducerService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.restaurant-service-url}")
    private String restaurantServiceUrl;

    @Value("${app.internal-service-key}")
    private String internalServiceKey;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PaymentController(PaymentProducerService paymentProducerService) {
        this.paymentProducerService = paymentProducerService;
    }

    // POST /api/payment/stripe/create
    @PostMapping("/stripe/create")
    public ResponseEntity<?> payWithStripe(@RequestBody Map<String, Object> body) {
        try {
            String orderId = String.valueOf(body.get("orderId"));

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-internal-key", internalServiceKey);

            ResponseEntity<Map> orderResp = restTemplate.exchange(
                    restaurantServiceUrl + "/api/order/payment/" + orderId,
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    Map.class
            );

            Map<?, ?> data = orderResp.getBody();
            if (data == null) {
                return ResponseEntity.status(500).body(Map.of("message", "Stripe payment failed"));
            }

            Number amount = (Number) data.get("amount");
            long unitAmount = Math.round(amount.doubleValue() * 100);

            SessionCreateParams params = SessionCreateParams.builder()
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("inr")
                                                    .setUnitAmount(unitAmount)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Tomato Food Order")
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("orderId", orderId)
                    .setSuccessUrl(frontendUrl + "/ordersuccess?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(frontendUrl + "/checkout")
                    .build();

            Session session = Session.create(params);

            return ResponseEntity.ok(Map.of("url", session.getUrl()));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Stripe payment failed"));
        }
    }

    // POST /api/payment/stripe/verify
    @PostMapping("/stripe/verify")
    public ResponseEntity<?> verifyStripe(@RequestBody Map<String, Object> body) {
        try {
            String sessionId = String.valueOf(body.get("sessionId"));

            Session session = Session.retrieve(sessionId);

            if (session == null) {
                return ResponseEntity.status(400).body(Map.of("message", "Payment verification failed"));
            }

            String orderId = session.getMetadata() != null ? session.getMetadata().get("orderId") : null;

            if (orderId == null) {
                return ResponseEntity.status(400).body(Map.of("message", "Order ID not found in Stripe session"));
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", orderId);
            payload.put("paymentId", sessionId);
            payload.put("provider", "stripe");

            paymentProducerService.publishPaymentSuccess(payload);

            return ResponseEntity.ok(Map.of("message", "Payment verified successfully"));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Stripe payment verification failed"));
        }
    }
}
