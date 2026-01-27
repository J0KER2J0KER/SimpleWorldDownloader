package com.j0ker2j0ker.swd.client;

import com.j0ker2j0ker.swd.client.util.SaveManager;
import com.j0ker2j0ker.swd.client.util.SwdConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.component.Component;
import net.minecraft.text.Text;

import java.util.Objects;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class SwdClient implements ClientModInitializer {

    public static SwdConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = SwdConfig.load();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.getServer() == null) {
                SaveManager.stop();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if(SaveManager.isSaving) {
                SaveManager.stop();
                SaveManager.start();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CONFIG.save();
        });


        registerCommands();
    }


    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("swd")
                            .then(literal("help")
                                    .executes(ctx -> {
                                        ctx.getSource().sendFeedback(
                                                Text.of("/swd help - Shows this menu.\n/swd saveWorldTo <World Name> - Set the name of the saved world. If a world with this name already exists, the new chunks overwrite parts of that world. Reset it with /swd default.\n/swd default - Worlds will now be saved with the default name again.")
                                        );
                                        return 1;
                                    })
                            )

                            .then(literal("saveWorldTo")
                                    .then(argument("World Name", greedyString())
                                            .executes(ctx -> {
                                                String text = getString(ctx, "World Name");
                                                String old = CONFIG.saveWorldTo;
                                                if(Objects.equals(old, "")) old = "default";
                                                else old = "\"" + old + "\"";
                                                CONFIG.saveWorldTo = text;
                                                ctx.getSource().sendFeedback(
                                                        Text.of("Your worlds will now be saved as \"" + text + "\". It was on " + old + " before. Reset it with /swd default.\n§cWarning: If a world with the name \"" + text + "\" already exists, the chunks will overwrite parts of it instead of creating a new world.")
                                                );
                                                return 1;
                                            })
                                    )
                            )

                            .then(literal("default")
                                .executes(ctx -> {
                                    String old = CONFIG.saveWorldTo;
                                    if(Objects.equals(old, "")) old = "default";
                                    else old = "\"" + old + "\"";
                                    CONFIG.saveWorldTo = "";
                                    ctx.getSource().sendFeedback(
                                            Text.of("Your worlds will now be saved as default. It was on " + old + " before.")
                                    );
                                    return 1;
                                })
                            )
            );
        });
    }
}
