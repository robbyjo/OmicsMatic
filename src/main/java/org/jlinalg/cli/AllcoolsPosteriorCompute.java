/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.omics.methylation.AllcoolsPosterior;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

final class AllcoolsPosteriorCompute {
    private AllcoolsPosteriorCompute() {}
    static Map<String,Object> map(Object... pairs) {var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    static String number(double v) {return Double.isNaN(v)?"NaN":v==Double.POSITIVE_INFINITY?"Inf":v==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(v);}
    static Map<String,Object> run(Path root,AllcoolsPosteriorRequest.Spec spec,boolean replay,Runnable check)throws IOException {
        String[] types=new String[spec.types()],features=new String[spec.features()];var cells=new HashSet<String>();
        long[] mc=new long[spec.features()],cov=new long[spec.features()];long rows=0,groups=0,covered=0,zero=0,replaced=0,clipped=0;
        var rawMarkers=new Markers();var postMarkers=new Markers();var valueMarkers=new Markers();var priorMarkers=new Markers();
        Path valuesFile=root.resolve("outputs/values.tsv"),priorsFile=root.resolve("outputs/priors.tsv");var budget=new Budget(spec.capacity().outputBytes());Pin valuePin,priorPin;
        try(var in=new Table(root.resolve("inputs/counts.tsv"),spec.capacity().inputBytes(),check);
            var values=new Sink(replay?OutputStream.nullOutputStream():Files.newOutputStream(valuesFile,StandardOpenOption.CREATE_NEW),budget,check);
            var priors=new Sink(replay?OutputStream.nullOutputStream():Files.newOutputStream(priorsFile,StandardOpenOption.CREATE_NEW),budget,check)) {
            String header=in.line();require(header!=null,"Missing typed count header");if(header.startsWith("\ufeff"))header=header.substring(1);
            require(header.equals("cell\tmc_type\tfeature\tmc\tcov"),"Require exact count header: cell, mc_type, feature, mc, cov (TSV)");
            values.text("cell\tmc_type\tfeature\tmc\tcov\traw_fraction\tposterior_fraction\tvalue\n");
            priors.text("cell\tmc_type\tmean\tvariance\talpha\tbeta\tprior_mean\tnormalization_divisor\tcovered_features\tzero_coverage_features\tpreclip_nans_replaced\tclipped_features\n");
            Set<String> featureSet=new HashSet<>(),typeSet=new HashSet<>();
            for(int cellIndex=0;cellIndex<spec.cells();cellIndex++) {
                String cell=null;
                for(int typeIndex=0;typeIndex<spec.types();typeIndex++) {
                    String type=null;
                    for(int featureIndex=0;featureIndex<spec.features();featureIndex++) {
                        check.run();String line=in.line();require(line!=null,"Incomplete declared cell/type/feature population at row "+(rows+1));
                        String[] f=line.split("\t",-1);require(f.length==5,"Require five literal fields; blank/extra fields are not repaired");
                        for(int i=0;i<3;i++)AllcoolsPosteriorRequest.scalar(f[i],32768);
                        if(featureIndex==0) {
                            type=f[1];
                            if(typeIndex==0){cell=f[0];require(cells.add(cell),"Repeated cell block is not reordered or merged");}
                            if(cellIndex==0){types[typeIndex]=type;require(typeSet.add(type),"Repeated mc_type within a cell");}
                        }
                        require(f[0].equals(cell)&&f[1].equals(types[typeIndex])&&f[1].equals(type),"Cell/type block differs from complete declared rectangular order");
                        if(cellIndex==0&&typeIndex==0){features[featureIndex]=f[2];require(featureSet.add(f[2]),"Repeated feature identifier");}
                        require(features[featureIndex].equals(f[2]),"Feature order differs; no intersection, sorting, imputation or dropping");
                        mc[featureIndex]=count(f[3]);cov[featureIndex]=count(f[4]);require(mc[featureIndex]<=cov[featureIndex],"mc must not exceed cov");rows++;
                    }
                    var result=AllcoolsPosterior.calculate(mc,cov,spec.settings(),check);double[] posterior=result.posterior(),output=result.values();
                    for(int i=0;i<spec.features();i++) {
                        check.run();double raw=(double)mc[i]/cov[i];rawMarkers.add(raw);postMarkers.add(posterior[i]);valueMarkers.add(output[i]);
                        values.text(String.join("\t",cell,type,features[i],Long.toString(mc[i]),Long.toString(cov[i]),number(raw),number(posterior[i]),number(output[i]))+"\n");
                    }
                    double[] p={result.mean(),result.variance(),result.alpha(),result.beta(),result.priorMean(),result.normalizationDivisor()};for(double v:p)priorMarkers.add(v);
                    priors.text(String.join("\t",cell,type,number(p[0]),number(p[1]),number(p[2]),number(p[3]),number(p[4]),number(p[5]),Integer.toString(result.coveredFeatures()),Integer.toString(result.zeroCoverageFeatures()),Integer.toString(result.preclipNaNsReplaced()),Integer.toString(result.clippedFeatures()))+"\n");
                    groups++;covered+=result.coveredFeatures();zero+=result.zeroCoverageFeatures();replaced+=result.preclipNaNsReplaced();clipped+=result.clippedFeatures();
                }
            }
            require(in.line()==null,"Extra records beyond declared complete population; no subsampling");
            require(rows==Math.multiplyExact(Math.multiplyExact((long)spec.cells(),spec.types()),spec.features())&&rows==covered+zero,"Complete count accounting differs");
            values.flush();priors.flush();valuePin=values.finish();priorPin=priors.finish();
        }
        if(replay)require(pin(valuesFile,spec.capacity().outputBytes(),check).equals(valuePin)&&pin(priorsFile,spec.capacity().outputBytes(),check).equals(priorPin),"Saved posterior/prior tables differ from complete original-input numerical replay");
        return map("schema_version",1,"status","COMPLETE_NATIVE_ALLCOOLS_PREPARED_POSTERIOR","complete_rows",rows,"complete_groups",groups,"cells",spec.cells(),"mc_types",spec.types(),"features_per_group",spec.features(),"covered_rows",covered,"zero_coverage_rows",zero,"preclip_nans_replaced",replaced,"clipped_rows",clipped,"raw_fraction_markers",rawMarkers.value(),"posterior_markers",postMarkers.value(),"value_markers",valueMarkers.value(),"prior_parameter_markers",priorMarkers.value(),"outputs",map("values.tsv",valuePin,"priors.tsv",priorPin),"source_behavior","ALLCools-1.0.8-float64","full_original_input_retained",true,"counts_or_axes_repaired",false,"original_runtime_executed",false,"whole_ALLCools_package_admitted",false,"whole_paper_reproduced",false);
    }
    private static long count(String v)throws IOException {
        require(v.matches("0|[1-9][0-9]{0,9}"),"Require literal uint32 count; decimal, sign, leading zeros and missing values are refused");
        long n=Long.parseLong(v);require(n<=4294967295L,"Count exceeds uint32 maximum");return n;
    }
    private static final class Markers {
        long finite,nan,positive,negative;
        void add(double v){if(Double.isNaN(v))nan++;else if(v==Double.POSITIVE_INFINITY)positive++;else if(v==Double.NEGATIVE_INFINITY)negative++;else finite++;}
        Object value(){return map("finite",finite,"NaN",nan,"Inf",positive,"-Inf",negative);}
    }
    private static final class Budget { final long maximum;long written;Budget(long maximum){this.maximum=maximum;} }
    private static final class Sink extends OutputStream {
        final OutputStream output;final MessageDigest digest;final Budget budget;final Runnable check;long count;boolean finished;
        Sink(OutputStream out,Budget budget,Runnable check){this.output=new BufferedOutputStream(out,65536);this.budget=budget;this.check=check;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
        void text(String value)throws IOException{write(value.getBytes(StandardCharsets.UTF_8));}
        @Override public void write(int value)throws IOException{write(new byte[]{(byte)value});}
        @Override public void write(byte[] b,int off,int len)throws IOException {check.run();require(!finished&&len<=budget.maximum-budget.written,"Complete posterior output byte capacity exceeded");output.write(b,off,len);digest.update(b,off,len);count+=len;budget.written+=len;}
        @Override public void flush()throws IOException{output.flush();}
        Pin finish(){if(finished)throw new IllegalStateException("Digest finalized twice");finished=true;return new Pin(count,HexFormat.of().formatHex(digest.digest()));}
        @Override public void close()throws IOException{output.close();}
    }
    private static final class Table implements AutoCloseable {
        final PushbackReader input;final Runnable check;long bytes;
        Table(Path path,long maximum,Runnable check)throws IOException {
            this.check=check;var bounded=new FilterInputStream(Files.newInputStream(path)) {
                private void add(int n)throws IOException {if(n>0){check.run();require(n<=maximum-bytes,"Complete count input byte capacity exceeded");bytes+=n;}}
                @Override public int read()throws IOException{int n=in.read();if(n>=0)add(1);return n;}
                @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);add(n);return n;}
            };
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            input=new PushbackReader(new BufferedReader(new InputStreamReader(bounded,decoder),65536),1);
        }
        String line()throws IOException {check.run();var line=new StringBuilder();for(int c=input.read();c!=-1;c=input.read()){if(c=='\n')return line.toString();if(c=='\r'){int n=input.read();if(n!=-1&&n!='\n')input.unread(n);return line.toString();}require(line.length()<1048576,"Typed record exceeds one MiB characters");line.append((char)c);}return line.isEmpty()?null:line.toString();}
        @Override public void close()throws IOException{input.close();}
    }
}
