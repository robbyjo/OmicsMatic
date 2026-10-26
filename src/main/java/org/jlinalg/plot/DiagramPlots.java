/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.plot;

import java.util.List;
import java.util.Map;

/** Factories for matrix, hierarchy, circular, parallel and flow diagrams. */
public final class DiagramPlots {
    private DiagramPlots() { }

    public enum ClusterMode { NONE, ROWS, COLUMNS, BOTH }
    public enum Orientation { VERTICAL, HORIZONTAL }

    /** Complete numeric grid in row-major order. */
    public record HeatmapData(List<String> columns, List<String> rows,
            double[][] values) { }

    /** One observation matrix for parallel coordinates or clustering. */
    public record ObservationData(List<String> labels, List<String> dimensions,
            double[][] values, List<String> groups) { }

    /** Positive-area item, optionally nested under a first-level group. */
    public record TreeItem(String label, String group, double value) { }

    /** Weighted graph or flow edge. Group controls edge coloring/bundling. */
    public record Edge(String source, String target, double weight, String group) { }

    /** Counts for an area-neutral two-set Venn diagram. */
    public record VennData(String leftLabel, String rightLabel, int leftOnly,
            int overlap, int rightOnly) {
        public int leftSize() { return leftOnly + overlap; }
        public int rightSize() { return rightOnly + overlap; }
    }

    /** Exclusive counts in binary-label order: 100,010,001,110,101,011,111. */
    public record ThreeSetVennData(List<String> labels,List<Long> counts) { }
    /** An exclusive intersection; false means outside that set, not untested. */
    public record SetIntersection(List<Boolean> included,long size) { public SetIntersection { included=List.copyOf(included); } }
    /** Set sizes refer to the complete analysis, even when only some intersections are displayed. */
    public record UpSetData(List<String> labels,List<Long> setSizes,List<SetIntersection> intersections,String caption) { }

    public static Plot heatmap(HeatmapData data, ClusterMode clustering,
            PlotOptions options) {
        return new Plot(new HeatmapChart(data, clustering, options));
    }

    public static Plot spider(List<String> axes, Map<String, double[]> series,
            PlotOptions options) {
        return new Plot(new SpiderChart(axes, series, options));
    }

    public static Plot circularBar(List<TreeItem> bars, PlotOptions options) {
        return new Plot(new CircularBarChart(bars, options));
    }

    public static Plot parallel(ObservationData data, PlotOptions options) {
        return new Plot(new ParallelChart(data, options));
    }

    public static Plot dendrogram(ObservationData data, Orientation orientation,
            PlotOptions options) {
        return new Plot(new DendrogramChart(data, orientation, options));
    }

    public static Plot treemap(List<TreeItem> items, PlotOptions options) {
        return new Plot(new TreemapChart(items, options));
    }

    public static Plot network(List<Edge> edges, PlotOptions options) {
        return new Plot(new NetworkDiagram(edges, NetworkDiagram.Mode.NETWORK, options));
    }

    public static Plot chord(List<Edge> edges, PlotOptions options) {
        return new Plot(new NetworkDiagram(edges, NetworkDiagram.Mode.CHORD, options));
    }

    public static Plot edgeBundle(List<Edge> edges, PlotOptions options) {
        return new Plot(new NetworkDiagram(edges, NetworkDiagram.Mode.EDGE_BUNDLE, options));
    }

    public static Plot sankey(List<Edge> edges, PlotOptions options) {
        return new Plot(new SankeyChart(edges, options));
    }

    public static Plot venn(VennData data, PlotOptions options) {
        return new Plot(new VennChart(data, options));
    }
    public static Plot venn(ThreeSetVennData data,PlotOptions options){return new Plot(new ThreeSetVennChart(data,options));}
    public static Plot upset(UpSetData data,PlotOptions options){return new Plot(new UpSetChart(data,options));}
}
