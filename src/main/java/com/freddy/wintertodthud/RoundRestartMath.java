package com.freddy.wintertodthud;

/** Conversion helpers for RuneScape's Wintertodt respawn-delay varbit. */
public final class RoundRestartMath
{
    private static final int FULL_DELAY_UNITS = 100;

    private RoundRestartMath() {}

    public static int secondsLeft(int respawnDelay)
    {
        return Math.max(0, respawnDelay) * 30 / 50;
    }

    public static int fillPercent(int respawnDelay)
    {
        return Math.max(0, Math.min(100, FULL_DELAY_UNITS - Math.max(0, respawnDelay)));
    }

    public static String label(int respawnDelay)
    {
        int seconds = secondsLeft(respawnDelay);
        return String.format("NEXT TODT — %d:%02d", seconds / 60, seconds % 60);
    }
}
