package net.j40climb.florafauna_hammer.common.item.abilities.menu;

import java.util.EnumMap;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class HammerMenuScreen extends Screen {
    private static final int WIDTH = 278, HEIGHT = 209;
    private static final int LILAC = 0xffd9b3ff, GOLD = 0xffffdf82;
    private static final String MENU = "gui.florafauna.hammer_menu.";
    private static final String CONFIG = "gui.florafauna.tool_config.";

    private enum Tab { MINING, ABILITIES, SETTINGS }

    private enum Group {
        FLAT(MiningShape.SINGLE, MiningShape.FLAT_3X3, MiningShape.FLAT_5X5, MiningShape.FLAT_7X7),
        TUNNEL(MiningShape.WALKABLE_TUNNEL, MiningShape.LARGE_TUNNEL, MiningShape.BORE),
        SPECIAL(MiningShape.SHAPELESS, MiningShape.TUNNEL_UP, MiningShape.TUNNEL_DOWN);
        final MiningShape[] shapes;
        Group(MiningShape... shapes) { this.shapes = shapes; }
    }

    private final int slot;
    private final ItemStack tool;
    private final EnumMap<Tab, Button> tabs = new EnumMap<>(Tab.class);
    private final EnumMap<Group, Button> groups = new EnumMap<>(Group.class);
    private final EnumMap<MiningShape, Button> shapes = new EnumMap<>(MiningShape.class);
    private final EnumMap<HammerAction, Button> abilities = new EnumMap<>(HammerAction.class);
    private MiningModeData mining;
    private HammerAction action;
    private ToolConfig config;
    private Tab tab;
    private Group group = Group.FLAT;
    private int left, top;

    public HammerMenuScreen(int slot, ItemStack stack) {
        super(Component.translatable("item.florafauna.hammer"));
        this.slot = slot;
        this.tool = stack.copy();
        mining = stack.get(HammerRegistry.MULTI_BLOCK_MINING);
        action = stack.get(HammerRegistry.HAMMER_ACTION);
        config = stack.get(HammerRegistry.TOOL_CONFIG);
        tab = mining != null ? Tab.MINING : action != null ? Tab.ABILITIES : Tab.SETTINGS;
        if (mining != null) {
            for (var candidate : Group.values()) {
                for (var shape : candidate.shapes) if (shape == mining.shape()) group = candidate;
            }
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        for (var choice : Tab.values()) {
            Button button = button(menuLabel(choice), 10 + choice.ordinal() * 87, 31, 84, ignored -> {
                tab = choice;
                rebuildWidgets();
                setFocused(tabs.get(tab));
            });
            button.active = switch (choice) {
                case MINING -> mining != null;
                case ABILITIES -> action != null;
                case SETTINGS -> config != null;
            };
            tabs.put(choice, button);
        }
        switch (tab) {
            case MINING -> miningButtons();
            case ABILITIES -> abilityButtons();
            case SETTINGS -> settingsButtons();
        }
        button(CommonComponents.GUI_DONE, WIDTH - 66, HEIGHT - 25, 56, ignored -> onClose());
    }

    private void miningButtons() {
        shapes.clear();
        for (Group choice : Group.values()) {
            groups.put(choice, button(menuLabel(choice), 10 + choice.ordinal() * 87, 60, 84, ignored -> {
                group = choice;
                rebuildWidgets();
                setFocused(groups.get(group));
            }));
        }
        int y = 85;
        for (var shape : group.shapes) {
            var name = Component.translatable("mining_shape.florafauna." + shape.getSerializedName());
            shapes.put(shape, button(name, 10, y, WIDTH - 55, ignored -> {
                mining = mining.withShape(shape);
                ClientPacketDistributor.sendToServer(new SelectMiningModePayload(slot, shape, false));
            }));
            Button star = button(Component.empty(), WIDTH - 40, y, 30, clicked -> {
                mining = mining.toggleFavorite(shape);
                labelStar(clicked, shape, name);
                ClientPacketDistributor.sendToServer(new SelectMiningModePayload(slot, shape, true));
            });
            labelStar(star, shape, name);
            y += 23;
        }
    }

    private void labelStar(Button star, MiningShape shape, Component name) {
        boolean favored = mining.isFavorite(shape);
        star.setMessage(Component.literal(favored ? "★" : "☆").withColor(favored ? GOLD : 0xffd4cbdc));
        star.setTooltip(Tooltip.create(Component.translatable(MENU + (favored ? "unfavorite" : "favorite"), name)));
    }

    private void abilityButtons() {
        for (var choice : HammerAction.values()) {
            Button button = button(choice.label(), 10 + choice.ordinal() % 2 * 131, 77 + choice.ordinal() / 2 * 25, 127, ignored -> {
                action = choice;
                ClientPacketDistributor.sendToServer(new SelectHammerActionPayload(slot, choice));
            });
            button.active = choice.availableOn(tool);
            abilities.put(choice, button);
        }
    }

    private void settingsButtons() {
        setting(0, ToolConfig::withToggledEnchantment);
        setting(1, ToolConfig::withNextMiningSpeed);
        setting(2, ToolConfig::withToggledVeinMatching);
        setting(3, ToolConfig::withToggledTunnelLighting);
    }

    private void setting(int row, UnaryOperator<ToolConfig> change) {
        button(settingValue(row), 142, 72 + 26 * row, 126, clicked -> {
            config = change.apply(config);
            clicked.setMessage(settingValue(row));
            ClientPacketDistributor.sendToServer(new UpdateToolConfigPayload(slot, config));
        });
    }

    private Component settingValue(int row) {
        return Component.translatable(CONFIG + switch (row) {
            case 0 -> "enchantment." + (config.fortune() ? "fortune" : "silk_touch");
            case 1 -> "speed." + config.miningSpeed().getSerializedName();
            case 2 -> "vein." + (config.exactVein() ? "exact" : "same_ore");
            default -> "lighting." + (config.tunnelLighting() ? "on" : "off");
        });
    }

    private Button button(Component label, int x, int y, int w, Button.OnPress press) {
        return addRenderableWidget(Button.builder(label, press).bounds(left + x, top + y, w, 18).build());
    }

    private static Component menuLabel(Enum<?> choice) {
        return Component.translatable(MENU + choice.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xf5201b29);
        g.outline(left, top, WIDTH, HEIGHT, 0xff705488);
        g.fill(left + 1, top + 1, left + WIDTH - 1, top + 25, 0xff392d46);
        g.fill(left + 10, top + 54, left + WIDTH - 10, top + 55, 0xff604c70);
        g.item(tool, left + 9, top + 5);
        g.text(font, title, left + 32, top + 9, 0xfff8edff);
        Component selected = switch (tab) {
            case MINING -> Component.translatable("mining_shape.florafauna." + mining.shape().getSerializedName());
            case ABILITIES -> action.label();
            case SETTINGS -> Component.empty();
        };
        g.text(font, selected, left + WIDTH - 10 - font.width(selected), top + 9, GOLD);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        highlight(g, tabs.get(tab), LILAC);
        switch (tab) {
            case MINING -> {
                highlight(g, groups.get(group), LILAC);
                highlight(g, shapes.get(mining.shape()), GOLD);
                g.text(font, Component.translatable(MENU + "favorites_hint"), left + 10, top + HEIGHT - 20, GOLD);
            }
            case ABILITIES -> {
                g.text(font, Component.translatable(MENU + "grounded"), left + 10, top + 61, LILAC);
                highlight(g, abilities.get(action), GOLD);
            }
            case SETTINGS -> {
                String[] labels = {"enchantment", "mining_speed", "vein_matching", "tunnel_lighting"};
                for (int i = 0; i < labels.length; i++) {
                    g.text(font, Component.translatable(CONFIG + labels[i]), left + 12, top + 77 + 26 * i, 0xffeee5f6);
                }
            }
        }
    }

    private static void highlight(GuiGraphicsExtractor g, Button button, int color) {
        if (button != null) g.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, color);
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.player.getInventory().getSelectedSlot() != slot
                || !minecraft.player.getMainHandItem().is(tool.getItem())) onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
