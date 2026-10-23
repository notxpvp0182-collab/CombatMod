package com.combatmod.module.combat;

import com.combatmod.config.BooleanSetting;
import com.combatmod.config.DoubleSetting;
import com.combatmod.config.EnumSetting;
import com.combatmod.config.KeybindSetting;
import com.combatmod.module.Category;
import com.combatmod.module.Module;
import com.combatmod.util.RotationUtil;
import com.combatmod.util.TargetUtil;
import com.combatmod.util.TargetUtil.SortPriority;
import com.combatmod.util.TargetUtil.TargetMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class AimAssistModule extends Module {
    public final EnumSetting<TargetMode> targetMode = addSetting(new EnumSetting<>(
            "TargetMode",
            "Filter eligible entities (Players Only or All Living)",
            TargetMode.PLAYERS_ONLY
    ));

    public final EnumSetting<SortPriority> sortPriority = addSetting(new EnumSetting<>(
            "Priority",
            "How Aim Assist selects and switches targets",
            SortPriority.ANGLE
    ));

    public final DoubleSetting reach = addSetting(new DoubleSetting(
            "Reach",
            "Maximum distance in blocks to acquire targets",
            5.2, 2.0, 10.0, 0.1
    ));

    public final DoubleSetting maxAngle = addSetting(new DoubleSetting(
            "MaxAngle",
            "Maximum field-of-view angle (Supports full 360° tracking)",
            360.0, 10.0, 360.0, 5.0
    ));

    public final DoubleSetting horizontalSpeed = addSetting(new DoubleSetting(
            "HorizontalSpeed",
            "Yaw rotation speed",
            9.5, 0.5, 45.0, 0.5
    ));

    public final DoubleSetting verticalSpeed = addSetting(new DoubleSetting(
            "VerticalSpeed",
            "Pitch rotation speed",
            6.0, 0.5, 30.0, 0.5
    ));

    public final DoubleSetting smoothing = addSetting(new DoubleSetting(
            "Smoothing",
            "Smooth interpolation factor (higher = more natural/human)",
            0.62, 0.05, 0.95, 0.02
    ));

    public final BooleanSetting adjustPitch = addSetting(new BooleanSetting(
            "AdjustPitch",
            "Smoothly adjust vertical pitch",
            true
    ));

    public final BooleanSetting allowTargetSwitch = addSetting(new BooleanSetting(
            "TargetSwitch",
            "Automatically switch to better target",
            true
    ));

    public final BooleanSetting manualLockEnabled = addSetting(new BooleanSetting(
            "ManualLock",
            "Allow locking target with keybind",
            true
    ));

    public final KeybindSetting lockKeybind = addSetting(new KeybindSetting(
            "LockKey",
            "Keybind to lock/unlock current target",
            GLFW.GLFW_KEY_UNKNOWN
    ));

    public final BooleanSetting showIndicator = addSetting(new BooleanSetting(
            "TargetIndicator",
            "Render HUD target box (Can be dragged in Click GUI)",
            true
    ));

    public final DoubleSetting hudPosX = addSetting(new DoubleSetting(
            "HudPosX",
            "Target HUD horizontal screen position (%)",
            55.0, 0.0, 100.0, 1.0
    ));

    public final DoubleSetting hudPosY = addSetting(new DoubleSetting(
            "HudPosY",
            "Target HUD vertical screen position (%)",
            55.0, 0.0, 100.0, 1.0
    ));

    public final BooleanSetting requireLineOfSight = addSetting(new BooleanSetting(
            "LineOfSight",
            "Only assist toward visible targets",
            true
    ));

    public final BooleanSetting ignoreInvisible = addSetting(new BooleanSetting(
            "IgnoreInvisible",
            "Skip invisible entities",
            true
    ));

    public final BooleanSetting unlockOnFocusLost = addSetting(new BooleanSetting(
            "UnlockOnBlur",
            "Release lock if window loses focus",
            true
    ));

    private LivingEntity currentTarget;
    private LivingEntity lockedTarget;

    public AimAssistModule() {
        super("AimAssist", "Humanized 360 smooth camera tracking toward valid combat targets.", Category.COMBAT);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        currentTarget = null;
        lockedTarget = null;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public boolean isTargetManuallyLocked() {
        return lockedTarget != null && lockedTarget == currentTarget;
    }

    public void clearManualLock() {
        lockedTarget = null;
    }

    public void toggleManualLock(MinecraftClient client) {
        if (!manualLockEnabled.getValue() || client.player == null || client.world == null) {
            lockedTarget = null;
            return;
        }
        if (lockedTarget != null) {
            lockedTarget = null;
            return;
        }
        if (client.crosshairTarget instanceof EntityHitResult hitResult) {
            Entity hit = hitResult.getEntity();
            if (TargetUtil.isValidTarget(
                    client.player, hit, reach.getValue(), 360.0,
                    targetMode.getValue(), ignoreInvisible.getValue(), requireLineOfSight.getValue())) {
                lockedTarget = (LivingEntity) hit;
                currentTarget = lockedTarget;
                return;
            }
        }
        LivingEntity best = TargetUtil.findBestTarget(
                client,
                reach.getValue(),
                maxAngle.getValue(),
                targetMode.getValue(),
                sortPriority.getValue(),
                ignoreInvisible.getValue(),
                requireLineOfSight.getValue()
        );
        if (best != null) {
            lockedTarget = best;
            currentTarget = best;
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null) {
            currentTarget = null;
            return;
        }

        if (!client.isWindowFocused()) {
            if (unlockOnFocusLost.getValue()) {
                lockedTarget = null;
            }
            return;
        }

        if (lockedTarget != null) {
            boolean stillValid = manualLockEnabled.getValue() && TargetUtil.isValidTarget(
                    client.player,
                    lockedTarget,
                    reach.getValue(),
                    360.0,
                    targetMode.getValue(),
                    ignoreInvisible.getValue(),
                    requireLineOfSight.getValue()
            );
            if (!stillValid) {
                lockedTarget = null;
            }
        }

        if (lockedTarget != null) {
            currentTarget = lockedTarget;
        } else {
            boolean keepCurrent = !allowTargetSwitch.getValue()
                    && currentTarget != null
                    && TargetUtil.isValidTarget(
                    client.player,
                    currentTarget,
                    reach.getValue(),
                    maxAngle.getValue(),
                    targetMode.getValue(),
                    ignoreInvisible.getValue(),
                    requireLineOfSight.getValue()
            );

            if (!keepCurrent) {
                currentTarget = TargetUtil.findBestTarget(
                        client,
                        reach.getValue(),
                        maxAngle.getValue(),
                        targetMode.getValue(),
                        sortPriority.getValue(),
                        ignoreInvisible.getValue(),
                        requireLineOfSight.getValue()
                );
            }
        }

        if (currentTarget != null) {
            RotationUtil.applySmoothRotation(
                    client.player,
                    currentTarget,
                    horizontalSpeed.getFloatValue(),
                    verticalSpeed.getFloatValue(),
                    smoothing.getFloatValue(),
                    adjustPitch.getValue()
            );
        }
    }
}
