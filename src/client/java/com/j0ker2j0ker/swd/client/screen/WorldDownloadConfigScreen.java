package com.j0ker2j0ker.swd.client.screen;

import com.j0ker2j0ker.swd.client.save.SaveConfig;
import com.j0ker2j0ker.swd.client.save.SaveManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;

import static com.j0ker2j0ker.swd.client.SwdClient.CONFIG;

public class WorldDownloadConfigScreen extends Screen {
    private static final int CHECKBOX_Y_SPACE = 25;

    private final Screen parent;
    private SaveConfig config;

    public WorldDownloadConfigScreen(Screen parent, SaveConfig initialConfig) {
        super(Component.literal("Configure World Download Save Data"));
        this.parent = parent;
        this.config = initialConfig;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        addSettingsWidgets();

        // Action buttons
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), _ -> onClose())
                .pos(centerX - 155, this.height - 50).width(150).build());

        this.addRenderableWidget(Button.builder(Component.literal("Start Downloading"), _ -> {
            SaveManager.start(config);
            CONFIG.defaultSaveConfig = config;
            CONFIG.save();
            minecraft.setScreen(null);
        }).pos(centerX + 5, this.height - 50).width(150).build());
    }

    private void addSettingsWidgets() {
        int centerX = this.width / 2;
        int y = 70;

        // Settings
        addRenderableWidget(createCheckbox(
                Component.literal("Entities"), null,
                centerX-155, y,
                config.includeEntities, (_, val) -> config.includeEntities = val
        ));
        addRenderableWidget(createCheckbox(
                Component.literal("Player data"), Component.literal("This option includes:\n- General player data\n- Advancements\n- Statistics"),
                centerX+5, y,
                config.includeEntities, (_, val) -> config.includeEntities = val
        ));
        this.addRenderableWidget(Checkbox.builder(Component.literal("Player data"), font)
                .tooltip(Tooltip.create(Component.literal("This option includes:")))
                .selected(config.includePlayerData)
                .onValueChange((_, val) -> config.includePlayerData = val)
                .pos(centerX+5, y).maxWidth(150).build()
        );
        y += CHECKBOX_Y_SPACE;
        this.addRenderableWidget(Checkbox.builder(Component.literal("Resource packs"), font)
                .selected(config.includeResourcePacks)
                .onValueChange((_, val) -> config.includeResourcePacks = val)
                .pos(centerX-155, y).maxWidth(150).build()
        );
    }

    private Checkbox createCheckbox(Component name, Component tooltip, int x, int y, boolean initialValue, Checkbox.OnValueChange valueChange) {
        Checkbox cb = Checkbox.builder(name, font)
                .selected(initialValue)
                .onValueChange(valueChange)
                .pos(x, y).maxWidth(150).build();
        if (tooltip != null) cb.setTooltip(Tooltip.create(tooltip));
        return cb;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int centerX = this.width / 2;

        graphics.centeredText(this.font, this.title, centerX, 20, 0xFFFFFFFF);

        graphics.text(font, Component.literal("The world download will include:"), centerX-155, 50, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
