/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.util.*;

/** Compare complete original AMPS RData statistics/exports with native source rules.
 * Numeric source NA, NaN, infinities and every finite double bit remain distinct.
 * This is software consistency evidence, never biological authentication.
 */
public final class HopsAssessmentAudit {
    private HopsAssessmentAudit() { }
    public static final Map<String,String> PACKAGES=Map.of("getopt","1.20.3","gridBase","0.4.7",
            "gridExtra","2.3","jsonlite","1.8.0","gtable","0.3.0");

    public static Map<String,Object> audit(HopsEvidence.Inspection nativeResult,Map<?,?> original,
            String originalTsv,Map<String,Map<String,List<Integer>>> originalJson) {
        require(original.keySet().equals(Set.of("schema","R","packages","samples","taxa","data","res",
                "red_res","table_columns","table_rows")),"Original RData export fields differ");
        same("HOPS035_RDATA_SCIENTIFIC_EXPORT_V1",original.get("schema"),"Original RData export schema differs");
        same("4.0.5",original.get("R"),"Original R version differs");
        same(PACKAGES,original.get("packages"),"Original R package versions differ");
        same(nativeResult.samples(),original.get("samples"),"Complete original sample identities/order differ");
        same(nativeResult.taxa(),original.get("taxa"),"Complete original taxon identities/order differ");
        var expectedRows=new ArrayList<Map<String,Object>>();
        for(var pair:nativeResult.pairs()) {
            var row=new LinkedHashMap<String,Object>();row.put("sample",pair.sample());row.put("taxon",pair.taxon());
            row.put("default",statistics(pair.defaults()));row.put("ancient",statistics(pair.ancient()));expectedRows.add(row);
        }
        same(expectedRows,original.get("data"),"Complete original RData numeric/node statistics differ from native source rules");
        same(matrix(nativeResult.fullMatrix()),original.get("res"),"Original full class matrix differs");
        same(matrix(nativeResult.reducedMatrix()),original.get("red_res"),"Original reduced class matrix differs");
        var retainedSamples=new ArrayList<String>();
        if(!nativeResult.reducedMatrix().isEmpty())retainedSamples.addAll(nativeResult.reducedMatrix().values().iterator().next().keySet());
        require(!retainedSamples.isEmpty(),"Original plotting has no nonempty reduced matrix; do not infer absence");
        var columns=new ArrayList<>(List.of("node"));var names=new HashSet<String>();
        for(String sample:retainedSamples) {
            require(sample.matches("[A-Za-z][A-Za-z0-9_.-]*"),"Original table column requires an unqualified R make.names case");
            String column=sample.replace('-','.');
            require(names.add(column),"Original R table column normalization creates an ambiguous identity");columns.add(column);
        }
        same(columns,original.get("table_columns"),"Original R table column transformation differs");
        var rows=new ArrayList<List<String>>();var json=new LinkedHashMap<String,Map<String,List<Integer>>>();
        for(int i=1;i<columns.size();i++)json.put(columns.get(i),new LinkedHashMap<>());
        var tsv=new StringBuilder("\"node\"");
        for(String column:columns.subList(1,columns.size()))tsv.append('\t').append('"').append(column).append('"');tsv.append('\n');
        for(var row:nativeResult.reducedMatrix().entrySet()) {
            require(row.getKey().matches("[A-Za-z0-9_.-]+"),"Original unquoted plot directory is unrepresentable safely");
            var cells=new ArrayList<>(List.of(row.getKey()));tsv.append('"').append(row.getKey()).append('"');
            for(int i=0;i<retainedSamples.size();i++) {
                int value=row.getValue().get(retainedSamples.get(i));cells.add(Integer.toString(value));tsv.append('\t').append(value);
                json.get(columns.get(i+1)).put(row.getKey(),List.of(value));
            }
            rows.add(cells);tsv.append('\n');
        }
        same(rows,original.get("table_rows"),"Complete original R table cells differ");
        same(tsv.toString(),originalTsv,"Original write.table TSV bytes differ");
        if(originalJson!=null)same(json,originalJson,"Original JSON orientation/array cells differ");
        return Map.of("status","pass-complete-original-amps-statistics-and-class-exports",
                "pairs",nativeResult.pairs().size(),"source_statistic_cells",Math.multiplyExact(14,nativeResult.pairs().size()),
                "samples",nativeResult.samples().size(),"taxa",nativeResult.taxa().size(),
                "json_checked",originalJson!=null,"expected_pdfs",expectedPdfs(nativeResult),
                "biological_result_admitted",false,"new_tool_credit",0);
    }
    public static List<String> expectedPdfs(HopsEvidence.Inspection result) {
        var paths=new ArrayList<>(List.of("heatmap_overview_Wevid.pdf"));
        for(var row:result.reducedMatrix().entrySet())for(var cell:row.getValue().entrySet())if(cell.getValue()>1)
            paths.add("pdf_candidate_profiles/"+row.getKey()+"/stp"+(cell.getValue()-1)+"_"+cell.getKey()+"_"+row.getKey()+"_summary.pdf");
        Collections.sort(paths);return List.copyOf(paths);
    }
    private static Map<String,Object> statistics(HopsEvidence.Statistics value) {
        var row=new LinkedHashMap<String,Object>();row.put("node",value.node());row.put("dr6",bits(value.dr6()));
        row.put("n6",bits(value.n6()==null?null:value.n6().doubleValue()));row.put("dr4",bits(value.dr4()));
        row.put("n4",bits(value.n4()==null?null:value.n4().doubleValue()));row.put("maximum_terminal_damage",bits(value.maximumTerminalDamage()));
        row.put("unique_per_reference",bits(value.uniquePerReference()));return row;
    }
    public static String bits(Double value) {
        if(value==null)return "NA";if(Double.isNaN(value))return "NaN";
        if(value==Double.POSITIVE_INFINITY)return "Inf";if(value==Double.NEGATIVE_INFINITY)return "-Inf";
        return String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(value));
    }
    private static Map<String,Map<String,String>> matrix(Map<String,Map<String,Integer>> source) {
        var out=new LinkedHashMap<String,Map<String,String>>();source.forEach((name,row)->{
            var cells=new LinkedHashMap<String,String>();row.forEach((sample,value)->cells.put(sample,Integer.toString(value)));out.put(name,cells);});return out;
    }
    private static void same(Object expected,Object actual,String cause) {require(Objects.equals(expected,actual),cause);}
    private static void require(boolean condition,String cause) {if(!condition)throw new IllegalArgumentException(cause);}
}
