/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.jlinalg.singlecell.ScviLikelihood;

/** Complete ordered native checks for original model exports; no row sampling or label sorting. */
final class ScviOutputAudit {
    private ScviOutputAudit() { }
    record Limits(long bytes,long rows,int columns,int lineCharacters) {
        Limits{if(bytes<1||rows<1||columns<1||lineCharacters<32)throw new IllegalArgumentException("Positive complete scVI output capacities required");}
        static Limits health(){return new Limits(67_108_864,1_000_000,8192,1_048_576);}
    }
    record Matrix(long rows,long values,String orderedFloat32Sha256) { }
    record Classification(Matrix probabilities,long labels) { }
    record Splits(long train,long validation,long test) { }
    record History(long metrics,long observations) { }
    private static ScviText.Lines lines(Path p,Limits l,Runnable check)throws IOException{ScviText.ordinary(p);ScviText.hash(p,l.bytes,check);return new ScviText.Lines(new ScviText.Source(p,ScviText.Encoding.PLAIN_UTF8),new ScviText.DecodedBudget(l.bytes),l.lineCharacters,check);}
    private static void population(List<String> ids,int columns,Limits l)throws IOException{if(ids.isEmpty()||ids.size()>l.rows||columns<1||columns>l.columns)throw new IOException("Complete model output population exceeds declared capacity");if(new HashSet<>(ids).size()!=ids.size())throw new IOException("Expected model population has duplicate IDs");}
    static Matrix latent(Path file,List<String> cells,int dimensions,Limits limits,Runnable check)throws IOException{
        population(cells,dimensions,limits);var hash=digest();
        try(var in=lines(file,limits,check)){
            var header=fields(in.next(),dimensions+1);if(!header[0].equals("cell_id"))throw new IOException("Latent output requires cell_id header");for(int j=0;j<dimensions;j++)if(!header[j+1].equals("latent_"+j))throw new IOException("Latent dimension order differs");
            for(String expected:cells){check.run();String[] row=fields(in.next(),dimensions+1);if(!row[0].equals(expected))throw new IOException("Latent output cell identity/order differs; expected "+expected);frame(hash,expected);for(int j=1;j<row.length;j++)bits(hash,float32(row[j]));}
            if(in.next()!=null)throw new IOException("Extra model latent output rows");
        }
        return new Matrix(cells.size(),Math.multiplyExact((long)cells.size(),dimensions),HexFormat.of().formatHex(hash.digest()));
    }
    static Classification classification(Path probabilities,Path labels,List<String> cells,List<String> classes,Limits limits,Runnable check)throws IOException{
        population(cells,classes.size(),limits);if(new HashSet<>(classes).size()!=classes.size()||classes.stream().anyMatch(String::isBlank))throw new IOException("Unique learned class order required");var hash=digest();
        try(var in=lines(probabilities,limits,check);var hard=lines(labels,limits,check)){
            String[] header=fields(in.next(),classes.size()+1);if(!header[0].equals("cell_id")||!Arrays.asList(header).subList(1,header.length).equals(classes))throw new IOException("Probability header differs from the learned class registry/order");
            if(!Arrays.equals(fields(hard.next(),2),new String[]{"cell_id","label"}))throw new IOException("Hard label header differs");
            for(String expected:cells){check.run();String[] row=fields(in.next(),classes.size()+1),label=fields(hard.next(),2);if(!row[0].equals(expected)||!label[0].equals(expected))throw new IOException("Classification cell identity/order differs; expected "+expected);frame(hash,expected);double sum=0;float best=-1;int argmax=0;
                for(int j=0;j<classes.size();j++){float value=float32(row[j+1]);if(value<0||value>1)throw new IOException("Class probability outside [0,1] for "+expected);sum+=value;bits(hash,value);if(value>best){best=value;argmax=j;}}
                if(Math.abs(sum-1)>Math.max(2e-6,classes.size()*Math.ulp(1.0f)))throw new IOException("Complete class probabilities do not sum to one for "+expected);
                if(!label[1].equals(classes.get(argmax)))throw new IOException("Original hard label differs from first-argmax learned class order for "+expected);
            }
            if(in.next()!=null||hard.next()!=null)throw new IOException("Extra classification output rows");
        }
        return new Classification(new Matrix(cells.size(),Math.multiplyExact((long)cells.size(),classes.size()),HexFormat.of().formatHex(hash.digest())),cells.size());
    }
    static Splits splits(Path file,List<String> cells,Limits limits,Runnable check)throws IOException{
        population(cells,1,limits);var remaining=new HashSet<>(cells);long train=0,validation=0,test=0;
        try(var in=lines(file,limits,check)){if(!Arrays.equals(fields(in.next(),2),new String[]{"partition","cell_id"}))throw new IOException("Training split header differs");String line;while((line=in.next())!=null){String[] row=fields(line,2);if(!remaining.remove(row[1]))throw new IOException("Duplicate or unknown training split cell: "+row[1]);switch(row[0]){case"train"->train++;case"validation"->validation++;case"test"->test++;default->throw new IOException("Unknown training partition");}}}
        if(!remaining.isEmpty()||train==0)throw new IOException("Training split does not account for the complete cell population");return new Splits(train,validation,test);
    }
    static History history(Path file,int epochs,Limits limits,Runnable check)throws IOException{
        if(epochs<1)throw new IllegalArgumentException("Positive training epoch bound required");var observations=new HashSet<String>();var metrics=new HashSet<String>();
        try(var in=lines(file,limits,check)){if(!Arrays.equals(fields(in.next(),3),new String[]{"metric","epoch","value"}))throw new IOException("Training history header differs");String line;while((line=in.next())!=null){String[] row=fields(line,3);if(row[0].isBlank())throw new IOException("Empty training metric");int epoch;try{epoch=Integer.parseInt(row[1]);}catch(NumberFormatException bad){throw new IOException("Invalid metric epoch",bad);}if(epoch<0||epoch>=epochs||!observations.add(row[0]+"\0"+epoch))throw new IOException("Duplicate/out-of-range training metric epoch");finite(row[2]);metrics.add(row[0]);if(observations.size()>limits.rows)throw new IOException("Complete training metric population exceeds capacity");}}
        if(observations.isEmpty())throw new IOException("Missing original training history; enable the supported dictionary logger");return new History(metrics.size(),observations.size());
    }
    static long kernels(Path file,Limits limits,Runnable check)throws IOException{
        if(limits.rows<24)throw new IOException("Complete scalar health population exceeds declared row capacity");
        long rows=0;try(var in=lines(file,limits,check)){
            if(!Arrays.equals(fields(in.next(),6),new String[]{"count","mean","theta","logit","nb","zinb"}))throw new IOException("Scalar health header differs");
            for(long count:new long[]{0,1,7})for(double mean:new double[]{.25,3})for(double theta:new double[]{.5,2})for(double logit:new double[]{-2,1}){
                String[] row=fields(in.next(),6);if(finite(row[0])!=count||finite(row[1])!=mean||finite(row[2])!=theta||finite(row[3])!=logit)throw new IOException("Complete original scalar health population/order differs");
                if(Math.abs(finite(row[4])-ScviLikelihood.logNegativeBinomial(count,mean,theta))>2e-12||Math.abs(finite(row[5])-ScviLikelihood.logZeroInflatedNegativeBinomial(count,mean,theta,logit))>2e-12)throw new IOException("Original scalar health differs from the native low-conditioning audit");rows++;
            }
            if(in.next()!=null)throw new IOException("Extra scalar health rows");
        }return rows;
    }
    private static String[] fields(String line,int n)throws IOException{if(line==null)throw new IOException("Truncated model output population");String[] f=line.split("\t",-1);if(f.length!=n)throw new IOException("Model output field population differs");return f;}
    private static double finite(String s)throws IOException{try{double x=Double.parseDouble(s);if(s.isBlank()||!Double.isFinite(x))throw new NumberFormatException();return x;}catch(NumberFormatException bad){throw new IOException("Nonfinite or malformed model output number",bad);}}
    private static float float32(String s)throws IOException{double x=finite(s);float f=(float)x;if(!Float.isFinite(f)||(double)f!=x)throw new IOException("Model export changed its declared exact float32 precision");return f;}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static void frame(MessageDigest h,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);h.update(ByteBuffer.allocate(4).putInt(bytes.length).array());h.update(bytes);}
    private static void bits(MessageDigest h,float x){h.update(ByteBuffer.allocate(4).putInt(Float.floatToRawIntBits(x)).array());}
}
