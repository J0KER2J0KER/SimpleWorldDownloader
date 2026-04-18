package com.j0ker2j0ker.swd.client.screen;

import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class SwdConfigScreen extends Screen {
    private final Screen parent;
    private EditBox nameField;
    private Checkbox autoDownloadCheckbox;

    // layout
    private int centerX;
    private int nameLabelX, nameLabelY;
    private int autoLabelX, autoLabelY;

    // description texts
    private static final List<Component> NAME_DESC = List.of(
            Component.literal("Set the name of the saved world."),
            Component.literal("If a world with this name already exists,"),
            Component.literal("the new chunks overwrite parts of that world.")
    );

    private static final List<Component> AUTO_DESC = List.of(
            Component.literal("Set whether worlds should be downloaded"),
            Component.literal("automatically on server joining.")
    );

    public SwdConfigScreen(Screen parent) {
        super(Component.literal("Simple World Downloader Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.centerX = this.width / 2;

        // label positions (left side)
        this.nameLabelX = centerX - 180;
        this.nameLabelY = 75;
        this.autoLabelX = centerX - 180;
        this.autoLabelY = 115;

        // move inputs right + smaller text box
        int nameFieldX = centerX - 20;   // was much more left before
        int nameFieldY = 70;
        int nameFieldW = 150;            // smaller
        int nameFieldH = 20;

        int resetX = nameFieldX + nameFieldW + 10;
        int resetW = 60;

        int autoCheckboxX = centerX - 20;
        int autoCheckboxY = 110;

        this.nameField = new EditBox(this.font, nameFieldX, nameFieldY, nameFieldW, nameFieldH,
                Component.literal("World name to save as"));
        this.nameField.setMaxLength(128);
        this.nameField.setValue(SwdClient.CONFIG.saveWorldTo != null ? SwdClient.CONFIG.saveWorldTo : "");
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.nameField.setValue("");
            SwdClient.CONFIG.saveWorldTo = "";
        }).pos(resetX, nameFieldY).width(resetW).build());

        this.autoDownloadCheckbox = Checkbox.builder(Component.empty(), this.font)
                .pos(autoCheckboxX, autoCheckboxY)
                .selected(SwdClient.CONFIG.autoDownload)
                .build();
        this.addRenderableWidget(this.autoDownloadCheckbox);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            SwdClient.CONFIG.saveWorldTo = this.nameField.getValue().trim();
            SwdClient.CONFIG.autoDownload = this.autoDownloadCheckbox.selected();
            SwdClient.CONFIG.save();
            this.onClose();
        }).pos(centerX - 155, this.height - 50).width(150).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .pos(centerX + 5, this.height - 50).width(150).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.nextStratum();

        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.text(this.font, Component.literal("Save world to:"), nameLabelX, nameLabelY, 0xFFFFFFFF);
        graphics.text(this.font, Component.literal("Automatically download:"), autoLabelX, autoLabelY, 0xFFFFFFFF);

        // hover descriptions (over label text or over input widgets)
        boolean hoverNameLabel = isHovering(mouseX, mouseY, nameLabelX, nameLabelY, this.font.width("Save world to:"), 10);
        boolean hoverNameField = this.nameField != null && this.nameField.isMouseOver(mouseX, mouseY);

        boolean hoverAutoLabel = isHovering(mouseX, mouseY, autoLabelX, autoLabelY, this.font.width("Automatically download:"), 10);
        boolean hoverAutoCheckbox = this.autoDownloadCheckbox != null && this.autoDownloadCheckbox.isMouseOver(mouseX, mouseY);

        if (hoverNameLabel || hoverNameField) {
            graphics.setComponentTooltipForNextFrame(this.font, NAME_DESC, mouseX, mouseY);
        } else if (hoverAutoLabel || hoverAutoCheckbox) {
            graphics.setComponentTooltipForNextFrame(this.font, AUTO_DESC, mouseX, mouseY);
        }
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}