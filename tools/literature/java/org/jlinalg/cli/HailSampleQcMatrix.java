/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.HailSampleQc;

/** Development-only complete matrix driver, loaded from the actual candidate JAR. */
public final class HailSampleQcMatrix {
    private HailSampleQcMatrix() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Require fixture JSON, NEW output directory and expected candidate JAR");
        Path expected=Path.of(args[2]).toRealPath();
        for(Class<?> type:List.of(HailSampleQcMatrix.class,HailSampleQc.class)){
            Path actual=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!actual.equals(expected))throw new IllegalStateException("Matrix driver and native implementation must load from the exact candidate: "+actual);
        }
        Path source=Path.of(args[0]).toAbsolutePath().normalize(),out=Path.of(args[1]).toAbsolutePath().normalize();
        Map<?,?> root=object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(source,16777216)),16777216,2000000));
        var cases=new LinkedHashMap<String,Object>();
        for(Object item:list(root.get("cases"))){
            Map<?,?> c=object(item);String id=text(c.get("id"));if(!id.matches("[a-zA-Z0-9-]+")||cases.containsKey(id))throw new IOException("Invalid/duplicate case identifier");
            boolean dp=text(c.get("depth_type")).equals("int32"),gq=text(c.get("gq_type")).equals("int32");var samples=new ArrayList<String>();for(Object s:list(c.get("samples")))samples.add(text(s));var rows=new ArrayList<HailSampleQc.Row>();
            for(Object r:list(c.get("rows"))){
                Map<?,?> row=object(r);var alleles=new ArrayList<String>();for(Object a:list(row.get("alleles")))alleles.add(text(a));var entries=new ArrayList<HailSampleQc.Entry>();
                for(Object e:list(row.get("entries"))){
                    Map<?,?> entry=object(e);HailSampleQc.Call call=null;
                    if(entry.get("call")!=null){Map<?,?> raw=object(entry.get("call"));var genotype=new ArrayList<Integer>();for(Object a:list(raw.get("alleles")))genotype.add(integer(a));call=new HailSampleQc.Call(genotype,bool(raw.get("phased")));}
                    entries.add(new HailSampleQc.Entry(call,dp?optionalInteger(entry.get("depth")):null,gq?optionalInteger(entry.get("genotype_quality")):null,bool(entry.get("filtered"))));
                }
                rows.add(new HailSampleQc.Row(alleles,entries));
            }
            var records=new ArrayList<Object>();for(var sample:HailSampleQc.compute(samples,new HailSampleQc.Schema(dp,gq),rows,HailSampleQc.Limits.defaults()))records.add(record(sample));cases.put(id,records);
        }
        var types=new ArrayList<Object>();for(Object p:list(root.get("allele_pairs"))){List<?> pair=list(p);if(pair.size()!=2)throw new IOException("Allele pair must have two strings");String ref=text(pair.get(0)),alt=text(pair.get(1));types.add(Map.of("ref",ref,"alt",alt,"allele_type",HailSampleQc.alleleType(ref,alt).name(),"qc_allele_type",HailSampleQc.qcAlleleType(ref,alt).name()));}
        var rejected=new ArrayList<String>();
        rejects("negative-call-index",()->new HailSampleQc.Call(List.of(-1),false),rejected);
        rejects("zero-ploidy-outside-native-profile",()->new HailSampleQc.Call(List.of(),false),rejected);
        rejects("triploid-outside-native-profile",()->new HailSampleQc.Call(List.of(0,1,1),false),rejected);
        var entry=new HailSampleQc.Entry(new HailSampleQc.Call(List.of(0,1),false),10,20,false);var row=new HailSampleQc.Row(List.of("A","G"),List.of(entry));var schema=new HailSampleQc.Schema(true,true);
        rejects("late-ragged-row-no-partial-return",()->HailSampleQc.compute(List.of("sample"),schema,List.of(row,new HailSampleQc.Row(List.of("A","G"),List.of())),HailSampleQc.Limits.defaults()),rejected);
        rejects("late-allele-out-of-range-no-partial-return",()->HailSampleQc.compute(List.of("sample"),schema,List.of(row,new HailSampleQc.Row(List.of("A"),List.of(entry))),HailSampleQc.Limits.defaults()),rejected);
        rejects("complete-sample-capacity",()->HailSampleQc.compute(List.of("A","B"),schema,List.of(),new HailSampleQc.Limits(1,10,10)),rejected);
        rejects("complete-row-capacity",()->HailSampleQc.compute(List.of("A"),schema,List.of(row,row),new HailSampleQc.Limits(10,1,10)),rejected);
        rejects("complete-entry-capacity",()->HailSampleQc.compute(List.of("A"),schema,List.of(row,row),new HailSampleQc.Limits(10,10,1)),rejected);
        rejects("invalid-capacity",()->new HailSampleQc.Limits(0,1,1),rejected);
        Files.createDirectory(out);
        for(var c:cases.entrySet())NextflowFiles.writeJson(out.resolve(c.getKey()+".json"),c.getValue());
        NextflowFiles.writeJson(out.resolve("allele-types.json"),types);NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","PASS_COMPLETE_NATIVE_SAMPLE_QC_CANDIDATE_ONLY","candidate",expected.toString(),"cases",cases.size(),"allele_pairs",types.size(),"expected_native_rejections",rejected,"implementation_credit",false,"whole_goal_complete",false));
        System.out.println("Complete native QC outputs: "+cases.size()+"; allele pairs: "+types.size()+"; native profile rejections: "+rejected.size());
    }
    private static Map<String,Object> record(HailSampleQc.Sample s){
        var m=new LinkedHashMap<String,Object>();m.put("call_rate",floating(s.callRate()));m.put("n_called",s.nCalled());m.put("n_not_called",s.nNotCalled());m.put("n_filtered",s.nFiltered());m.put("n_hom_ref",s.nHomRef());m.put("n_het",s.nHet());m.put("n_hom_var",s.nHomVar());m.put("n_non_ref",s.nNonRef());m.put("n_singleton",s.nSingleton());m.put("n_snp",s.nSnp());m.put("n_insertion",s.nInsertion());m.put("n_deletion",s.nDeletion());m.put("n_transition",s.nTransition());m.put("n_transversion",s.nTransversion());m.put("n_star",s.nStar());m.put("r_ti_tv",floating(s.transitionTransversion()));m.put("r_het_hom_var",floating(s.hetHomVar()));m.put("r_insertion_deletion",floating(s.insertionDeletion()));if(s.depth()!=null)m.put("dp_stats",stats(s.depth()));if(s.genotypeQuality()!=null)m.put("gq_stats",stats(s.genotypeQuality()));return Map.of("sample",s.id(),"ordinal",s.ordinal(),"metrics",m);
    }
    private static Map<String,Object> stats(HailSampleQc.Stats s){return Map.of("mean",floating(s.mean()),"stdev",floating(s.stdev()),"min",floating(s.min()),"max",floating(s.max()));}
    private static Map<String,String> floating(Double value){if(value==null)return Map.of("state","missing");String state=Double.isNaN(value)?"NaN":value==Double.POSITIVE_INFINITY?"+Inf":value==Double.NEGATIVE_INFINITY?"-Inf":"value";return Map.of("state",state,"text",state.equals("value")?Double.toString(value):state);}
    private static Map<?,?> object(Object value)throws IOException{return NextflowFiles.object(value);}
    private static List<?> list(Object value)throws IOException{return NextflowFiles.list(value);}
    private static String text(Object value)throws IOException{return NextflowFiles.text(value);}
    private static int integer(Object value)throws IOException{return (int)NextflowFiles.integer(value,Integer.MIN_VALUE,Integer.MAX_VALUE);}
    private static Integer optionalInteger(Object value)throws IOException{return value==null?null:integer(value);}
    private static boolean bool(Object value)throws IOException{if(!(value instanceof Boolean b))throw new IOException("Require explicit boolean");return b;}
    private static void rejects(String name,Runnable action,List<String> result){try{action.run();}catch(IllegalArgumentException expected){result.add(name);return;}throw new AssertionError("Native profile must reject "+name);}
}
