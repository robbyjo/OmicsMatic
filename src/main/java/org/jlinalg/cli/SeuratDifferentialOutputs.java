/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.SeuratMarkerSummary;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SeuratDifferentialInputs.*;

/** Native preparation/summary/correction audit; source p values are not independently refitted. */
final class SeuratDifferentialOutputs {
    private SeuratDifferentialOutputs() { }
    static final String HEADER="contrast\tfeature\tp_value\tfold_change\tpct_1\tpct_2\tp_adj";
    record Row(String group,int feature,String p,String fc,String a,String b,String adjusted) { }
    static List<String> publish(Path root,Map<String,Object> r,Prepared prepared,boolean compare)throws IOException {
        Path worker=root.resolve("worker");var report=read(worker.resolve("worker-report.json"),64_000_000);
        if(!"completed-with-warnings".equals(report.get("status"))||!PROFILE.equals(report.get("profile"))||!r.get("test").equals(report.get("test"))||!r.get("mode").equals(report.get("mode"))||!"data".equals(report.get("matrix_filter_slot"))||!Boolean.FALSE.equals(report.get("installed_DLL_source_authentication"))||!Boolean.FALSE.equals(report.get("independent_native_statistical_replay")))throw new IOException("Incomplete/mismatched original marker worker report");
        var sourceProfile=read(root.resolve("source/reference/SOURCE-PROFILE.json"));var versions=object(report.get("versions"),"versions");if(!same(versions,object(sourceProfile.get("required_packages"),"required packages")))throw new IOException("Recorded runtime versions differ from the admitted profile");
        var guards=object(report.get("source_guards"),"source guards");if(!guards.keySet().equals(new HashSet<>(strings(sourceProfile.get("functions"),"source functions",42,42)))||guards.values().stream().anyMatch(v->!Boolean.TRUE.equals(v)))throw new IOException("Recorded source guards are incomplete");
        if(!list(report.get("RNG_kind"),"RNG kind").equals(List.of("Mersenne-Twister","Inversion","Rejection")))throw new IOException("Unadmitted RNG kind");for(String key:List.of("RNG_before","RNG_after")){var values=list(report.get(key),key);if(values.size()!=626)throw new IOException("Incomplete recorded source RNG state");for(Object v:values)if(!"NA".equals(v))integer(Map.of("x",v),"x",0,Integer.MIN_VALUE,Integer.MAX_VALUE);}
        Map<String,Object> failures=objectOrEmpty(report.get("source_contrast_failures"));var groupsBy=new LinkedHashMap<String,Group>();for(var g:prepared.groups())groupsBy.put(g.code(),g);if(!groupsBy.keySet().containsAll(failures.keySet())||failures.values().stream().anyMatch(v->!(v instanceof String s)||s.isBlank()))throw new IOException("Unknown/incomplete failed comparison records");
        if(r.get("mode").equals("PAIR")&&!failures.isEmpty())throw new IOException("A failed pair comparison cannot be admitted");if(!Objects.equals(report.get("complete_comparison_set"),failures.isEmpty()))throw new IOException("Source comparison-completeness flag differs");
        var recordedGroups=object(report.get("groups"),"groups");if(!recordedGroups.keySet().equals(groupsBy.keySet()))throw new IOException("Recorded source populations differ");
        try(var preview=reader(worker.resolve("sampling-rule-preview.tsv"))) {
            if(!"contrast\tside\tcell".equals(line(preview)))throw new IOException("Invalid sampling-rule preview header");
            long cap=r.get("max_cells_per_group").equals("Inf")?Long.MAX_VALUE:((Number)r.get("max_cells_per_group")).longValue();
            for(var g:prepared.groups()) {
                var capture=object(recordedGroups.get(g.code()),"group capture");boolean sampled=g.numerator().length>cap||g.denominator().length>cap;
                if(!Objects.equals(capture.get("sampling_rule_preview_written"),sampled))throw new IOException("Source-rule preview presence differs");
                for(String side:List.of("numerator","denominator")) {
                    int[] full=side.equals("numerator")?g.numerator():g.denominator();int expected=(int)Math.min(full.length,cap);
                    if(((Number)capture.get(side+"_count")).intValue()!=full.length||((Number)capture.get("source_rule_sampled_"+side+"_count")).intValue()!=expected)throw new IOException("Source population sizes differ");
                    if(sampled){var seen=new BitSet(prepared.counts().cells().size());for(int i=0;i<expected;i++) {
                        String[] row=fields(line(preview));if(row.length!=3||!row[0].equals(g.code())||!row[1].equals(side)||!row[2].matches("c[1-9][0-9]*"))throw new IOException("Malformed source-rule sampled population");
                        int c;try{c=Integer.parseInt(row[2].substring(1))-1;}catch(NumberFormatException e){throw new IOException("Sampled cell index exceeds capacity",e);}
                        if(c<0||c>=prepared.counts().cells().size()||Arrays.binarySearch(full,c)<0||seen.get(c))throw new IOException("Source-rule preview cells are not a unique subset of the declared group");seen.set(c);
                    }}
                }
            }
            if(line(preview)!=null)throw new IOException("Extra sampled population preview rows");
        }
        int limit=((Number)r.get("maximum_result_rows")).intValue();var raw=rows(worker.resolve("raw-group-markers.tsv"),prepared,limit);var result=rows(worker.resolve("markers.tsv"),prepared,limit);
        if(((Number)report.get("source_returned_rows")).intValue()!=result.size())throw new IOException("Source result count differs");
        var layer=prepared.foldLayer(text(r,"fc_slot"));var matrix=layer.summaryMatrix();var options=new SeuratMarkerSummary.Options(SeuratMarkerSummary.Profile.V5_6_0,slot(text(r,"fc_slot")),normalization(text(r,"fc_normalization")),((Number)r.get("pseudocount")).doubleValue(),((Number)r.get("log_base")).doubleValue());
        var filters=new SeuratMarkerSummary.Filters(((Number)r.get("min_pct")).doubleValue(),r.get("min_diff_pct").equals("-Inf")?Double.NEGATIVE_INFINITY:((Number)r.get("min_diff_pct")).doubleValue(),((Number)r.get("logfc_threshold")).doubleValue(),Boolean.TRUE.equals(r.get("only_positive")));
        List<String> warnings=new ArrayList<>();List<Row> reconstructed=new ArrayList<>();int summaryRows=0;
        try(var summaries=reader(worker.resolve("summaries.tsv"))) {
            if(!"contrast\tfeature\tfold_change\tpct_1\tpct_2".equals(line(summaries)))throw new IOException("Invalid source summary header");
            for(var g:prepared.groups()) {
                var nativeSummary=SeuratMarkerSummary.summarize(matrix,g.numerator(),g.denominator(),options,((Number)r.get("maximum_work")).longValue(),()->Thread.currentThread().isInterrupted());for(String warning:nativeSummary.warnings())warnings.add("NATIVE_SUMMARY_WARNING ["+g.label()+"]: "+warning);
                for(var feature:nativeSummary.features()) {
                    String[] row=fields(line(summaries));if(row.length!=5||!row[0].equals(g.code())||!row[1].equals("f"+(feature.originalIndex()+1)))throw new IOException("Source summary identities/order differ");
                    audit(feature.foldChange(),row[2],"source fold change");audit(feature.fraction1(),row[3],"source detection fraction");audit(feature.fraction2(),row[4],"source detection fraction");summaryRows++;
                }
                var groupRaw=raw.stream().filter(x->x.group.equals(g.code())).toList();
                if(failures.containsKey(g.code())){if(!groupRaw.isEmpty()||result.stream().anyMatch(x->x.group.equals(g.code())))throw new IOException("Failed comparison has admitted marker rows");continue;}
                var eligible=new HashSet<Integer>();for(var f:SeuratMarkerSummary.prefilter(nativeSummary,filters,SeuratMarkerSummary.Slot.DATA))if(!filters.onlyPositive()||f.foldChange()>0)eligible.add(f.originalIndex());
                var expected=prepared.selected().stream().filter(eligible::contains).toList();var by=new HashMap<Integer,Row>();for(var row:groupRaw)if(by.put(row.feature,row)!=null)throw new IOException("Repeated marker feature within comparison");if(!by.keySet().equals(new HashSet<>(expected)))throw new IOException("Source marker population differs from native full-group filters/selection");
                var tested=expected.stream().map(i->nativeSummary.features().get(i)).toList();double[] probabilities=new double[tested.size()];for(int i=0;i<tested.size();i++)probabilities[i]=probability(by.get(tested.get(i).originalIndex()).p);
                var finished=SeuratMarkerSummary.finish(nativeSummary,tested,probabilities,filters.onlyPositive());if(finished.size()!=groupRaw.size())throw new IOException("Post-test source population differs");
                for(int i=0;i<finished.size();i++) {
                    var f=finished.get(i);var row=groupRaw.get(i);if(row.feature!=f.feature().originalIndex())throw new IOException("Source marker order differs from native p/detection ordering");
                    audit(f.feature().foldChange(),row.fc,"marker fold change");audit(f.feature().fraction1(),row.a,"marker detection");audit(f.feature().fraction2(),row.b,"marker detection");
                    if(row.p.equals("NA")||row.p.equals("NaN")){if(!row.p.equals(row.adjusted))throw new IOException("Source adjusted-probability missing kind changed");}else audit(f.adjustedP(),row.adjusted,"full-assay Bonferroni correction");
                    if(r.get("mode").equals("PAIR")||probability(row.p)<((Number)r.get("return_threshold")).doubleValue())reconstructed.add(row);
                }
            }
            if(line(summaries)!=null)throw new IOException("Extra source summaries");
        }
        if(!reconstructed.equals(result))throw new IOException("Final original marker result differs from native per-group processing/raw-p return rule");
        for(Object warning:list(report.get("warnings"),"source warnings")){if(!(warning instanceof String s))throw new IOException("Malformed source warning");warnings.add("ORIGINAL_SEURAT_WARNING: "+s);}
        for(var e:failures.entrySet())warnings.add("SOURCE_COMPARISON_NOT_TESTED: "+groupsBy.get(e.getKey()).label()+": "+e.getValue()+". The source returned a partial comparison set; absence is not a negative result. Correct the named design/group cause and rerun into NEW paths.");
        warnings.add("VERIFY_SCOPE: native preparation, fold summaries, selection, full-assay correction and ordering are audited. Source statistical p values and seeded sampling are not independently refitted/replayed in Java. Coordinated fabricated worker/published values with recomputed hashes can pass structural checks; hashes do not authenticate execution or biology.");
        try(var out=new Lines(root.resolve("markers.tsv"),compare)){out.line(HEADER);for(var row:result)out.line(groupsBy.get(row.group).label()+"\t"+prepared.counts().features().get(row.feature)+"\t"+String.join("\t",row.p,row.fc,row.a,row.b,row.adjusted));}
        var diagnostics=new LinkedHashMap<String,Object>();diagnostics.put("native_summary_rows_checked",summaryRows);diagnostics.put("raw_group_rows_checked",raw.size());diagnostics.put("published_rows",result.size());diagnostics.put("original_failed_comparisons",failures);diagnostics.put("complete_comparison_set",failures.isEmpty());diagnostics.put("native_statistical_p_value_replay",false);diagnostics.put("native_sampling_replay",false);diagnostics.put("source_guard_count",guards.size());diagnostics.put("warnings",warnings);
        if(compare){if(!same(diagnostics,read(root.resolve("diagnostics.json"),64_000_000)))throw new IOException("Native diagnostics differ");}else json(root.resolve("diagnostics.json"),diagnostics);
        return List.copyOf(warnings);
    }
    private static List<Row> rows(Path path,Prepared p,int limit)throws IOException {
        List<Row> result=new ArrayList<>();Set<String> groups=new HashSet<>();for(var g:p.groups())groups.add(g.code());
        try(var in=reader(path)){if(!HEADER.equals(line(in)))throw new IOException("Invalid marker protocol header");String raw;while((raw=line(in))!=null){check();if(result.size()>=limit)throw new IOException("Marker row capacity exceeded");String[] f=fields(raw);if(f.length!=7||!groups.contains(f[0])||!f[1].matches("f[1-9][0-9]*"))throw new IOException("Marker contains unknown/missing identities; no repair or placeholder admission");int index;try{index=Integer.parseInt(f[1].substring(1))-1;}catch(NumberFormatException e){throw new IOException("Marker index exceeds capacity",e);}if(index>=p.counts().features().size())throw new IOException("Unknown marker feature");probability(f[2]);probability(f[6]);extended(f[3]);for(int i:List.of(4,5)){double v=extended(f[i]);if(!Double.isFinite(v)||v<0||v>1)throw new IOException("Invalid marker detection fraction");}result.add(new Row(f[0],index,f[2],f[3],f[4],f[5],f[6]));}}return List.copyOf(result);
    }
    private static Map<String,Object> objectOrEmpty(Object v)throws IOException {if(v instanceof List<?> l&&l.isEmpty())return Map.of();return object(v,"failed comparisons");}
    private static SeuratMarkerSummary.Slot slot(String s){return s.equals("counts")?SeuratMarkerSummary.Slot.COUNTS:s.equals("scale.data")?SeuratMarkerSummary.Slot.SCALE_DATA:SeuratMarkerSummary.Slot.DATA;}
    private static SeuratMarkerSummary.Normalization normalization(String s){return s.equals("LogNormalize")?SeuratMarkerSummary.Normalization.LOG_NORMALIZE:s.equals("other")?SeuratMarkerSummary.Normalization.OTHER:SeuratMarkerSummary.Normalization.UNRECORDED;}
    static double extended(String s)throws IOException {return switch(s){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->numeric(s);};}
    private static double probability(String s)throws IOException {double p=extended(s);if(!Double.isNaN(p)&&(!Double.isFinite(p)||p<0||p>1))throw new IOException("Invalid original probability");return p;}
    private static void audit(double expected,String token,String name)throws IOException {
        if(Double.isNaN(expected)){if(!token.equals("NaN"))throw new IOException(name+" missing/NaN kind differs");return;}
        double actual=extended(token);if(!Double.isFinite(expected)){if(actual!=expected)throw new IOException(name+" infinity differs");return;}
        if(!Double.isFinite(actual)||expected==0&&actual!=0||Math.abs(actual-expected)>Math.max(3e-12,Math.abs(expected)*3e-12))throw new IOException(name+" differs from native source arithmetic");
    }
}
