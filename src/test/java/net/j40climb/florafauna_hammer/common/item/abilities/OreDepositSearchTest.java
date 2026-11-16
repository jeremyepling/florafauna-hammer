package net.j40climb.florafauna_hammer.common.item.abilities;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OreDepositSearchTest {
    @Test
    void denseSphereIsOneDepositAndBothStagesRespectTheWorkBudget() {
        var search = new OreDepositSearch(BlockPos.ZERO, 24, 24);
        int[] reads = {0};
        int slices = 0;
        boolean complete;
        do {
            int before = reads[0];
            complete = search.advance(512, (x, y, z) -> {
                assertTrue(x*x + y*y + z*z <= 24*24);
                reads[0]++;
                return true;
            });
            assertTrue(reads[0] - before <= 512);
            assertTrue(++slices <= 350);
        } while (!complete);
        assertEquals(57777, reads[0]);
        // Every cube position, followed by one flood-fill visit per ore and one finalization.
        assertEquals(Math.ceilDiv(49*49*49 + reads[0] + 1, 512), slices);
        assertEquals(List.of(BlockPos.ZERO), search.markers());
    }

    @Test
    void connectsFacesAndIncludesTheRadiusBoundary() {
        var origin = new BlockPos(-10, -30, 8);
        var ores = Set.of(origin.east(24), origin.west(24), origin.west(23), origin.east(25),
                origin.offset(20, 20, 0));
        var search = new OreDepositSearch(origin, 24, 24);
        while (!search.advance(777, (x, y, z) -> ores.contains(new BlockPos(x, y, z)))) {}
        assertEquals(Set.of(origin.east(24), origin.west(24)), new HashSet<>(search.markers()));
    }

    @Test
    void keepsNearestClustersWhenPayloadLimitIsExceeded() {
        var search = new OreDepositSearch(BlockPos.ZERO, 24, 24);
        while (!search.advance(2048, (x, y, z) -> x % 4 == 0 && y % 4 == 0 && z % 4 == 0)) {}
        assertEquals(24, search.markers().size());
        assertEquals(BlockPos.ZERO, search.markers().getFirst());
        assertTrue(search.markers().getLast().distSqr(BlockPos.ZERO) <= 48);
    }

    @Test
    void emptyTerrainProducesEmptyResult() {
        var search = new OreDepositSearch(BlockPos.ZERO, 3, 24);
        while (!search.advance(32, (x, y, z) -> false)) {}
        assertTrue(search.markers().isEmpty());
    }
}
