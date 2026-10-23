package com.combatmod.input;

import com.combatmod.CombatModClient;
import com.combatmod.gui.CombatClickGuiScreen;
import com.combatmod.module.Module;
import com.combatmod.module.combat.AimAssistModule;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class KeybindManager {
    public static final KeyBinding.Category COMBAT_KEY_CATEGORY =
            KeyBinding.Category.create(Identifier.of("combat", "general"));

    private KeyBinding openGuiKeyBinding;
    private KeyBinding manualLockKeyBinding;

    private final Map<Integer, Boolean> previousKeyStates = new HashMap<>();
    private boolean prevGuiCustomKeyDown = false;

    public void register() {
        openGuiKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.combat.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                COMBAT_KEY_CATEGORY
        ));

        manualLockKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.combat.aim_assist_lock",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                COMBAT_KEY_CATEGORY
        ));
    }

    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getWindow() == null) {
            previousKeyStates.clear();
            prevGuiCustomKeyDown = false;
            return;
        }

        // Do not process module keybinds when any GUI screen (chat, inventory, pause, ClickGUI) is open
        if (client.currentScreen != null) {
            while (openGuiKeyBinding.wasPressed()) {
                // Consume presses while screen is open
            }
            while (manualLockKeyBinding.wasPressed()) {
                // Consume presses while screen is open
            }
            previousKeyStates.clear();
            prevGuiCustomKeyDown = false;
            return;
        }

        long windowHandle = client.getWindow().getHandle();

        // Check registered Vanilla Options keybind OR custom GUI keybind from ConfigManager
        boolean openGuiTriggered = false;
        while (openGuiKeyBinding.wasPressed()) {
            openGuiTriggered = true;
        }

        int configuredGuiKey = CombatModClient.getInstance().getConfigManager().getGuiToggleKey();
        if (configuredGuiKey > 0 && configuredGuiKey != GLFW.GLFW_KEY_UNKNOWN) {
            boolean isDown = InputUtil.isKeyPressed(windowHandle, configuredGuiKey);
            if (isDown && !prevGuiCustomKeyDown && openGuiKeyBinding.isUnbound()) {
                openGuiTriggered = true;
            }
            prevGuiCustomKeyDown = isDown;
        } else {
            prevGuiCustomKeyDown = false;
        }

        if (openGuiTriggered) {
            client.setScreen(new CombatClickGuiScreen());
            return;
        }

        // Check manual lock keybind from Vanilla Controls
        AimAssistModule aimAssist = CombatModClient.getInstance().getModuleManager().getModule(AimAssistModule.class);
        boolean lockTriggered = false;
        while (manualLockKeyBinding.wasPressed()) {
            lockTriggered = true;
        }

        if (aimAssist != null && aimAssist.isEnabled()) {
            int customLockCode = aimAssist.lockKeybind.getValue();
            if (customLockCode > 0 && customLockCode != GLFW.GLFW_KEY_UNKNOWN) {
                if (wasKeyJustPressed(windowHandle, customLockCode)) {
                    lockTriggered = true;
                }
            }
            if (lockTriggered) {
                aimAssist.toggleManualLock(client);
            }
        }

        // Poll each module's independent toggle keybind using edge detection (never intercepts movement keys)
        for (Module module : CombatModClient.getInstance().getModuleManager().getModules()) {
            int keyCode = module.getToggleKeybind().getValue();
            if (keyCode > 0 && keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                if (wasKeyJustPressed(windowHandle, keyCode)) {
                    module.toggle();
                    CombatModClient.getInstance().getConfigManager().save();
                }
            }
        }
    }

    private boolean wasKeyJustPressed(long windowHandle, int keyCode) {
        boolean currentlyDown = InputUtil.isKeyPressed(windowHandle, keyCode);
        boolean previouslyDown = previousKeyStates.getOrDefault(keyCode, false);
        previousKeyStates.put(keyCode, currentlyDown);
        return currentlyDown && !previouslyDown;
    }
}