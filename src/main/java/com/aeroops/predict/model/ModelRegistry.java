package com.aeroops.predict.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads model artifacts (exported by aeroops-ml) once at startup. */
@Component
public class ModelRegistry {
    private final XgbTreeModel model;
    private final DelayFeatureBuilder features;
    private final double threshold;
    private final String version;

    public ModelRegistry(@Value("${aeroops.model-dir:classpath:models/}") Resource dir, ObjectMapper mapper) throws IOException {
        try (InputStream in = dir.createRelative("xgb_delay.json").getInputStream()) {
            this.model = XgbTreeModel.load(in, mapper);
        }
        JsonNode meta;
        try (InputStream in = dir.createRelative("xgb_delay_meta.json").getInputStream()) {
            meta = mapper.readTree(in);
        }
        JsonNode snap;
        try (InputStream in = dir.createRelative("online_features.json").getInputStream()) {
            snap = mapper.readTree(in);
        }
        List<String> feats = mapper.convertValue(meta.get("features"), new TypeReference<>() {});
        Map<String, List<String>> cats = mapper.convertValue(meta.get("categories"), new TypeReference<>() {});
        Map<String, Map<String, double[]>> tables = new HashMap<>();
        snap.get("tables").fields().forEachRemaining(t -> {
            Map<String, double[]> m = new HashMap<>();
            t.getValue().fields().forEachRemaining(e ->
                    m.put(e.getKey(), new double[]{e.getValue().get(0).asDouble(), e.getValue().get(1).asDouble()}));
            tables.put(t.getKey(), m);
        });
        this.features = new DelayFeatureBuilder(feats, cats, tables, snap.get("as_of").asText());
        this.threshold = meta.get("threshold").asDouble();
        this.version = "xgb_delay@" + model.numTrees() + "trees/snapshot " + features.asOf();
    }

    public XgbTreeModel model() { return model; }
    public DelayFeatureBuilder features() { return features; }
    public double threshold() { return threshold; }
    public String version() { return version; }
}
