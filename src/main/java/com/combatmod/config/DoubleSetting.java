package com.combatmod.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class DoubleSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public DoubleSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, clampAndStep(defaultValue, min, max, step));
        this.min = min;
        this.max = max;
        this.step = step <= 0.0 ? 0.01 : step;
        setValue(defaultValue);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public float getFloatValue() {
        return getValue().floatValue();
    }

    public int getIntValue() {
        return (int) Math.round(getValue());
    }

    @Override
    protected Double validate(Double incoming) {
        if (incoming == null || Double.isNaN(incoming) || Double.isInfinite(incoming)) {
            return getDefaultValue();
        }
        return clampAndStep(incoming, min, max, step);
    }

    private static double clampAndStep(double val, double min, double max, double step) {
        double clamped = Math.max(min, Math.min(max, val));
        if (step > 0.0) {
            clamped = Math.round(clamped / step) * step;
            clamped = Math.round(clamped * 1000.0) / 1000.0;
        }
        return Math.max(min, Math.min(max, clamped));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsDouble());
        }
    }
}