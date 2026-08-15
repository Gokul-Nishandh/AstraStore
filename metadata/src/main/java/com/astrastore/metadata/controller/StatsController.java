package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.stats.StatsResponse;
import com.astrastore.metadata.dto.stats.StorageBreakdown;
import com.astrastore.metadata.service.BucketService;
import com.astrastore.metadata.service.ObjectService;
import com.astrastore.shared.security.AstraPrincipal;
import com.astrastore.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own totals.
 *
 * <p>Every figure is aggregated in the database over rows the caller owns. The
 * dashboard previously summed a paginated listing client-side, so the headline
 * numbers only ever described the first page.
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ObjectService objectService;
    private final BucketService bucketService;

    /**
     * The same totals, broken down for charting.
     *
     * <p>Separate from the scalar endpoint because it is materially more
     * expensive: the dashboard's headline tiles should not wait on two
     * grouped scans to paint.
     */
    @GetMapping("/breakdown")
    public ResponseEntity<StorageBreakdown> breakdown(
            @RequestParam(defaultValue = "30") int days) {

        AstraPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(objectService.storageBreakdown(principal.userId(), days));
    }

    @GetMapping
    public ResponseEntity<StatsResponse> stats() {
        AstraPrincipal principal = CurrentUser.require();
        Long userId = principal.userId();

        return ResponseEntity.ok(new StatsResponse(
                objectService.countActiveForUser(userId),
                objectService.sumActiveBytesForUser(userId),
                bucketService.countBucketsForUser(userId),
                objectService.countStarredForUser(userId),
                objectService.countTrashedForUser(userId)));
    }
}
