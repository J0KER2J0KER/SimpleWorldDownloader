package com.j0ker2j0ker.swd.client;

import com.j0ker2j0ker.swd.client.util.SwdConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Swd {

    public static final String MOD_ID = "swd";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static SwdConfig CONFIG;
    public static Path resourcepack_locations;

    private Swd() {
    }

    public static void init(Path configDir) {
        CONFIG = SwdConfig.load(configDir);
    }
}
