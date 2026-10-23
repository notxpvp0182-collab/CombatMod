package com.combatmod.config;

import com.combatmod.CombatModClient;
import com.combatmod.module.Module;
import com.combatmod.module.ModuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private final ModuleManager moduleManager;
    private int guiToggleKey = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;

    public ConfigManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("combat.json");
    }

    public int getGuiToggleKey() {
        return guiToggleKey;
    }

    public void setGuiToggleKey(int guiToggleKey) {
        this.guiToggleKey = guiToggleKey;
    }

    public void load() {
        if (!Files.exists(configPath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                backupCorruptConfig();
                save();
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            if (root.has("guiToggleKey") && root.get("guiToggleKey").isJsonPrimitive()) {
                try {
                    this.guiToggleKey = root.get("guiToggleKey").getAsInt();
                } catch (Exception ignored) {
                }
            }

            if (root.has("modules") && root.get("modules").isJsonObject()) {
                JsonObject modulesObj = root.getAsJsonObject("modules");
                for (Module module : moduleManager.getModules()) {
                    if (!modulesObj.has(module.getName()) || !modulesObj.get(module.getName()).isJsonObject()) {
                        continue;
                    }
                    JsonObject modJson = modulesObj.getAsJsonObject(module.getName());
                    if (modJson.has("enabled") && modJson.get("enabled").isJsonPrimitive()) {
                        try {
                            boolean shouldEnable = modJson.get("enabled").getAsBoolean();
                            if (module.isPersistState()) {
                                module.setEnabled(shouldEnable);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    if (modJson.has("settings") && modJson.get("settings").isJsonObject()) {
                        JsonObject settingsJson = modJson.getAsJsonObject("settings");
                        for (Setting<?> setting : module.getSettings()) {
                            if (settingsJson.has(setting.getName())) {
                                try {
                                    setting.fromJson(settingsJson.get(setting.getName()));
                                } catch (Exception e) {
                                    CombatModClient.LOGGER.warn("Invalid setting '{}' in module '{}', using default.",
                                            setting.getName(), module.getName());
                                    setting.reset();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            CombatModClient.LOGGER.error("Failed to read config file, backing up and restoring defaults.", e);
            backupCorruptConfig();
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("guiToggleKey", guiToggleKey);

            JsonObject modulesObj = new JsonObject();
            for (Module module : moduleManager.getModules()) {
                JsonObject modJson = new JsonObject();
                modJson.addProperty("enabled", module.isPersistState() && module.isEnabled());

                JsonObject settingsJson = new JsonObject();
                for (Setting<?> setting : module.getSettings()) {
                    settingsJson.add(setting.getName(), setting.toJson());
                }
                modJson.add("settings", settingsJson);
                modulesObj.add(module.getName(), modJson);
            }
            root.add("modules", modulesObj);

            Path tempPath = configPath.resolveSibling("combat.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            CombatModClient.LOGGER.error("Failed to save Combat configuration", e);
        }
    }

    public void resetAllToDefaults() {
        this.guiToggleKey = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
        for (Module module : moduleManager.getModules()) {
            module.setEnabled(false);
            module.resetSettings();
        }
        save();
    }

    private void backupCorruptConfig() {
        try {
            if (Files.exists(configPath)) {
                Path backup = configPath.resolveSibling("combat.json.corrupt-" + System.currentTimeMillis());
                Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }
}