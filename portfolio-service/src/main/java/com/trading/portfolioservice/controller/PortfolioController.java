package com.trading.portfolioservice.controller;

import com.trading.portfolioservice.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolioService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getPortfolio(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(userId));
    }
}
