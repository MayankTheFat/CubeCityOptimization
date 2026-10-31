package city.cube.optimization.storage;

import java.util.Map;

/** Tiny JSON encoder for counter maps; avoids pulling a JSON library into the jar. */
public final class SnapshotJson {

    private SnapshotJson() {
    }

    public static String counters(Map<String, Long> counters) {
        StringBuilder builder = new StringBuilder(counters.size() * 24 + 2);
        builder.append('{');
        boolean first = true;
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        return builder.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
