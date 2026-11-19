package net.j40climb.florafauna_hammer.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.j40climb.florafauna_hammer.common.item.abilities.GroundSlam;
import net.j40climb.florafauna_hammer.common.item.abilities.RightClickActionHandler;
import net.j40climb.florafauna_hammer.common.item.abilities.data.HammerAction;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningShape;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.SeismicScanPayload;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerItem;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
//? if >=26.3 {
/*import net.minecraft.world.entity.EntityTypes;*/
//?} else {
import net.minecraft.world.entity.EntityType;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.List;

final class HammerPowerGameTests {
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private static final BlockPos FLOOR = new BlockPos(8, 8, 8);

    static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> env) {
        var mine = Identifier.fromNamespaceAndPath("florafauna", "test_hammer_mine");
        HammerGameTests.registerStructureTest(event, env, "hammer_grounded_lightning_and_teleport", mine, 100, HammerPowerGameTests::groundedAbilities);
        HammerGameTests.registerStructureTest(event, env, "hammer_slam_impact", mine, 100, HammerPowerGameTests::slamImpact);
        HammerGameTests.registerStructureTest(event, env, "hammer_slam_rejections", mine, 100, HammerPowerGameTests::slamRejections);
        HammerGameTests.registerStructureTest(event, env, "hammer_scan_private_results", mine, 200, HammerPowerGameTests::scan);
    }

    static ServerPlayer airborne(GameTestHelper h) {
        var player = HammerMiningGameTests.player(h, MiningShape.SINGLE);
        player.setGameMode(GameType.SURVIVAL);
        var floor = h.absolutePos(FLOOR);
        HammerMiningGameTests.fill(player, floor.above().offset(-4, 0, -4), floor.above(5).offset(4, 0, 4), Blocks.AIR);
        HammerMiningGameTests.fill(player, floor.offset(-4, 0, -4), floor.offset(4, 0, 4), Blocks.STONE);
        player.setPos(Vec3.atBottomCenterOf(floor.above(3)));
        player.setOnGround(false);
        player.getMainHandItem().set(HammerRegistry.HAMMER_ACTION, HammerAction.THROW);
        return player;
    }

    private static boolean clickAir(ServerPlayer player) {
        var click = new PlayerInteractEvent.RightClickItem(player, HAND);
        RightClickActionHandler.onRightClickItem(click);
        return click.isCanceled();
    }

    private static void groundedAbilities(GameTestHelper h) {
        var origin = h.absolutePos(FLOOR.above());
        var player = scanner(h, origin).player;
        try {
            player.setItemInHand(HAND, HammerStage.TEMPEST.createStack());
            var target = origin.east(2);
            player.getMainHandItem().set(HammerRegistry.HAMMER_ACTION, HammerAction.LIGHTNING);
            var hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
            var click = new PlayerInteractEvent.RightClickBlock(player, HAND, target, hit);
            RightClickActionHandler.onRightClickBlock(click);
            //? if >=26.3 {
            /*var bolts = h.getLevel().getEntities(EntityTypes.LIGHTNING_BOLT, bolt -> bolt.blockPosition().equals(target));*/
            //?} else {
            var bolts = h.getLevel().getEntities(EntityType.LIGHTNING_BOLT, bolt -> bolt.blockPosition().equals(target));
            //?}
            h.assertTrue(click.isCanceled() && bolts.size() == 1, "Lightning did not strike the selected target");
            bolts.getFirst().discard();
            var roof = origin.above(3);
            h.getLevel().setBlockAndUpdate(origin.above(2), Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(roof.above(), Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(roof.above(2), Blocks.AIR.defaultBlockState());
            player.getMainHandItem().set(HammerRegistry.HAMMER_ACTION, HammerAction.TELEPORT);
            h.assertTrue(clickAir(player) && player.blockPosition().equals(roof.above()),
                    "Surface Teleport failed to reach the cleared surface");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    private static void slamImpact(GameTestHelper h) {
        var player = airborne(h);
        var floor = h.absolutePos(FLOOR);
        var hammer = player.getMainHandItem();
        //? if >=26.3 {
        /*var near = h.spawn(EntityTypes.HUSK, FLOOR.above().east());*/
        //?} else {
        var near = h.spawn(EntityType.HUSK, FLOOR.above().east());
        //?}
        //? if >=26.3 {
        /*var far = h.spawn(EntityTypes.HUSK, FLOOR.above().east(3));*/
        //?} else {
        var far = h.spawn(EntityType.HUSK, FLOOR.above().east(3));
        //?}
        //? if >=26.3 {
        /*var cow = h.spawn(EntityTypes.COW, FLOOR.above().north());*/
        //?} else {
        var cow = h.spawn(EntityType.COW, FLOOR.above().north());
        //?}
        float health = near.getHealth(), friendlyHealth = cow.getHealth(), selfHealth = player.getHealth();
        var hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
        var click = new PlayerInteractEvent.RightClickBlock(player, HAND, floor, hit);
        RightClickActionHandler.onRightClickBlock(click);
        h.assertTrue(click.isCanceled() && player.getMainHandItem() == hammer && HammerItem.charge(hammer) == 84
                && player.getDeltaMovement().y < -1 && player.hasData(HammerRegistry.PENDING_SLAM), "Airborne block click failed to slam instead of throwing");
        h.assertTrue(clickAir(player) && HammerItem.charge(hammer) == 84, "Repeated slam spent charge twice");
        player.setPos(Vec3.atBottomCenterOf(floor.above()));
        player.setOnGround(true);
        GroundSlam.tick(new PlayerTickEvent.Post(player));
        h.assertTrue(near.getHealth() < far.getHealth() && far.getHealth() < health && health - near.getHealth() <= 4,
                "Hostile damage did not fall off with distance");
        h.assertTrue(near.getDeltaMovement().horizontalDistance() > far.getDeltaMovement().horizontalDistance()
                && far.getDeltaMovement().horizontalDistance() > 0, "Knockback did not decrease with distance");
        h.assertTrue(cow.getHealth() == friendlyHealth && player.getHealth() == selfHealth
                && h.getLevel().getBlockState(floor).is(Blocks.STONE), "Impact affected a friendly target or destroyed terrain");
        //? if >=26.3 {
        /*near.setInvulnerableTime(0);*/
        //?} else {
        near.invulnerableTime = 0;
        //?}
        float after = near.getHealth();
        GroundSlam.tick(new PlayerTickEvent.Post(player));
        h.assertTrue(after == near.getHealth() && !player.hasData(HammerRegistry.PENDING_SLAM), "Impact fired twice");
        player.setPos(Vec3.atBottomCenterOf(floor.above(3)));
        player.setOnGround(false);
        h.assertTrue(clickAir(player) && HammerItem.charge(hammer) == 84 && !player.hasData(HammerRegistry.PENDING_SLAM),
                "Cooldown allowed a second activation or throw");
        h.succeed();
    }

    private static void slamRejections(GameTestHelper h) {
        var player = airborne(h);
        var hammer = player.getMainHandItem();
        hammer.set(HammerRegistry.HAMMER_CHARGE, 11);
        h.assertTrue(clickAir(player) && player.getMainHandItem() == hammer && HammerItem.charge(hammer) == 11,
                "Insufficient charge fell through to Throw");
        hammer.set(HammerRegistry.HAMMER_CHARGE, 64);
        player.getAbilities().flying = true;
        h.assertTrue(clickAir(player) && !player.hasData(HammerRegistry.PENDING_SLAM), "Flying player could slam");
        player.getAbilities().flying = false;
        player.startFallFlying();
        h.assertTrue(clickAir(player) && !player.hasData(HammerRegistry.PENDING_SLAM), "Gliding player could slam");
        player.stopFallFlying();
        player.setSwimming(true);
        h.assertTrue(clickAir(player) && !player.hasData(HammerRegistry.PENDING_SLAM), "Swimming player could slam");
        player.setSwimming(false);
        h.assertTrue(HammerItem.charge(hammer) == 64, "Rejected movements consumed charge");
        h.assertTrue(clickAir(player) && player.hasData(HammerRegistry.PENDING_SLAM), "Valid slam rejected");
        player.setItemInHand(HAND, hammer.copy());
        GroundSlam.tick(new PlayerTickEvent.Post(player));
        h.assertTrue(!player.hasData(HammerRegistry.PENDING_SLAM), "Replacing the equipped Hammer kept its slam pending");
        h.succeed();
    }

    private record Scanner(ServerPlayer player, List<SeismicScanPayload> received) {}

    @SuppressWarnings("removal")
    private static Scanner scanner(GameTestHelper h, BlockPos position) {
        var player = h.makeMockServerPlayerInLevel();
        var connection = player.connection.getConnection();
        NetworkRegistry.configureMockConnection(connection);
        var received = new ArrayList<SeismicScanPayload>();
        connection.channel().pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                if (message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof SeismicScanPayload scan) {
                    received.add(scan);
                }
                super.write(ctx, message, promise);
            }
        });
        player.setPos(Vec3.atBottomCenterOf(position));
        player.setOnGround(true);
        player.setItemInHand(HAND, HammerStage.RESONANT.createStack());
        player.getMainHandItem().set(HammerRegistry.HAMMER_ACTION, HammerAction.SCAN);
        return new Scanner(player, received);
    }

    private static void scan(GameTestHelper h) {
        var origin = h.absolutePos(FLOOR.above());
        h.getLevel().setBlockAndUpdate(origin.east(4), Blocks.DIAMOND_ORE.defaultBlockState());
        var first = scanner(h, origin);
        var second = scanner(h, origin.south(4));
        var hammer = first.player.getMainHandItem();
        hammer.set(HammerRegistry.HAMMER_CHARGE, 15);
        clickAir(first.player);
        h.assertTrue(first.received.isEmpty() && HammerItem.charge(hammer) == 15, "Rejected scan sent a pulse or spent charge");
        hammer.set(HammerRegistry.HAMMER_CHARGE, 64);
        clickAir(first.player);
        clickAir(first.player);
        clickAir(second.player);
        h.assertTrue(HammerItem.charge(hammer) == 48 && HammerItem.charge(second.player.getMainHandItem()) == 48,
                "Scanner charge or player-specific cooldown failed");
        h.succeedWhen(() -> {
            for (var scan : List.of(first, second)) {
                h.assertTrue(scan.received.size() == 2, "Expected only the owner's pulse and result, got " + scan.received);
                var pulse = scan.received.getFirst();
                var result = scan.received.getLast();
                h.assertTrue(pulse.started() && !result.started() && pulse.origin().equals(result.origin())
                        && !result.markers().isEmpty(), "Empty or misrouted result");
            }
            h.assertTrue(first.received.getLast().origin().equals(origin) && second.received.getLast().origin().equals(origin.south(4)),
                    "Connected players received each other's scans");
            for (var scan : List.of(first, second)) h.getLevel().getServer().getPlayerList().remove(scan.player);
        });
    }
}
