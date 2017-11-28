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
            ORANGE,
            BLACK,
            CORAL,
            DARKORANGE,
            LAVENDER,
            MAGENTA,
            MAROON,
            GOLDENROD,
            ALICEBLUE,
            BROWN,
            DARKSALMON,
            INDIGO,
            LIGHTGREEN,
            NAVY,
            TOMATO,
            PLUM,
            DARKSLATEGRAY,
            LIME,
            OLIVE

    };
    private int colorCounter = 0;

    public Color getNextColor() {
        Color color = COLORS[colorCounter];
        colorCounter++;
        if (isOutOfColors()) {
            resetColor();
        }
        return color;
    }

    public void resetColor() {
        colorCounter = 0;
    }

    private boolean isOutOfColors() {
        return colorCounter == COLORS.length;
    }
}
