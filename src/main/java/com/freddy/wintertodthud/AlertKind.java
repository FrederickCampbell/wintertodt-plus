package com.freddy.wintertodthud;

public enum AlertKind
{
    IDLE("IDLE", MeterTarget.BOTH),
    LOW_WARMTH("LOW WARMTH", MeterTarget.WARMTH),
    CRITICAL_WARMTH("EAT NOW", MeterTarget.WARMTH),
    LOW_ENERGY("ROUND ENDING", MeterTarget.ENERGY),
    GO_NOW("GO NOW", MeterTarget.BOTH),
    POINTS_NOT_SAFE("500 AT RISK — BURN NOW", MeterTarget.BOTH),
    INVENTORY_FULL("INVENTORY FULL", MeterTarget.BOTH),
    OUT_OF_ROOTS("OUT OF ROOTS", MeterTarget.BOTH),
    BRAZIER_OUT("BRAZIER OUT", MeterTarget.BOTH),
    ROUND_STARTING("ROUND STARTING", MeterTarget.BOTH),
    INTERRUPTED("ACTION STOPPED", MeterTarget.BOTH);

    private final String text;
    private final MeterTarget target;

    AlertKind(String text, MeterTarget target)
    {
        this.text = text;
        this.target = target;
    }

    public String text(){ return text; }
    public MeterTarget target(){ return target; }

    public enum MeterTarget
    {
        WARMTH,
        ENERGY,
        BOTH
    }
}
