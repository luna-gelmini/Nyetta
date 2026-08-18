package com.ladyluh.nyetta.services;

import flux.api.entities.Guild;
import flux.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class WelcomeImageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomeImageService.class);
    private final Path tempDir;

    public WelcomeImageService() {
        this.tempDir = Path.of("temp", "welcome_images");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create temp directory for welcome images", e);
        }
    }

    public File generateWelcomeImage(User user, Guild guild) {
        try {
            int width = 1024;
            int height = 500;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            GradientPaint gp = new GradientPaint(0, 0, new Color(44, 47, 51), width, height, new Color(35, 39, 42));
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, width, height);

            g2d.setColor(new Color(114, 137, 218, 50));
            g2d.fillOval(-100, -100, 400, 400);
            g2d.setColor(new Color(67, 181, 129, 50));
            g2d.fillOval(width - 300, height - 300, 500, 500);

            BufferedImage avatar = downloadAvatar(user.getEffectiveAvatarUrl().replace("?size=128", "?size=256"));
            if (avatar != null) {
                int avatarSize = 250;
                int avatarX = (width - avatarSize) / 2;
                int avatarY = 50;

                BufferedImage circularAvatar = new BufferedImage(avatarSize, avatarSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D ag = circularAvatar.createGraphics();
                ag.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ag.setClip(new Ellipse2D.Float(0, 0, avatarSize, avatarSize));
                ag.drawImage(avatar, 0, 0, avatarSize, avatarSize, null);
                ag.dispose();

                g2d.setColor(new Color(255, 255, 255));
                g2d.setStroke(new BasicStroke(10));
                g2d.drawOval(avatarX - 5, avatarY - 5, avatarSize + 10, avatarSize + 10);

                g2d.drawImage(circularAvatar, avatarX, avatarY, null);
            }

            g2d.setColor(Color.WHITE);

            Font fontWelcome = new Font("SansSerif", Font.BOLD, 60);
            g2d.setFont(fontWelcome);
            String welcomeText = "WELCOME";
            FontMetrics metrics = g2d.getFontMetrics(fontWelcome);
            int x = (width - metrics.stringWidth(welcomeText)) / 2;
            g2d.drawString(welcomeText, x, 380);

            Font fontName = new Font("SansSerif", Font.PLAIN, 40);
            g2d.setFont(fontName);
            String nameText = user.getGlobalName() != null ? user.getGlobalName() : user.getUsername();
            metrics = g2d.getFontMetrics(fontName);
            x = (width - metrics.stringWidth(nameText)) / 2;
            g2d.setColor(new Color(200, 200, 200));
            g2d.drawString(nameText, x, 430);

            g2d.dispose();

            File outputFile = tempDir.resolve("welcome_" + user.getId() + "_" + UUID.randomUUID() + ".png").toFile();
            ImageIO.write(image, "PNG", outputFile);
            return outputFile;
        } catch (Exception e) {
            LOGGER.error("Failed to generate welcome image", e);
            return null;
        }
    }

    private BufferedImage downloadAvatar(String url) {
        try {
            return ImageIO.read(new URL(url));
        } catch (IOException e) {
            LOGGER.error("Failed to download avatar: {}", url);
            return null;
        }
    }
}
