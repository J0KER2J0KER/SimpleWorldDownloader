package com.j0ker2j0ker.swd.client.screen;

import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class SwdConfigScreen extends Screen {
    private final Screen parent;

    // layout
    private int centerX;
    private int includesHeaderX;
    private int includesHeaderY;

    private final List<SettingEntry> settings = new java.util.ArrayList<>();

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
        this.centerX = this.width / 2;
        this.settings.clear();

        // label positions (left side)
        int nameLabelX = centerX - 180;
        int nameLabelY = 75;
        int autoLabelX = centerX - 180;
        int autoLabelY = 100;
        int resumeLabelX = centerX - 180;
        int resumeLabelY = 120;
        this.includesHeaderX = centerX - 180;
        this.includesHeaderY = 145;
        int entitiesLabelX = centerX - 180;
        int entitiesLabelY = 170;
        int playerDataLabelX = centerX - 180;
        int playerDataLabelY = 190;
        int resourcePacksLabelX = centerX - 180;
        int resourcePacksLabelY = 210;

        // move inputs right + smaller text box
        int nameFieldX = centerX - 20;
        int nameFieldY = 70;
        int nameFieldW = 150;
        int nameFieldH = 20;

        int resetX = nameFieldX + nameFieldW + 10;
        int resetW = 60;

        int autoCheckboxX = centerX - 20;
        int autoCheckboxY = 95;
        int resumeCheckboxX = centerX - 20;
        int resumeCheckboxY = 115;
        int entitiesCheckboxX = centerX - 20;
        int entitiesCheckboxY = 165;
        int playerDataCheckboxX = centerX - 20;
        int playerDataCheckboxY = 185;
        int resourcePacksCheckboxX = centerX - 20;
        int resourcePacksCheckboxY = 205;

        // dynamic settings list
        this.settings.add(new StringSettingEntry(
                Component.translatable("swd.screen.config.label.save_world_to"),
                NAME_DESC,
                nameLabelX,
                nameLabelY,
                nameFieldX,
                nameFieldY,
                nameFieldW,
                nameFieldH,
                () -> SwdClient.CONFIG.saveWorldTo != null ? SwdClient.CONFIG.saveWorldTo : "",
                value -> SwdClient.CONFIG.saveWorldTo = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.auto_download"),
                AUTO_DESC,
                autoLabelX,
                autoLabelY,
                autoCheckboxX,
                autoCheckboxY,
                () -> SwdClient.CONFIG.autoDownload,
                value -> SwdClient.CONFIG.autoDownload = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.resume_downloads"),
                RESUME_DESC,
                resumeLabelX,
                resumeLabelY,
                resumeCheckboxX,
                resumeCheckboxY,
                () -> SwdClient.CONFIG.resumeDownloads,
                value -> SwdClient.CONFIG.resumeDownloads = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.include_entities"),
                ENTITIES_DESC,
                entitiesLabelX,
                entitiesLabelY,
                entitiesCheckboxX,
                entitiesCheckboxY,
                () -> SwdClient.CONFIG.includeEntities,
                value -> SwdClient.CONFIG.includeEntities = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.include_player_data"),
                PLAYER_DATA_DESC,
                playerDataLabelX,
                playerDataLabelY,
                playerDataCheckboxX,
                playerDataCheckboxY,
                () -> SwdClient.CONFIG.includePlayerData,
                value -> SwdClient.CONFIG.includePlayerData = value
        ));

        this.settings.add(new BooleanSettingEntry(
                Component.translatable("swd.screen.config.label.include_resource_packs"),
                RESOURCE_PACKS_DESC,
                resourcePacksLabelX,
                resourcePacksLabelY,
                resourcePacksCheckboxX,
                resourcePacksCheckboxY,
                () -> SwdClient.CONFIG.includeResourcePacks,
                value -> SwdClient.CONFIG.includeResourcePacks = value
        ));

        for (SettingEntry setting : this.settings) {
            setting.addWidgets(this);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("swd.button.reset"), b -> {
            for (SettingEntry setting : this.settings) {
                if (setting instanceof StringSettingEntry stringSetting) {
                    stringSetting.setValue("");
                }
            }
            SwdClient.CONFIG.saveWorldTo = "";
        }).pos(resetX, nameFieldY).width(resetW).build());

        this.addRenderableWidget(Button.builder(Component.translatable("swd.button.save"), b -> {
            for (SettingEntry setting : this.settings) {
                setting.applyToConfig();
            }
            SwdClient.CONFIG.save();
            this.onClose();
        }).pos(centerX - 155, this.height - 50).width(150).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .pos(centerX + 5, this.height - 50).width(150).build());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.nextStratum();

        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.pose().translate(includesHeaderX, includesHeaderY);
        graphics.pose().scale(1.35f, 1.35f);
        graphics.text(this.font, Component.translatable("swd.screen.config.includes_heading"), 0, 0, 0xFFAAAAAA);
        graphics.pose().scale(0.7407407f, 0.7407407f);
        graphics.pose().translate(-includesHeaderX, -includesHeaderY);

        for (SettingEntry setting : this.settings) {
            setting.renderLabel(this.font, graphics);
        }

        for (SettingEntry setting : this.settings) {
            if (setting.isHovered(this.font, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, setting.getTooltip(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    private abstract static class SettingEntry {
        private final Component label;
        private final List<Component> tooltip;
        private final int labelX;
        private final int labelY;

        protected SettingEntry(Component label, List<Component> tooltip, int labelX, int labelY) {
            this.label = label;
            this.tooltip = tooltip;
            this.labelX = labelX;
            this.labelY = labelY;
        }

        public List<Component> getTooltip() {
            return tooltip;
        }

        public void renderLabel(net.minecraft.client.gui.Font font, GuiGraphicsExtractor graphics) {
            graphics.text(font, label, labelX, labelY, 0xFFFFFFFF);
        }

        public boolean isHovered(net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
            int labelW = font.width(label);
            boolean hoverLabel = isHovering(mouseX, mouseY, labelX, labelY, labelW, 10);
            return hoverLabel || isWidgetHovered(mouseX, mouseY);
        }

        protected abstract boolean isWidgetHovered(int mouseX, int mouseY);

        public abstract void addWidgets(SwdConfigScreen screen);

        public abstract void applyToConfig();

        protected boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private static final class StringSettingEntry extends SettingEntry {
        private final int fieldX;
        private final int fieldY;
        private final int fieldW;
        private final int fieldH;
        private final java.util.function.Supplier<String> getter;
        private final java.util.function.Consumer<String> setter;
        private EditBox editBox;

        private StringSettingEntry(Component label, List<Component> tooltip, int labelX, int labelY,
                                   int fieldX, int fieldY, int fieldW, int fieldH,
                                   java.util.function.Supplier<String> getter,
                                   java.util.function.Consumer<String> setter) {
            super(label, tooltip, labelX, labelY);
            this.fieldX = fieldX;
            this.fieldY = fieldY;
            this.fieldW = fieldW;
            this.fieldH = fieldH;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void addWidgets(SwdConfigScreen screen) {
            this.editBox = new EditBox(screen.font, fieldX, fieldY, fieldW, fieldH,
                    Component.translatable("swd.screen.config.placeholder.world_name"));
            this.editBox.setMaxLength(128);
            this.editBox.setValue(getter.get());
            screen.addRenderableWidget(this.editBox);
        }

        @Override
        protected boolean isWidgetHovered(int mouseX, int mouseY) {
            return this.editBox != null && this.editBox.isMouseOver(mouseX, mouseY);
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

    private static final class BooleanSettingEntry extends SettingEntry {
        private final int checkboxX;
        private final int checkboxY;
        private final java.util.function.BooleanSupplier getter;
        private final java.util.function.Consumer<Boolean> setter;
        private Checkbox checkbox;

        private BooleanSettingEntry(Component label, List<Component> tooltip, int labelX, int labelY,
                                    int checkboxX, int checkboxY,
                                    java.util.function.BooleanSupplier getter,
                                    java.util.function.Consumer<Boolean> setter) {
            super(label, tooltip, labelX, labelY);
            this.checkboxX = checkboxX;
            this.checkboxY = checkboxY;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void addWidgets(SwdConfigScreen screen) {
            this.checkbox = Checkbox.builder(Component.empty(), screen.font)
                    .pos(checkboxX, checkboxY)
                    .selected(getter.getAsBoolean())
                    .build();
            screen.addRenderableWidget(this.checkbox);
        }

        @Override
        protected boolean isWidgetHovered(int mouseX, int mouseY) {
            return this.checkbox != null && this.checkbox.isMouseOver(mouseX, mouseY);
        }

        @Override
        public void applyToConfig() {
            if (this.checkbox != null) {
                setter.accept(this.checkbox.selected());
            }
        }
    }
}

