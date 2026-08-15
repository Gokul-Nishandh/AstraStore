package com.astrastore.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets {@code astrastore.monitoring.targets} bind from a comma-separated
 * environment variable as well as from the indexed YAML form.
 *
 * <p>Without this, the deployment's single {@code ASTRASTORE_MONITORING_TARGETS}
 * variable would have to be exploded into one variable per field per target.
 */
@Component
@ConfigurationPropertiesBinding
public class MonitoringTargetConverter implements Converter<String, MonitoringProperties.Target> {

    @Override
    public MonitoringProperties.Target convert(String source) {
        return MonitoringProperties.Target.parse(source);
    }
}
