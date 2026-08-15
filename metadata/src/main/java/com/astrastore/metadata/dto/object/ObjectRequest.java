package com.astrastore.metadata.dto.object;

import com.astrastore.metadata.dto.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ObjectRequest(

                UUID id,

                @NotNull UUID bucketId,

                @NotBlank
                @Size(max = Validation.KEY_MAX)
                @Pattern(regexp = Validation.KEY_PATTERN, message = Validation.KEY_MESSAGE)
                String key,

                @NotNull @PositiveOrZero Long sizeBytes,

                @NotBlank @Size(max = 64) String checksum,

                @Size(max = 255) String contentType

) {
}
