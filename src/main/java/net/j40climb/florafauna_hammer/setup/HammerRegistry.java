package net.j40climb.florafauna_hammer.setup;

import net.j40climb.florafauna_hammer.HammerMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.j40climb.florafauna_hammer.common.item.projectile.ThrownItemEntity;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningModeData;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MultiToolAbilityData;
import net.j40climb.florafauna_hammer.common.item.abilities.data.RightClickAction;
import net.j40climb.florafauna_hammer.common.item.abilities.data.HammerAction;
import net.j40climb.florafauna_hammer.common.item.abilities.data.ThrowableAbilityData;
import net.j40climb.florafauna_hammer.common.item.abilities.data.ToolConfig;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;

import java.util.function.Supplier;

import net.j40climb.florafauna_hammer.common.item.abilities.networking.ChargedThrowPayload;
//? if <26.3 {
import net.j40climb.florafauna_hammer.common.item.abilities.GroundSlam;

import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
//?}

import static net.j40climb.florafauna_hammer.common.item.hammer.HammerItem.HAMMER_MATERIAL;

public final class HammerRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HammerMod.CONTENT_NAMESPACE);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, HammerMod.CONTENT_NAMESPACE);

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HammerMod.CONTENT_NAMESPACE);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HammerMod.CONTENT_NAMESPACE);

    public static final ResourceKey<EntityType<?>> THROWN_ITEM_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(HammerMod.CONTENT_NAMESPACE, "thrown_item"));

    public static final Supplier<EntityType<ThrownItemEntity>> THROWN_ITEM = ENTITY_TYPES.register("thrown_item",
            () -> EntityType.Builder.<ThrownItemEntity>of(ThrownItemEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(THROWN_ITEM_KEY));

    //? if <26.3 {
    public static final DeferredItem<Item> RESONANCE_CORE = ITEMS.registerSimpleItem("resonance_core");
    public static final DeferredItem<Item> TEMPEST_CORE = ITEMS.registerSimpleItem("tempest_core");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HammerStage>> HAMMER_STAGE =
            DATA_COMPONENTS.registerComponentType("hammer_stage",
                    builder -> builder.persistent(HammerStage.CODEC).networkSynchronized(HammerStage.STREAM_CODEC));

    //?}
    public static final DeferredItem<Item> HAMMER = ITEMS.registerItem("hammer", properties ->
            new HammerItem(properties.tool(HAMMER_MATERIAL, BlockTags.MINEABLE_WITH_PICKAXE, 8, -2.4f, 0)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MiningModeData>> MULTI_BLOCK_MINING =
            DATA_COMPONENTS.registerComponentType("multi_block_mining",
                    //? if <26.3 {
                    builder -> builder.persistent(MiningModeData.CODEC).networkSynchronized(MiningModeData.STREAM_CODEC).ignoreSwapAnimation());
                    //?} else {
                    /*builder -> builder.persistent(MiningModeData.CODEC).networkSynchronized(MiningModeData.STREAM_CODEC));
                    *///?}

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolConfig>> TOOL_CONFIG =
            DATA_COMPONENTS.registerComponentType("tool_config",
                    builder -> builder.persistent(ToolConfig.CODEC).networkSynchronized(ToolConfig.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> LIGHTNING_ABILITY =
            DATA_COMPONENTS.registerComponentType("lightning_ability",
                    builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> TELEPORT_SURFACE_ABILITY =
            DATA_COMPONENTS.registerComponentType("teleport_surface_ability",
                    builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HAMMER_CHARGE =
            //? if <26.3 {
            DATA_COMPONENTS.registerComponentType("hammer_charge", b -> b.persistent(Codec.intRange(0, HammerStage.TEMPEST.capacity()))
                    .networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation());
            //?} else {
            /*DATA_COMPONENTS.registerComponentType("hammer_charge", b -> b.persistent(Codec.intRange(0, HammerItem.MAX_CHARGE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
            *///?}

    public static final Supplier<AttachmentType<ChargedThrowPayload.Hold>> THROW_HOLD = ATTACHMENT_TYPES.register("throw_hold",
            () -> AttachmentType.builder(() -> new ChargedThrowPayload.Hold(ItemStack.EMPTY, net.minecraft.world.level.Level.OVERWORLD, 0)).build());
    //? if <26.3 {

    public static final Supplier<AttachmentType<GroundSlam.Pending>> PENDING_SLAM = ATTACHMENT_TYPES.register("pending_slam",
            () -> AttachmentType.builder(() -> new GroundSlam.Pending(ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.level.Level.OVERWORLD, 0)).build());
    //?}

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ThrowableAbilityData>> THROWABLE_ABILITY =
            DATA_COMPONENTS.registerComponentType("throwable_ability",
                    builder -> builder.persistent(ThrowableAbilityData.CODEC).networkSynchronized(ThrowableAbilityData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RightClickAction>> RIGHT_CLICK_ACTION =
            DATA_COMPONENTS.registerComponentType("right_click_action",
                    builder -> builder.persistent(RightClickAction.CODEC).networkSynchronized(RightClickAction.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HammerAction>> HAMMER_ACTION =
            DATA_COMPONENTS.registerComponentType("hammer_action",
                    //? if <26.3 {
                    builder -> builder.persistent(HammerAction.CODEC).networkSynchronized(HammerAction.STREAM_CODEC).ignoreSwapAnimation());
                    //?} else {
                    /*builder -> builder.persistent(HammerAction.CODEC).networkSynchronized(HammerAction.STREAM_CODEC));
                    *///?}

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MultiToolAbilityData>> MULTI_TOOL_ABILITY =
            DATA_COMPONENTS.registerComponentType("multi_tool_ability",
                    builder -> builder.persistent(MultiToolAbilityData.CODEC).networkSynchronized(MultiToolAbilityData.STREAM_CODEC));
    //? if >=26.3 {
    /*
    public static final Supplier<AttachmentType<Boolean>> PRECISION_MINING = ATTACHMENT_TYPES.register("precision_mining",
            () -> AttachmentType.builder(() -> false).build());
    *///?}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITY_TYPES.register(bus);
        DATA_COMPONENTS.register(bus);
        ATTACHMENT_TYPES.register(bus);
    }

    private HammerRegistry() {}
}
