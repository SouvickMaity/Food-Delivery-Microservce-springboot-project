package com.fooddelivery.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GoogleAuthService {

    @Value("${app.google.client-id}")
    private String clientId;

    @Value("${app.google.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Exchanges an authorization code for Google tokens then fetches the user's profile.
     * Mirrors: oauth2client.getToken(code) + GET userinfo?access_token=...
     */
    public Map<String, Object> exchangeCodeForProfile(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", "postmessage");
        form.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(form, headers);

        Map<?, ?> tokenResponse = restTemplate.postForObject(
                "https://oauth2.googleapis.com/token", tokenRequest, Map.class);

        String accessToken = (String) tokenResponse.get("access_token");

        Map<?, ?> userInfo = restTemplate.getForObject(
                "https://www.googleapis.com/oauth2/v1/userinfo?alt=json&access_token=" + accessToken,
                Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) userInfo;
        return result;
    }
}
