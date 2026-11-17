package net.j40climb.florafauna_hammer.client;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerItem;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** One baked insert in the same socket and display transforms as the mechanical head. */
public final class HammerCoreModel implements ItemModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(HammerMod.CONTENT_NAMESPACE, "hammer_core");
    private static final float X = 7.5f / 16, Y = 12f / 16, Z = 7.5f / 16;

    private record Insert(List<BakedQuad> quads, Vector3fc[] extents, ModelRenderProperties properties) {}
    private final EnumMap<HammerStage, Insert> inserts;
    private final Matrix4fc transform;

    private HammerCoreModel(EnumMap<HammerStage, Insert> inserts, Matrix4fc transform) {
        this.inserts = inserts;
        this.transform = transform;
    }

    public record Unbaked() implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        private static Identifier model(HammerStage stage) {
            String name = switch (stage) {
                case FORGED -> "forged_insert";
                case RESONANT -> "resonant_rotor";
                case TEMPEST -> "tempest_rotor";
            };
            return Identifier.fromNamespaceAndPath(HammerMod.CONTENT_NAMESPACE, "item/" + name);
        }

        @Override public MapCodec<Unbaked> type() { return CODEC; }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            for (var stage : HammerStage.values()) resolver.markDependency(model(stage));
        }

        @Override
        public ItemModel bake(BakingContext context, Matrix4fc transformation) {
            var inserts = new EnumMap<HammerStage, Insert>(HammerStage.class);
            var baker = context.blockModelBaker();
            for (var stage : HammerStage.values()) {
                var resolved = baker.getModel(model(stage));
                var slots = resolved.getTopTextureSlots();
                var quads = resolved.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY).getAll();
                if (stage != HammerStage.FORGED) quads = quads.stream().map(HammerCoreModel::emissive).toList();
                inserts.put(stage, new Insert(quads, CuboidItemModelWrapper.computeExtents(quads),
                        ModelRenderProperties.fromResolvedModel(baker, resolved, slots)));
            }
            return new HammerCoreModel(inserts, transformation);
        }
    }

    // The standard emissive entity pass keeps the lit faces visible even when the player's hand
    // turns away from the sun. The surrounding metal still uses Minecraft's normal item lighting.
    private static BakedQuad emissive(BakedQuad quad) {
        var material = quad.materialInfo();
        var emissive = new BakedQuad.MaterialInfo(material.sprite(), material.layer(),
                RenderTypes.entityTranslucentEmissive(material.sprite().atlasLocation()), material.tintIndex(),
                false, material.lightEmission(), false);
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(),
                emissive, quad.bakedNormals(), quad.bakedColors());
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext context,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        HammerStage stage = HammerStage.of(stack);
        Insert insert = inserts.get(stage);
        var layer = output.newLayer();
        output.appendModelIdentityElement(this);
        output.appendModelIdentityElement(stage);
        insert.properties.applyToLayer(layer, context);
        layer.prepareQuadList().addAll(insert.quads);
        layer.setExtents(() -> insert.extents);
        if (stage == HammerStage.FORGED) {
            layer.setLocalTransform(transform);
            return;
        }

        var mc = Minecraft.getInstance();
        if (level == null) level = mc.level;
        double time = level == null ? 0 : level.getGameTime() + (double) mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean tempest = stage == HammerStage.TEMPEST;
        int charge = HammerItem.charge(stack);
        float fraction = (float) charge / stage.capacity();
        float drive = HammerCoreEffects.drive(stack, owner, time, context);
        // Absolute world time keeps an inventory sync, a throw, or a change of perspective from restarting the rotor.
        double period = tempest ? 80 : 160;
        float angle = (float) ((time % period) * Math.TAU / period + drive * 0.38);
        layer.setLocalTransform(new Matrix4f(transform).translate(X, Y, Z)
                .rotateZ(tempest ? 0.43f : 0.24f).rotateY(angle).translate(-X, -Y, -Z));
        double pulse = 0.5 + 0.5 * Math.sin(time * (tempest ? 0.095 : 0.065));
        double glow = charge == 0 ? 0.14 : 0.35 + 0.55 * Math.sqrt(fraction);
        glow += pulse * (tempest ? 0.10 : 0.055) * Math.min(1, fraction * 4);
        if (charge > 0 && fraction < 0.25) glow -= 0.08 * Math.pow(pulse, 6);
        glow = Math.clamp(glow + drive * 0.33, 0.08, 1.24);
        int red = (int) Math.min(255, (tempest ? 213 : 177) * glow);
        int green = (int) Math.min(255, (tempest ? 142 : 124) * glow + Math.max(0, drive) * 45);
        int blue = (int) Math.min(255, 255 * glow);
        int violet = ARGB.color(255, red, green, blue);
        var tints = layer.tintLayers();
        tints.add(violet);
        tints.add(ARGB.scaleRGB(violet, 0.87f));
        tints.add(ARGB.scaleRGB(violet, 0.74f));
        // The GUI atlas can reuse this slot each frame. Never key its cache by animation time.
        output.appendModelIdentityElement(charge);
        output.setAnimated();
    }
}
