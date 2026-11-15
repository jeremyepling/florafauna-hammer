package net.j40climb.florafauna_hammer.test;

import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningModeData;
import net.j40climb.florafauna_hammer.common.item.abilities.data.MiningShape;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

public final class HammerGameTests {
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    public static void registerTests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(Identifier.fromNamespaceAndPath(HammerMod.MOD_ID, "default"),
                new TestEnvironmentDefinition.AllOf());
        registerMiningModeTests(event, env);
        HammerAbilityGameTests.register(event, env);
        ChargedHammerGameTests.register(event, env);
        HammerMiningGameTests.register(event, env);
    }

    static void registerStructureTest(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> env,
            String name,
            Identifier structure,
            int maxTicks,
            Consumer<GameTestHelper> testFunction
    ) {
        Identifier testId = Identifier.fromNamespaceAndPath(HammerMod.MOD_ID, name);

        TestData<Holder<TestEnvironmentDefinition<?>>> testData = new TestData<>(
                env,
                structure,
                maxTicks,
                20,   // setupTicks - give block entities time to load
                true  // required
        );

        event.registerTest(testId, new SimpleGameTestInstance(testData, testFunction));
    }

    /**
     * Register a test with the default empty structure and settings.
     */
    static void registerTest(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> env,
            String name,
            Consumer<GameTestHelper> testFunction
    ) {
        Identifier testId = Identifier.fromNamespaceAndPath(HammerMod.MOD_ID, name);

        TestData<Holder<TestEnvironmentDefinition<?>>> testData = new TestData<>(
                env,
                EMPTY_STRUCTURE,
                100, // maxTicks
                0,   // setupTicks
                true  // required
        );

        // Create a SimpleGameTestInstance wrapper
        event.registerTest(testId, new SimpleGameTestInstance(testData, testFunction));
    }

    private static void registerMiningModeTests(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> env) {
        registerTest(event, env, "mining_mode_initial_state", HammerGameTests::testMiningModeInitialState);
        registerTest(event, env, "mining_mode_cycling", HammerGameTests::testMiningModeCycling);
        registerTest(event, env, "mining_mode_wrap_around", HammerGameTests::testMiningModeWrapAround);
        registerTest(event, env, "mining_mode_radius_per_shape", HammerGameTests::testMiningModeRadiusPerShape);
    }

    private static void testMiningModeInitialState(GameTestHelper helper) {
        MiningModeData data = MiningModeData.DEFAULT;

        if (data.shape() != MiningShape.SINGLE) {
            throw helper.assertionException("Default shape should be SINGLE, got: " + data.shape());
        }
        if (data.radius() != 0) {
            throw helper.assertionException("Default radius should be 0, got: " + data.radius());
        }
        if (data.maxBlocksToBreak() != 64) {
            throw helper.assertionException("Default maxBlocksToBreak should be 64, got: " + data.maxBlocksToBreak());
        }

        helper.succeed();
    }

    private static void testMiningModeCycling(GameTestHelper helper) {
        MiningModeData data = MiningModeData.DEFAULT;

        // Existing shapes keep their order; new patterns follow the original staircase modes.
        MiningShape[] expectedOrder = {
            MiningShape.FLAT_3X3,
            MiningShape.FLAT_5X5,
            MiningShape.FLAT_7X7,
            MiningShape.SHAPELESS,
            MiningShape.TUNNEL_UP,
            MiningShape.TUNNEL_DOWN,
            MiningShape.WALKABLE_TUNNEL,
            MiningShape.LARGE_TUNNEL,
            MiningShape.BORE
        };

        for (MiningShape expected : expectedOrder) {
            data = data.getNextMode();
            if (data.shape() != expected) {
                throw helper.assertionException("Expected " + expected + ", got: " + data.shape());
            }
        }

        helper.succeed();
    }

    private static void testMiningModeWrapAround(GameTestHelper helper) {
        // Start from the last configured shape.
        MiningModeData data = new MiningModeData(MiningShape.BORE, 0, 64, true);

        // Next mode should wrap to SINGLE
        data = data.getNextMode();

        if (data.shape() != MiningShape.SINGLE) {
            throw helper.assertionException("After BORE should wrap to SINGLE, got: " + data.shape());
        }

        helper.succeed();
    }

    private static void testMiningModeRadiusPerShape(GameTestHelper helper) {
        // Only flat and shapeless mining use a configurable radius.
        int[] expectedRadii = {0, 1, 2, 3, 1, 0, 0, 0, 0, 0};
        MiningShape[] shapes = MiningShape.values();

        for (int i = 0; i < shapes.length; i++) {
            int actualRadius = shapes[i].getRadius();
            if (actualRadius != expectedRadii[i]) {
                throw helper.assertionException(shapes[i] + " should have radius " + expectedRadii[i] + ", got: " + actualRadius);
            }
        }

        helper.succeed();
    }
}
