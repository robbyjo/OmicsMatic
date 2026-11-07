/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Typed PFM counts/background declarations for original TFBSTools/motifmatchr preparation. */
public final class SignacPfmIO {
    private SignacPfmIO() { }
    public static final String HEADER="motif_id\tmotif_name\tposition\tA\tC\tG\tT\tbg_A\tbg_C\tbg_G\tbg_T";
    public record Limits(long bytes,int motifs,int width,int positions,int lineCharacters) {
        public Limits {if(bytes<1||motifs<1||motifs>100_000||width<1||width>4096||positions<1||positions>2_000_000||lineCharacters<64||lineCharacters>1_000_000)throw new IllegalArgumentException("Invalid typed PFM capacities");}
        public static Limits defaults(){return new Limits(256_000_000L,100_000,512,2_000_000,16384);}
    }
    public record Model(String id,String name,double[][] positions,double[] background) {
        public Model {
            SignacFragments.identity(id);SignacFragments.identity(name);positions=Arrays.stream(positions).map(double[]::clone).toArray(double[][]::new);background=background.clone();if(positions.length<1||positions.length>4096||background.length!=4)throw new IllegalArgumentException("Require nonempty four-base PFM and background");double sum=0;
            for(double p:background){if(!Double.isFinite(p)||p<=0||p>1)throw new IllegalArgumentException("Require finite positive PFM background probabilities");sum+=p;}if(Math.abs(sum-1)>2e-12)throw new IllegalArgumentException("PFM background probabilities must sum to one; no normalization was applied");
            for(double[] row:positions){if(row.length!=4)throw new IllegalArgumentException("Require four PFM counts per position");double total=0;for(double value:row){if(!Double.isFinite(value)||value<0||value>1e12)throw new IllegalArgumentException("Require finite nonnegative declared PFM counts at most1e12");total+=value;}if(!(total>0))throw new IllegalArgumentException("Each PFM position requires a positive total");}
        }
        @Override public double[][] positions(){return Arrays.stream(positions).map(double[]::clone).toArray(double[][]::new);}
        @Override public double[] background(){return background.clone();}
    }
    public static List<Model> read(Path path,Limits limits,BooleanSupplier cancelled)throws IOException{
        Objects.requireNonNull(limits);Objects.requireNonNull(cancelled);if(!Files.isRegularFile(path)||Files.size(path)>limits.bytes)throw new IOException("PFM TSV unavailable or exceeds byte capacity");List<Model> models=new ArrayList<>();List<double[]> rows=new ArrayList<>();Set<String> seen=new HashSet<>();String current=null,name=null;double[] background=null;int count=0;
        try(var in=new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))){
            if(!HEADER.equals(line(in,limits,cancelled)))throw new IOException("Require the exact typed PFM header");String raw;
            while((raw=line(in,limits,cancelled))!=null){String[] f=raw.split("\t",-1);if(f.length!=11||++count>limits.positions)throw new IOException("Ragged/excessive PFM positions");SignacFragments.identity(f[0]);SignacFragments.identity(f[1]);
                if(!f[0].equals(current)){
                    if(current!=null)models.add(new Model(current,name,rows.toArray(double[][]::new),background));if(models.size()>=limits.motifs||!seen.add(f[0]))throw new IOException("Excessive motifs or noncontiguous/repeated motif ID");current=f[0];name=f[1];rows=new ArrayList<>();background=new double[]{number(f[7]),number(f[8]),number(f[9]),number(f[10])};
                }
                if(!name.equals(f[1]))throw new IOException("PFM display name changes within a motif");if(!f[2].matches("[0-9]+")||!f[2].equals(Integer.toString(rows.size()+1))||rows.size()>=limits.width)throw new IOException("PFM positions must be consecutive canonical1-based integers within capacity");
                for(int i=0;i<4;i++)if(number(f[i+7])!=background[i])throw new IOException("PFM background changes within a motif");rows.add(new double[]{number(f[3]),number(f[4]),number(f[5]),number(f[6])});
            }
        }catch(IllegalArgumentException error){throw new IOException("Invalid typed PFM model",error);}
        if(current==null)throw new IOException("No PFM models supplied");try{models.add(new Model(current,name,rows.toArray(double[][]::new),background));}catch(IllegalArgumentException error){throw new IOException("Invalid typed PFM model",error);}return List.copyOf(models);
    }
    private static double number(String value)throws IOException{try{if(!value.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new NumberFormatException();double n=Double.parseDouble(value);if(!Double.isFinite(n))throw new NumberFormatException();return n;}catch(NumberFormatException error){throw new IOException("PFM values require finite nonnegative decimal declarations",error);}}
    private static String line(BufferedReader in,Limits limits,BooleanSupplier cancelled)throws IOException{SignacFragments.check(cancelled);StringBuilder value=new StringBuilder();int c;while((c=in.read())>=0&&c!='\n'){if(value.length()>=limits.lineCharacters)throw new IOException("PFM line capacity exceeded");value.append((char)c);}if(c<0&&value.isEmpty())return null;if(!value.isEmpty()&&value.charAt(value.length()-1)=='\r')value.setLength(value.length()-1);return value.toString();}
}
