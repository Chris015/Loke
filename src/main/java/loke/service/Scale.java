package loke.service;

import java.util.Arrays;
import java.util.List;

public enum Scale {
    BETWEN_0_10("USD", 0.1, Arrays.asList(0,1,2,3,4,5,6,7,8,9,10)),
    BETWEEN_11_100("USD", 1, Arrays.asList(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)),
    BETWEEN_101_1000("hundred USD", 10, Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
    BETWEEN_1001_INFINITY("thousand USD", 100, Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

    private final String name;
    private final double divideBy;
    private final List<Integer> yAxisLabels;

    Scale(String name, double divideBy, List<Integer> yAxisLabels) {
        this.name = name;
        this.divideBy = divideBy;
        this.yAxisLabels = yAxisLabels;
    }

    public String getSuffix() {
        return name;
    }

    public double getDivideBy() {
        return divideBy;
    }

    public List<Integer> getyAxisLabels() {return yAxisLabels;}
}
