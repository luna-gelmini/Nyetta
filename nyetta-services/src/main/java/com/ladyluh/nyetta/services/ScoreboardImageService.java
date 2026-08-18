package com.ladyluh.nyetta.services;

import flux.api.entities.User;
import com.ladyluh.nyetta.database.UserXP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ScoreboardImageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScoreboardImageService.class);
    private static final int WIDTH = 800;
    private static final int HEADER_HEIGHT = 100;
    private static final int ROW_HEIGHT = 80;
    private static final int PADDING = 20;
    private static final Color BACKGROUND_COLOR = new Color(30, 30, 35);
    private static final Color ROW_COLOR = new Color(45, 45, 50);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color ACCENT_COLOR = new Color(0xFFD700);

    public CompletableFuture<byte[]> generateLeaderboardImage(List<UserXP> topUsers,
            Function<String, CompletableFuture<User>> userProvider) {
        return CompletableFuture.supplyAsync(() -> {
            int height = HEADER_HEIGHT + (topUsers.size() * (ROW_HEIGHT + 10)) + PADDING;
            BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2d.setColor(BACKGROUND_COLOR);
            g2d.fillRect(0, 0, WIDTH, height);

            g2d.setColor(ACCENT_COLOR);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 40));
            g2d.drawString("Leaderboard", PADDING, 65);

            g2d.setColor(Color.GRAY);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g2d.drawString("Top 10", PADDING, 90);

            int y = HEADER_HEIGHT;
            for (int i = 0; i < topUsers.size(); i++) {
                UserXP userXP = topUsers.get(i);
                drawUserRow(g2d, i + 1, userXP, y, userProvider);
                y += ROW_HEIGHT + 10;
            }

            g2d.dispose();

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            } catch (IOException e) {
                LOGGER.error("Failed to write leaderboard image", e);
                throw new RuntimeException("Failed to generate image", e);
            }
        });
    }

    private void drawUserRow(Graphics2D g2d, int rank, UserXP userXP, int y,
            Function<String, CompletableFuture<User>> userProvider) {
        g2d.setColor(ROW_COLOR);
        g2d.fill(new RoundRectangle2D.Float(PADDING, y, WIDTH - (PADDING * 2), ROW_HEIGHT, 20, 20));

        g2d.setColor(rank <= 3 ? ACCENT_COLOR : Color.LIGHT_GRAY);
        g2d.setFont(new Font("Dialog", Font.BOLD, 30));
        g2d.drawString("#" + rank, PADDING + 20, y + 50);

        User user = userProvider.apply(userXP.getUserId()).join();
        String username = user != null ? (user.getGlobalName() != null ? user.getGlobalName() : user.getUsername())
                : "Unknown User";
        String avatarUrl = user != null ? user.getEffectiveAvatarUrl() : null;

        if (avatarUrl != null) {
            try {
                BufferedImage avatar = ImageIO.read(java.net.URI.create(avatarUrl).toURL());
                if (avatar != null) {
                    BufferedImage circularAvatar = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D ag2d = circularAvatar.createGraphics();
                    ag2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    ag2d.setClip(new Ellipse2D.Float(0, 0, 60, 60));
                    ag2d.drawImage(avatar, 0, 0, 60, 60, null);
                    ag2d.dispose();

                    g2d.drawImage(circularAvatar, PADDING + 100, y + 10, null);
                }
            } catch (Exception e) {
            }
        }

        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("Dialog", Font.BOLD, 24));
        g2d.drawString(username, PADDING + 180, y + 35);

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setFont(new Font("Dialog", Font.PLAIN, 18));
        g2d.drawString("Level " + userXP.getLevel() + " | XP: " + userXP.getXp(), PADDING + 180, y + 65);
    }
}
