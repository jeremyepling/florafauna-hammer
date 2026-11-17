package net.j40climb.florafauna_hammer;

import net.j40climb.florafauna_hammer.common.HammerCommands;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.*;
import net.j40climb.florafauna_hammer.setup.ClientSetup;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.j40climb.florafauna_hammer.test.HammerGameTests;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Arrays;

@Mod(HammerMod.MOD_ID)
public final class HammerMod {
    public static final String MOD_ID = "florafauna_hammer";
    // Existing worlds and item stacks reference these IDs.
    public static final String CONTENT_NAMESPACE = "florafauna";

    public HammerMod(IEventBus bus) {
        HammerRegistry.register(bus);
        bus.addListener(HammerMod::networking);
        bus.addListener(HammerMod::creativeTab);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> HammerCommands.register(event.getDispatcher()));
        if (FMLLoader.getCurrent().getDist().isClient()) ClientSetup.init(bus);
        var namespaces = System.getProperty("neoforge.enabledGameTestNamespaces", "").split(",");
        if (Arrays.asList(namespaces).contains(MOD_ID)) bus.addListener(HammerGameTests::registerTests);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (HammerStage stage : HammerStage.values()) event.accept(stage.createStack());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(HammerRegistry.RESONANCE_CORE);
            event.accept(HammerRegistry.TEMPEST_CORE);
        }
    }

    private static void networking(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(HammerCorePayload.TYPE, HammerCorePayload.STREAM_CODEC);
        registrar.playToClient(SeismicScanPayload.TYPE, SeismicScanPayload.STREAM_CODEC);
        registrar.playToServer(SpawnLightningPayload.TYPE, SpawnLightningPayload.STREAM_CODEC, SpawnLightningPayload::onServerReceived);
        registrar.playToServer(TeleportToSurfacePayload.TYPE, TeleportToSurfacePayload.STREAM_CODEC, TeleportToSurfacePayload::onServerReceived);
        registrar.versioned("3").playToServer(UpdateToolConfigPayload.TYPE, UpdateToolConfigPayload.STREAM_CODEC, UpdateToolConfigPayload::onServerReceived);
        registrar.playToServer(ChargedThrowPayload.TYPE, ChargedThrowPayload.STREAM_CODEC, ChargedThrowPayload::handle);
        registrar.playToServer(ThrowItemPayload.TYPE, ThrowItemPayload.STREAM_CODEC, ThrowItemPayload::onServerReceived);
        registrar.playToServer(CycleMiningModePayload.TYPE, CycleMiningModePayload.STREAM_CODEC, CycleMiningModePayload::onServerReceived);
        registrar.playToServer(SelectMiningModePayload.TYPE, SelectMiningModePayload.STREAM_CODEC, SelectMiningModePayload::onServerReceived);
        registrar.playToServer(SelectHammerActionPayload.TYPE, SelectHammerActionPayload.STREAM_CODEC, SelectHammerActionPayload::onServerReceived);
    }
}
