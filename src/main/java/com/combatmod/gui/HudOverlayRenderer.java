package com.combatmod.gui;

import com.combatmod.CombatModClient;
import com.combatmod.module.combat.AimAssistModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

public class HudOverlayRenderer {
    private static final Identifier TARGET_HUD_ID = Identifier.of("combat", "target_hud");
    public static final int BOX_WIDTH = 154;
    public static final int BOX_HEIGHT = 38;

    public static void register() {
        HudElementRegistry.addLast(TARGET_HUD_ID, HudOverlayRenderer::render);
    }

    public static int getRenderX(MinecraftClient client, AimAssistModule aimAssist, int sw) {
        double pctX = aimAssist.hudPosX.getValue() / 100.0;
        int x = (int) (sw * pctX);
        return MathHelper.clamp(x, 4, Math.max(4, sw - BOX_WIDTH - 4));
    }

    public static int getRenderY(MinecraftClient client, AimAssistModule aimAssist, int sh) {
        double pctY = aimAssist.hudPosY.getValue() / 100.0;
        int y = (int) (sh * pctY);
        return MathHelper.clamp(y, 4, Math.max(4, sh - BOX_HEIGHT - 4));
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }

        AimAssistModule aimAssist = CombatModClient.getInstance().getModuleManager().getModule(AimAssistModule.class);
        if (aimAssist == null || !aimAssist.isEnabled() || !aimAssist.showIndicator.getValue()) {
            return;
        }

        LivingEntity target = aimAssist.getCurrentTarget();
        boolean isGuiEditing = client.currentScreen instanceof CombatClickGuiScreen;

        // If no target and not editing in GUI, don't show
        if (target == null && !isGuiEditing) {
            return;
        }

        TextRenderer tr = client.textRenderer;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        int x = getRenderX(client, aimAssist, sw);
        int y = getRenderY(client, aimAssist, sh);

        boolean locked = aimAssist.isTargetManuallyLocked();
        int accentColor = locked ? 0xFFE04646 : 0xFF39C272;

        // Dark charcoal background with subtle border
        context.fill(x - 1, y - 1, x + BOX_WIDTH + 1, y + BOX_HEIGHT + 1, 0xFF282C34);
        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, 0xE614161B);
        context.fill(x, y, x + BOX_WIDTH, y + 2, isGuiEditing ? 0xFFE5A93B : accentColor);

        String title = isGuiEditing && target == null 
                ? "[DRAG HUD HERE]" 
                : (locked ? "[LOCKED] " : "[TARGET] ") + (target != null ? target.getName().getString() : "Preview Entity");
        context.drawTextWithShadow(tr, title, x + 6, y + 6, 0xFFF0F2F5);

        float health = target != null ? Math.max(0.0f, target.getHealth() + target.getAbsorptionAmount()) : 20.0f;
        float maxHealth = target != null ? Math.max(1.0f, target.getMaxHealth() + target.getAbsorptionAmount()) : 20.0f;
        float healthPct = MathHelper.clamp(health / maxHealth, 0.0f, 1.0f);
        double dist = target != null ? Math.sqrt(client.player.squaredDistanceTo(target)) : 3.5;

        String info = String.format(Locale.ROOT, "HP: %.1f  |  %.1fm", health, dist);
        context.drawTextWithShadow(tr, info, x + 6, y + 17, 0xFFAEB4C0);

        int barX = x + 6;
        int barY = y + 29;
        int barW = BOX_WIDTH - 12;
        context.fill(barX, barY, barX + barW, barY + 4, 0xFF242730);
        int fillW = (int) (barW * healthPct);
        int barColor = healthPct > 0.5f ? 0xFF39C272 : (healthPct > 0.25f ? 0xFFE5A93B : 0xFFE04646);
        context.fill(barX, barY, barX + fillW, barY + 4, barColor);
    }
}
