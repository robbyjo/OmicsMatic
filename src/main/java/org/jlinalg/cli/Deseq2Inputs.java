/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Deseq2Contracts.*;

/** Exact identity admission and indexed transport for the DESeq2 R worker. */
final class Deseq2Inputs {
    private Deseq2Inputs() { }
    record Prepared(List<String> genes,List<String> samples,List<String> analysisSamples,List<Integer> keep,
                    double[][] counts,int[] group,List<String> warnings,Map<String,Object> summary,
                    Map<String,Object> parameters) { }
    static Prepared prepare(Path root,Map<String,Object> request,boolean compare)throws IOException {
        Path inputs=root.resolve("inputs"),worker=root.resolve("worker");
        long entries=((Number)request.get("maximum_entries")).longValue();int geneCap=((Number)request.get("maximum_genes")).intValue();
        int sampleCap=((Number)request.get("maximum_samples")).intValue();boolean rounding="R_round".equals(request.get("count_preparation"));
        List<String> samples=new ArrayList<>(),genes=new ArrayList<>();List<double[]> rows=new ArrayList<>();Set<String> ids=new HashSet<>();
        try(var reader=Files.newBufferedReader(inputs.resolve("counts.tsv"),StandardCharsets.UTF_8)) {
            String[] header=fields(reader.readLine());if(header.length<3||!header[0].equals("gene")||header.length-1>sampleCap)throw new IOException("Counts header must be gene followed by 2..maximum_samples exact sample IDs");
            for(int i=1;i<header.length;i++){identity(header[i]);if(!ids.add(header[i]))throw new IOException("Duplicate count sample ID: "+header[i]);samples.add(header[i]);}ids.clear();
            String line;while((line=reader.readLine())!=null) {
                if(rows.size()>=geneCap||(long)(rows.size()+1)*samples.size()>entries)throw new IOException("Counts exceed declared gene/matrix capacity");
                String[] f=fields(line);if(f.length!=header.length)throw new IOException("Count row width differs");identity(f[0]);if(!ids.add(f[0]))throw new IOException("Duplicate gene ID; no renaming performed: "+f[0]);
                double[] row=new double[samples.size()];for(int s=0;s<row.length;s++) {
                    String token=f[s+1];double value=numeric(token,"count");
                    if(value<0||value>Integer.MAX_VALUE)throw new IOException("Count outside nonnegative signed-integer capacity");
                    if(!rounding)try{new BigDecimal(token).toBigIntegerExact().intValueExact();}catch(NumberFormatException|ArithmeticException e){throw new IOException("Fractional/invalid raw count; declare estimated_counts and explicit R_round only when that preparation is scientifically intended",e);}
                    row[s]=value;
                }
                genes.add(f[0]);rows.add(row);
            }
        }
        if(genes.isEmpty())throw new IOException("Require at least one gene");double[][] counts=rows.toArray(double[][]::new);
        Map<String,Integer> geneIndex=new HashMap<>();for(int i=0;i<genes.size();i++)geneIndex.put(genes.get(i),i);
        var variables=list(request.get("variables"),"variables");var variableObjects=new ArrayList<Map<String,Object>>();for(Object v:variables)variableObjects.add(object(v,"variable"));
        String[][] metadata=new String[samples.size()][variableObjects.size()];int[] group=new int[samples.size()];
        List<String> analysisSamples=new ArrayList<>();List<Integer> firstGroupSample=new ArrayList<>();Map<String,Integer> groupIndex=new LinkedHashMap<>();
        List<String> extraColumns=new ArrayList<>();String collapse=request.containsKey("collapse_by")?text(request,"collapse_by"):null;
        try(var reader=Files.newBufferedReader(inputs.resolve("samples.tsv"),StandardCharsets.UTF_8)) {
            String[] header=fields(reader.readLine());if(header.length<1||!header[0].equals("sample"))throw new IOException("Sample metadata first column must be sample");
            var columns=new HashMap<String,Integer>();for(int i=1;i<header.length;i++){identity(header[i]);if(header[i].equals("sample")||columns.put(header[i],i)!=null)throw new IOException("Duplicate metadata column");}
            Set<String> used=new HashSet<>();for(var v:variableObjects){String name=text(v,"name");if(!columns.containsKey(name))throw new IOException("Missing modeled variable column: "+name);used.add(name);}
            if(collapse!=null){if(!columns.containsKey(collapse))throw new IOException("Missing technical-replicate grouping column: "+collapse);used.add(collapse);}
            for(int i=1;i<header.length;i++)if(!used.contains(header[i]))extraColumns.add(header[i]);
            for(int s=0;s<samples.size();s++) {
                String[] f=fields(reader.readLine());if(f.length!=header.length||!samples.get(s).equals(f[0]))throw new IOException("Metadata sample identities/order must match count columns exactly");
                for(int v=0;v<variableObjects.size();v++) {
                    var spec=variableObjects.get(v);String raw=f[columns.get(spec.get("name"))];
                    if("factor".equals(spec.get("kind"))){var levels=list(spec.get("levels"),"levels");int index=levels.indexOf(raw);if(index<0)throw new IOException("Undeclared exact factor level for "+spec.get("name")+": "+raw);metadata[s][v]="l"+(index+1);}
                    else metadata[s][v]=Double.toString(numeric(raw,"numeric covariate"));
                }
                String label=collapse==null?samples.get(s):f[columns.get(collapse)];identity(label);
                Integer index=groupIndex.get(label);if(index==null){index=groupIndex.size();groupIndex.put(label,index);analysisSamples.add(label);firstGroupSample.add(s);}group[s]=index;
                if(collapse!=null&&!Arrays.equals(metadata[firstGroupSample.get(index)],metadata[s]))throw new IOException("Technical replicate group has inconsistent modeled covariates: "+label);
            }
            if(reader.readLine()!=null)throw new IOException("Extra metadata sample rows");
        }
        List<Integer> keep=new ArrayList<>();
        if(request.containsKey("keep_genes")) {
            var selected=geneList(inputs.resolve("keep_genes.tsv"),geneIndex,geneCap,false);
            if(selected.isEmpty())throw new IOException("keep_genes must select at least one exact gene");for(int i:selected)keep.add(i+1);
        }else for(int i=1;i<=genes.size();i++)keep.add(i);
        List<String> warnings=new ArrayList<>();
        warnings.add("INDEXED_TRANSPORT: original gene/sample/factor identities are retained in captured inputs and prepared-inputs.json. Only ASCII row/sample/level indices cross the R TSV boundary; output tables restore exact identities and order. Coefficient names use the recorded transport-level codes; factor contrasts are specified with original labels.");
        if(rounding)warnings.add("EXPLICIT_ESTIMATED_COUNT_ROUNDING: caller declared estimated_counts and R_round. Original R round() is applied before the DESeqDataSet constructor. Original estimates and prepared integer counts are retained; no TPM/CPM conversion is inferred.");
        if(collapse!=null)warnings.add("EXPLICIT_TECHNICAL_REPLICATE_COLLAPSE: original collapseReplicates sums declared technical groups before factor estimation. Groups use first-appearance order and identical modeled covariates; biological replication is the caller's responsibility.");
        if(!extraColumns.isEmpty())warnings.add("UNMODELED_METADATA_RETAINED: captured metadata includes columns not used in the declared model: "+String.join(", ",extraColumns));
        Map<String,Object> parameters=new LinkedHashMap<>();for(String key:List.of("fit","design","results","transforms","seed","save_fitted_object","maximum_model_columns"))parameters.put(key,request.get(key));
        if(request.containsKey("reduced_design"))parameters.put("reduced_design",request.get("reduced_design"));
        parameters.put("count_preparation",request.get("count_preparation"));parameters.put("selected_gene_indices",keep);parameters.put("collapse",collapse!=null);
        parameters.put("genes",genes.size());parameters.put("samples",samples.size());parameters.put("analysis_samples",analysisSamples.size());
        List<Map<String,Object>> rVariables=new ArrayList<>(),factorMap=new ArrayList<>();
        for(var v:variableObjects) {
            var rv=new LinkedHashMap<String,Object>(Map.of("name",v.get("name"),"kind",v.get("kind")));
            if("factor".equals(v.get("kind"))){var levels=list(v.get("levels"),"levels");List<String> codes=new ArrayList<>();for(int i=0;i<levels.size();i++){String code="l"+(i+1);codes.add(code);factorMap.add(Map.of("variable",v.get("name"),"code",code,"original_level",levels.get(i)));}rv.put("levels",codes);}
            rVariables.add(rv);
        }
        parameters.put("variables",rVariables);
        for(String key:List.of("size_factors","normalization_factors","geometric_means","control_genes"))parameters.put("has_"+key,request.containsKey(key));
        if(request.containsKey("control_genes")){var controls=geneList(inputs.resolve("control_genes.tsv"),geneIndex,geneCap,true);parameters.put("control_genes",controls.stream().map(i->i+1).toList());}
        if(request.containsKey("geometric_means")&&"poscounts".equals(object(request.get("fit"),"fit").get("size_factor_type")))warnings.add("SOURCE_POSCOUNTS_OVERRIDES_SUPPLIED_MEANS: the original estimateSizeFactors(DESeqDataSet,type='poscounts') computes its own positive-count means, replacing the supplied means. This is retained and reported, not substituted with the low-level Java API.");
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("genes",genes);summary.put("samples",samples);summary.put("analysis_samples",analysisSamples);summary.put("group_indices_zero_based",Arrays.stream(group).boxed().toList());summary.put("selected_gene_indices_one_based",keep);summary.put("factor_level_mapping",factorMap);summary.put("unmodeled_metadata_columns",extraColumns);summary.put("warnings",warnings);
        if(!compare)Files.createDirectories(worker);
        try(var output=new Lines(worker.resolve("counts.tsv"),compare)) {
            StringBuilder header=new StringBuilder("gene_index");for(int s=1;s<=samples.size();s++)header.append("\ts").append(s);output.line(header.toString());
            for(int g=0;g<genes.size();g++){StringBuilder line=new StringBuilder("g"+(g+1));for(double c:counts[g])line.append('\t').append(Double.toString(c));output.line(line.toString());}
        }
        try(var output=new Lines(worker.resolve("samples.tsv"),compare)) {
            var header=new ArrayList<String>();header.add("sample_index");for(var v:variableObjects)header.add(text(v,"name"));if(collapse!=null)header.add("collapse_group");output.line(String.join("\t",header));
            for(int s=0;s<samples.size();s++){var fields=new ArrayList<String>();fields.add("s"+(s+1));fields.addAll(Arrays.asList(metadata[s]));if(collapse!=null)fields.add("c"+(group[s]+1));output.line(String.join("\t",fields));}
        }
        if(request.containsKey("size_factors"))vector(inputs.resolve("size_factors.tsv"),worker.resolve("size-factors.tsv"),samples,"sample\tsize_factor","sample_index",false,compare);
        if(request.containsKey("geometric_means"))vector(inputs.resolve("geometric_means.tsv"),worker.resolve("geometric-means.tsv"),genes,"gene\tgeometric_mean","gene_index",true,compare);
        if(request.containsKey("normalization_factors")) {
            try(var reader=Files.newBufferedReader(inputs.resolve("normalization_factors.tsv"),StandardCharsets.UTF_8);var output=new Lines(worker.resolve("normalization-factors.tsv"),compare)) {
                String expected="gene\t"+String.join("\t",samples);if(!expected.equals(reader.readLine()))throw new IOException("Normalization-factor sample identities/order differ");
                StringBuilder header=new StringBuilder("gene_index");for(int s=1;s<=samples.size();s++)header.append("\ts").append(s);output.line(header.toString());
                for(int g=0;g<genes.size();g++){String[] row=fields(reader.readLine());if(row.length!=samples.size()+1||!row[0].equals(genes.get(g)))throw new IOException("Normalization-factor gene identities/order differ");StringBuilder line=new StringBuilder("g"+(g+1));for(int s=1;s<row.length;s++){double value=numeric(row[s],"normalization factor");if(value<=0)throw new IOException("Positive finite normalization factors required");line.append('\t').append(Double.toString(value));}output.line(line.toString());}
                if(reader.readLine()!=null)throw new IOException("Extra normalization-factor rows");
            }
        }
        if(compare){long cap=((Number)request.get("maximum_input_bytes")).longValue();if(!RsemJson.json(summary).equals(RsemJson.json(read(root.resolve("prepared-inputs.json"),cap))))throw new IOException("Prepared identity/intake record differs");if(!RsemJson.json(parameters).equals(RsemJson.json(read(worker.resolve("parameters.json"),cap))))throw new IOException("Worker parameters differ from typed captured request");}
        else {json(root.resolve("prepared-inputs.json"),summary);json(worker.resolve("parameters.json"),parameters);}
        return new Prepared(List.copyOf(genes),List.copyOf(samples),List.copyOf(analysisSamples),List.copyOf(keep),counts,group,List.copyOf(warnings),summary,parameters);
    }
    private static void vector(Path source,Path target,List<String> identities,String header,String index,boolean means,boolean compare)throws IOException {
        try(var reader=Files.newBufferedReader(source,StandardCharsets.UTF_8);var output=new Lines(target,compare)) {
            if(!header.equals(reader.readLine()))throw new IOException("Unexpected factor/mean vector header; require "+header);output.line(index+"\tvalue");
            for(int i=0;i<identities.size();i++){String[] f=fields(reader.readLine());if(f.length!=2||!identities.get(i).equals(f[0]))throw new IOException("Factor/mean identities/order differ");String token=f[1];double value;
                if(means&&Set.of("NA","NaN","Inf").contains(token))value=switch(token){case "Inf"->Double.POSITIVE_INFINITY;default->Double.NaN;};else value=numeric(token,"factor/geometric mean");
                if(value<0||!means&&(!(value>0)||!Double.isFinite(value)))throw new IOException("Invalid size factor/geometric mean");output.line((means?"g":"s")+(i+1)+"\t"+(Double.isNaN(value)?"NaN":value==Double.POSITIVE_INFINITY?"Inf":Double.toString(value)));}
            if(reader.readLine()!=null)throw new IOException("Extra factor/mean vector rows");
        }
    }
    private static List<Integer> geneList(Path file,Map<String,Integer> index,int cap,boolean repeats)throws IOException {
        List<Integer> values=new ArrayList<>();Set<Integer> seen=new HashSet<>();try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            if(!"gene".equals(reader.readLine()))throw new IOException("Gene selection header must be gene");String line;
            while((line=reader.readLine())!=null){if(values.size()>=cap)throw new IOException("Gene-selection capacity exceeded");Integer i=index.get(line);if(i==null)throw new IOException("Unknown exact selection gene: "+line);if(!repeats&&!seen.add(i))throw new IOException("Repeated keep_genes identity");values.add(i);}
        }return values;
    }
    static String[] fields(String line)throws IOException {if(line==null)throw new IOException("Missing required TSV row");return line.split("\t",-1);}
    static double numeric(String token,String label)throws IOException {if(!token.equals(token.strip())||!token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require a decimal numeric "+label);try{double d=Double.parseDouble(token);if(!Double.isFinite(d))throw new NumberFormatException();return d;}catch(NumberFormatException e){throw new IOException("Require finite "+label,e);}}
    static final class Lines implements AutoCloseable {
        private final BufferedReader input;private final BufferedWriter output;
        Lines(Path path,boolean compare)throws IOException {input=compare?Files.newBufferedReader(path,StandardCharsets.UTF_8):null;output=compare?null:Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
        void line(String text)throws IOException {if(input!=null){if(!text.equals(input.readLine()))throw new IOException("Indexed transport/output differs from captured inputs");}else {output.write(text);output.write('\n');}}
        @Override public void close()throws IOException {if(input!=null)try{if(input.readLine()!=null)throw new IOException("Extra indexed transport rows");}finally{input.close();}else output.close();}
    }
}
