package com.astrastore.monitoring.dto;

import java.time.Instant;
import java.util.List;

public record ServicesResponse(
        String window,
        Instant generatedAt,
        List<ServiceHealth> services
) {
}
