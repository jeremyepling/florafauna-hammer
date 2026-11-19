package net.j40climb.florafauna_hammer.common.item.abilities;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.core.BlockPos;

import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Incrementally samples a sphere, then flood-fills its six-connected ore deposits. */
final class OreDepositSearch {
    interface Sampler {
        boolean isOre(int x, int y, int z);
    }

    private final BlockPos origin;
    private final int radius;
    private final int width;
    private final int volume;
    private final int limit;
    private final BitSet ores;
    private final IntArrayFIFOQueue frontier = new IntArrayFIFOQueue();
    private final Comparator<BlockPos> distanceOrder;
    private final PriorityQueue<BlockPos> nearest;
    private int cursor;
    private int nextSeed;
    private int depositSize;
    private int sumX, sumY, sumZ;
    private boolean complete;

    OreDepositSearch(BlockPos origin, int radius, int limit) {
        this.origin = origin;
        this.radius = radius;
        this.limit = limit;
        width = 2 * radius + 1;
        volume = width * width * width;
        ores = new BitSet(volume);
        distanceOrder = Comparator.<BlockPos>comparingDouble(origin::distSqr).thenComparing(BlockPos::compareTo);
        nearest = new PriorityQueue<>(distanceOrder.reversed());
    }

    boolean advance(int budget, Sampler sample) {
        while (budget-- > 0 && !complete) {
            if (cursor < volume) {
                int index = cursor++;
                int x = index % width - radius;
                int y = index / (width * width) - radius;
                int z = index / width % width - radius;
                if (x*x + y*y + z*z <= radius*radius
                        && sample.isOre(origin.getX() + x, origin.getY() + y, origin.getZ() + z)) {
                    ores.set(index);
                }
            } else {
                visitOre();
            }
        }
        return complete;
    }

    private void visitOre() {
        if (frontier.isEmpty()) {
            if (depositSize > 0) finishDeposit();
            nextSeed = ores.nextSetBit(nextSeed);
            if (nextSeed < 0) {
                complete = true;
                return;
            }
            enqueue(nextSeed++);
        }
        int index = frontier.dequeueInt();
        int x = index % width, y = index / (width * width), z = index / width % width;
        depositSize++;
        sumX += x;
        sumY += y;
        sumZ += z;
        if (x > 0) enqueue(index - 1);
        if (x + 1 < width) enqueue(index + 1);
        if (z > 0) enqueue(index - width);
        if (z + 1 < width) enqueue(index + width);
        if (y > 0) enqueue(index - width * width);
        if (y + 1 < width) enqueue(index + width * width);
    }

    private void enqueue(int index) {
        if (ores.get(index)) {
            ores.clear(index);
            frontier.enqueue(index);
        }
    }

    private void finishDeposit() {
        // Round the centroid to a two-block grid before it leaves the server.
        BlockPos marker = new BlockPos(cell(origin.getX(), sumX), cell(origin.getY(), sumY), cell(origin.getZ(), sumZ));
        nearest.add(marker);
        if (nearest.size() > limit) nearest.remove();
        sumX = sumY = sumZ = depositSize = 0;
    }

    private int cell(int base, int sum) {
        return Math.floorDiv(base - radius + sum / depositSize, 2) * 2;
    }

    List<BlockPos> markers() {
        if (!complete) throw new IllegalStateException("Scan still running");
        return nearest.stream().sorted(distanceOrder).toList();
    }
}
