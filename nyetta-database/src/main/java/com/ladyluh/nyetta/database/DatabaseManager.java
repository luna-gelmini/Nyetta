package com.ladyluh.nyetta.database;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
    private final String dbUrl;
    private final ExecutorService dbExecutor;

    public DatabaseManager(String dbFileName) {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            if (dataDir.mkdirs()) {
                LOGGER.info("Created 'data' directory.");
            } else {
                LOGGER.error("Failed to create 'data' directory.");
            }
        }
        this.dbUrl = "jdbc:sqlite:data/" + dbFileName;

        this.dbExecutor = Executors.newFixedThreadPool(5, new ThreadFactory() {
            private int count = 0;

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r, "Nyetta-DB-Pool-Thread-" + count++);
                thread.setDaemon(true);
                return thread;
            }
        });

        initializeDatabase();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    private void initializeDatabase() {
        String createUserXPTableSQL = """
                CREATE TABLE IF NOT EXISTS user_xp (
                    guild_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    xp INTEGER DEFAULT 0,
                    level INTEGER DEFAULT 0,
                    last_message_timestamp INTEGER DEFAULT 0,
                    PRIMARY KEY (guild_id, user_id)
                );""";

        String createTempChannelsTableSQL = """
                CREATE TABLE IF NOT EXISTS temporary_channels (
                    channel_id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    owner_user_id TEXT NOT NULL,
                    created_at_timestamp INTEGER NOT NULL,
                    locked INTEGER DEFAULT 0
                );""";

        String createUserChannelPrefsTableSQL = """
                CREATE TABLE IF NOT EXISTS user_channel_preferences (
                    guild_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    preferred_user_limit INTEGER,
                    preferred_name_template TEXT,
                    locked INTEGER DEFAULT 0,
                    auto_owner_switching INTEGER DEFAULT 1,
                    PRIMARY KEY (guild_id, user_id)
                );""";

        String createGuildConfigsTableSQL = """
                CREATE TABLE IF NOT EXISTS guild_configs (
                    guild_id TEXT PRIMARY KEY,
                    log_channel_id TEXT,
                    welcome_channel_id TEXT,
                    auto_assign_role_id TEXT,
                    temp_hub_channel_id TEXT,
                    temp_channel_category_id TEXT,
                    temp_channel_name_prefix TEXT,
                    default_temp_channel_user_limit INTEGER,
                    default_temp_channel_lock INTEGER
                );""";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createUserXPTableSQL);
            stmt.execute(createTempChannelsTableSQL);
            stmt.execute(createUserChannelPrefsTableSQL);
            stmt.execute(createGuildConfigsTableSQL);
            for (String table : new String[] {
                    "known_users", "server_topics", "inside_jokes", "friend_expertise",
                    "nyetta_facts", "conversation_threads", "dynamic_instructions",
                    "interaction_metadata", "user_relationships"}) {
                stmt.execute("DROP TABLE IF EXISTS " + table);
            }
            LOGGER.info("SQLite tables checked/created.");
        } catch (SQLException e) {
            LOGGER.error("Failed to initialize SQLite:", e);
        }
    }

    public CompletableFuture<Optional<GuildConfig>> getGuildConfig(String guildId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM guild_configs WHERE guild_id = ?";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return Optional.of(new GuildConfig(
                            guildId,
                            rs.getString("log_channel_id"),
                            rs.getString("welcome_channel_id"),
                            rs.getString("auto_assign_role_id"),
                            rs.getString("temp_hub_channel_id"),
                            rs.getString("temp_channel_category_id"),
                            rs.getString("temp_channel_name_prefix"),
                            rs.getObject("default_temp_channel_user_limit", Integer.class),
                            rs.getObject("default_temp_channel_lock", Integer.class)));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load GuildConfig for guild {}:", guildId, e);
                throw new RuntimeException("DB Error fetching guild config for guild " + guildId, e);
            }
            return Optional.empty();
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateGuildConfig(GuildConfig config) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT OR REPLACE INTO guild_configs (
                        guild_id, log_channel_id, welcome_channel_id, auto_assign_role_id,
                        temp_hub_channel_id, temp_channel_category_id, temp_channel_name_prefix,
                        default_temp_channel_user_limit, default_temp_channel_lock
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);""";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int i = 1;
                pstmt.setString(i++, config.guildId);
                pstmt.setString(i++, config.logChannelId);
                pstmt.setString(i++, config.welcomeChannelId);
                pstmt.setString(i++, config.autoAssignRoleId);
                pstmt.setString(i++, config.tempHubChannelId);
                pstmt.setString(i++, config.tempChannelCategoryId);
                pstmt.setString(i++, config.tempChannelNamePrefix);

                if (config.defaultTempChannelUserLimit != null) {
                    pstmt.setInt(i++, config.defaultTempChannelUserLimit);
                } else {
                    pstmt.setNull(i++, Types.INTEGER);
                }

                if (config.defaultTempChannelLock != null) {
                    pstmt.setInt(i++, config.defaultTempChannelLock);
                } else {
                    pstmt.setNull(i++, Types.INTEGER);
                }

                pstmt.executeUpdate();
                LOGGER.info("Guild {} config upserted in DB.", config.guildId);
            } catch (SQLException e) {
                LOGGER.error("Failed to update GuildConfig for guild {}:", config.guildId, e);
                throw new RuntimeException("DB Error updating guild config for guild " + config.guildId, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<UserXP> getUserXP(String guildId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT guild_id, user_id, xp, level, last_message_timestamp FROM user_xp WHERE guild_id = ? AND user_id = ?";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return new UserXP(
                            rs.getString("guild_id"),
                            rs.getString("user_id"),
                            rs.getInt("xp"),
                            rs.getInt("level"),
                            rs.getLong("last_message_timestamp"));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load UserXP for guild {} user {}:", guildId, userId, e);
                throw new RuntimeException("DB Error fetching XP for user " + userId, e);
            }
            return new UserXP(guildId, userId, 0, 0, 0);
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateUserXP(String guildId, String userId, int newXp, int newLevel,
            long lastMessageTimestamp) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT OR REPLACE INTO user_xp (guild_id, user_id, xp, level, last_message_timestamp)
                    VALUES (?, ?, ?, ?, ?);""";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setInt(3, newXp);
                pstmt.setInt(4, newLevel);
                pstmt.setLong(5, lastMessageTimestamp);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to upsert UserXP for guild {} user {}:", guildId, userId, e);
                throw new RuntimeException("DB Error updating XP for user " + userId, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<List<UserXP>> getTopXPUsers(String guildId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT guild_id, user_id, xp, level, last_message_timestamp FROM user_xp WHERE guild_id = ? ORDER BY level DESC, xp DESC LIMIT ?";
            List<UserXP> topUsers = new ArrayList<>();
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setInt(2, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    topUsers.add(new UserXP(
                            rs.getString("guild_id"),
                            rs.getString("user_id"),
                            rs.getInt("xp"),
                            rs.getInt("level"),
                            rs.getLong("last_message_timestamp")));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load top XP users for guild {}:", guildId, e);
                throw new RuntimeException("DB Error fetching top XP users for guild " + guildId, e);
            }
            return topUsers;
        }, dbExecutor);
    }

    public CompletableFuture<Void> addTemporaryChannel(String channelId, String guildId, String ownerUserId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO temporary_channels (channel_id, guild_id, owner_user_id, created_at_timestamp) VALUES (?, ?, ?, ?)";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                pstmt.setString(2, guildId);
                pstmt.setString(3, ownerUserId);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
                LOGGER.info("Temporary channel {} added to DB.", channelId);
            } catch (SQLException e) {
                LOGGER.error("Failed to add temporary channel {} to DB:", channelId, e);
                throw new RuntimeException("DB Error adding temporary channel " + channelId, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Optional<TemporaryChannelRecord>> getTemporaryChannel(String channelId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT channel_id, guild_id, owner_user_id, created_at_timestamp, locked FROM temporary_channels WHERE channel_id = ?";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return Optional.of(new TemporaryChannelRecord(
                            rs.getString("channel_id"),
                            rs.getString("guild_id"),
                            rs.getString("owner_user_id"),
                            rs.getLong("created_at_timestamp"),
                            rs.getInt("locked")));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load temporary channel {} from DB:", channelId, e);
                throw new RuntimeException("DB Error fetching temporary channel " + channelId, e);
            }
            return Optional.empty();
        }, dbExecutor);
    }

    public CompletableFuture<Optional<TemporaryChannelRecord>> getTemporaryChannelByOwner(String guildId,
            String ownerUserId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT channel_id, guild_id, owner_user_id, created_at_timestamp, locked " +
                    "FROM temporary_channels WHERE guild_id = ? AND owner_user_id = ?";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, ownerUserId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return Optional.of(new TemporaryChannelRecord(
                            rs.getString("channel_id"),
                            rs.getString("guild_id"),
                            rs.getString("owner_user_id"),
                            rs.getLong("created_at_timestamp"),
                            rs.getInt("locked")));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load temporary channel by owner {} in guild {}:", ownerUserId, guildId, e);
                throw new RuntimeException("DB Error fetching temporary channel by owner " + ownerUserId, e);
            }
            return Optional.empty();
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateTemporaryChannelOwner(String channelId, String newOwnerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE temporary_channels SET owner_user_id = ? WHERE channel_id = ?";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newOwnerId);
                pstmt.setString(2, channelId);
                pstmt.executeUpdate();
                LOGGER.info("Temporary channel {} owner updated to {} in DB.", channelId, newOwnerId);
            } catch (SQLException e) {
                LOGGER.error("Failed to update temporary channel {} owner in DB:", channelId, e);
                throw new RuntimeException("DB Error updating temporary channel owner for " + channelId, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> removeTemporaryChannel(String channelId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM temporary_channels WHERE channel_id = ?";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    LOGGER.info("Temporary channel {} removed from DB.", channelId);
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to remove temporary channel {} from DB:", channelId, e);
                throw new RuntimeException("DB Error removing temporary channel " + channelId, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<List<TemporaryChannelRecord>> getTemporaryChannelsByGuild(String guildId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT channel_id, guild_id, owner_user_id, created_at_timestamp, locked " +
                    "FROM temporary_channels WHERE guild_id = ? ORDER BY created_at_timestamp ASC";
            List<TemporaryChannelRecord> channels = new ArrayList<>();
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    channels.add(new TemporaryChannelRecord(
                            rs.getString("channel_id"),
                            rs.getString("guild_id"),
                            rs.getString("owner_user_id"),
                            rs.getLong("created_at_timestamp"),
                            rs.getInt("locked")));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load temporary channels for guild {}:", guildId, e);
                throw new RuntimeException("DB Error fetching temporary channels for guild " + guildId, e);
            }
            return channels;
        }, dbExecutor);
    }

    public CompletableFuture<Optional<UserChannelPreference>> getUserChannelPreference(String guildId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT preferred_user_limit, preferred_name_template, locked, auto_owner_switching FROM user_channel_preferences WHERE guild_id = ? AND user_id = ?";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    Integer limit = rs.getObject("preferred_user_limit", Integer.class);
                    String nameTemplate = rs.getString("preferred_name_template");
                    Integer defaultLocked = rs.getObject("locked", Integer.class);
                    Integer autoSwitch = rs.getObject("auto_owner_switching", Integer.class);
                    return Optional.of(
                            new UserChannelPreference(guildId, userId, limit, nameTemplate, defaultLocked, autoSwitch));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load UserChannelPreference for guild {} user {}:", guildId, userId, e);
                throw new RuntimeException("DB Error fetching user channel preference for user " + userId, e);
            }
            return Optional.empty();
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateUserChannelPreference(String guildId, String userId,
            Integer preferredUserLimit, String preferredNameTemplate, Integer defaultLocked,
            Integer autoOwnerSwitching) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT OR REPLACE INTO user_channel_preferences
                    (guild_id, user_id, preferred_user_limit, preferred_name_template, locked, auto_owner_switching)
                    VALUES (?, ?, ?, ?, ?, ?);""";
            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                if (preferredUserLimit != null)
                    pstmt.setInt(3, preferredUserLimit);
                else
                    pstmt.setNull(3, Types.INTEGER);
                if (preferredNameTemplate != null)
                    pstmt.setString(4, preferredNameTemplate);
                else
                    pstmt.setNull(4, Types.VARCHAR);
                if (defaultLocked != null)
                    pstmt.setInt(5, defaultLocked);
                else
                    pstmt.setNull(5, Types.INTEGER);
                if (autoOwnerSwitching != null)
                    pstmt.setInt(6, autoOwnerSwitching);
                else
                    pstmt.setNull(6, Types.INTEGER);
                pstmt.executeUpdate();
                LOGGER.info("Channel preferences for user {} in guild {} upserted.", userId, guildId);
            } catch (SQLException e) {
                LOGGER.error("Failed to upsert UserChannelPreference for guild {} user {}:", guildId, userId,
                        e);
                throw new RuntimeException("DB Error updating user channel preference for user " + userId, e);
            }
        }, dbExecutor);
    }

    public void shutdown() {
        LOGGER.info("Shutting down the database executor...");
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warn("Database executor did not finish in time. Forcing shutdown.");
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Database executor shutdown interrupted.");
        }
    }
}
