package com.combatmod.module;

import com.combatmod.module.combat.AimAssistModule;
import com.combatmod.module.combat.AutoShieldBreakerModule;
import com.combatmod.module.combat.TriggerBotModule;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void registerModules() {
        modules.clear();
        modules.add(new AimAssistModule());
        modules.add(new TriggerBotModule());
        modules.add(new AutoShieldBreakerModule());
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModulesByCategory(Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module module : modules) {
            if (clazz.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }

    public void onTick(MinecraftClient client) {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick(client);
            }
        }
    }

    public void onWindowFocusChanged(MinecraftClient client, boolean focused) {
        if (!focused) {
            // Release any temporary target locks or slot holds cleanly if needed
            AimAssistModule aimAssist = getModule(AimAssistModule.class);
            if (aimAssist != null && aimAssist.unlockOnFocusLost.getValue()) {
                aimAssist.clearManualLock();
            }
        }
    }
}