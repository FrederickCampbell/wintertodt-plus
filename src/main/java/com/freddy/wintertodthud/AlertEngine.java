package com.freddy.wintertodthud;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.Notifier;

@Singleton
public class AlertEngine
{
    private final WintertodtHudConfig config;
    private final Notifier notifier;
    private final EnumMap<AlertKind, AlertRecord> active = new EnumMap<>(AlertKind.class);

    @Inject
    AlertEngine(WintertodtHudConfig config, Notifier notifier)
    {
        this.config = config;
        this.notifier = notifier;
    }

    public void clear(){ active.clear(); }

    public void dismiss(AlertKind kind)
    {
        active.remove(kind);
    }

    public void setPersistent(AlertKind kind, boolean on, AlertVisual visual, boolean notify)
    {
        if (!on)
        {
            active.remove(kind);
            return;
        }

        long now = System.currentTimeMillis();
        AlertRecord r = active.get(kind);
        if (r == null)
        {
            r = new AlertRecord(kind, visual, Long.MAX_VALUE);
            active.put(kind, r);
            if (notify)
            {
                send(r, now);
            }
        }
        else
        {
            r.visual = visual;
            if (notify && config.repeatPersistentNotifications()
                && now - r.lastNotification >= config.repeatSeconds() * 1000L)
            {
                send(r, now);
            }
        }
    }

    public void fire(AlertKind kind, AlertVisual visual, boolean notify)
    {
        fire(kind, visual, notify, config.eventDuration());
    }

    public void fire(AlertKind kind, AlertVisual visual, boolean notify, int durationSeconds)
    {
        long now = System.currentTimeMillis();
        AlertRecord r = new AlertRecord(kind, visual, now + Math.max(1, durationSeconds) * 1000L);
        active.put(kind, r);
        if (notify)
        {
            send(r, now);
        }
    }

    public void expire()
    {
        long now = System.currentTimeMillis();
        active.values().removeIf(r -> r.expiresAt != Long.MAX_VALUE && now >= r.expiresAt);
    }

    private void send(AlertRecord r, long now)
    {
        notifier.notify("Wintertodt: " + r.kind.text());
        r.lastNotification = now;
    }

    public Collection<AlertRecord> active()
    {
        expire();
        return Collections.unmodifiableCollection(active.values());
    }

    public AlertKind primaryOverlayKind()
    {
        AlertKind[] priority = {
            AlertKind.CRITICAL_WARMTH,
            AlertKind.POINTS_NOT_SAFE,
            AlertKind.GO_NOW,
            AlertKind.LOW_WARMTH,
            AlertKind.INTERRUPTED,
            AlertKind.BRAZIER_OUT,
            AlertKind.IDLE,
            AlertKind.INVENTORY_FULL,
            AlertKind.OUT_OF_ROOTS,
            AlertKind.ROUND_STARTING,
            AlertKind.LOW_ENERGY
        };

        for (AlertKind kind : priority)
        {
            AlertRecord r = active.get(kind);
            if (r != null && (r.visual.screen() || r.visual.banner()))
            {
                return kind;
            }
        }
        return null;
    }

    public AlertVisual visual(AlertKind kind)
    {
        AlertRecord r = active.get(kind);
        return r == null ? AlertVisual.NONE : r.visual;
    }

    public AlertKind primaryScreenKind()
    {
        return primaryKind(true, false);
    }

    public AlertKind primaryBannerKind()
    {
        return primaryKind(false, true);
    }

    private AlertKind primaryKind(boolean screen, boolean banner)
    {
        AlertKind[] priority = {
            AlertKind.CRITICAL_WARMTH,
            AlertKind.LOW_WARMTH,
            AlertKind.IDLE,
            AlertKind.POINTS_NOT_SAFE,
            AlertKind.GO_NOW,
            AlertKind.INVENTORY_FULL,
            AlertKind.OUT_OF_ROOTS,
            AlertKind.BRAZIER_OUT,
            AlertKind.INTERRUPTED,
            AlertKind.LOW_ENERGY,
            AlertKind.ROUND_STARTING
        };

        for (AlertKind kind : priority)
        {
            AlertRecord r = active.get(kind);
            if (r == null)
            {
                continue;
            }
            if ((screen && r.visual.screen()) || (banner && r.visual.banner()))
            {
                return kind;
            }
        }
        return null;
    }

    public boolean meterActive(AlertKind.MeterTarget meter)
    {
        for (AlertRecord r : active())
        {
            if (r.visual.bothMeters())
            {
                return true;
            }
            if (r.visual.relevantMeter()
                && (r.kind.target() == meter || r.kind.target() == AlertKind.MeterTarget.BOTH))
            {
                return true;
            }
        }
        return false;
    }

    public boolean isActive(AlertKind kind)
    {
        expire();
        return active.containsKey(kind);
    }

    public static final class AlertRecord
    {
        final AlertKind kind;
        AlertVisual visual;
        final long expiresAt;
        long lastNotification;

        AlertRecord(AlertKind kind, AlertVisual visual, long expiresAt)
        {
            this.kind = kind;
            this.visual = visual;
            this.expiresAt = expiresAt;
        }
    }
}
