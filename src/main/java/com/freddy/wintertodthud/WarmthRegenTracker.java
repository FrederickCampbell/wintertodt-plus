package com.freddy.wintertodthud;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.InventoryID;

/**
 * Tracks the natural Warmth regeneration pulse for the HUD ring.
 *
 * Wintertodt Warmth uses the normal Hitpoints-style regeneration cadence:
 * 100 game ticks (60 seconds), with Rapid Heal reducing the cadence to 50.
 * Warm clothing changes the amount restored (8% + 1% per warm item, max 4),
 * not the base cadence. The equipment-name scan is deliberately conservative;
 * a clean observed +8..+12 natural pulse self-corrects the clothing count.
 */
@Singleton
public class WarmthRegenTracker
{
    static final int NORMAL_REGEN_TICKS = 100;
    static final int RAPID_HEAL_REGEN_TICKS = 50;

    private final Client client;
    private final WintertodtState state;

    private int ticksSinceRegen = -2;
    private int lastWarmth = -1;
    private int observedWarmPieces = -1;
    private int recognizedWarmPieces = -1;
    private boolean rapidHealWasActive;

    @Inject
    WarmthRegenTracker(Client client, WintertodtState state)
    {
        this.client = client;
        this.state = state;
    }

    void reset()
    {
        ticksSinceRegen = -2;
        lastWarmth = -1;
        observedWarmPieces = -1;
        recognizedWarmPieces = -1;
        rapidHealWasActive = false;
    }

    void onGameTick()
    {
        boolean rapid = client.isPrayerActive(Prayer.RAPID_HEAL);
        int cycle = rapid ? RAPID_HEAL_REGEN_TICKS : NORMAL_REGEN_TICKS;

        if (rapid != rapidHealWasActive)
        {
            // RuneLite's own regeneration meter resynchronizes when Rapid Heal
            // changes. Do the same rather than showing a stale phase.
            ticksSinceRegen = 0;
            rapidHealWasActive = rapid;
        }
        else
        {
            ticksSinceRegen = (ticksSinceRegen + 1) % cycle;
        }

        recognizedWarmPieces = scanRecognizedWarmPieces();

        if (!state.inWintertodt())
        {
            lastWarmth = -1;
            return;
        }

        int warmth = state.warmth();
        if (lastWarmth >= 0 && warmth > lastWarmth)
        {
            int delta = warmth - lastWarmth;
            // A clean unclipped natural pulse directly reveals 0..4 warm items.
            if (lastWarmth <= 88 && delta >= 8 && delta <= 12)
            {
                observedWarmPieces = delta - 8;
                ticksSinceRegen = 0;
            }
            else if (lastWarmth <= 76 && delta >= 8 && delta <= 24)
            {
                // Regen bracelet / cape / other passive regeneration can make the
                // pulse larger. We can still phase-sync without guessing clothing.
                ticksSinceRegen = 0;
            }
        }
        lastWarmth = warmth;
    }

    double progress()
    {
        int cycle = rapidHealWasActive ? RAPID_HEAL_REGEN_TICKS : NORMAL_REGEN_TICKS;
        return ticksSinceRegen < 0 ? 0.0
            : Math.max(0.0, Math.min(1.0, ticksSinceRegen / (double)cycle));
    }

    int ticksUntilPulse()
    {
        int cycle = rapidHealWasActive ? RAPID_HEAL_REGEN_TICKS : NORMAL_REGEN_TICKS;
        return ticksSinceRegen < 0 ? cycle : Math.max(0, cycle - ticksSinceRegen);
    }

    int warmPieces()
    {
        int equipped = recognizedWarmPieces;
        if (equipped >= 4)
        {
            return 4;
        }
        if (observedWarmPieces >= 0)
        {
            return Math.max(equipped, observedWarmPieces);
        }
        return equipped > 0 ? equipped : -1;
    }

    int basePulseAmount()
    {
        int pieces = warmPieces();
        return pieces < 0 ? -1 : 8 + Math.min(4, pieces);
    }

    private int scanRecognizedWarmPieces()
    {
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        if (worn == null || worn.getItems() == null)
        {
            return -1;
        }

        int count = 0;
        for (Item item : worn.getItems())
        {
            if (item == null || item.getId() <= 0)
            {
                continue;
            }
            ItemComposition composition = client.getItemDefinition(item.getId());
            if (composition != null && isKnownWarmName(composition.getName()))
            {
                count++;
                if (count >= 4)
                {
                    return 4;
                }
            }
        }
        return count;
    }

    static boolean isKnownWarmName(String name)
    {
        if (name == null)
        {
            return false;
        }
        String n = name.toLowerCase();
        return n.contains("clue hunter")
            || n.equals("rainbow scarf") || n.equals("rainbow jumper")
            || n.contains("pyromancer") || n.equals("warm gloves")
            || n.contains("bruma torch") || n.contains("infernal axe")
            || n.equals("fire cape") || n.equals("infernal cape")
            || n.contains("firemaking cape") || n.contains("max cape")
            || n.equals("fire tiara") || n.equals("staff of fire")
            || n.equals("tome of fire") || n.equals("wolf mask")
            || n.equals("earmuffs") || n.equals("cap and goggles")
            || n.contains("woolly hat") || n.contains("bobble hat")
            || n.equals("jester hat") || n.equals("tri-jester hat")
            || n.contains("dragon candle dagger") || n.contains("old school jumper")
            || n.contains("santa hat") || n.contains("christmas")
            || n.contains("winter") || n.contains("snow")
            || n.contains("scarf") || n.contains("earmuff");
    }
}
