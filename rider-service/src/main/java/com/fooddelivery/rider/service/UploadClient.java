package com.fooddelivery.rider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@Service
public class UploadClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.utils-service-url}")
    private String utilsServiceUrl;

    public String uploadAndGetUrl(MultipartFile file) throws Exception {
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        String dataUri = "data:" + contentType + ";base64," + base64;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("buffer", dataUri);

        Map<?, ?> response = restTemplate.postForObject(
                utilsServiceUrl + "/api/upload",
                new HttpEntity<>(body, headers),
                Map.class
        );

        return response != null ? String.valueOf(response.get("url")) : null;
    }
}
