package loke.utils;

import org.junit.Test;

import java.text.DecimalFormat;

import static org.junit.Assert.assertEquals;

public class DecimalFormatFactoryTest {
    private DecimalFormat zero = DecimalFormatFactory.create(0);
    private DecimalFormat one = DecimalFormatFactory.create(1);
    private DecimalFormat two = DecimalFormatFactory.create(2);
    private DecimalFormat three = DecimalFormatFactory.create(3);
    private DecimalFormat four = DecimalFormatFactory.create(4);
    private DecimalFormat five = DecimalFormatFactory.create(5);
    private DecimalFormat six = DecimalFormatFactory.create(6);


    @Test
    public void format_withZeroDecimals_returnsTwoDecimals() throws Exception {
        String expected = "1.00";
        assertEquals(expected, zero.format(1));
        assertEquals(expected, one.format(1));
        assertEquals(expected, two.format(1));
        assertEquals(expected, three.format(1));
    }

    @Test
    public void format_withDecimalsEqualToZero_returnsTwoDecimals() throws Exception {
        String expected = "1.00";
        assertEquals(expected, zero.format(1.00));
        assertEquals(expected, one.format(1.000));
        assertEquals(expected, two.format(1.000));
        assertEquals(expected, three.format(1.00000));
    }

    @Test
    public void format_withDecimalOtherThanZero_returnsSpecifiedAmountsOfDecimals() throws Exception {
        double aDouble = 1.123456789;
        assertEquals("1.13", two.format(aDouble));
        assertEquals("1.124", three.format(aDouble));
        assertEquals("1.1235", four.format(aDouble));
        assertEquals("1.12346", five.format(aDouble));
        assertEquals("1.123457", six.format(aDouble));
    }

    @Test
    public void format_minimumAmountOfDecimalsIsAlwaysTwo() throws Exception {
        String expected = "1.13";
        double aDouble = 1.1234;
        assertEquals(expected, zero.format(aDouble));
        assertEquals(expected, one.format(aDouble));
        assertEquals(expected, two.format(aDouble));
    }
}