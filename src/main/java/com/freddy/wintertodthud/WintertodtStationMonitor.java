/*
 * Wintertodt station widget/status constants are adapted from Wintertodt Solo Helper.
 * Copyright (c) 2023, AprilHT
 * BSD 2-Clause licensed; see THIRD_PARTY_NOTICES.md.
 *
 * The current component mapping is verified against the live-cache `wint_status` interface:
 * pyromancers 7-10, braziers 11-14, Warmth layer 15, Energy layer 21.
 * Alive pyromancers render with a null sprite (-1 through RuneLite); incapacitated
 * pyromancers use 1400. Brazier 1397/1398/1399 = broken/unlit/lit.
 */
package com.freddy.wintertodthud;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/**
 * Reads the four Wintertodt station indicators from the native HUD.
 *
 * The native interface gives us a compact, exact four-way state snapshot. We
 * deliberately validate all eight widgets before allowing the Energy clock to
 * trust it. Unknown values suppress numerical advice rather than becoming an
 * assumption.
 */
@Singleton
public class WintertodtStationMonitor
{
    public enum Quadrant
    {
        SOUTH_WEST,
        NORTH_WEST,
        NORTH_EAST,
        SOUTH_EAST
    }

    public enum BrazierState
    {
        UNKNOWN,
        UNLIT,
        LIT,
        BROKEN
    }

    private static final int[] PYRO_WIDGETS = {7, 8, 9, 10};
    private static final int[] BRAZIER_WIDGETS = {11, 12, 13, 14};

    private static final int SPRITE_BROKEN_BRAZIER = 1397;
    private static final int SPRITE_UNLIT_BRAZIER = 1398;
    private static final int SPRITE_LIT_BRAZIER = 1399;
    private static final int SPRITE_DEAD_PYROMANCER = 1400;
    private static final int SPRITE_ALIVE_PYROMANCER = -1; // Jagex script sets sprite null.

    private final Client client;
    private final BrazierState[] brazierStates = new BrazierState[4];
    private final boolean[] pyromancerAlive = new boolean[4];
    private final int[] rawBrazierSprites = new int[4];
    private final int[] rawPyroSprites = new int[4];
    private final int[] stationRevisions = new int[4];

    private boolean reliable;
    private int activeDrainers = -1;
    private int epoch;
    private String diagnostic = "not scanned";

    @Inject
    WintertodtStationMonitor(Client client)
    {
        this.client = client;
        reset();
    }

    public void reset()
    {
        for (int i = 0; i < 4; i++)
        {
            brazierStates[i] = BrazierState.UNKNOWN;
            pyromancerAlive[i] = false;
            rawBrazierSprites[i] = Integer.MIN_VALUE;
            rawPyroSprites[i] = Integer.MIN_VALUE;
            stationRevisions[i]++;
        }
        reliable = false;
        activeDrainers = -1;
        diagnostic = "calibrating";
        epoch++;
    }

    public void tick(boolean inWintertodt)
    {
        if (!inWintertodt)
        {
            if (reliable || activeDrainers != -1)
            {
                reset();
            }
            return;
        }

        BrazierState[] nextBrazier = new BrazierState[4];
        boolean[] nextAlive = new boolean[4];
        boolean valid = true;

        for (int i = 0; i < 4; i++)
        {
            Widget brazier = client.getWidget(WintertodtState.WINTERTODT_GROUP, BRAZIER_WIDGETS[i]);
            Widget pyro = client.getWidget(WintertodtState.WINTERTODT_GROUP, PYRO_WIDGETS[i]);

            int brazierSprite = brazier == null ? Integer.MIN_VALUE : brazier.getSpriteId();
            int pyroSprite = pyro == null ? Integer.MIN_VALUE : pyro.getSpriteId();
            rawBrazierSprites[i] = brazierSprite;
            rawPyroSprites[i] = pyroSprite;

            nextBrazier[i] = decodeBrazier(brazierSprite);
            Boolean alive = decodePyromancer(pyroSprite);
            nextAlive[i] = Boolean.TRUE.equals(alive);

            if (nextBrazier[i] == BrazierState.UNKNOWN || alive == null)
            {
                valid = false;
            }
        }

        int nextActive = -1;
        if (valid)
        {
            nextActive = 0;
            for (int i = 0; i < 4; i++)
            {
                if (nextBrazier[i] == BrazierState.LIT && nextAlive[i])
                {
                    nextActive++;
                }
            }
        }

        boolean changed = reliable != valid || activeDrainers != nextActive;
        for (int i = 0; i < 4; i++)
        {
            if (brazierStates[i] != nextBrazier[i] || pyromancerAlive[i] != nextAlive[i])
            {
                changed = true;
                stationRevisions[i]++;
            }
            brazierStates[i] = nextBrazier[i];
            pyromancerAlive[i] = nextAlive[i];
        }

        reliable = valid;
        activeDrainers = nextActive;
        diagnostic = valid ? stationSummary() : "unverified · " + rawSummary();
        if (changed)
        {
            epoch++;
        }
    }

    static BrazierState decodeBrazier(int sprite)
    {
        switch (sprite)
        {
            case SPRITE_UNLIT_BRAZIER:
                return BrazierState.UNLIT;
            case SPRITE_LIT_BRAZIER:
                return BrazierState.LIT;
            case SPRITE_BROKEN_BRAZIER:
                return BrazierState.BROKEN;
            default:
                return BrazierState.UNKNOWN;
        }
    }

    /**
     * @return TRUE when alive, FALSE when incapacitated, null when unknown.
     */
    static Boolean decodePyromancer(int sprite)
    {
        if (sprite == SPRITE_ALIVE_PYROMANCER)
        {
            return Boolean.TRUE;
        }
        if (sprite == SPRITE_DEAD_PYROMANCER)
        {
            return Boolean.FALSE;
        }
        return null;
    }

    public boolean reliable()
    {
        return reliable;
    }

    /** Exact count only when all four paired station widgets validated. */
    public int activeDrainers()
    {
        return reliable ? activeDrainers : -1;
    }

    public int epoch()
    {
        return epoch;
    }

    /** Whether this exact station is currently a validated Energy drainer. */
    public boolean contributing(Quadrant quadrant)
    {
        int i = quadrant.ordinal();
        return reliable && brazierStates[i] == BrazierState.LIT && pyromancerAlive[i];
    }

    /**
     * Per-station revision counter. The Energy clock uses this to invalidate only
     * the phase belonging to the physical station that actually changed.
     */
    public int stationRevision(Quadrant quadrant)
    {
        return stationRevisions[quadrant.ordinal()];
    }

    public BrazierState brazierState(Quadrant quadrant)
    {
        if (!reliable || quadrant == null)
        {
            return BrazierState.UNKNOWN;
        }
        return brazierStates[quadrant.ordinal()];
    }


    public boolean pyromancerAlive(Quadrant quadrant)
    {
        return reliable && pyromancerAlive[quadrant.ordinal()];
    }

    public String diagnostic()
    {
        return diagnostic;
    }

    public String stationSummary()
    {
        if (!reliable)
        {
            return "stations unknown";
        }
        return "SW " + shortState(0) + " · NW " + shortState(1)
            + " · NE " + shortState(2) + " · SE " + shortState(3);
    }

    /** Raw values are intentionally compact so a user can paste one audit line. */
    public String rawSummary()
    {
        return "SW " + rawState(0) + " · NW " + rawState(1)
            + " · NE " + rawState(2) + " · SE " + rawState(3);
    }

    private String rawState(int index)
    {
        return printable(rawBrazierSprites[index]) + "/" + printable(rawPyroSprites[index]);
    }

    private static String printable(int value)
    {
        return value == Integer.MIN_VALUE ? "null" : Integer.toString(value);
    }

    private String shortState(int index)
    {
        String fire;
        switch (brazierStates[index])
        {
            case LIT:
                fire = "L";
                break;
            case BROKEN:
                fire = "B";
                break;
            case UNLIT:
                fire = "U";
                break;
            default:
                fire = "?";
                break;
        }
        return fire + (pyromancerAlive[index] ? "+" : "-");
    }
}
