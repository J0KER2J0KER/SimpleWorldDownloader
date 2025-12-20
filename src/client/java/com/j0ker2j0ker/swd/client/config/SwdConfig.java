package com.j0ker2j0ker.swd.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "swd")
public class SwdConfig implements ConfigData
{

    @ConfigEntry.Gui.Tooltip
    public boolean showMessages = true;

    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip(count = 2)
    @ConfigEntry.BoundedDiscrete(min = 0, max = 2)
    public int buttonSide = 0;

    @ConfigEntry.Gui.Tooltip(count = 2)
    @ConfigEntry.BoundedDiscrete(min = 0, max = 1)
    public int buttonHeight = 1;
}
