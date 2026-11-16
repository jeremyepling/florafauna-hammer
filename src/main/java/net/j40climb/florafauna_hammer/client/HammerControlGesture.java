package net.j40climb.florafauna_hammer.client;

/** Defers the first tap until a second press can no longer claim it. Times are monotonic milliseconds. */
final class HammerControlGesture {
    static final int DOUBLE_TAP_MS = 250;
    static final int HOLD_MS = 300;
    enum Action { NONE, CYCLE, SINGLE, MENU }

    private long pressedAt;
    private long tapDeadline;
    private boolean pressed;
    private boolean pendingTap;
    private boolean secondTap;
    private boolean opened;

    Action press(long now) {
        if (pressed) return Action.NONE;
        boolean expired = pendingTap && now > tapDeadline;
        secondTap = pendingTap && !expired;
        pendingTap = false;
        pressed = true;
        opened = false;
        pressedAt = now;
        return expired ? Action.CYCLE : Action.NONE;
    }

    Action advance(long now) {
        if (pressed && !opened && now - pressedAt >= HOLD_MS) {
            opened = true;
            return Action.MENU;
        }
        if (pendingTap && now > tapDeadline) {
            pendingTap = false;
            return Action.CYCLE;
        }
        return Action.NONE;
    }

    Action release(long now) {
        if (!pressed) return Action.NONE;
        var held = advance(now);
        pressed = false;
        if (opened) return held;
        if (secondTap) return Action.SINGLE;
        pendingTap = true;
        tapDeadline = now + DOUBLE_TAP_MS;
        return Action.NONE;
    }

    void reset() {
        pressed = false;
        pendingTap = false;
    }
}
