package com.ladyluh.nyetta.commands.impl;

import flux.api.entities.TargetType;
import flux.api.payload.channel.ChannelModifyPayload;
import flux.api.payload.permission.Permission;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.TemporaryChannelRecord;
import com.ladyluh.nyetta.database.UserChannelPreference;
import com.ladyluh.nyetta.services.TemporaryChannelTreeService;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RoomCommand implements Command {
    private final DatabaseManager dbManager;
    private final TemporaryChannelTreeService treeService;

    public RoomCommand(DatabaseManager dbManager, TemporaryChannelTreeService treeService) {
        this.dbManager = dbManager;
        this.treeService = treeService;
    }

    @Override
    public String getName() {
        return "room";
    }

    @Override
    public List<String> getAliases() {
        return List.of("vc", "temp");
    }

    @Override
    public String getDescription() {
        return "Manage your temporary voice channel.";
    }

    @Override
    public String getUsage() {
        return "room <limit|name|lock|unlock|allow|deny|autoowner|config>";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        String authorId = ctx.getAuthor().getId();

        if (ctx.getArgs().isEmpty() || ctx.getArgs().getFirst().isEmpty()) {
            return ctx.reply(usage(ctx, getUsage() + " [args]"));
        }
        String subCommand = ctx.getArgs().getFirst().toLowerCase();

        return switch (subCommand) {
            case "limit" -> handleLimit(ctx, guildId, authorId);
            case "name" -> handleName(ctx, guildId, authorId);
            case "lock" -> handleLock(ctx, guildId, authorId);
            case "unlock" -> handleUnlock(ctx, guildId, authorId);
            case "allow" -> handleAllow(ctx, guildId, authorId);
            case "deny" -> handleDeny(ctx, guildId, authorId);
            case "autoowner", "ao" -> handleAutoOwner(ctx, guildId, authorId);
            case "config" -> handleConfig(ctx, guildId, authorId);
            default -> ctx.reply(
                    "Unknown subcommand. Use: limit, name, lock, unlock, allow, deny, autoowner, config");
        };
    }

    private CompletableFuture<Void> handleLimit(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2)
            return ctx.reply(usage(ctx, "room limit <number>"));
        int limit;
        try {
            limit = Integer.parseInt(ctx.getArgs().get(1));
        } catch (NumberFormatException e) {
            return ctx.reply("Need a valid number.");
        }

        if (limit < 0 || limit > 99)
            return ctx.reply("Limit must be between 0 and 99.");

        CompletableFuture<Optional<TemporaryChannelRecord>> tempFuture = dbManager.getTemporaryChannelByOwner(guildId,
                authorId);
        CompletableFuture<Optional<UserChannelPreference>> prefsFuture = dbManager.getUserChannelPreference(guildId,
                authorId);

        int finalLimit = limit;
        return CompletableFuture.allOf(tempFuture, prefsFuture).thenCompose(v -> {
            UserChannelPreference prefs = prefsFuture.join().orElse(new UserChannelPreference(guildId, authorId));
            prefs.preferredUserLimit = finalLimit;
            CompletableFuture<Void> dbUpdate = dbManager.updateUserChannelPreference(guildId, authorId,
                    prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked, prefs.autoOwnerSwitching);

            Optional<TemporaryChannelRecord> channelOpt = tempFuture.join();
            if (channelOpt.isPresent()) {
                ChannelModifyPayload payload = new ChannelModifyPayload();
                payload.setUserLimit(finalLimit == 0 ? null : finalLimit);
                return dbUpdate
                        .thenCompose(x -> ctx.getClient().modifyChannel(channelOpt.get().channelId, payload))
                        .thenCompose(x -> ctx.reply("✅ Limit: " + (finalLimit == 0 ? "unlimited" : finalLimit)));
            }
            return dbUpdate.thenCompose(
                    x -> ctx.reply("✅ Saved: " + (finalLimit == 0 ? "unlimited" : finalLimit)));
        });
    }

    private CompletableFuture<Void> handleName(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2)
            return ctx.reply(usage(ctx, "room name <name>"));
        String nameTemplate = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));

        if (nameTemplate.length() > 80)
            return ctx.reply("Name too long (max 80 characters).");

        CompletableFuture<Optional<TemporaryChannelRecord>> tempFuture = dbManager.getTemporaryChannelByOwner(guildId,
                authorId);
        CompletableFuture<Optional<UserChannelPreference>> prefsFuture = dbManager.getUserChannelPreference(guildId,
                authorId);

        String finalTemplate = nameTemplate;
        return CompletableFuture.allOf(tempFuture, prefsFuture).thenCompose(v -> {
            UserChannelPreference prefs = prefsFuture.join().orElse(new UserChannelPreference(guildId, authorId));
            prefs.preferredName = finalTemplate;
            CompletableFuture<Void> dbUpdate = dbManager.updateUserChannelPreference(guildId, authorId,
                    prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked, prefs.autoOwnerSwitching);

            Optional<TemporaryChannelRecord> channelOpt = tempFuture.join();
            if (channelOpt.isPresent()) {
                String channelId = channelOpt.get().channelId;
                String baseName = finalTemplate.replace("%username%",
                        ctx.getAuthor().getGlobalName() != null ? ctx.getAuthor().getGlobalName()
                                : ctx.getAuthor().getUsername());

                return dbUpdate.thenCompose(x -> treeService.isLastChannel(guildId, channelId)
                        .thenCompose(isLast -> {
                            String finalName = treeService.applyTreePrefix(baseName, isLast);
                            if (finalName.length() > 100)
                                finalName = finalName.substring(0, 100);

                            ChannelModifyPayload payload = new ChannelModifyPayload();
                            payload.setName(finalName);
                            String displayName = finalName;
                            return ctx.getClient().modifyChannel(channelId, payload)
                                    .thenCompose(y -> ctx.reply("✅ Name: **" + displayName + "**"));
                        }));
            }
            return dbUpdate.thenCompose(x -> ctx.reply("✅ Saved template: **" + finalTemplate + "**"));
        });
    }

    private CompletableFuture<Void> handleLock(CommandContext ctx, String guildId, String authorId) {
        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ You don't have an active room.");
            String channelId = channelOpt.get().channelId;

            return dbManager.getUserChannelPreference(guildId, authorId).thenCompose(prefsOpt -> {
                UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, authorId));
                prefs.defaultLocked = 1;
                CompletableFuture<Void> updatePrefs = dbManager.updateUserChannelPreference(guildId, authorId,
                        prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked, prefs.autoOwnerSwitching);

                CompletableFuture<Void> denyEveryone = ctx.getClient().editChannelPermissions(channelId, guildId,
                        TargetType.ROLE, EnumSet.noneOf(Permission.class), EnumSet.of(Permission.CONNECT));
                CompletableFuture<Void> allowOwner = ctx.getClient().editChannelPermissions(channelId, authorId,
                        TargetType.MEMBER, EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL),
                        EnumSet.noneOf(Permission.class));

                return CompletableFuture.allOf(updatePrefs, denyEveryone, allowOwner)
                        .thenCompose(v -> ctx.reply("🔒 Room locked."));
            });
        });
    }

    private CompletableFuture<Void> handleUnlock(CommandContext ctx, String guildId, String authorId) {
        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ You don't have an active room.");
            String channelId = channelOpt.get().channelId;

            return dbManager.getUserChannelPreference(guildId, authorId).thenCompose(prefsOpt -> {
                UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, authorId));
                prefs.defaultLocked = 0;
                CompletableFuture<Void> updatePrefs = dbManager.updateUserChannelPreference(guildId, authorId,
                        prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked, prefs.autoOwnerSwitching);
                CompletableFuture<Void> allowEveryone = ctx.getClient().editChannelPermissions(channelId, guildId,
                        TargetType.ROLE, EnumSet.of(Permission.CONNECT), EnumSet.noneOf(Permission.class));

                return CompletableFuture.allOf(updatePrefs, allowEveryone)
                        .thenCompose(v -> ctx.reply("🔓 Room unlocked."));
            });
        });
    }

    private CompletableFuture<Void> handleAllow(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2 || !ctx.getArgs().get(1).matches("<@!?[0-9]+>")) {
            return ctx.reply(usage(ctx, "room allow @user"));
        }
        String targetId = ctx.getArgs().get(1).replaceAll("[<@!>]", "");

        if (targetId.equals(authorId))
            return ctx.reply("❌ You can't allow yourself.");

        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ You don't have an active room.");
            return ctx.getClient()
                    .editChannelPermissions(channelOpt.get().channelId, targetId, TargetType.MEMBER,
                            EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL),
                            EnumSet.noneOf(Permission.class))
                    .thenCompose(v -> ctx.reply("✅ Allowed <@" + targetId + ">."));
        });
    }

    private CompletableFuture<Void> handleDeny(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2 || !ctx.getArgs().get(1).matches("<@!?[0-9]+>")) {
            return ctx.reply(usage(ctx, "room deny @user"));
        }
        String targetId = ctx.getArgs().get(1).replaceAll("[<@!>]", "");

        if (targetId.equals(authorId))
            return ctx.reply("❌ You can't deny yourself.");

        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ You don't have an active room.");
            return ctx.getClient()
                    .editChannelPermissions(channelOpt.get().channelId, targetId, TargetType.MEMBER,
                            EnumSet.noneOf(Permission.class),
                            EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL))
                    .thenCompose(v -> ctx.reply("🚫 Blocked <@" + targetId + ">."));
        });
    }

    private CompletableFuture<Void> handleAutoOwner(CommandContext ctx, String guildId, String authorId) {
        return dbManager.getUserChannelPreference(guildId, authorId).thenCompose(prefsOpt -> {
            UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, authorId));
            int newStatus = (prefs.autoOwnerSwitching == null || prefs.autoOwnerSwitching == 0) ? 1 : 0;
            prefs.autoOwnerSwitching = newStatus;
            return dbManager
                    .updateUserChannelPreference(guildId, authorId, prefs.preferredUserLimit, prefs.preferredName,
                            prefs.defaultLocked, prefs.autoOwnerSwitching)
                    .thenCompose(v -> ctx.reply(
                            "Auto owner transfer: **" + (newStatus == 1 ? "ON" : "OFF") + "**"));
        });
    }

    private CompletableFuture<Void> handleConfig(CommandContext ctx, String guildId, String authorId) {
        String prefix = ctx.getConfig().getCommandPrefix();
        return dbManager.getUserChannelPreference(guildId, authorId).thenCompose(prefsOpt -> {
            UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, authorId));
            String name = prefs.preferredName != null ? prefs.preferredName : "(default)";
            String limit = prefs.preferredUserLimit != null ? String.valueOf(prefs.preferredUserLimit) : "0";
            return ctx.reply("Room config\nName: **" + name + "**\nLimit: **" + limit
                    + "**\nUse `" + prefix + "room name` / `" + prefix + "room limit` / `" + prefix
                    + "room lock` to change.");
        });
    }

    private static String usage(CommandContext ctx, String spec) {
        return "Usage: `" + ctx.getConfig().getCommandPrefix() + spec + "`";
    }
}
