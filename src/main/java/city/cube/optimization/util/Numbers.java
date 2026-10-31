package city.cube.optimization.util;

import java.util.Locale;

/** Number/label formatting shared by chat output, the GUI and placeholders. */
public final class Numbers {

    private Numbers() {
    }

    public static String ints(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public static String oneDecimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    public static String money(double value) {
        return String.format(Locale.US, "$%.2f", value);
    }

    public static String percent(double ratio) {
        return String.format(Locale.US, "%.0f%%", ratio * 100.0);
    }

    /** Full MiniMessage snippet for a TPS value, e.g. {@code <green>20.0</green>}. */
    public static String tpsText(double tps) {
        String color = tps >= 19.5 ? "green" : tps >= 17.0 ? "yellow" : "red";
        return "<" + color + ">" + oneDecimal(tps) + "</" + color + ">";
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
