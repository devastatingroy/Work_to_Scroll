package com.roy.worktoscroll;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorkProgressTest {
    private static final int BOOT = 8;
    private WorkProgress start() {
        return new WorkProgress(0, 0, 0, 0, false, -1, -1, BOOT).begin(123, 1000, BOOT);
    }
    // Real observations arrive every 400 ms; exercise the production checkpoint path.
    private WorkProgress work(WorkProgress p, long millis) {
        p = p.observe(0, true, p.checkpointElapsedMs() + 1, BOOT);
        while (millis > 0) {
            long step = Math.min(400, millis);
            p = p.observe(step, true, p.checkpointElapsedMs() + step, BOOT);
            millis -= step;
        }
        return p;
    }
    @Test public void firstRewardAtExactlyTenMinutesThenEveryMinute() {
        WorkProgress p = work(start(), 599_999);
        assertEquals(0, p.sessionRewardMs());
        assertEquals(1, p.nextRewardInMs());
        p = work(p, 1);
        assertEquals(300_000, p.sessionRewardMs());
        assertEquals(60_000, p.nextRewardInMs());
        assertEquals(330_000, work(p, 60_000).sessionRewardMs());
        assertEquals(360_000, work(p, 120_000).sessionRewardMs());
        assertTrue(p.active());
    }
    @Test public void newSessionCannotUseHistoryOrPriorRemainder() {
        WorkProgress p = work(start(), 795_000).finish().begin(999, 800_000, BOOT);
        assertEquals(0, p.sessionValidMs());
        assertEquals(0, p.sessionRewardMs());
        assertEquals(600_000, p.nextRewardInMs());
        p = work(p, 60_000);
        assertEquals(0, p.sessionRewardMs());
        assertEquals(855_000, p.totalValidMs());
    }
    @Test public void unfinishedFirstGateDoesNotSpanExplicitEnd() {
        WorkProgress p = work(start(), 540_000).finish().begin(999, 600_000, BOOT);
        assertEquals(0, work(p, 60_000).sessionRewardMs());
        assertEquals(300_000, work(p, 600_000).sessionRewardMs());
    }
    @Test public void eightMinutesAndTwoMinutePauseEarnAfterTwoMoreMinutes() {
        WorkProgress p = work(start(), 480_000);
        long now = p.checkpointElapsedMs();
        p = p.pause(now, BOOT).observe(0, true, now + 120_000, BOOT);
        p = work(p, 120_000);
        assertEquals(600_000, p.totalValidMs());
        assertEquals(300_000, p.sessionRewardMs());
    }
    @Test public void pauseThresholdIsExactly180000Milliseconds() {
        WorkProgress p = work(start(), 480_000);
        long now = p.checkpointElapsedMs();
        p = p.pause(now, BOOT);
        assertEquals(1, p.resetRemainingMs(now + 179_999));
        assertEquals(480_000, p.refresh(now + 179_999, BOOT).sessionValidMs());
        WorkProgress reset = p.refresh(now + 180_000, BOOT);
        assertEquals(0, reset.sessionValidMs());
        assertEquals(480_000, reset.totalValidMs());
        assertEquals(0, reset.rewardSince(p));
        assertEquals(600_000, reset.nextRewardInMs());
        assertSame(reset, reset.refresh(now + 190_000, BOOT));
    }
    @Test public void returningBeforeDeadlinePreservesProgressWithoutCreditingPause() {
        WorkProgress p = work(start(), 599_999);
        long now = p.checkpointElapsedMs();
        p = p.pause(now, BOOT).observe(0, true, now + 179_999, BOOT);
        assertEquals(-1, p.resetRemainingMs(now + 180_001));
        assertEquals(300_000, work(p, 1).sessionRewardMs());
    }
    @Test public void returningAtDeadlineCannotEarnWithOneMoreMinute() {
        WorkProgress p = work(start(), 599_999);
        long now = p.checkpointElapsedMs();
        p = p.pause(now, BOOT).observe(0, true, now + 180_000, BOOT);
        assertEquals(0, work(p, 60_000).sessionRewardMs());
        assertEquals(300_000, work(p, 600_000).sessionRewardMs());
    }
    @Test public void qualifiedRoundResetPreservesEarnedRewardAndRepeatsGate() {
        WorkProgress p = work(start(), 750_000);
        long now = p.checkpointElapsedMs();
        WorkProgress reset = p.pause(now, BOOT).refresh(now + 180_000, BOOT);
        assertEquals(360_000, reset.sessionRewardMs());
        assertEquals(0, reset.rewardSince(p));
        assertEquals(750_000, reset.totalValidMs());
        assertEquals(0, reset.remainderMs());
        assertEquals(360_000, work(reset, 60_000).sessionRewardMs());
        WorkProgress nextGate = work(reset, 600_000);
        assertEquals(660_000, nextGate.sessionRewardMs());
        assertEquals(300_000, nextGate.rewardSince(reset));
        assertEquals(690_000, work(nextGate, 60_000).sessionRewardMs());
    }
    @Test public void repeatedPausesAndRestartsDoNotExtendDeadline() {
        WorkProgress p = work(start(), 500_000);
        long now = p.checkpointElapsedMs();
        p = p.pause(now, BOOT).pause(now + 60_000, BOOT).recover(now + 120_000, BOOT);
        assertEquals(now, p.interruptedAtElapsedMs());
        assertEquals(0, p.recover(now + 180_000, BOOT).sessionValidMs());
    }
    @Test public void processDeathUsesLastObservationAsInterruptionAnchor() {
        WorkProgress p = work(start(), 600_000);
        long now = p.checkpointElapsedMs();
        WorkProgress recovered = p.recover(now + 120_000, BOOT);
        assertEquals(now, recovered.interruptedAtElapsedMs());
        assertEquals(600_000, recovered.sessionValidMs());
        assertEquals(0, recovered.recover(now + 180_000, BOOT).sessionValidMs());
        assertEquals(300_000, recovered.recover(now + 180_000, BOOT).sessionRewardMs());
    }
    @Test public void rebootOrElapsedRollbackResetsOnlyCurrentRound() {
        WorkProgress p = work(start(), 660_000);
        for (WorkProgress reset : List.of(p.recover(100, BOOT + 1), p.recover(100, BOOT))) {
            assertEquals(0, reset.sessionValidMs());
            assertEquals(660_000, reset.totalValidMs());
            assertEquals(330_000, reset.sessionRewardMs());
            assertEquals(0, reset.rewardSince(p));
        }
    }
    @Test public void delayedTickCannotGiveFreshThreeMinutesAfterLongStall() {
        WorkProgress p = work(start(), 660_000);
        long now = p.checkpointElapsedMs();
        assertEquals(0, p.pause(now + 180_000, BOOT).sessionValidMs());
        assertEquals(0, p.observe(0, true, now + 180_000, BOOT).sessionValidMs());
    }
    @Test public void migratingLifetimeRulesNeverGrantsOrClawsBackRewards() {
        WorkProgress old = new WorkProgress(2_162_008, 2_162_008, 900_000, 123, true, -1, -1, BOOT);
        WorkProgress migrated = old.migrateRoundRules(1000, BOOT);
        assertEquals(0, migrated.sessionValidMs());
        assertEquals(2_162_008, migrated.totalValidMs());
        assertEquals(0, migrated.rewardSince(old));
        assertEquals(900_000, work(migrated, 60_000).sessionRewardMs());
        assertEquals(1_200_000, work(migrated, 600_000).sessionRewardMs());
    }
    @Test public void restoredCheckpointDoesNotReissueReward() {
        WorkProgress p = work(start(), 600_000);
        WorkProgress restored = p.recover(p.checkpointElapsedMs() + 100, BOOT);
        assertEquals(0, restored.rewardSince(p));
        assertEquals(0, work(restored, 59_999).rewardSince(p));
        assertEquals(30_000, work(restored, 60_000).rewardSince(p));
    }
    @Test public void immutableCandidateLeavesPreviousCheckpointIntact() {
        WorkProgress p = work(start(), 599_999);
        WorkProgress next = work(p, 1);
        assertEquals(0, p.sessionRewardMs());
        assertEquals(599_999, p.sessionValidMs());
        assertEquals(300_000, next.rewardSince(p));
    }
    @Test public void walletConsumptionSurvivesRoundReset() {
        WorkProgress p = work(start(), 720_000);
        BalanceEngine balance = new BalanceEngine(p.sessionRewardMs(), 0, 0, false);
        balance.settle(List.of(new BalanceEngine.Transition(1000, true),
            new BalanceEngine.Transition(121000, false)), 200000);
        assertEquals(240000, balance.balanceMs);
        WorkProgress reset = p.pause(p.checkpointElapsedMs(), BOOT)
            .refresh(p.checkpointElapsedMs() + 180_000, BOOT);
        balance.balanceMs = Math.addExact(balance.balanceMs, reset.rewardSince(p));
        assertEquals(240000, balance.balanceMs);
        assertEquals(120000, balance.consumedMs);
    }
    @Test(expected = IllegalArgumentException.class) public void endedSessionCannotEarn() {
        start().finish().observe(1000, true, 2000, BOOT);
    }
    @Test public void subMinuteCarryIsKeptOnlyWithinSameRound() {
        WorkProgress p = work(start(), 659_999);
        assertEquals(59_999, p.remainderMs());
        assertEquals(1, p.nextRewardInMs());
        assertEquals(30_000, work(p, 1).rewardSince(p));
        assertEquals(0, p.finish().begin(456, 700_000, BOOT).remainderMs());
    }
    @Test public void largeValidTimeDoesNotOverflowEntitlement() {
        assertEquals((Long.MAX_VALUE / 60_000) * 30_000, WorkProgress.earnedFor(Long.MAX_VALUE));
    }
    @Test public void freshnessDeadlineSurvivesDelayedTick() {
        WorkClock clock = new WorkClock();
        clock.observe(1000, 1); clock.observe(2000, 1); clock.observe(2400, 1);
        assertEquals(3900, clock.expire(200_000));
        assertEquals(-1, clock.expire(201_000));
        assertEquals(WorkClock.State.PAUSED, clock.state());
    }
    @Test public void staleFramePauseAndDeadlineComposeWithoutExtraGrace() {
        WorkProgress p = new WorkProgress(600_000, 600_000, 300_000, 123, true, -1, 2400, BOOT);
        WorkClock clock = new WorkClock();
        clock.observe(1000, 1); clock.observe(2000, 1); clock.observe(2400, 1);
        long stale = clock.expire(183_900);
        WorkProgress reset = p.pause(stale, BOOT).refresh(183_900, BOOT);
        assertEquals(3900, reset.interruptedAtElapsedMs());
        assertEquals(0, reset.sessionValidMs());
        assertEquals(0, reset.rewardSince(p));
    }
    @Test public void awayCheckingDoesNotCancelDeadlineUntilPresenceConfirmed() {
        WorkProgress p = work(start(), 540_000);
        long pausedAt = p.checkpointElapsedMs();
        p = p.pause(pausedAt, BOOT);
        WorkClock clock = new WorkClock();
        clock.observe(pausedAt + 179_200, 1);
        assertEquals(WorkClock.State.CHECKING, clock.state());
        p = p.observe(0, false, pausedAt + 179_200, BOOT);
        clock.observe(pausedAt + 180_200, 1);
        assertEquals(WorkClock.State.PRESENT, clock.state());
        p = p.observe(0, true, pausedAt + 180_200, BOOT);
        assertEquals(0, p.sessionValidMs());
        assertEquals(0, work(p, 60_000).sessionRewardMs());
    }
    @Test public void repeatedRoundsAwardOnlyNewNodes() {
        WorkProgress p = start();
        for (int i = 1; i <= 3; i++) {
            p = work(p, 660_000);
            assertEquals(330_000L * i, p.sessionRewardMs());
            long pausedAt = p.checkpointElapsedMs();
            p = p.pause(pausedAt, BOOT).refresh(pausedAt + 180_000, BOOT);
            assertEquals(660_000L * i, p.totalValidMs());
            assertEquals(0, p.sessionValidMs());
        }
    }

}
