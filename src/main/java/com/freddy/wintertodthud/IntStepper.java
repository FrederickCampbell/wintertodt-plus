package com.freddy.wintertodthud;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.runelite.client.ui.ColorScheme;

/**
 * Compact integer stepper for the Wintertodt+ sidebar.
 *
 * The +/- buttons deliberately keep RuneLite's normal ButtonUI. The center value
 * is only a label styled with RuneLite ColorScheme constants, so no custom
 * LookAndFeel delegate can interfere with mouse/keyboard behavior.
 */
@SuppressWarnings("serial")
final class IntStepper extends JPanel
{
    private final int min;
    private final int max;
    private int value;
    private final JButton decrease = new JButton("−");
    private final JButton increase = new JButton("+");
    private final JLabel valueLabel = new JLabel("", SwingConstants.CENTER);
    private final List<ChangeListener> listeners = new ArrayList<>();

    IntStepper(int value, int min, int max)
    {
        super(new BorderLayout(2, 0));
        if (min > max)
        {
            throw new IllegalArgumentException("min > max");
        }
        this.min = min;
        this.max = max;
        this.value = clamp(value);

        setOpaque(false);
        setPreferredSize(new Dimension(100, 22));
        setMaximumSize(new Dimension(100, 22));

        configureButton(decrease);
        configureButton(increase);

        valueLabel.setOpaque(true);
        valueLabel.setBackground(ColorScheme.CONTROL_COLOR);
        valueLabel.setForeground(ColorScheme.TEXT_COLOR);
        valueLabel.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
        valueLabel.setPreferredSize(new Dimension(42, 22));

        decrease.addActionListener(e -> setValue(this.value - 1));
        increase.addActionListener(e -> setValue(this.value + 1));

        add(decrease, BorderLayout.WEST);
        add(valueLabel, BorderLayout.CENTER);
        add(increase, BorderLayout.EAST);
        refresh();
    }

    private static void configureButton(JButton button)
    {
        button.setFocusable(false);
        button.setPreferredSize(new Dimension(27, 22));
        button.setMinimumSize(new Dimension(27, 22));
        button.setMargin(new Insets(0, 0, 0, 0));
    }

    int getValue()
    {
        return value;
    }

    void setValue(int value)
    {
        int next = clamp(value);
        if (next == this.value)
        {
            refresh();
            return;
        }
        this.value = next;
        refresh();
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : new ArrayList<>(listeners))
        {
            listener.stateChanged(event);
        }
    }

    void addChangeListener(ChangeListener listener)
    {
        if (listener != null)
        {
            listeners.add(listener);
        }
    }

    JButton decreaseButtonForTest()
    {
        return decrease;
    }

    JButton increaseButtonForTest()
    {
        return increase;
    }

    private int clamp(int value)
    {
        return Math.max(min, Math.min(max, value));
    }

    private void refresh()
    {
        valueLabel.setText(Integer.toString(value));
        decrease.setEnabled(value > min);
        increase.setEnabled(value < max);
    }
}
