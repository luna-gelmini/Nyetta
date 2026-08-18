package com.ladyluh.nyetta.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @Test
    void loadsKeysFromProperties() {
        Properties properties = new Properties();
        properties.setProperty("BOT_TOKEN", "token-123");
        properties.setProperty("LOG_CHANNEL_ID", "log-456");
        properties.setProperty("WELCOME_CHANNEL_ID", "welcome-789");

        ConfigManager configManager = new ConfigManager(properties);

        assertEquals("token-123", configManager.getBotToken());
        assertEquals("log-456", configManager.getLogChannelId());
        assertEquals("welcome-789", configManager.getWelcomeChannelId());
    }

    @Test
    void environmentVariableWinsOverProperties() {
        Properties properties = new Properties();
        properties.setProperty("BOT_TOKEN", "file-token");

        ConfigManager configManager = new ConfigManager(properties, key ->
                "BOT_TOKEN".equals(key) ? "env-token" : null);

        assertEquals("env-token", configManager.getBotToken());
    }

    @Test
    void defaultsCommandPrefixToBangWhenUnset() {
        ConfigManager configManager = new ConfigManager(new Properties());

        assertEquals("!", configManager.getCommandPrefix());
    }

    @Test
    void missingTokenFailsFast() {
        ConfigManager configManager = new ConfigManager(new Properties());

        assertThrows(IllegalStateException.class, configManager::getBotToken);
    }

    @Test
    void loadsBotStatusesFromQuotedList() {
        Properties properties = new Properties();
        properties.setProperty("BOT_STATUSES", "a, b");

        ConfigManager configManager = new ConfigManager(properties);

        assertEquals(List.of("a", "b"), configManager.getBotStatuses());
    }

    @Test
    void parsesDotEnvFile(@TempDir Path tempDir) throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                # comment
                BOT_TOKEN="secret-token"
                export COMMAND_PREFIX=?
                TEMP_CHANNEL_NAME_PREFIX="%username%'s room"
                """);

        Map<String, String> values = EnvFile.load(env);

        assertEquals("secret-token", values.get("BOT_TOKEN"));
        assertEquals("?", values.get("COMMAND_PREFIX"));
        assertEquals("%username%'s room", values.get("TEMP_CHANNEL_NAME_PREFIX"));
    }

    @Test
    void bundledConfigResourceHasNoSecrets() throws Exception {
        String bundledConfig = readBundledConfigResource();

        assertFalse(bundledConfig.contains("BOT_TOKEN="));
        assertFalse(bundledConfig.contains("GelpQH"));
    }

    @Test
    void exampleEnvIsATemplateWithoutLiveSecrets() throws Exception {
        String example = Files.readString(locateExampleEnv(), StandardCharsets.UTF_8);

        Set<String> expectedKeys = Set.of(
                "BOT_TOKEN=",
                "LOG_CHANNEL_ID=",
                "WELCOME_CHANNEL_ID=",
                "AUTO_ASSIGN_ROLE_ID=",
                "HUB_CHANNEL_ID=",
                "TEMP_CHANNEL_CATEGORY_ID=",
                "COMMAND_PREFIX="
        );
        for (String key : expectedKeys) {
            assertTrue(example.contains(key), "missing " + key);
        }
        assertFalse(example.contains("GelpQH"));
        assertTrue(example.contains("your_fluxer_bot_token_here"));
    }

    private static String readBundledConfigResource() throws Exception {
        try (InputStream input = ConfigManagerTest.class.getClassLoader().getResourceAsStream("config.properties")) {
            assertNotNull(input, "Expected bundled config.properties resource to exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path locateExampleEnv() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path repositoryRootCandidate = workingDirectory.resolve(".env.example");
        if (Files.exists(repositoryRootCandidate)) {
            return repositoryRootCandidate;
        }
        Path parent = workingDirectory.resolve("..").resolve(".env.example").normalize();
        assertTrue(Files.exists(parent), "Expected .env.example at repo root");
        return parent;
    }
}
