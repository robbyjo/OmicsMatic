/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.PrsCsMath;

/** Stream exact original posterior strings, verify identities and bounded rounding intervals. */
final class PrsCsOutputs {
    private PrsCsOutputs() { }
    record Result(long variants,long posteriorScalars,long psiFields,long meanDrawChecks,Map<String,String> originalHashes) { }
    record WarningCounts(int numpyArrayToScalar,int scipyAliases) { }
    private static final Map<String,String> LEGACY_ALIASES=Map.of("array","numpy.array","diag","numpy.diag","dot","numpy.dot","ix_","numpy.ix_","log","numpy.lib.scimath.log","ones","numpy.ones","outer","numpy.outer","sign","numpy.sign","sqrt","numpy.lib.scimath.sqrt","zeros","numpy.zeros");
    private record Printed(String text,BigDecimal value,BigDecimal radius,double numeric) { }
    private static final class Writer implements AutoCloseable {
        private final BufferedWriter stream;
        private final long[] bytes;
        private final long maximum;
        Writer(Path file,long[] bytes,long maximum)throws IOException {stream=Files.newBufferedWriter(file);this.bytes=bytes;this.maximum=maximum;}
        void write(String value)throws IOException {
            bytes[0]=Math.addExact(bytes[0],value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            if(bytes[0]>maximum)throw new IOException("PRS-CS canonical report-byte ceiling exceeded");stream.write(value);
        }
        @Override public void close()throws IOException{stream.close();}
    }

    static Result validate(PrsCsInputs.Prepared input,Path mean,Path draws,boolean psi,int retained,String units,boolean scoreExport,Path out,long availableBytes)throws Exception {
        if(mean==null&&draws==null||scoreExport&&mean==null)throw new IllegalArgumentException("PRS-CS output validation requires requested posterior mode;score export needs a mean run");
        long variants=0,scalars=0,psiFields=0,checks=0;long[] bytes={0};var hashes=new LinkedHashMap<String,String>();
        try(var means=mean==null?null:new Writer(out.resolve("posterior-means.tsv"),bytes,availableBytes);
                var samples=draws==null?null:new Writer(out.resolve("posterior-draws.tsv"),bytes,availableBytes);
                var shrinkage=!psi?null:new Writer(out.resolve("shrinkage.tsv"),bytes,availableBytes);
                var score=!scoreExport?null:new Writer(out.resolve("score-weights.tsv"),bytes,availableBytes)) {
            String identity="chromosome\tvariant\tposition\tea\toa\treference_ea_frequency\teffect_units";
            if(means!=null)means.write(identity+"\tposterior_mean_raw\n");
            if(samples!=null){samples.write(identity);for(int d=1;d<=retained;d++)samples.write("\tdraw_"+d);samples.write("\n");}
            if(shrinkage!=null)shrinkage.write("chromosome\tvariant\tposterior_psi_raw\n");
            if(score!=null)score.write("variant\tea\toa\tweight\tea_frequency\n");
            for(var entry:input.model().entrySet()) {
                int chromosome=entry.getKey();Path meanFile=mean==null?null:original(mean,"eff",chromosome),drawFile=draws==null?null:original(draws,"eff",chromosome);
                Path psiFile=!psi?null:original(mean==null?draws:mean,"psi",chromosome),drawPsi=psi&&mean!=null&&draws!=null?original(draws,"psi",chromosome):null;
                for(Path p:List.of(mean==null?out:meanFile,draws==null?out:drawFile,psi?psiFile:out,drawPsi==null?out:drawPsi))if(!p.equals(out))hashes.put(out.relativize(p).toString().replace('\\','/'),hash(p));
                try(var meanStream=open(meanFile);var drawStream=open(drawFile);var psiStream=open(psiFile);var drawPsiStream=open(drawPsi)) {
                    for(var v:entry.getValue()) {
                        String prefix=v.chromosome()+"\t"+v.id()+"\t"+v.position()+"\t"+v.effectAllele()+"\t"+v.otherAllele()+"\t"+v.frequency()+"\t"+units;
                        Printed m=null;
                        if(meanStream!=null) {
                            String[] row=effectRow(meanStream,input,v,1);m=printed(row[5]);means.write(prefix+"\t"+m.text()+"\n");scalars++;
                            if(score!=null) {
                                String value=units.equals("per-allele")?m.text():Double.toString(PrsCsMath.perAllele(m.numeric(),v.frequency()));
                                score.write(v.id()+"\t"+v.effectAllele()+"\t"+v.otherAllele()+"\t"+value+"\t"+v.frequency()+"\n");
                            }
                        }
                        if(drawStream!=null) {
                            String[] row=effectRow(drawStream,input,v,retained);BigDecimal sum=BigDecimal.ZERO,radius=BigDecimal.ZERO;samples.write(prefix);
                            for(int d=0;d<retained;d++) {Printed p=printed(row[5+d]);samples.write("\t"+p.text());sum=sum.add(p.value());radius=radius.add(p.radius());scalars++;}
                            samples.write("\n");
                            if(m!=null) {
                                BigDecimal count=BigDecimal.valueOf(retained),difference=sum.subtract(m.value().multiply(count)).abs();
                                BigDecimal bound=radius.add(m.radius().multiply(count)).add(BigDecimal.valueOf(32*Math.ulp(m.numeric())).multiply(count));
                                if(difference.compareTo(bound)>0)throw new IOException("Original mean and same-seed retained draws disagree beyond seven-significant-digit printing intervals at "+v.id());checks++;
                            }
                        }
                        if(psiStream!=null) {
                            String[] row=psiRow(psiStream,input,v);Printed p=printed(row[1]);if(p.numeric()<=0||p.numeric()>1)throw new IOException("Original posterior psi outside (0,1] at "+v.id());
                            if(drawPsiStream!=null&&!java.util.Arrays.equals(row,psiRow(drawPsiStream,input,v)))throw new IOException("Same-seed mean/draw runs disagree on original psi at "+v.id());
                            shrinkage.write(v.chromosome()+"\t"+v.id()+"\t"+p.text()+"\n");psiFields++;
                        }
                        variants++;
                    }
                    for(InputStream stream:List.of(meanStream==null?InputStream.nullInputStream():meanStream,drawStream==null?InputStream.nullInputStream():drawStream,psiStream==null?InputStream.nullInputStream():psiStream,drawPsiStream==null?InputStream.nullInputStream():drawPsiStream))
                        if(line(stream,input)!=null)throw new IOException("Extra original PRS-CS effect/shrinkage output rows");
                }
            }
        }
        for(Path directory:List.of(mean==null?out:mean,draws==null?out:draws))if(!directory.equals(out)) {
            try(var files=Files.list(directory)) {
                for(Path file:files.filter(Files::isRegularFile).toList())if(file.getFileName().toString().startsWith("result_pst_")&&!hashes.containsKey(out.relativize(file).toString().replace('\\','/')))throw new IOException("Unexpected original posterior output;version capability/mode differs: "+file.getFileName());
            }
        }
        var result=new Result(variants,scalars,psiFields,checks,Map.copyOf(hashes));verify(result,out);return result;
    }
    static void verify(Result result,Path out)throws IOException {for(var e:result.originalHashes().entrySet())if(!hash(out.resolve(e.getKey())).equals(e.getValue()))throw new IOException("Original PRS-CS posterior output changed: "+e.getKey());}
    static WarningCounts warnings(Path stderr,int maximumLineBytes,boolean legacy)throws IOException {
        int numpy=0,scipy=0;boolean continuation=false;
        try(var stream=new BufferedInputStream(Files.newInputStream(stderr))) {
            for(String text;(text=PlinkGlmInputs.line(stream,maximumLineBytes))!=null;) {
                if(text.isBlank())continue;
                if(!legacy&&text.matches(".*(?:gigrnd|mcmc_gtb)\\.py:[0-9]+: DeprecationWarning: Conversion of an array with ndim > 0 to a scalar is deprecated, and will error in future\\. Ensure you extract a single element from your array before performing this operation\\. \\(Deprecated NumPy 1\\.25\\.\\)")) {numpy++;continuation=true;}
                else if(legacy&&legacyAliasWarning(text)) {scipy++;continuation=true;}
                else if(continuation&&text.startsWith("  ")&&!text.contains("Warning:")&&!text.contains("Error:"))continuation=false;
                else throw new IOException("Unreviewed original PRS-CS stderr/warning;inspect "+stderr+" before using outputs");
            }
        }
        return new WarningCounts(numpy,scipy);
    }
    private static boolean legacyAliasWarning(String text) {
        String marker=": DeprecationWarning: ";int start=text.indexOf(marker);
        if(start<0||!text.substring(0,start).matches(".*(?:parse_genet|mcmc_gtb|gigrnd)\\.py:[0-9]+"))return false;
        String message=text.substring(start+marker.length());
        for(var alias:LEGACY_ALIASES.entrySet())if(message.equals("scipy."+alias.getKey()+" is deprecated and will be removed in SciPy 2.0.0, use "+alias.getValue()+" instead"))return true;
        return false;
    }
    private static Path original(Path directory,String type,int chromosome)throws IOException {
        List<Path> paths;try(var files=Files.list(directory)){paths=files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().matches("result_pst_"+type+"_a[^/]+_chr"+chromosome+"\\.txt")).toList();}
        if(paths.size()!=1)throw new IOException("Require exactly one original PRS-CS "+type+" file for chromosome "+chromosome);return paths.get(0);
    }
    private static InputStream open(Path p)throws IOException{return p==null?null:new BufferedInputStream(Files.newInputStream(p));}
    private static String line(InputStream stream,PrsCsInputs.Prepared input)throws IOException{return PlinkGlmInputs.line(stream,input.limits().lineBytes());}
    private static String[] effectRow(InputStream stream,PrsCsInputs.Prepared input,PrsCsInputs.Variant v,int columns)throws IOException {
        String text=line(stream,input);if(text==null)throw new IOException("Missing original effect row for "+v.id());String[] row=text.split("\t",-1);
        if(row.length!=5+columns||!row[0].equals(Integer.toString(v.chromosome()))||!row[1].equals(v.id())||!row[2].equals(Integer.toString(v.position()))||!row[3].equals(Character.toString(v.effectAllele()))||!row[4].equals(Character.toString(v.otherAllele())))throw new IOException("Original posterior SNP/order/position/allele/width differs at "+v.id());return row;
    }
    private static String[] psiRow(InputStream stream,PrsCsInputs.Prepared input,PrsCsInputs.Variant v)throws IOException {
        String text=line(stream,input);if(text==null)throw new IOException("Missing original psi row for "+v.id());String[] row=text.split("\t",-1);if(row.length!=2||!row[0].equals(v.id()))throw new IOException("Original psi SNP/order/width differs at "+v.id());return row;
    }
    private static Printed printed(String text)throws IOException {
        if(!text.matches("[+-]?[0-9]\\.[0-9]{6}e[+-][0-9]{2,3}"))throw new IOException("Original PRS-CS numeric printing format differs");
        var value=new BigDecimal(text);double numeric=Double.parseDouble(text);
        if(!Double.isFinite(numeric)||numeric==0&&value.signum()!=0)throw new IOException("Nonfinite/underflowed original posterior value");
        int exponent=Integer.parseInt(text.substring(text.indexOf('e')+1));BigDecimal radius=value.signum()==0?BigDecimal.ZERO:BigDecimal.valueOf(5).scaleByPowerOfTen(exponent-7);
        return new Printed(text,value,radius,numeric);
    }
}
