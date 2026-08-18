package com.ladyluh.nyetta.commands.impl.moderation;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SlowmodeCommand implements Command {
    @Override
    public String getName() {
        return "slowmode";
    }

    @Override
    public List<String> getAliases() {
        return List.of("slow");
    }

    @Override
    public String getDescription() {
        return "Set this channel's slowmode in seconds.";
    }

    @Override
    public String getUsage() {
        return "slowmode <seconds|off>";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public boolean requiresAdministrator() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (ctx.getArgs().isEmpty()) {
            return ctx.reply("Usage: `" + getUsage() + "`");
        }
        String raw = ctx.getArgs().getFirst().trim();
        int seconds;
        if (raw.equalsIgnoreCase("off") || raw.equals("0")) {
            seconds = 0;
        } else {
            try {
                seconds = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                return ctx.reply("Invalid value. Use seconds or `off`.");
            }
        }
        if (seconds < 0 || seconds > 21600) {
            return ctx.reply("Slowmode must be between 0 and 21600 seconds.");
        }

        String body = "{\"rate_limit_per_user\":" + seconds + "}";
        return ctx.getClient().rest().channels.updateChannel(ctx.getChannelId(), body)
                .thenCompose(ignored -> ctx.reply(seconds == 0
                        ? "Slowmode off."
                        : "Slowmode set to " + seconds + "s."));
    }
}
