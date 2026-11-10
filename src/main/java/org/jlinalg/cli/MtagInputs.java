/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import static org.jlinalg.cli.MtagJson.*;

/** Complete original input-byte staging and bounded native TSV/selection/grid contracts. */
final class MtagInputs {
    private MtagInputs() { }
    record Staged(List<Path> traits,Map<Path,Path> copies,Map<Path,Map<String,Object>> originals,List<Long> rows,
                  Path ldReference,List<Path> include,List<Path> exclude,Path sigma,Path omega,Path grid,long decodedBytes) { }
    static final class Lines implements AutoCloseable {
        private final BufferedReader reader;
        private final long maximum;
        private long bytes;
        Lines(Path path,long maximum)throws IOException {
            this.maximum=maximum;
            InputStream in=Files.newInputStream(path);
            try {
                if(path.toString().endsWith(".gz"))in=new GZIPInputStream(in);
                else if(path.toString().endsWith(".bz2"))in=new BZip2CompressorInputStream(in,true);
                InputStream counted=new FilterInputStream(in) {
                    @Override public int read()throws IOException {int value=super.read();if(value!=-1)add(1);return value;}
                    @Override public int read(byte[] b,int start,int length)throws IOException {int n=this.in.read(b,start,length);if(n>0)add(n);return n;}
                    private void add(int n)throws IOException {bytes=Math.addExact(bytes,n);need(bytes<=Lines.this.maximum,"Complete decoded MTAG input-byte capacity exceeded");}
                };
                reader=new BufferedReader(new InputStreamReader(counted,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
            } catch(IOException|RuntimeException error){in.close();throw error;}
        }
        String next()throws IOException {
            var line=new StringBuilder();int c;
            while((c=reader.read())!=-1){if(c=='\n')break;line.append((char)c);need(line.length()<=1048576,"MTAG line exceeds1048576 characters; no truncation");}
            if(c==-1&&line.isEmpty())return null;
            if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);
            return line.toString();
        }
        long bytes(){return bytes;}
        @Override public void close()throws IOException {reader.close();}
    }
    static String[] fields(String line,int columns)throws IOException {
        String[] f=line.split("\t",-1);need(f.length==columns,"Complete MTAG TSV column count differs; unquoted tab-separated fields required");
        for(String s:f)need(!s.isEmpty()&&s.chars().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||c=='"'),"Empty, quoted or whitespace-containing MTAG field; use explicit source missing-value tokens instead");return f;
    }
    static boolean missing(String s){return List.of("NA","NaN","nan","N/A","NULL","null",".").contains(s);}
    static double numeric(String s,boolean missingAllowed)throws IOException {
        if(missingAllowed&&missing(s))return Double.NaN;
        try{double value=Double.parseDouble(s);need(Double.isFinite(value),"Nonfinite original numeric input outside the finite/missing MTAG profile; no repair");return value;}
        catch(NumberFormatException error){throw new IOException("Original MTAG numeric token is invalid: "+s,error);}
    }
    static String identifier(String value)throws IOException {
        need(!value.isEmpty()&&value.length()<=256&&value.chars().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||c=='"'),"Invalid literal MTAG SNP identifier");
        // An all-numeric ID column can be inferred as numbers by pandas and
        // lose leading zeros. Do not silently repair/relabel such identifiers.
        need(missing(value)||!value.matches("[+\\-]?[0-9]+(?:\\.[0-9]+)?"),"All-numeric SNP IDs are outside this profile because original pandas may coerce their identity; supply the genuine source IDs without repair");return value;
    }
    static Map<String,Integer> header(String line)throws IOException {
        need(line!=null&&!line.startsWith("\ufeff"),"Complete UTF-8 MTAG header without a BOM required");
        String[] columns=line.split("\t",-1);need(columns.length>=9&&columns.length<=2000,"MTAG summary header capacity/schema differs");var names=new LinkedHashMap<String,Integer>();
        for(int i=0;i<columns.length;i++){String s=columns[i];need(s.matches("[A-Za-z][A-Za-z0-9_.-]{0,79}")&&names.put(s,i)==null,"Duplicate, empty or invalid source column name; no automatic mangling");}return names;
    }
    static long[] summary(Path path,MtagRequest.Request r,long remaining)throws IOException {
        long rows=0;
        try(Lines lines=new Lines(path,remaining)){
            Map<String,Integer> header=header(lines.next());for(String name:r.columns().values())if(name!=null)need(header.containsKey(name),"Declared source column absent: "+name);
            for(String line;(line=lines.next())!=null;){need(++rows<=r.limits().rows(),"Complete original summary exceeds rows_per_trait; no sampling/truncation");String[] f=fields(line,header.size());
                identifier(f[header.get(r.columns().get("snp"))]);
                for(String kind:List.of("chr","bp","z","n","freq","p")){
                    double d=numeric(f[header.get(r.columns().get(kind))],true);if(Double.isNaN(d))continue;
                    if(kind.equals("chr"))need(d>=1&&d<=22&&d==Math.rint(d),"Explicit autosomal chromosome1..22 or missing token required");
                    if(kind.equals("bp"))need(d>=1&&d<=3_000_000_000L&&d==Math.rint(d),"Positive exact base-pair coordinate required");
                    if(kind.equals("n"))need(d>0&&d<=1e9,"Positive effective N<=1e9 required; no sample-size imputation or integer-product overflow");
                    if(kind.equals("freq")||kind.equals("p"))need(d>=0&&d<=1,"Original frequency/P outside[0,1]");
                }
                if(r.columns().get("info")!=null){double d=numeric(f[header.get(r.columns().get("info"))],true);need(Double.isNaN(d)||d>=0&&d<=1,"INFO outside[0,1]");}
                for(String kind:List.of("a1","a2")){String value=f[header.get(r.columns().get(kind))];need(value.length()<=1000,"Original allele token capacity exceeded");}
            }
            need(rows>0,"Empty original MTAG summary; no result admitted");return new long[]{rows,lines.bytes()};
        }
    }
    static Staged stage(MtagRequest.Request r,MtagRuntime.Runtime runtime,Path out)throws IOException {
        need(!out.startsWith(runtime.environment())&&!out.startsWith(runtime.source())&&!runtime.environment().startsWith(out)&&!runtime.source().startsWith(out),"MTAG output overlaps immutable runtime roots");
        Path inputs=out.resolve("inputs");Files.createDirectory(inputs);var copies=new LinkedHashMap<Path,Path>();var originals=new LinkedHashMap<Path,Map<String,Object>>();var traits=new ArrayList<Path>();var rows=new ArrayList<Long>();long decoded=0,totalRows=0;
        for(int t=0;t<r.traits().size();t++){
            Path original=r.traits().get(t).input();String suffix=original.toString().endsWith(".gz")?".tsv.gz":original.toString().endsWith(".bz2")?".tsv.bz2":".tsv";
            Path target=copy(original,inputs.resolve(String.format(java.util.Locale.ROOT,"trait-%03d",t+1)+suffix),r,out,copies,originals);
            long[] count=summary(target,r,r.limits().inputBytes()-decoded);rows.add(count[0]);decoded=Math.addExact(decoded,count[1]);totalRows=Math.addExact(totalRows,count[0]);traits.add(target);
        }
        long nativeEstimate=Math.addExact(1048576L,Math.multiplyExact(3072L,totalRows));
        need(nativeEstimate<=r.limits().workingBytes()&&nativeEstimate<=java.lang.Runtime.getRuntime().maxMemory()/2,"Complete native input/audit allocation estimate exceeds declared/JVM capacity; preserve inputs and provide sufficient permitted memory, no implicit subset");
        List<Path> include=selection(MtagRequest.paths(r.source().getParent(),r.filters().get("include")),"include",inputs,r,out,copies,originals);
        List<Path> exclude=selection(MtagRequest.paths(r.source().getParent(),r.filters().get("exclude")),"exclude",inputs,r,out,copies,originals);
        Path sigma=matrixFile(MtagRequest.optional(r.source().getParent(),r.covariance().get("sigma_file")),"sigma",inputs,r,out,copies,originals);
        Path omega=matrixFile(MtagRequest.optional(r.source().getParent(),r.covariance().get("omega_file")),"omega",inputs,r,out,copies,originals);
        Path grid=MtagRequest.optional(r.source().getParent(),r.fdr().get("grid_file"));
        if(grid!=null){grid=copy(grid,inputs.resolve("fdr-grid.txt"),r,out,copies,originals);probabilityGrid(grid,r.traits().size(),r.limits().gridPoints(),r.limits().inputBytes());}
        Path ld=runtime.source().resolve("ld_ref_panel/eur_w_ld_chr");
        if(text(r.covariance().get("ld_reference")).equals("provided")){
            Path original=MtagRequest.path(r.source().getParent(),r.covariance().get("ld_reference_dir"));MtagRuntime.canonical(original,true);
            need(!out.startsWith(original)&&!original.startsWith(out),"Supplied LD-score directory overlaps output");ld=inputs.resolve("ld-reference");Files.createDirectory(ld);
            for(int chr=1;chr<=22;chr++)for(String extension:List.of(".l2.ldscore.gz",".l2.M_5_50"))copy(original.resolve(chr+extension),ld.resolve(chr+extension),r,out,copies,originals);
        }
        long total=0;for(var p:originals.values())total=Math.addExact(total,(Long)p.get("bytes"));need(total<=r.limits().inputBytes(),"Complete input/reference raw byte total exceeds declared capacity");
        return new Staged(List.copyOf(traits),Map.copyOf(copies),Map.copyOf(originals),List.copyOf(rows),ld,include,exclude,sigma,omega,grid,decoded);
    }
    private static Path copy(Path original,Path target,MtagRequest.Request r,Path out,Map<Path,Path> copies,Map<Path,Map<String,Object>> originals)throws IOException {
        MtagRuntime.canonical(original,false);need(!original.startsWith(out),"MTAG original input overlaps new output");
        Map<String,Object> before=MtagRuntime.pin(original,r.limits().inputBytes());Files.copy(original,target);
        need(before.equals(MtagRuntime.pin(target,r.limits().inputBytes()))&&before.equals(MtagRuntime.pin(original,r.limits().inputBytes())),"Original MTAG input changed during immutable byte copy");
        copies.put(target,original);originals.put(original,before);return target;
    }
    private static List<Path> selection(List<Path> paths,String tag,Path inputs,MtagRequest.Request r,Path out,Map<Path,Path> copies,Map<Path,Map<String,Object>> originals)throws IOException {
        var result=new ArrayList<Path>();int count=0;
        for(Path path:paths){Path target=copy(path,inputs.resolve(tag+"-"+(++count)+".csv"),r,out,copies,originals);var seen=new HashSet<String>();
            try(Lines lines=new Lines(target,r.limits().inputBytes())){need("SNP".equals(lines.next()),"Original MTAG selection files require the literal CSV header SNP, regardless of the summary SNP column name");
                for(String s;(s=lines.next())!=null;){identifier(s);need(!s.contains(",")&&seen.add(s)&&seen.size()<=r.limits().rows(),"Duplicate/invalid selection ID or capacity exceeded; no implicit deduplication");}}
            result.add(target);
        }return List.copyOf(result);
    }
    private static Path matrixFile(Path path,String tag,Path inputs,MtagRequest.Request r,Path out,Map<Path,Path> copies,Map<Path,Map<String,Object>> originals)throws IOException {
        if(path==null)return null;
        // The original reader accepts .txt and .npy, not arbitrary .csv suffixes.
        if(path.toString().endsWith(".npy"))throw new MtagRuntime.Unavailable("Provided NPY covariance intake is not yet admitted in this typed profile; dependent analysis skipped. Supply a source-authorized exact finite .txt matrix or retain the skip; no automatic matrix conversion.");
        need(path.toString().endsWith(".txt"),"Original covariance matrix requires a literal .txt suffix in this profile");
        Path target=copy(path,inputs.resolve(tag+".txt"),r,out,copies,originals);int n=0,t=r.traits().size();
        try(Lines lines=new Lines(target,1048576)) {for(String s;(s=lines.next())!=null;){String[] f=s.trim().split("\\s+");need(f.length==t&&++n<=t,"Complete provided covariance dimensions differ");for(String v:f)numeric(v,false);}}
        need(n==t,"Incomplete provided covariance matrix");return target;
    }
    static long probabilityGrid(Path path,int traits,int maximum,long bytes)throws IOException {
        long n=0;int states=1<<traits;
        try(Lines lines=new Lines(path,bytes)){for(String line;(line=lines.next())!=null;){need(++n<=maximum,"Complete maxFDR grid capacity exceeded");String[] f=line.trim().split("\\s+");need(f.length==states,"Custom maxFDR grid needs2^traits columns in original binary-state order");double sum=0;for(String v:f){double d=numeric(v,false);need(d>=0&&d<=1,"Invalid original maxFDR prior probability");sum+=d;}need(Math.abs(sum-1)<=1e-12,"Custom maxFDR probability row must sum to1; no normalization");}}
        need(n>0,"Empty custom maxFDR probability grid");return n;
    }
}
