package com.aeroops.predict.model;

import com.aeroops.predict.api.FlightRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the 20-feature vector exactly as the Python AeroOps API does: schedule features,
 * history features looked up from the online feature-store snapshot, and operational features.
 * Unknown categories and missing values become NaN (treated as missing by the trees).
 */
public final class DelayFeatureBuilder {
    /** name -> grouping keys, mirrors aeroops.flights.schema.HISTORY_SPECS */
    static final Map<String, String[]> HISTORY_SPECS = new java.util.LinkedHashMap<>();
    static {
        HISTORY_SPECS.put("origin_7d", new String[]{"origin"});
        HISTORY_SPECS.put("dest_7d", new String[]{"dest"});
        HISTORY_SPECS.put("carrier_7d", new String[]{"carrier"});
        HISTORY_SPECS.put("route_28d", new String[]{"origin", "dest"});
        HISTORY_SPECS.put("origin_hour_28d", new String[]{"origin", "dep_hour"});
    }

    private final List<String> features;
    private final Map<String, Map<String, Integer>> categoryCodes;
    private final Map<String, Map<String, double[]>> snapshot;
    private final String asOf;

    public DelayFeatureBuilder(List<String> features, Map<String, List<String>> categories,
                               Map<String, Map<String, double[]>> snapshot, String asOf) {
        this.features = List.copyOf(features);
        this.categoryCodes = new HashMap<>();
        categories.forEach((c, levels) -> {
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; i < levels.size(); i++) m.put(levels.get(i), i);
            categoryCodes.put(c, m);
        });
        this.snapshot = snapshot;
        this.asOf = asOf;
    }

    public String asOf() { return asOf; }

    public record Built(float[] vector, List<String> unknownCategories) {}

    public Built build(FlightRequest r) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("carrier", r.carrier());
        raw.put("origin", r.origin());
        raw.put("dest", r.dest());
        raw.put("dep_hour", r.depHour());
        Map<String, Double> num = new HashMap<>();
        num.put("dep_hour", (double) r.depHour());
        num.put("day_of_week", (double) (r.flightDate().getDayOfWeek().getValue() % 7 + 1)); // Spark: Sun=1..Sat=7
        num.put("day_of_month", (double) r.flightDate().getDayOfMonth());
        num.put("distance", r.distance());
        num.put("prev_leg_arr_delay", r.prevLegArrDelay());
        num.put("prev_leg_dep_delay", r.prevLegDepDelay());
        num.put("leg_of_day", r.legOfDay() == null ? null : r.legOfDay().doubleValue());
        HISTORY_SPECS.forEach((name, keys) -> {
            StringBuilder k = new StringBuilder();
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) k.append('|');
                k.append(raw.get(keys[i]));
            }
            double[] v = snapshot.getOrDefault(name, Map.of()).get(k.toString());
            num.put(name + "_delay_rate", v == null ? null : v[0]);
            num.put(name + "_flights", v == null ? 0.0 : v[1]);
        });
        float[] x = new float[features.size()];
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            String f = features.get(i);
            Map<String, Integer> codes = categoryCodes.get(f);
            if (codes != null) {
                Integer code = codes.get((String) raw.get(f));
                if (code == null) unknown.add(f);
                x[i] = code == null ? Float.NaN : code;
            } else {
                Double v = num.get(f);
                x[i] = v == null ? Float.NaN : (float) v.doubleValue();
            }
        }
        return new Built(x, unknown);
    }
}
