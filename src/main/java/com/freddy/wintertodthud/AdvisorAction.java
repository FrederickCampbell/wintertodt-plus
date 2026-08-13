package com.freddy.wintertodthud;

/**
 * Primary coach commands. WAITING is calibration-only; an active calibrated
 * round intentionally uses only the six commands below it.
 */
public enum AdvisorAction
{
    WAITING("WAITING", Level.NORMAL),
    KEEP_CUTTING("KEEP CUTTING", Level.GOOD),
    FLETCH("FLETCH", Level.WARN),
    RUN_TO_BRAZIER("RUN TO BRAZIER", Level.WARN),
    BURN_NOW("BURN NOW", Level.WARN),
    ROUND_ENDING("ROUND ENDING", Level.DANGER),
    MAX_POINTS_BURN_NOW("500 AT RISK — BURN NOW", Level.DANGER);

    public enum Level
    {
        NORMAL,
        GOOD,
        WARN,
        DANGER
    }

    private final String label;
    private final Level level;

    AdvisorAction(String label, Level level)
    {
        this.label = label;
        this.level = level;
    }

    public String label()
    {
        return label;
    }

    public Level level()
    {
        return level;
    }
}
