package com.freddy.wintertodthud;

public enum MeterShape
{
    RECTANGLE("Rectangle"),
    ROUNDED_RECTANGLE("Rounded bar"),
    CIRCLE_ORB("Orb"),
    RING("Ring"),
    TRIANGLE("Triangle");

    private final String label;
    MeterShape(String label){ this.label = label; }
    @Override public String toString(){ return label; }
}
