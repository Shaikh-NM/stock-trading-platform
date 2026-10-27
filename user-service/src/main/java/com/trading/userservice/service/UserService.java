package com.trading.userservice.service;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.entity.User;
import com.trading.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String USER_REGISTERED_TOPIC = "user.registered";

    public AuthResponse register(RegisterRequest request) {
        log.info("registering user: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException(
                    "Email already exists : {}" + request.getEmail());
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setWalletBalance(
                request.getInitialDeposit() == null ?
                        BigDecimal.valueOf(10000L) : request.getInitialDeposit()
        );

        User savedUser = userRepository.save(user);
        log.info("User registered: {}", savedUser.getId());

        Map<String, Object> userRegisteredEvent = new HashMap<>();
        userRegisteredEvent.put("userId", savedUser.getId());
        userRegisteredEvent.put("email", savedUser.getEmail());
        userRegisteredEvent.put("firstName", savedUser.getFirstName());
        userRegisteredEvent.put("lastName", savedUser.getLastName());
        userRegisteredEvent.put("walletBalance", savedUser.getWalletBalance());

        kafkaTemplate.send(USER_REGISTERED_TOPIC, userRegisteredEvent);

        String accessToken = generateAccessToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = generateRefreshToken(savedUser.getId());
        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("login attempt: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(
                        () -> new RuntimeException("User Not Found : {}" + request.getEmail())
                );

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid Credentials");
        }

        log.info("Login successful: {}", user.getId());

        String token = generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());
        return buildAuthResponse(user, token, refreshToken);
    }

    public UserResponse getUserById(String userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new RuntimeException("User Not Found : " + userId)
        );

        return mapToResponse(user);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUserId(user.getId());
        authResponse.setEmail(user.getEmail());
        authResponse.setFirstName(user.getFirstName());
        authResponse.setLastName(user.getLastName());
        authResponse.setWalletBalance(user.getWalletBalance());

        authResponse.setAccessToken(accessToken);
        authResponse.setRefreshToken(refreshToken);
        return authResponse;
    }

    private UserResponse mapToResponse(User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        userResponse.setEmail(user.getEmail());
        userResponse.setWalletBalance(user.getWalletBalance());
        userResponse.setStatus(user.getStatus());
        userResponse.setCreatedAt(user.getCreatedAt());
        return userResponse;
    }

    public UserResponse addFunds(String userId, BigDecimal amount) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new RuntimeException("User Not Found : " + userId)
        );

        BigDecimal newAmount = user.getWalletBalance().add(amount);
        user.setWalletBalance(newAmount);

        User savedUser = userRepository.save(user);

        log.info("Funds added: {} to user: {}", amount, savedUser.getId());
        return mapToResponse(savedUser);
    }

    public UserResponse deductFunds(String userId, BigDecimal amount) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new RuntimeException("User Not Found : " + userId)
        );

        if (user.getWalletBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        BigDecimal newAmount = user.getWalletBalance().subtract(amount);
        user.setWalletBalance(newAmount);

        User savedUser = userRepository.save(user);

        log.info("Funds deducted: {} to user: {}", amount, savedUser.getId());
        return mapToResponse(savedUser);
    }

    public UserResponse creditFunds(String userId, BigDecimal amount) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new RuntimeException("User Not Found : " + userId)
        );

        BigDecimal newAmount = user.getWalletBalance().add(amount);
        user.setWalletBalance(newAmount);

        User savedUser = userRepository.save(user);

        log.info("Funds added: {} to user: {}", amount, savedUser.getId());
        return mapToResponse(savedUser);
    }
}
