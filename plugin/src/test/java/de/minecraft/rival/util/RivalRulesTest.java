package de.minecraft.rival.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RivalRulesTest {
    @Test void formatsDailyTimeWithoutNegativeValues() {
        assertEquals("01:01:01", RivalRules.formatDuration(3661));
        assertEquals("00:00:00", RivalRules.formatDuration(-1));
    }

    @Test void warningTriggersExactlyOnceWhenThresholdIsCrossed() {
        assertTrue(RivalRules.warningCrossed(301, 300, 300));
        assertFalse(RivalRules.warningCrossed(300, 299, 300));
        assertFalse(RivalRules.warningCrossed(299, 298, 300));
    }

    @Test void splitCoordinateBelongsToNeitherSide() {
        assertTrue(RivalRules.allowedSide(-1, -0.01, 0));
        assertTrue(RivalRules.allowedSide(1, 0.01, 0));
        assertFalse(RivalRules.allowedSide(-1, 0, 0));
        assertFalse(RivalRules.allowedSide(1, 0, 0));
        assertFalse(RivalRules.allowedSide(0, 10, 0));
    }

    @Test void islandZoneUsesOnlyXZAndIncludesItsEdges() {
        assertTrue(RivalRules.insideVerticalZone(5, 5, 0, 0, 10, 10));
        assertTrue(RivalRules.insideVerticalZone(0, 10, 0, 0, 10, 10));
        assertFalse(RivalRules.insideVerticalZone(11, 5, 0, 0, 10, 10));
        // Y ist absichtlich kein Parameter: dieselbe X/Z-Fläche gilt auf jeder Höhe.
    }

    @Test void detectsOverlappingSpecialIslands() {
        assertTrue(RivalRules.verticalZonesOverlap(0, 0, 10, 10, 10, 5, 20, 15));
        assertFalse(RivalRules.verticalZonesOverlap(0, 0, 9, 9, 10, 10, 20, 20));
    }

    @Test void parsesTemporaryAndPermanentBanDurations() {
        assertEquals(0, RivalRules.parseBanDurationMillis("permanent"));
        assertEquals(30 * 60_000L, RivalRules.parseBanDurationMillis("30m"));
        assertEquals(9 * 86_400_000L, RivalRules.parseBanDurationMillis("1w2d"));
        assertEquals(90 * 60_000L, RivalRules.parseBanDurationMillis("1h30m"));
    }

    @Test void rejectsInvalidBanDurations() {
        assertThrows(IllegalArgumentException.class, () -> RivalRules.parseBanDurationMillis("0d"));
        assertThrows(IllegalArgumentException.class, () -> RivalRules.parseBanDurationMillis("5days"));
        assertThrows(IllegalArgumentException.class, () -> RivalRules.parseBanDurationMillis("1h-30m"));
    }

    @Test void endFightRequiresExactlyTwoRemainingAndOnlinePlayers() {
        assertTrue(RivalRules.canStartEndFight(2, 2));
        assertFalse(RivalRules.canStartEndFight(3, 3));
        assertFalse(RivalRules.canStartEndFight(2, 1));
        assertFalse(RivalRules.canStartEndFight(1, 1));
    }
}
