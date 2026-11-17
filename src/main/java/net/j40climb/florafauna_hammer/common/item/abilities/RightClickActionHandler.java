package net.j40climb.florafauna_hammer.common.item.abilities;

import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MultiToolAbilityData;
import net.j40climb.florafauna_hammer.common.item.abilities.data.HammerAction;
import net.j40climb.florafauna_hammer.common.item.abilities.data.RightClickAction;
import net.j40climb.florafauna_hammer.common.item.abilities.multiblock.MultiBlockPatterns;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.CycleMiningModePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.SpawnLightningPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.TeleportToSurfacePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.ThrowItemPayload;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Airborne Hammers slam. Grounded tools use their selected action or Auto's component fallback. */
@EventBusSubscriber(modid = HammerMod.MOD_ID)
public class RightClickActionHandler {

    /**
     * Tracks the game tick when each player last performed a tool modification.
     * Used to prevent RightClickItem from also triggering an ability in the same tick
     * when RightClickBlock already performed a tool modification.
     */
    private static final Map<UUID, Long> recentToolModifications = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        recentToolModifications.remove(event.getEntity().getUUID());
    }

    /**
     * Handle right-click on a block.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (handleRightClick(event.getEntity(), event.getHand(), event.getPos(), event.getHitVec())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Handle right-click in air (with item in hand).
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (handleRightClick(event.getEntity(), event.getHand(), null, null)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (attemptAirborneSlam(event.getEntity(), event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (attemptAirborneSlam(event.getEntity(), event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean attemptAirborneSlam(Player player, InteractionHand hand) {
        if (player.onGround() || !player.getItemInHand(hand).is(HammerRegistry.HAMMER)) return false;
        if (player instanceof ServerPlayer serverPlayer) GroundSlam.activate(serverPlayer, hand);
        return true;
    }

    private static boolean handleRightClick(Player player, InteractionHand hand, @Nullable BlockPos clickedPos, @Nullable BlockHitResult hitResult) {
        if (attemptAirborneSlam(player, hand)) return true;
        ItemStack stack = player.getItemInHand(hand);
        HammerAction selected = stack.getOrDefault(HammerRegistry.HAMMER_ACTION, HammerAction.AUTO);
        ServerPlayer serverPlayer = player instanceof ServerPlayer server ? server : null;
        switch (selected) {
            case SCAN -> {
                // HammerItem.useOn runs after the block gets its normal interaction.
                if (clickedPos != null || !selected.availableOn(stack)) return false;
                if (serverPlayer != null) SeismicScanner.activate(serverPlayer, hand);
                return true;
            }
            case THROW -> {
                if (!stack.has(HammerRegistry.THROWABLE_ABILITY)) return false;
                if (serverPlayer != null) ThrowItemPayload.throwItem(serverPlayer, hand);
                return true;
            }
            case LIGHTNING -> {
                if (!selected.availableOn(stack)) return false;
                if (serverPlayer != null) {
                    BlockPos target = clickedPos != null ? clickedPos : SpawnLightningPayload.targetFromView(player);
                    SpawnLightningPayload.spawnLightningBolt(serverPlayer, hand, target);
                }
                return true;
            }
            case TELEPORT -> {
                if (!selected.availableOn(stack)) return false;
                if (serverPlayer != null) TeleportToSurfacePayload.teleportToSurface(serverPlayer);
                return true;
            }
        }
        if (serverPlayer == null) return false;
        long currentTick = player.level().getGameTime();

        // Priority 1: Try multi-tool modifications if targeting a block
        if (clickedPos != null && hitResult != null) {
            MultiToolAbilityData multiTool = stack.get(HammerRegistry.MULTI_TOOL_ABILITY);
            if (multiTool != null) {
                var actions = selected.toolActions(multiTool);
                if (selected != HammerAction.AUTO && !canModifyTarget(serverPlayer, hand, hitResult, actions)) return false;
                if (actions.hasAnyEnabled() && tryToolModification(serverPlayer, hand, clickedPos, hitResult, actions)) {
                    // Track that we did a tool modification this tick
                    // This prevents RightClickItem from also executing the ability
                    recentToolModifications.put(player.getUUID(), currentTick);
                    return true;
                }
            }
        }

        // A selected block action that cannot apply leaves the interaction to vanilla.
        if (selected != HammerAction.AUTO) return false;

        // Check if we already did a tool modification this tick
        // (Handles case where RightClickItem fires after RightClickBlock did a tool modification)
        Long lastToolTick = recentToolModifications.get(player.getUUID());
        if (lastToolTick != null && lastToolTick == currentTick) {
            return false; // Don't run ability - tool modification already happened this tick
        }

        // Priority 2: Execute RIGHT_CLICK_ACTION ability
        RightClickAction action = stack.get(HammerRegistry.RIGHT_CLICK_ACTION);
        if (action == null || action.equals(RightClickAction.NONE)) {
            return false;
        }

        if (!action.abilityId().getNamespace().equals(HammerMod.CONTENT_NAMESPACE)) return false;
        String abilityName = action.abilityId().getPath();
        switch (abilityName) {
            case "throwable_ability" -> {
                if (!stack.has(HammerRegistry.THROWABLE_ABILITY)) return false;
                ThrowItemPayload.throwItem(serverPlayer, hand);
            }
            case "multi_block_mining" -> {
                if (!stack.has(HammerRegistry.MULTI_BLOCK_MINING)) return false;
                CycleMiningModePayload.cycleMiningMode(serverPlayer, stack);
            }
            case "lightning_ability" -> {
                if (clickedPos == null || !HammerAction.LIGHTNING.availableOn(stack)) return false;
                SpawnLightningPayload.spawnLightningBolt(serverPlayer, hand, clickedPos);
            }
            case "teleport_surface_ability" -> {
                if (!HammerAction.TELEPORT.availableOn(stack)) return false;
                TeleportToSurfacePayload.teleportToSurface(serverPlayer);
            }
            default -> {
                return false; // Unknown ability, don't cancel the event
            }
        }
        return true; // Ability executed, cancel the event
    }

    // Let the player open a chest or use a workbench even if a neighboring block is eligible.
    private static boolean canModifyTarget(ServerPlayer player, InteractionHand hand, BlockHitResult hit, MultiToolAbilityData actions) {
        if (!actions.hasAnyEnabled()) return false;
        var ability = actions.strip() ? ItemAbilities.AXE_STRIP : actions.flatten() ? ItemAbilities.SHOVEL_FLATTEN : ItemAbilities.HOE_TILL;
        BlockState state = player.level().getBlockState(hit.getBlockPos());
        var changed = state.getToolModifiedState(new UseOnContext(player, hand, hit), ability, true);
        return changed != null && changed != state;
    }

    /**
     * Attempts to perform a tool modification (strip/path/till) on blocks based on mining pattern.
     * Uses the same pattern calculation as multi-block mining.
     *
     * @param player the player performing the action
     * @param hand the hand holding the tool
     * @param pos the position of the target block
     * @param hitResult the block hit result
     * @param multiTool the multi-tool configuration
     * @return true if any modification was performed
     */
    private static boolean tryToolModification(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult, MultiToolAbilityData multiTool) {
        // Get blocks based on mining pattern (reuses existing pattern logic)
        var blocksToModify = MultiBlockPatterns.plan(player, player.getItemInHand(hand), pos, hitResult.getDirection()).blocks();

        // Try to modify each block in the pattern
        boolean anyModified = false;
        for (BlockPos targetPos : blocksToModify) {
            if (tryModifySingleBlock(player, hand, targetPos, hitResult, multiTool)) {
                anyModified = true;
            }
        }
        return anyModified;
    }

    /**
     * Attempts to perform a tool modification on a single block.
     *
     * @param player the player performing the action
     * @param hand the hand holding the tool
     * @param pos the position of the target block
     * @param originalHit the original block hit result (used for direction)
     * @param multiTool the multi-tool configuration
     * @return true if the block was modified
     */
    private static boolean tryModifySingleBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult originalHit, MultiToolAbilityData multiTool) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);

        // Create synthetic hit result for this block position
        BlockHitResult syntheticHit = new BlockHitResult(
                Vec3.atCenterOf(pos), originalHit.getDirection(), pos, originalHit.isInside());
        UseOnContext context = new UseOnContext(player, hand, syntheticHit);

        // Try each enabled tool action in order of priority
        if (multiTool.strip()) {
            BlockState modified = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
            if (modified != null && modified != state) {
                level.setBlock(pos, modified, 11);
                level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 1.0f);
                return true;
            }
        }

        if (multiTool.flatten()) {
            BlockState modified = state.getToolModifiedState(context, ItemAbilities.SHOVEL_FLATTEN, false);
            if (modified != null && modified != state) {
                level.setBlock(pos, modified, 11);
                level.playSound(null, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0f, 1.0f);
                return true;
            }
        }

        if (multiTool.till()) {
            BlockState modified = state.getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
            if (modified != null && modified != state) {
                level.setBlock(pos, modified, 11);
                level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                return true;
            }
        }

        return false;
    }
}
