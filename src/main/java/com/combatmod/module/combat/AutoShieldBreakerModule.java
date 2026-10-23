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
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoShieldBreakerModule extends Module {
    public enum WeaponPreference {
        AXES_ONLY("Axes Only"),
        AXES_AND_MACES("Axes & Maces"),
        ANY_SHIELD_DISABLER("Any Shield-Disabling Weapon");

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
            "Which weapons are eligible to break shields",
            WeaponPreference.AXES_ONLY
    ));

    public final DoubleSetting switchDelayTicks = addSetting(new DoubleSetting(
            "SwitchDelayTicks",
            "Ticks to wait before switching to the axe/breaker slot",
            1.0, 0.0, 10.0, 1.0
    ));

    public final DoubleSetting attackDelayTicks = addSetting(new DoubleSetting(
            "AttackDelayTicks",
            "Ticks to wait after switching before striking the shield",
            1.0, 0.0, 10.0, 1.0
    ));

    public final DoubleSetting restoreDelayTicks = addSetting(new DoubleSetting(
            "RestoreDelayTicks",
            "Ticks to wait after striking before restoring the previous hotbar slot",
            2.0, 0.0, 15.0, 1.0
    ));

    public final BooleanSetting restorePreviousSlot = addSetting(new BooleanSetting(
            "RestoreSlot",
            "Restore the original hotbar slot if you have not manually changed slots",
            true
    ));

    public final BooleanSetting requireCooldown = addSetting(new BooleanSetting(
            "RespectCooldown",
            "Wait for normal attack cooldown before striking",
            true
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

    public AutoShieldBreakerModule() {
        super("AutoShieldBreaker", "Switches to an eligible hotbar axe/weapon to disable a blocking target's shield and safely restores your slot.", Category.COMBAT);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        if (client != null && client.player != null) {
            safelyRestoreSlot(client.player);
        }
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        savedOriginalSlot = -1;
        activeBreakerSlot = -1;
        timerTicks = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

        if (client.currentScreen != null || !client.isWindowFocused()) {
            safelyRestoreSlot(client.player);
            resetState();
            return;
        }

        ClientPlayerEntity player = client.player;
        PlayerInventory inventory = player.getInventory();

        // Detect if user manually changed hotbar slot during an operation
        if (state == State.WAITING_TO_ATTACK || state == State.WAITING_TO_RESTORE) {
            if (inventory.getSelectedSlot() != activeBreakerSlot) {
                // User manually selected another slot — do not overwrite their choice!
                resetState();
                return;
            }
        }

        if (state == State.WAITING_TO_RESTORE) {
            timerTicks++;
            if (timerTicks >= restoreDelayTicks.getIntValue()) {
                safelyRestoreSlot(player);
                resetState();
            }
            return;
        }

        LivingEntity blockingTarget = getCrosshairBlockingTarget(client);
        if (blockingTarget == null || player.isUsingItem()) {
            if (state == State.WAITING_TO_ATTACK) {
                safelyRestoreSlot(player);
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
                    if (inventory.getSelectedSlot() != breakerSlot) {
                        savedOriginalSlot = inventory.getSelectedSlot();
                        inventory.setSelectedSlot(breakerSlot);
                    }
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
                if (requireCooldown.getValue() && player.getAttackCooldownProgress(0.5f) < 0.90f) {
                    return;
                }
                client.interactionManager.attackEntity(player, blockingTarget);
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

    private void safelyRestoreSlot(ClientPlayerEntity player) {
        if (!restorePreviousSlot.getValue() || savedOriginalSlot < 0 || savedOriginalSlot >= 9) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        // Only restore if the current slot is still the one we switched to
        if (activeBreakerSlot != -1 && inventory.getSelectedSlot() == activeBreakerSlot) {
            inventory.setSelectedSlot(savedOriginalSlot);
        }
    }

    private LivingEntity getCrosshairBlockingTarget(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)) {
            return null;
        }
        Entity entity = entityHit.getEntity();
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
            return null;
        }
        if (isEntityBlockingWithShield(living)) {
            return living;
        }
        return null;
    }

    private boolean isEntityBlockingWithShield(LivingEntity entity) {
        if (entity.isBlocking()) {
            return true;
        }
        if (entity.isUsingItem()) {
            ItemStack active = entity.getActiveItem();
            if (!active.isEmpty()) {
                return active.isOf(Items.SHIELD) || active.get(DataComponentTypes.BLOCKS_ATTACKS) != null;
            }
        }
        return false;
    }

    private int findEligibleHotbarSlot(PlayerInventory inventory) {
        int current = inventory.getSelectedSlot();
        if (isEligibleBreakerWeapon(inventory.getStack(current))) {
            return current;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isEligibleBreakerWeapon(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isEligibleBreakerWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        boolean isAxe = stack.getItem() instanceof AxeItem || stack.isIn(ItemTags.AXES);
        boolean isMace = stack.getItem() instanceof MaceItem || stack.isOf(Items.MACE);
        WeaponComponent weaponComponent = stack.get(DataComponentTypes.WEAPON);
        boolean disablesShields = weaponComponent != null && weaponComponent.disableBlockingForSeconds() > 0.0f;

        return switch (weaponPreference.getValue()) {
            case AXES_ONLY -> isAxe;
            case AXES_AND_MACES -> isAxe || isMace;
            case ANY_SHIELD_DISABLER -> isAxe || isMace || disablesShields;
        };
    }
}