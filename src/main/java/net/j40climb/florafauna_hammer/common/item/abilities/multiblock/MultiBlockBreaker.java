package net.j40climb.florafauna_hammer.common.item.abilities.multiblock;

import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningModeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.HashSet;
import java.util.Set;

/** Completes an accepted center break. Every neighbor goes through the normal player mining pipeline. */
public final class MultiBlockBreaker {
    private MultiBlockBreaker() {}

    public static void finish(ServerPlayer player, ItemStack tool, MiningModeData mode,
                              BlockPos center, MultiBlockPatterns.Plan plan) {
        ServerLevel level = player.level();
        Set<BlockPos> mined = new HashSet<>();
        mined.add(center);
        for (BlockPos pos : plan.blocks()) {
            if (player.getMainHandItem() != tool || tool.isEmpty()) break;
            if (pos.equals(center) || !MultiBlockPatterns.isMineable(level, player, tool, mode, pos)) continue;
            var before = level.getBlockState(pos);
            if (player.gameMode.destroyBlock(pos) && !level.getBlockState(pos).is(before.getBlock())) mined.add(pos);
        }
        for (BlockPos pos : plan.stairs()) {
            if (plan.blocks().contains(pos) && !mined.contains(pos)) continue;
            if (!MultiBlockPatterns.canInteract(level, player, pos) || !level.isEmptyBlock(pos)) continue;
            BlockState stair = plan.stairState();
            if (level.isUnobstructed(stair, pos, CollisionContext.placementContext(player))) {
                placeStair(level, player, pos, stair);
            }
        }
        if (plan.passage() != null) TunnelLighting.afterMining(player, tool, plan.passage(), mined);
    }

    private static void placeStair(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState stair) {
        if (level.captureBlockSnapshots) {
            // The enclosing NeoForge placement owns the event and subsequent notifications.
            level.setBlock(pos, stair, Block.UPDATE_ALL);
            return;
        }
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos, Block.UPDATE_ALL);
        var chunk = level.getChunkAt(pos);
        // The place event reads the stair from the world. Defer observers, clients and
        // shape updates until the event accepts it, including on paths that restore air.
        if (chunk.setBlockState(pos, stair, Block.UPDATE_SKIP_ALL_SIDEEFFECTS) == null) return;
        boolean accepted = false;
        try {
            accepted = !EventHooks.onBlockPlace(player, snapshot, Direction.UP);
        } finally {
            if (!accepted) chunk.setBlockState(pos, snapshot.getState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }
        if (accepted && level.getBlockState(pos) == stair) {
            stair.onPlace(level, pos, snapshot.getState(), false);
            level.markAndNotifyBlock(pos, chunk, snapshot.getState(), stair, Block.UPDATE_ALL, Block.UPDATE_LIMIT);
        }
    }
}
