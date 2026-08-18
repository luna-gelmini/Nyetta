package com.ladyluh.nyetta.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EnvFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvFile.class);

    private EnvFile() {
    }

    static Path find() {
        String explicit = System.getenv("NYETTA_ENV_FILE");
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit);
            return Files.isRegularFile(path) ? path : null;
        }

        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd.resolve(".env"));
        if (cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve(".env"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static Map<String, String> load(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            String pendingKey = null;
            StringBuilder pendingValue = new StringBuilder();
            boolean inQuotes = false;

            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine;
                if (pendingKey != null) {
                    pendingValue.append('\n').append(line);
                    if (countUnescapedQuotes(pendingValue.toString()) % 2 == 0) {
                        values.put(pendingKey, unquote(pendingValue.toString().trim()));
                        pendingKey = null;
                        pendingValue.setLength(0);
                    }
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                int eq = indexOfUnquotedEquals(trimmed);
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (isUnclosedQuote(value)) {
                    pendingKey = key;
                    pendingValue.append(value);
                    inQuotes = true;
                    continue;
                }
                values.put(key, unquote(value));
            }
            if (pendingKey != null && inQuotes) {
                LOGGER.warn("Ignoring unclosed quoted value for {} in {}", pendingKey, path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
        return values;
    }

    private static int indexOfUnquotedEquals(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '=' && !quoted) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isUnclosedQuote(String value) {
        return value.startsWith("\"") && countUnescapedQuotes(value) % 2 == 1;
    }

    private static int countUnescapedQuotes(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '"') {
                count++;
            }
        }
        return count;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
