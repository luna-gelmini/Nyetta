package com.ladyluh.nyetta.commands.impl.utility;

import com.ladyluh.nyetta.cache.SnipeCache;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SnipeCommand implements Command {
    private final SnipeCache snipeCache;

    public SnipeCommand(SnipeCache snipeCache) {
        this.snipeCache = snipeCache;
    }

    @Override
    public String getName() {
        return "snipe";
    }

    @Override
    public List<String> getAliases() {
        return List.of("s");
    }

    @Override
    public String getDescription() {
        return "Mostra a última mensagem apagada neste canal.";
    }

    @Override
    public String getUsage() {
        return "snipe";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        return snipeCache.lastDeleted(ctx.getChannelId())
                .map(sniped -> {
                    String content = sniped.content().isBlank() ? "*sem texto*" : sniped.content();
                    if (content.length() > 1000) {
                        content = content.substring(0, 997) + "...";
                    }
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("Mensagem apagada")
                            .setColor(new Color(0xED4245))
                            .setDescription(content)
                            .addField("Autor", sniped.authorTag() + " (`" + sniped.authorId() + "`)", false)
                            .setFooter(ago(sniped.deletedAt()), null);
                    return ctx.getClient()
                            .sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build())
                            .thenAccept(m -> {
                            });
                })
                .orElseGet(() -> ctx.reply("Nenhuma mensagem apagada recentemente neste canal."));
    }

    private static String ago(long deletedAt) {
        long seconds = Math.max(0, (System.currentTimeMillis() - deletedAt) / 1000);
        if (seconds < 60) {
            return "há " + seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return "há " + minutes + "min";
        }
        return "há " + (minutes / 60) + "h";
    }
}
