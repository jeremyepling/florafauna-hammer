package net.j40climb.florafauna_hammer.common.item.abilities;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SeismicTerrainGameTests {
    private SeismicTerrainGameTests() {}

    public static void checkLoadedTerrain(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunks = level.getChunkSource();
        var start = helper.absolutePos(BlockPos.ZERO);
        int chunkX = start.getX() >> 4;
        int chunkZ = start.getZ() >> 4;
        helper.assertTrue(chunks.getChunkNow(chunkX, chunkZ) != null, "The scan origin must be loaded");
        var origin = new BlockPos(chunkX * 16 + 8, level.getMaxY() - 40, chunkZ * 16 + 8);
        var boundaryOre = origin.above(SeismicScanner.RADIUS);
        // Inside the search cube, outside the sphere, and separated from the boundary ore.
        var outside = origin.offset(7, SeismicScanner.RADIUS, 0);
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        try {
            for (int dx = -3; dx <= 3; dx++) {
                var pos = origin.offset(dx, 0, 0);
                previous.put(pos, level.getBlockState(pos));
                var ore = dx % 2 == 0 ? Blocks.IRON_ORE : Blocks.DEEPSLATE_IRON_ORE;
                level.setBlock(pos, ore.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            for (var pos : new BlockPos[]{boundaryOre, outside}) {
                previous.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.NETHER_QUARTZ_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            var markers = scan(origin, new SeismicScanner.LoadedOres(level), helper).markers();
            helper.assertTrue(markers.size() == 2, "Expected a mixed iron deposit and the ore at radius 24: " + markers);
            for (var deposit : new BlockPos[]{origin, boundaryOre}) {
                helper.assertTrue(markers.stream().anyMatch(marker -> marker.distSqr(deposit) <= 3),
                        "Missing deposit marker near " + deposit + ": " + markers);
            }
        } finally {
            previous.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
        }
        // World writes can refresh neighbor chunks. Locate the edge after restoring the ore fixture.
        while (chunks.getChunkNow(chunkX + 1, chunkZ) != null) chunkX++;
        int absentChunk = chunkX + 1;
        var edge = new BlockPos(chunkX * 16 + 8, origin.getY(), origin.getZ());
        var loaded = new SeismicScanner.LoadedOres(level);
        boolean[] visitedGuard = {false};
        scan(edge, (x, y, z) -> {
            if ((x >> 4) == absentChunk && (z >> 4) == chunkZ) visitedGuard[0] = true;
            return loaded.isOre(x, y, z);
        }, helper);
        helper.assertTrue(visitedGuard[0] && chunks.getChunkNow(absentChunk, chunkZ) == null,
                "The scan must sample the unloaded part of the sphere without loading its chunk");
        helper.succeed();
    }

    private static OreDepositSearch scan(BlockPos origin, OreDepositSearch.Sampler sampler, GameTestHelper helper) {
        var search = new OreDepositSearch(origin, SeismicScanner.RADIUS, SeismicScanner.MAX_DEPOSITS);
        long longestSlice = 0;
        // Complete within one callback so other fixtures cannot change the guard's load state mid-scan.
        for (int slice = 1; slice <= 400; slice++) {
            long started = System.nanoTime();
            boolean done = search.advance(512, sampler);
            longestSlice = Math.max(longestSlice, System.nanoTime() - started);
            if (done) {
                LogUtils.getLogger().info("Seismic terrain fixture used {} slices of 512 operations; longest {} microseconds",
                        slice, longestSlice / 1_000);
                return search;
            }
        }
        throw helper.assertionException("Scan exceeded its slice limit");
    }
}
