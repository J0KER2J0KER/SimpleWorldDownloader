package com.j0ker2j0ker.swd.client;

import com.j0ker2j0ker.swd.client.screen.SwdConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuEntry implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SwdConfigScreen::new;
    }
}