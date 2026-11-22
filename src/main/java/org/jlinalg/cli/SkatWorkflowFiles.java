/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SkatWorkflowContracts.require;

/** Literal streaming intake, generated source parameters and complete file inventory. */
final class SkatWorkflowFiles {
    private SkatWorkflowFiles(){ }
    static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    record GeneSet(String id,List<Integer> columns) {GeneSet {columns=List.copyOf(columns);}}
    record Prepared(List<String> samples,List<String> variants,List<String> covariates,List<GeneSet> sets,List<Integer> included,long inputCells,long processedCells) {
        Prepared {samples=List.copyOf(samples);variants=List.copyOf(variants);covariates=List.copyOf(covariates);sets=List.copyOf(sets);included=List.copyOf(included);}
    }
    @FunctionalInterface interface Row {void accept(int index,String[] values)throws IOException;}
    static Map<String,Object> map(Object value)throws IOException{return object(value,"object");}
    static long cap(Map<String,Object> request,String key)throws IOException{return ((Number)map(request.get("limits")).get(key)).longValue();}
    static byte[] bytes(Path path,long maximum)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new SkatWorkflowContracts.Unavailable("Supply exact permitted regular file: "+path);
        require(Files.size(path)<=maximum&&Files.size(path)<=Integer.MAX_VALUE-8,"Member exceeds declared byte capacity: "+path);byte[] raw=Files.readAllBytes(path);require(raw.length<=maximum,"File changed/exceeded byte capacity while reading: "+path);return raw;
    }
    static String utf8(byte[] raw)throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    static Map<String,Object> read(Path path,long maximum)throws IOException{return map(SimpleJson.parse(utf8(bytes(path,maximum))));}
    static void json(Path path,Object value)throws IOException {Files.write(path,RsemJson.bytes(value));}
    static String hash(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}}
    static Map<String,Object> pin(byte[] raw){return Map.of("bytes",raw.length,"sha256",hash(raw));}
    static Map<String,Object> pin(Path path)throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require a regular captured member: "+path);
        try(var input=Files.newInputStream(path)){var digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];long total=0;int n;while((n=input.read(block))!=-1){digest.update(block,0,n);total+=n;}require(total==Files.size(path),"File length changed during fingerprint: "+path);return Map.of("bytes",total,"sha256",HexFormat.of().formatHex(digest.digest()));}
        catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    static long capture(Path source,Path target,long maximum)throws IOException {
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new SkatWorkflowContracts.Unavailable("Supply exact permitted input file: "+source);
        require(Files.size(source)<=maximum,"Input exceeds remaining declared byte capacity: "+source);Map<String,Object> before=pin(source);Files.copy(source,target);require(before.equals(pin(source))&&before.equals(pin(target)),"Input changed while capturing: "+source);return ((Number)before.get("bytes")).longValue();
    }
    static List<String> rows(Path path,long maximum,int maximumColumns,int maximumRows,Row action)throws IOException {
        return rows(path,maximum,maximumColumns,maximumRows,action,false);
    }
    static List<String> rows(Path path,long maximum,int maximumColumns,int maximumRows,Row action,boolean allowEmpty)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new SkatWorkflowContracts.Unavailable("Supply exact permitted captured table: "+path);
        require(Files.size(path)<=maximum,"Table exceeds declared member capacity: "+path);
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line=reader.readLine();require(line!=null,"Missing literal table header: "+path);String[] header=line.split("\t",-1);require(header.length>0&&header.length<=maximumColumns,"Header column capacity exceeded: "+path);
            var names=new ArrayList<String>();var seen=new HashSet<String>();for(String name:header){SkatWorkflowContracts.identifier(name);require(seen.add(name),"Duplicate literal header: "+name);names.add(name);}
            int row=0;while((line=reader.readLine())!=null){require(++row<=maximumRows,"Row capacity exceeded: "+path);String[] values=line.split("\t",-1);require(values.length==header.length,"Literal table width differs at row"+row+": "+path);action.accept(row,values);}require(allowEmpty||row>0,"Empty input table: "+path);return List.copyOf(names);
        }
    }
    static double finite(String value)throws IOException {require(DECIMAL.matcher(value).matches(),"Require finite decimal numeric cells;no trimming,coercion or repair: "+value);double result;try{result=Double.parseDouble(value);}catch(NumberFormatException error){throw new IOException("Invalid decimal numeric cell",error);}require(Double.isFinite(result),"Nonfinite numeric input;no repair performed");return result;}
    static String id(String value)throws IOException{return SkatWorkflowContracts.identifier(value);}
    static Prepared prepare(Path root,Map<String,Object> request,boolean compare)throws IOException {
        Path inputs=root.resolve("inputs");int maxSamples=Math.toIntExact(cap(request,"maximum_samples")),maxVariants=Math.toIntExact(cap(request,"maximum_variants")),maxCovariates=Math.toIntExact(cap(request,"maximum_covariates"));long maxBytes=cap(request,"maximum_member_bytes");var samples=new ArrayList<String>();var seenSamples=new HashSet<String>();var missing=new BitSet();var nonbinary=new BitSet();
        var responses=new ArrayList<Double>();
        List<String> phenotype=rows(inputs.resolve("phenotype.tsv"),maxBytes,2,maxSamples,(row,values)->{
            require(values.length==2,"Phenotype requires sample_id and exactly one value column");String sample=id(values[0]);require(seenSamples.add(sample),"Duplicate sample identifier");samples.add(sample);if(values[1].equals("NA")){missing.set(row-1);responses.add(null);}else{double y=finite(values[1]);responses.add(y);if(y!=0&&y!=1)nonbinary.set(row-1);}
        });require(phenotype.get(0).equals("sample_id"),"Require literal UTF-8 header sample_id without a BOM");
        List<String> covariates=List.of();
        if(request.containsKey("covariates")){
            List<String> header=rows(inputs.resolve("covariates.tsv"),maxBytes,maxCovariates+1,maxSamples,(row,values)->{
                require(row<=samples.size()&&values[0].equals(samples.get(row-1)),"Covariate sample membership/order differs;no automatic matching");for(int i=1;i<values.length;i++)if(values[i].equals("NA"))missing.set(row-1);else finite(values[i]);
            });require(header.size()>=2&&header.get(0).equals("sample_id"),"Covariates require sample_id and at least one numeric column;omit the file for intercept-only");require(countRows(inputs.resolve("covariates.tsv"))==samples.size(),"Covariate sample count differs");covariates=header.subList(1,header.size());
        }
        var test=map(request.get("test"));boolean discrete=Boolean.TRUE.equals(test.get("check_genotype"))&&!Boolean.TRUE.equals(test.get("dosage")),dosage=Boolean.TRUE.equals(test.get("dosage"));
        List<String> genotypeHeader=rows(inputs.resolve("genotypes.tsv"),maxBytes,maxVariants+1,maxSamples,(row,values)->{
            require(row<=samples.size()&&values[0].equals(samples.get(row-1)),"Genotype sample membership/order differs;no automatic matching");for(int i=1;i<values.length;i++){
                if(values[i].equals("NA"))continue;double z=finite(values[i]);if(discrete)require(z==0||z==1||z==2||z==9,"Discrete genotypes require 0,1,2,9 or NA;no rounding");if(dosage)require(z>=0&&z<=2||z==9,"Dosage requires [0,2],9 or NA;no clipping");
            }
        });require(genotypeHeader.size()>=2&&genotypeHeader.get(0).equals("sample_id"),"Genotypes require sample_id and explicit variant headers");require(countRows(inputs.resolve("genotypes.tsv"))==samples.size(),"Genotype sample count differs");List<String> variants=genotypeHeader.subList(1,genotypeHeader.size());
        var nullModel=map(request.get("null_model"));if(nullModel.get("missing_model").equals("reject"))require(missing.isEmpty(),"Missing phenotype/covariate values require explicit complete-cases policy");int used=samples.size()-missing.cardinality();require(used>=3,"Fewer than three complete phenotype/covariate samples");var invalidBinary=(BitSet)nonbinary.clone();invalidBinary.andNot(missing);if(nullModel.get("out_type").equals("D"))require(invalidBinary.isEmpty(),"Binary outcome requires literal 0/1 for included samples");
        if(nullModel.get("out_type").equals("D")||Boolean.TRUE.equals(nullModel.get("intercept"))){
            Double first=null;boolean varies=false;for(int i=0;i<responses.size();i++)if(!missing.get(i)){double value=responses.get(i);if(first==null)first=value;else if(value!=first.doubleValue())varies=true;}
            require(varies,"Included outcome is constant;binary outcomes and continuous null models with an intercept require variation. Tiny positive floating-point residual variance does not establish variation;no original model was fitted.");
        }
        int columns=covariates.size()+(Boolean.TRUE.equals(nullModel.get("intercept"))?1:0);require(columns>0&&used>columns,"Require an estimable nonempty null design with positive residual degrees of freedom");
        var variantIndex=new HashMap<String,Integer>();for(int i=0;i<variants.size();i++)variantIndex.put(variants.get(i),i+1);var sets=new LinkedHashMap<String,List<Integer>>();var pairSets=new HashMap<String,Set<Integer>>();
        List<String> setHeader=rows(inputs.resolve("sets.tsv"),maxBytes,2,Math.toIntExact(cap(request,"maximum_memberships")),(row,values)->{
            require(values.length==2,"Set membership requires exactly set_id and variant_id");String set=id(values[0]),variant=id(values[1]);Integer index=variantIndex.get(variant);require(index!=null,"Set membership names an unknown literal variant: "+variant);if(!sets.containsKey(set)){require(sets.size()<cap(request,"maximum_sets"),"Set count capacity exceeded");sets.put(set,new ArrayList<>());pairSets.put(set,new HashSet<>());}require(pairSets.get(set).add(index),"Duplicate variant within set: "+set+"/"+variant);sets.get(set).add(index);
        });require(setHeader.equals(List.of("set_id","variant_id")),"Require exact set_id,variant_id membership columns");
        if(request.containsKey("weights")){
            List<String> header=rows(inputs.resolve("weights.tsv"),maxBytes,2,maxVariants,(row,values)->{require(values.length==2&&row<=variants.size()&&values[0].equals(variants.get(row-1)),"Supplied weights must match all genotype variants in literal order");finite(values[1]);});require(header.equals(List.of("variant_id","weight"))&&countRows(inputs.resolve("weights.tsv"))==variants.size(),"Incomplete/invalid supplied weight table");
        }
        long cells=Math.multiplyExact((long)samples.size(),variants.size()+covariates.size());require(cells<=cap(request,"maximum_cells"),"Input numeric cell capacity exceeded");long processed=0;var groups=new ArrayList<GeneSet>();for(var entry:sets.entrySet()){processed=Math.addExact(processed,Math.multiplyExact((long)used,entry.getValue().size()));groups.add(new GeneSet(entry.getKey(),entry.getValue()));}require(processed<=cap(request,"maximum_set_cells"),"Aggregate processed-set cell capacity exceeded");
        if(!Set.of("linear","linear.weighted").contains(test.get("kernel")))require(Math.multiplyExact((long)used,used)<=cap(request,"maximum_kernel_cells"),"Dense nonlinear-kernel cell capacity exceeded");
        long nk=nullModel.get("function").equals("SKAT_Null_Model_MomentAdjust")?(Boolean.TRUE.equals(nullModel.get("kurtosis_adjustment"))?((Number)nullModel.get("kurtosis_resamples")).longValue():0):(nullModel.get("out_type").equals("D")&&Boolean.TRUE.equals(nullModel.get("adjustment"))&&used<2000?10000:0);long nr=((Number)nullModel.get("n_resampling")).longValue();require(Math.multiplyExact((long)used,nr+nk)<=cap(request,"maximum_resampling_cells"),"Declared/implicit null resampling cell capacity exceeded");
        var included=new ArrayList<Integer>();for(int i=0;i<samples.size();i++)if(!missing.get(i))included.add(i+1);var prepared=new Prepared(samples,variants,covariates,groups,included,cells,processed);
        var metadata=new LinkedHashMap<String,Object>();metadata.put("samples",samples);metadata.put("variants",variants);metadata.put("covariates",covariates);metadata.put("included_sample_indices",included);metadata.put("input_cells",cells);metadata.put("processed_set_cells",processed);metadata.put("declared_null_resampling_cells",Math.multiplyExact((long)used,nr+nk));metadata.put("sets",groups.stream().map(g->Map.of("set_id",g.id(),"input_column_indices",g.columns())).toList());
        generated(root.resolve("worker/input-metadata.json"),RsemJson.bytes(metadata),compare);generated(root.resolve("worker/parameters.tsv"),parameters(request).getBytes(StandardCharsets.UTF_8),compare);return prepared;
    }
    static long countRows(Path path)throws IOException {try(var lines=Files.lines(path,StandardCharsets.UTF_8)){return lines.count()-1;}}
    static void generated(Path path,byte[] raw,boolean compare)throws IOException {if(compare)require(java.util.Arrays.equals(raw,bytes(path,Math.max(raw.length,1))),"Changed generated intake/parameters: "+path);else Files.write(path,raw,StandardOpenOption.CREATE_NEW);}
    static String parameters(Map<String,Object> r)throws IOException {
        var values=new LinkedHashMap<String,Object>();values.put("profile",r.get("profile"));values.put("seed",r.get("seed"));var n=map(r.get("null_model"));var t=map(r.get("test"));var a=map(r.get("admission"));
        values.put("null_function",n.get("function"));for(String key:List.of("out_type","intercept","missing_model","rank_policy","n_resampling","resampling"))values.put(key,n.get(key));values.put("adjustment",n.getOrDefault("adjustment",false));values.put("kurtosis_adjustment",n.getOrDefault("kurtosis_adjustment",true));values.put("kurtosis_resamples",n.getOrDefault("kurtosis_resamples",10000));
        values.putAll(t);for(String key:List.of("weights_beta","r_corr"))values.put(key,String.join(",",list(t.get(key),key).stream().map(Object::toString).toList()));values.putAll(a);values.put("allow_source_fallback",values.remove("allow_reported_fallback"));values.putAll(map(r.get("limits")));var text=new StringBuilder("key\tvalue\n");for(var entry:new java.util.TreeMap<>(values).entrySet())text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');return text.toString();
    }
    static Map<String,Object> inventory(Path root,Map<String,Object> request)throws IOException {
        return inventory(root,request,true);
    }
    static Map<String,Object> inventory(Path root,Map<String,Object> request,boolean enforceAdmissionLimits)throws IOException {
        var result=new java.util.TreeMap<String,Object>();long bytes=0;int count=0;long member=cap(request,"maximum_member_bytes"),total=cap(request,"maximum_output_bytes"),maxFiles=cap(request,"maximum_files");
        try(var stream=Files.walk(root)){for(Path path:stream.toList()){
            require(!Files.isSymbolicLink(path),"Capture contains a symbolic link: "+path);if(path.equals(root)||Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){require(path.toRealPath().equals(path.toAbsolutePath().normalize()),"Capture directory resolves through an alias: "+path);continue;}require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Capture contains a nonregular node: "+path);String name=root.relativize(path).toString().replace('\\','/');if(name.equals("capture-files.json"))continue;long size=Files.size(path);if(enforceAdmissionLimits)require(size<=member,"Captured member exceeds declared capacity: "+name);bytes=Math.addExact(bytes,size);count++;if(enforceAdmissionLimits)require(bytes<=total&&count<=maxFiles,"Captured total byte/file capacity exceeded");result.put(name,pin(path));
        }}return result;
    }
    static Path output(Path candidate)throws IOException {
        Path resolved=resolvedOutput(candidate);require(!Files.exists(resolved,LinkOption.NOFOLLOW_LINKS),"Output already exists;preserve it and choose NEW output");Files.createDirectories(resolved.getParent());return Files.createDirectory(resolved);
    }
    static Path resolvedOutput(Path candidate)throws IOException {
        Path current=candidate.toAbsolutePath().normalize();var missing=new ArrayList<Path>();while(!Files.exists(current,LinkOption.NOFOLLOW_LINKS)){require(current.getParent()!=null&&current.getFileName()!=null,"Invalid output ancestor");missing.add(current.getFileName());current=current.getParent();}Path result=current.toRealPath();for(int i=missing.size()-1;i>=0;i--)result=result.resolve(missing.get(i));return result.normalize();
    }
}
