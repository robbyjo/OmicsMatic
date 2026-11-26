/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.omics.methylation.AllcoolsRegionQuantifier;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

final class AllcoolsRegionCompute {
    private AllcoolsRegionCompute() { }
    static Map<String,Object> run(Path root,AllcoolsRegionRequest.Spec spec,boolean replay,Runnable check)throws IOException {
        String[] types=new String[spec.types()],features=new String[spec.features()];var cells=new HashSet<String>();
        long[] mc=new long[spec.features()],cov=new long[spec.features()];long rows=0,groups=0,zero=0,saturatedMc=0,saturatedCov=0;
        long hypoNonzero=0,hyperNonzero=0,zeroHyper=0;var budget=new Budget(spec.capacity().outputBytes());Pin valuesPin,poolsPin,countsPin;
        Path valuesPath=root.resolve("outputs/values.tsv"),poolsPath=root.resolve("outputs/pools.tsv"),countsPath=root.resolve("outputs/posterior-counts.tsv");
        try(var in=new Table(root.resolve("inputs/counts.tsv"),check);
            var values=new Sink(replay?OutputStream.nullOutputStream():Files.newOutputStream(valuesPath,StandardOpenOption.CREATE_NEW),budget,check);
            var pools=new Sink(replay?OutputStream.nullOutputStream():Files.newOutputStream(poolsPath,StandardOpenOption.CREATE_NEW),budget,check);
            var counts=new Sink(replay?OutputStream.nullOutputStream():Files.newOutputStream(countsPath,StandardOpenOption.CREATE_NEW),budget,check)) {
            String header=in.line();require(header!=null,"Missing raw count header");if(header.startsWith("\ufeff"))header=header.substring(1);
            require(header.equals("cell\tmc_type\tfeature\tmc\tcov"),"Require exact TSV count header: cell, mc_type, feature, mc, cov");
            values.text("cell\tmc_type\tfeature\traw_mc\traw_cov\tcount_mc\tcount_cov\thypo_score\thyper_score\n");
            pools.text("cell\tmc_type\tsum_raw_mc\tsum_raw_cov\tpooled_probability\tsaturated_mc\tsaturated_cov\tzero_coverage_features\n");
            counts.text("cell\tmc_type\tfeature\tmc\tcov\n");
            Set<String> featureSet=new HashSet<>(),typeSet=new HashSet<>();
            for(int ci=0;ci<spec.cells();ci++) {
                String cell=null;
                for(int ti=0;ti<spec.types();ti++) {
                    String type=null;
                    for(int fi=0;fi<spec.features();fi++) {
                        check.run();String line=in.line();require(line!=null,"Incomplete declared cell/type/feature population at row "+(rows+1));
                        String[] f=line.split("\t",-1);require(f.length==5,"Require five literal fields; blank or extra fields are refused");
                        for(int i=0;i<3;i++)AllcoolsPosteriorRequest.scalar(f[i],32768);
                        if(fi==0) {
                            type=f[1];
                            if(ti==0){cell=f[0];require(cells.add(cell),"Repeated cell block; no reordering or merging");}
                            if(ci==0){types[ti]=type;require(typeSet.add(type),"Repeated mc_type within a cell");}
                        }
                        require(f[0].equals(cell)&&f[1].equals(types[ti])&&f[1].equals(type),"Cell/type order differs from declared complete rectangle");
                        if(ci==0&&ti==0){features[fi]=f[2];require(featureSet.add(f[2]),"Repeated feature identifier");}
                        require(features[fi].equals(f[2]),"Feature axes differ; no intersection, sorting, dropping or imputation");
                        mc[fi]=count(f[3]);cov[fi]=count(f[4]);require(mc[fi]<=cov[fi],"mc must not exceed cov");rows++;
                    }
                    var r=AllcoolsRegionQuantifier.calculate(mc,cov,spec.hypoCutoff(),spec.hyperCutoff(),check);
                    long[] outputMc=r.countsMc(),outputCov=r.countsCov();double[] hypo=r.hypo(),hyper=r.hyper();
                    for(int i=0;i<spec.features();i++) {
                        check.run();String prefix=String.join("\t",cell,type,features[i])+"\t";
                        values.text(prefix+String.join("\t",Long.toString(mc[i]),Long.toString(cov[i]),Long.toString(outputMc[i]),Long.toString(outputCov[i]),Double.toString(hypo[i]),Double.toString(hyper[i]))+"\n");
                        counts.text(prefix+outputMc[i]+"\t"+outputCov[i]+"\n");
                        if(hypo[i]!=0)hypoNonzero++;if(hyper[i]!=0)hyperNonzero++;if(cov[i]==0&&hyper[i]==1)zeroHyper++;
                    }
                    pools.text(String.join("\t",cell,type,Long.toString(r.sumMc()),Long.toString(r.sumCov()),Double.toString(r.pooledProbability()),Integer.toString(r.saturatedMc()),Integer.toString(r.saturatedCov()),Integer.toString(r.zeroCoverage()))+"\n");
                    groups++;zero+=r.zeroCoverage();saturatedMc+=r.saturatedMc();saturatedCov+=r.saturatedCov();
                }
            }
            require(in.line()==null,"Extra records beyond complete declared population");
            require(rows==Math.multiplyExact(Math.multiplyExact((long)spec.cells(),spec.types()),spec.features()),"Complete row accounting differs");
            values.flush();pools.flush();counts.flush();valuesPin=values.finish();poolsPin=pools.finish();countsPin=counts.finish();
        }
        if(replay)require(pin(valuesPath,spec.capacity().outputBytes(),check).equals(valuesPin)&&pin(poolsPath,spec.capacity().outputBytes(),check).equals(poolsPin)&&pin(countsPath,spec.capacity().outputBytes(),check).equals(countsPin),"Saved count/score/pool tables differ from full original-input numerical replay");
        return map("schema_version",1,"status","COMPLETE_NATIVE_ALLCOOLS_REGION_QUANTIFICATION","complete_rows",rows,"complete_groups",groups,"cells",spec.cells(),"mc_types",spec.types(),"features_per_group",spec.features(),"zero_coverage_rows",zero,"saturated_mc_rows",saturatedMc,"saturated_cov_rows",saturatedCov,"hypo_nonzero_rows",hypoNonzero,"hyper_nonzero_rows",hyperNonzero,"zero_coverage_hyper_one_rows",zeroHyper,"outputs",map("values.tsv",valuesPin,"pools.tsv",poolsPin,"posterior-counts.tsv",countsPin),"source_behavior","ALLCools-1.0.8-uint32-binary16","full_original_input_retained",true,"axes_repaired",false,"original_runtime_executed",false,"raw_ALLC_or_Zarr_preparation",false,"whole_ALLCools_package_admitted",false,"whole_paper_reproduced",false);
    }
    private static long count(String value)throws IOException {
        require(value.matches("0|[1-9][0-9]{0,18}"),"Require literal nonnegative int64 count; signs, leading zeros, decimal and missing values are refused");
        try{return Long.parseLong(value);}catch(NumberFormatException e){throw new IOException("Raw count exceeds int64 maximum",e);}
    }
    private static final class Budget {final long maximum;long written;Budget(long maximum){this.maximum=maximum;}}
    private static final class Sink extends OutputStream {
        final OutputStream output;final MessageDigest digest;final Budget budget;final Runnable check;long count;boolean finished;
        Sink(OutputStream out,Budget budget,Runnable check){output=new BufferedOutputStream(out,65536);this.budget=budget;this.check=check;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
        void text(String value)throws IOException{write(value.getBytes(StandardCharsets.UTF_8));}
        @Override public void write(int value)throws IOException{write(new byte[]{(byte)value});}
        @Override public void write(byte[] b,int off,int len)throws IOException {check.run();require(!finished&&len<=budget.maximum-budget.written,"Complete quantification output byte capacity exceeded");output.write(b,off,len);digest.update(b,off,len);count+=len;budget.written+=len;}
        @Override public void flush()throws IOException{output.flush();}
        Pin finish(){if(finished)throw new IllegalStateException("Digest finalized twice");finished=true;return new Pin(count,HexFormat.of().formatHex(digest.digest()));}
        @Override public void close()throws IOException{output.close();}
    }
    private static final class Table implements AutoCloseable {
        final PushbackReader input;final Runnable check;
        Table(Path path,Runnable check)throws IOException {
            this.check=check;var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            input=new PushbackReader(new BufferedReader(new InputStreamReader(Files.newInputStream(path),decoder),65536),1);
        }
        String line()throws IOException {check.run();var line=new StringBuilder();for(int c=input.read();c!=-1;c=input.read()){if(c=='\n')return line.toString();if(c=='\r'){int n=input.read();if(n!=-1&&n!='\n')input.unread(n);return line.toString();}require(line.length()<1048576,"Typed record exceeds one MiB characters");line.append((char)c);}return line.isEmpty()?null:line.toString();}
        @Override public void close()throws IOException{input.close();}
    }
}
