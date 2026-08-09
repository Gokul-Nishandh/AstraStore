package com.astrastore.metadata.dto.bucket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BucketRequest(

        @NotBlank String name,

        UUID ownerId

) {
}