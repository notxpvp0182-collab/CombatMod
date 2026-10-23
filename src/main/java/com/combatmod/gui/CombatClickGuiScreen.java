package com.combatmod.gui;

import com.combatmod.CombatModClient;
import com.combatmod.config.BooleanSetting;
import com.combatmod.config.DoubleSetting;
import com.combatmod.config.EnumSetting;
import com.combatmod.config.KeybindSetting;
import com.combatmod.config.Setting;
import com.combatmod.module.Category;
import com.combatmod.module.Module;
import com.combatmod.module.combat.AimAssistModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CombatClickGuiScreen extends Screen {
    private static final int COL_BG_WINDOW = 0xF2121317;
    private static final int COL_BG_SIDEBAR = 0xF5181A20;
    private static final int COL_BG_PANEL = 0xF21A1D24;
    private static final int COL_BG_CARD = 0xFF222630;
    private static final int COL_BG_CARD_HOVER = 0xFF2A2F3B;
    private static final int COL_ACCENT_GREEN = 0xFF39C272;
    private static final int COL_ACCENT_RED = 0xFFE04646;
    private static final int COL_TEXT_PRIMARY = 0xFFF0F2F5;
    private static final int COL_TEXT_MUTED = 0xFF9AA0AC;
    private static final int COL_BORDER = 0xFF2E3340;

    private Category selectedCategory = Category.COMBAT;
    private Module selectedModuleForSettings = null;
    private String searchQuery = "";
    private boolean searchFocused = false;

    private DoubleSetting draggingSlider = null;
    private int draggingSliderX = 0;
    private int draggingSliderWidth = 1;

    private boolean isDraggingHud = false;
    private int hudDragOffsetX = 0;
    private int hudDragOffsetY = 0;

    private KeybindSetting listeningKeybind = null;
    private boolean listeningForGuiKey = false;

    private double settingsScrollOffset = 0.0;
    private int maxSettingsScroll = 0;
    private float openAnimationProgress = 0.0f;

    public CombatClickGuiScreen() {
        super(Text.literal("Combat Click GUI"));
    }

    @Override
    protected void init() {
        super.init();
        openAnimationProgress = 0.25f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        draggingSlider = null;
        isDraggingHud = false;
        listeningKeybind = null;
        listeningForGuiKey = false;
        CombatModClient.getInstance().getConfigManager().save();
    }

    private int getWindowWidth() {
        return Math.min(500, this.width - 24);
    }

    private int getWindowHeight() {
        return Math.min(310, this.height - 24);
    }

    private int getWindowX() {
        return (this.width - getWindowWidth()) / 2;
    }

    private int getWindowY() {
        return (this.height - getWindowHeight()) / 2;
    }

    private List<Module> getFilteredModules() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        return CombatModClient.getInstance().getModuleManager().getModulesByCategory(selectedCategory)
                .stream()
                .filter(m -> q.isEmpty()
                        || m.getName().toLowerCase(Locale.ROOT).contains(q)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(q))
                .collect(Collectors.toList());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openAnimationProgress = MathHelper.clamp(openAnimationProgress + delta * 0.18f, 0.0f, 1.0f);

        int winW = getWindowWidth();
        int winH = (int) (getWindowHeight() * (0.85f + 0.15f * openAnimationProgress));
        int winX = getWindowX();
        int winY = getWindowY();

        context.fill(winX - 1, winY - 1, winX + winW + 1, winY + winH + 1, COL_BORDER);
        context.fill(winX, winY, winX + winW, winY + winH, COL_BG_WINDOW);
        context.fill(winX, winY, winX + winW, winY + 2, COL_ACCENT_RED);

        // Header
        context.fill(winX, winY + 2, winX + winW, winY + 30, COL_BG_SIDEBAR);
        context.drawTextWithShadow(this.textRenderer, "COMBAT CLIENT", winX + 10, winY + 11, COL_TEXT_PRIMARY);

        // Search box
        int searchX = winX + 115;
        int searchY = winY + 7;
        int searchW = 165;
        int searchH = 18;
        int searchBorder = searchFocused ? COL_ACCENT_GREEN : COL_BORDER;
        context.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + searchH + 1, searchBorder);
        context.fill(searchX, searchY, searchX + searchW, searchY + searchH, COL_BG_CARD);
        String displaySearch = searchQuery.isEmpty() && !searchFocused
                ? "Search modules..."
                : searchQuery + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        int searchTextColor = searchQuery.isEmpty() && !searchFocused ? COL_TEXT_MUTED : COL_TEXT_PRIMARY;
        context.drawTextWithShadow(this.textRenderer, displaySearch, searchX + 6, searchY + 5, searchTextColor);

        // GUI Keybind button
        int guiKeyBtnW = 96;
        int guiKeyBtnX = winX + winW - guiKeyBtnW - 82;
        int guiKeyBtnY = winY + 7;
        boolean hoverGuiKey = isHovered(mouseX, mouseY, guiKeyBtnX, guiKeyBtnY, guiKeyBtnW, 18);
        context.fill(guiKeyBtnX, guiKeyBtnY, guiKeyBtnX + guiKeyBtnW, guiKeyBtnY + 18, hoverGuiKey ? COL_BG_CARD_HOVER : COL_BG_CARD);
        String guiKeyLabel = listeningForGuiKey
                ? "GUI Key: [...]"
                : "GUI: " + formatKey(CombatModClient.getInstance().getConfigManager().getGuiToggleKey());
        context.drawTextWithShadow(this.textRenderer, guiKeyLabel, guiKeyBtnX + 5, guiKeyBtnY + 5, COL_TEXT_MUTED);

        // Reset All button
        int resetBtnW = 70;
        int resetBtnX = winX + winW - resetBtnW - 8;
        int resetBtnY = winY + 7;
        boolean hoverReset = isHovered(mouseX, mouseY, resetBtnX, resetBtnY, resetBtnW, 18);
        context.fill(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + 18, hoverReset ? COL_ACCENT_RED : COL_BG_CARD);
        context.drawTextWithShadow(this.textRenderer, "Reset All", resetBtnX + 11, resetBtnY + 5, COL_TEXT_PRIMARY);

        // Sidebar
        int sidebarW = 92;
        int contentTop = winY + 31;
        int contentBottom = winY + winH;
        context.fill(winX, contentTop, winX + sidebarW, contentBottom, COL_BG_SIDEBAR);

        int catY = contentTop + 10;
        for (Category category : Category.values()) {
            boolean active = category == selectedCategory;
            int bg = active ? COL_BG_CARD_HOVER : COL_BG_SIDEBAR;
            context.fill(winX + 4, catY, winX + sidebarW - 4, catY + 22, bg);
            if (active) {
                context.fill(winX + 4, catY, winX + 7, catY + 22, COL_ACCENT_RED);
            }
            context.drawTextWithShadow(this.textRenderer, category.getDisplayName(), winX + 14, catY + 7,
                    active ? COL_TEXT_PRIMARY : COL_TEXT_MUTED);
            catY += 26;
        }

        // Module list
        int modListX = winX + sidebarW + 6;
        int modListW = selectedModuleForSettings == null ? (winW - sidebarW - 12) : 165;
        int modY = contentTop + 8;

        for (Module module : getFilteredModules()) {
            boolean hovered = isHovered(mouseX, mouseY, modListX, modY, modListW, 30);
            boolean settingsOpen = module == selectedModuleForSettings;

            context.fill(modListX, modY, modListX + modListW, modY + 30,
                    hovered || settingsOpen ? COL_BG_CARD_HOVER : COL_BG_CARD);

            int statusCol = module.isEnabled() ? COL_ACCENT_GREEN : COL_ACCENT_RED;
            context.fill(modListX, modY, modListX + 3, modY + 30, statusCol);

            context.drawTextWithShadow(this.textRenderer, module.getName(), modListX + 8, modY + 6, COL_TEXT_PRIMARY);
            String stateBadge = module.isEnabled() ? "ON" : "OFF";
            int badgeW = this.textRenderer.getWidth(stateBadge);
            context.drawTextWithShadow(this.textRenderer, stateBadge, modListX + modListW - badgeW - 6, modY + 6, statusCol);

            String hint = settingsOpen ? "Right-click: close" : "L: Toggle | R: Settings";
            context.drawTextWithShadow(this.textRenderer, hint, modListX + 8, modY + 18, COL_TEXT_MUTED);

            modY += 35;
        }

        // Settings panel
        if (selectedModuleForSettings != null) {
            int panelX = modListX + modListW + 6;
            int panelW = (winX + winW) - panelX - 6;
            int panelTop = contentTop + 8;
            int panelBottom = contentBottom - 8;

            context.fill(panelX, panelTop, panelX + panelW, panelBottom, COL_BG_PANEL);
            context.fill(panelX, panelTop, panelX + panelW, panelTop + 22, COL_BG_CARD);
            context.drawTextWithShadow(this.textRenderer,
                    selectedModuleForSettings.getName() + " Settings", panelX + 8, panelTop + 7, COL_TEXT_PRIMARY);

            int modResetW = 42;
            int modResetX = panelX + panelW - modResetW - 4;
            int modResetY = panelTop + 3;
            boolean hoverModReset = isHovered(mouseX, mouseY, modResetX, modResetY, modResetW, 16);
            context.fill(modResetX, modResetY, modResetX + modResetW, modResetY + 16,
                    hoverModReset ? COL_ACCENT_RED : COL_BG_WINDOW);
            context.drawTextWithShadow(this.textRenderer, "Reset", modResetX + 7, modResetY + 4, COL_TEXT_PRIMARY);

            int scissorTop = panelTop + 24;
            int scissorBottom = panelBottom - 4;
            context.enableScissor(panelX, scissorTop, panelX + panelW, scissorBottom);

            int settingY = scissorTop + 4 - (int) settingsScrollOffset;
            int contentHeight = 0;

            for (Setting<?> setting : selectedModuleForSettings.getSettings()) {
                int rowHeight = renderSettingControl(context, setting, panelX + 6, settingY, panelW - 14, mouseX, mouseY);
                settingY += rowHeight + 5;
                contentHeight += rowHeight + 5;
            }

            int visibleHeight = scissorBottom - scissorTop;
            maxSettingsScroll = Math.max(0, contentHeight - visibleHeight + 10);
            settingsScrollOffset = MathHelper.clamp(settingsScrollOffset, 0.0, maxSettingsScroll);

            context.disableScissor();

            if (maxSettingsScroll > 0) {
                int trackH = scissorBottom - scissorTop;
                int thumbH = Math.max(18, (int) ((double) visibleHeight / (contentHeight + 10) * trackH));
                int thumbY = scissorTop + (int) ((settingsScrollOffset / maxSettingsScroll) * (trackH - thumbH));
                context.fill(panelX + panelW - 3, scissorTop, panelX + panelW - 1, scissorBottom, 0xFF14161B);
                context.fill(panelX + panelW - 3, thumbY, panelX + panelW - 1, thumbY + thumbH, COL_ACCENT_GREEN);
            }
        }
    }

    private int renderSettingControl(DrawContext context, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY) {
        if (setting instanceof BooleanSetting boolSetting) {
            boolean hovered = isHovered(mouseX, mouseY, x, y, width, 22);
            context.fill(x, y, x + width, y + 22, hovered ? COL_BG_CARD_HOVER : COL_BG_CARD);
            context.drawTextWithShadow(this.textRenderer, boolSetting.getName(), x + 6, y + 7, COL_TEXT_PRIMARY);

            int pillW = 30;
            int pillX = x + width - pillW - 6;
            int pillY = y + 4;
            boolean on = boolSetting.getValue();
            context.fill(pillX, pillY, pillX + pillW, pillY + 14, on ? COL_ACCENT_GREEN : COL_ACCENT_RED);
            String text = on ? "ON" : "OFF";
            context.drawTextWithShadow(this.textRenderer, text, pillX + 6, pillY + 3, COL_TEXT_PRIMARY);
            return 22;
        }

        if (setting instanceof DoubleSetting doubleSetting) {
            context.fill(x, y, x + width, y + 30, COL_BG_CARD);
            String label = String.format(Locale.ROOT, "%s: %.1f", doubleSetting.getName(), doubleSetting.getValue());
            context.drawTextWithShadow(this.textRenderer, label, x + 6, y + 5, COL_TEXT_PRIMARY);

            int barX = x + 6;
            int barY = y + 20;
            int barW = width - 12;
            context.fill(barX, barY, barX + barW, barY + 5, COL_BG_WINDOW);

            double pct = (doubleSetting.getValue() - doubleSetting.getMin())
                    / (doubleSetting.getMax() - doubleSetting.getMin());
            pct = MathHelper.clamp(pct, 0.0, 1.0);
            int fillW = (int) (barW * pct);
            context.fill(barX, barY, barX + fillW, barY + 5, COL_ACCENT_GREEN);
            return 30;
        }

        if (setting instanceof EnumSetting<?> enumSetting) {
            boolean hovered = isHovered(mouseX, mouseY, x, y, width, 22);
            context.fill(x, y, x + width, y + 22, hovered ? COL_BG_CARD_HOVER : COL_BG_CARD);
            String text = enumSetting.getName() + ": " + enumSetting.getValue().toString();
            context.drawTextWithShadow(this.textRenderer, text, x + 6, y + 7, COL_TEXT_PRIMARY);
            return 22;
        }

        if (setting instanceof KeybindSetting keybindSetting) {
            boolean hovered = isHovered(mouseX, mouseY, x, y, width, 22);
            context.fill(x, y, x + width, y + 22, hovered ? COL_BG_CARD_HOVER : COL_BG_CARD);
            boolean listening = (listeningKeybind == keybindSetting);
            String keyStr = listening ? "Press key (ESC/DEL clears)..." : (keybindSetting.getName() + ": [" + keybindSetting.getKeyName() + "]");
            int col = listening ? COL_ACCENT_GREEN : COL_TEXT_PRIMARY;
            context.drawTextWithShadow(this.textRenderer, keyStr, x + 6, y + 7, col);
            return 22;
        }

        return 22;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        AimAssistModule aimAssist = CombatModClient.getInstance().getModuleManager().getModule(AimAssistModule.class);
        if (aimAssist != null && aimAssist.showIndicator.getValue()) {
            int hudX = HudOverlayRenderer.getRenderX(this.client, aimAssist, this.width);
            int hudY = HudOverlayRenderer.getRenderY(this.client, aimAssist, this.height);
            if (isHovered(mouseX, mouseY, hudX, hudY, HudOverlayRenderer.BOX_WIDTH, HudOverlayRenderer.BOX_HEIGHT)) {
                isDraggingHud = true;
                hudDragOffsetX = (int) mouseX - hudX;
                hudDragOffsetY = (int) mouseY - hudY;
                return true;
            }
        }

        int winW = getWindowWidth();
        int winH = getWindowHeight();
        int winX = getWindowX();
        int winY = getWindowY();

        int searchX = winX + 115;
        int searchY = winY + 7;
        searchFocused = isHovered(mouseX, mouseY, searchX, searchY, 165, 18);
        if (searchFocused && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            searchQuery = "";
            return true;
        }

        int guiKeyBtnW = 96;
        int guiKeyBtnX = winX + winW - guiKeyBtnW - 82;
        int guiKeyBtnY = winY + 7;
        if (isHovered(mouseX, mouseY, guiKeyBtnX, guiKeyBtnY, guiKeyBtnW, 18) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            listeningForGuiKey = !listeningForGuiKey;
            listeningKeybind = null;
            return true;
        }

        int resetBtnW = 70;
        int resetBtnX = winX + winW - resetBtnW - 8;
        int resetBtnY = winY + 7;
        if (isHovered(mouseX, mouseY, resetBtnX, resetBtnY, resetBtnW, 18) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CombatModClient.getInstance().getConfigManager().resetAllToDefaults();
            return true;
        }

        int sidebarW = 92;
        int contentTop = winY + 31;
        int catY = contentTop + 10;
        for (Category category : Category.values()) {
            if (isHovered(mouseX, mouseY, winX + 4, catY, sidebarW - 8, 22) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectedCategory = category;
                return true;
            }
            catY += 26;
        }

        int modListX = winX + sidebarW + 6;
        int modListW = selectedModuleForSettings == null ? (winW - sidebarW - 12) : 165;
        int modY = contentTop + 8;

        for (Module module : getFilteredModules()) {
            if (isHovered(mouseX, mouseY, modListX, modY, modListW, 30)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    module.toggle();
                    CombatModClient.getInstance().getConfigManager().save();
                    return true;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (selectedModuleForSettings == module) {
                        selectedModuleForSettings = null;
                    } else {
                        selectedModuleForSettings = module;
                        settingsScrollOffset = 0.0;
                    }
                    listeningKeybind = null;
                    return true;
                }
            }
            modY += 35;
        }

        if (selectedModuleForSettings != null) {
            int panelX = modListX + modListW + 6;
            int panelW = (winX + winW) - panelX - 6;
            int panelTop = contentTop + 8;
            int panelBottom = winY + winH - 8;

            int modResetW = 42;
            int modResetX = panelX + panelW - modResetW - 4;
            int modResetY = panelTop + 3;
            if (isHovered(mouseX, mouseY, modResetX, modResetY, modResetW, 16) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectedModuleForSettings.resetSettings();
                CombatModClient.getInstance().getConfigManager().save();
                return true;
            }

            int scissorTop = panelTop + 24;
            int scissorBottom = panelBottom - 4;
            if (isHovered(mouseX, mouseY, panelX, scissorTop, panelW, scissorBottom - scissorTop)) {
                int settingY = scissorTop + 4 - (int) settingsScrollOffset;
                int ctrlX = panelX + 6;
                int ctrlW = panelW - 14;

                for (Setting<?> setting : selectedModuleForSettings.getSettings()) {
                    int rowH = (setting instanceof DoubleSetting) ? 30 : 22;
                    if (isHovered(mouseX, mouseY, ctrlX, settingY, ctrlW, rowH)) {
                        handleSettingClick(setting, button, mouseX, ctrlX, ctrlW);
                        return true;
                    }
                    settingY += rowH + 5;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleSettingClick(Setting<?> setting, int button, double mouseX, int ctrlX, int ctrlW) {
        if (setting instanceof BooleanSetting boolSetting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolSetting.toggle();
            CombatModClient.getInstance().getConfigManager().save();
        } else if (setting instanceof DoubleSetting doubleSetting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingSlider = doubleSetting;
            draggingSliderX = ctrlX + 6;
            draggingSliderWidth = Math.max(1, ctrlW - 12);
            updateSliderFromMouse(mouseX);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                enumSetting.cycleNext();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                enumSetting.cyclePrevious();
            }
            CombatModClient.getInstance().getConfigManager().save();
        } else if (setting instanceof KeybindSetting keybindSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                listeningKeybind = (listeningKeybind == keybindSetting) ? null : keybindSetting;
                listeningForGuiKey = false;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                keybindSetting.unbind();
                if (listeningKeybind == keybindSetting) {
                    listeningKeybind = null;
                }
                CombatModClient.getInstance().getConfigManager().save();
            }
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (isDraggingHud) {
            AimAssistModule aimAssist = CombatModClient.getInstance().getModuleManager().getModule(AimAssistModule.class);
            if (aimAssist != null && this.width > 0 && this.height > 0) {
                double targetX = click.x() - hudDragOffsetX;
                double targetY = click.y() - hudDragOffsetY;
                double pctX = MathHelper.clamp((targetX / this.width) * 100.0, 0.0, 100.0);
                double pctY = MathHelper.clamp((targetY / this.height) * 100.0, 0.0, 100.0);
                aimAssist.hudPosX.setValue(pctX);
                aimAssist.hudPosY.setValue(pctY);
                return true;
            }
        }
        if (draggingSlider != null && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateSliderFromMouse(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (isDraggingHud) {
            isDraggingHud = false;
            CombatModClient.getInstance().getConfigManager().save();
            return true;
        }
        if (draggingSlider != null) {
            draggingSlider = null;
            CombatModClient.getInstance().getConfigManager().save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedModuleForSettings != null && maxSettingsScroll > 0) {
            settingsScrollOffset = MathHelper.clamp(settingsScrollOffset - verticalAmount * 16.0, 0.0, maxSettingsScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void updateSliderFromMouse(double mouseX) {
        if (draggingSlider == null) {
            return;
        }
        double pct = MathHelper.clamp((mouseX - draggingSliderX) / (double) draggingSliderWidth, 0.0, 1.0);
        double newVal = draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin());
        draggingSlider.setValue(newVal);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (listeningForGuiKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                CombatModClient.getInstance().getConfigManager().setGuiToggleKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            } else {
                CombatModClient.getInstance().getConfigManager().setGuiToggleKey(keyCode);
            }
            listeningForGuiKey = false;
            CombatModClient.getInstance().getConfigManager().save();
            return true;
        }

        if (listeningKeybind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                listeningKeybind.unbind();
            } else {
                listeningKeybind.setValue(keyCode);
            }
            listeningKeybind = null;
            CombatModClient.getInstance().getConfigManager().save();
            return true;
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (selectedModuleForSettings != null) {
                selectedModuleForSettings = null;
                return true;
            }
            this.close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused) {
            String str = input.asString();
            if (str != null && !str.isEmpty() && searchQuery.length() < 24) {
                char c = str.charAt(0);
                if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-') {
                    searchQuery += c;
                    return true;
                }
            }
        }
        return super.charTyped(input);
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String formatKey(int keyCode) {
        if (keyCode <= 0 || keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return "NONE";
        }
        try {
            InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(keyCode);
            String txt = key.getLocalizedText().getString();
            return (txt == null || txt.isBlank()) ? "KEY_" + keyCode : txt.toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "KEY_" + keyCode;
        }
    }
}
