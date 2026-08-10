/**
 * CLI configuration loaded from ~/.astra/config.yaml.
 * Stores gateway URLs, default output format, and user preferences.
 * Singleton bean managed by Main entry point.
 */
package com.astrastore.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class AstraConfig {

    private static String configDir() {
        return System.getProperty("user.home") + "/.astra";
    }

    private static String configFile() {
        return configDir() + "/config.yaml";
    }

    private String gatewayUrl = "http://localhost:8080";
    private String authUrl = "http://localhost:8081";
    private String placementUrl = "http://localhost:8085";
    private String outputFormat = "table";
    private int timeoutSeconds = 30;

    private static AstraConfig instance;

    public static synchronized AstraConfig load() {
        if (instance != null) return instance;
        instance = new AstraConfig();
        Path configPath = Paths.get(configFile());
        if (Files.exists(configPath)) {
            try {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                instance = mapper.readValue(configPath.toFile(), AstraConfig.class);
                log.debug("Loaded config from {}", configPath);
            } catch (IOException e) {
                log.warn("Failed to load config from {}, using defaults: {}", configPath, e.getMessage());
            }
        }
        return instance;
    }

    public void save() throws IOException {
        Files.createDirectories(Paths.get(configDir()));
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writeValue(Paths.get(configFile()).toFile(), this);
        log.debug("Saved config to {}", configFile());
    }
}
