package com.roy.worktoscroll;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static com.roy.worktoscroll.BalanceEngine.Transition;

public class BalanceEngineTest {
    @Test public void twoMinutesThenThreeMinutesExhaustFiveMinutes() {
        var e = new BalanceEngine(300_000, 0, 0, false);
        e.settle(List.of(new Transition(1_000, true), new Transition(121_000, false)), 181_000);
        assertEquals(180_000, e.balanceMs);
        e.settle(List.of(new Transition(181_000, true), new Transition(361_000, false)), 400_000);
        assertEquals(0, e.balanceMs);
        assertEquals(300_000, e.consumedMs);
    }
    @Test public void lockAndOtherAppsDoNotConsume() {
        var e = new BalanceEngine(300_000, 0, 0, false);
        e.settle(List.of(new Transition(10_000, true), new Transition(20_000, false)), 1_000_000);
        assertEquals(290_000, e.balanceMs);
    }
    @Test public void replayCannotDoubleCharge() {
        var e = new BalanceEngine(300_000, 0, 0, false);
        var events = List.of(new Transition(1_000, true), new Transition(10_000, false));
        e.settle(events, 20_000);
        e.settle(events, 20_000);
        assertEquals(291_000, e.balanceMs);
    }
    @Test public void restartRestoresAndSettlesOpenInterval() {
        var e = new BalanceEngine(300_000, 0, 0, false);
        e.settle(List.of(new Transition(1_000, true)), 6_000);
        var restored = new BalanceEngine(e.balanceMs, e.consumedMs, e.cursorMs, e.targetActive);
        restored.settle(List.of(new Transition(11_000, false)), 30_000);
        assertEquals(290_000, restored.balanceMs);
    }
    @Test public void intervalEndEventIsAppliedInNextBatch() {
        var e = new BalanceEngine(300_000, 0, 0, false);
        e.settle(List.of(new Transition(10_000, true)), 10_000);
        e.settle(List.of(new Transition(10_000, true)), 11_000);
        assertEquals(299_000, e.balanceMs);
    }
    @Test public void exhaustedBalanceCannotGoNegative() {
        var e = new BalanceEngine(100, 0, 0, true);
        e.settle(List.of(), 10_000);
        assertEquals(0, e.balanceMs);
        assertEquals(100, e.consumedMs);
    }
    @Test public void unknownIntervalPreservesBalance() {
        var e = new BalanceEngine(250_000, 50_000, 10_000, true);
        e.resetObservation(100_000);
        e.settle(List.of(), 200_000);
        assertEquals(250_000, e.balanceMs);
    }
    @Test(expected = IllegalArgumentException.class) public void backwardsClockRejected() {
        new BalanceEngine(300_000, 0, 10_000, true).settle(List.of(), 9_000);
    }
    @Test public void creditStartsAtCurrentCheckpoint() {
        var e = new BalanceEngine(0, 0, 0, true);
        e.settle(List.of(new Transition(5_000, false)), 10_000);
        e.grant(300_000);
        e.settle(List.of(), 20_000);
        assertEquals(300_000, e.balanceMs);
    }
}
