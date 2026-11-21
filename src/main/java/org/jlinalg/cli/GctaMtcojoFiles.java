/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.GctaWorkflowFiles.*;

/** Complete bounded mtCOJO summaries and LD shards; bytes and original allele labels stay intact. */
final class GctaMtcojoFiles {
    private GctaMtcojoFiles() { }
    record Data(List<Map<String,List<String>>> summaries,Set<String> candidate,Set<String> common,
            List<Double> sourceDerivedVp,Map<String,Object> intake) { }
    static Data prepare(Path root,Map<String,Object> r,List<Variant> reference,long[] retained,boolean verify) throws IOException {
        var declarations=list(r.get("traits"),"traits");var tables=new ArrayList<Map<String,List<String>>>();long[] work={0};
        var variants=new HashMap<String,Variant>();for(var v:reference){uppercase(v.a1());uppercase(v.a2());if(v.position()>Integer.MAX_VALUE)throw new IOException("Reference position exceeds original mtCOJO signed 32-bit coordinate capacity");variants.put(v.id(),v);}
        for(int i=0;i<declarations.size();i++) {
            var t=object(declarations.get(i),"trait");var table=new LinkedHashMap<String,List<String>>();boolean[] header={true};
            rows(root.resolve(GctaMtcojoContracts.traitPath(i,t)),r,GctaMtcojoContracts.suffix(t).equals(".gz"),number(r,"maximum_variants")+1,work,f->{
                if(header[0]){if(!Arrays.equals(f,new String[]{"SNP","A1","A2","freq","b","se","p","N"}))throw new IOException("Require exact eight-column mtCOJO summary header");header[0]=false;return;}
                width(f,8,"mtCOJO summary");for(String value:f)literal(value);
                if(f[0].contains(","))throw new IOException("Upstream comma-delimited pleiotropy exports cannot unambiguously represent this marker ID; no identifier rewriting");
                uppercase(f[1]);uppercase(f[2]);if(f[1].equals(f[2])||Set.of("0",".","NA","NAN").contains(f[1])||Set.of("0",".","NA","NAN").contains(f[2]))throw new IOException("Require two distinct supplied nonmissing GWAS alleles");
                double af=finite(f[3],"mtCOJO frequency"),b=finite(f[4],"mtCOJO effect"),se=finite(f[5],"mtCOJO SE"),p=finite(f[6],"mtCOJO P"),n=finite(f[7],"mtCOJO N");
                if(af<0||af>1||se<=0||p<0||p>1||n<=0)throw new IOException("Invalid complete mtCOJO summary statistic");
                var known=variants.get(f[0]);if(known!=null&&!pair(f[1],f[2],known.a1(),known.a2()))throw new IOException("mtCOJO summary/reference alleles differ from literal pairs; no strand repair");
                retain(retained,256L+4L*Arrays.stream(f).mapToInt(String::length).sum(),r);
                if(table.putIfAbsent(f[0],List.of(f))!=null)throw new IOException("Duplicate mtCOJO summary marker");
            });
            if(header[0]||table.isEmpty())throw new IOException("Empty mtCOJO summary");tables.add(Collections.unmodifiableMap(table));
        }
        var union=new HashSet<String>();for(int i=1;i<tables.size();i++)union.addAll(tables.get(i).keySet());
        var candidate=new LinkedHashSet<>(tables.get(0).keySet());candidate.retainAll(union);
        var common=new LinkedHashSet<>(candidate);for(int i=1;i<tables.size();i++)common.retainAll(tables.get(i).keySet());
        if(common.isEmpty())throw new IOException("mtCOJO traits have no complete literal marker intersection; no imputation");
        long product=(long)tables.size()*tables.size();charge(work,(long)candidate.size()*product,r);
        var swapped=new ArrayList<Long>();
        for(int i=0;i<tables.size();i++) {
            long flips=0;for(var entry:tables.get(i).entrySet()) {
                var target=tables.get(0).get(entry.getKey());if(target==null)continue;var row=entry.getValue();
                if(!pair(row.get(1),row.get(2),target.get(1),target.get(2)))throw new IOException("mtCOJO trait alleles differ from literal target pairs; no complement/identifier repair");
                if(!row.get(1).equals(target.get(1)))flips++;
            }swapped.add(flips);
        }
        var refIds=new HashSet<String>();var weightIds=new HashSet<String>();var shards=new ArrayList<Map<String,Object>>();long markerCount=0;
        for(String key:List.of("reference_ld","weight_ld"))for(Object item:list(r.get(key),key)) {
            var shard=object(item,"LD-score shard");int chromosome=(int)integer(shard,"chromosome",0,1,22);
            if(key.equals("reference_ld")) {
                long[] values={-1};int[] count={0};
                rows(root.resolve(GctaMtcojoContracts.markerPath(shard)),r,false,1,work,f->{width(f,1,"LD reference marker count");values[0]=longInteger(f[0],0,Integer.MAX_VALUE,"LD M_5_50");count[0]++;});
                if(count[0]!=1)throw new IOException("Require one integer M_5_50 per source chromosome");
                markerCount+=values[0];if(markerCount>Integer.MAX_VALUE)throw new IOException("Original mtCOJO total reference M exceeds its signed32-bit capacity");
            }
            var seen=key.equals("reference_ld")?refIds:weightIds;long[] count={0};int[] columns={0};
            rows(root.resolve(GctaMtcojoContracts.scorePath(key.equals("reference_ld")?"ref":"weight",shard)),r,GctaMtcojoContracts.suffix(shard).equals(".gz"),number(r,"maximum_variants")+1,work,f->{
                if(columns[0]==0) {
                    if((f.length!=4&&f.length!=6)||!f[0].equals("CHR")||!f[1].equals("SNP")||!f[2].equals("BP")||!f[f.length-1].equals("L2"))throw new IOException("Require declared CHR SNP BP ... L2 four/six-column LD-score header");columns[0]=f.length;return;
                }
                width(f,columns[0],"LD score");for(String value:f)literal(value);
                if(whole(f[0],1,22,"LD chromosome")!=chromosome)throw new IOException("LD-score row differs from its declared chromosome");
                longInteger(f[2],0,Long.MAX_VALUE,"LD position");finite(f[f.length-1],"source LD score");
                retain(retained,128L+4L*f[1].length(),r);if(!seen.add(f[1]))throw new IOException("Duplicate LD-score marker, including across chromosomes; no deduplication");count[0]++;
            });
            if(columns[0]==0)throw new IOException("Missing LD-score header");shards.add(Map.of("kind",key,"chromosome",chromosome,"rows",count[0],"columns",columns[0]));
        }
        if(markerCount<=0)throw new IOException("Original mtCOJO requires positive total reference M");
        long[][] overlap=new long[tables.size()][tables.size()];
        for(String id:candidate) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("mtCOJO overlap audit interrupted");
            if(!refIds.contains(id)||!weightIds.contains(id))continue;
            for(int i=0;i<tables.size();i++)if(tables.get(i).containsKey(id))for(int j=0;j<=i;j++)if(tables.get(j).containsKey(id))overlap[i][j]++;
        }
        var overlapRows=new ArrayList<List<Long>>();for(int i=0;i<tables.size();i++){var row=new ArrayList<Long>();for(int j=0;j<tables.size();j++){long value=i>=j?overlap[i][j]:overlap[j][i];if(value==0)throw new IOException("A declared mtCOJO trait pair has no LD-score overlap; no overlap/correlation replacement");row.add(value);}overlapRows.add(row);}
        var vp=new ArrayList<Double>();
        for(var table:tables) {
            if(Math.min(table.size(),candidate.size())>number(r,"maximum_audit_entries"))throw new IOException("maximum_audit_entries exceeded before source-derived Vp audit");
            int bound=Math.min(table.size(),candidate.size());charge(work,(long)candidate.size()+bound*(3L+32-Integer.numberOfLeadingZeros(Math.max(1,bound))),r);retain(retained,16L*bound,r);
            double[] values=new double[Math.min(table.size(),candidate.size())];int count=0;
            for(String id:candidate) {
                var row=table.get(id);if(row==null)continue;
                double af=Double.parseDouble(row.get(3)),b=Double.parseDouble(row.get(4)),se=Double.parseDouble(row.get(5)),n=Double.parseDouble(row.get(7));
                double h=2*af*(1-af),value=h*b*b+h*n*se*se;if(!Double.isFinite(value))throw new IOException("Nonfinite source-derived mtCOJO phenotype variance");values[count++]=value;
            }
            Arrays.sort(values,0,count);double value=count%2==0?(values[count/2-1]+values[count/2])/2:values[count/2];
            if(!Double.isFinite(value)||value<=0)throw new IOException("Undefined source-derived mtCOJO phenotype variance");vp.add(value);
        }
        compareOrWrite(root.resolve("worker/mtcojo-traits.list"),GctaMtcojoContracts.traitList(r),verify);
        if(text(r,"causal_effect_mode").equals("supplied"))compareOrWrite(root.resolve("worker/mtcojo-bxy.tsv"),GctaMtcojoContracts.effectList(r),verify);
        var intake=new LinkedHashMap<String,Object>();intake.put("trait_order",declarations.stream().map(v->{try{return text(object(v,"trait"),"name");}catch(IOException e){throw new IllegalArgumentException(e);}}).toList());
        intake.put("trait_summary_rows",tables.stream().map(Map::size).toList());intake.put("candidate_markers",candidate.size());intake.put("complete_trait_intersection",common.size());
        intake.put("literal_swapped_pairs_relative_to_target",swapped);intake.put("reference_M_5_50",markerCount);intake.put("LD_score_pair_overlaps",overlapRows);intake.put("LD_shards",shards);
        intake.put("source_derived_Vp",vp);intake.put("Vp_scope","Independently computed from original input candidate rows using the source median[2*f*(1-f)*(b*b+N*se*se)] formula with its source operation order; not an exported upstream internal object");
        intake.put("allele_policy","Already uppercase, matching literal same/swapped allele pairs. Original data and source harmonization/filter exports retained. No complement, case, identifier or population repair");
        intake.put("effect_scale","as_supplied; this source edition parses and stores prevalence columns without using them in downstream calculations");
        return new Data(List.copyOf(tables),Collections.unmodifiableSet(candidate),Collections.unmodifiableSet(common),List.copyOf(vp),Collections.unmodifiableMap(intake));
    }
    static boolean pair(String a1,String a2,String b1,String b2){return (a1.equals(b1)&&a2.equals(b2))||(a1.equals(b2)&&a2.equals(b1));}
    private static void uppercase(String value) throws IOException {literal(value);if(!value.equals(value.toUpperCase(Locale.ROOT)))throw new IOException("Prepare literal uppercase mtCOJO allele labels explicitly; no automatic case/strand repair");}
    private static void rows(Path path,Map<String,Object> r,boolean gzip,long maximum,long[] work,Row row) throws IOException {
        long cap=number(r,"maximum_metadata_bytes");if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>cap)throw new IOException("Require bounded regular mtCOJO metadata: "+path.getFileName());
        int lineCap=(int)Math.min(number(r,"maximum_line_bytes"),gzip?1023:Integer.MAX_VALUE);long bytes=0,count=0;
        try(var file=new BufferedInputStream(Files.newInputStream(path));var input=gzip?new GZIPInputStream(file):file;var line=new ByteArrayOutputStream()) {
            for(int b;(b=input.read())!=-1;) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("mtCOJO input decoding interrupted");
                if(++bytes>cap)throw new IOException("maximum_metadata_bytes exceeded by decompressed mtCOJO input");
                if(b=='\n') {if(++count>maximum)throw new IOException("mtCOJO metadata row capacity exceeded");accept(line.toByteArray(),row,work,r);line.reset();}
                else {if(line.size()>=lineCap)throw new IOException("mtCOJO line exceeds declared/source gzip byte capacity; no upstream truncation admitted");line.write(b);}
            }
            if(line.size()>0){if(gzip)throw new IOException("Original mtCOJO gzip readers drop an unterminated final record; preserve input and prepare an explicitly newline-terminated gzip file");if(++count>maximum)throw new IOException("mtCOJO metadata row capacity exceeded");accept(line.toByteArray(),row,work,r);}
        }
    }
    private static void accept(byte[] bytes,Row row,long[] work,Map<String,Object> r) throws IOException {
        if(bytes.length>0&&bytes[bytes.length-1]=='\r')bytes=Arrays.copyOf(bytes,bytes.length-1);
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        int start=0,end=text.length();while(start<end&&(text.charAt(start)==' '||text.charAt(start)=='\t'))start++;while(end>start&&(text.charAt(end-1)==' '||text.charAt(end-1)=='\t'))end--;
        if(start==end)throw new IOException("Empty mtCOJO metadata row; no implicit row removal");
        String[] fields=text.substring(start,end).split("[ \t]+",-1);charge(work,fields.length,r);row.accept(fields);
    }
    private static void charge(long[] work,long amount,Map<String,Object> r) throws IOException {if(amount<0||amount>number(r,"maximum_work")-work[0])throw new IOException("maximum_work exceeded by mtCOJO input audit");work[0]+=amount;}
}
