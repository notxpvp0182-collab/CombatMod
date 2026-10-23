package com.combatmod.config;

import com.google.gson.JsonElement;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private Consumer<T> changeListener;

    protected Setting(String name, String description, T defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T newValue) {
        if (newValue == null) {
            return;
        }
        T validated = validate(newValue);
        if (!Objects.equals(this.value, validated)) {
            this.value = validated;
            if (changeListener != null) {
                changeListener.accept(this.value);
            }
        }
    }

    public void reset() {
        setValue(defaultValue);
    }

    public Setting<T> onChanged(Consumer<T> listener) {
        this.changeListener = listener;
        return this;
    }

    protected T validate(T incoming) {
        return incoming;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}