package com.ladyluh.nyetta;

import flux.Flux;

import flux.api.FluxClient;
import flux.api.gateway.GatewayIntent;
import com.ladyluh.nyetta.cache.VoiceStateCacheManager;
import com.ladyluh.nyetta.commands.CommandManager;
import com.ladyluh.nyetta.config.ConfigManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.listeners.GuildEventListener;
import com.ladyluh.nyetta.listeners.LogEventListener;
import com.ladyluh.nyetta.listeners.MessageEventListener;
import com.ladyluh.nyetta.listeners.TemporaryChannelListener;
import com.ladyluh.nyetta.listeners.ChannelNameEnforcementListener;
import com.ladyluh.nyetta.services.TemporaryChannelTreeService;
import com.ladyluh.nyetta.services.XPRoleService;
import com.ladyluh.nyetta.services.WelcomeImageService;
import com.ladyluh.nyetta.cache.SnipeCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Nyetta {
    private static final Logger LOGGER = LoggerFactory.getLogger(Nyetta.class);
    private final ConfigManager config;
    private final FluxClient fluxClient;
    private final DatabaseManager databaseManager;
    private final CommandManager commandManager;
    private final VoiceStateCacheManager voiceStateCacheManager;
    private final XPRoleService xpRoleService;
    private final TemporaryChannelTreeService treeService;
    private final WelcomeImageService welcomeImageService;
    private final SnipeCache snipeCache;
    private final ScheduledExecutorService statusRotator;

    public Nyetta() throws Exception {
        this.config = new ConfigManager();
        this.config.applyToApiEnvironment();
        this.fluxClient = Flux.createDefault();
        this.databaseManager = new DatabaseManager("nyetta.db");
        this.voiceStateCacheManager = new VoiceStateCacheManager();

        this.xpRoleService = new XPRoleService(fluxClient, config);
        this.treeService = new TemporaryChannelTreeService(fluxClient, databaseManager);
        this.welcomeImageService = new WelcomeImageService();
        this.snipeCache = new SnipeCache();

        this.commandManager = new CommandManager(fluxClient, config, databaseManager,
                voiceStateCacheManager, treeService, snipeCache);
        this.statusRotator = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nyetta-Status-Rotator");
            t.setDaemon(true);
            return t;
        });
        setupListeners();
    }

    public static void main(String[] args) {
        try {
            Nyetta bot = new Nyetta();
            bot.start();
        } catch (Exception e) {
            LOGGER.error("failed to initialize", e);
        }
    }

    private void setupListeners() {
        fluxClient.addEventListener(new GuildEventListener(fluxClient, databaseManager));
        fluxClient.addEventListener(new LogEventListener(fluxClient, databaseManager, welcomeImageService, snipeCache));
        fluxClient.addEventListener(
                new MessageEventListener(fluxClient, databaseManager, commandManager, xpRoleService,
                        config.getCommandPrefix(), snipeCache));

        TemporaryChannelListener tempListener = new TemporaryChannelListener(config, fluxClient, databaseManager,
                voiceStateCacheManager, treeService);
        fluxClient.addEventListener(tempListener);
        fluxClient.addEventListener(new ChannelNameEnforcementListener(fluxClient, databaseManager, treeService));
    }

    public void start() {
        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILDS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_VOICE_STATES);

        String guildId = config.getGuildId();
        LOGGER.info("Nyetta 0.1.0 · Fluxer4J 1.0.1 · pid {}", ProcessHandle.current().pid());
        LOGGER.info("prefix {} · guild {} · hub {} · category {}",
                config.getCommandPrefix(),
                blankToDash(guildId),
                blankToDash(config.getHubChannelId()),
                blankToDash(config.getTempChannelCategoryId()));
        fluxClient.login(config.getBotToken(), intents)
                .thenRun(() -> {
                    LOGGER.info("gateway connected");
                    startStatusRotation();
                    startPeriodicTreeVerification();
                })
                .exceptionally(throwable -> {
                    LOGGER.error("failed to start", throwable);
                    return null;
                });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("shutting down");
            if (statusRotator != null && !statusRotator.isShutdown()) {
                statusRotator.shutdownNow();
            }
            treeService.stopPeriodicVerification();

            fluxClient.shutdown();
            databaseManager.shutdown();
            LOGGER.info("bye");
        }));
    }

    private void startStatusRotation() {
        final List<String> statuses = config.getBotStatuses();
        if (statuses.isEmpty()) {
            LOGGER.warn("No bot statuses configured in BOT_STATUSES. Status rotation disabled.");
            return;
        }
        final AtomicInteger index = new AtomicInteger(0);

        statusRotator.scheduleAtFixedRate(() -> {
            try {
                String statusText = statuses.get(index.getAndIncrement() % statuses.size());
                fluxClient.setActivity(FluxClient.ActivityType.PLAYING, statusText);
                LOGGER.debug("Bot status changed to: Playing {}", statusText);
            } catch (Exception e) {
                LOGGER.error("Failed to rotate bot status", e);
            }
        }, 5, 25, TimeUnit.SECONDS);
    }

    private void startPeriodicTreeVerification() {
        String guildId = config.getGuildId();
        if (guildId == null || guildId.isEmpty()) {
            LOGGER.warn("GUILD_ID is not configured. Periodic prefix checks disabled.");
            return;
        }
        treeService.startPeriodicVerification(guildId);
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
