package com.roy.worktoscroll;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorkProgressTest {
    private WorkProgress start() { return new WorkProgress(0, 0, 0, 0, false).begin(123); }

    @Test public void tenMinuteNodeRewardsWithoutEndingSession() {
        WorkProgress p = start().advance(599_999);
        assertEquals(0, p.sessionRewardMs());
        p = p.advance(1);
        assertEquals(300_000, p.sessionRewardMs());
        assertTrue(p.active());
        assertEquals(0, p.remainderMs());
        assertEquals(600_000, p.advance(600_000).sessionRewardMs());
    }
    @Test public void remainderSurvivesEndAndNewSession() {
        WorkProgress p = start().advance(795_000).finish().begin(999);
        assertEquals(15_000, p.remainderMs());
        assertEquals(0, p.sessionValidMs());
        assertEquals(0, p.sessionRewardMs());
        p = p.advance(405_000);
        assertEquals(210_000, p.sessionRewardMs());
        assertEquals(1_200_000, p.totalValidMs());
    }
    @Test public void restoredCheckpointDoesNotReissuePriorReward() {
        WorkProgress saved = start().advance(600_000);
        WorkProgress restored = new WorkProgress(saved.totalValidMs(), saved.sessionValidMs(),
            saved.sessionRewardMs(), saved.startedWallMs(), saved.active());
        assertEquals(0, restored.advance(0).sessionRewardMs() - restored.sessionRewardMs());
        assertEquals(0, restored.advance(59_999).sessionRewardMs() - restored.sessionRewardMs());
        assertEquals(30_000, restored.advance(60_000).sessionRewardMs() - restored.sessionRewardMs());
        assertSame(restored, restored.begin(Long.MAX_VALUE));
    }
    @Test public void immutableCandidateLeavesPriorCheckpointIntact() {
        WorkProgress before = start().advance(599_999);
        WorkProgress candidate = before.advance(1);
        assertEquals(0, before.sessionRewardMs());
        assertEquals(599_999, before.totalValidMs());
        assertEquals(300_000, candidate.sessionRewardMs());
    }
    @Test public void earnedRewardCanBeConsumedAcrossTwoIntervals() {
        WorkProgress work = start().advance(600_000);
        BalanceEngine balance = new BalanceEngine(work.sessionRewardMs(), 0, 0, false);
        balance.settle(List.of(new BalanceEngine.Transition(1000, true),
            new BalanceEngine.Transition(121000, false)), 200000);
        assertEquals(180000, balance.balanceMs);
        balance.settle(List.of(new BalanceEngine.Transition(201000, true),
            new BalanceEngine.Transition(381000, false)), 400000);
        assertEquals(0, balance.balanceMs);
        assertEquals(300000, balance.consumedMs);
    }
    @Test(expected = IllegalArgumentException.class) public void endedSessionCannotEarn() {
        start().finish().advance(1000);
    }
    @Test public void minuteRewardsMatchApprovedExamples() {
        assertEquals(330_000, start().advance(660_000).sessionRewardMs());
        assertEquals(360_000, start().advance(720_000).sessionRewardMs());
        assertEquals(690_000, start().advance(1_380_000).sessionRewardMs());
    }
    @Test public void firstGateSpansSessionsButDoesNotRepeat() {
        WorkProgress p = start().advance(540_000).finish().begin(999);
        assertEquals(0, p.sessionRewardMs());
        assertEquals(60_000, p.nextRewardInMs());
        p = p.advance(60_000);
        assertEquals(300_000, p.sessionRewardMs());
        p = p.finish().begin(1000).advance(60_000);
        assertEquals(30_000, p.sessionRewardMs());
    }
    @Test public void subMinuteRemainderAndCountdownAreExact() {
        WorkProgress p = start().advance(599_999);
        assertEquals(1, p.nextRewardInMs());
        p = p.advance(1);
        assertEquals(60_000, p.nextRewardInMs());
        p = p.advance(59_999).finish().begin(456);
        assertEquals(59_999, p.remainderMs());
        assertEquals(1, p.nextRewardInMs());
        assertEquals(30_000, p.advance(1).sessionRewardMs());
    }
    @Test public void segmentedAccountingEqualsContinuousAccounting() {
        WorkProgress p = start();
        for (int i = 0; i < 10_000; i++) p = p.advance(401);
        assertEquals(start().advance(4_010_000).sessionRewardMs(), p.sessionRewardMs());
        assertEquals(4_010_000, p.totalValidMs());
    }
    @Test public void legacyTopupOnlyAddsDifference() {
        assertEquals(0, WorkProgress.legacyUpgradeCreditFor(599_999));
        assertEquals(0, WorkProgress.legacyUpgradeCreditFor(600_000));
        assertEquals(0, WorkProgress.legacyUpgradeCreditFor(659_999));
        assertEquals(30_000, WorkProgress.legacyUpgradeCreditFor(660_000));
        assertEquals(270_000, WorkProgress.legacyUpgradeCreditFor(1_199_999));
        assertEquals(0, WorkProgress.legacyUpgradeCreditFor(1_200_000));
        assertEquals(180_000, WorkProgress.legacyUpgradeCreditFor(2_162_008));
    }
    @Test public void legacySessionRewardIsPreservedAndOnlyNewMinutesEarn() {
        WorkProgress old = new WorkProgress(2_162_008, 2_162_008, 900_000, 123, true);
        assertEquals(900_000, old.advance(0).sessionRewardMs());
        assertEquals(930_000, old.advance(57_992).sessionRewardMs());
        assertEquals(180_000, WorkProgress.legacyUpgradeCreditFor(old.totalValidMs()));
    }
    @Test public void largeValidTimeDoesNotOverflowEntitlement() {
        assertEquals((Long.MAX_VALUE / 60_000) * 30_000, WorkProgress.earnedFor(Long.MAX_VALUE));
    }
}
