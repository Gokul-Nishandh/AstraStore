package com.astrastore.metadata.dto.bucket;

import com.astrastore.metadata.dto.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param name    the bucket name, unique per owner
 * @param ownerId <b>ignored.</b> Accepted only so existing clients that still
 *                send it do not get a 400; the owner is always the
 *                authenticated caller. Letting a client name its own owner is
 *                exactly how one account would write into another's namespace.
 */
public record BucketRequest(

        @NotBlank
        @Size(max = Validation.BUCKET_NAME_MAX)
        @Pattern(regexp = Validation.BUCKET_NAME_PATTERN, message = Validation.BUCKET_NAME_MESSAGE)
        String name,

        @Deprecated
        UUID ownerId

) {
}
