package net.j40climb.florafauna_hammer.test;

import net.j40climb.florafauna_hammer.HammerMod;
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
}
