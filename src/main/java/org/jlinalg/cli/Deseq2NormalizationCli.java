/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.sequence.Deseq2SizeFactors;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Standalone development command for native DESeq2 median-location normalization. */
public final class Deseq2NormalizationCli {
    private Deseq2NormalizationCli() { }
    private static final Set<String> KEYS=Set.of("schema_version","mode","type","counts","geometric_means","controls","out","maximum_entries","maximum_work","scratch_entries","maximum_input_bytes","maximum_output_bytes");
    private static final String SCOPE="NATIVE NORMALIZATION ONLY: DESeq2 1.52.0 estimateSizeFactorsForMatrix median-location subset (ratio/poscounts). No dispersion, differential test, VST, rlog, shrinkage, quantifier import or historical whole-package analysis is performed. This development command is not registered in the main dispatcher and earns no full DESeq2 catalog credit.";
    private static final String ACTION="Read WARNINGS.md, analysis-status.json and the manifest. Preserve the attempt. Correct the named raw-count, identity, geometric-mean, control-selection or capacity issue and use a NEW output path. Undefined, zero or infinite size factors cannot be used for differential fitting. No R installation is required for this normalization command. A separate validated upstream workflow is required for DESeq2 inference; if its runtime is unavailable or installation is forbidden, skip that dependent analysis and report the missing requirement.";
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")) {
                emit(out,"Deseq2NormalizationCli --request UTF8_JSON\nRequest mode NORMALIZE, schema_version 1; paths resolve against the request directory. Require raw integer gene-by-sample counts TSV and a NEW output directory.\n"+SCOPE+"\n"+ACTION);return 0;
            }
            if(args.length!=2||!args[0].equals("--request")) throw new IOException("Require --request UTF8_JSON or --help");
            Path request=Path.of(args[1]).toAbsolutePath().normalize();
            if(!Files.isRegularFile(request,LinkOption.NOFOLLOW_LINKS)||Files.size(request)>1_000_000)throw new IOException("Require a bounded regular request JSON file");
            byte[] requestBytes=Files.readAllBytes(request);
            Object parsed=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(requestBytes)).toString());
            if(!(parsed instanceof Map<?,?> raw))throw new IOException("Request must be an object");
            Map<String,Object> value=new LinkedHashMap<>();
            for(var e:raw.entrySet()) {if(!(e.getKey() instanceof String key)||!KEYS.contains(key))throw new IOException("Unknown request key: "+e.getKey());value.put(key,e.getValue());}
            if(integer(value,"schema_version",0,1,1)!=1||!"NORMALIZE".equals(value.get("mode")))throw new IOException("Require schema_version 1 and mode NORMALIZE");
            String type=text(value,"type");
            if(!Set.of("ratio","poscounts").contains(type))throw new IOException("Require type ratio or poscounts; iterative estimation is not implemented here");
            long inputCap=integer(value,"maximum_input_bytes",256_000_000,1,1_000_000_000);
            long outputCap=integer(value,"maximum_output_bytes",512_000_000,1,2_000_000_000);
            var limits=new Deseq2SizeFactors.Limits(integer(value,"maximum_entries",20_000_000,1,20_000_000),integer(value,"maximum_work",100_000_000,1,500_000_000),(int)integer(value,"scratch_entries",2_000_000,1,20_000_000));
            Path destination=resolve(request.getParent(),text(value,"out"));int[] code={0};
            FollowupSupport.externalTransaction(Map.of("out",destination.toString()),"native-deseq2-normalization-development",(stage,manifest)->{
                List<String> warnings=new ArrayList<>();warnings.add(SCOPE);
                Map<String,Object> status=new LinkedHashMap<>();status.put("engine","native-java");status.put("reference_version","1.52.0");status.put("differential_inference_performed",false);
                try {
                    Files.write(stage.resolve("source-request.json"),requestBytes);
                    Files.createDirectory(stage.resolve("inputs"));long[] remaining={inputCap};Map<String,String> inputs=new TreeMap<>();
                    for(String key:List.of("counts","geometric_means","controls"))if(value.containsKey(key)) {
                        Path source=resolve(request.getParent(),text(value,key));
                        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>remaining[0])throw new IOException("Input is unavailable, not a regular file or exceeds maximum_input_bytes: "+key);
                        long size=Files.size(source);String before=FollowupSupport.hash(source);Path copy=stage.resolve("inputs/"+key+".tsv");Files.copy(source,copy);
                        if(Files.size(copy)!=size||!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(copy)))throw new IOException("Input changed during capture: "+key);
                        remaining[0]-=size;inputs.put(key,before);
                    }
                    if(!inputs.containsKey("counts"))throw new IOException("Missing counts input");
                    Counts counts=counts(stage.resolve("inputs/counts.tsv"),limits);
                    double[] means=inputs.containsKey("geometric_means")?means(stage.resolve("inputs/geometric_means.tsv"),counts.genes):null;
                    int[] controls=inputs.containsKey("controls")?controls(stage.resolve("inputs/controls.tsv"),counts.genes,limits.scratchEntries()):null;
                    var result=Deseq2SizeFactors.estimate(counts.values,Deseq2SizeFactors.Type.valueOf(type.toUpperCase(Locale.ROOT)),means,controls,limits,()->false);
                    warnings.addAll(result.warnings());double[] factors=result.factors();int[] selected=result.selectedPerSample();
                    long[] outputRemaining={outputCap};
                    try(var writer=writer(stage.resolve("size-factors.tsv"),outputRemaining)) {
                        writer.write("sample\tsize_factor\tselected_gene_entries\n");
                        for(int s=0;s<factors.length;s++)writer.write(counts.samples.get(s)+"\t"+number(factors[s])+"\t"+selected[s]+"\n");
                    }
                    try(var writer=writer(stage.resolve("normalized-counts.tsv"),outputRemaining)) {
                        writer.write("gene\t"+String.join("\t",counts.samples)+"\n");
                        for(int g=0;g<counts.values.length;g++) {
                            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Normalization output cancelled");
                            writer.write(counts.genes.get(g));
                            for(int s=0;s<factors.length;s++)writer.write("\t"+number(Deseq2SizeFactors.isMissing(factors[s])?Deseq2SizeFactors.MISSING:counts.values[g][s]/factors[s]));
                            writer.write("\n");
                        }
                    }
                    status.put("status",result.usableForFit()?"completed-with-warnings":"completed-with-unusable-factors");status.put("usable_for_differential_fit",result.usableForFit());
                    status.put("genes",counts.genes.size());status.put("samples",counts.samples.size());status.put("type",type);
                    manifest.put("captured_input_sha256",inputs);manifest.put("normalization_status",status);
                } catch(Exception failure) {
                    code[0]=2;status.put("status","FAIL2_NO_NORMALIZATION_RESULT_ADMITTED");status.put("error",failure.toString());status.put("usable_for_differential_fit",false);
                    warnings.add("FAIL2: "+failure+". Any partial tables in this retained attempt are not admitted results.");
                }
                writeJson(stage.resolve("analysis-status.json"),status);
                Files.writeString(stage.resolve("WARNINGS.md"),"# "+status.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+ACTION+"\n",StandardCharsets.UTF_8);
                manifest.put("engine","native-java");manifest.put("reference_version","1.52.0");manifest.put("analysis_status",status.get("status"));manifest.put("differential_inference_performed",false);
            });
            emit(code[0]==0?out:err,(code[0]==0?"Completed native normalization: ":"FAIL2 — NO NORMALIZATION RESULT ADMITTED: ")+destination+"\nRead WARNINGS.md and analysis-status.json. "+ACTION);return code[0];
        }catch(Exception failure){emit(err,"FAIL2 — NO NORMALIZATION RESULT ADMITTED: "+failure+"\n"+ACTION);return 2;}
    }
    private record Counts(List<String> genes,List<String> samples,int[][] values) { }
    private static Counts counts(Path path,Deseq2SizeFactors.Limits limits)throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line=reader.readLine();if(line==null)throw new IOException("Counts TSV is empty");String[] header=line.split("\t",-1);
            if(header.length<2||!header[0].equals("gene"))throw new IOException("Counts header must be gene followed by sample IDs");
            List<String> samples=new ArrayList<>();Set<String> unique=new HashSet<>();
            for(int s=1;s<header.length;s++){identity(header[s]);if(!unique.add(header[s]))throw new IOException("Duplicate sample ID: "+header[s]);samples.add(header[s]);}
            List<String> genes=new ArrayList<>();List<int[]> rows=new ArrayList<>();unique.clear();
            while((line=reader.readLine())!=null) {
                if((long)(rows.size()+1)*samples.size()>limits.matrixEntries()||rows.size()>=limits.scratchEntries())throw new IOException("Counts exceed declared matrix/temporary-vector capacity");
                String[] fields=line.split("\t",-1);if(fields.length!=header.length)throw new IOException("Counts TSV row width differs");identity(fields[0]);
                if(!unique.add(fields[0]))throw new IOException("Duplicate gene ID; no renaming was performed: "+fields[0]);
                int[] row=new int[samples.size()];for(int s=0;s<row.length;s++) {
                    if(!fields[s+1].matches("[0-9]+"))throw new IOException("Raw counts must be decimal nonnegative integers; no rounding or repair performed");
                    try {row[s]=Integer.parseInt(fields[s+1]);}catch(NumberFormatException e){throw new IOException("Raw count exceeds signed integer range",e);}
                }
                genes.add(fields[0]);rows.add(row);
            }
            if(rows.isEmpty())throw new IOException("Require at least one gene");return new Counts(List.copyOf(genes),List.copyOf(samples),rows.toArray(int[][]::new));
        }
    }
    private static double[] means(Path path,List<String> genes)throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            if(!"gene\tgeometric_mean".equals(reader.readLine()))throw new IOException("Geometric-means header must be gene TAB geometric_mean");
            double[] values=new double[genes.size()];for(int i=0;i<values.length;i++) {
                String line=reader.readLine();if(line==null)throw new IOException("Missing geometric-mean row");String[] fields=line.split("\t",-1);
                if(fields.length!=2||!fields[0].equals(genes.get(i)))throw new IOException("Geometric means must match exact gene identities and order");
                String value=fields[1];if(!value.equals(value.strip()))throw new IOException("Whitespace in geometric mean");
                try {values[i]=switch(value){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;default->Double.parseDouble(value);};}
                catch(NumberFormatException e){throw new IOException("Invalid geometric mean",e);}
                if(values[i]<0)throw new IOException("Negative geometric mean; no repair performed");
            }
            if(reader.readLine()!=null)throw new IOException("Extra geometric-mean rows");return values;
        }
    }
    private static int[] controls(Path path,List<String> genes,int cap)throws IOException {
        Map<String,Integer> index=new HashMap<>();for(int i=0;i<genes.size();i++)index.put(genes.get(i),i);List<Integer> selected=new ArrayList<>();
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            if(!"gene".equals(reader.readLine()))throw new IOException("Controls header must be gene");String line;
            while((line=reader.readLine())!=null){if(selected.size()>=cap)throw new IOException("Control vector exceeds declared capacity");Integer i=index.get(line);if(i==null)throw new IOException("Unknown control gene; no alias/renaming performed: "+line);selected.add(i);}
        }
        return selected.stream().mapToInt(Integer::intValue).toArray();
    }
    private static Writer writer(Path path,long[] remaining)throws IOException {
        OutputStream file=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW);
        OutputStream bounded=new OutputStream(){
            @Override public void write(int value)throws IOException {if(remaining[0]<1)throw new IOException("maximum_output_bytes exceeded");file.write(value);remaining[0]--;}
            @Override public void write(byte[] bytes,int offset,int length)throws IOException {if(length>remaining[0])throw new IOException("maximum_output_bytes exceeded");file.write(bytes,offset,length);remaining[0]-=length;}
            @Override public void close()throws IOException {file.close();}
        };
        return new BufferedWriter(new OutputStreamWriter(bounded,StandardCharsets.UTF_8));
    }
    private static long integer(Map<String,Object> map,String key,long fallback,long minimum,long maximum)throws IOException {
        Object v=map.get(key);if(v==null){if(map.containsKey(key))throw new IOException("Null integer: "+key);return fallback;}
        if(!(v instanceof Number))throw new IOException("Require numeric integer: "+key);
        try {long n=new BigDecimal(v.toString()).longValueExact();if(n<minimum||n>maximum)throw new ArithmeticException();return n;}
        catch(ArithmeticException e){throw new IOException("Invalid integer capacity/version: "+key,e);}
    }
    private static String text(Map<String,Object> map,String key)throws IOException {
        if(!(map.get(key) instanceof String s)||s.isBlank()||s.indexOf('\0')>=0)throw new IOException("Missing/invalid text field: "+key);return s;
    }
    private static Path resolve(Path directory,String text){Path p=Path.of(text);return(p.isAbsolute()?p:directory.resolve(p)).toAbsolutePath().normalize();}
    private static void identity(String value)throws IOException {if(value.isBlank()||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require nonblank exact IDs without control characters");}
    private static String number(double value){if(Deseq2SizeFactors.isMissing(value))return "NA";if(Double.isNaN(value))return "NaN";if(value==Double.POSITIVE_INFINITY)return "Inf";if(value==Double.NEGATIVE_INFINITY)return "-Inf";return Double.toString(value);}
    private static void writeJson(Path path,Object value)throws IOException {Files.write(path,RsemJson.bytes(value));}
    private static void emit(PrintStream stream,String message){stream.writeBytes((message+"\n").getBytes(StandardCharsets.UTF_8));}
}
