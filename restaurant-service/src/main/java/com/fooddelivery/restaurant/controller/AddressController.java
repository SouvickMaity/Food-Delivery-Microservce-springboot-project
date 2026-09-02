package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.model.Address;
import com.fooddelivery.restaurant.repository.AddressRepository;
import com.fooddelivery.restaurant.security.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final AddressRepository addressRepository;
    private final AuthHelper authHelper;

    public AddressController(AddressRepository addressRepository, AuthHelper authHelper) {
        this.addressRepository = addressRepository;
        this.authHelper = authHelper;
    }

    // POST /api/address/new (isAuth)
    @PostMapping("/new")
    public ResponseEntity<?> addAddress(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            Object mobileObj = body.get("mobile");
            Object formattedAddressObj = body.get("formattedAddress");
            Object latitudeObj = body.get("latitude");
            Object longitudeObj = body.get("longitude");

            if (mobileObj == null || formattedAddressObj == null || latitudeObj == null || longitudeObj == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Please give all fields"));
            }

            Address address = new Address();
            address.setUserId(userId);
            address.setMobile(Long.valueOf(String.valueOf(mobileObj)));
            address.setFormattedAddress(String.valueOf(formattedAddressObj));
            address.setLocation(new GeoJsonPoint(
                    Double.parseDouble(String.valueOf(longitudeObj)),
                    Double.parseDouble(String.valueOf(latitudeObj))
            ));
            address.setCreatedAt(Instant.now());
            address.setUpdatedAt(Instant.now());

            addressRepository.save(address);

            return ResponseEntity.ok(Map.of("message", "Address Added successfully", "address", address));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // DELETE /api/address/{id} (isAuth)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAddress(HttpServletRequest request, @PathVariable String id) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            Optional<Address> addressOpt = addressRepository.findByIdAndUserId(id, userId);
            if (addressOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Address not found"));
            }

            addressRepository.delete(addressOpt.get());

            return ResponseEntity.ok(Map.of("message", "Address deleted Successfully"));
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/address/all (isAuth)
    @GetMapping("/all")
    public ResponseEntity<?> getMyAddresses(HttpServletRequest request) {
        try {
            Map<String, Object> user = authHelper.requireAuth(request);
            String userId = String.valueOf(user.get("_id"));

            List<Address> addresses = addressRepository.findByUserIdOrderByCreatedAtDesc(userId);

            return ResponseEntity.ok(addresses);
        } catch (AuthHelper.AuthException e) {
            throw e;
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }
}
