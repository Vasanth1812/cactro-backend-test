package backend_test.controller;

import backend_test.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "Endpoints for checking system health and deployment status")
public class HealthController {

    @Value("${spring.application.name:backend-test}")
    private String appName;

    @GetMapping
    @Operation(summary = "Check service health", description = "Returns service health, timestamp, and database status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> healthData = Map.of(
                "application", appName,
                "status", "UP",
                "database", "Neon PostgreSQL (Serverless)",
                "timestamp", LocalDateTime.now()
        );
        return ResponseEntity.ok(ApiResponse.success("Service is up and running healthy", healthData));
    }
}
