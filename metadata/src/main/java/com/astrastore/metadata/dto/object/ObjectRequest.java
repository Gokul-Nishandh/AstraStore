package com.astrastore.metadata.dto.object;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ObjectRequest(

                UUID id,

                @NotNull UUID bucketId,

                @NotBlank String key,

                @NotNull Long sizeBytes,

                @NotBlank String checksum,

                String contentType

) {
}