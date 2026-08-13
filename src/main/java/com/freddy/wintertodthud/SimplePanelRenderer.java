package com.freddy.wintertodthud;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

final class SimplePanelRenderer
{
    private SimplePanelRenderer(){}

    static Dimension render(Graphics2D source, WintertodtHudConfig config, String title, List<Line> lines)
    {
        int width = config.panelWidth();
        int fontSize = config.panelFontSize();
        int padding = 7;
        int lineHeight = fontSize + 5;
        int height = padding * 2 + lineHeight * (lines.size() + 1);

        Graphics2D g = (Graphics2D)source.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(config.panelBackground());
            g.fillRoundRect(0, 0, width - 1, height - 1, 8, 8);
            g.setColor(config.panelBorder());
            g.setStroke(new BasicStroke(1.25f));
            g.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);

            Font normal = new Font(config.panelFontName(), config.panelBold() ? Font.BOLD : Font.PLAIN, fontSize);
            Font titleFont = new Font(config.panelFontName(), Font.BOLD, fontSize + 1);
            g.setFont(titleFont);
            g.setColor(config.panelBorder());
            FontMetrics titleMetrics = g.getFontMetrics();
            g.drawString(title, padding, padding + titleMetrics.getAscent());
            Color border = config.panelBorder();
            g.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), Math.min(110, border.getAlpha())));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(padding, padding + lineHeight - 2, width - padding, padding + lineHeight - 2);

            g.setFont(normal);
            FontMetrics fm = g.getFontMetrics();
            int y = padding + lineHeight + fm.getAscent();
            for (Line line : lines)
            {
                g.setColor(line.color == null ? config.panelText() : line.color);
                g.drawString(line.text, padding, y);
                y += lineHeight;
            }
            return new Dimension(width, height);
        }
        finally
        {
            g.dispose();
        }
    }

    static final class Line
    {
        final String text;
        final Color color;
        Line(String text, Color color)
        {
            this.text = text;
            this.color = color;
        }
    }
}
