package com.combatmod;

import com.combatmod.config.ConfigManager;
import com.combatmod.gui.HudOverlayRenderer;
import com.combatmod.input.KeybindManager;
import com.combatmod.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombatModClient implements ClientModInitializer {
    public static final String MOD_ID = "combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CombatModClient instance;

    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final KeybindManager keybindManager;

    private boolean previousWindowFocus = true;

    public CombatModClient() {
        instance = this;
        this.moduleManager = new ModuleManager();
        this.configManager = new ConfigManager(this.moduleManager);
        this.keybindManager = new KeybindManager();
    }

    public static CombatModClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public KeybindManager getKeybindManager() {
        return keybindManager;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Combat] Initializing client subsystems for Minecraft 1.21.11...");

        // 1. Register module pool
        moduleManager.registerModules();

        // 2. Register vanilla controls & client key bindings
        keybindManager.register();

        // 3. Load user configurations from config/combat.json
        configManager.load();

        // 4. Register HUD target indicator overlay
        HudOverlayRenderer.register();

        // 5. Register primary client tick loop
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) {
                return;
            }

            // Window focus state transition tracking
            boolean currentlyFocused = client.isWindowFocused();
            if (currentlyFocused != previousWindowFocus) {
                previousWindowFocus = currentlyFocused;
                moduleManager.onWindowFocusChanged(client, currentlyFocused);
            }

            // Poll keybindings when game window is active
            keybindManager.onTick(client);

            // Execute active module logic
            moduleManager.onTick(client);
        });

        // 6. Save configuration cleanly when client shuts down
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("[Combat] Client stopping: persisting module states and configuration...");
            configManager.save();
        });

        LOGGER.info("[Combat] Client initialization complete. Default GUI toggle key: RIGHT SHIFT.");
    }
}