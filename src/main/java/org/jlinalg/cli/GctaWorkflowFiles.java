/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.GctaHardcallGrm;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal, bounded prepared-file intake; no data rewriting, filtering or imputation. */
final class GctaWorkflowFiles {
    private GctaWorkflowFiles() { }
    record Variant(int chromosome,String id,long position,String a1,String a2,int ordinal) { }
    record Prepared(List<GctaHardcallGrm.SampleId> samples,List<Variant> variants,
            List<List<GctaHardcallGrm.SampleId>> grmSamples,Set<String> extract,
            Set<String> condition,int fixedColumns,Map<String,List<String>> summary) { }
    @FunctionalInterface interface Row { void accept(String[] fields) throws IOException; }

    static Prepared prepare(Path root,Map<String,Object> r,boolean verify) throws IOException {
        var samples=new ArrayList<GctaHardcallGrm.SampleId>();var variants=new ArrayList<Variant>();
        var componentIds=new ArrayList<List<GctaHardcallGrm.SampleId>>();String model=text(r,"model");
        long[] retained={0};
        if(r.containsKey("genotypes")) {
            var unique=new HashSet<GctaHardcallGrm.SampleId>();
            rows(root.resolve("inputs/genotypes.fam"),r,number(r,"maximum_samples"),f->{
                width(f,6,"FAM");var id=id(f[0],f[1]);if(!unique.add(id))throw new IOException("Duplicate composite FAM identity");
                int sex=whole(f[4],0,2,"FAM sex");literal(f[2]);literal(f[3]);
                retain(retained,256L+4L*Arrays.stream(f).mapToInt(String::length).sum(),r);samples.add(id);
            });
            if(samples.isEmpty())throw new IOException("FAM contains no samples");
            var names=new HashSet<String>();
            rows(root.resolve("inputs/genotypes.bim"),r,number(r,"maximum_variants"),f->{
                width(f,6,"BIM");for(String s:f)literal(s);
                int chr=whole(f[0],1,(int)number(r,"autosome_number"),"prepared autosomal chromosome");
                finite(f[2],"BIM genetic distance");long bp=longInteger(f[3],0,Long.MAX_VALUE,"BIM position");
                if(!names.add(f[1]))throw new IOException("Duplicate BIM marker; no identifier repair");
                if(f[4].equals(f[5])&&!Set.of("0",".").contains(f[4]))throw new IOException("Identical nonmissing BIM alleles");
                retain(retained,384L+4L*Arrays.stream(f).mapToInt(String::length).sum(),r);
                variants.add(new Variant(chr,f[1],bp,f[4],f[5],variants.size()));
            });
            if(variants.isEmpty())throw new IOException("BIM contains no variants");
            long expected;
            try{expected=Math.addExact(3,Math.multiplyExact((samples.size()+3L)/4,variants.size()));}
            catch(ArithmeticException e){throw new IOException("BED dimension overflow",e);}
            Path bed=root.resolve("inputs/genotypes.bed");
            if(Files.size(bed)!=expected)throw new IOException("SNP-major BED length differs from complete FAM/BIM dimensions");
            try(var input=Files.newInputStream(bed)) {
                if(!Arrays.equals(input.readNBytes(3),new byte[]{0x6c,0x1b,1}))throw new IOException("Require SNP-major PLINK BED");
            }
        }
        if(r.containsKey("grms")) {
            int count=list(r.get("grms"),"grms").size();
            for(int k=0;k<count;k++) {
                var ids=ids(root.resolve("inputs/grm"+k+".grm.id"),r,retained);
                long pairs=pairs(ids.size());if(pairs>number(r,"maximum_sample_pairs"))throw new IOException("maximum_sample_pairs exceeded");
                checkFloats(root.resolve("inputs/grm"+k+".grm.bin"),pairs,false);
                Path counts=root.resolve("inputs/grm"+k+".grm.N.bin");
                if(Files.exists(counts))checkFloats(counts,pairs,true);
                if(componentIds.isEmpty())samples.addAll(ids);
                else if(!new HashSet<>(samples).equals(new HashSet<>(ids)))throw new IOException("Prepared GRM components must contain the same literal composite identities");
                componentIds.add(ids);
            }
        }
        if(r.containsKey("sparse")) {
            var ids=ids(root.resolve("inputs/sparse.grm.id"),r,retained);
            if(!new HashSet<>(samples).equals(new HashSet<>(ids)))throw new IOException("Sparse GRM identities differ from complete genotype cohort");
            var seen=new HashSet<Long>();var diagonal=new HashSet<Integer>();
            rows(root.resolve("inputs/sparse.grm.sp"),r,number(r,"maximum_result_rows"),f->{
                width(f,3,"sparse GRM");int i=whole(f[0],0,ids.size()-1,"zero-based sparse row"),j=whole(f[1],0,ids.size()-1,"zero-based sparse column");
                if(j>i)throw new IOException("Require source lower-triangular sparse GRM entries");
                if(!seen.add((long)i*(i+1L)/2+j))throw new IOException("Duplicate sparse GRM pair");
                finite(f[2],"sparse GRM value");if(i==j)diagonal.add(i);
                retain(retained,48,r);
            });
        }
        var names=new HashSet<String>();for(var variant:variants)names.add(variant.id());
        Set<String> extract=r.containsKey("extract")?markers(root.resolve("inputs/extract"),r,names):Set.of();
        Set<String> condition=r.containsKey("condition")?markers(root.resolve("inputs/condition"),r,names):Set.of();
        var selected=variants.stream().filter(v->extract.isEmpty()||extract.contains(v.id())).toList();
        if(model.equals("PCA")&&number(r,"pca_components")>samples.size())throw new IOException("pca_components exceeds prepared sample count; source clamping is not admitted");
        int fixedColumns=1;
        if(r.containsKey("phenotype")) {
            var values=aligned(root.resolve("inputs/phenotype"),r,samples,1,false,retained);
            if(model.equals("FASTGWA_BINARY")) {
                int first=text(r,"binary_coding").equals("0/1")?0:1;int cases=0;
                for(var row:values.values()) {
                    double value=finite(row.get(0),"binary phenotype");
                    if(value!=first&&value!=first+1)throw new IOException("Binary phenotype differs from declared coding; no recoding");
                    if(value==first+1)cases++;
                }
                if(cases==0||cases==samples.size())throw new IOException("Binary phenotype requires both declared outcome classes");
            }
        }
        for(String key:List.of("qcovar","covar"))if(r.containsKey(key)) {
            var values=aligned(root.resolve("inputs/"+key),r,samples,-1,key.equals("covar"),retained);
            int columns=values.values().iterator().next().size();
            if(key.equals("qcovar"))fixedColumns+=columns;
            else for(int c=0;c<columns;c++) {
                var levels=new HashSet<String>();for(var row:values.values())levels.add(row.get(c));
                if(levels.size()<2)throw new IOException("Categorical covariate has only one level; no automatic column removal");
                fixedColumns+=levels.size()-1;
            }
        }
        if(r.containsKey("phenotype")&&fixedColumns>=samples.size())throw new IOException("No residual degrees of freedom for declared fixed effects");
        var summary=new LinkedHashMap<String,List<String>>();
        if(r.containsKey("summary")) {
            var metadata=new HashMap<String,Variant>();for(var v:variants)metadata.put(v.id(),v);boolean[] header={false};
            rows(root.resolve("inputs/summary"),r,number(r,"maximum_result_rows")+1,f->{
                if(!header[0]) {
                    if(!Arrays.equals(f,new String[]{"SNP","A1","A2","freq","b","se","p","N"}))throw new IOException("Require the exact eight-column GCTA summary header");
                    header[0]=true;return;
                }
                width(f,8,"GWAS summary");for(String s:f)literal(s);
                if(summary.containsKey(f[0]))throw new IOException("Duplicate summary marker");
                var variant=metadata.get(f[0]);
                if(variant!=null&&!((f[1].equals(variant.a1())&&f[2].equals(variant.a2()))||(f[2].equals(variant.a1())&&f[1].equals(variant.a2()))))
                    throw new IOException("Summary alleles differ from literal BIM alleles; no automatic strand or identifier repair");
                double frequency=finite(f[3],"summary frequency"),effect=finite(f[4],"summary effect"),se=finite(f[5],"summary SE"),p=finite(f[6],"summary P"),n=finite(f[7],"summary N");
                if(frequency<0||frequency>1||se<=0||p<0||p>1||n<=0)throw new IOException("Invalid prepared summary statistic");
                retain(retained,192L+4L*Arrays.stream(f).mapToInt(String::length).sum(),r);summary.put(f[0],List.of(f));
            });
            if(summary.isEmpty())throw new IOException("Summary contains no marker rows");
            if(!summary.keySet().containsAll(condition)||!summary.keySet().containsAll(extract))throw new IOException("Explicit selected/conditioning marker is absent from summary data");
        }
        var prepared=new Prepared(List.copyOf(samples),List.copyOf(selected),List.copyOf(componentIds),Set.copyOf(extract),Set.copyOf(condition),fixedColumns,Collections.unmodifiableMap(summary));
        var report=new LinkedHashMap<String,Object>();report.put("sample_count",samples.size());report.put("selected_variant_count",selected.size());report.put("genotype_variant_count",variants.size());report.put("fixed_effect_columns",fixedColumns);
        report.put("samples",samples.stream().map(s->List.of(s.familyId(),s.individualId())).toList());
        report.put("variants",selected.stream().map(v->List.of(v.chromosome(),v.id(),v.position(),v.a1(),v.a2(),v.ordinal())).toList());
        report.put("grm_component_orders",componentIds.stream().map(ids->ids.stream().map(s->List.of(s.familyId(),s.individualId())).toList()).toList());
        report.put("summary_rows",summary.size());report.put("retained_metadata_estimate",retained[0]);
        if(!summary.isEmpty()) {
            var unmatched=summary.keySet().stream().filter(id->!names.contains(id)).toList();
            if(unmatched.size()==summary.size())throw new IOException("Summary and reference panel share no marker identities");
            report.put("summary_markers_absent_from_reference",unmatched);
            report.put("source_overlap_policy","Raw summaries are retained unchanged; original GCTA intersects reference markers. Explicit extract/condition markers must be present in both inputs.");
        }
        compareOrWrite(root.resolve("intake.json"),RsemJson.bytes(report),verify);
        return prepared;
    }

    static GctaHardcallGrm.Result nativeGrm(Path root,Map<String,Object> r,Prepared input) throws IOException {
        int n=input.samples().size(),m=input.variants().size();
        if((long)n*m>number(r,"maximum_native_cells"))throw new IOException("maximum_native_cells exceeded before genotype allocation");
        long pairs=pairs(n);if(pairs>number(r,"maximum_sample_pairs"))throw new IOException("maximum_sample_pairs exceeded");
        if(pairs>number(r,"maximum_work")/m)throw new IOException("maximum_work exceeded before native fitting");
        double[][] calls=new double[m][];int width=(int)((n+3L)/4);
        try(var channel=java.nio.channels.FileChannel.open(root.resolve("inputs/genotypes.bed"),StandardOpenOption.READ)) {
            for(int v=0;v<m;v++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Native genotype intake interrupted");
                var packed=ByteBuffer.allocate(width);long offset=3+input.variants().get(v).ordinal()*(long)width;
                while(packed.hasRemaining()){int read=channel.read(packed,offset+packed.position());if(read<0)throw new EOFException("BED changed during intake");}
                calls[v]=new double[n];byte[] bytes=packed.array();
                for(int i=0;i<n;i++) { int code=(bytes[i/4]>>>(2*(i%4)))&3;calls[v][i]=switch(code){case 0->2;case 2->1;case 3->0;default->Double.NaN;}; }
            }
        }
        return GctaHardcallGrm.compute(input.samples(),input.variants().stream().map(Variant::id).toList(),calls,
            GctaHardcallGrm.Algorithm.values()[(int)number(r,"grm_algorithm")],
            new GctaHardcallGrm.Limits(number(r,"maximum_native_cells"),number(r,"maximum_sample_pairs"),number(r,"maximum_work")));
    }

    static void compareOrWrite(Path path,byte[] bytes,boolean verify) throws IOException {
        if(verify){
            byte[] previous=Files.readAllBytes(path);
            boolean equal=path.getFileName().toString().endsWith(".json")
                ?same(SimpleJson.parse(new String(previous,StandardCharsets.UTF_8)),SimpleJson.parse(new String(bytes,StandardCharsets.UTF_8)))
                :Arrays.equals(previous,bytes);
            if(!equal)throw new IOException("Deterministic intake/derived record differs: "+path.getFileName());
        }
        else Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
    static long number(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
    static long pairs(int n){return (long)n*(n+1L)/2;}
    static double finite(String value,String label) throws IOException {
        try{double x=Double.parseDouble(value);if(!Double.isFinite(x))throw new NumberFormatException();return x;}
        catch(NumberFormatException e){throw new IOException("Expected finite "+label+": "+value,e);}
    }
    static long longInteger(String value,long min,long max,String label) throws IOException {
        try{long n=Long.parseLong(value);if(n<min||n>max)throw new NumberFormatException();return n;}
        catch(NumberFormatException e){throw new IOException("Invalid "+label+": "+value,e);}
    }
    static int whole(String value,int min,int max,String label) throws IOException{return (int)longInteger(value,min,max,label);}
    static void literal(String value) throws IOException {
        try{new GctaHardcallGrm.SampleId(value,"literal");}catch(IllegalArgumentException e){throw new IOException("Invalid literal file field",e);}
    }
    static GctaHardcallGrm.SampleId id(String fid,String iid) throws IOException {
        try{return new GctaHardcallGrm.SampleId(fid,iid);}catch(IllegalArgumentException e){throw new IOException("Invalid composite sample identity",e);}
    }
    static void width(String[] f,int n,String label) throws IOException {if(f.length!=n)throw new IOException(label+" column count differs");}
    private static void retain(long[] bytes,long add,Map<String,Object> r) throws IOException {
        if(add>number(r,"maximum_retained_metadata_bytes")-bytes[0])throw new IOException("maximum_retained_metadata_bytes exceeded");bytes[0]+=add;
    }
    static List<GctaHardcallGrm.SampleId> ids(Path path,Map<String,Object> r,long[] retained) throws IOException {
        var result=new ArrayList<GctaHardcallGrm.SampleId>();var unique=new HashSet<GctaHardcallGrm.SampleId>();
        rows(path,r,number(r,"maximum_samples"),f->{width(f,2,"GRM identities");var id=id(f[0],f[1]);if(!unique.add(id))throw new IOException("Duplicate GRM identity");retain(retained,256L+4L*(f[0].length()+f[1].length()),r);result.add(id);});
        if(result.isEmpty())throw new IOException("No GRM identities");return List.copyOf(result);
    }
    private static Set<String> markers(Path path,Map<String,Object> r,Set<String> known) throws IOException {
        var result=new LinkedHashSet<String>();rows(path,r,number(r,"maximum_variants"),f->{width(f,1,"marker list");literal(f[0]);if(!known.contains(f[0])||!result.add(f[0]))throw new IOException("Unknown/repeated explicit marker: "+f[0]);});
        if(result.isEmpty())throw new IOException("Explicit marker list is empty");return result;
    }
    private static Map<GctaHardcallGrm.SampleId,List<String>> aligned(Path path,Map<String,Object> r,List<GctaHardcallGrm.SampleId> ids,int expected,boolean categorical,long[] retained) throws IOException {
        var values=new LinkedHashMap<GctaHardcallGrm.SampleId,List<String>>();int[] columns={expected};
        rows(path,r,number(r,"maximum_samples"),f->{
            if(columns[0]<0)columns[0]=f.length-2;
            if(columns[0]<1||columns[0]>128||f.length!=columns[0]+2)throw new IOException("Prepared phenotype/covariate width differs");
            var id=id(f[0],f[1]);var row=List.of(Arrays.copyOfRange(f,2,f.length));
            for(String value:row){literal(value);if(value.equals("-9")||value.equals("NA")||value.equals("NaN")||(!categorical&&finite(value,"phenotype/covariate")==-9))throw new IOException("Prepared model inputs must be complete; no silent missing-value filtering");}
            retain(retained,256L+24L*columns[0]+4L*Arrays.stream(f).mapToInt(String::length).sum(),r);
            if(values.putIfAbsent(id,row)!=null)throw new IOException("Duplicate phenotype/covariate identity");
        });
        if(!values.keySet().equals(new HashSet<>(ids)))throw new IOException("Prepared phenotype/covariate identities differ from complete model cohort");return values;
    }
    static void checkFloats(Path path,long entries,boolean counts) throws IOException {
        if(Files.size(path)!=Math.multiplyExact(entries,4))throw new IOException("GRM binary dimensions differ");
        try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            for(long i=0;i<entries;i++) {
                float x=Float.intBitsToFloat(Integer.reverseBytes(input.readInt()));
                if(!Float.isFinite(x)||(counts&&(x<=0||x!=Math.rint(x))))throw new IOException("Nonfinite GRM or undefined/nonintegral pair count; no complete estimate");
            }
        }
    }
    static void rows(Path path,Map<String,Object> r,long limit,Row row) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>number(r,"maximum_metadata_bytes"))throw new IOException("Require bounded regular metadata: "+path.getFileName());
        long count=0;int cap=(int)number(r,"maximum_line_bytes");
        try(var input=new BufferedInputStream(Files.newInputStream(path));var line=new ByteArrayOutputStream()) {
            for(int b;(b=input.read())!=-1;) {
                if(b=='\n'||b=='\r') {
                    if(line.size()>0){if(++count>limit)throw new IOException("Metadata row capacity exceeded");row.accept(fields(line.toByteArray()));line.reset();}
                }else {if(line.size()>=cap)throw new IOException("maximum_line_bytes exceeded before decoding");line.write(b);}
            }
            if(line.size()>0){if(++count>limit)throw new IOException("Metadata row capacity exceeded");row.accept(fields(line.toByteArray()));}
        }
    }
    private static String[] fields(byte[] bytes) throws IOException {
        String line=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        int start=0,end=line.length();while(start<end&&(line.charAt(start)==' '||line.charAt(start)=='\t'))start++;while(end>start&&(line.charAt(end-1)==' '||line.charAt(end-1)=='\t'))end--;
        if(start==end)throw new IOException("Whitespace-only metadata row is not an identified record");
        return line.substring(start,end).split("[ \t]+",-1);
    }
}
