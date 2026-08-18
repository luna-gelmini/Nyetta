package com.ladyluh.nyetta.commands.impl.general;

import flux.api.FluxClient;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.awt.*;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StatsCommand implements Command {
    private final FluxClient client;

    public StatsCommand(FluxClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("metrics");
    }

    @Override
    public String getDescription() {
        return "Mostra métricas do sistema e status do bot.";
    }

    @Override
    public String getUsage() {
        return "stats";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration uptime = Duration.ofMillis(uptimeMillis);

        String uptimeStr = String.format("%dd %dh %dm %ds",
            uptime.toDaysPart(), uptime.toHoursPart(), uptime.toMinutesPart(), uptime.toSecondsPart());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 System Metrics")
                .setColor(new Color(114, 137, 218))
                .addField("⏱️ Uptime", uptimeStr, true)
                .addField("💾 Memória (Heap)", String.format("%dMB / %dMB", usedMemory, totalMemory), true)
                .addField("🏓 Ping", "N/A", true)
                .addField("🤖 Java Version", System.getProperty("java.version"), true)
                .addField("THREADS", String.valueOf(Thread.activeCount()), true)
                .setFooter("Nyetta", null);

        return client.sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenAccept(v -> {});
    }
}
