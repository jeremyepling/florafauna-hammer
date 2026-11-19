package net.j40climb.florafauna_hammer.common.item.abilities.multiblock;

//? if <26.3 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningModeData;
//?} else {
/*import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.j40climb.florafauna_hammer.HammerMod;
*///?}
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
//? if <26.3 {
import net.minecraft.client.renderer.ShapeRenderer;
//?}
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
//? if <26.3 {
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Draws the additional outlines for a multi-block mining tool. */
@EventBusSubscriber(modid = HammerMod.MOD_ID, value = Dist.CLIENT)
public class MultiBlockOutlineRenderer {
    // Show planned placements and newly exposed lava even inside the unbroken wall.
    private static final RenderPipeline PREVIEW_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(HammerMod.CONTENT_NAMESPACE, "pipeline/hammer_preview"))
            .withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
    private static final RenderType PREVIEW_LINES = RenderType.create("florafauna_hammer_preview",
            RenderSetup.builder(PREVIEW_PIPELINE).createRenderSetup());
    private static final int PLACEMENT_COLOR = 0xD92DE7B1;
    private static final int LAVA_COLOR = 0xFFFF780D;
    private static final Map<Direction, VoxelShape> FACE_OUTLINES = Shapes.rotateAll(Shapes.box(0, 0, 0, 1, 1, 0.005));

    //? if <26.3 {
    private static BlockPos previewTarget;
    private static int previewCount;
    private static long previewTick;

    @SubscribeEvent
    public static void registerMiningHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(HammerMod.CONTENT_NAMESPACE, "mining_preview"), (gui, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.player == null || mc.screen != null
                    || !(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(previewTarget)
                    || Math.abs(mc.player.level().getGameTime() - previewTick) > 1 || previewCount == 0) return;
            var stack = mc.player.getMainHandItem();
            if (!stack.has(HammerRegistry.MULTI_BLOCK_MINING)) return;
            var mining = MiningModeData.effective(stack);
            var shape = Component.translatable("mining_shape.florafauna." + mining.shape().getSerializedName());
            var key = previewCount == 1 ? "hud.florafauna_hammer.mining_preview.single" : "hud.florafauna_hammer.mining_preview";
            var label = Component.translatable(key, shape, previewCount);
            gui.text(mc.font, label, (gui.guiWidth() - mc.font.width(label)) / 2, gui.guiHeight() / 2 + 17, 0xFFDEC9FF);
        });
    }

    //?}
    @SubscribeEvent
    public static void registerPipeline(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PREVIEW_PIPELINE);
    }

    public static MultiBlockPatterns.Plan previewPlan(Player player, BlockPos target, Direction hoveredFace) {
        return MultiBlockPatterns.plan(player, player.getMainHandItem(), target, MultiBlockVisualFeedback.previewFace(target, hoveredFace));
    }

    @SubscribeEvent
    public static void extractBlockOutlines(ExtractBlockOutlineRenderStateEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !player.getMainHandItem().has(HammerRegistry.MULTI_BLOCK_MINING)) {
            return;
        }

        BlockPos target = event.getBlockPos();
        var plan = previewPlan(player, target, event.getHitResult().getDirection());
        //? if <26.3 {
        previewTarget = target.immutable();
        previewTick = player.level().getGameTime();
        previewCount = plan.blocks().size();
        //?}
        List<VoxelShape> outlines = plan.blocks().stream()
                .filter(pos -> !pos.equals(target))
                .map(pos -> event.getLevel().getBlockState(pos)
                        .getShape(event.getLevel(), pos, event.getCollisionContext())
                        //? if <26.3 {
                        .move(pos.getX(), pos.getY(), pos.getZ()))
                        //?} else {
                        /*.move(pos.getX() - target.getX(), pos.getY() - target.getY(), pos.getZ() - target.getZ()))
                        *///?}
                .filter(shape -> !shape.isEmpty())
                .toList();
        List<VoxelShape> placements = plan.stairs().stream()
                .map(pos -> plan.stairState().getShape(event.getLevel(), pos)
                        //? if <26.3 {
                        .move(pos.getX(), pos.getY(), pos.getZ()))
                        //?} else {
                        /*.move(pos.getX() - target.getX(), pos.getY() - target.getY(), pos.getZ() - target.getZ()))
                        *///?}
                .toList();
        List<LavaExposure> lava = LavaExposure.find(event.getLevel(), plan);
        if (outlines.isEmpty() && placements.isEmpty() && lava.isEmpty()) {
            return;
        }

        //? if <26.3 {
        event.addCustomRenderer((state, buffers, poseStack, translucentPass, levelState) -> {
            if (translucentPass == state.isTranslucent()) {
                Vec3 camera = levelState.cameraRenderState.pos;
                var vertices = buffers.getBuffer(RenderTypes.lines());
                for (VoxelShape outline : outlines) {
                    ShapeRenderer.renderShape(poseStack, vertices, outline, -camera.x, -camera.y, -camera.z, 0xADBCA0E8, 2.0F);
                }
                if (!placements.isEmpty() || !lava.isEmpty()) {
                    var previewVertices = buffers.getBuffer(PREVIEW_LINES);
                    for (VoxelShape stair : placements) {
                        ShapeRenderer.renderShape(poseStack, previewVertices, stair, -camera.x, -camera.y, -camera.z, PLACEMENT_COLOR, 2.5F);
                    }
                    for (LavaExposure exposure : lava) {
                        BlockPos pos = exposure.pos();
                        ShapeRenderer.renderShape(poseStack, previewVertices, FACE_OUTLINES.get(exposure.face()),
                                pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, LAVA_COLOR, 3.0F);
                    }
                }
            }
        //?} else {
        /*event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(target.getX() - camera.x, target.getY() - camera.y, target.getZ() - camera.z);
            for (VoxelShape outline : outlines) {
                collector.submitShapeOutline(poseStack, outline, RenderTypes.linesTranslucentNoDepthWrite(),
                        0x66000000, 2.0F, state.isTranslucent());
            }
            for (VoxelShape stair : placements) {
                collector.submitShapeOutline(poseStack, stair, PREVIEW_LINES, PLACEMENT_COLOR, 2.5F, state.isTranslucent());
            }
            for (LavaExposure exposure : lava) {
                BlockPos pos = exposure.pos();
                var shape = FACE_OUTLINES.get(exposure.face()).move(pos.getX() - target.getX(),
                        pos.getY() - target.getY(), pos.getZ() - target.getZ());
                collector.submitShapeOutline(poseStack, shape, PREVIEW_LINES, LAVA_COLOR, 3.0F, state.isTranslucent());
            }
            poseStack.popPose();
        *///?}
            return false; // Vanilla draws the targeted block.
        });
    }
}
