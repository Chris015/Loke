package loke.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class DecimalFormatFactory {

    public static DecimalFormat create(int decimals) {
        StringBuilder pattern = new StringBuilder("###,###.");
        for (int i = 0; i < decimals; i++) {
            pattern.append("#");
        }

        DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        formatter.applyPattern(pattern.toString());
        formatter.setRoundingMode(RoundingMode.CEILING);
        formatter.setMinimumFractionDigits(2);
        DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();

        symbols.setGroupingSeparator(' ');
        formatter.setDecimalFormatSymbols(symbols);

        return formatter;
    }
}
