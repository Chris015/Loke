package loke.utils;

import com.googlecode.charts4j.Color;

import static com.googlecode.charts4j.Color.*;

public class ColorPicker {
    private final static Color[] COLORS = new Color[]{
            BLUE,
            RED,
            PURPLE,
            GREEN,
            GRAY,
            AQUAMARINE,
            ORANGE
    };
    private static int colorCounter = 0;

    public static Color getNextColor() {
        Color color = COLORS[colorCounter];
        colorCounter++;
        if (isOutOfColors()) {
            resetColor();
        }
        return color;
    }

    public static void resetColor() {
        colorCounter = 0;
    }

    private static boolean isOutOfColors() {
        return colorCounter == COLORS.length;
    }
}
