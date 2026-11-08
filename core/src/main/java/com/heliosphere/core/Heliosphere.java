package com.heliosphere.core;

import com.heliosphere.core.block.CoreBlocks;
import com.heliosphere.core.command.HelioCommand;
import com.heliosphere.core.env.EnvironmentSystem;
import com.heliosphere.core.env.HelioComponents;
import com.heliosphere.core.machine.MachineMenu;
import com.heliosphere.core.net.HelioNetwork;
import com.heliosphere.core.world.WrapSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Heliosphere core library: celestial data, environments, spherical-world topology, machines and energy. */
public final class Heliosphere implements ModInitializer {
    public static final String MOD_ID = "heliosphere";
    public static final Logger LOG = LoggerFactory.getLogger("Heliosphere");

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        HelioComponents.init();
        Reg.tab("core");
        CoreBlocks.init();
        MachineMenu.init();
        HelioNetwork.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            EnvironmentSystem.tick(server);
            WrapSystem.tick(server);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> HelioCommand.register(dispatcher));
        LOG.info("Heliosphere core ready: {} celestial bodies", com.heliosphere.core.celestial.SolarSystem.all().size());
    }
}
