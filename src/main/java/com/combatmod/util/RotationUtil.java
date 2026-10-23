package com.combatmod.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    private RotationUtil() {
    }

    public static Vec3d getTargetAimPoint(ClientPlayerEntity player, Entity target) {
        Vec3d eyePos = player.getEyePos();
        double minY = target.getBoundingBox().minY + target.getHeight() * 0.15;
        double maxY = target.getBoundingBox().maxY - target.getHeight() * 0.1;
        double clampedY = MathHelper.clamp(eyePos.y, minY, maxY);
        return new Vec3d(target.getX(), clampedY, target.getZ());
    }

    public static float[] getRequiredRotations(ClientPlayerEntity player, Vec3d targetVec) {
        Vec3d eyePos = player.getEyePos();
        double diffX = targetVec.x - eyePos.x;
        double diffY = targetVec.y - eyePos.y;
        double diffZ = targetVec.z - eyePos.z;
        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontalDist));

        return new float[]{
                player.getYaw() + MathHelper.wrapDegrees(targetYaw - player.getYaw()),
                MathHelper.clamp(targetPitch, -90.0f, 90.0f)
        };
    }

    public static double getAngleDifference(ClientPlayerEntity player, Entity target) {
        Vec3d aimPoint = getTargetAimPoint(player, target);
        float[] rots = getRequiredRotations(player, aimPoint);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(rots[0] - player.getYaw()));
        float pitchDiff = Math.abs(rots[1] - player.getPitch());
        return Math.hypot(yawDiff, pitchDiff);
    }

    /**
     * Smoothly interpolates the player's camera yaw/pitch toward the target entity
     * without locking the camera or interfering with WASD / mouse movement.
     */
    public static void applySmoothRotation(
            ClientPlayerEntity player,
            Entity target,
            float horizontalSpeed,
            float verticalSpeed,
            float smoothingFactor,
            boolean adjustPitch
    ) {
        Vec3d aimPoint = getTargetAimPoint(player, target);
        float[] targetRotations = getRequiredRotations(player, aimPoint);

        float yawDelta = MathHelper.wrapDegrees(targetRotations[0] - player.getYaw());
        float pitchDelta = targetRotations[1] - player.getPitch();

        // Deadzone so micro-movements don't fight the player's mouse when already aimed on target
        if (Math.abs(yawDelta) < 0.15f && (!adjustPitch || Math.abs(pitchDelta) < 0.15f)) {
            return;
        }

        float smoothMultiplier = MathHelper.clamp(1.0f - (smoothingFactor * 0.85f), 0.05f, 1.0f);
        float maxYawStep = horizontalSpeed * smoothMultiplier;
        float maxPitchStep = verticalSpeed * smoothMultiplier;

        float appliedYawStep = MathHelper.clamp(yawDelta * smoothMultiplier, -maxYawStep, maxYawStep);
        player.setYaw(player.getYaw() + appliedYawStep);

        if (adjustPitch) {
            float appliedPitchStep = MathHelper.clamp(pitchDelta * smoothMultiplier, -maxPitchStep, maxPitchStep);
            player.setPitch(MathHelper.clamp(player.getPitch() + appliedPitchStep, -90.0f, 90.0f));
        }
    }
}