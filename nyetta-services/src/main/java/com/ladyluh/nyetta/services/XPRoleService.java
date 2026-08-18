package com.ladyluh.nyetta.services;

import flux.api.FluxClient;
import com.ladyluh.nyetta.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class XPRoleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(XPRoleService.class);
    private final FluxClient client;
    private final TreeMap<Integer, String> xpRoleMappings;

    public XPRoleService(FluxClient client, ConfigManager config) {
        this.client = client;
        this.xpRoleMappings = new TreeMap<>(config.getXPRoleMappings());
        if (this.xpRoleMappings.isEmpty()) {
            LOGGER.warn("No XP role mappings in config. XP roles disabled.");
        }
    }

    public String getHighestApplicableRole(int level) {

        Map.Entry<Integer, String> entry = xpRoleMappings.floorEntry(level);
        if (entry != null) {
            return entry.getValue();
        }
        return null;
    }

    public CompletableFuture<Void> assignXPRoles(String guildId, String userId, int oldLevel, int newLevel) {
        if (xpRoleMappings.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String roleIdToAssign = getHighestApplicableRole(newLevel);
        String roleIdToRemove = getHighestApplicableRole(oldLevel);

        LOGGER.info("XP role assignment for user {} (guild {}): level {} -> {}. Role to add: {}. Role to remove: {}",
                userId, guildId, oldLevel, newLevel, roleIdToAssign, roleIdToRemove);

        CompletableFuture<Void> addRoleFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> removeRoleFuture = CompletableFuture.completedFuture(null);

        if (roleIdToAssign != null && !roleIdToAssign.equals(roleIdToRemove)) {
            addRoleFuture = client.addRoleToMember(guildId, userId, roleIdToAssign)
                    .thenRun(() -> LOGGER.info("XP role {} assigned to {} in {}.", roleIdToAssign, userId, guildId))
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to assign XP role {} to {} in {}:", roleIdToAssign, userId, guildId, ex);
                        return null;
                    });
        } else if (roleIdToAssign == null && roleIdToRemove != null) {

            LOGGER.debug("User {} has no XP role at level {}. Removing old role {}.", userId, newLevel, roleIdToRemove);
        } else if (roleIdToAssign != null) {
            LOGGER.debug("XP role does not need to change for {}. Stays {}.", userId, roleIdToAssign);
            return CompletableFuture.completedFuture(null);
        }

        if (roleIdToRemove != null) {
            removeRoleFuture = client.removeRoleFromMember(guildId, userId, roleIdToRemove)
                    .thenRun(() -> LOGGER.info("XP role {} removed from {} in {}.", roleIdToRemove, userId, guildId))
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to remove XP role {} from {} in {}:", roleIdToRemove, userId, guildId, ex);
                        return null;
                    });
        }

        return CompletableFuture.allOf(addRoleFuture, removeRoleFuture);
    }
}
