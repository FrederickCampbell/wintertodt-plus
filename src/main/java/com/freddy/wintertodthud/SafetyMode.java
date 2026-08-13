package com.freddy.wintertodthud;

public enum SafetyMode
{
    FAST("Fast", 1),
    NORMAL("Normal", 4),
    SAFE("Safe", 8);

    private final String label;
    private final int baseBufferTicks;

    SafetyMode(String label, int baseBufferTicks)
    {
        this.label = label;
        this.baseBufferTicks = baseBufferTicks;
    }

    public int baseBufferTicks()
    {
        return baseBufferTicks;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
