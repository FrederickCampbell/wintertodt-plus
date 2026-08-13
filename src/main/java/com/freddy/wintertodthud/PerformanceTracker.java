package com.freddy.wintertodthud;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

@Singleton
public class PerformanceTracker
{
    private static final int MAX_SAMPLES = 30;
    private static final int MAX_CLEAN_FEED_GAP_TICKS = 6;

    private final Client client;
    private final WintertodtState state;

    private final Deque<Integer> cutSamples = new ArrayDeque<>();
    private final Deque<Integer> fletchSamples = new ArrayDeque<>();
    private final Deque<Integer> feedSamples = new ArrayDeque<>();
    private final Deque<Integer> moveTenthsPerTileSamples = new ArrayDeque<>();

    private long tick;
    private long lastRootGainTick = -1;
    private long lastFletchTick = -1;
    private long lastFeedTick = -1;
    private int roots;
    private int kindling;
    private int usedSlots;
    private boolean inventoryReady;
    private int trackedPoints;
    private int totalFletchesObserved;
    private WorldPoint lastWorldPoint;
    private boolean learningArmed;

    // These flags prevent AFK gaps from becoming fake "slow action" samples.
    private boolean cutIntervalClean;
    private boolean fletchIntervalClean;
    private boolean feedIntervalClean;

    @Inject
    PerformanceTracker(Client client, WintertodtState state)
    {
        this.client = client;
        this.state = state;
    }

    public void resetRound()
    {
        tick = 0;
        lastRootGainTick = -1;
        lastFletchTick = -1;
        lastFeedTick = -1;
        trackedPoints = 0;
        totalFletchesObserved = 0;
        inventoryReady = false;
        roots = 0;
        kindling = 0;
        usedSlots = 0;
        lastWorldPoint = null;
        learningArmed = state.hasBrazierAnchor();
        cutIntervalClean = false;
        fletchIntervalClean = false;
        feedIntervalClean = false;
    }

    public void onGameTick()
    {
        tick++;
        initializeInventorySnapshotIfNeeded();
        updateLearningArm();
        if (learningArmed)
        {
            sampleMovement();
            updateContinuousActionFlags();
        }
        else
        {
            lastWorldPoint = null;
        }
    }

    private void initializeInventorySnapshotIfNeeded()
    {
        if (inventoryReady)
        {
            return;
        }
        ItemContainer container = client.getItemContainer(InventoryID.INV);
        if (container == null || container.getItems() == null)
        {
            return;
        }

        int initialRoots = 0;
        int initialKindling = 0;
        int initialUsedSlots = 0;
        for (Item item : container.getItems())
        {
            if (item == null)
            {
                continue;
            }
            if (item.getId() > 0)
            {
                initialUsedSlots++;
            }
            if (item.getId() == ItemID.WINT_BRUMA_ROOT)
            {
                initialRoots += Math.max(1, item.getQuantity());
            }
            else if (item.getId() == ItemID.WINT_BRUMA_KINDLING)
            {
                initialKindling += Math.max(1, item.getQuantity());
            }
        }
        roots = initialRoots;
        kindling = initialKindling;
        usedSlots = initialUsedSlots;
        inventoryReady = true;
    }

    public void onInventoryChanged(ItemContainerChanged event)
    {
        ItemContainer container = event.getItemContainer();
        if (container == null || container != client.getItemContainer(InventoryID.INV))
        {
            return;
        }

        int newRoots = 0;
        int newKindling = 0;
        int newUsedSlots = 0;
        for (Item item : container.getItems())
        {
            if (item.getId() > 0)
            {
                newUsedSlots++;
            }
            if (item.getId() == ItemID.WINT_BRUMA_ROOT)
            {
                newRoots += Math.max(1, item.getQuantity());
            }
            else if (item.getId() == ItemID.WINT_BRUMA_KINDLING)
            {
                newKindling += Math.max(1, item.getQuantity());
            }
        }

        if (!inventoryReady)
        {
            roots = newRoots;
            kindling = newKindling;
            usedSlots = newUsedSlots;
            inventoryReady = true;
            return;
        }

        int rootDelta = newRoots - roots;
        int kindlingDelta = newKindling - kindling;
        int animation = currentAnimation();

        // Inventory/point state is always tracked, but session timing samples are
        // deliberately unarmed until the player has interacted with a brazier.
        // This prevents lobby/world-hop movement and pre-route actions from
        // contaminating the learned cut/fletch/feed/movement timings.
        if (!learningArmed)
        {
            int transformed = Math.min(Math.max(0, -rootDelta), Math.max(0, kindlingDelta));
            totalFletchesObserved += transformed;
            int fedRoots = Math.max(0, roots - newRoots - transformed);
            int fedKindling = Math.max(0, kindling - newKindling);
            trackedPoints += fedRoots * WintertodtMechanics.ROOT_POINTS
                + fedKindling * WintertodtMechanics.KINDLING_POINTS;
            roots = newRoots;
            kindling = newKindling;
            usedSlots = newUsedSlots;
            lastRootGainTick = -1;
            lastFletchTick = -1;
            lastFeedTick = -1;
            cutIntervalClean = false;
            fletchIntervalClean = false;
            feedIntervalClean = false;
            return;
        }

        if (rootDelta > 0)
        {
            if (cutIntervalClean)
            {
                recordInterval(cutSamples, lastRootGainTick, tick, rootDelta);
            }
            lastRootGainTick = tick;
            cutIntervalClean = isCutAnimation(animation);
        }

        int transformed = Math.min(Math.max(0, -rootDelta), Math.max(0, kindlingDelta));
        totalFletchesObserved += transformed;
        if (transformed > 0)
        {
            if (fletchIntervalClean)
            {
                recordInterval(fletchSamples, lastFletchTick, tick, transformed);
            }
            lastFletchTick = tick;
            fletchIntervalClean = animation == AnimationID.HUMAN_FLETCHING;
        }

        int fedRoots = Math.max(0, roots - newRoots - transformed);
        int fedKindling = Math.max(0, kindling - newKindling);
        int fed = fedRoots + fedKindling;
        if (fed > 0)
        {
            long gap = lastFeedTick < 0 ? Long.MAX_VALUE : tick - lastFeedTick;
            boolean feedAnimation = animation == AnimationID.HUMAN_PICKUPTABLE
                || state.activity() == WintertodtActivity.FEEDING;
            if (lastFeedTick >= 0 && gap > 0 && gap <= MAX_CLEAN_FEED_GAP_TICKS
                && (feedIntervalClean || feedAnimation))
            {
                recordInterval(feedSamples, lastFeedTick, tick, fed);
            }
            lastFeedTick = tick;
            // The decrement itself proves a brazier-feeding action. Keep the
            // short episode armed across animation gaps; timeout handles AFK.
            feedIntervalClean = true;
            trackedPoints += fedRoots * WintertodtMechanics.ROOT_POINTS
                + fedKindling * WintertodtMechanics.KINDLING_POINTS;
        }

        roots = newRoots;
        kindling = newKindling;
        usedSlots = newUsedSlots;
    }

    private void updateLearningArm()
    {
        boolean shouldArm = state.hasBrazierAnchor();
        if (shouldArm == learningArmed)
        {
            return;
        }

        learningArmed = shouldArm;
        lastRootGainTick = -1;
        lastFletchTick = -1;
        lastFeedTick = -1;
        lastWorldPoint = null;
        cutIntervalClean = false;
        fletchIntervalClean = false;
        feedIntervalClean = false;
    }

    public boolean learningArmed()
    {
        return learningArmed;
    }

    private void updateContinuousActionFlags()
    {
        int animation = currentAnimation();
        if (lastRootGainTick >= 0 && !isCutAnimation(animation))
        {
            cutIntervalClean = false;
        }
        if (lastFletchTick >= 0 && animation != AnimationID.HUMAN_FLETCHING)
        {
            fletchIntervalClean = false;
        }
        if (lastFeedTick >= 0 && tick - lastFeedTick > MAX_CLEAN_FEED_GAP_TICKS)
        {
            feedIntervalClean = false;
        }
    }

    private int currentAnimation()
    {
        Player player = client.getLocalPlayer();
        return player == null ? -1 : player.getAnimation();
    }

    private void recordInterval(Deque<Integer> samples, long previousTick, long nowTick, int actions)
    {
        if (previousTick >= 0 && nowTick > previousTick)
        {
            int perAction = Math.max(1, (int)Math.ceil((nowTick - previousTick) / (double)Math.max(1, actions)));
            addSample(samples, perAction);
        }
    }

    private static void addSample(Deque<Integer> samples, int value)
    {
        samples.addLast(Math.min(60, Math.max(1, value)));
        while (samples.size() > MAX_SAMPLES)
        {
            samples.removeFirst();
        }
    }

    public void addTrackedPoints(int points)
    {
        trackedPoints += Math.max(0, points);
    }

    public int trackedPoints(){ return trackedPoints; }
    public int roots(){ return roots; }
    public int kindling(){ return kindling; }
    public int totalFletchesObserved(){ return totalFletchesObserved; }
    public int usedSlots(){ return usedSlots; }
    public int freeSlots(){ return Math.max(0, 28 - usedSlots); }
    public int inventoryPoints(){ return AdvisorMath.inventoryPoints(roots, kindling); }
    public int potentialInventoryPoints(){ return AdvisorMath.potentialInventoryPoints(roots, kindling); }

    /** Hard mechanical cycle: Bruma-root cutting is budgeted at 3 ticks/root. */
    public double maxCutTicks()
    {
        return WintertodtMechanics.CUT_TICKS;
    }

    /** SAFE uses the same mechanic; safety lives in one route-level reserve. */
    public double safeCutTicks()
    {
        return WintertodtMechanics.CUT_TICKS;
    }

    public double maxFletchTicks()
    {
        return WintertodtMechanics.fletchTicks(fletchingKnifeEquipped());
    }

    public double safeFletchTicks()
    {
        return WintertodtMechanics.fletchTicks(fletchingKnifeEquipped());
    }

    public double maxFeedTicks()
    {
        return WintertodtMechanics.FEED_TICKS;
    }

    public double safeFeedTicks()
    {
        return WintertodtMechanics.FEED_TICKS;
    }

    public double maxMovementTicksPerTile(boolean assumeRunning)
    {
        return WintertodtMechanics.movementTicksPerTile(assumeRunning);
    }

    public double safeMovementTicksPerTile(boolean assumeRunning)
    {
        return WintertodtMechanics.movementTicksPerTile(assumeRunning);
    }

    /**
     * The new Fletching knife only changes the mechanic when it is equipped.
     * Scan the worn-item container each planner tick so equipment swaps are
     * reflected immediately without another learned/calibrated state.
     */
    public boolean fletchingKnifeEquipped()
    {
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        if (worn == null || worn.getItems() == null)
        {
            return false;
        }
        for (Item item : worn.getItems())
        {
            if (item != null && item.getId() == ItemID.FLETCHING_KNIFE)
            {
                return true;
            }
        }
        return false;
    }

    // Legacy aliases used by the Advanced display.
    public double cutTicks(){ return safeCutTicks(); }
    public double fletchTicks(){ return safeFletchTicks(); }
    public double feedTicks(){ return safeFeedTicks(); }
    public double movementTicksPerTile(boolean assumeRunning){ return safeMovementTicksPerTile(assumeRunning); }

    public int learnedSamples()
    {
        return cutSamples.size() + fletchSamples.size() + feedSamples.size() + moveTenthsPerTileSamples.size();
    }

    public String sampleCountSummary()
    {
        return "cut=" + cutSamples.size() + " fletch=" + fletchSamples.size()
            + " feed=" + feedSamples.size() + " move=" + moveTenthsPerTileSamples.size();
    }

    private void sampleMovement()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            lastWorldPoint = null;
            return;
        }
        WorldPoint now = player.getWorldLocation();
        if (lastWorldPoint != null && now != null && now.getPlane() == lastWorldPoint.getPlane())
        {
            int tiles = Math.max(Math.abs(now.getX() - lastWorldPoint.getX()), Math.abs(now.getY() - lastWorldPoint.getY()));
            if (tiles > 0)
            {
                addSample(moveTenthsPerTileSamples, (int)Math.ceil(10.0 / tiles));
            }
        }
        lastWorldPoint = now;
    }

    private static boolean isCutAnimation(int animation)
    {
        switch (animation)
        {
            case AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE:
            case AnimationID.HUMAN_WOODCUTTING_IRON_AXE:
            case AnimationID.HUMAN_WOODCUTTING_STEEL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_BLACK_AXE:
            case AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE:
            case AnimationID.HUMAN_WOODCUTTING_RUNE_AXE:
            case AnimationID.HUMAN_WOODCUTTING_GILDED_AXE:
            case AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL:
            case AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_3A_AXE:
            case AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE:
            case AnimationID.HUMAN_OPENHEAVYCHEST:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_3A:
                return true;
            default:
                return false;
        }
    }
}
