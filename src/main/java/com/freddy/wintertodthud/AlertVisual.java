package com.freddy.wintertodthud;

/**
 * Where an alert is shown. BANNER is intentionally non-flashing and is the
 * default for one-shot informational alerts such as 500 AT RISK and GO NOW.
 */
public enum AlertVisual
{
    NONE(false, false, false, false),
    BANNER(false, true, false, false),
    SCREEN(true, false, false, false),
    RELEVANT_METER(false, false, true, false),
    BOTH_METERS(false, false, false, true),
    SCREEN_AND_RELEVANT(true, false, true, false),
    SCREEN_AND_BOTH(true, false, false, true),
    BANNER_AND_RELEVANT(false, true, true, false),
    BANNER_AND_BOTH(false, true, false, true);

    private final boolean screen;
    private final boolean banner;
    private final boolean relevant;
    private final boolean both;

    AlertVisual(boolean screen, boolean banner, boolean relevant, boolean both)
    {
        this.screen = screen;
        this.banner = banner;
        this.relevant = relevant;
        this.both = both;
    }

    public boolean screen(){ return screen; }
    public boolean banner(){ return banner; }
    public boolean relevantMeter(){ return relevant; }
    public boolean bothMeters(){ return both; }
}
