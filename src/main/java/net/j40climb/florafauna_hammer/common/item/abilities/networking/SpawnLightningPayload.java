package net.j40climb.florafauna_hammer.common.item.abilities.networking;

import io.netty.buffer.ByteBuf;
import net.j40climb.florafauna_hammer.HammerMod;
//? if <26.3 {
import net.j40climb.florafauna_hammer.common.item.abilities.data.HammerAction;
//?}
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
//? if <26.3 {
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
//?} else {
/*import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
*///?}
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpawnLightningPayload(BlockPos targetPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpawnLightningPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HammerMod.CONTENT_NAMESPACE, "spawn_lightning_payload"));
    public static final StreamCodec<ByteBuf, SpawnLightningPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SpawnLightningPayload::targetPos,
            SpawnLightningPayload::new
    );

    public static void onServerReceived(final SpawnLightningPayload data, final IPayloadContext context) {
        //? if <26.3 {
        if (context.player() instanceof ServerPlayer player && data.targetPos().equals(targetFromView(player))) {
            spawnLightningBolt(player, InteractionHand.MAIN_HAND, data.targetPos());
        //?} else {
        /*Player player = context.player();
        if (player.getMainHandItem().has(HammerRegistry.LIGHTNING_ABILITY)) {
            BlockPos target = targetFromView(player);
            if (target.equals(data.targetPos()) && player.level().hasChunkAt(target)) {
                spawnLightningBolt(player.level(), target);
            }
        *///?}
        }
    }

    public static BlockPos targetFromView(Player player) {
        return BlockPos.containing(player.pick(10.0, 1.0F, false).getLocation());
    }

    //? if <26.3 {
    public static void spawnLightningBolt(ServerPlayer player, InteractionHand hand, BlockPos targetPos) {
        ServerLevel serverLevel = player.level();
        if (!HammerAction.LIGHTNING.availableOn(player.getItemInHand(hand)) || !serverLevel.hasChunkAt(targetPos)) return;
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (lightningBolt != null) {
            lightningBolt.setPos(Vec3.atBottomCenterOf(targetPos));
            if (serverLevel.addFreshEntity(lightningBolt) && player.getItemInHand(hand).is(HammerRegistry.HAMMER)) {
                HammerCorePayload.send(player, hand, HammerCorePayload.Cue.LIGHTNING);
            }
    //?} else {
    /*public static void spawnLightningBolt(Level level, BlockPos targetPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LightningBolt lightningBolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (lightningBolt != null) {
            lightningBolt.setPos(Vec3.atBottomCenterOf(targetPos));
            serverLevel.addFreshEntity(lightningBolt);
    *///?}
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
