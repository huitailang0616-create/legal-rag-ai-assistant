package com.ailegal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "code", 200,
                "message", "ok",
                "data", "AI Legal Consulting System Backend"
        );
    }

    @GetMapping("/health/db")
    public Map<String, Object> healthDb() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            if (rs.next()) {
                int result = rs.getInt(1);
                return Map.of(
                        "code", 200,
                        "message", "ok",
                        "data", Map.of(
                                "database", "ai_legal",
                                "select1", result
                        )
                );
            }
        } catch (Exception e) {
            return Map.of(
                    "code", 500,
                    "message", "error",
                    "data", e.getMessage()
            );
        }
        return Map.of("code", 500, "message", "error", "data", "no result");
    }
}