package com.combatmod.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String name, String description, int defaultKeyCode) {
        super(name, description, defaultKeyCode);
    }

    public boolean isBound() {
        return getValue() != GLFW.GLFW_KEY_UNKNOWN && getValue() > 0;
    }

    public void unbind() {
        setValue(GLFW.GLFW_KEY_UNKNOWN);
    }

    public String getKeyName() {
        int code = getValue();
        if (code == GLFW.GLFW_KEY_UNKNOWN || code <= 0) {
            return "NONE";
        }
        try {
            InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(code);
            String localized = key.getLocalizedText().getString();
            return (localized == null || localized.isBlank()) ? "KEY_" + code : localized.toUpperCase();
        } catch (Exception e) {
            return "KEY_" + code;
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsInt());
        }
    }
}