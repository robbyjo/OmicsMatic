/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Select supplied precomputed GENE2FUNC expression matrices in resource order.
 * Values are not transformed. Gene IDs must already be resolved to the exact
 * resource namespace; this is not the original symbol/Entrez conversion stage.
 */
public final class FumaGeneExpression {
    private FumaGeneExpression() { }
    /** Annotation order defines the first symbol for a repeated gene ID. */
    public record Gene(String id, String symbol) {
        public Gene { token(id);token(symbol); }
    }
    /** One immutable resource row; finite values are required without imputation. */
    public record InputRow(String gene, List<Double> values) {
        public InputRow {
            token(gene);values=List.copyOf(values);
            for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite expression value; supply a complete precomputed resource");
        }
    }
    /** Exact resource order and labels; no sorting, centering or log transform. */
    public record Matrix(List<String> labels, List<InputRow> rows) {
        public Matrix {
            labels=List.copyOf(labels);rows=List.copyOf(rows);
            if(labels.size()<2)throw new IllegalArgumentException("Original matrix subset requires at least two expression columns");
            var names=new HashSet<String>();for(String label:labels){token(label);if(!names.add(label))throw new IllegalArgumentException("Repeated expression column label");}
            var genes=new HashSet<String>();for(InputRow row:rows) {
                if(row.values.size()!=labels.size())throw new IllegalArgumentException("Expression matrix row width differs");
                if(!genes.add(row.gene))throw new IllegalArgumentException("Repeated expression resource gene ID");
            }
        }
    }
    /** Selected row and the first matching annotation symbol. */
    public record Row(String gene, String symbol, List<Double> values) {
        public Row { values=List.copyOf(values); }
    }
    /** Independent log2/norm resource populations; the source gate uses query entries. */
    public record Result(boolean sourceGateOpened, int queryEntriesInLog2,
                         List<String> log2Labels, List<String> normLabels,
                         List<Row> log2Rows, List<Row> normRows, boolean sameOrderedPopulation) {
        public Result {
            log2Labels=List.copyOf(log2Labels);normLabels=List.copyOf(normLabels);
            log2Rows=List.copyOf(log2Rows);normRows=List.copyOf(normRows);
        }
    }
    /** Complete input bounds; these are population limits, not a JVM heap quota. */
    public record Limits(int maximumGenes, int maximumLabels, long maximumValues) {
        public Limits {
            if(maximumGenes<1||maximumGenes>10000000||maximumLabels<2||maximumLabels>100000
                    ||maximumValues<1||maximumValues>100000000)throw new IllegalArgumentException("Invalid expression capacities");
        }
    }
    /**
     * Select both supplied matrices using the original log2 query-entry gate.
     * A closed gate yields no matrices. A duplicate query that opens the source
     * gate for one resource row is rejected: R would drop matrix dimensions.
     * @param query exact resolved IDs, including deliberate source multiplicity
     * @param annotation complete first-symbol lookup in supplied order
     * @param log2 supplied precomputed log2-labelled matrix
     * @param norm supplied independently precomputed normalized matrix
     * @param limits full input capacities
     * @param check cancellation, deadline or resource-identity callback
     * @return complete selected resource rows with their unchanged finite values
     */
    public static Result select(List<String> query, List<Gene> annotation, Matrix log2, Matrix norm,
                                Limits limits, FumaDegEnrichment.Check check) {
        if(query==null||annotation==null||log2==null||norm==null||limits==null||check==null)
            throw new IllegalArgumentException("Require expression inputs, limits and callback");
        if(query.isEmpty()||query.size()>limits.maximumGenes||annotation.size()>limits.maximumGenes)
            throw new IllegalArgumentException("Empty query or expression gene capacity exceeded");
        check.run();Set<String> selected=new HashSet<>();for(String gene:query){token(gene);selected.add(gene);}
        Map<String,String> symbols=new HashMap<>();for(Gene gene:annotation)symbols.putIfAbsent(gene.id,gene.symbol);
        validate(log2,limits,check);validate(norm,limits,check);
        long totalValues=Math.addExact(Math.multiplyExact((long)log2.rows.size(),log2.labels.size()),Math.multiplyExact((long)norm.rows.size(),norm.labels.size()));
        if(totalValues>limits.maximumValues)throw new IllegalArgumentException("Combined complete expression value capacity exceeded");
        var available=new HashSet<String>();for(InputRow row:log2.rows)available.add(row.gene);
        int hits=0;for(String gene:query)if(available.contains(gene))hits++;
        if(hits<2)return new Result(false,hits,log2.labels,norm.labels,List.of(),List.of(),false);
        List<Row> left=subset(log2,selected,symbols,check),right=subset(norm,selected,symbols,check);
        if(left.size()==1||right.size()==1)throw new IllegalArgumentException("Original expression subset would drop one-row matrix dimensions; correct query/resource coverage");
        boolean same=log2.labels.equals(norm.labels)&&left.stream().map(Row::gene).toList().equals(right.stream().map(Row::gene).toList());
        check.run();return new Result(true,hits,log2.labels,norm.labels,left,right,same);
    }
    private static List<Row> subset(Matrix matrix,Set<String> selected,Map<String,String> symbols,FumaDegEnrichment.Check check) {
        var rows=new ArrayList<Row>();for(InputRow row:matrix.rows) {
            check.run();if(selected.contains(row.gene)) {
                String symbol=symbols.get(row.gene);if(symbol==null)throw new IllegalArgumentException("Missing annotation symbol for selected expression gene: "+row.gene);
                rows.add(new Row(row.gene,symbol,row.values));
            }
        }return List.copyOf(rows);
    }
    private static void validate(Matrix matrix,Limits limits,FumaDegEnrichment.Check check) {
        check.run();if(matrix.rows.size()>limits.maximumGenes||matrix.labels.size()>limits.maximumLabels
                ||(long)matrix.rows.size()*matrix.labels.size()>limits.maximumValues)
            throw new IllegalArgumentException("Complete expression matrix capacity exceeded");
    }
    private static void token(String value) {
        if(value==null||value.isBlank()||value.length()>4096||value.codePoints().anyMatch(c->Character.isISOControl(c)||Character.isWhitespace(c)))
            throw new IllegalArgumentException("Require bounded expression IDs, symbols and labels without whitespace or controls");
    }
}
