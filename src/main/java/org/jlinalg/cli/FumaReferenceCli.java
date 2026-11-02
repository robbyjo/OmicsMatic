/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FumaLocusDefinition;
import org.jlinalg.genetics.FumaReferencePreparation;
import org.jlinalg.genetics.HardcallLd;
import org.jlinalg.genetics.PlinkBedDataset;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed local hardcall reference capture, complete replay and locus admission. */
final class FumaReferenceCli {
    private FumaReferenceCli() { }
    private static final String FORMAT="omicsmatic-autosomal-hardcall-reference-v1";
    private static final long MAGIC=0x4f4d4943534c4431L; // OMICSLD1; then int sample/variant counts, packed rows.
    private static final List<String> PAYLOAD=List.of("variants.tsv","samples.tsv","sample-selection.tsv","genotypes.bin","reference.tsv","variant-audit.tsv","ld.tsv","ld-audit.tsv");
    private static final Set<String> PROTOCOL=Set.of("format","assembly","population","release","sample-policy","minimum-maf","ld-floor","window-bp","window-index-gap");
    private static final Set<String> BUDGETS=Set.of("max-samples","max-variants","max-pairs","max-word-work","max-owned-bytes","max-output-bytes","max-metadata-bytes");
    private static final Set<String> ALLOWED;
    static {var s=new HashSet<>(BUDGETS);s.addAll(PROTOCOL);s.remove("format");s.addAll(Set.of("method","bfile","variants","samples","bundle","out"));ALLOWED=Set.copyOf(s);}
    private static final String SCOPE="LOCAL AUTOSOMAL DIPLOID REFERENCE ONLY. The supplied assembly/population/release and FAM founder flags are declarations, not independently established ancestry, ploidy or provenance. Numeric chromosomes 1..22 only. No reference download, imputation, phased LD, sex-chromosome model or historical hosted FUMA reconstruction. Required action: review all selections, exclusions, pair coverage and resource identities before downstream use.";
    private record Budgets(int samples,long metadata,long output,FumaReferencePreparation.Limits nativeLimits) {
        long metadataBound(){return Math.min(metadata,Math.min(nativeLimits.maximumOwnedBytes()/8,Runtime.getRuntime().maxMemory()/16));}
    }
    record Admission(Path reference,Path ld,String identity,String assembly,double floor,FumaLocusDefinition.LdWindow window) { }
    private record Panel(Map<String,String> protocol,List<PlinkBedDataset.Variant> variants,List<PlinkBedDataset.Sample> samples,byte[] selection,Map<Path,String> pins,Map<?,?> accounting) { }
    private static Budgets budgets(Map<String,String> o){
        return new Budgets(integer(o,"max-samples",100000,2,1000000),positive(o,"max-metadata-bytes",16777216),positive(o,"max-output-bytes",268435456),
            new FumaReferencePreparation.Limits(integer(o,"max-variants",100000,1,1000000),nonnegative(o,"max-pairs",2000000),positive(o,"max-word-work",100000000),positive(o,"max-owned-bytes",268435456)));
    }
    private static long positive(Map<String,String> o,String k,long d){long n=nonnegative(o,k,d);if(n==0)throw new IllegalArgumentException("--"+k+" must be positive");return n;}
    private static long nonnegative(Map<String,String> o,String k,long d){long n=Long.parseLong(o.getOrDefault(k,Long.toString(d)));if(n<0)throw new IllegalArgumentException("--"+k+" must be nonnegative");return n;}
    private static FumaReferencePreparation.Options nativeOptions(Map<String,String> p){return new FumaReferencePreparation.Options(number(p,"minimum-maf",0),number(p,"ld-floor",0),new FumaLocusDefinition.LdWindow(Long.parseLong(required(p,"window-bp")),Integer.parseInt(required(p,"window-index-gap"))));}
    private static Map<String,String> protocol(Map<String,String> input){
        Map<String,String> p=new LinkedHashMap<>();for(String k:PROTOCOL.stream().sorted().toList())p.put(k,k.equals("format")?FORMAT:required(input,k));
        for(String k:List.of("assembly","population","release")){String v=p.get(k);if(v.length()>256||v.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid declared reference "+k);}
        if(!Set.of("founders","selected").contains(p.get("sample-policy")))throw new IllegalArgumentException("--sample-policy must be founders or selected");nativeOptions(p);return p;
    }
    private static String identity(Map<String,String> p){return RegenieRuntime.json(List.of(p.get("assembly"),p.get("population"),p.get("release")));}
    private static void check(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Reference preparation cancelled; no prefix result is admitted");}

    static int run(String[] args,PrintStream out,PrintStream err){
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try {
            Map<String,String> o=options(args,ALLOWED);String method=required(o,"method");boolean replay=method.equals("replay-reference");
            if(!replay&&!method.equals("prepare-reference"))throw new IllegalArgumentException("--method must be prepare-reference or replay-reference");
            required(o,"out");Budgets b=budgets(o);
            if(replay){required(o,"bundle");for(String k:o.keySet())if(!BUDGETS.contains(k)&&!Set.of("method","bundle","out").contains(k))throw new IllegalArgumentException("Replay protocol comes from the bundle; do not override --"+k);}
            else {for(String k:List.of("bfile","variants","samples"))required(o,k);if(o.containsKey("bundle"))throw new IllegalArgumentException("--bundle is only for replay-reference");protocol(o);}
            int[] code={0};transaction(o,"fuma-"+method,(directory,manifest)->{
                Map<String,Object> session=new LinkedHashMap<>();Map<Path,String> pins=new LinkedHashMap<>();List<String> warnings=new ArrayList<>(List.of(SCOPE));
                try {
                    FumaReferencePreparation.Result result;Map<String,String> p;
                    BudgetOutput output=new BudgetOutput(directory,b.output());
                    if(replay){
                        Path bundle=Path.of(o.get("bundle")).toAbsolutePath().normalize();Panel panel=readPanel(bundle,b);pins.putAll(panel.pins());p=panel.protocol();
                        try(var calls=new CapturedRows(bundle.resolve("genotypes.bin"),panel.variants(),panel.samples().size())){
                            result=emit(panel.variants(),panel.samples(),panel.selection(),frame(panel.samples().size(),all(panel.samples().size()),p,b),calls,p,b,output);
                        }
                        for(String name:PAYLOAD)if(Files.mismatch(bundle.resolve(name),directory.resolve(name))!=-1)throw new IOException("Complete replay differs from captured producer output: "+name);
                        verifyAccounting(panel,result);
                        session.put("replayed_and_compared_payload_files",PAYLOAD);
                    }else{
                        p=protocol(o);Path prefix=Path.of(o.get("bfile")).toAbsolutePath().normalize();
                        for(String ext:List.of(".bed",".bim",".fam"))pin(Path.of(prefix+ext),pins);
                        Set<String> ids=variantIds(Path.of(o.get("variants")),pins,b);Set<String> requested=sampleIds(Path.of(o.get("samples")),pins,b);
                        long retained=Math.min(b.nativeLimits().maximumOwnedBytes()/4,Runtime.getRuntime().maxMemory()/8);
                        var dl=new PlinkBedDataset.Limits(b.samples(),50000000,b.nativeLimits().maximumVariants(),b.metadataBound(),retained,16384);
                        try(var data=PlinkBedDataset.open(prefix,ids,dl)){
                            List<Integer> selected=new ArrayList<>();Set<String> seen=new HashSet<>();List<PlinkBedDataset.Sample> samples=new ArrayList<>();
                            var selection=new StringBuilder("source_index\tfid\tiid\tfounder\tsex\trequested\tselected\n");
                            for(int i=0;i<data.samples().size();i++){
                                check();var s=data.samples().get(i);boolean request=requested.contains(key(s.familyId(),s.individualId()));if(request)seen.add(key(s.familyId(),s.individualId()));
                                boolean keep=request&&(!p.get("sample-policy").equals("founders")||s.founder());
                                if(keep){selected.add(i);samples.add(s);}
                                selection.append(i).append('\t').append(s.familyId()).append('\t').append(s.individualId()).append('\t').append(s.founder()).append('\t').append(s.sex()).append('\t').append(request).append('\t').append(keep).append('\n');
                                if(4L*selection.length()>b.metadataBound())throw new IOException("Complete sample selection audit exceeds metadata bound");
                            }
                            if(!seen.equals(requested))throw new IOException("Requested FID/IID absent from FAM; no unknown sample is silently dropped");
                            int[] indices=selected.stream().mapToInt(Integer::intValue).toArray();
                            result=emit(data.selectedVariants(),samples,selection.toString().getBytes(StandardCharsets.UTF_8),frame(data.samples().size(),indices,p,b),data::readPackedRow,p,b,output);
                        }
                    }
                    for(String name:List.of("variants.tsv","samples.tsv","sample-selection.tsv"))if(Files.size(directory.resolve(name))>b.metadataBound())throw new IOException("Captured metadata exceeds replay admission bound: "+name);
                    unchanged(pins);Map<String,Object> payload=new LinkedHashMap<>();for(String name:PAYLOAD){Path f=directory.resolve(name);payload.put(name,Map.of("bytes",Files.size(f),"sha256",hash(f)));}
                    Map<String,Object> bundle=new LinkedHashMap<>();bundle.put("format",FORMAT);bundle.put("status","completed");bundle.put("protocol",p);bundle.put("payload",payload);bundle.put("accounting",accounting(result));
                    Files.writeString(directory.resolve("reference-bundle.json"),RegenieRuntime.json(bundle)+"\n");
                    session.put("protocol",p);session.put("accounting",accounting(result));session.put("locus_ready",true);session.put("captured_requested_variants",result.assessments().size());session.put("payload_bytes",output.used);
                    session.put("estimated_owned_bytes_this_execution",result.estimatedOwnedBytes());
                    warnings.add("WINDOW COVERAGE: rank is genomic order AFTER the recorded MAF/constant-call exclusions. Both distance and index-gap limits are inclusive. Pairs outside either limit are uncomputed, not observed zero/weak LD. Inside-window omitted pairs are assessed and strictly below the inclusive storage floor. Required action: carry reference-bundle.json and the entire payload into the locus command; do not copy the sparse LD table alone and assert unbounded coverage.");
                    long excluded=result.assessments().stream().filter(a->a.disposition()!=FumaReferencePreparation.Disposition.RETAINED).count();
                    warnings.add("VARIANT EXCLUSIONS: "+excluded+" requested variants were excluded; every requested row remains captured in genotypes.bin and variant-audit.tsv. Unobserved calls, fewer than two observed calls, zero MAF, constant observed genotypes (including all-heterozygous rows), and below-minimum MAF are separate decisions. Required action: inspect each exclusion and sample-selection.tsv; these decisions change the reference rank/window family.");
                    manifest.put("analysis_status","completed-with-review-warnings");
                }catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(directory);Files.createDirectories(directory.resolve("partial"));warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and retained partial/ audits (empty if admission failed before output), correct the named inputs, missingness, population/variant selection or capacity, and rerun the COMPLETE request to a new directory. No reference or sparse prefix is admitted for downstream use.");}
                Map<String,String> sources=new LinkedHashMap<>();pins.forEach((k,v)->sources.put(k.toString(),v));session.put("input_sha256",sources);manifest.put("input_sha256",sources);session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);
                Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# FUMA REFERENCE "+(code[0]==0?"WARNINGS REQUIRE REVIEW":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Native FUMA reference "+(replay?"replayed and compared":"prepared and captured")+": "+o.get("out"));return code[0];
        }catch(Exception failure){err.println("ANALYSIS FAILED: "+failure.getMessage()+"\nRequired action: correct the named options/files and choose a new writable --out directory.\n"+SCOPE);return 2;}
    }
    private static Map<String,Object> accounting(FumaReferencePreparation.Result r){return Map.of("retained_variants",r.variants().size(),"assessed_pairs",r.assessedPairs(),"stored_pairs",r.storedPairs(),"below_floor_pairs",r.belowFloorPairs(),"word_work",r.wordWork(),"peak_cached_rows",r.peakCachedRows());}
    private static void verifyAccounting(Panel panel,FumaReferencePreparation.Result result)throws IOException{Map<String,Object> expected=accounting(result);if(!panel.accounting().keySet().equals(expected.keySet()))throw new IOException("Unexpected captured accounting fields");for(var e:expected.entrySet())if(!e.getValue().toString().equals(String.valueOf(panel.accounting().get(e.getKey()))))throw new IOException("Captured accounting disagrees with complete replay: "+e.getKey());}
    private static int[] all(int n){int[] a=new int[n];for(int i=0;i<n;i++)a[i]=i;return a;}
    private static HardcallLd.Frame frame(int total,int[] selected,Map<String,String> p,Budgets b){return HardcallLd.frame(identity(p),total,selected,new HardcallLd.Limits(b.samples(),b.samples(),b.nativeLimits().maximumOwnedBytes(),b.nativeLimits().maximumWordWork()),FumaReferenceCli::check);}
    private static String key(String fid,String iid){return fid+"\t"+iid;}
    private static void token(String s)throws IOException{if(s.isBlank()||s.length()>4096||s.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||Character.isSpaceChar(c)))throw new IOException("Empty, oversized or whitespace/control-containing identifier");}
    private static void pin(Path file,Map<Path,String> pins)throws IOException{file=file.toAbsolutePath().normalize();if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Regular non-symlink input required: "+file);String h=hash(file),old=pins.putIfAbsent(file,h);if(old!=null&&!old.equals(h))throw new IOException("Input changed: "+file);}
    private static void unchanged(Map<Path,String> pins)throws IOException{for(var e:pins.entrySet()){check();if(!Files.isRegularFile(e.getKey(),LinkOption.NOFOLLOW_LINKS)||!hash(e.getKey()).equals(e.getValue()))throw new IOException("Input changed during complete reference request: "+e.getKey());}}
    private static List<String[]> tsv(Path file,String header,int width,int maxRows,long bytes,Map<Path,String> pins)throws IOException{
        pin(file,pins);byte[] raw=AdmixtureInputs.raw(file,Math.min(bytes,Integer.MAX_VALUE-1L));if(!AdmixtureInputs.digest(raw).equals(pins.get(file.toAbsolutePath().normalize())))throw new IOException("Input changed while parsing: "+file);
        List<String> lines=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString().lines().toList();if(lines.isEmpty()||!lines.get(0).equals(header))throw new IOException("Expected exact TSV header: "+header);
        List<String[]> rows=new ArrayList<>();for(int i=1;i<lines.size();i++){check();String line=lines.get(i);if(line.length()>16384||line.indexOf('\0')>=0)throw new IOException("Oversized/NUL TSV row");String[] r=line.split("\t",-1);if(r.length!=width||rows.size()>=maxRows)throw new IOException("TSV width or complete row capacity exceeded: "+file);rows.add(r);}return rows;
    }
    private static Set<String> variantIds(Path file,Map<Path,String> pins,Budgets b)throws IOException{Set<String> s=new HashSet<>();for(String[] r:tsv(file,"id",1,b.nativeLimits().maximumVariants(),b.metadataBound(),pins)){token(r[0]);if(!s.add(r[0]))throw new IOException("Duplicate requested variant ID");}if(s.isEmpty())throw new IOException("Empty requested variant family");return s;}
    private static Set<String> sampleIds(Path file,Map<Path,String> pins,Budgets b)throws IOException{Set<String> s=new HashSet<>();for(String[] r:tsv(file,"fid\tiid",2,b.samples(),b.metadataBound(),pins)){token(r[0]);token(r[1]);if(!s.add(key(r[0],r[1])))throw new IOException("Duplicate requested sample ID");}if(s.isEmpty())throw new IOException("Empty requested sample family");return s;}

    @FunctionalInterface private interface Outputs {OutputStream open(String name)throws IOException;}
    private static final class BudgetOutput implements Outputs {
        final Path directory;final long maximum;long used;
        BudgetOutput(Path directory,long maximum){this.directory=directory;this.maximum=maximum;}
        public OutputStream open(String name)throws IOException{
            OutputStream file=new BufferedOutputStream(Files.newOutputStream(directory.resolve(name),StandardOpenOption.CREATE_NEW));
            return new OutputStream(){
                private void admit(int n)throws IOException{check();if(n>maximum-used)throw new IOException("Complete reference output exceeds --max-output-bytes");used+=n;}
                public void write(int b)throws IOException{admit(1);file.write(b);}
                public void write(byte[] b,int off,int len)throws IOException{admit(len);file.write(b,off,len);}
                public void close()throws IOException{file.close();}
            };
        }
    }
    private static final class CompareOutput implements Outputs {
        final Path directory;long used;final long maximum;
        CompareOutput(Path directory,long maximum){this.directory=directory;this.maximum=maximum;}
        public OutputStream open(String name)throws IOException{
            InputStream expected=new BufferedInputStream(Files.newInputStream(directory.resolve(name)));
            return new OutputStream(){
                public void write(int value)throws IOException{check();if(++used>maximum)throw new IOException("Reference verification output exceeds --max-output-bytes");if(expected.read()!=(value&255))throw new IOException("Complete replay disagrees with captured output: "+name);}
                public void close()throws IOException{try{if(expected.read()!=-1)throw new IOException("Captured output has an unmatched suffix: "+name);}finally{expected.close();}}
            };
        }
    }
    private static void line(OutputStream out,String... values)throws IOException{out.write((String.join("\t",values)+"\n").getBytes(StandardCharsets.UTF_8));}
    private static FumaReferencePreparation.Result emit(List<PlinkBedDataset.Variant> variants,List<PlinkBedDataset.Sample> samples,byte[] selection,HardcallLd.Frame frame,FumaReferencePreparation.Rows rows,Map<String,String> p,Budgets b,Outputs output)throws IOException{
        try(var metadata=output.open("variants.tsv")){line(metadata,"ordinal","chr","pos","id","A1","A2");for(var v:variants)line(metadata,""+v.ordinal(),v.chromosome(),""+v.position(),v.id(),v.allele1(),v.allele2());}
        try(var meta=output.open("samples.tsv")){line(meta,"fid","iid","founder","sex");for(var s:samples)line(meta,s.familyId(),s.individualId(),""+s.founder(),""+s.sex());}
        try(var meta=output.open("sample-selection.tsv")){meta.write(selection);}
        try(var capture=new DataOutputStream(output.open("genotypes.bin"));var ld=output.open("ld.tsv");var audit=output.open("ld-audit.tsv");var decisions=output.open("variant-audit.tsv")){
            capture.writeLong(MAGIC);capture.writeInt(samples.size());capture.writeInt(variants.size());line(ld,"chr","pos1","pos2","r2");line(audit,"chr","pos1","pos2","id1","id2","status","paired_calls","r","r2","stored");
            line(decisions,"id","chr","pos","called","missing","A1_count","A2_count","A1_frequency","MAF","minor_allele","self_status","disposition");
            var sink=new FumaReferencePreparation.Sink(){
                public void capture(PlinkBedDataset.Variant v,byte[] packed)throws IOException{capture.write(packed);}
                public void assessment(FumaReferencePreparation.Assessment a)throws IOException{var v=a.variant();var f=a.frequencies();line(decisions,v.id(),v.chromosome(),""+v.position(),""+f.called(),""+f.missing(),""+f.allele1Count(),""+f.allele2Count(),""+f.allele1Frequency(),""+f.minorAlleleFrequency(),f.minorAllele().name(),a.selfStatus().name(),a.disposition().name());}
                public void pair(PlinkBedDataset.Variant a,PlinkBedDataset.Variant z,HardcallLd.Pair r,boolean stored)throws IOException{
                    line(audit,a.chromosome(),""+a.position(),""+z.position(),a.id(),z.id(),r.status().name(),""+r.pairedCalls(),""+r.correlation(),""+r.rSquared(),""+stored);
                    if(stored)line(ld,a.chromosome(),""+a.position(),""+z.position(),""+r.rSquared());
                }
            };
            var result=FumaReferencePreparation.prepare(variants,frame,rows,nativeOptions(p),b.nativeLimits(),sink,FumaReferenceCli::check);
            try(var refs=output.open("reference.tsv")){line(refs,"chr","pos","rsID","A1","A2","MAF");for(var v:result.variants())line(refs,""+v.chromosome(),""+v.position(),v.rsId(),v.a1(),v.a2(),""+v.maf());}
            return result;
        }
    }
    private static final class CapturedRows implements AutoCloseable,FumaReferencePreparation.Rows {
        final FileChannel channel;final Map<String,Integer> index=new HashMap<>();final int bytes;
        CapturedRows(Path file,List<PlinkBedDataset.Variant> variants,int samples)throws IOException{
            bytes=(samples+3)/4;try(var in=new DataInputStream(Files.newInputStream(file))){if(in.readLong()!=MAGIC||in.readInt()!=samples||in.readInt()!=variants.size())throw new IOException("Captured genotype header disagrees with metadata");}
            if(Files.size(file)!=Math.addExact(16,Math.multiplyExact((long)bytes,variants.size())))throw new IOException("Captured genotype file has truncation or trailing bytes");
            for(int i=0;i<variants.size();i++)if(index.put(variants.get(i).id(),i)!=null)throw new IOException("Duplicate captured variant ID");channel=FileChannel.open(file,StandardOpenOption.READ);
        }
        public byte[] read(PlinkBedDataset.Variant v)throws IOException{Integer i=index.get(v.id());if(i==null)throw new IOException("Unknown captured variant");ByteBuffer b=ByteBuffer.allocate(bytes);long offset=16+Math.multiplyExact((long)i,bytes);while(b.hasRemaining()){check();int n=channel.read(b,offset+b.position());if(n<0)throw new IOException("Captured genotype row truncated");}return b.array();}
        public void close()throws IOException{channel.close();}
    }
    private static Panel readPanel(Path directory,Budgets b)throws IOException{
        Map<Path,String> pins=new LinkedHashMap<>();Path carrier=directory.resolve("reference-bundle.json");pin(carrier,pins);byte[] raw=AdmixtureInputs.raw(carrier,1048576);if(!AdmixtureInputs.digest(raw).equals(pins.get(carrier.toAbsolutePath().normalize())))throw new IOException("Reference carrier changed while reading");
        Map<?,?> m=AdmixtureInputs.decode(raw);if(!m.keySet().equals(Set.of("format","status","protocol","payload","accounting"))||!FORMAT.equals(m.get("format"))||!"completed".equals(m.get("status")))throw new IOException("Not a complete supported reference bundle");
        Map<?,?> pm=AdmixtureInputs.map(m.get("protocol"));if(!pm.keySet().equals(PROTOCOL)||!FORMAT.equals(pm.get("format")))throw new IOException("Unexpected captured reference protocol");Map<String,String> p=new LinkedHashMap<>();for(String k:PROTOCOL)if(pm.get(k) instanceof String s)p.put(k,s);else throw new IOException("Reference protocol values must be strings");p=protocol(p);
        Map<?,?> payload=AdmixtureInputs.map(m.get("payload"));if(!payload.keySet().equals(Set.copyOf(PAYLOAD)))throw new IOException("Reference payload has unknown or missing members");long bytes=0;
        for(String name:PAYLOAD){Path f=directory.resolve(name);Map<?,?> record=AdmixtureInputs.map(payload.get(name));if(!record.keySet().equals(Set.of("bytes","sha256")))throw new IOException("Unexpected payload receipt");pin(f,pins);long size=Files.size(f);bytes=Math.addExact(bytes,size);if(bytes>b.output()||!Long.toString(size).equals(String.valueOf(record.get("bytes")))||!pins.get(f.toAbsolutePath().normalize()).equals(record.get("sha256")))throw new IOException("Payload integrity or complete byte capacity failure: "+name);}
        List<PlinkBedDataset.Variant> variants=new ArrayList<>();for(var r:tsv(directory.resolve("variants.tsv"),"ordinal\tchr\tpos\tid\tA1\tA2",6,b.nativeLimits().maximumVariants(),b.metadataBound(),pins)){for(String v:r)token(v);variants.add(new PlinkBedDataset.Variant(r[1],r[3],Long.parseLong(r[2]),r[4],r[5],Long.parseLong(r[0])));}
        List<PlinkBedDataset.Sample> samples=new ArrayList<>();Set<String> seen=new HashSet<>();for(var r:tsv(directory.resolve("samples.tsv"),"fid\tiid\tfounder\tsex",4,b.samples(),b.metadataBound(),pins)){token(r[0]);token(r[1]);boolean founder=strictBoolean(r[2]);int sex=Integer.parseInt(r[3]);if(sex<0||sex>2||!seen.add(key(r[0],r[1])))throw new IOException("Invalid or duplicate captured sample metadata");samples.add(new PlinkBedDataset.Sample(r[0],r[1],founder,sex));}
        List<PlinkBedDataset.Sample> selected=new ArrayList<>();seen.clear();var selection=tsv(directory.resolve("sample-selection.tsv"),"source_index\tfid\tiid\tfounder\tsex\trequested\tselected",7,b.samples(),b.metadataBound(),pins);
        for(int i=0;i<selection.size();i++){var r=selection.get(i);token(r[1]);token(r[2]);boolean founder=strictBoolean(r[3]),request=strictBoolean(r[5]),keep=strictBoolean(r[6]);int sex=Integer.parseInt(r[4]);if(!Integer.toString(i).equals(r[0])||sex<0||sex>2||!seen.add(key(r[1],r[2]))||keep!=(request&&(!p.get("sample-policy").equals("founders")||founder)))throw new IOException("Captured sample selection disagrees with the explicit policy");if(keep)selected.add(new PlinkBedDataset.Sample(r[1],r[2],founder,sex));}
        if(samples.size()<2||!samples.equals(selected))throw new IOException("Captured sample frame disagrees with complete source selection audit");
        return new Panel(p,variants,samples,AdmixtureInputs.raw(directory.resolve("sample-selection.tsv"),b.metadataBound()),pins,AdmixtureInputs.map(m.get("accounting")));
    }
    private static boolean strictBoolean(String s)throws IOException{if(!Set.of("true","false").contains(s))throw new IOException("Expected literal true or false");return Boolean.parseBoolean(s);}

    /** Hash admission plus COMPLETE native replay; unsigned producer claims alone are insufficient. */
    static Admission admit(Path directory,Map<String,String> limitOptions,Map<Path,String> sources)throws IOException{
        directory=directory.toAbsolutePath().normalize();Budgets b=budgets(limitOptions);Panel panel=readPanel(directory,b);sources.putAll(panel.pins());
        try(var rows=new CapturedRows(directory.resolve("genotypes.bin"),panel.variants(),panel.samples().size())){
            var result=emit(panel.variants(),panel.samples(),panel.selection(),frame(panel.samples().size(),all(panel.samples().size()),panel.protocol(),b),rows,panel.protocol(),b,new CompareOutput(directory,b.output()));verifyAccounting(panel,result);
        }
        unchanged(panel.pins());var options=nativeOptions(panel.protocol());return new Admission(directory.resolve("reference.tsv"),directory.resolve("ld.tsv"),identity(panel.protocol()),panel.protocol().get("assembly"),options.ldFloor(),options.window());
    }
    private static String help(){return """
        Native autosomal diploid reference preparation, capture and complete replay.
          fuma-tools --method prepare-reference --bfile INPUT_PREFIX
            --variants variants.tsv --samples samples.tsv --sample-policy founders|selected
            --assembly GRCh37 --population DECLARED_POPULATION --release DECLARED_RELEASE
            --minimum-maf 0 --ld-floor 0.05 --window-bp 1000000 --window-index-gap 99998
            --out NEW_DIRECTORY
          fuma-tools --method replay-reference --bundle CAPTURE_DIRECTORY --out NEW_DIRECTORY
        Exact TSV headers: variants: id; samples: fid<TAB>iid. Explicit selections required.
        Selected samples retain FAM order. founders intersects the explicit selection with
        recorded FAM parent-zero flags; selected includes every explicitly selected sample.
        Variants must have resolved alleles and unique IDs/coordinates; chromosomes 1..22 only.
        Exclude no-call, fewer-than-two-call, MAF-zero, constant-call and MAF < minimum variants,
        with a complete audit.
        Retained genomic ranks determine inclusive distance/index windows. Store r2 >= floor.
        Undefined eligible LD FAILS the whole request; no sparse prefix or imputed zero is admitted.
        Capture includes selected hardcalls for ALL requested variants, including exclusions.
        Replay verifies payload hashes, recomputes the entire declared family and compares every
        output payload byte. Original producer paths and upstream software are not needed.
        Locus handoff: fuma-tools --method loci --reference-bundle CAPTURE_DIRECTORY ... --help
        Limits: --max-samples 100000 --max-variants 100000 --max-pairs 2000000
          --max-word-work 100000000 --max-owned-bytes 268435456
          --max-output-bytes 268435456 --max-metadata-bytes 16777216.
        Work includes marginal self checks and all eligible pairs; storage is admitted before
        the complete pair pass and must fit half the heap. Metadata has smaller heap sublimits.
        Read WARNINGS.md, session.json, variant-audit.tsv, sample-selection.tsv and ld-audit.tsv.
        Exit 0: completed with review warnings; 2: failed. Existing outputs are preserved.
        No external runtime is required. No download, ancestry inference or hosted FUMA reproduction.
        """;}
}
