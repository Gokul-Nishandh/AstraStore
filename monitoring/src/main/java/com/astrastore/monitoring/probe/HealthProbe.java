package com.astrastore.monitoring.probe;

import com.astrastore.monitoring.config.MonitoringProperties;

/** Issues one health request. Separated from the sweep so both can be tested alone. */
public interface HealthProbe {

    ProbeOutcome probe(MonitoringProperties.Target target);
}
