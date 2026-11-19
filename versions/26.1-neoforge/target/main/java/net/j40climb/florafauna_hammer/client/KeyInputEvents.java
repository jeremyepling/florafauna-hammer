package net.j40climb.florafauna_hammer.client;

import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.CycleMiningModePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.SpawnLightningPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.TeleportToSurfacePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.ThrowItemPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.ChargedThrowPayload;
import net.j40climb.florafauna_hammer.setup.ClientSetup;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.client.Minecraft;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningShape;
import net.j40climb.florafauna_hammer.common.item.abilities.menu.HammerMenuScreen;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.SelectMiningModePayload;
import net.minecraft.core.BlockPos;
import net.j40climb.florafauna_hammer.common.item.abilities.data.HammerAction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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
        if (holdingThrow && (mc.screen != null || player.getInventory().getSelectedSlot() != throwSlot
                || !held.is(HammerRegistry.HAMMER.get()))) {
            ClientPacketDistributor.sendToServer(new ChargedThrowPayload(ChargedThrowPayload.CANCEL));
            holdingThrow = false;
        }
        while (key.consumeClick()) {
            if (held.is(HammerRegistry.HAMMER.get())) {
                if (mc.screen == null && !holdingThrow && held.has(HammerRegistry.THROWABLE_ABILITY)) {
                    holdingThrow = true;
                    throwSlot = player.getInventory().getSelectedSlot();
                    ClientPacketDistributor.sendToServer(new ChargedThrowPayload(ChargedThrowPayload.PRESS));
                }
            } else if (held.has(HammerRegistry.THROWABLE_ABILITY)) {
                player.swing(InteractionHand.MAIN_HAND);
                ClientPacketDistributor.sendToServer(new ThrowItemPayload(true));
            }
        }
        if (holdingThrow && !key.isDown()) {
            holdingThrow = false;
            player.swing(InteractionHand.MAIN_HAND);
            ClientPacketDistributor.sendToServer(new ChargedThrowPayload(ChargedThrowPayload.RELEASE));
        }
    }

    private static final HammerControlGesture gesture = new HammerControlGesture();
    private static Player gesturePlayer;
    private static int gestureSlot = -1;
    private static boolean wasDown;
    private static boolean waitForRelease;
    private static boolean menuHold;

    // Observe physical edges before another input can arrive in the same tick. Tick polling also
    // supports injected key mappings and fires deadlines while the keyboard is idle.
    @SubscribeEvent
    public static void onControlKey(InputEvent.Key event) {
        if (ClientSetup.CYCLE_MINING_MODE_KEY.get().matches(event.getKeyEvent())) controlEdge(event.getAction());
    }

    @SubscribeEvent
    public static void onControlMouse(InputEvent.MouseButton.Post event) {
        if (ClientSetup.CYCLE_MINING_MODE_KEY.get().getKey().equals(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            controlEdge(event.getAction());
        }
    }

    private static void controlEdge(int action) {
        var mc = Minecraft.getInstance();
        if (menuHold && action == InputConstants.RELEASE) {
            releaseMenuHold(mc);
            return;
        }
        // Escape, changing worlds, or another screen may have consumed the old release.
        if (menuHold && action == InputConstants.PRESS && mc.screen == null) resetMenuHold();
        updateControl(mc);
    }

    /** Opening a Screen clears KeyMapping.isDown. Its keyboard/mouse release is the authoritative edge. */
    public static void releaseMenuHold(Minecraft mc) {
        if (mc.screen instanceof HammerMenuScreen screen) screen.onClose();
        resetMenuHold();
        var key = ClientSetup.CYCLE_MINING_MODE_KEY.get();
        key.setDown(false);
        while (key.consumeClick()) { } // Discard any repeat queued before the screen grabbed input.
    }

    private static void resetMenuHold() {
        menuHold = false;
        wasDown = false;
        waitForRelease = false;
        gesture.reset();
    }

    private static void updateControl(Minecraft mc) {
        var key = ClientSetup.CYCLE_MINING_MODE_KEY.get();
        var player = mc.player;
        boolean down = key.isDown();
        int clicks = 0;
        while (key.consumeClick()) clicks++;
        if (menuHold) {
            if (player != gesturePlayer) {
                releaseMenuHold(mc);
                gesturePlayer = player;
            }
            return;
        }

        int slot = player == null ? -1 : player.getInventory().getSelectedSlot();
        var held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        boolean usable = held.has(HammerRegistry.MULTI_BLOCK_MINING) || held.has(HammerRegistry.HAMMER_ACTION)
                || held.has(HammerRegistry.TOOL_CONFIG);
        if (player != gesturePlayer || slot != gestureSlot || mc.screen != null || !usable) {
            gesture.reset();
            waitForRelease = down && (wasDown || mc.screen != null || !usable);
            wasDown = false;
            gesturePlayer = player;
            gestureSlot = slot;
        }
        if (!down) waitForRelease = false;
        if (mc.screen != null || !usable || waitForRelease) return;

        long now = System.nanoTime() / 1_000_000L;
        if (wasDown && !down) dispatch(mc, gesture.release(now));
        // A quick physical press and release can both occur between client ticks.
        if (!down) {
            for (int i = 0; i < clicks; i++) {
                dispatch(mc, gesture.press(now));
                dispatch(mc, gesture.release(now));
            }
        } else if (!wasDown) {
            dispatch(mc, gesture.press(now));
        }
        dispatch(mc, gesture.advance(now));
        wasDown = down;
    }

    private static void dispatch(Minecraft mc, HammerControlGesture.Action action) {
        switch (action) {
            case NONE -> { }
            case CYCLE -> ClientPacketDistributor.sendToServer(CycleMiningModePayload.INSTANCE);
            case SINGLE -> ClientPacketDistributor.sendToServer(new SelectMiningModePayload(gestureSlot, MiningShape.SINGLE, false));
            case MENU -> {
                menuHold = true;
                mc.setScreen(new HammerMenuScreen(gestureSlot, mc.player.getMainHandItem()));
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        updateControl(mc);
        Player player = mc.player;
        if (player == null)
            return;

        ItemStack itemStack = player.getMainHandItem();
        // The key mappings have to be consumed during the event or it will replay the event on each tick. Don't check anything for this outside of player null
        while (ClientSetup.SUMMON_LIGHTNING_KEY.get().consumeClick()) {
            // Component-based check - works with any item that has LIGHTNING_ABILITY
            if (HammerAction.LIGHTNING.availableOn(itemStack)) {
                BlockPos targetPos = SpawnLightningPayload.targetFromView(player);
                ClientPacketDistributor.sendToServer(new SpawnLightningPayload(targetPos));
            }
        }
        while (ClientSetup.TELEPORT_SURFACE_KEY.get().consumeClick()) {
            // Component-based check - works with any item that has TELEPORT_SURFACE_ABILITY
            if (HammerAction.TELEPORT.availableOn(itemStack)) {
                ClientPacketDistributor.sendToServer(TeleportToSurfacePayload.INSTANCE);
            }
        }
        updateThrowKey(mc, itemStack);
    }
}
