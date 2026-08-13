package com.freddy.wintertodthud;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

@Singleton
public class WintertodtState
{
    static final int WINTERTODT_REGION = 6462;
    static final int WINTERTODT_GROUP = 396;
    static final int NATIVE_WARMTH_CHILD = 15;
    static final int NATIVE_ENERGY_CHILD = 21;

    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern POINTS_AFTER = Pattern.compile("(?i)points?\\s*:?\\s*(\\d{1,5})");
    private static final Pattern POINTS_BEFORE = Pattern.compile("(?i)(\\d{1,5})\\s*points?");

    // Stable Wintertodt brazier anchor tiles. Travel is estimated with Chebyshev tile distance.
    private static final WorldPoint[] BRAZIERS = {
        new WorldPoint(1621, 3998, 0), // SW
        new WorldPoint(1621, 4016, 0), // NW
        new WorldPoint(1639, 4016, 0), // NE
        new WorldPoint(1639, 3998, 0)  // SE
    };

    private final Client client;
    private int warmth = 100;
    private int energy = -1;
    private int points = -1;
    private long gameTick;
    private long lastActivityTick;
    private long lastMovementTick;
    private LocalPoint lastLocation;
    private boolean hadRecentActivity;
    private boolean forcedIdle;
    private WintertodtActivity activity = WintertodtActivity.IDLE;
    private WorldPoint lastRootWorkTile;
    private WorldPoint lastBrazierWorkTile;
    private boolean brazierOutLikely;

    @Inject
    WintertodtState(Client client)
    {
        this.client = client;
    }

    public void tick()
    {
        gameTick++;
        if (!ready())
        {
            return;
        }

        warmth = clamp(client.getVarbitValue(VarbitID.WINT_WARMTH) / 10);
        if (inWintertodt())
        {
            int parsedEnergy = readEnergy();
            if (parsedEnergy >= 0)
            {
                energy = parsedEnergy;
            }
            int parsedPoints = readPoints();
            if (parsedPoints >= 0)
            {
                points = parsedPoints;
            }
        }
        else
        {
            energy = -1;
            points = -1;
        }

        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        LocalPoint now = player.getLocalLocation();
        if (now != null && !now.equals(lastLocation))
        {
            lastMovementTick = gameTick;
            lastLocation = now;
            if (player.getAnimation() == -1 && hadRecentActivity)
            {
                activity = WintertodtActivity.MOVING;
                forcedIdle = false;
            }
        }
        if (player.getAnimation() != -1 && hadRecentActivity)
        {
            lastActivityTick = gameTick;
            forcedIdle = false;
        }
    }

    public void setActivity(WintertodtActivity newActivity)
    {
        forcedIdle = false;
        activity = newActivity;
        Player player = client.getLocalPlayer();
        if (player != null)
        {
            WorldPoint here = player.getWorldLocation();
            if (newActivity == WintertodtActivity.CUTTING)
            {
                lastRootWorkTile = here;
            }
            else if (newActivity == WintertodtActivity.FEEDING
                || newActivity == WintertodtActivity.LIGHTING
                || newActivity == WintertodtActivity.REPAIRING)
            {
                lastBrazierWorkTile = here;
                if (newActivity == WintertodtActivity.FEEDING || newActivity == WintertodtActivity.LIGHTING)
                {
                    brazierOutLikely = false;
                }
            }
        }
        noteActivity();
    }

    public void noteActivity()
    {
        lastActivityTick = gameTick;
        hadRecentActivity = true;
    }

    public void resetActivity()
    {
        lastActivityTick = lastMovementTick = gameTick;
        hadRecentActivity = false;
        forcedIdle = false;
        lastLocation = null;
        activity = WintertodtActivity.IDLE;
    }

    /** Mark a known Wintertodt action interruption as immediately idle. */
    public void interruptActivity()
    {
        lastActivityTick = lastMovementTick = gameTick;
        hadRecentActivity = true;
        forcedIdle = true;
        activity = WintertodtActivity.IDLE;
    }

    public int warmth(){ return warmth; }
    public int energy(){ return energy; }
    public int points(){ return points; }
    public WintertodtActivity activity(){ return activity; }
    public boolean hasRecentActivity(){ return hadRecentActivity; }

    public boolean ready()
    {
        return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
    }

    public boolean inWintertodt()
    {
        Player player = client.getLocalPlayer();
        return player != null && player.getWorldLocation().getRegionID() == WINTERTODT_REGION;
    }

    public boolean roundActive()
    {
        return inWintertodt() && client.getVarbitValue(VarbitID.WINT_TRANSMIT_RESPAWNDELAY) == 0;
    }

    public boolean idleFor(int seconds)
    {
        if (!roundActive() || !hadRecentActivity)
        {
            return false;
        }
        if (forcedIdle)
        {
            return true;
        }
        Player player = client.getLocalPlayer();
        if (player == null || player.getAnimation() != -1)
        {
            return false;
        }

        // Wintertodt actions have legitimate blank-animation ticks between repeats.
        // Treat 0 seconds as "instant once the current action cadence has expired",
        // not "the first tick animation becomes -1". A new repeat animation or
        // movement refreshes the evidence tick before this can fire.
        long evidenceTick = Math.max(lastActivityTick, lastMovementTick);
        long requestedTicks = seconds <= 0 ? 0 : (long)Math.ceil(seconds / 0.6d);
        long graceTicks = Math.max(requestedTicks, actionCadenceGraceTicks(activity));
        boolean idle = gameTick - evidenceTick >= graceTicks;
        if (idle)
        {
            activity = WintertodtActivity.IDLE;
        }
        return idle;
    }

    static int actionCadenceGraceTicks(WintertodtActivity activity)
    {
        if (activity == null)
        {
            return 1;
        }
        switch (activity)
        {
            case CUTTING:
                return 4; // 3t roots + one tick to distinguish a real stop
            case FLETCHING:
                return 5; // 4t action cadence
            case FEEDING:
                return 4; // 3t feed cadence; animation can blank between feeds
            case LIGHTING:
            case REPAIRING:
                return 5;
            case MOVING:
                return 2;
            case IDLE:
            default:
                return 1;
        }
    }

    public int nearestBrazierTiles()
    {
        Player player = client.getLocalPlayer();
        if (player == null || !inWintertodt())
        {
            return -1;
        }
        WorldPoint here = player.getWorldLocation();
        if (lastBrazierWorkTile != null && lastBrazierWorkTile.getPlane() == here.getPlane())
        {
            return distance(here, lastBrazierWorkTile);
        }
        int best = Integer.MAX_VALUE;
        for (WorldPoint brazier : BRAZIERS)
        {
            best = Math.min(best, distance(here, brazier));
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    public int nearestRootTiles()
    {
        Player player = client.getLocalPlayer();
        if (player == null || !inWintertodt() || lastRootWorkTile == null)
        {
            return -1;
        }
        return distance(player.getWorldLocation(), lastRootWorkTile);
    }

    public int rootToBrazierTiles(int fallback)
    {
        if (lastRootWorkTile != null && lastBrazierWorkTile != null
            && lastRootWorkTile.getPlane() == lastBrazierWorkTile.getPlane())
        {
            return Math.max(1, distance(lastRootWorkTile, lastBrazierWorkTile));
        }

        // Mid-round login/world-hop bootstrap: once cutting proves the root tile,
        // we can derive the nearest physical brazier without waiting for the player
        // to click/feed one. This is better than a generic fallback and still
        // converges to the exact learned route after a real brazier interaction.
        if (lastRootWorkTile != null)
        {
            int best = Integer.MAX_VALUE;
            for (WorldPoint brazier : BRAZIERS)
            {
                if (brazier.getPlane() == lastRootWorkTile.getPlane())
                {
                    best = Math.min(best, distance(lastRootWorkTile, brazier));
                }
            }
            if (best != Integer.MAX_VALUE)
            {
                return Math.max(1, best);
            }
        }
        return Math.max(1, fallback);
    }

    public boolean hasRootAnchor()
    {
        return lastRootWorkTile != null;
    }

    public WorldPoint rootAnchor()
    {
        return lastRootWorkTile;
    }

    public boolean hasBrazierAnchor()
    {
        return lastBrazierWorkTile != null;
    }

    public WorldPoint brazierAnchor()
    {
        return lastBrazierWorkTile;
    }

    public WintertodtStationMonitor.Quadrant brazierQuadrant()
    {
        if (lastBrazierWorkTile == null)
        {
            return null;
        }
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < BRAZIERS.length; i++)
        {
            int candidate = distance(lastBrazierWorkTile, BRAZIERS[i]);
            if (candidate < bestDistance)
            {
                bestDistance = candidate;
                best = i;
            }
        }
        return best < 0 ? null : WintertodtStationMonitor.Quadrant.values()[best];
    }

    public boolean routeCalibrated()
    {
        return hasRootAnchor() && hasBrazierAnchor();
    }

    /**
     * Anchors the station the player is most likely interacting with. This is
     * called on a real Brazier menu click so lobby/random movement never
     * calibrates route timing.
     */
    public void anchorNearestBrazier()
    {
        Player player = client.getLocalPlayer();
        if (player == null || !inWintertodt())
        {
            return;
        }

        WorldPoint here = player.getWorldLocation();
        WorldPoint best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (WorldPoint brazier : BRAZIERS)
        {
            int candidate = distance(here, brazier);
            if (candidate < bestDistance)
            {
                bestDistance = candidate;
                best = brazier;
            }
        }
        if (best != null)
        {
            lastBrazierWorkTile = best;
            noteActivity();
        }
    }

    public void clearRouteCalibration()
    {
        lastRootWorkTile = null;
        lastBrazierWorkTile = null;
    }

    public boolean atBrazier()
    {
        if (activity == WintertodtActivity.FEEDING || activity == WintertodtActivity.LIGHTING
            || activity == WintertodtActivity.REPAIRING)
        {
            return true;
        }
        int tiles = nearestBrazierTiles();
        return tiles >= 0 && tiles <= 2;
    }

    public void noteBrazierOut()
    {
        brazierOutLikely = true;
    }

    public void noteBrazierLit()
    {
        brazierOutLikely = false;
    }

    public boolean brazierOutLikely()
    {
        return brazierOutLikely;
    }

    private static int distance(WorldPoint a, WorldPoint b)
    {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    public void setGamePointsHidden(boolean hidden)
    {
        for (int child = 0; child < 45; child++)
        {
            Widget widget = client.getWidget(WINTERTODT_GROUP, child);
            setPointTextHidden(widget, hidden, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    private void setPointTextHidden(Widget widget, boolean hidden, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget))
        {
            return;
        }
        String text = widget.getText();
        if (text != null && text.toLowerCase().contains("point"))
        {
            widget.setHidden(hidden);
        }
        Widget[][] groups = {widget.getChildren(), widget.getStaticChildren(), widget.getNestedChildren()};
        for (Widget[] group : groups)
        {
            if (group == null)
            {
                continue;
            }
            for (Widget child : group)
            {
                setPointTextHidden(child, hidden, visited);
            }
        }
    }

    private int readEnergy()
    {
        Widget preferred = client.getWidget(WINTERTODT_GROUP, NATIVE_ENERGY_CHILD);
        Integer value = findEnergyPercent(preferred, true, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (value != null)
        {
            return value;
        }
        for (int child = 0; child < 45; child++)
        {
            Widget widget = client.getWidget(WINTERTODT_GROUP, child);
            value = findEnergyPercent(widget, false, Collections.newSetFromMap(new IdentityHashMap<>()));
            if (value != null)
            {
                return value;
            }
        }
        return -1;
    }

    private int readPoints()
    {
        for (int child = 0; child < 45; child++)
        {
            Widget widget = client.getWidget(WINTERTODT_GROUP, child);
            Integer value = findPoints(widget, Collections.newSetFromMap(new IdentityHashMap<>()));
            if (value != null)
            {
                return value;
            }
        }
        return -1;
    }

    private Integer findEnergyPercent(Widget widget, boolean acceptAnyPercent, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget))
        {
            return null;
        }
        String text = widget.getText();
        if (text != null && !text.isEmpty())
        {
            boolean energyText = text.toLowerCase().contains("energy");
            if (energyText || acceptAnyPercent)
            {
                Matcher matcher = PERCENT.matcher(text);
                if (matcher.find())
                {
                    return clamp(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        Widget[][] groups = {widget.getChildren(), widget.getStaticChildren(), widget.getNestedChildren()};
        for (Widget[] group : groups)
        {
            if (group == null)
            {
                continue;
            }
            for (Widget child : group)
            {
                Integer value = findEnergyPercent(child, acceptAnyPercent, visited);
                if (value != null)
                {
                    return value;
                }
            }
        }
        return null;
    }

    private Integer findPoints(Widget widget, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget))
        {
            return null;
        }
        String text = widget.getText();
        if (text != null && !text.isEmpty())
        {
            Matcher after = POINTS_AFTER.matcher(text);
            if (after.find())
            {
                return Integer.parseInt(after.group(1));
            }
            Matcher before = POINTS_BEFORE.matcher(text);
            if (before.find())
            {
                return Integer.parseInt(before.group(1));
            }
        }
        Widget[][] groups = {widget.getChildren(), widget.getStaticChildren(), widget.getNestedChildren()};
        for (Widget[] group : groups)
        {
            if (group == null)
            {
                continue;
            }
            for (Widget child : group)
            {
                Integer value = findPoints(child, visited);
                if (value != null)
                {
                    return value;
                }
            }
        }
        return null;
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(100, value));
    }
}
