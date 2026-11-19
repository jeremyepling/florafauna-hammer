package net.j40climb.florafauna_hammer.common.item.abilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundSlamTest {
    @Test
    void forceFallsOffToZeroAtTheRadius() {
        assertEquals(1, GroundSlam.strength(0));
        assertEquals(0.5, GroundSlam.strength(GroundSlam.RADIUS / 2));
        assertEquals(0, GroundSlam.strength(GroundSlam.RADIUS));
        assertEquals(0, GroundSlam.strength(12));
    }

    @Test
    void cushionAbsorbsJumpHeightButPreservesLongFalls() {
        assertEquals(0, GroundSlam.cushionedFall(1.25));
        assertEquals(38, GroundSlam.cushionedFall(40));
    }
}
