package com.combatmod.module;

import com.combatmod.config.KeybindSetting;
import com.combatmod.config.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeybindSetting toggleKeybind;
    private boolean enabled = false;
    private boolean persistState = true;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.toggleKeybind = addSetting(new KeybindSetting(
                "ToggleKey",
                "Keybind to toggle " + name + " ON or OFF",
                GLFW.GLFW_KEY_UNKNOWN
        ));
    }

    protected <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public KeybindSetting getToggleKeybind() {
        return toggleKeybind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPersistState() {
        return persistState;
    }

    protected void setPersistState(boolean persistState) {
        this.persistState = persistState;
    }

    public void setEnabled(boolean shouldEnable) {
        if (this.enabled == shouldEnable) {
            return;
        }
        this.enabled = shouldEnable;
        MinecraftClient client = MinecraftClient.getInstance();
        if (this.enabled) {
            onEnable(client);
        } else {
            onDisable(client);
        }
    }

    public void toggle() {
        setEnabled(!this.enabled);
    }

    public void resetSettings() {
        for (Setting<?> setting : settings) {
            setting.reset();
        }
    }

    protected void onEnable(MinecraftClient client) {
    }

    protected void onDisable(MinecraftClient client) {
    }

    public abstract void onTick(MinecraftClient client);
}