package net.j40climb.florafauna_hammer.common.item.abilities.networking;

import io.netty.buffer.ByteBuf;
import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningModeData;
//? if <26.3 {
import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
//?}
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
//? if <26.3 {
import net.minecraft.world.InteractionHand;
//?}
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Payload sent from client to server when the cycle mining mode key is pressed.
 * Cycles the mining mode on the held item if it has the MULTI_BLOCK_MINING component.
 */
public enum CycleMiningModePayload implements CustomPacketPayload {
    INSTANCE;

    public static final Type<CycleMiningModePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HammerMod.CONTENT_NAMESPACE, "cycle_mining_mode"));

    public static final StreamCodec<ByteBuf, CycleMiningModePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void onServerReceived(final CycleMiningModePayload data, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        cycleMiningMode(player, player.getMainHandItem());
    }

    /**
     * Cycles the mining mode on the interacting stack.
     * Can be called directly from items or via network payload.
     */
    public static void cycleMiningMode(ServerPlayer player, ItemStack stack) {

        //? if <26.3 {
        MiningModeData previous = stack.get(HammerRegistry.MULTI_BLOCK_MINING);
        if (previous == null) return;
        MiningModeData selected = previous.getNextMode(HammerStage.of(stack));
        stack.set(HammerRegistry.MULTI_BLOCK_MINING, selected);
        player.sendOverlayMessage(selected.modeDescription());
        if (stack.is(HammerRegistry.HAMMER) && selected.shape() != previous.shape()) {
            var hand = stack == player.getMainHandItem() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            HammerCorePayload.send(player, hand, HammerCorePayload.Cue.MODE);
        //?} else {
        /*// Check if item has multi-block mining component
        if (!stack.has(HammerRegistry.MULTI_BLOCK_MINING)) {
            return;
        }

        // Cycle to next mode
        stack.update(
                HammerRegistry.MULTI_BLOCK_MINING,
                MiningModeData.DEFAULT,
                MiningModeData::getNextMode
        );

        // Display the new mode to the player
        MiningModeData currentMode = stack.get(HammerRegistry.MULTI_BLOCK_MINING);
        if (currentMode != null) {
            player.sendOverlayMessage(currentMode.modeDescription());
        *///?}
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
