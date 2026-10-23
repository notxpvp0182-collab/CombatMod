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
            4.5, 2.0, 8.0, 0.1
    ));

    public final DoubleSetting maxAngle = addSetting(new DoubleSetting(
            "MaxAngle",
            "Maximum field-of-view angle in degrees from crosshair",
            75.0, 10.0, 180.0, 1.0
    ));

    public final DoubleSetting horizontalSpeed = addSetting(new DoubleSetting(
            "HorizontalSpeed",
            "Yaw rotation speed in degrees per tick",
            8.5, 0.5, 35.0, 0.5
    ));

    public final DoubleSetting verticalSpeed = addSetting(new DoubleSetting(
            "VerticalSpeed",
            "Pitch rotation speed in degrees per tick",
            4.5, 0.5, 25.0, 0.5
    ));

    public final DoubleSetting smoothing = addSetting(new DoubleSetting(
            "Smoothing",
            "Camera interpolation factor for smooth rotation",
            0.55, 0.05, 0.95, 0.05
    ));

    public final BooleanSetting adjustPitch = addSetting(new BooleanSetting(
            "AdjustPitch",
            "Smoothly adjust vertical camera pitch as well as yaw",
            true
    ));

    public final BooleanSetting allowTargetSwitch = addSetting(new BooleanSetting(
            "TargetSwitch",
            "Automatically switch to a better target when not manually locked",
            true
    ));

    public final BooleanSetting manualLockEnabled = addSetting(new BooleanSetting(
            "ManualLock",
            "Allow locking onto a single target using the Lock Keybind",
            true
    ));

    public final KeybindSetting lockKeybind = addSetting(new KeybindSetting(
            "LockKey",
            "Keybind to manually lock/unlock the current target",
            GLFW.GLFW_KEY_UNKNOWN
    ));

    public final BooleanSetting showIndicator = addSetting(new BooleanSetting(
            "TargetIndicator",
            "Render a HUD target indicator and health bar when a target is active",
            true
    ));

    public final BooleanSetting requireLineOfSight = addSetting(new BooleanSetting(
            "LineOfSight",
            "Only assist aim toward visible targets",
            true
    ));

    public final BooleanSetting ignoreInvisible = addSetting(new BooleanSetting(
            "IgnoreInvisible",
            "Skip invisible entities",
            true
    ));

    public final BooleanSetting unlockOnFocusLost = addSetting(new BooleanSetting(
            "UnlockOnBlur",
            "Release manual target lock if the game window loses focus",
            true
    ));

    private LivingEntity currentTarget;
    private LivingEntity lockedTarget;

    public AimAssistModule() {
        super("AimAssist", "Configurable smooth camera rotation toward valid combat targets.", Category.COMBAT);
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
                    client.player, hit, reach.getValue(), 180.0,
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

        // Validate manual lock target
        if (lockedTarget != null) {
            boolean stillValid = manualLockEnabled.getValue() && TargetUtil.isValidTarget(
                    client.player,
                    lockedTarget,
                    reach.getValue(),
                    180.0,
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
            // Validate existing automatic target if TargetSwitch is disabled
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