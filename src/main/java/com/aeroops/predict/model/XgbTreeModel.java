package com.aeroops.predict.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Dependency-free evaluator for an XGBoost (>=2.0) JSON model with gbtree booster,
 * numeric + native categorical (partition) splits, and binary:logistic objective.
 * Arrays are flattened per tree for cache-friendly traversal. Features are float32
 * (as in XGBoost); NaN means missing and follows the node's default direction.
 */
public final class XgbTreeModel {
    private final int[][] left, right, splitIdx;
    private final float[][] splitCond;
    private final boolean[][] defaultLeft;
    private final int[][][] catSets; // [tree][node] -> sorted category codes going RIGHT (null if numeric split)
    private final double baseMargin;
    private final int numFeatures;

    private XgbTreeModel(JsonNode root) {
        JsonNode learner = root.get("learner");
        String objective = learner.get("objective").get("name").asText();
        if (!objective.equals("binary:logistic")) {
            throw new IllegalArgumentException("Unsupported objective: " + objective);
        }
        String bs = learner.get("learner_model_param").get("base_score").asText().replace("[", "").replace("]", "");
        double base = Double.parseDouble(bs);
        this.baseMargin = Math.log(base / (1 - base));
        this.numFeatures = Integer.parseInt(learner.get("learner_model_param").get("num_feature").asText());
        JsonNode trees = learner.get("gradient_booster").get("model").get("trees");
        int n = trees.size();
        left = new int[n][]; right = new int[n][]; splitIdx = new int[n][];
        splitCond = new float[n][]; defaultLeft = new boolean[n][]; catSets = new int[n][][];
        for (int t = 0; t < n; t++) {
            JsonNode tr = trees.get(t);
            left[t] = ints(tr.get("left_children"));
            right[t] = ints(tr.get("right_children"));
            splitIdx[t] = ints(tr.get("split_indices"));
            int m = left[t].length;
            splitCond[t] = new float[m];
            defaultLeft[t] = new boolean[m];
            for (int i = 0; i < m; i++) {
                splitCond[t][i] = (float) tr.get("split_conditions").get(i).asDouble();
                defaultLeft[t][i] = tr.get("default_left").get(i).asInt() == 1;
            }
            catSets[t] = new int[m][];
            int[] types = ints(tr.get("split_type"));
            int[] catNodes = ints(tr.get("categories_nodes"));
            int[] segs = ints(tr.get("categories_segments"));
            int[] sizes = ints(tr.get("categories_sizes"));
            int[] cats = ints(tr.get("categories"));
            for (int k = 0; k < catNodes.length; k++) {
                int node = catNodes[k];
                if (types[node] != 1) continue;
                int[] set = Arrays.copyOfRange(cats, segs[k], segs[k] + sizes[k]);
                Arrays.sort(set);
                catSets[t][node] = set;
            }
        }
    }

    private static int[] ints(JsonNode arr) {
        int[] out = new int[arr.size()];
        for (int i = 0; i < out.length; i++) out[i] = arr.get(i).asInt();
        return out;
    }

    public static XgbTreeModel load(InputStream in, ObjectMapper mapper) throws IOException {
        return new XgbTreeModel(mapper.readTree(in));
    }

    public int numTrees() { return left.length; }
    public int numFeatures() { return numFeatures; }

    public double margin(float[] x) {
        double sum = baseMargin;
        for (int t = 0; t < left.length; t++) {
            int[] l = left[t], r = right[t], si = splitIdx[t];
            float[] sc = splitCond[t];
            boolean[] dl = defaultLeft[t];
            int[][] cs = catSets[t];
            int node = 0;
            while (l[node] != -1) {
                float v = x[si[node]];
                if (Float.isNaN(v)) {
                    node = dl[node] ? l[node] : r[node];
                } else if (cs[node] != null) {
                    // XGBoost partition split: categories in the stored set go right.
                    node = Arrays.binarySearch(cs[node], (int) v) >= 0 ? r[node] : l[node];
                } else {
                    node = v < sc[node] ? l[node] : r[node];
                }
            }
            sum += sc[node]; // leaf value is stored in split_conditions
        }
        return sum;
    }

    public double predictProba(float[] x) {
        return 1.0 / (1.0 + Math.exp(-margin(x)));
    }
}
