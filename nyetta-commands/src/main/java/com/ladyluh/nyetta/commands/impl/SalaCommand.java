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

public class SalaCommand implements Command {
    private final DatabaseManager dbManager;
    private final TemporaryChannelTreeService treeService;

    public SalaCommand(DatabaseManager dbManager, TemporaryChannelTreeService treeService) {
        this.dbManager = dbManager;
        this.treeService = treeService;
    }

    @Override
    public String getName() {
        return "sala";
    }

    @Override
    public List<String> getAliases() {
        return List.of("minhasala", "vc");
    }

    @Override
    public String getDescription() {
        return "Gerencia seu canal de voz temporário.";
    }

    @Override
    public String getUsage() {
        return "sala <subcomando>";
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
            return ctx.reply("Uso: `!sala <limite/nome/trancar/destrancar/permitir/proibir/autoowner/config> [args]`");
        }
        String subCommand = ctx.getArgs().getFirst().toLowerCase();

        return switch (subCommand) {
            case "limite" -> handleLimite(ctx, guildId, authorId);
            case "nome" -> handleNome(ctx, guildId, authorId);
            case "trancar" -> handleTrancar(ctx, guildId, authorId);
            case "destrancar" -> handleDestrancar(ctx, guildId, authorId);
            case "permitir" -> handlePermitir(ctx, guildId, authorId);
            case "proibir" -> handleProibir(ctx, guildId, authorId);
            case "autoowner", "ao" -> handleAutoOwner(ctx, guildId, authorId);
            case "config" -> handleConfig(ctx, guildId, authorId);
            default -> ctx.reply(
                    "Subcomando desconhecido. Use: limite, nome, trancar, destrancar, permitir, proibir, autoowner, config");
        };
    }

    private CompletableFuture<Void> handleLimite(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2)
            return ctx.reply("Uso: `!sala limite <numero>`");
        int limit;
        try {
            limit = Integer.parseInt(ctx.getArgs().get(1));
        } catch (NumberFormatException e) {
            return ctx.reply("Informe um número válido.");
        }

        if (limit < 0 || limit > 99)
            return ctx.reply("O limite deve ser entre 0 e 99.");

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
                        .thenCompose(x -> ctx.reply("✅ Limite: " + (finalLimit == 0 ? "ilimitado" : finalLimit)));
            }
            return dbUpdate.thenCompose(
                    x -> ctx.reply("✅ Preferência salva: " + (finalLimit == 0 ? "ilimitado" : finalLimit)));
        });
    }

    private CompletableFuture<Void> handleNome(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2)
            return ctx.reply("Uso: `!sala nome <nome>`");
        String nameTemplate = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));

        if (nameTemplate.length() > 80)
            return ctx.reply("Nome muito longo (max 80 caracteres).");

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
                                    .thenCompose(y -> ctx.reply("✅ Nome: **" + displayName + "**"));
                        }));
            }
            return dbUpdate.thenCompose(x -> ctx.reply("✅ Template salvo: **" + finalTemplate + "**"));
        });
    }

    private CompletableFuture<Void> handleTrancar(CommandContext ctx, String guildId, String authorId) {
        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ Você não tem uma sala ativa.");
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
                        .thenCompose(v -> ctx.reply("🔒 Sala trancada!"));
            });
        });
    }

    private CompletableFuture<Void> handleDestrancar(CommandContext ctx, String guildId, String authorId) {
        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ Você não tem uma sala ativa.");
            String channelId = channelOpt.get().channelId;

            return dbManager.getUserChannelPreference(guildId, authorId).thenCompose(prefsOpt -> {
                UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, authorId));
                prefs.defaultLocked = 0;
                CompletableFuture<Void> updatePrefs = dbManager.updateUserChannelPreference(guildId, authorId,
                        prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked, prefs.autoOwnerSwitching);
                CompletableFuture<Void> allowEveryone = ctx.getClient().editChannelPermissions(channelId, guildId,
                        TargetType.ROLE, EnumSet.of(Permission.CONNECT), EnumSet.noneOf(Permission.class));

                return CompletableFuture.allOf(updatePrefs, allowEveryone)
                        .thenCompose(v -> ctx.reply("🔓 Sala destrancada!"));
            });
        });
    }

    private CompletableFuture<Void> handlePermitir(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2 || !ctx.getArgs().get(1).matches("<@!?[0-9]+>")) {
            return ctx.reply("Uso: `!sala permitir @usuario`");
        }
        String targetId = ctx.getArgs().get(1).replaceAll("[<@!>]", "");

        if (targetId == null)
            return ctx.reply("Informe o usuário.");
        if (targetId.equals(authorId))
            return ctx.reply("❌ Você não pode permitir a si mesmo.");

        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ Você não tem uma sala ativa.");
            return ctx.getClient()
                    .editChannelPermissions(channelOpt.get().channelId, targetId, TargetType.MEMBER,
                            EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL),
                            EnumSet.noneOf(Permission.class))
                    .thenCompose(v -> ctx.reply("✅ <@" + targetId + "> permitido!"));
        });
    }

    private CompletableFuture<Void> handleProibir(CommandContext ctx, String guildId, String authorId) {
        if (ctx.getArgs().size() < 2 || !ctx.getArgs().get(1).matches("<@!?[0-9]+>")) {
            return ctx.reply("Uso: `!sala proibir @usuario`");
        }
        String targetId = ctx.getArgs().get(1).replaceAll("[<@!>]", "");

        if (targetId == null)
            return ctx.reply("Informe o usuário.");
        if (targetId.equals(authorId))
            return ctx.reply("❌ Você não pode proibir a si mesmo.");

        return dbManager.getTemporaryChannelByOwner(guildId, authorId).thenCompose(channelOpt -> {
            if (channelOpt.isEmpty())
                return ctx.reply("❌ Você não tem uma sala ativa.");
            return ctx.getClient()
                    .editChannelPermissions(channelOpt.get().channelId, targetId, TargetType.MEMBER,
                            EnumSet.noneOf(Permission.class),
                            EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL))
                    .thenCompose(v -> ctx.reply("🚫 <@" + targetId + "> bloqueado!"));
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
                            "Transferência automática: **" + (newStatus == 1 ? "✅ ATIVADA" : "❌ DESATIVADA") + "**"));
        });
    }

    private CompletableFuture<Void> handleConfig(CommandContext ctx, String guildId, String authorId) {
        return dbManager.getUserChannelPreference(guildId, authorId).thenCompose(prefsOpt -> {
            UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, authorId));
            String nome = prefs.preferredName != null ? prefs.preferredName : "(padrão)";
            String limite = prefs.preferredUserLimit != null ? String.valueOf(prefs.preferredUserLimit) : "0";
            return ctx.reply("⚙️ Config da sala\nNome: **" + nome + "**\nLimite: **" + limite
                    + "**\nUse `!sala nome` / `!sala limite` / `!sala trancar` para alterar.");
        });
    }
}
