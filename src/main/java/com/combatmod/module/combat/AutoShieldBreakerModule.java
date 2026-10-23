package com.combatmod.module.combat;

import com.combatmod.config.BooleanSetting;
import com.combatmod.config.DoubleSetting;
import com.combatmod.config.EnumSetting;
import com.combatmod.module.Category;
import com.combatmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoShieldBreakerModule extends Module {
    public enum WeaponPreference {
        AXES_ONLY("Axes Only"),
        AXES_AND_MACES("Axes & Maces"),
        ANY_SHIELD_DISABLER("Any Shield-Disabler");

        private final String label;

        WeaponPreference(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public final EnumSetting<WeaponPreference> weaponPreference = addSetting(new EnumSetting<>(
            "WeaponType",
            "Eligible shield-breaking weapons",
            WeaponPreference.AXES_ONLY
    ));

    public final DoubleSetting breakRange = addSetting(new DoubleSetting(
            "Range",
            "Reach distance to break enemy shield",
            4.5, 2.5, 6.0, 0.1
    ));

    public final DoubleSetting switchDelayTicks = addSetting(new DoubleSetting(
            "SwitchDelayTicks",
            "Delay before switching to axe",
            0.0, 0.0, 5.0, 1.0
    ));

    public final DoubleSetting attackDelayTicks = addSetting(new DoubleSetting(
            "AttackDelayTicks",
            "Delay after switching before attacking",
            1.0, 0.0, 8.0, 1.0
    ));

    public final DoubleSetting restoreDelayTicks = addSetting(new DoubleSetting(
            "RestoreDelayTicks",
            "Delay before restoring original slot",
            2.0, 0.0, 10.0, 1.0
    ));

    public final BooleanSetting restorePreviousSlot = addSetting(new BooleanSetting(
            "RestoreSlot",
            "Restore previous hotbar slot after breaking",
            true
    ));

    public final BooleanSetting requireCooldown = addSetting(new BooleanSetting(
            "RespectCooldown",
            "Wait for weapon attack cooldown before striking",
            false
    ));

    private enum State {
        IDLE,
        WAITING_TO_SWITCH,
        WAITING_TO_ATTACK,
        WAITING_TO_RESTORE
    }

    private State state = State.IDLE;
    private int savedOriginalSlot = -1;
    private int activeBreakerSlot = -1;
    private int timerTicks = 0;
    private LivingEntity targetEntity = null;

    public AutoShieldBreakerModule() {
        super("AutoShieldBreaker", "Auto-switches to axe, disables blocking target's shield, and restores slot.", Category.COMBAT);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        if (client != null && client.player != null) {
            safelyRestoreSlot(client);
        }
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        savedOriginalSlot = -1;
        activeBreakerSlot = -1;
        timerTicks = 0;
        targetEntity = null;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

        if (client.currentScreen != null || !client.isWindowFocused()) {
            safelyRestoreSlot(client);
            resetState();
            return;
        }

        ClientPlayerEntity player = client.player;
        PlayerInventory inventory = player.getInventory();

        if (state == State.WAITING_TO_ATTACK || state == State.WAITING_TO_RESTORE) {
            if (inventory.getSelectedSlot() != activeBreakerSlot) {
                resetState();
                return;
            }
        }

        if (state == State.WAITING_TO_RESTORE) {
            timerTicks++;
            if (timerTicks >= restoreDelayTicks.getIntValue()) {
                safelyRestoreSlot(client);
                resetState();
            }
            return;
        }

        targetEntity = findShieldBlockingTarget(client);
        if (targetEntity == null || player.isUsingItem()) {
            if (state == State.WAITING_TO_ATTACK) {
                safelyRestoreSlot(client);
            }
            resetState();
            return;
        }

        int breakerSlot = findEligibleHotbarSlot(inventory);
        if (breakerSlot == -1) {
            resetState();
            return;
        }

        switch (state) {
            case IDLE -> {
                savedOriginalSlot = inventory.getSelectedSlot();
                activeBreakerSlot = breakerSlot;
                timerTicks = 0;
                state = State.WAITING_TO_SWITCH;
            }
            case WAITING_TO_SWITCH -> {
                timerTicks++;
                if (timerTicks >= switchDelayTicks.getIntValue()) {
                    changeHotbarSlot(client, breakerSlot);
                    activeBreakerSlot = breakerSlot;
                    timerTicks = 0;
                    state = State.WAITING_TO_ATTACK;
                }
            }
            case WAITING_TO_ATTACK -> {
                timerTicks++;
                if (timerTicks < attackDelayTicks.getIntValue()) {
                    return;
                }
                if (requireCooldown.getValue() && player.getAttackCooldownProgress(0.5f) < 0.85f) {
                    return;
                }
                client.interactionManager.attackEntity(player, targetEntity);
                player.swingHand(Hand.MAIN_HAND);
                timerTicks = 0;
                if (restorePreviousSlot.getValue() && savedOriginalSlot != -1 && savedOriginalSlot != activeBreakerSlot) {
                    state = State.WAITING_TO_RESTORE;
                } else {
                    resetState();
                }
            }
            default -> resetState();
        }
    }

    private void changeHotbarSlot(MinecraftClient client, int slot) {
        if (client.player == null) return;
        client.player.getInventory().setSelectedSlot(slot);
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private void safelyRestoreSlot(MinecraftClient client) {
        if (!restorePreviousSlot.getValue() || savedOriginalSlot < 0 || savedOriginalSlot >= 9 || client.player == null) {
            return;
        }
        if (activeBreakerSlot != -1 && client.player.getInventory().getSelectedSlot() == activeBreakerSlot) {
            changeHotbarSlot(client, savedOriginalSlot);
        }
    }

    private LivingEntity findShieldBlockingTarget(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;

        // 1. Check direct crosshair hit
        if (client.crosshairTarget instanceof EntityHitResult entityHit) {
            Entity ent = entityHit.getEntity();
            if (ent instanceof LivingEntity living && living.isAlive() && isBlockingShield(living)) {
                if (client.player.squaredDistanceTo(living) <= breakRange.getValue() * breakRange.getValue()) {
                    return living;
                }
            }
        }

        // 2. Search nearby blocking targets within breakRange
        double maxDistSq = breakRange.getValue() * breakRange.getValue();
        for (Entity ent : client.world.getEntities()) {
            if (ent == client.player || !(ent instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            if (client.player.squaredDistanceTo(living) <= maxDistSq && isBlockingShield(living) && client.player.canSee(living)) {
                return living;
            }
        }
        return null;
    }

    private boolean isBlockingShield(LivingEntity entity) {
        if (entity.isBlocking()) {
            return true;
        }
        if (entity.isUsingItem()) {
            ItemStack active = entity.getActiveItem();
            if (!active.isEmpty()) {
                return active.isOf(Items.SHIELD) || active.get(DataComponentTypes.BLOCKS_ATTACKS) != null;
            }
        }
        ItemStack offhand = entity.getOffHandStack();
        if (!offhand.isEmpty() && offhand.isOf(Items.SHIELD) && entity.isUsingItem()) {
            return true;
        }
        return false;
    }

    private int findEligibleHotbarSlot(PlayerInventory inventory) {
        int current = inventory.getSelectedSlot();
        if (isBreaker(inventory.getStack(current))) {
            return current;
        }
        for (int i = 0; i < 9; i++) {
            if (isBreaker(inventory.getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBreaker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean isAxe = stack.getItem() instanceof AxeItem || stack.isIn(ItemTags.AXES);
        boolean isMace = stack.getItem() instanceof MaceItem || stack.isOf(Items.MACE);
        WeaponComponent wc = stack.get(DataComponentTypes.WEAPON);
        boolean disables = wc != null && wc.disableBlockingForSeconds() > 0.0f;

        return switch (weaponPreference.getValue()) {
            case AXES_ONLY -> isAxe;
            case AXES_AND_MACES -> isAxe || isMace;
            case ANY_SHIELD_DISABLER -> isAxe || isMace || disables;
        };
    }
}
