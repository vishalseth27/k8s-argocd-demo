package com.example.orderservice.client;

import com.example.orderservice.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
public class UserServiceClient {
    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private final RestTemplate restTemplate;

    @Value("${user-service.url}")
    private String userServiceUrl;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<UserDTO> getUserById(Long userId) {
        try {
            String url = userServiceUrl + "/api/users/" + userId;
            log.info("Calling user-service at: {}", url);

            UserDTO user = restTemplate.getForObject(url, UserDTO.class);
            log.info("Successfully fetched user: {}", user);
            return Optional.ofNullable(user);

        } catch (RestClientException e) {
            log.error("Error calling user-service for userId: {}", userId, e);
            return Optional.empty();
        }
    }
}
