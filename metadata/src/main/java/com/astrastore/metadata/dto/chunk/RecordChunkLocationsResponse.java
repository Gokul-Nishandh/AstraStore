package com.astrastore.metadata.dto.chunk;

import java.util.UUID;

public record RecordChunkLocationsResponse(

        UUID objectId,

        int chunksRecorded

) {
}
