package net.j40climb.florafauna_hammer.playtest;

import net.j40climb.florafauna_hammer.common.item.abilities.data.HammerAction;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningShape;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningSpeed;
import net.j40climb.florafauna_hammer.common.item.abilities.menu.HammerMenuScreen;
import net.j40climb.florafauna_hammer.setup.ClientSetup;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class HammerRadialPlaytest {
    private static final String MENU = "gui.florafauna.hammer_menu.";

    static void steps(Playtest t) {
        t.server("Equip a fresh Hammer in the test plot", HammerPlaytest::prepare);
        t.awaitClient("Receive the Hammer", mc -> mc.player.getMainHandItem().is(HammerRegistry.HAMMER));
        t.client("Hold C until the wheel opens", mc -> ClientSetup.CYCLE_MINING_MODE_KEY.get().setDown(true));
        t.awaitClient("Open the root wheel", mc -> mc.screen instanceof HammerMenuScreen);
        t.client("Release C", mc -> ClientSetup.CYCLE_MINING_MODE_KEY.get().setDown(false));
        t.screenshot("hammer-radial-root");
        t.client("Click the outer Mining sector beyond the label bounds", HammerRadialPlaytest::clickOuterMiningSector);
        t.screenshot("hammer-radial-mining");
        t.client("Open the Tunnel wedges", mc -> select(mc, "tunnel"));
        t.screenshot("hammer-radial-tunnel");
        t.client("Right-click Walkable to favorite it", mc -> favorite(mc, MiningShape.WALKABLE_TUNNEL));
        t.awaitServer("A secondary click favorites Walkable and preserves Single", p -> {
            var mode = p.getMainHandItem().get(HammerRegistry.MULTI_BLOCK_MINING);
            return mode.shape() == MiningShape.SINGLE && mode.isFavorite(MiningShape.WALKABLE_TUNNEL);
        });
        t.client("Left-click Walkable", mc -> select(mc, "shape.walkable_tunnel"));
        t.awaitServer("Persist Walkable as the mining shape", p ->
                p.getMainHandItem().get(HammerRegistry.MULTI_BLOCK_MINING).shape() == MiningShape.WALKABLE_TUNNEL);
        t.client("Move keyboard focus clockwise and favorite the focused Large wedge", mc -> {
            key(mc, GLFW.GLFW_KEY_RIGHT);
            key(mc, GLFW.GLFW_KEY_F);
        });
        t.awaitServer("F favorites Large without switching the selected shape", p -> {
            var mode = p.getMainHandItem().get(HammerRegistry.MULTI_BLOCK_MINING);
            return mode.shape() == MiningShape.WALKABLE_TUNNEL && mode.isFavorite(MiningShape.LARGE_TUNNEL);
        });
        t.client("Apply the focused Large wedge with Enter", mc -> key(mc, GLFW.GLFW_KEY_ENTER));
        t.awaitServer("Persist Large Tunnel from the keyboard", p ->
                p.getMainHandItem().get(HammerRegistry.MULTI_BLOCK_MINING).shape() == MiningShape.LARGE_TUNNEL);
        t.screenshot("hammer-radial-favorites");
        t.client("Click the center to return to Mining", mc -> {
            HammerPlaytest.click(mc, HammerPlaytest.button(mc, CommonComponents.GUI_BACK));
            HammerPlaytest.button(mc, Component.translatable(MENU + "special"));
        });
        t.client("Return to the root with Backspace, then open Abilities", mc -> {
            key(mc, GLFW.GLFW_KEY_BACKSPACE);
            select(mc, "abilities");
        });
        t.screenshot("hammer-radial-abilities");
        t.client("Choose Scan without closing the wheel", mc -> HammerPlaytest.click(mc,
                HammerPlaytest.button(mc, actionLabel(HammerAction.SCAN))));
        t.awaitServer("Persist Scan as the grounded action", p -> p.getMainHandItem().get(HammerRegistry.HAMMER_ACTION) == HammerAction.SCAN);
        t.client("Open the Settings wheel", mc -> {
            HammerPlaytest.click(mc, HammerPlaytest.button(mc, CommonComponents.GUI_BACK));
            select(mc, "settings");
        });
        t.screenshot("hammer-radial-settings");
        t.client("Cycle the mining speed wedge", mc -> HammerPlaytest.click(mc, HammerPlaytest.button(mc,
                Component.translatable("gui.florafauna.tool_config.speed.efficiency"))));
        t.awaitServer("Settings wedge selects Instabreak", p -> p.getMainHandItem().get(HammerRegistry.TOOL_CONFIG).miningSpeed() == MiningSpeed.INSTABREAK);
        t.client("Use the center to return, then close the root", mc -> {
            HammerPlaytest.click(mc, HammerPlaytest.button(mc, CommonComponents.GUI_BACK));
            HammerPlaytest.click(mc, HammerPlaytest.button(mc, CommonComponents.GUI_DONE));
            Playtest.require(mc.screen == null, "Root center did not close the wheel");
        });
    }

    static Component actionLabel(HammerAction action) {
        return switch (action) {
            case SCAN, TELEPORT -> Component.translatable(MENU + action.getSerializedName());
            default -> action.label();
        };
    }

    static void select(Minecraft mc, String choice) {
        HammerPlaytest.click(mc, HammerPlaytest.button(mc, Component.translatable(MENU + choice)));
    }

    static void favorite(Minecraft mc, MiningShape shape) {
        Button wedge = HammerPlaytest.button(mc, Component.translatable(MENU + "shape." + shape.getSerializedName()));
        clickAt(mc, wedge.getX() + wedge.getWidth() / 2.0, wedge.getY() + wedge.getHeight() / 2.0, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    private static void clickOuterMiningSector(Minecraft mc) {
        Button wedge = HammerPlaytest.button(mc, Component.translatable(MENU + "mining"));
        Button center = HammerPlaytest.button(mc, CommonComponents.GUI_DONE);
        double cx = center.getX() + center.getWidth() / 2.0;
        double cy = center.getY() + center.getHeight() / 2.0;
        double dx = wedge.getX() + wedge.getWidth() / 2.0 - cx;
        double dy = wedge.getY() + wedge.getHeight() / 2.0 - cy;
        double radius = Math.min(138, Math.min((mc.screen.width - 16) / 2, (mc.screen.height - 48) / 2)) - 5;
        double scale = radius / Math.hypot(dx, dy);
        double x = cx + dx * scale, y = cy + dy * scale;
        Playtest.require(x < wedge.getX() || x >= wedge.getRight() || y < wedge.getY() || y >= wedge.getBottom(),
                "Probe must hit the sector outside its nominal button rectangle");
        clickAt(mc, x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        HammerPlaytest.button(mc, Component.translatable(MENU + "flat"));
    }

    private static void clickAt(Minecraft mc, double x, double y, int button) {
        mc.screen.mouseMoved(x, y);
        var click = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
        Playtest.require(mc.screen.mouseClicked(click, false), "Ignored radial mouse button " + button);
        mc.screen.mouseReleased(click);
    }

    private static void key(Minecraft mc, int key) {
        var event = new KeyEvent(key, 0, 0);
        Playtest.require(mc.screen.keyPressed(event), "Ignored radial keyboard input " + key);
        mc.screen.keyReleased(event);
    }
}
