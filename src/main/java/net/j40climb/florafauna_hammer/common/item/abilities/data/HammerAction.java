package net.j40climb.florafauna_hammer.common.item.abilities.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
//? if <26.3 {
import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
//?} else {
/*import net.minecraft.network.chat.Component;
*///?}
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum HammerAction implements StringRepresentable {
    //? if <26.3 {
    AUTO("auto"), STRIP("strip"), PATH("path"), TILL("till"), THROW("throw"),
    LIGHTNING("lightning"), TELEPORT("teleport"), SCAN("scan");
    //?} else {
    /*AUTO("auto"), STRIP("strip"), PATH("path"), TILL("till"), THROW("throw");
    *///?}

    public static final Codec<HammerAction> CODEC = StringRepresentable.fromEnum(HammerAction::values);
    public static final StreamCodec<ByteBuf, HammerAction> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    private final String name;

    HammerAction(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component label() {
        return Component.translatable("gui.florafauna.hammer_action." + name);
    }

    //? if <26.3 {
    public boolean availableOn(ItemStack tool) {
        if (!HammerStage.of(tool).allows(this)) return false;
        return switch (this) {
            case AUTO -> true;
            case STRIP, PATH, TILL -> !toolActions(tool.getOrDefault(HammerRegistry.MULTI_TOOL_ABILITY, MultiToolAbilityData.NONE))
                    .equals(MultiToolAbilityData.NONE);
            case THROW -> tool.has(HammerRegistry.THROWABLE_ABILITY);
            case LIGHTNING -> tool.has(HammerRegistry.LIGHTNING_ABILITY);
            case TELEPORT -> tool.has(HammerRegistry.TELEPORT_SURFACE_ABILITY);
            case SCAN -> tool.is(HammerRegistry.HAMMER.get());
        };
    }

    //?}
    public MultiToolAbilityData toolActions(MultiToolAbilityData enabled) {
        return switch (this) {
            case AUTO -> enabled;
            case STRIP -> enabled.strip() ? MultiToolAbilityData.AXE_ONLY : MultiToolAbilityData.NONE;
            case PATH -> enabled.flatten() ? MultiToolAbilityData.SHOVEL_ONLY : MultiToolAbilityData.NONE;
            case TILL -> enabled.till() ? MultiToolAbilityData.HOE_ONLY : MultiToolAbilityData.NONE;
            //? if <26.3 {
            case THROW, LIGHTNING, TELEPORT, SCAN -> MultiToolAbilityData.NONE;
            //?} else {
            /*case THROW -> MultiToolAbilityData.NONE;
            *///?}
        };
    }
}
