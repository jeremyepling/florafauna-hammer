package net.j40climb.florafauna_hammer.client;

import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

import java.util.List;
import java.util.function.BiConsumer;

@EventBusSubscriber(modid = HammerMod.MOD_ID, value = Dist.CLIENT)
public final class HammerDebugInfo {
    @SubscribeEvent
    public static void offerDiagnostics(InterModEnqueueEvent event) {
        // The optional FloraFauna debug overlay accepts a callback using only shared game and JDK types.
        InterModComms.sendTo(HammerMod.MOD_ID, "florafauna", "hammer_tool_diagnostics",
                () -> (BiConsumer<ItemStack, List<String>>) HammerDebugInfo::append);
    }

    private static void append(ItemStack stack, List<String> lines) {
        var mining = stack.get(HammerRegistry.MULTI_BLOCK_MINING);
        if (mining != null) lines.add("Mining: " + mining.shape().name() + ", radius=" + mining.radius());
        var config = stack.get(HammerRegistry.TOOL_CONFIG);
        if (config != null) {
            var enchant = config.fortune() ? "Fortune" : "SilkTouch";
            lines.add("Tool Config: " + enchant + ", " + config.miningSpeed().name());
        }
        if (stack.has(HammerRegistry.LIGHTNING_ABILITY)) lines.add("Lightning: Yes");
        if (stack.has(HammerRegistry.TELEPORT_SURFACE_ABILITY)) lines.add("Teleport: Yes");
        var throwable = stack.get(HammerRegistry.THROWABLE_ABILITY);
        if (throwable != null) {
            lines.add("Throw: dmg=" + throwable.damage() + ", range=" + throwable.maxRange());
            lines.add("  return=" + (throwable.autoReturn() ? "yes" : "no") + ", speed=" + throwable.returnSpeed());
        }
    }

    private HammerDebugInfo() {}
}
