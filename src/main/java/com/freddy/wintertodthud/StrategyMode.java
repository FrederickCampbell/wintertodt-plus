package com.freddy.wintertodthud;

public enum StrategyMode
{
    MAX_FM_XP("Max FM XP"),
    MAX_POINTS("Max Points");

    private final String label;

    StrategyMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
