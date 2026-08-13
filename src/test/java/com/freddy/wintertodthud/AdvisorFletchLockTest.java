package com.freddy.wintertodthud;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdvisorFletchLockTest
{
    @Test
    public void releasesOnlyUnstartedCalibratedRawSafeLock()
    {
        assertTrue(AdvisorEngine.shouldReleaseUnstartedFletchLock(true, true, 0, 0));
        assertTrue(AdvisorEngine.shouldReleaseUnstartedFletchLock(true, true, 14, 14));
        assertFalse(AdvisorEngine.shouldReleaseUnstartedFletchLock(false, true, 0, 0));
        assertFalse(AdvisorEngine.shouldReleaseUnstartedFletchLock(true, false, 0, 0));
        assertFalse(AdvisorEngine.shouldReleaseUnstartedFletchLock(true, true, 1, 0));
    }
}
