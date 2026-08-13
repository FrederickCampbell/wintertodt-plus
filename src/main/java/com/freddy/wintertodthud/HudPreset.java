package com.freddy.wintertodthud;

public enum HudPreset
{
    SIMPLE("Simple"),
    RECOMMENDED("Recommended"),
    MINIMAL("Minimal"),
    ADVANCED("Advanced"),
    CUSTOM("Custom");

    private final String label;

    HudPreset(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
