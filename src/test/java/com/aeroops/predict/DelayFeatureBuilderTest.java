package com.aeroops.predict;

import com.aeroops.predict.api.FlightRequest;
import com.aeroops.predict.model.DelayFeatureBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DelayFeatureBuilderTest {
    final List<String> feats = List.of("carrier", "origin", "dest", "dep_hour", "day_of_week", "day_of_month", "distance",
            "origin_7d_delay_rate", "route_28d_flights", "origin_hour_28d_delay_rate", "prev_leg_arr_delay", "leg_of_day");
    final DelayFeatureBuilder b = new DelayFeatureBuilder(feats,
            Map.of("carrier", List.of("AA", "WN"), "origin", List.of("ATL", "LGA"), "dest", List.of("ATL", "LGA")),
            Map.of("origin_7d", Map.of("ATL", new double[]{0.2, 100}),
                    "route_28d", Map.of("ATL|LGA", new double[]{0.3, 42}),
                    "origin_hour_28d", Map.of("ATL|9", new double[]{0.25, 7})), "2024-03-31");

    @Test
    void encodesCategoriesScheduleAndHistory() {
        // 2024-04-07 is a Sunday -> Spark dayofweek 1
        float[] x = b.build(new FlightRequest("WN", "ATL", "LGA", LocalDate.of(2024, 4, 7), 9, 762.0, 35.0, null, 2)).vector();
        assertThat(x[0]).isEqualTo(1f);
        assertThat(x[1]).isEqualTo(0f);
        assertThat(x[2]).isEqualTo(1f);
        assertThat(x[4]).isEqualTo(1f);
        assertThat(x[5]).isEqualTo(7f);
        assertThat(x[7]).isEqualTo(0.2f);
        assertThat(x[8]).isEqualTo(42f);
        assertThat(x[9]).isEqualTo(0.25f);
        assertThat(x[10]).isEqualTo(35f);
        assertThat(x[11]).isEqualTo(2f);
    }

    @Test
    void saturdayIsSeven() {
        float[] x = b.build(new FlightRequest("AA", "ATL", "LGA", LocalDate.of(2024, 4, 6), 9, 762.0, null, null, null)).vector();
        assertThat(x[4]).isEqualTo(7f);
    }

    @Test
    void unknownCategoryAndMissingHistoryBecomeMissing() {
        DelayFeatureBuilder.Built r = b.build(new FlightRequest("ZZ", "LGA", "ATL", LocalDate.of(2024, 4, 2), 5, 500.0, null, null, null));
        assertThat(r.unknownCategories()).containsExactly("carrier");
        assertThat(Float.isNaN(r.vector()[0])).isTrue();
        assertThat(Float.isNaN(r.vector()[7])).isTrue();   // no origin_7d entry for LGA
        assertThat(r.vector()[8]).isEqualTo(0f);           // missing count -> 0, like Python
        assertThat(Float.isNaN(r.vector()[10])).isTrue();
    }
}
