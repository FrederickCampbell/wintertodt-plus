package com.freddy.wintertodthud;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class AlertOverlay extends Overlay
{
    private final Client client;
    private final WintertodtHudConfig config;
    private final AlertEngine alerts;

    @Inject
    AlertOverlay(WintertodtHudPlugin plugin, Client client, WintertodtHudConfig config, AlertEngine alerts)
    {
        super(plugin);
        this.client = client;
        this.config = config;
        this.alerts = alerts;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setMovable(false);
        setSnappable(false);
    }

    @Override
    public Dimension render(Graphics2D source)
    {
        if (!config.alertsEnabled())
        {
            return null;
        }

        AlertKind primaryKind = config.screenAlertsEnabled() ? alerts.primaryOverlayKind() : null;
        if (primaryKind == null)
        {
            return null;
        }
        AlertVisual primaryVisual = alerts.visual(primaryKind);

        Graphics2D g = (Graphics2D)source.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (primaryVisual.screen())
            {
                int period = Math.max(250, config.flashPeriodMs());
                boolean pulseOn = (System.currentTimeMillis() % period) < period / 2L;
                if (pulseOn)
                {
                    g.setColor(screenColor(primaryKind));
                    g.fillRect(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
                }

                if (config.showAlertText())
                {
                    drawLargeText(g, primaryKind.text());
                }
            }
            else if (primaryVisual.banner())
            {
                drawBanner(g, primaryKind.text(), screenColor(primaryKind));
            }
        }
        finally
        {
            g.dispose();
        }
        return null;
    }

    private Color screenColor(AlertKind kind)
    {
        switch (kind)
        {
            case IDLE:
                return config.idleScreenColor();
            case LOW_WARMTH:
                return config.lowWarmthScreenColor();
            case CRITICAL_WARMTH:
                return config.criticalWarmthScreenColor();
            default:
                return config.screenFlashColor();
        }
    }

    private void drawLargeText(Graphics2D g, String text)
    {
        g.setFont(new Font(config.alertFontName(), Font.BOLD, config.alertFontSize()));
        FontMetrics fm = g.getFontMetrics();
        int x = Math.max(8, (client.getCanvasWidth() - fm.stringWidth(text)) / 2);
        int y = Math.max(fm.getAscent() + 10, client.getCanvasHeight() / 7);
        g.setColor(Color.BLACK);
        g.drawString(text, x + 2, y + 2);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }

    private void drawBanner(Graphics2D g, String text, Color accent)
    {
        int fontSize = Math.max(16, Math.min(28, config.alertFontSize() - 6));
        Font font = new Font(config.alertFontName(), Font.BOLD, fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int paddingX = 18;
        int width = Math.min(client.getCanvasWidth() - 20, Math.max(210, fm.stringWidth(text) + paddingX * 2));
        int height = fontSize + 24;
        int x = Math.max(10, (client.getCanvasWidth() - width) / 2);
        int y = Math.max(12, client.getCanvasHeight() / 8);

        g.setColor(new Color(18, 18, 18, 225));
        g.fillRoundRect(x, y, width, height, 12, 12);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 235));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, width, height, 12, 12);

        int tx = x + (width - fm.stringWidth(text)) / 2;
        int ty = y + (height - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(Color.WHITE);
        g.drawString(text, tx, ty);
    }
}
