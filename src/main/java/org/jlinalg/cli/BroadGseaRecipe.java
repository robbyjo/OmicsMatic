/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.enrichment.BroadGseaEngine;
import org.jlinalg.enrichment.BroadGseaStatistics;

/** Explicit prepared-data contract; numeric input lexemes are never routed through double. */
final class BroadGseaRecipe {
    private BroadGseaRecipe() { }
    static final String ENGINE = "native-broad-gsea-source-core";
    static final String SCOPE = "Native Broad GSEA prepared-data ranking, permutations and meandiv statistics only. No vendor file ingestion, expression transformation, identifier mapping, annotation/chip collapse or marker/GUI reports. No whole-paper reproduction.";
    static final String ACTION = "SKIP3: supply the named exact permitted input or executable prerequisite, or retain the dependent analysis as skipped. FAIL2: NO COMPLETE RESULT; preserve inputs, partials and diagnostics, correct the named data/design/integrity/numerical/capacity/process cause and retry NEW output. Never silently repair identifiers or nonfinite values, change editions or substitute algorithms.";
    static final int RECIPE_BYTES = 4_000_000;
    record Caps(long inputBytes, long outputBytes, int files, long seconds, int heapMiB,
                long denseCells, long nullCells, long work, int permutations) {
        Map<String,Object> json() {
            return Map.of("maximum_input_bytes",inputBytes,"maximum_output_bytes",outputBytes,
                "maximum_files",files,"timeout_seconds",seconds,"heap_mib",heapMiB,
                "maximum_dense_cells",denseCells,"maximum_null_cells",nullCells,
                "maximum_work_per_analysis",work,"maximum_permutations",permutations);
        }
        BroadGseaEngine.Limits engine() { return new BroadGseaEngine.Limits(denseCells,nullCells,work,permutations); }
    }
    record Analysis(String id, String input, String form, BroadGseaEngine.Settings settings,
                    BroadGseaEngine.RankingOptions ranking, BroadGseaEngine.Randomization randomization,
                    Map<String,Object> raw) { }
    record Run(Map<String,Object> raw, Caps caps, List<Analysis> analyses) { }
    record Prepared(String[] genes, float[] scores, float[][] expression, BroadGseaEngine.Grouping grouping,
                    List<BroadGseaEngine.GeneSet> sets) { }

    static Map<String,Object> object(Object value, String name) throws IOException {
        if (!(value instanceof Map<?,?> map)) throw new IOException(name+" must be an object");
        var result=new LinkedHashMap<String,Object>();
        for(var e:map.entrySet()) { if(!(e.getKey() instanceof String key))throw new IOException("Non-string key");result.put(key,e.getValue()); }
        return result;
    }
    static List<?> list(Object value,String name) throws IOException {
        if(!(value instanceof List<?> result))throw new IOException(name+" must be an array");return result;
    }
    static void keys(Map<String,Object> value, Set<String> required, Set<String> optional) throws IOException {
        var allowed=new HashSet<>(required);allowed.addAll(optional);
        if(!value.keySet().containsAll(required)||!allowed.containsAll(value.keySet()))
            throw new IOException("Schema keys differ; require "+required+", optional "+optional+", received "+value.keySet());
    }
    static String string(Object value,String name) throws IOException {
        if(!(value instanceof String text)||text.isEmpty())throw new IOException(name+" must be nonempty literal text");
        NextflowParameters.unicode(text);return text;
    }
    static String text(Map<String,Object> value,String name) throws IOException {return string(value.get(name),name);}
    static boolean bool(Map<String,Object> value,String name) throws IOException {
        if(!(value.get(name) instanceof Boolean b))throw new IOException(name+" must be boolean");return b;
    }
    static long integer(Object value,String name,long low,long high) throws IOException {
        try {
            String lexeme=value instanceof NextflowParameters.NumberToken n?n.text():value instanceof Number n?n.toString():"";
            if(lexeme.isEmpty())throw new NumberFormatException();long result=new BigDecimal(lexeme).longValueExact();
            if(result<low||result>high)throw new NumberFormatException();return result;
        } catch(ArithmeticException|NumberFormatException error) {throw new IOException(name+" must be an exact integer in ["+low+","+high+"]");}
    }
    static long integer(Map<String,Object> value,String name,long low,long high) throws IOException {return integer(value.get(name),name,low,high);}
    static float f32(Object value,String name) throws IOException {
        if(!(value instanceof NextflowParameters.NumberToken token))throw new IOException(name+" must be a JSON number, not text/null");
        float result;
        try {result=Float.parseFloat(token.text());}catch(NumberFormatException error){throw new IOException("Invalid float32 "+name,error);}
        if(!Float.isFinite(result))throw new IOException("Nonfinite float32 at "+name+"; no replacement is allowed");return result;
    }
    static <E extends Enum<E>> E choice(Class<E> type,String text) throws IOException {
        try{return Enum.valueOf(type,text);}catch(IllegalArgumentException error){throw new IOException("Unsupported explicit "+type.getSimpleName()+": "+text,error);}
    }
    static Caps caps(Object source) throws IOException {
        var m=object(source,"limits");keys(m,Set.of("maximum_input_bytes","maximum_output_bytes","maximum_files","timeout_seconds","heap_mib","maximum_dense_cells","maximum_null_cells","maximum_work_per_analysis","maximum_permutations"),Set.of());
        return new Caps(integer(m,"maximum_input_bytes",1,256_000_000),integer(m,"maximum_output_bytes",1,2_000_000_000L),
            (int)integer(m,"maximum_files",1,10000),integer(m,"timeout_seconds",1,604800),(int)integer(m,"heap_mib",64,65536),
            integer(m,"maximum_dense_cells",1,100_000_000),integer(m,"maximum_null_cells",1,100_000_000),
            integer(m,"maximum_work_per_analysis",1,10_000_000_000_000L),(int)integer(m,"maximum_permutations",1,1_000_000));
    }
    static Run run(Map<String,Object> raw) throws IOException {
        keys(raw,Set.of("schema_version","mode","engine","out","limits","analyses"),Set.of());
        if(integer(raw,"schema_version",1,1)!=1||!text(raw,"mode").equals("RUN")||!text(raw,"engine").equals(ENGINE))throw new IOException("Require schema_version 1, mode RUN and engine "+ENGINE);
        text(raw,"out");Caps caps=caps(raw.get("limits"));List<?> cases=list(raw.get("analyses"),"analyses");
        if(cases.isEmpty()||cases.size()>64)throw new IOException("Declare 1 through 64 analyses");
        var ids=new HashSet<String>();var analyses=new ArrayList<Analysis>();
        for(Object item:cases){var a=object(item,"analysis");String form=text(a,"form");
            if(!Set.of("PRERANKED","EXPRESSION_GENE_SET","EXPRESSION_PHENOTYPE").contains(form))throw new IOException("Unknown analysis form: "+form);
            var required=new HashSet<>(Set.of("id","input","form","edition","normalization","permutations","seed","minimum_size","maximum_size","weight"));
            if(!form.equals("PRERANKED"))required.add("ranking");if(form.equals("EXPRESSION_PHENOTYPE"))required.add("randomization");keys(a,required,Set.of());
            String id=text(a,"id");if(!ids.add(id))throw new IOException("Duplicate literal analysis id "+id);
            String edition=text(a,"edition");if(!Set.of("4.3.2","4.3.3","4.4.0").contains(edition))throw new BroadGseaFiles.Unavailable("Unsupported exact GSEA source edition "+edition+"; supported native profiles are 4.3.2, 4.3.3, 4.4.0");
            if(!text(a,"normalization").equals("meandiv"))throw new IOException("Only explicit meandiv normalization is implemented");
            long seed;String seedText=text(a,"seed");
            try{seed=Long.parseLong(seedText);if(!Long.toString(seed).equals(seedText))throw new NumberFormatException();}
            catch(NumberFormatException error){throw new IOException("seed must be a canonical signed 64-bit decimal STRING",error);}
            int minimum=(int)integer(a,"minimum_size",0,Integer.MAX_VALUE),maximum=(int)integer(a,"maximum_size",minimum,Integer.MAX_VALUE);
            var settings=new BroadGseaEngine.Settings(edition,(int)integer(a,"permutations",1,caps.permutations),seed,minimum,maximum,
                choice(BroadGseaStatistics.Weight.class,text(a,"weight")),caps.engine());
            BroadGseaEngine.RankingOptions ranking=null;BroadGseaEngine.Randomization randomization=null;
            if(!form.equals("PRERANKED")){
                var r=object(a.get("ranking"),"ranking");keys(r,Set.of("metric","median","biased_variance","fix_low_variance","absolute_sort","ascending","class_of_interest"),Set.of());
                ranking=new BroadGseaEngine.RankingOptions(choice(BroadGseaEngine.Metric.class,text(r,"metric")),bool(r,"median"),bool(r,"biased_variance"),bool(r,"fix_low_variance"),bool(r,"absolute_sort"),bool(r,"ascending"),(int)integer(r,"class_of_interest",0,1));
            }
            if(form.equals("EXPRESSION_PHENOTYPE"))randomization=choice(BroadGseaEngine.Randomization.class,text(a,"randomization"));
            analyses.add(new Analysis(id,text(a,"input"),form,settings,ranking,randomization,Collections.unmodifiableMap(a)));
        }
        return new Run(Collections.unmodifiableMap(raw),caps,List.copyOf(analyses));
    }
    static Map<String,Object> effective(Run run) {
        var result=new LinkedHashMap<String,Object>(run.raw);result.put("out",".");var analyses=new ArrayList<Object>();
        for(int i=0;i<run.analyses.size();i++){var a=new LinkedHashMap<String,Object>(run.analyses.get(i).raw);a.put("input",inputName(i));analyses.add(a);}
        result.put("analyses",analyses);return result;
    }
    static String inputName(int index){return String.format(Locale.ROOT,"inputs/case-%04d.json",index);}
    static String outputName(int index){return String.format(Locale.ROOT,"results/case-%04d",index);}
    static Prepared prepared(byte[] bytes,Analysis a,Caps caps) throws IOException {
        var m=object(BroadGseaFiles.parse(bytes),"prepared input");
        var required=new HashSet<>(Set.of("schema_version","genes","gene_sets","provenance"));
        if(a.form.equals("PRERANKED"))required.add("ranking_scores");else required.addAll(Set.of("expression","samples","class0","class1"));keys(m,required,Set.of());integer(m,"schema_version",1,1);
        var provenance=object(m.get("provenance"),"provenance");keys(provenance,Set.of("organism","identifier_namespace","expression_units","universe_description","gene_set_source","preparation_note"),Set.of());
        for(String key:provenance.keySet())text(provenance,key);
        String[] genes=identities(m.get("genes"),"genes");if(genes.length<2)throw new IOException("Require at least two prepared genes");
        var sets=new ArrayList<BroadGseaEngine.GeneSet>();var names=new HashSet<String>();
        for(Object item:list(m.get("gene_sets"),"gene_sets")){
            var set=object(item,"gene set");keys(set,Set.of("name","members"),Set.of());String name=text(set,"name");
            if(!names.add(name))throw new IOException("Duplicate literal set name "+name);
            sets.add(new BroadGseaEngine.GeneSet(name,Arrays.asList(identities(set.get("members"),"set "+name+" members"))));
        }
        if(sets.isEmpty())throw new IOException("No supplied gene sets");
        float[] scores=null;float[][] matrix=null;BroadGseaEngine.Grouping grouping=null;
        if(a.form.equals("PRERANKED")){
            List<?> values=list(m.get("ranking_scores"),"ranking_scores");if(values.size()!=genes.length)throw new IOException("Prepared ranking dimensions differ");
            if(genes.length>caps.denseCells)throw new IOException("Preranked input exceeds maximum_dense_cells");
            scores=new float[genes.length];for(int i=0;i<scores.length;i++)scores[i]=f32(values.get(i),"ranking_scores["+i+"]");
        }else{
            String[] samples=identities(m.get("samples"),"samples");List<?> rows=list(m.get("expression"),"expression");
            if(samples.length==0||rows.size()!=genes.length||(long)samples.length*genes.length>caps.denseCells)throw new IOException("Expression dimensions or maximum_dense_cells exceeded");
            matrix=new float[genes.length][samples.length];
            for(int i=0;i<matrix.length;i++){List<?> row=list(rows.get(i),"expression row "+i);if(row.size()!=samples.length)throw new IOException("Expression row width differs at "+i);
                for(int j=0;j<samples.length;j++)matrix[i][j]=f32(row.get(j),"expression["+i+"]["+j+"]");}
            grouping=new BroadGseaEngine.Grouping(indices(m.get("class0"),samples.length,"class0"),indices(m.get("class1"),samples.length,"class1"));
        }
        return new Prepared(genes,scores,matrix,grouping,List.copyOf(sets));
    }
    private static int[] indices(Object source,int size,String name)throws IOException{
        List<?> list=list(source,name);int[] result=new int[list.size()];for(int i=0;i<result.length;i++)result[i]=(int)integer(list.get(i),name+"["+i+"]",0,size-1);return result;
    }
    private static String[] identities(Object source,String name)throws IOException{
        List<?> values=list(source,name);String[] result=new String[values.size()];var seen=new HashSet<String>();
        for(int i=0;i<result.length;i++){result[i]=string(values.get(i),name+"["+i+"]");if(!seen.add(result[i]))throw new IOException("Duplicate literal identity in "+name+": "+result[i]);}return result;
    }
}
