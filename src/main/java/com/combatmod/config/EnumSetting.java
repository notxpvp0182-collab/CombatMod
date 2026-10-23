package com.combatmod.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] values;

    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue);
        this.values = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public E[] getValues() {
        return values;
    }

    public void cycleNext() {
        int nextIndex = (getValue().ordinal() + 1) % values.length;
        setValue(values[nextIndex]);
    }

    public void cyclePrevious() {
        int prevIndex = (getValue().ordinal() - 1 + values.length) % values.length;
        setValue(values[prevIndex]);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue().name());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            for (E constant : values) {
                if (constant.name().equalsIgnoreCase(raw)) {
                    setValue(constant);
                    return;
                }
            }
        }
    }
}