package com.combatmod.module.combat;

import com.combatmod.config.BooleanSetting;
import com.combatmod.config.DoubleSetting;
import com.combatmod.config.EnumSetting;
import com.combatmod.module.Category;
import com.combatmod.module.Module;
import com.combatmod.util.TargetUtil.TargetMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class TriggerBotModule extends Module {
    public final EnumSetting<TargetMode> targetMode = addSetting(new EnumSetting<>(
            "TargetMode",
            "Filter which entities TriggerBot will attack",
            TargetMode.PLAYERS_ONLY
    ));

    public final DoubleSetting minCooldown = addSetting(new DoubleSetting(
            "MinCooldown",
            "Required attack cooldown progress (0.90 - 1.00)",
            0.95, 0.50, 1.00, 0.01
    ));

    public final DoubleSetting attackDelayTicks = addSetting(new DoubleSetting(
            "AttackDelayTicks",
            "Additional delay in client ticks after target enters crosshair",
            1.0, 0.0, 10.0, 1.0
    ));

    public final BooleanSetting ignoreInvisible = addSetting(new BooleanSetting(
            "IgnoreInvisible",
            "Do not automatically attack invisible entities",
            true
    ));

    public final BooleanSetting pauseWhileUsingItem = addSetting(new BooleanSetting(
            "PauseWhenUsingItem",
            "Do not attack while eating, drinking, or blocking with a shield",
            true
    ));

    private int ticksOnTarget = 0;

    public TriggerBotModule() {
        super("TriggerBot", "Automatically attacks valid targets under the crosshair when attack cooldown is ready.", Category.COMBAT);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        ticksOnTarget = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            ticksOnTarget = 0;
            return;
        }

        // Never attack when a UI screen is open or the game window is unfocused
        if (client.currentScreen != null || !client.isWindowFocused()) {
            ticksOnTarget = 0;
            return;
        }

        if (pauseWhileUsingItem.getValue() && client.player.isUsingItem()) {
            ticksOnTarget = 0;
            return;
        }

        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)) {
            ticksOnTarget = 0;
            return;
        }

        Entity target = entityHit.getEntity();
        if (!isEligibleTarget(client, target)) {
            ticksOnTarget = 0;
            return;
        }

        ticksOnTarget++;
        if (ticksOnTarget <= attackDelayTicks.getIntValue()) {
            return;
        }

        if (client.player.getAttackCooldownProgress(0.5f) < minCooldown.getFloatValue()) {
            return;
        }

        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
        ticksOnTarget = 0;
    }

    private boolean isEligibleTarget(MinecraftClient client, Entity entity) {
        if (entity == null || entity == client.player || !(entity instanceof LivingEntity living)) {
            return false;
        }
        if (entity instanceof ArmorStandEntity) {
            return false;
        }
        if (!living.isAlive() || living.isDead() || living.isRemoved()) {
            return false;
        }
        if (living instanceof PlayerEntity player && player.isSpectator()) {
            return false;
        }
        if (targetMode.getValue() == TargetMode.PLAYERS_ONLY && !(living instanceof PlayerEntity)) {
            return false;
        }
        return !ignoreInvisible.getValue() || !living.isInvisible();
    }
}