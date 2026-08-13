package com.freddy.wintertodthud;

import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class IntStepperTest
{
    @Test
    public void ordinaryButtonsAdvanceAndClamp() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            IntStepper stepper = new IntStepper(0, 0, 2);
            stepper.increaseButtonForTest().doClick();
            assertEquals(1, stepper.getValue());
            stepper.increaseButtonForTest().doClick();
            assertEquals(2, stepper.getValue());
            stepper.increaseButtonForTest().doClick();
            assertEquals(2, stepper.getValue());
            stepper.decreaseButtonForTest().doClick();
            assertEquals(1, stepper.getValue());
        });
    }

    @Test
    public void renderedMousePressReleaseAdvancesValue() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            IntStepper stepper = new IntStepper(0, 0, 2);
            stepper.setSize(100, 22);
            stepper.doLayout();

            JButton plus = stepper.increaseButtonForTest();
            plus.setSize(27, 22);
            long now = System.currentTimeMillis();
            plus.dispatchEvent(new MouseEvent(plus, MouseEvent.MOUSE_PRESSED, now, 0, 13, 11, 1, false, MouseEvent.BUTTON1));
            plus.dispatchEvent(new MouseEvent(plus, MouseEvent.MOUSE_RELEASED, now + 1, 0, 13, 11, 1, false, MouseEvent.BUTTON1));

            assertEquals(1, stepper.getValue());
        });
    }
}
