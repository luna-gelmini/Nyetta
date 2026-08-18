package com.ladyluh.nyetta.commands.impl;

import flux.api.payload.permission.Permission;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.GuildConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCommand.class);
    private final DatabaseManager dbManager;

    public ConfigCommand(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("cfg", "settings");
    }

    @Override
    public String getDescription() {
        return "Define configurações para este servidor.";
    }

    @Override
    public String getUsage() {
        return "config <set/show/audit> <key> [value]";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    public CompletableFuture<Void> execute(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        if (guildId == null) {
            return ctx.reply("Este comando só pode ser usado em um servidor.");
        }

        return ctx.getClient().getGuildMember(guildId, ctx.getAuthor().getId())
                .thenCompose(member -> {
                    if (member == null) {
                        return ctx.reply("Não consegui verificar suas permissões neste servidor.");
                    }
                    return member.hasPermission(Permission.ADMINISTRATOR)
                            .thenCompose(hasAdminPerm -> {
                                if (!hasAdminPerm) {
                                    return ctx.reply("Você não tem permissão para usar este comando. (Requer permissão de ADMINISTRADOR)");
                                }

                                if (ctx.getArgs().isEmpty()) {
                                    return showHelp(ctx);
                                }
                                String subCommand = ctx.getArgs().getFirst().toLowerCase();
                                List<String> cmdArgs = ctx.getArgs().subList(1, ctx.getArgs().size());

                                return switch (subCommand) {
                                    case "show" -> showConfig(ctx, cmdArgs);
                                    case "set" -> setConfig(ctx, cmdArgs);
                                    case "audit" -> auditConfig(ctx);
                                    default -> showHelp(ctx);
                                };
                            });
                });
    }

    private CompletableFuture<Void> showHelp(CommandContext ctx) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Configuração do Servidor")
                .setDescription("Use `!config set <chave> <valor>` para alterar as configurações ou `!config audit` para revisar o estado atual.")
                .setColor(new Color(0x2F3136))
                .addField("Canais",
                        """
                        `log_channel` - Canal de logs
                        `welcome_channel` - Canal de boas-vindas
                        `temp_hub_channel` - Canal de voz para criar salas
                        """, true)
                .addField("Cargos & Categorias",
                        """
                        `auto_assign_role` - Cargo inicial
                        `temp_channel_category` - Categoria das salas
                        """, true)
                .addField("Salas Temporárias",
                        """
                        `temp_channel_name_prefix` - Prefixo do nome
                        `default_temp_channel_user_limit` - Limite de usuários
                        `default_temp_channel_lock` - Trancar ao criar?
                        """, false)
                .setFooter("Nyetta Config", ctx.getClient().getSelfUser().getEffectiveAvatarUrl())
                .setTimestamp(OffsetDateTime.now());
        return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenAccept(v -> {
        });
    }

    private CompletableFuture<Void> auditConfig(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        return dbManager.getGuildConfig(guildId)
                .thenCompose(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));

                    java.util.List<String> missing = new java.util.ArrayList<>();
                    java.util.List<String> ok = new java.util.ArrayList<>();

                    checkValue("log_channel", guildConfig.logChannelId, missing, ok);
                    checkValue("welcome_channel", guildConfig.welcomeChannelId, missing, ok);
                    checkValue("auto_assign_role", guildConfig.autoAssignRoleId, missing, ok);
                    checkValue("temp_hub_channel", guildConfig.tempHubChannelId, missing, ok);
                    checkValue("temp_channel_category", guildConfig.tempChannelCategoryId, missing, ok);

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("🔎 Config Audit")
                            .setColor(missing.isEmpty() ? new Color(0x4CAF50) : new Color(0xFF9800))
                            .addField("OK", ok.isEmpty() ? "*Nada validado*" : String.join("\n", ok), false)
                            .addField("Faltando/Ajustar", missing.isEmpty() ? "*Nada*" : String.join("\n", missing), false)
                            .setTimestamp(OffsetDateTime.now());

                    return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenApply(v -> null);
                });
    }

    private void checkValue(String key, String value, java.util.List<String> missing, java.util.List<String> ok) {
        if (value == null || value.isBlank()) {
            missing.add("`" + key + "`");
        } else {
            ok.add("`" + key + "` → `" + value + "`");
        }
    }

    private CompletableFuture<Void> showConfig(CommandContext ctx, List<String> args) {
        String guildId = ctx.getGuildId();
        return dbManager.getGuildConfig(guildId)
                .thenCompose(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("⚙️ Configurações Atuais")
                            .setColor(new Color(0x2F3136))
                            .setFooter("Solicitado por " + ctx.getAuthor().getAsTag(), ctx.getAuthor().getEffectiveAvatarUrl())
                            .setTimestamp(OffsetDateTime.now());

                    for (Field field : GuildConfig.class.getDeclaredFields()) {
                        if (field.isSynthetic() || field.getName().startsWith("this$")) continue;

                        try {
                            field.setAccessible(true);
                            Object value = field.get(guildConfig);
                            String displayKey = field.getName().replaceAll("([A-Z])", "_$1").toLowerCase();

                            String displayValue = value == null ? "*Não definido*" : "`" + value.toString() + "`";
                            if (value instanceof Boolean) {
                                displayValue = ((Boolean) value) ? "✅ Ativado" : "❌ Desativado";
                            }

                            embed.addField(displayKey, displayValue, true);
                        } catch (IllegalAccessException e) {
                            LOGGER.error("Failed to access field via reflection in showConfig: {}", field.getName(), e);
                        }
                    }

                    return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenApply(v -> null);
                });
    }

    private String configFormat(Object value) {
        return value == null ? "Não definido" : String.valueOf(value);
    }

    private CompletableFuture<Void> setConfig(CommandContext ctx, List<String> args) {
        if (args.size() < 2) {
            return ctx.reply("Uso: `!config set <key> <value>`");
        }

        String key = args.getFirst().toLowerCase();
        String rawValue = String.join(" ", args.subList(1, args.size())).trim();
        String guildId = ctx.getGuildId();

        return dbManager.getGuildConfig(guildId)
                .thenCompose(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                    Field targetField;

                    Pattern pattern = Pattern.compile("_([a-z])");
                    Matcher matcher = pattern.matcher(key);
                    StringBuilder sb = new StringBuilder();
                    while (matcher.find()) {
                        matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
                    }
                    matcher.appendTail(sb);
                    String fieldName = sb.toString();

                    LOGGER.info("generated fieldName: {}", fieldName);
                    LOGGER.info("original key: {}", key);

                    String finalReplyMessage;

                    try {
                        try {
                            targetField = GuildConfig.class.getDeclaredField(fieldName);
                        } catch (NoSuchFieldException e) {
                            targetField = GuildConfig.class.getDeclaredField(fieldName + "Id");
                        }
                        targetField.setAccessible(true);

                        Object valueToSet = getObject(targetField, rawValue, key);

                        targetField.set(guildConfig, valueToSet);

                        finalReplyMessage = "Configuração `" + key + "` definida para: `" + configFormat(valueToSet) + "`";

                    } catch (NoSuchFieldException e) {
                        LOGGER.warn("Config key '{}' is not a field on GuildConfig.", key, e);
                        return ctx.reply("Chave de configuração desconhecida: '" + key + "'. Use `!config show` ou `!config audit` para revisar as opções atuais.");
                    } catch (NumberFormatException e) {
                        return ctx.reply("Valor inválido para '" + key + "'. Esperado um número.");
                    } catch (IllegalArgumentException e) {
                        return ctx.reply("Valor inválido para '" + key + "'. " + e.getMessage());
                    } catch (IllegalAccessException e) {
                        LOGGER.error("Reflection field access error: {}", key, e);
                        return ctx.reply("Erro interno ao tentar configurar. Verifique os logs.");
                    }

                    return dbManager.updateGuildConfig(guildConfig)
                            .thenCompose(v -> ctx.reply(finalReplyMessage))
                            .exceptionally(ex -> {
                                LOGGER.error("Failed to save config {}:{} for guild {}:", key, rawValue, guildId, ex);
                                ctx.reply("Erro ao salvar a configuração. Verifique os logs.");

                                return null;
                            });
                });
    }

    private static @Nullable Object getObject(Field targetField, String rawValue, String key) {
        Object valueToSet;
        Class<?> fieldType = targetField.getType();

        String processedValue = rawValue;
        if (rawValue.matches("<#[0-9]+>")) {
            processedValue = rawValue.substring(2, rawValue.length() - 1);
        } else if (rawValue.matches("<@&[0-9]+>")) {
            processedValue = rawValue.substring(3, rawValue.length() - 1);
        } else if (rawValue.matches("<@!?[0-9]+>")) {
            processedValue = rawValue.replaceAll("[<@!>]", "").replaceAll(">", "");
        }

        if (processedValue.isEmpty() || processedValue.equalsIgnoreCase("null")) {
            valueToSet = null;
        } else if (fieldType == String.class) {
            valueToSet = processedValue;
        } else if (fieldType == Integer.class || fieldType == int.class) {
            int intValue = Integer.parseInt(processedValue);
            if (key.equals("default_temp_channel_user_limit") && (intValue < 0 || intValue > 99)) {
                throw new IllegalArgumentException("Limite de usuários deve ser entre 0 e 99.");
            }
            valueToSet = intValue;
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {

            if (!processedValue.equalsIgnoreCase("true") && !processedValue.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Valor deve ser 'true' ou 'false'.");
            }
            valueToSet = Boolean.parseBoolean(processedValue);
        } else {
            throw new IllegalArgumentException("Tipo de valor para '" + key + "' não suportado.");
        }
        return valueToSet;
    }
}
