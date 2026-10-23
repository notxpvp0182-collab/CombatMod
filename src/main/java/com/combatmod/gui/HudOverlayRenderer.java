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

    public static void register() {
        HudElementRegistry.addLast(TARGET_HUD_ID, HudOverlayRenderer::render);
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
        if (target == null || !target.isAlive()) {
            return;
        }

        TextRenderer tr = client.textRenderer;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        int boxWidth = 148;
        int boxHeight = 36;
        int x = (sw / 2) + 16;
        int y = (sh / 2) + 10;

        boolean locked = aimAssist.isTargetManuallyLocked();
        int accentColor = locked ? 0xFFE04646 : 0xFF39C272;

        // Dark charcoal background with top accent bar
        context.fill(x, y, x + boxWidth, y + boxHeight, 0xD814161A);
        context.fill(x, y, x + boxWidth, y + 2, accentColor);

        String statusTag = locked ? "[LOCKED] " : "[TARGET] ";
        String nameText = statusTag + target.getName().getString();
        context.drawTextWithShadow(tr, nameText, x + 6, y + 6, 0xFFF0F2F5);

        float health = Math.max(0.0f, target.getHealth() + target.getAbsorptionAmount());
        float maxHealth = Math.max(1.0f, target.getMaxHealth() + target.getAbsorptionAmount());
        float healthPct = MathHelper.clamp(health / maxHealth, 0.0f, 1.0f);
        double dist = Math.sqrt(client.player.squaredDistanceTo(target));

        String infoLine = String.format(Locale.ROOT, "HP: %.1f  |  %.1fm", health, dist);
        context.drawTextWithShadow(tr, infoLine, x + 6, y + 17, 0xFFAEB4C0);

        // Health bar track & fill
        int barX = x + 6;
        int barY = y + 29;
        int barW = boxWidth - 12;
        context.fill(barX, barY, barX + barW, barY + 4, 0xFF282C34);
        int fillW = (int) (barW * healthPct);
        int barColor = healthPct > 0.5f ? 0xFF39C272 : (healthPct > 0.25f ? 0xFFE5A93B : 0xFFE04646);
        context.fill(barX, barY, barX + fillW, barY + 4, barColor);
    }
}