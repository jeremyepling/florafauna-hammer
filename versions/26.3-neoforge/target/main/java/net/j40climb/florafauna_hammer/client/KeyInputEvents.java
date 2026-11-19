package net.j40climb.florafauna_hammer.client;

import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.menu.ToolConfigScreen;
import net.j40climb.florafauna_hammer.common.item.abilities.menu.HammerActionWheel;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.CycleMiningModePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.PrecisionMiningPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.multiblock.MultiBlockVisualFeedback;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.SpawnLightningPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.TeleportToSurfacePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.ThrowItemPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.ChargedThrowPayload;
import net.j40climb.florafauna_hammer.setup.ClientSetup;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HammerMod.MOD_ID, value = Dist.CLIENT)
public class KeyInputEvents {

    private static int throwKeyPlayerId = Integer.MIN_VALUE;
    private static boolean holdingThrow;
    private static int throwSlot;

    private static void updateThrowKey(Minecraft mc, ItemStack held) {
        Player player = mc.player;
        if (throwKeyPlayerId != player.getId()) {
            throwKeyPlayerId = player.getId();
            holdingThrow = false;
        }
        var key = ClientSetup.THROW_ITEM_KEY.get();
        if (holdingThrow && (mc.gui.screen() != null || player.getInventory().getSelectedSlot() != throwSlot
                || !held.is(HammerRegistry.HAMMER.get()))) {
            ClientPacketDistributor.sendToServer(new ChargedThrowPayload(ChargedThrowPayload.CANCEL));
            holdingThrow = false;
        }
        while (key.consumeClick()) {
            if (held.is(HammerRegistry.HAMMER.get())) {
                if (mc.gui.screen() == null && !holdingThrow && held.has(HammerRegistry.THROWABLE_ABILITY)) {
                    holdingThrow = true;
                    throwSlot = player.getInventory().getSelectedSlot();
                    ClientPacketDistributor.sendToServer(new ChargedThrowPayload(ChargedThrowPayload.PRESS));
                }
            } else if (held.has(HammerRegistry.THROWABLE_ABILITY)) {
                player.swing(InteractionHand.MAIN_HAND, held.getAttackAnimation(), false);
                ClientPacketDistributor.sendToServer(new ThrowItemPayload(true));
            }
        }
        if (holdingThrow && !key.isDown()) {
            holdingThrow = false;
            player.swing(InteractionHand.MAIN_HAND, held.getAttackAnimation(), false);
            ClientPacketDistributor.sendToServer(new ChargedThrowPayload(ChargedThrowPayload.RELEASE));
        }
    }

    @SubscribeEvent
    public static void beforeClientTick(ClientTickEvent.Pre event) {
        updatePrecision(Minecraft.getInstance());
    }

    // Sync before the vanilla mining packet, including a press and an instant break in the same tick.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeMining(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().level().isClientSide()) updatePrecision(Minecraft.getInstance());
    }

    private static void updatePrecision(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;
        boolean held = mc.gui.screen() == null && ClientSetup.PRECISION_MINING_KEY.get().isDown()
                && mc.player.getMainHandItem().has(HammerRegistry.MULTI_BLOCK_MINING);
        // A new local player must also initialize its server state after respawn or a level change.
        if (Boolean.valueOf(held).equals(mc.player.getExistingDataOrNull(HammerRegistry.PRECISION_MINING))) return;
        mc.player.setData(HammerRegistry.PRECISION_MINING, held);
        ClientPacketDistributor.sendToServer(new PrecisionMiningPayload(held));
        MultiBlockVisualFeedback.invalidatePattern(mc.player);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null)
            return;

        ItemStack itemStack = player.getMainHandItem();
        // The key mappings have to be consumed during the event or it will replay the event on each tick. Don't check anything for this outside of player null
        while (ClientSetup.SUMMON_LIGHTNING_KEY.get().consumeClick()) {
            // Component-based check - works with any item that has LIGHTNING_ABILITY
            if (itemStack.has(HammerRegistry.LIGHTNING_ABILITY)) {
                BlockPos targetPos = SpawnLightningPayload.targetFromView(player);
                ClientPacketDistributor.sendToServer(new SpawnLightningPayload(targetPos));
            }
        }
        while (ClientSetup.TELEPORT_SURFACE_KEY.get().consumeClick()) {
            // Component-based check - works with any item that has TELEPORT_SURFACE_ABILITY
            if (itemStack.has(HammerRegistry.TELEPORT_SURFACE_ABILITY)) {
                ClientPacketDistributor.sendToServer(TeleportToSurfacePayload.INSTANCE);
            }
        }
        while (ClientSetup.HAMMER_CONFIG_KEY.get().consumeClick()) {
            // Component-based check - works with any item that has TOOL_CONFIG
            if (itemStack.has(HammerRegistry.TOOL_CONFIG)) {
                mc.gui.setScreen(new ToolConfigScreen());
            }
        }
        updateThrowKey(mc, itemStack);
        while (ClientSetup.CYCLE_MINING_MODE_KEY.get().consumeClick()) {
            // Component-based check - works with any item that has MULTI_BLOCK_MINING
            if (itemStack.has(HammerRegistry.MULTI_BLOCK_MINING)) {
                ClientPacketDistributor.sendToServer(CycleMiningModePayload.INSTANCE);
            }
        }
        while (ClientSetup.HAMMER_ACTION_WHEEL_KEY.get().consumeClick()) {
            if (mc.gui.screen() == null && itemStack.has(HammerRegistry.HAMMER_ACTION)) {
                mc.gui.setScreen(new HammerActionWheel(player.getInventory().getSelectedSlot(), itemStack));
            }
        }
    }
}
