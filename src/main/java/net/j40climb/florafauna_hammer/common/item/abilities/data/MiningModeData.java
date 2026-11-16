package net.j40climb.florafauna_hammer.common.item.abilities.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record MiningModeData(MiningShape shape, int radius, int maxBlocksToBreak, boolean ignoreToolRestrictions, int favorites) {
    private static final int ALL_SHAPES = (1 << MiningShape.values().length) - 1;

    public MiningModeData(MiningShape shape, int radius, int maxBlocksToBreak, boolean ignoreToolRestrictions) {
        this(shape, radius, maxBlocksToBreak, ignoreToolRestrictions, 0);
    }
    // Normalize existing saved and networked components before iterating their bounds.
    public MiningModeData {
        favorites &= ALL_SHAPES;
        radius = Math.clamp(radius, 0, 3);
        maxBlocksToBreak = Math.clamp(maxBlocksToBreak, 1, 64);
    }

    public static final Codec<MiningModeData> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    MiningShape.CODEC.fieldOf("shape").forGetter(MiningModeData::shape),
                    Codec.INT.fieldOf("radius").forGetter(MiningModeData::radius),
                    Codec.INT.fieldOf("maxBlocksToBreak").forGetter(MiningModeData::maxBlocksToBreak),
                    Codec.BOOL.optionalFieldOf("ignoreToolRestrictions", true).forGetter(MiningModeData::ignoreToolRestrictions),
                    Codec.INT.optionalFieldOf("favorites", 0).forGetter(MiningModeData::favorites)
            ).apply(builder, MiningModeData::new));

    public static final StreamCodec<ByteBuf, MiningModeData> STREAM_CODEC = StreamCodec.composite(
            MiningShape.STREAM_CODEC, MiningModeData::shape,
            ByteBufCodecs.INT, MiningModeData::radius,
            ByteBufCodecs.INT, MiningModeData::maxBlocksToBreak,
            ByteBufCodecs.BOOL, MiningModeData::ignoreToolRestrictions,
            ByteBufCodecs.VAR_INT, MiningModeData::favorites,
            MiningModeData::new
    );

    public static final MiningModeData DEFAULT = new MiningModeData(MiningShape.SINGLE, 0, 64, true);

    public MiningModeData getNextMode() {
        var shapes = MiningShape.values();
        int index = shape.ordinal();
        do {
            index = (index + 1) % shapes.length;
        } while (favorites != 0 && !isFavorite(shapes[index]));
        return withShape(shapes[index]);
    }

    public MiningModeData withShape(MiningShape selected) {
        return new MiningModeData(selected, selected.getRadius(), maxBlocksToBreak, ignoreToolRestrictions, favorites);
    }

    public boolean isFavorite(MiningShape candidate) {
        return (favorites & (1 << candidate.id())) != 0;
    }

    public MiningModeData toggleFavorite(MiningShape candidate) {
        return new MiningModeData(shape, radius, maxBlocksToBreak, ignoreToolRestrictions, favorites ^ (1 << candidate.id()));
    }

    public Component modeDescription() {
        return Component.translatable("tooltip.florafauna.hammer.mode",
                Component.translatable("mining_shape.florafauna." + shape.getSerializedName()));
    }
}
