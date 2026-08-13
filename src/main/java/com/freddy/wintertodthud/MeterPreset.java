package com.freddy.wintertodthud;

public enum MeterPreset
{
    CUSTOM("Custom"),
    OSRS("OSRS"),
    MINIMAL("Minimal"),
    THIN_BARS("Thin bars"),
    VERTICAL("Vertical"),
    ORBS("Orbs"),
    RINGS("Rings"),
    COMPACT("Compact");

    private final String label;
    MeterPreset(String label){ this.label = label; }
    @Override public String toString(){ return label; }
}
