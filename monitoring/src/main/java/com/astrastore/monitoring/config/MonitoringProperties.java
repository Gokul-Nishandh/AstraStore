package com.astrastore.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the availability tracker needs, driven from configuration.
 *
 * <p>The probe list lives here rather than in code so that adding a service,
 * renaming one or moving it to a different port is a deployment change. The
 * defaults in {@code application.yaml} describe the Docker Compose network.
 */
@ConfigurationProperties(prefix = "astrastore.monitoring")
public class MonitoringProperties {

    private long probeIntervalMs = 15_000;

    /**
     * Connect and read timeout for a single probe. Deliberately far shorter
     * than the interval: a service that hangs rather than refusing must not
     * be able to hold the sweep open past the next tick.
     */
    private long probeTimeoutMs = 2_000;

    /**
     * Consecutive failures required before an incident is opened, and
     * consecutive successes before it is closed. A single dropped packet or a
     * GC pause on the far side is not an outage, and an incident recorded for
     * one is a lie that stays in the history forever.
     */
    private int failureThreshold = 2;
    private int successThreshold = 2;

    /** Samples older than this are deleted; the table is append-only otherwise. */
    private int retentionDays = 7;

    private long retentionIntervalMs = 3_600_000;

    /**
     * A service whose newest sample is older than this many probe intervals is
     * reported UNKNOWN rather than carried forward at its last value — after a
     * monitoring restart we genuinely do not know what happened in the gap.
     */
    private int staleAfterIntervals = 3;

    /**
     * Floors below which a window's uptime percentage is withheld (reported as
     * {@code null}) instead of computed. A cluster that has been observed for
     * ninety seconds cannot honestly be described as "99.9% over 24h".
     */
    private int minSamplesForUptime = 5;
    private long minObservedSecondsForUptime = 300;

    private int maxSparklineBuckets = 60;

    private String healthPath = "/actuator/health";

    private List<Target> targets = new ArrayList<>();

    public Duration probeInterval() {
        return Duration.ofMillis(probeIntervalMs);
    }

    public Duration probeTimeout() {
        return Duration.ofMillis(probeTimeoutMs);
    }

    /** How old a sample may be before the service is considered unobserved. */
    public Duration staleAfter() {
        return Duration.ofMillis(probeIntervalMs * Math.max(1, staleAfterIntervals));
    }

    public long getProbeIntervalMs() {
        return probeIntervalMs;
    }

    public void setProbeIntervalMs(long probeIntervalMs) {
        this.probeIntervalMs = probeIntervalMs;
    }

    public long getProbeTimeoutMs() {
        return probeTimeoutMs;
    }

    public void setProbeTimeoutMs(long probeTimeoutMs) {
        this.probeTimeoutMs = probeTimeoutMs;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public int getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(int successThreshold) {
        this.successThreshold = successThreshold;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getRetentionIntervalMs() {
        return retentionIntervalMs;
    }

    public void setRetentionIntervalMs(long retentionIntervalMs) {
        this.retentionIntervalMs = retentionIntervalMs;
    }

    public int getStaleAfterIntervals() {
        return staleAfterIntervals;
    }

    public void setStaleAfterIntervals(int staleAfterIntervals) {
        this.staleAfterIntervals = staleAfterIntervals;
    }

    public int getMinSamplesForUptime() {
        return minSamplesForUptime;
    }

    public void setMinSamplesForUptime(int minSamplesForUptime) {
        this.minSamplesForUptime = minSamplesForUptime;
    }

    public long getMinObservedSecondsForUptime() {
        return minObservedSecondsForUptime;
    }

    public void setMinObservedSecondsForUptime(long minObservedSecondsForUptime) {
        this.minObservedSecondsForUptime = minObservedSecondsForUptime;
    }

    public int getMaxSparklineBuckets() {
        return maxSparklineBuckets;
    }

    public void setMaxSparklineBuckets(int maxSparklineBuckets) {
        this.maxSparklineBuckets = maxSparklineBuckets;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public List<Target> getTargets() {
        return targets;
    }

    public void setTargets(List<Target> targets) {
        this.targets = targets == null ? new ArrayList<>() : targets;
    }

    /** One probed endpoint. */
    public static class Target {

        private String id;
        private String displayName;
        private String kind;
        private String baseUrl;

        /** Overrides {@link MonitoringProperties#getHealthPath()} for this target. */
        private String healthPath;

        public Target() {
        }

        public Target(String id, String displayName, String kind, String baseUrl) {
            this.id = id;
            this.displayName = displayName;
            this.kind = kind;
            this.baseUrl = baseUrl;
        }

        /**
         * Parses the compact {@code id|display name|base url} form.
         *
         * <p>The deployment passes the target list as a single environment
         * variable, which can only carry flat strings; the indexed form in
         * {@code application.yaml} binds the same type field by field.
         */
        public static Target parse(String spec) {
            if (spec == null || spec.isBlank()) {
                throw new IllegalArgumentException("Monitoring target must not be blank");
            }
            String[] parts = spec.split("\\|", -1);
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                        "Monitoring target must be 'id|display name|base url', got: " + spec);
            }
            Target target = new Target();
            target.setId(parts[0].trim());
            if (parts.length == 2) {
                // Two fields means the display name was omitted, not the URL.
                target.setBaseUrl(parts[1].trim());
            } else {
                target.setDisplayName(parts[1].trim());
                target.setBaseUrl(parts[2].trim());
                if (parts.length > 3 && !parts[3].isBlank()) {
                    target.setKind(parts[3].trim());
                }
            }
            return target;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id == null ? null : id.trim();
        }

        /** Falls back to the id so a target is never rendered nameless. */
        public String getDisplayName() {
            return (displayName == null || displayName.isBlank()) ? id : displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Inferred from the id when unset, so the compact string form does not
         * have to carry a field that is derivable.
         */
        public String getKind() {
            if (kind != null && !kind.isBlank()) {
                return kind;
            }
            if (id == null) {
                return "service";
            }
            if (id.startsWith("storage-node")) {
                return "storage-node";
            }
            if (id.contains("gateway")) {
                return "gateway";
            }
            return "service";
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? null : baseUrl.trim();
        }

        public String getHealthPath() {
            return healthPath;
        }

        public void setHealthPath(String healthPath) {
            this.healthPath = healthPath;
        }

        /** Absolute health URL, tolerating a trailing slash on the base URL. */
        public String healthUrl(String defaultHealthPath) {
            String path = (healthPath == null || healthPath.isBlank()) ? defaultHealthPath : healthPath;
            String base = baseUrl == null ? "" : baseUrl;
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return base + path;
        }
    }
}
