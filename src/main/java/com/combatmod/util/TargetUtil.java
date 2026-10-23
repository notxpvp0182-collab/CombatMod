package com.combatmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Comparator;
import java.util.stream.StreamSupport;

public final class TargetUtil {
    public enum TargetMode {
        PLAYERS_ONLY("Players Only"),
        ALL("All Living");

        private final String label;

        TargetMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SortPriority {
        ANGLE("Closest Angle"),
        DISTANCE("Closest Distance"),
        HEALTH("Lowest Health");

        private final String label;

        SortPriority(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private TargetUtil() {
    }

    public static boolean isValidTarget(
            ClientPlayerEntity player,
            Entity entity,
            double maxRange,
            double maxFovDegrees,
            TargetMode targetMode,
            boolean ignoreInvisible,
            boolean requireLineOfSight
    ) {
        if (player == null || entity == null || entity == player) {
            return false;
        }
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        if (entity instanceof ArmorStandEntity) {
            return false;
        }
        if (!living.isAlive() || living.isRemoved() || living.isDead()) {
            return false;
        }
        if (living instanceof PlayerEntity targetPlayer && targetPlayer.isSpectator()) {
            return false;
        }
        if (targetMode == TargetMode.PLAYERS_ONLY && !(living instanceof PlayerEntity)) {
            return false;
        }
        if (ignoreInvisible && living.isInvisible()) {
            return false;
        }
        double distSq = player.squaredDistanceTo(living);
        if (distSq > maxRange * maxRange) {
            return false;
        }
        if (requireLineOfSight && !player.canSee(living)) {
            return false;
        }
        double angle = RotationUtil.getAngleDifference(player, living);
        return angle <= maxFovDegrees;
    }

    public static LivingEntity findBestTarget(
            MinecraftClient client,
            double maxRange,
            double maxFovDegrees,
            TargetMode targetMode,
            SortPriority sortPriority,
            boolean ignoreInvisible,
            boolean requireLineOfSight
    ) {
        if (client.player == null || client.world == null) {
            return null;
        }
        ClientPlayerEntity player = client.player;

        Comparator<LivingEntity> comparator = switch (sortPriority) {
            case ANGLE -> Comparator.comparingDouble(e -> RotationUtil.getAngleDifference(player, e));
            case DISTANCE -> Comparator.comparingDouble(player::squaredDistanceTo);
            case HEALTH -> Comparator.comparingDouble(LivingEntity::getHealth);
        };

        return StreamSupport.stream(client.world.getEntities().spliterator(), false)
                .filter(e -> isValidTarget(player, e, maxRange, maxFovDegrees, targetMode, ignoreInvisible, requireLineOfSight))
                .map(e -> (LivingEntity) e)
                .min(comparator)
                .orElse(null);
    }
}