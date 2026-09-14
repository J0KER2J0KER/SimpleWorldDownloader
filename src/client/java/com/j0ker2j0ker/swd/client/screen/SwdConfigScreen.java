package com.j0ker2j0ker.swd.client.screen;

import com.j0ker2j0ker.swd.client.SwdClient;
import com.j0ker2j0ker.swd.client.util.SwdConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SwdConfigScreen extends Screen {
    private final Screen parent;
    private final List<SettingEntry> settings = new ArrayList<>();

    private ScrollPanel scrollPanel;

    // description texts
    private static final List<Component> NAME_DESC = List.of(
            Component.translatable("swd.tooltip.save_world_to.1"),
            Component.translatable("swd.tooltip.save_world_to.2"),
            Component.translatable("swd.tooltip.save_world_to.3")
    );

    private static final List<Component> AUTO_DESC = List.of(
            Component.translatable("swd.tooltip.auto_download.1"),
            Component.translatable("swd.tooltip.auto_download.2")
    );

    private static final List<Component> RESUME_DESC = List.of(
            Component.translatable("swd.tooltip.resume_downloads.1"),
            Component.translatable("swd.tooltip.resume_downloads.2")
    );

    private static final List<Component> NOTIFICATION_DESC = List.of(
            Component.translatable("swd.tooltip.notification_mode.1"),
            Component.translatable("swd.tooltip.notification_mode.2")
    );

    private static final List<Component> ENTITIES_DESC = List.of(
            Component.translatable("swd.tooltip.include_entities.1"),
            Component.translatable("swd.tooltip.include_entities.2")
    );

    private static final List<Component> PLAYER_DATA_DESC = List.of(
            Component.translatable("swd.tooltip.include_player_data.1"),
            Component.translatable("swd.tooltip.include_player_data.2")
    );

    private static final List<Component> RESOURCE_PACKS_DESC = List.of(
            Component.translatable("swd.tooltip.include_resource_packs.1"),
            Component.translatable("swd.tooltip.include_resource_packs.2")
    );

    public SwdConfigScreen(Screen parent) {
        super(Component.translatable("swd.screen.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.settings.clear();

        int centerX = this.width / 2;
        int contentTop = 55;
        int contentBottom = this.height - 65;
        int panelWidth = 390;
        int panelX = centerX - panelWidth / 2;
        int panelHeight = Math.max(1, contentBottom - contentTop);

        this.scrollPanel = new ScrollPanel(panelX, contentTop, panelWidth, panelHeight);

        int labelX = panelX + 8;
        int controlX = panelX + 190;

        this.settings.add(new StringSettingEntry(
                Component.translatable("swd.screen.config.label.save_world_to"),
                NAME_DESC, labelX, 15, controlX, 10, 110, 20,
                () -> SwdClient.CONFIG.saveWorldTo != null ? SwdClient.CONFIG.saveWorldTo : "",
                value -> SwdClient.CONFIG.saveWorldTo = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.auto_download"),
                AUTO_DESC, labelX, 45, controlX, 40,
                () -> SwdClient.CONFIG.autoDownload,
                value -> SwdClient.CONFIG.autoDownload = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.resume_downloads"),
                RESUME_DESC, labelX, 70, controlX, 65,
                () -> SwdClient.CONFIG.resumeDownloads,
                value -> SwdClient.CONFIG.resumeDownloads = value
        ));

        this.settings.add(new EnumSettingEntry<>(
                Component.translatable("swd.screen.config.label.notification_mode"),
                NOTIFICATION_DESC, labelX, 95, controlX, 90, 150,
                SwdConfig.NotificationMode.class,
                mode -> Component.translatable("swd.screen.config.notification_mode." + mode.name().toLowerCase()),
                () -> SwdClient.CONFIG.notificationMode,
                value -> SwdClient.CONFIG.notificationMode = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.include_entities"),
                ENTITIES_DESC, labelX, 145, controlX, 140,
                () -> SwdClient.CONFIG.includeEntities,
                value -> SwdClient.CONFIG.includeEntities = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.include_player_data"),
                PLAYER_DATA_DESC, labelX, 170, controlX, 165,
                () -> SwdClient.CONFIG.includePlayerData,
                value -> SwdClient.CONFIG.includePlayerData = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.include_resource_packs"),
                RESOURCE_PACKS_DESC, labelX, 195, controlX, 190,
                () -> SwdClient.CONFIG.includeResourcePacks,
                value -> SwdClient.CONFIG.includeResourcePacks = value
        ));

        for (SettingEntry setting : this.settings) {
            setting.addWidgets(this.scrollPanel);
        }

        this.addRenderableWidget(this.scrollPanel);

        this.addRenderableWidget(Button.builder(Component.translatable("swd.button.save"),
                        button -> {
                            for (SettingEntry setting : this.settings) {
                                setting.applyToConfig();
                            }
                            SwdClient.CONFIG.save();
                            this.onClose();
                        }).pos(centerX - 155, this.height - 45).width(150).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .pos(centerX + 5, this.height - 45).width(150).build());
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.nextStratum();
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    private final class ScrollPanel extends AbstractScrollArea {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final int panelX;
        private final int panelY;
        private final int panelWidth;
        private final int panelHeight;

        private ScrollPanel(int x, int y, int width, int height) {
            super(x, y, width, height,
                    Component.translatable("swd.screen.config.title"),
                    AbstractScrollArea.defaultSettings(12));

            this.panelX = x;
            this.panelY = y;
            this.panelWidth = width;
            this.panelHeight = height;
        }

        private void addWidget(AbstractWidget widget) {
            this.widgets.add(widget);
            this.updateChildPositions();
        }

        @Override
        public void setScrollAmount(double scrollAmount) {
            super.setScrollAmount(scrollAmount);
            this.updateChildPositions();
        }

        private void updateChildPositions() {
            int offset = (int) this.scrollAmount();
            for (SettingEntry setting : SwdConfigScreen.this.settings) {
                setting.updateWidgetPosition(this.getY(), offset);
            }
        }

        @Override
        protected int contentHeight() {
            return 235;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }

        @Override
        protected void extractWidgetRenderState(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.enableScissor(
                    this.getX(),
                    this.getY(),
                    this.getRight(),
                    this.getBottom());

            graphics.nextStratum();

            int headingY = this.getY() + 0 - (int) this.scrollAmount();
            graphics.text(
                    SwdConfigScreen.this.font,
                    Component.translatable("swd.screen.config.includes_heading"),
                    this.getX() + 8,
                    headingY,
                    0xFFAAAAAA
            );

            this.updateChildPositions();

            for (SettingEntry setting : settings) {
                setting.renderLabel(SwdConfigScreen.this.font, graphics, this.getY());
            }

            for (AbstractWidget widget : this.widgets) {
                widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            graphics.disableScissor();
            this.extractScrollbar(graphics, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!this.visible || !this.active) {
                return false;
            }

            if (this.updateScrolling(event)) {
                return true;
            }

            if (!this.isMouseOver(event.x(), event.y())) {
                return false;
            }

            for (int i = this.widgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = this.widgets.get(i);
                if (widget.mouseClicked(event, doubleClick)) {
                    this.setFocusedWidget(widget);
                    return true;
                }
            }

            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            boolean handled = false;

            for (AbstractWidget widget : this.widgets) {
                handled |= widget.mouseReleased(event);
            }

            this.onRelease(event);
            return handled;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            if (super.mouseDragged(event, dx, dy)) {
                return true;
            }

            for (AbstractWidget widget : this.widgets) {
                if (widget.mouseDragged(event, dx, dy)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void mouseMoved(double x, double y) {
            for (AbstractWidget widget : this.widgets) {
                widget.mouseMoved(x, y);
            }
        }

        @Override
        public boolean mouseScrolled(
                double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return false;
            }

            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            for (AbstractWidget widget : this.widgets) {
                if (widget.isFocused() && widget.keyPressed(event)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean keyReleased(KeyEvent event) {
            for (AbstractWidget widget : this.widgets) {
                if (widget.isFocused() && widget.keyReleased(event)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            for (AbstractWidget widget : this.widgets) {
                if (widget.isFocused() && widget.charTyped(event)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.getX()
                    && mouseX < this.getRight()
                    && mouseY >= this.getY()
                    && mouseY < this.getBottom();
        }

        private void setFocusedWidget(@Nullable GuiEventListener focused) {
            for (AbstractWidget widget : this.widgets) {
                widget.setFocused(widget == focused);
            }
        }
    }

    private abstract class SettingEntry {
        private final Component label;
        private final List<Component> tooltip;
        private final int labelX;
        private final int baseLabelY;

        protected SettingEntry(
                Component label, List<Component> tooltip, int labelX, int labelY) {
            this.label = label;
            this.tooltip = tooltip;
            this.labelX = labelX;
            this.baseLabelY = labelY;
        }

        public void renderLabel(
                net.minecraft.client.gui.Font font,
                GuiGraphicsExtractor graphics,
                int panelY) {
            int y = panelY + baseLabelY - (int) SwdConfigScreen.this.scrollPanel.scrollAmount();
            graphics.text(font, label, labelX, y, 0xFFFFFFFF);

            if (isHovered(font, graphics)) {
                graphics.setComponentTooltipForNextFrame(font, tooltip, labelX, y);
            }
        }

        protected abstract boolean isHovered(net.minecraft.client.gui.Font font, GuiGraphicsExtractor graphics);

        public abstract void addWidgets(ScrollPanel panel);

        public abstract void updateWidgetPosition(int panelY, int scrollOffset);

        public abstract void applyToConfig();

        protected static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final class StringSettingEntry extends SettingEntry {
        private final int fieldX;
        private final int baseFieldY;
        private final int fieldW;
        private final int fieldH;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private EditBox editBox;
        private Button resetButton;

        private StringSettingEntry(Component label, List<Component> tooltip, int labelX, int labelY, int fieldX, int fieldY, int fieldW, int fieldH, Supplier<String> getter, Consumer<String> setter) {
            super(label, tooltip, labelX, labelY);
            this.fieldX = fieldX;
            this.baseFieldY = fieldY;
            this.fieldW = fieldW;
            this.fieldH = fieldH;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void addWidgets(ScrollPanel panel) {
            this.editBox = new EditBox(
                    SwdConfigScreen.this.font,
                    fieldX,
                    panel.getY() + baseFieldY,
                    fieldW,
                    fieldH,
                    Component.translatable("swd.screen.config.placeholder.world_name"));
            this.editBox.setMaxLength(128);
            this.editBox.setValue(getter.get());
            panel.addWidget(this.editBox);

            this.resetButton = Button.builder(
                            Component.translatable("swd.button.reset"),
                            button -> {
                                this.setValue("");
                                SwdClient.CONFIG.saveWorldTo = "";
                            })
                    .pos(fieldX + fieldW + 10, panel.getY() + baseFieldY)
                    .width(60)
                    .build();

            panel.addWidget(this.resetButton);
        }

        @Override
        public void updateWidgetPosition(int panelY, int scrollOffset) {
            if (this.editBox != null) {
                this.editBox.setY(panelY + this.baseFieldY - scrollOffset);
            }
            if (this.resetButton != null) {
                this.resetButton.setY(panelY + this.baseFieldY - scrollOffset);
            }
        }

        @Override
        protected boolean isHovered(
                net.minecraft.client.gui.Font font, GuiGraphicsExtractor graphics) {
            return this.editBox != null && this.editBox.isMouseOver(graphics.guiWidth(), graphics.guiHeight());
        }

        @Override
        public void applyToConfig() {
            if (this.editBox != null) {
                setter.accept(this.editBox.getValue().trim());
            }
        }

        public void setValue(String value) {
            if (this.editBox != null) {
                this.editBox.setValue(value);
            }
        }
    }

    private final class BooleanSettingEntry extends SettingEntry {
        private final int checkboxX;
        private final int baseCheckboxY;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        private Checkbox checkbox;

        private BooleanSettingEntry(
                Component label, List<Component> tooltip,
                int labelX, int labelY,
                int checkboxX, int checkboxY,
                BooleanSupplier getter, Consumer<Boolean> setter) {
            super(label, tooltip, labelX, labelY);
            this.checkboxX = checkboxX;
            this.baseCheckboxY = checkboxY;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void addWidgets(ScrollPanel panel) {
            this.checkbox = Checkbox.builder(Component.empty(), SwdConfigScreen.this.font)
                    .pos(checkboxX, panel.getY() + baseCheckboxY)
                    .selected(getter.getAsBoolean())
                    .build();
            panel.addWidget(this.checkbox);
        }

        @Override
        public void updateWidgetPosition(int panelY, int scrollOffset) {
            if (this.checkbox != null) {
                this.checkbox.setY(panelY + this.baseCheckboxY - scrollOffset);
            }
        }

        @Override
        protected boolean isHovered(net.minecraft.client.gui.Font font, GuiGraphicsExtractor graphics) {
            return this.checkbox != null && this.checkbox.isMouseOver(graphics.guiWidth(), graphics.guiHeight());
        }

        @Override
        public void applyToConfig() {
            if (this.checkbox != null) {
                setter.accept(this.checkbox.selected());
            }
        }
    }

    private final class EnumSettingEntry<T extends Enum<T>> extends SettingEntry {
        private final int buttonX;
        private final int baseButtonY;
        private final int buttonW;
        private final T[] values;
        private final java.util.function.Function<T, Component> valueName;
        private final java.util.function.Supplier<T> getter;
        private final java.util.function.Consumer<T> setter;
        private CycleButton<T> button;

        private EnumSettingEntry(Component label, List<Component> tooltip, int labelX, int labelY,
                                 int buttonX, int buttonY, int buttonW,
                                 Class<T> enumClass,
                                 java.util.function.Function<T, Component> valueName,
                                 java.util.function.Supplier<T> getter,
                                 java.util.function.Consumer<T> setter) {
            super(label, tooltip, labelX, labelY);
            this.buttonX = buttonX;
            this.baseButtonY = buttonY;
            this.buttonW = buttonW;
            this.values = enumClass.getEnumConstants();
            this.valueName = valueName;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void addWidgets(ScrollPanel panel) {
            this.button = CycleButton.builder(valueName, getter.get())
                    .withValues(values)
                    .displayOnlyValue()
                    .create(buttonX, panel.getY() + baseButtonY, buttonW, 20, Component.empty());
            panel.addWidget(this.button);
        }

        @Override
        public void updateWidgetPosition(int panelY, int scrollOffset) {
            if (this.button != null) {
                this.button.setY(panelY + this.baseButtonY - scrollOffset);
            }
        }

        @Override
        protected boolean isHovered(
                net.minecraft.client.gui.Font font, GuiGraphicsExtractor graphics) {
            return button != null && button.isMouseOver(
                    graphics.guiWidth(), graphics.guiHeight());
        }

        @Override
        public void applyToConfig() {
            if (this.button != null) {
                setter.accept(this.button.getValue());
            }
        }
    }
}