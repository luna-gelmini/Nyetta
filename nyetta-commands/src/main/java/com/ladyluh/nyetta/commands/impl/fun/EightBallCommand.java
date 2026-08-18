package com.ladyluh.nyetta.commands.impl.fun;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class EightBallCommand implements Command {
    private final String[] responses = {
            "Com certeza.", "Definitivamente sim.", "Sem dúvida.", "Sim, definitivamente.",
            "Pode confiar.", "Como eu vejo, sim.", "Muito provável.", "Perspectivas boas.",
            "Sim.", "Sinais apontam que sim.", "Resposta nebulosa, tente novamente.", "Pergunte mais tarde.",
            "Melhor não te dizer agora.", "Não dá pra prever agora.", "Concentre-se e pergunte de novo.",
            "Não conte com isso.", "Minha resposta é não.", "Minhas fontes dizem não.", "Perspectivas não muito boas.",
            "Muito duvidoso."
    };
    private final Random random = new Random();

    @Override
    public String getName() {
        return "8ball";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getDescription() {
        return "Pergunte à bola mágica 8.";
    }

    @Override
    public String getUsage() {
        return "8ball <question>";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String response = responses[random.nextInt(responses.length)];
        return ctx.reply("🎱 " + response);
    }
}
