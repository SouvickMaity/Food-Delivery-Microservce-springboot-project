package com.fooddelivery.utils.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UploadController {

    private final Cloudinary cloudinary;

    public UploadController(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    // POST /api/upload  -> body: { "buffer": "data:image/png;base64,...." }
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestBody Map<String, Object> body) {
        try {
            String buffer = String.valueOf(body.get("buffer"));

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(buffer, ObjectUtils.emptyMap());

            return ResponseEntity.ok(Map.of("url", result.get("secure_url")));
        } catch (Exception error) {
            return ResponseEntity.status(500).body(Map.of("message", error.getMessage()));
        }
    }
}
