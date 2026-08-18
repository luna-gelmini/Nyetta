package com.ladyluh.nyetta.config;

import flux.api.ApiEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    private final Properties properties;
    private final TreeMap<Integer, String> xpRoleMappings = new TreeMap<>();
    private final Function<String, String> environmentLookup;

    public ConfigManager() throws Exception {
        this.properties = new Properties();
        this.environmentLookup = System::getenv;

        Path envFile = EnvFile.find();
        if (envFile != null) {
            properties.putAll(EnvFile.load(envFile));
            LOGGER.info("loaded .env from {}", envFile.toAbsolutePath());
        } else {
            LOGGER.warn("No .env found in {} (or parent). Copy .env.example to .env",
                    System.getProperty("user.dir"));
        }

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                Properties bundled = new Properties();
                bundled.load(input);
                for (String key : bundled.stringPropertyNames()) {
                    properties.putIfAbsent(key, bundled.getProperty(key));
                }
            }
        }

        loadXPRoleMappings();
    }

    public ConfigManager(Properties properties) {
        this(properties, key -> null);
    }

    ConfigManager(Properties properties, Function<String, String> environmentLookup) {
        this.properties = new Properties();
        this.properties.putAll(properties);
        this.environmentLookup = environmentLookup;
        loadXPRoleMappings();
    }

    public String getBotToken() {
        return required("BOT_TOKEN");
    }

    public void applyToApiEnvironment() {
        ApiEnvironment.configure(
                first("API_BASE_URL"),
                first("GATEWAY_URL"),
                first("MEDIA_CDN_URL"),
                first("APP_BASE_URL"));
        LOGGER.debug("api {} gateway {} cdn {} app {}",
                ApiEnvironment.getApiBaseUrl(),
                ApiEnvironment.getGatewayUrl(),
                ApiEnvironment.getMediaCdnUrl(),
                ApiEnvironment.getAppBaseUrl());
    }

    public String getLogChannelId() {
        return first("LOG_CHANNEL_ID");
    }

    public String getWelcomeChannelId() {
        return first("WELCOME_CHANNEL_ID");
    }

    public String getAutoAssignRoleId() {
        return first("AUTO_ASSIGN_ROLE_ID");
    }

    public String getHubChannelId() {
        return first("HUB_CHANNEL_ID");
    }

    public String getGuildId() {
        return first("GUILD_ID");
    }

    public String getTempChannelCategoryId() {
        return first("TEMP_CHANNEL_CATEGORY_ID");
    }

    public String getTempChannelNamePrefix() {
        return first("TEMP_CHANNEL_NAME_PREFIX");
    }

    public Integer getTempChannelUserLimit() {
        return Integer.parseInt(value("5", "TEMP_CHANNEL_USER_LIMIT"));
    }

    public Integer getTempChannelDefaultLock() {
        return Integer.parseInt(value("0", "TEMP_CHANNEL_DEFAULT_LOCKED"));
    }

    public String getCommandPrefix() {
        return value("!", "COMMAND_PREFIX");
    }

    public List<String> getBotStatuses() {
        String statusesString = first("BOT_STATUSES");
        if (statusesString == null || statusesString.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(statusesString.split(","))
                .map(String::trim)
                .filter(status -> !status.isEmpty())
                .collect(Collectors.toList());
    }

    public Map<Integer, String> getXPRoleMappings() {
        return Collections.unmodifiableMap(xpRoleMappings);
    }

    private String required(String... keys) {
        String resolved = value(null, keys);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException(
                    "Missing " + keys[0] + ". Copy .env.example to .env or set the environment variable.");
        }
        return resolved;
    }

    private String first(String... keys) {
        return value(null, keys);
    }

    private String value(String defaultValue, String... keys) {
        for (String key : keys) {
            String environmentValue = environmentLookup.apply(key);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return environmentValue;
            }
        }
        for (String key : keys) {
            if (properties.containsKey(key)) {
                String propertyValue = properties.getProperty(key);
                if (propertyValue != null && !propertyValue.isBlank()) {
                    return propertyValue;
                }
            }
        }
        return defaultValue;
    }

    private void loadXPRoleMappings() {
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("XP_ROLE_LEVEL_")) {
                continue;
            }
            try {
                int level = Integer.parseInt(key.substring("XP_ROLE_LEVEL_".length()));
                String roleId = properties.getProperty(key);
                if (roleId != null && !roleId.isBlank()) {
                    xpRoleMappings.put(level, roleId);
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("XP role key is not a level: {}", key);
            }
        }
        LOGGER.info("xp roles: {}", xpRoleMappings.size());
    }
}
