package net.j40climb.florafauna_hammer.common.item.abilities;

import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningSpeed;
import net.j40climb.florafauna_hammer.common.item.abilities.data.ToolConfig;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Event handlers for tool abilities.
 * These work with any item that has the appropriate DataComponents.
 */
@EventBusSubscriber(modid = HammerMod.MOD_ID)
public class ToolAbilityEventHandlers {
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack itemStack = event.getEntity().getMainHandItem();

        // Component-based check - works with any item that has TOOL_CONFIG
        if (itemStack.has(HammerRegistry.TOOL_CONFIG)) {
            ToolConfig config = itemStack.get(HammerRegistry.TOOL_CONFIG);
            float newSpeed = switch (config.effectiveSpeed(HammerStage.of(itemStack))) {
                case MiningSpeed.STANDARD -> event.getOriginalSpeed();
                case MiningSpeed.EFFICIENCY -> 35.0F;
                case MiningSpeed.INSTABREAK -> 100.0F;
            };
            event.setNewSpeed(newSpeed);
        }
    }
}
