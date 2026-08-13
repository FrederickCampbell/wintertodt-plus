package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundRestartMathTest
{
    @Test public void fullMinute(){ assertEquals(60, RoundRestartMath.secondsLeft(100)); assertEquals(0, RoundRestartMath.fillPercent(100)); assertEquals("NEXT TODT — 1:00", RoundRestartMath.label(100)); }
    @Test public void halfway(){ assertEquals(30, RoundRestartMath.secondsLeft(50)); assertEquals(50, RoundRestartMath.fillPercent(50)); assertEquals("NEXT TODT — 0:30", RoundRestartMath.label(50)); }
    @Test public void ready(){ assertEquals(0, RoundRestartMath.secondsLeft(0)); assertEquals(100, RoundRestartMath.fillPercent(0)); assertEquals("NEXT TODT — 0:00", RoundRestartMath.label(0)); }
}
