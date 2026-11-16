package net.j40climb.florafauna_hammer.common.item.abilities.menu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.j40climb.florafauna_hammer.common.item.abilities.data.HammerAction;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningModeData;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningShape;
import net.j40climb.florafauna_hammer.common.item.abilities.data.ToolConfig;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.SelectHammerActionPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.SelectMiningModePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.UpdateToolConfigPayload;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class HammerMenuScreen extends Screen {
    private static final String MENU = "gui.florafauna.hammer_menu.";
    private static final String CONFIG = "gui.florafauna.tool_config.";

    private enum Page { ROOT, MINING, ABILITIES, SETTINGS, SHAPES }

    private enum Group {
        FLAT(Items.SMOOTH_STONE, MiningShape.SINGLE, MiningShape.FLAT_3X3, MiningShape.FLAT_5X5, MiningShape.FLAT_7X7),
        TUNNEL(Items.RAIL, MiningShape.WALKABLE_TUNNEL, MiningShape.LARGE_TUNNEL, MiningShape.BORE),
        SPECIAL(Items.AMETHYST_CLUSTER, MiningShape.SHAPELESS, MiningShape.TUNNEL_UP, MiningShape.TUNNEL_DOWN);

        final Item icon;
        final List<MiningShape> shapes;

        Group(Item icon, MiningShape... shapes) {
            this.icon = icon;
            this.shapes = List.of(shapes);
        }

        Component label() {
            return Component.translatable(MENU + name().toLowerCase(Locale.ROOT));
        }
    }

    private final int slot;
    private final ItemStack tool;
    private final List<RadialButton> wedges = new ArrayList<>();
    private final EnumMap<MiningShape, RadialButton> shapeButtons = new EnumMap<>(MiningShape.class);
    private final EnumMap<HammerAction, RadialButton> actionButtons = new EnumMap<>(HammerAction.class);
    private MiningModeData mining;
    private HammerAction action;
    private ToolConfig config;
    private Page page = Page.ROOT;
    private Group group = Group.FLAT;
    private int cx, cy, outer, inner;
    private boolean keyboardNavigation;
    private boolean favoriteKeyDown;
    private double pointerX, pointerY;

    public HammerMenuScreen(int slot, ItemStack stack) {
        super(Component.translatable("item.florafauna.hammer"));
        this.slot = slot;
        tool = stack.copy();
        mining = stack.get(HammerRegistry.MULTI_BLOCK_MINING);
        action = stack.get(HammerRegistry.HAMMER_ACTION);
        config = stack.get(HammerRegistry.TOOL_CONFIG);
    }

    @Override
    protected void init() {
        cx = width / 2;
        cy = height / 2;
        outer = Math.min(138, Math.min((width - 16) / 2, (height - 48) / 2));
        inner = outer / 3;
        wedges.clear();
        shapeButtons.clear();
        actionButtons.clear();
        switch (page) {
            case ROOT -> {
                section(Page.MINING, Items.DIAMOND_PICKAXE, mining == null ? null : shortShape(mining.shape()), mining != null);
                section(Page.ABILITIES, Items.AMETHYST_SHARD, action == null ? null : shortAction(action), action != null);
                section(Page.SETTINGS, Items.COMPARATOR, null, config != null);
            }
            case MINING -> {
                for (var choice : Group.values()) {
                    var slice = wedge(3, choice.label(), null, choice.icon, ignored -> {
                        group = choice;
                        open(Page.SHAPES);
                    }, null);
                    slice.setSelected(choice.shapes.contains(mining.shape()));
                }
            }
            case SHAPES -> {
                for (var shape : group.shapes) {
                    var slice = wedge(group.shapes.size(), shortShape(shape), null, shapeIcon(shape),
                            ignored -> chooseShape(shape), () -> favorite(shape));
                    slice.setSelected(shape == mining.shape());
                    slice.setFavorite(mining.isFavorite(shape));
                    shapeButtons.put(shape, slice);
                }
            }
            case ABILITIES -> {
                for (var choice : HammerAction.values()) {
                    var slice = wedge(HammerAction.values().length, shortAction(choice), null, actionIcon(choice),
                            ignored -> chooseAction(choice), null);
                    slice.active = choice.availableOn(tool);
                    slice.setSelected(choice == action);
                    actionButtons.put(choice, slice);
                }
            }
            case SETTINGS -> {
                setting("enchantment", Items.ENCHANTED_BOOK, () -> config.fortune() ? "enchantment.fortune" : "enchantment.silk_touch",
                        ToolConfig::withToggledEnchantment);
                setting("mining_speed", Items.GOLDEN_PICKAXE, () -> "speed." + config.miningSpeed().getSerializedName(),
                        ToolConfig::withNextMiningSpeed);
                setting("vein_matching", Items.RAW_IRON, () -> config.exactVein() ? "vein.exact" : "vein.same_ore",
                        ToolConfig::withToggledVeinMatching);
                setting("tunnel_lighting", Items.TORCH, () -> config.tunnelLighting() ? "lighting.on" : "lighting.off",
                        ToolConfig::withToggledTunnelLighting);
            }
        }
        addRenderableWidget(new HubButton());
    }

    private void open(Page next) {
        page = next;
        rebuildWidgets();
    }

    private void back() {
        switch (page) {
            case ROOT -> onClose();
            case SHAPES -> open(Page.MINING);
            default -> open(Page.ROOT);
        }
    }

    private void section(Page next, Item icon, Component caption, boolean available) {
        wedge(3, pageLabel(next), caption, icon, ignored -> open(next), null).active = available;
    }

    private RadialButton wedge(int count, Component label, Component caption, Item icon, Button.OnPress select, Runnable alternate) {
        var slice = new RadialButton(cx, cy, outer, inner, wedges.size(), count, label, caption,
                new ItemStack(icon), select, alternate);
        wedges.add(addRenderableWidget(slice));
        return slice;
    }

    private void chooseShape(MiningShape shape) {
        mining = mining.withShape(shape);
        shapeButtons.forEach((candidate, button) -> button.setSelected(candidate == shape));
        ClientPacketDistributor.sendToServer(new SelectMiningModePayload(slot, shape, false));
    }

    private void favorite(MiningShape shape) {
        mining = mining.toggleFavorite(shape);
        shapeButtons.get(shape).setFavorite(mining.isFavorite(shape));
        ClientPacketDistributor.sendToServer(new SelectMiningModePayload(slot, shape, true));
    }

    private void chooseAction(HammerAction choice) {
        action = choice;
        actionButtons.forEach((candidate, button) -> button.setSelected(candidate == choice));
        ClientPacketDistributor.sendToServer(new SelectHammerActionPayload(slot, choice));
    }

    private void setting(String name, Item icon, Supplier<String> value, UnaryOperator<ToolConfig> change) {
        wedge(4, Component.translatable(CONFIG + value.get()), Component.translatable(CONFIG + name), icon, button -> {
            config = change.apply(config);
            button.setMessage(Component.translatable(CONFIG + value.get()));
            ClientPacketDistributor.sendToServer(new UpdateToolConfigPayload(slot, config));
        }, null);
    }

    private static Item shapeIcon(MiningShape shape) {
        return switch (shape) {
            case SINGLE -> Items.STONE;
            case FLAT_3X3 -> Items.STONE_BRICKS;
            case FLAT_5X5 -> Items.DEEPSLATE_BRICKS;
            case FLAT_7X7 -> Items.POLISHED_BLACKSTONE_BRICKS;
            case WALKABLE_TUNNEL -> Items.LEATHER_BOOTS;
            case LARGE_TUNNEL -> Items.MINECART;
            case BORE -> Items.IRON_PICKAXE;
            case SHAPELESS -> Items.DIAMOND_ORE;
            case TUNNEL_UP -> Items.QUARTZ_STAIRS;
            case TUNNEL_DOWN -> Items.DEEPSLATE_BRICK_STAIRS;
        };
    }

    private Item actionIcon(HammerAction choice) {
        return switch (choice) {
            case AUTO -> Items.COMPASS;
            case STRIP -> Items.IRON_AXE;
            case PATH -> Items.IRON_SHOVEL;
            case TILL -> Items.IRON_HOE;
            case THROW -> tool.getItem();
            case LIGHTNING -> Items.LIGHTNING_ROD;
            case TELEPORT -> Items.ENDER_PEARL;
            case SCAN -> Items.ECHO_SHARD;
        };
    }

    private static Component shortShape(MiningShape shape) {
        return Component.translatable(MENU + "shape." + shape.getSerializedName());
    }

    private static Component shortAction(HammerAction choice) {
        return switch (choice) {
            case TELEPORT, SCAN -> Component.translatable(MENU + choice.getSerializedName());
            default -> choice.label();
        };
    }

    private static Component pageLabel(Page page) {
        return Component.translatable(MENU + page.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        var heading = switch (page) {
            case ROOT -> title;
            case SHAPES -> Component.translatable(MENU + "group_title", pageLabel(Page.MINING), group.label());
            default -> pageLabel(page);
        };
        g.centeredText(font, heading, cx, cy - outer - 16, 0xffffe1aa);
        if (page == Page.SHAPES) {
            g.centeredText(font, Component.translatable(MENU + "favorites_hint"), cx, cy + outer + 5, 0xfff4e1c5);
        } else if (page == Page.ABILITIES) {
            g.centeredText(font, Component.translatable(MENU + "grounded", action.label()), cx, cy + outer + 5, 0xfff4e1c5);
        }
        if (page == Page.MINING || page == Page.SHAPES) {
            var name = Component.translatable("mining_shape.florafauna." + mining.shape().getSerializedName());
            g.centeredText(font, Component.translatable(MENU + "equipped", name), cx,
                    cy + outer + (page == Page.SHAPES ? 15 : 5), 0xffffdd8a);
        }
    }

    private final class HubButton extends Button {
        HubButton() {
            super(cx - inner, cy - inner, 2 * inner, 2 * inner,
                    page == Page.ROOT ? CommonComponents.GUI_DONE : CommonComponents.GUI_BACK,
                    ignored -> back(), DEFAULT_NARRATION);
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return Math.hypot(x - cx, y - cy) < inner;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int radius = inner - 2;
            int edge = isMouseOver(mouseX, mouseY) || isFocused() ? 0xffffdf94 : 0xffa88c69;
            RadialButton.drawHub(g, cx, cy, radius, 0xff251d2d, edge);
            g.item(tool, cx - 8, cy - 12);
            g.centeredText(font, getMessage(), cx, cy + 7, 0xfff8e7cf);
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        keyboardNavigation = false;
        pointerX = x;
        pointerY = y;
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        mouseMoved(event.x(), event.y());
        boolean handled = super.mouseClicked(event, doubleClick);
        // A navigation click may have replaced the widget that just handled it.
        if (getFocused() != null && !children().contains(getFocused())) clearFocus();
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                back();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> {
                keyboardNavigation = true;
                int step = event.key() == GLFW.GLFW_KEY_RIGHT || event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                int index = wedges.indexOf(getFocused());
                if (index < 0 && step < 0) index = 0;
                for (int i = 0; i < wedges.size(); i++) {
                    index = Math.floorMod(index + step, wedges.size());
                    if (wedges.get(index).active) {
                        setFocused(wedges.get(index));
                        break;
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                if (page != Page.SHAPES) break;
                if (favoriteKeyDown) return true;
                favoriteKeyDown = true;
                if (!keyboardNavigation) {
                    for (var button : wedges) {
                        if (button.isMouseOver(pointerX, pointerY)) return button.toggleFavorite();
                    }
                }
                return getFocused() instanceof RadialButton button && button.toggleFavorite();
            }
            case GLFW.GLFW_KEY_TAB -> keyboardNavigation = true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F) favoriteKeyDown = false;
        return super.keyReleased(event);
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.player.getInventory().getSelectedSlot() != slot
                || !minecraft.player.getMainHandItem().is(tool.getItem())) onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
