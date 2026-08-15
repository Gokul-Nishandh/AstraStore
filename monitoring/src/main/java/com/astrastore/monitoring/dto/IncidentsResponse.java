package com.astrastore.monitoring.dto;

import java.util.List;

public record IncidentsResponse(
        List<IncidentDto> incidents
) {
}
