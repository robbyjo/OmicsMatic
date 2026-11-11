/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.Deseq2SizeFactors;
import static org.jlinalg.cli.Deseq2Contracts.*;

/** Restore exact identities and audit captured structure/normalization without replaying NB inference. */
final class Deseq2Outputs {
    private Deseq2Outputs() { }
    static List<String> publish(Path root,Map<String,Object> request,Deseq2Inputs.Prepared p,boolean compare)throws IOException {
        Path worker=root.resolve("worker");long cap=((Number)request.get("maximum_output_bytes")).longValue();
        var report=read(worker.resolve("worker-report.json"),cap);if(!"completed-with-warnings".equals(report.get("status")))throw new IOException("Original worker did not complete; no result can be verified/admitted");
        var effective=read(worker.resolve("effective-settings.json"),cap);
        if(!PROFILE.equals(effective.get("profile"))||!"4.6.1".equals(effective.get("R"))||!"1.52.0".equals(effective.get("DESeq2")))throw new IOException("Recorded runtime profile differs");
        if(((Number)effective.get("genes")).intValue()!=p.keep().size()||((Number)effective.get("samples")).intValue()!=p.analysisSamples().size())throw new IOException("Effective gene/sample population differs");
        if(!same(effective.get("fit"),request.get("fit"))||!same(effective.get("transforms"),request.get("transforms")))throw new IOException("Worker effective fit/transform settings differ");
        Map<String,Object> expectedResults=new LinkedHashMap<>();for(Object v:list(request.get("results"),"results")){var value=object(v,"result");expectedResults.put(text(value,"id"),value);}
        Object actualResults=effective.get("results");if(expectedResults.isEmpty()&&actualResults instanceof List<?> empty&&empty.isEmpty())actualResults=Map.of();
        if(!same(expectedResults,actualResults))throw new IOException("Worker effective result/contrast settings differ");
        var guards=list(effective.get("source_body_formals_guards"),"source guards");if(guards.size()!=16||!same(guards,report.get("source_body_formals_guards")))throw new IOException("Original function source guard population differs");
        List<String> warnings=new ArrayList<>();
        for(Object item:list(report.get("warnings"),"worker warnings"))warnings.add("ORIGINAL_R_WARNING: "+text(object(item,"warning"),"message"));
        int genes=p.genes().size(),samples=p.analysisSamples().size();int[][] prepared=new int[genes][samples];
        for(int g=0;g<genes;g++)for(int s=0;s<p.samples().size();s++) {
            double input=p.counts()[g][s];long value=(long)("R_round".equals(request.get("count_preparation"))?Math.rint(input):input);
            long sum=(long)prepared[g][p.group()[s]]+value;if(sum>Integer.MAX_VALUE)throw new IOException("Collapsed integer count overflow; no repair allowed");prepared[g][p.group()[s]]=(int)sum;
        }
        List<String> indexedSamples=new ArrayList<>();for(int s=1;s<=samples;s++)indexedSamples.add((request.containsKey("collapse_by")?"c":"s")+s);
        List<Integer> all=new ArrayList<>();for(int g=1;g<=genes;g++)all.add(g);
        remapGeneTable(worker.resolve("counts-prepared.tsv"),root.resolve("counts-prepared.tsv"),p,all,indexedSamples,true,compare,(position,column,value)->{
            if(number(value)!=prepared[position][column])throw new IOException("Original count preparation/collapse differs from explicit request");
        });
        double[] factors=new double[samples];
        try(var input=reader(worker.resolve("size-factors-original.tsv"));var output=new Deseq2Inputs.Lines(root.resolve("size-factors.tsv"),compare)) {
            if(!"sample_index\tsize_factor".equals(input.readLine()))throw new IOException("Unexpected size-factor table header");output.line("sample\tsize_factor");
            for(int s=0;s<samples;s++){String[] f=Deseq2Inputs.fields(input.readLine());if(f.length!=2||!f[0].equals(indexedSamples.get(s)))throw new IOException("Size-factor sample order differs");factors[s]=number(f[1]);output.line(p.analysisSamples().get(s)+"\t"+f[1]);}
            end(input);
        }
        double[][] normalization=null;
        if(request.containsKey("normalization_factors")) {
            normalization=new double[p.keep().size()][samples];Map<Integer,Integer> positions=new HashMap<>();for(int i=0;i<p.keep().size();i++)positions.put(p.keep().get(i),i);
            try(var input=reader(worker.resolve("normalization-factors.tsv"))) {
                if(!("gene_index\t"+String.join("\t",indexedSamples)).equals(input.readLine()))throw new IOException("Normalization-factor transport header differs");
                for(int g=1;g<=genes;g++){String[] f=Deseq2Inputs.fields(input.readLine());if(f.length!=samples+1||!f[0].equals("g"+g))throw new IOException("Normalization-factor transport row order differs");Integer row=positions.get(g);if(row!=null)for(int s=0;s<samples;s++)normalization[row][s]=number(f[s+1]);}end(input);
            }
            double[][] expected=normalization;
            remapGeneTable(worker.resolve("normalization-factors-original.tsv"),root.resolve("normalization-factors.tsv"),p,p.keep(),indexedSamples,true,compare,(row,s,value)->equal(expected[row][s],number(value),"Explicit normalization factor differs"));
        } else {
            if(Files.exists(worker.resolve("normalization-factors-original.tsv")))throw new IOException("Unexpected normalization-factor output");
            for(double f:factors)if(!(f>0)||!Double.isFinite(f))throw new IOException("Original successful dataset requires positive finite size factors");
            auditSizeFactors(root,request,p,prepared,factors);
        }
        double[][] nf=normalization;
        remapGeneTable(worker.resolve("normalized-counts-original.tsv"),root.resolve("normalized-counts.tsv"),p,p.keep(),indexedSamples,true,compare,(row,s,value)->{
            double divisor=nf==null?factors[s]:nf[row][s];if(!(divisor>0)||!Double.isFinite(divisor))throw new IOException("Invalid normalization divisor");
            equal(prepared[p.keep().get(row)-1][s]/divisor,number(value),"Normalized count differs from captured integer counts and factors");
        });
        remapGeneTable(worker.resolve("gene-estimates.tsv"),root.resolve("gene-estimates.tsv"),p,p.keep(),null,false,compare,(row,column,value)->numericOrLogical(value));
        try(var input=reader(worker.resolve("model-matrix.tsv"));var output=new Deseq2Inputs.Lines(root.resolve("model-matrix.tsv"),compare)) {
            String[] header=Deseq2Inputs.fields(input.readLine());if(header.length<2||!header[0].equals("sample_index")||header.length-1>((Number)request.get("maximum_model_columns")).intValue())throw new IOException("Invalid model matrix header");output.line("sample\t"+String.join("\t",Arrays.copyOfRange(header,1,header.length)));
            for(int s=0;s<samples;s++){String[] f=Deseq2Inputs.fields(input.readLine());if(f.length!=header.length||!f[0].equals(indexedSamples.get(s)))throw new IOException("Model matrix sample order differs");for(int c=1;c<f.length;c++)if(!Double.isFinite(number(f[c])))throw new IOException("Model matrix is not finite");f[0]=p.analysisSamples().get(s);output.line(String.join("\t",f));}end(input);
        }
        try(var input=reader(worker.resolve("coefficients.tsv"));var output=new Deseq2Inputs.Lines(root.resolve("coefficients.tsv"),compare)) {
            if(!"index\tsource_coefficient".equals(input.readLine()))throw new IOException("Unexpected coefficient header");output.line("index\tsource_coefficient");String line;int index=0;Set<String> unique=new HashSet<>();
            while((line=input.readLine())!=null){String[] f=Deseq2Inputs.fields(line);if(f.length!=2||!f[0].equals(Integer.toString(++index))||!unique.add(f[1]))throw new IOException("Invalid original coefficient population");output.line(line);}
        }
        if(!compare){Files.createDirectories(root.resolve("results"));Files.createDirectories(root.resolve("transforms"));}
        for(Object value:list(request.get("results"),"results")) {
            var spec=object(value,"result");String id=text(spec,"id");
            for(String suffix:spec.containsKey("shrink")?List.of(".tsv","-shrunken.tsv"):List.of(".tsv"))
                remapGeneTable(worker.resolve("results/"+id+suffix),root.resolve("results/"+id+suffix),p,p.keep(),null,false,compare,(row,col,v)->number(v));
            for(String suffix:spec.containsKey("shrink")?List.of("-metadata.txt","-columns.tsv","-shrink-prior.txt"):List.of("-metadata.txt","-columns.tsv"))copy(worker.resolve("results/"+id+suffix),root.resolve("results/"+id+suffix),compare,cap);
        }
        for(Object value:list(request.get("transforms"),"transforms")) {
            var spec=object(value,"transform");String name=text(spec,"id")+".tsv";
            remapGeneTable(worker.resolve("transforms/"+name),root.resolve("transforms/"+name),p,p.keep(),indexedSamples,true,compare,(row,col,v)->number(v));
        }
        if(Boolean.TRUE.equals(request.get("save_fitted_object"))&&!Files.isRegularFile(worker.resolve("fitted-dds.rds")))throw new IOException("Requested fitted R object is missing");
        if(!Boolean.TRUE.equals(request.get("save_fitted_object"))&&Files.exists(worker.resolve("fitted-dds.rds")))throw new IOException("Unrequested fitted R object appeared");
        return warnings;
    }
    private static void auditSizeFactors(Path root,Map<String,Object> r,Deseq2Inputs.Prepared p,int[][] counts,double[] observed)throws IOException {
        Path worker=root.resolve("worker");double[] expected;
        if(r.containsKey("size_factors")) {
            expected=new double[observed.length];try(var input=reader(worker.resolve("size-factors.tsv"))){if(!"sample_index\tvalue".equals(input.readLine()))throw new IOException("Invalid size-factor transport");for(int s=0;s<expected.length;s++){String[] row=Deseq2Inputs.fields(input.readLine());if(!row[0].equals("s"+(s+1))||row.length!=2)throw new IOException("Invalid size-factor index");expected[s]=number(row[1]);}end(input);}
        }else {
            var fit=object(r.get("fit"),"fit");String type=text(fit,"size_factor_type");if(type.equals("iterate"))return; // Explicitly not independently replayed by Java.
            double[] means=null;
            if(r.containsKey("geometric_means")&&!type.equals("poscounts")) {
                means=new double[counts.length];try(var input=reader(worker.resolve("geometric-means.tsv"))){if(!"gene_index\tvalue".equals(input.readLine()))throw new IOException("Invalid geometric-mean transport");for(int g=0;g<means.length;g++){String[] row=Deseq2Inputs.fields(input.readLine());if(row.length!=2||!row[0].equals("g"+(g+1)))throw new IOException("Invalid geometric-mean index");means[g]=number(row[1]);}end(input);}
            }
            int[] controls=null;if(r.containsKey("control_genes"))controls=list(p.parameters().get("control_genes"),"controls").stream().mapToInt(v->((Number)v).intValue()-1).toArray();
            int selected=controls==null?counts.length:controls.length;long n=(long)counts.length*observed.length;
            var limits=new Deseq2SizeFactors.Limits(Math.max(1,n),Math.max(1,2*n+(long)selected*observed.length),Math.max(1,Math.max(counts.length,selected)));
            if(type.equals("poscounts")) {
                // S4 estimateSizeFactors supplies computed means to the low-level function, which centers factors.
                double[] logs=Deseq2SizeFactors.estimate(counts,Deseq2SizeFactors.Type.POSCOUNTS,null,null,limits,()->false).logGeometricMeans();
                means=new double[logs.length];for(int g=0;g<logs.length;g++)means[g]=Math.exp(logs[g]);
            }
            expected=Deseq2SizeFactors.estimate(counts,Deseq2SizeFactors.Type.RATIO,means,controls,limits,()->false).factors();
        }
        for(int s=0;s<observed.length;s++)equal(expected[s],observed[s],"Original dataset size factor differs from native/prepared normalization");
    }
    @FunctionalInterface private interface CellCheck {void accept(int row,int column,String value)throws IOException;}
    private static void remapGeneTable(Path inputPath,Path outputPath,Deseq2Inputs.Prepared p,List<Integer> indices,List<String> expectedSamples,boolean matrix,boolean compare,CellCheck check)throws IOException {
        try(var input=reader(inputPath);var output=new Deseq2Inputs.Lines(outputPath,compare)) {
            String[] header=Deseq2Inputs.fields(input.readLine());if(header.length<2||!header[0].equals("gene_index"))throw new IOException("Missing indexed gene output header: "+inputPath.getFileName());
            if(matrix&&(expectedSamples==null||header.length!=expectedSamples.size()+1||!Arrays.asList(Arrays.copyOfRange(header,1,header.length)).equals(expectedSamples)))throw new IOException("Worker output sample identities/order differ");
            Set<String> columns=new HashSet<>();for(String field:header)if(!columns.add(field)||field.isBlank())throw new IOException("Invalid/repeated output column");
            header[0]="gene";if(matrix)for(int s=1;s<header.length;s++)header[s]=p.analysisSamples().get(s-1);output.line(String.join("\t",header));
            for(int row=0;row<indices.size();row++){String[] f=Deseq2Inputs.fields(input.readLine());if(f.length!=header.length||!f[0].equals("g"+indices.get(row)))throw new IOException("Worker output gene population/order differs: "+inputPath.getFileName());for(int col=1;col<f.length;col++)check.accept(row,col-1,f[col]);f[0]=p.genes().get(indices.get(row)-1);output.line(String.join("\t",f));}end(input);
        }
    }
    private static void copy(Path source,Path target,boolean compare,long cap)throws IOException {if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>cap)throw new IOException("Unavailable/oversized original metadata file");if(compare){if(!FollowupSupport.hash(source).equals(FollowupSupport.hash(target)))throw new IOException("Published original metadata differs");}else Files.copy(source,target);}
    private static BufferedReader reader(Path path)throws IOException {if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing regular worker output: "+path.getFileName());return Files.newBufferedReader(path,StandardCharsets.UTF_8);}
    private static void end(BufferedReader input)throws IOException {if(input.readLine()!=null)throw new IOException("Extra worker output rows");}
    static double number(String value)throws IOException {try{return switch(value){case "NA"->Deseq2SizeFactors.MISSING;case "NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value);};}catch(NumberFormatException e){throw new IOException("Invalid original numeric output",e);}}
    private static void numericOrLogical(String value)throws IOException {if(!value.equals("TRUE")&&!value.equals("FALSE"))number(value);}
    private static void equal(double expected,double actual,String message)throws IOException {if(Double.isNaN(expected)){if(!Double.isNaN(actual)||Deseq2SizeFactors.isMissing(expected)!=Deseq2SizeFactors.isMissing(actual))throw new IOException(message);}else if(Double.isInfinite(expected)){if(expected!=actual)throw new IOException(message);}else if(!Double.isFinite(actual)||Math.abs(expected-actual)>Math.max(1e-300,Math.abs(expected)*2e-12))throw new IOException(message);}
    static boolean same(Object a,Object b) {
        if(a instanceof Number x&&b instanceof Number y)return Double.compare(x.doubleValue(),y.doubleValue())==0;
        if(a instanceof Map<?,?> x&&b instanceof Map<?,?> y){if(!x.keySet().equals(y.keySet()))return false;for(Object key:x.keySet())if(!same(x.get(key),y.get(key)))return false;return true;}
        if(a instanceof List<?> x&&b instanceof List<?> y){if(x.size()!=y.size())return false;for(int i=0;i<x.size();i++)if(!same(x.get(i),y.get(i)))return false;return true;}
        return Objects.equals(a,b);
    }
}
