# Food Delivery Backend — Spring Boot Conversion


## Services & ports (same defaults as the original `.env` files)

| Service            | Port | Responsibility                                                        |
|---------------------|------|-------------------------------------------------------------------------|
| `auth-service`       | 5000 | Google OAuth login, JWT issuance, `/api/location/reverse` proxy         |
| `restaurant-service`  | 5001 | Restaurants, menu items, cart, addresses, orders, analytics             |
| `utils-service`       | 5002 | Stripe checkout/verify, Cloudinary upload, payment RabbitMQ producer    |
| `realtime-service`    | 5004 | WebSocket hub (replaces Socket.IO) + internal `/emit` REST endpoint     |
| `rider-service`       | 5005 | Rider profile, availability, order acceptance, order-ready consumer     |
| `admin-service`       | 5008 | Restaurant/rider verification, dashboard & revenue analytics            |


## 4. Socket.IO Room Architecture
     Authenticated user → Socket.IO → JWT verification
     Rider/customer → user:{userId}
     Restaurant → restaurant:{restaurantId}
     Delivery tracking → order:{orderId}
     The order room is shared only by the rider and customer participating in that delivery.

## 5. Customer Flow
     Register/Login → Browse Restaurants → Select Restaurant → View Menu → Add Items to Cart → " "Checkout →
     Payment → Place Order → Receive Order Updates → Track Rider → Delivery Completed.
## 6.  Restaurant Flow
      Restaurant Login → Manage Restaurant Profile → Add/Update/Delete Food Items → Receive Incoming Orders → "
      "Accept/Reject → Prepare Food → Coordinate Rider → Update Order Status.
## 7. Cart Flow
   Customer selects food → Cart API → Store/update cart → Change quantity → Calculate subtotal → Checkout
   calculates subtotal + delivery fee + platform fee = total.
## 8. Payment and Order Creation
                                CUSTOMER
                            │
                            │ Place Order
                            ▼
                    RESTAURANT SERVICE
                            │
                            │ Create Order
                            ▼
                         MongoDB
                            │
                            │
                    Customer pays
                            ▼
                    PAYMENT SERVICE
                            │
                            │ Create Stripe Session
                            ▼
                         STRIPE
                            │
                            │ Payment completed
                            ▼
                    /stripe/verify
                            │
                            │ Verify payment
                            ▼
                PaymentProducerService
                            │
                            │ publishPaymentSuccess()
                            ▼
                     ┌──────────────┐
                     │   RABBITMQ   │
                     │              │
                     │   Exchange   │
                     │      ↓       │
                     │    Queue     │
                     └──────┬───────┘
                            │
                            ▼
                    PAYMENT CONSUMER
                            │
                            │ Payment Success Event
                            ▼
                    RESTAURANT SERVICE
                            │
                            │ Update order/payment
                            ▼
                         MongoDB
                            │
                            ▼
                    Order Confirmed
## 9. Rider Availability
       Rider opens dashboard → Go Online → Browser requests GPS → Rider Service receives availability and initial
       location → Rider availability/location is persisted. Continuous delivery GPS is handled separately by Socket.IO.
       
       Customer
       ↓
    Order Created
       ↓
    Restaurant Service
       ↓
    RabbitMQ
       ↓
    Rider Service
       ↓
    Find Available Riders
       ↓
    Realtime Service
       ↓
    Socket.IO
       ↓
    Rider Dashboard
    
## 10. Rider Order Notification
    Order requires rider → Available riders are identified → Realtime notification → RiderDashboard receives
    order:available → Incoming order appears → Optional sound notification → Rider accepts
## 11. Order Assignment
    Rider clicks Accept → Rider Service → Restaurant Service → Order is assigned to rider → Realtime order update
    can notify relevant clients.
## 12. . Order Status Lifecycle
    Typical lifecycle: pending → accepted/confirmed → preparing → rider_assigned → picked_up → delivered.
## 13.  Realtime Order Updates
     Backend status change → Realtime Service → Socket.IO → Customer/Restaurant/Rider UI. Customer listens to
     events such as order:update and order:rider_assigned and refreshes order data.
## 14. Live Rider GPS Tracking — Key Feature
    Tracking begins only when order.status === picked_up.
    Rider Dashboard → navigator.geolocation.watchPosition() → socket.emit("rider:location") → "
    "Socket.IO :5007 → order:{orderId} → Customer OrderPage → setRiderLocation() → UserOrderMap
    → Leaflet map.
    Example location payload:
    { "orderId": "6a97c6733ac1c77c12a3ad5e", "latitude": 22.7162062, "longitude": 88.491993375 }
    Important architectural decision: continuous GPS coordinates are NOT sent to Rider Service and are NOT written to
    MongoDB for every update. They are streamed directly through Socket.IO to the customer
## 15.  Live Tracking Room Flow
     Rider
     ■ join:order { orderId }
     ▼
     order:{orderId}
     ▲
     ■ join:order { orderId }
     Customer
     Rider GPS → rider:location → order:{orderId} → Customer
## 16. Customer Map Flow
    Customer receives latitude/longitude → riderLocation state updates → UserOrderMap receives riderLocation →
    Leaflet renders rider marker → OSRM/Leaflet Routing Machine calculates/display route to delivery location.
## 17. Delivery Completion
    Rider reaches customer → Rider updates order status → Restaurant Service persists delivered status → Customer
    receives order:update → Tracking effect stops because status is no longer picked_up → Customer sees delivered
    status.
## 18. RabbitMQ Flow
    Service A publishes an asynchronous event → RabbitMQ → Consumer Service processes event → Realtime
    notification/business action. This reduces tight synchronous coupling between services
## 19.  Internal Service Security
     Backend-to-backend internal endpoints use an internal service key, for example x-internal-key. The React browser
     should NOT directly call /api/v1/internal/emit for GPS tracking
## 20. Complete End-to-End Delivery Journey
    CUSTOMER
    ↓
    Login/Register
    ↓
    Browse Restaurant
    ↓
    Select Food
    ↓
    Cart
    ↓
    Checkout + Payment
    ↓
    Order Created
    ↓
    Restaurant Receives Order
    ↓
    Restaurant Accepts / Prepares
    ↓
    Rider Matching
    ↓
    Rider Gets Realtime Notification
    ↓
    Rider Accepts
    ↓
    Rider Assigned
    ↓
    PICKED UP
    ↓
    ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
    ■ Rider + Customer join order room ■
    ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
    ↓
    Rider GPS → Socket.IO → Customer
    ↓
    Customer Live Map
    ↓
    Rider Reaches Customer
    ↓
    Delivered
    ↓
    Order Completed

                             FOOD DELIVERY SYSTEM

     Customer
        │
        ├──────────────► Restaurant Service
        │                       │
        │                       ▼
        │                    MongoDB
        │
        └──────────────► Payment Service
                                │
                                ▼
                              Stripe
                                │
                                ▼
                       Payment Verification
                                │
                                ▼
                       Payment Producer
                                │
                                ▼
                           RABBITMQ
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
              Order Consumer          Other Consumers
                    │
                    ▼
           Restaurant/Order Service
                    │
                    ▼
                 MongoDB
                    │
                    ▼
              Order Processing
                    │
                    ▼
              Rider Service
                    │
                    ▼
           Available Rider
                    │
                    ▼
            Realtime Service
                    │
                    ▼
               Socket.IO
              /          \
             ▼            ▼
          Rider        Customer
         Browser        Browser
                           │
                           ▼
                       Live Map
    
