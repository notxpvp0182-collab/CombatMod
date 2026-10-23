package com.combatmod.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    private RotationUtil() {
    }

    public static Vec3d getTargetAimPoint(ClientPlayerEntity player, Entity target) {
        Box box = target.getBoundingBox();
        // Target upper chest / center of mass for reliable hits without slipping
        double targetY = box.minY + (target.getHeight() * 0.65);
        return new Vec3d(target.getX(), targetY, target.getZ());
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
     * Flexible, organic 360 tracking that sticks to target body like a real human player.
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
        double totalDelta = Math.hypot(yawDelta, pitchDelta);

        // Sticky deadzone: if already on target chest/body, don't jitter
        if (totalDelta < 0.25) {
            return;
        }

        // Proportional organic curve (smooth human-like easing)
        float progress = MathHelper.clamp(1.0f - (smoothingFactor * 0.75f), 0.08f, 0.95f);
        
        // Sticky dynamic speed adjustment: speeds up when turning, glides smoothly near target
        float speedScale = (float) Math.min(1.0, Math.max(0.2, totalDelta / 45.0));
        float maxYawStep = Math.max(0.5f, horizontalSpeed * speedScale);
        float maxPitchStep = Math.max(0.3f, verticalSpeed * speedScale);

        float stepYaw = MathHelper.clamp(yawDelta * progress, -maxYawStep, maxYawStep);
        player.setYaw(player.getYaw() + stepYaw);

        if (adjustPitch) {
            float stepPitch = MathHelper.clamp(pitchDelta * progress, -maxPitchStep, maxPitchStep);
            player.setPitch(MathHelper.clamp(player.getPitch() + stepPitch, -90.0f, 90.0f));
        }
    }
}
