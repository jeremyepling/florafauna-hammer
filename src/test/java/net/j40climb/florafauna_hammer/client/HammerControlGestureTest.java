package net.j40climb.florafauna_hammer.client;

import org.junit.jupiter.api.Test;

import static net.j40climb.florafauna_hammer.client.HammerControlGesture.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HammerControlGestureTest {
    private final HammerControlGesture gesture = new HammerControlGesture();

    @Test
    void shortTapCyclesExactlyOnceAfterDoubleTapWindow() {
        assertEquals(NONE, gesture.press(0));
        assertEquals(NONE, gesture.release(30));
        assertEquals(NONE, gesture.advance(280));
        assertEquals(CYCLE, gesture.advance(281));
        assertEquals(NONE, gesture.advance(600));
    }

    @Test
    void secondPressInsideWindowClaimsFirstTap() {
        gesture.press(0);
        gesture.release(30);
        assertEquals(NONE, gesture.press(280));
        assertEquals(NONE, gesture.advance(350));
        assertEquals(SINGLE, gesture.release(360));
        assertEquals(NONE, gesture.advance(900));
    }

    @Test
    void secondPressOutsideWindowStartsIndependentGesture() {
        gesture.press(0);
        gesture.release(10);
        assertEquals(CYCLE, gesture.press(261));
        assertEquals(NONE, gesture.release(270));
        assertEquals(CYCLE, gesture.advance(521));
    }

    @Test
    void holdingOpensOnceAndReleaseDoesNotCycle() {
        gesture.press(0);
        assertEquals(NONE, gesture.advance(299));
        assertEquals(MENU, gesture.advance(300));
        assertEquals(NONE, gesture.press(380));
        assertEquals(NONE, gesture.release(470));
        assertEquals(NONE, gesture.advance(920));
    }

    @Test
    void holdingSecondPressOpensMenuEvenWhenReleaseIsFirstTickPastThreshold() {
        gesture.press(0);
        gesture.release(20);
        gesture.press(110);
        assertEquals(MENU, gesture.release(422));
        assertEquals(NONE, gesture.advance(900));
    }

    @Test
    void changingContextDropsPendingTaps() {
        gesture.press(0);
        gesture.release(40);
        gesture.reset();
        assertEquals(NONE, gesture.advance(350));
    }
}
