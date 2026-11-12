/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.HtseqFeatureAssignment;
import org.jlinalg.sequence.HtseqReadCounter;
import org.jlinalg.sequence.HtseqReadCounter.*;
import static org.jlinalg.cli.FollowupSupport.*;

/** Java-only HTSeq2.1.2 SAM/BAMâ€“GTF counting and complete deterministic replay. */
public final class HtseqNativeCli {
    private HtseqNativeCli() { }
    private static final Object ORDINAL=new Object();
    private static final Set<String> FIELDS=Set.of("schema_version","operation","version","alignment","annotation","format","order","stranded","overlap","nonunique","secondary","supplementary","minimum_mapq","feature_type","id_attribute","genome_label","annotation_label","counting_unit","out","max_input_bytes","max_output_bytes","timeout_seconds","maximum_features","maximum_buffer_records","maximum_units","maximum_blocks_per_alignment");
    private record Request(Path source,String sourceHash,Map<String,Object> values,Path alignment,Path annotation,Path out,long inputBytes,long outputBytes,int features,int buffer,int blocks,long units) { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(List.of(args).contains("--help")){out.println("Java-only HTSeq2.1.2 counting and replay\nhtseq-tools --engine native --request REQUEST.json\nNative COUNT uses the original COUNT schema without runtime_home/memory_bytes, and explicit version2.1.2. Native SAM/BAM and plain GTF preparation implements name/position mate pairing, M/=/X blocks and deterministic NONE/ALL/FRACTION allocation. counting_unit must be original-auto. Optional maximum_features, maximum_buffer_records, maximum_units, maximum_blocks_per_alignment, max_input_bytes, max_output_bytes and timeout_seconds are logical/cooperative budgets. VERIFY takes schema_version1, operation VERIFY, run and NEW out; it checks the complete hash inventory and independently replays the saved native count. Historical HTSeq editions remain explicit upstream operations; no Python or installation is needed by this engine.");return 0;}
        try {
            var options=options(args,Set.of("request"));Path source=Path.of(required(options,"request")).toAbsolutePath().normalize();var raw=read(source);
            String operation=text(raw,"operation");
            if(operation.equals("VERIFY")) {
                allowed(raw,Set.of("schema_version","operation","run","out"));version(raw);
                Path run=source.getParent().resolve(text(raw,"run")).toAbsolutePath().normalize(),target=source.getParent().resolve(text(raw,"out")).toAbsolutePath().normalize();
                if(run.startsWith(target)||target.startsWith(run))throw new IOException("VERIFY source and output must be disjoint");
                return NativeToolSupport.run(Map.of("out",target.toString()),"native-htseq-verify",(directory,manifest)->verify(run,directory,manifest),out,err);
            }
            Request request=request(source,raw);var transactionOptions=Map.of("out",request.out.toString(),"timeout",Long.toString(integer(raw,"timeout_seconds",3600,1,86400)));
            return NativeToolSupport.run(transactionOptions,"native-htseq-count",(directory,manifest)->execute(request,directory,manifest),out,err);
        }catch(Exception failure){err.println("Native HTSeq not started: "+failure.getMessage());return 2;}
    }
    private static Request request(Path source,Map<String,Object> raw)throws IOException {
        allowed(raw,FIELDS);version(raw);if(!text(raw,"operation").equals("COUNT"))throw new IOException("Native HTSeq operation must be COUNT or VERIFY");
        if(!text(raw,"version").equals("2.1.2"))throw new IOException("Native file counting requires explicit HTSeq2.1.2; historical editions remain upstream");
        choice(raw,"format","sam","bam");choice(raw,"order","name","pos");choice(raw,"stranded","yes","no","reverse");choice(raw,"overlap","union","intersection-strict","intersection-nonempty");choice(raw,"nonunique","none","all","fraction");choice(raw,"secondary","ignore","score");choice(raw,"supplementary","ignore","score");choice(raw,"counting_unit","original-auto");
        integer(raw,"minimum_mapq",-1,0,255);text(raw,"feature_type");text(raw,"id_attribute");text(raw,"genome_label");text(raw,"annotation_label");
        Path parent=source.getParent();
        String sourceHash=hash(source);if(!read(source).equals(raw))throw new IOException("Native HTSeq request changed during parsing");
        return new Request(source,sourceHash,Map.copyOf(raw),parent.resolve(text(raw,"alignment")).normalize(),parent.resolve(text(raw,"annotation")).normalize(),parent.resolve(text(raw,"out")).normalize(),integer(raw,"max_input_bytes",1073741824,1,107374182400L),integer(raw,"max_output_bytes",67108864,1024,107374182400L),(int)integer(raw,"maximum_features",2000000,1,2000000),(int)integer(raw,"maximum_buffer_records",100000,1,30000000),(int)integer(raw,"maximum_blocks_per_alignment",100000,1,1000000),integer(raw,"maximum_units",1000000000000L,1,1000000000000L));
    }
    private static Config config(Request request,Layout layout)throws IOException {
        var v=request.values;return new Config(layout,Strand.valueOf(text(v,"stranded").toUpperCase(Locale.ROOT)),HtseqFeatureAssignment.Mode.valueOf(text(v,"overlap").toUpperCase(Locale.ROOT).replace('-','_')),Nonunique.valueOf(text(v,"nonunique").toUpperCase(Locale.ROOT)),(int)integer(v,"minimum_mapq",-1,0,255),FlagPolicy.valueOf(text(v,"secondary").toUpperCase(Locale.ROOT)),FlagPolicy.valueOf(text(v,"supplementary").toUpperCase(Locale.ROOT)));
    }
    private static void execute(Request request,Path directory,Map<String,Object> manifest)throws Exception {
        Path inputs=Files.createDirectory(directory.resolve("inputs"));String format=text(request.values,"format");Path alignment=inputs.resolve("alignment."+format),annotation=inputs.resolve("annotation.gtf");
        String samPin=capture(request.alignment,alignment,request.inputBytes),gtfPin=capture(request.annotation,annotation,request.inputBytes);
        Files.copy(request.source,directory.resolve("request.original.json"));
        if(!request.sourceHash.equals(hash(request.source))||!request.sourceHash.equals(hash(directory.resolve("request.original.json"))))throw new IOException("Native HTSeq request changed during capture");
        var effective=new LinkedHashMap<>(request.values);effective.put("alignment","inputs/alignment."+format);effective.put("annotation","inputs/annotation.gtf");effective.put("out",".");
        Files.writeString(directory.resolve("effective-request.json"),NextflowJson.json(effective)+"\n");
        var features=HtseqNativePreparation.features(annotation,text(request.values,"feature_type"),text(request.values,"id_attribute"),!text(request.values,"stranded").equals("no"),request.features);
        var written=new long[]{0};var counts=new ArrayList<String[]>();HtseqReadCounter.Counts result;Layout layout;long rawRecords=0,filteredPrimary=0;long[] units={0};long missing=0,ambiguous=0;
        try(var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(alignment);var records=reader.iterator();
                var raw=writer(directory.resolve("read-preparation.tsv"),written,request.outputBytes);var ledger=writer(directory.resolve("unit-dispositions.tsv"),written,request.outputBytes)) {
            if(reader.type()!=(format.equals("sam")?SamReader.Type.SAM_TYPE:SamReader.Type.BAM_TYPE))throw new IOException("Declared native SAM/BAM format does not match actual file type");
            if(format.equals("sam")){try(var bytes=Files.newInputStream(alignment)){if(bytes.read()==31)throw new IOException("Compressed SAM is outside the declared native plain-SAM contract");}}
            row(raw,new String[]{"record","read_name","flags","reference","position","cigar","MAPQ","NH","preparation"});
            row(ledger,new String[]{"unit","read_name","first_record","second_record","state","NH_check","unknown_chromosome","features"});
            SAMRecord read=records.hasNext()?records.next():null;layout=read!=null&&read.getReadPairedFlag()?Layout.PAIRED:Layout.SINGLE;var config=config(request,layout);
            java.util.function.BooleanSupplier cancelled=()->{try{NativeToolSupport.check();return false;}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}};
            var model=HtseqReadCounter.prepare(features,config,HtseqFeatureAssignment.Limits.defaults(),cancelled);var accumulator=new HtseqReadCounter.Accumulator(model,config,request.units,cancelled);
            HtseqNativePreparation.PairConsumer consume=(a,b)->{
                NativeToolSupport.check();SAMRecord representative=a==null?b:a;var unit=new Unit(representative.getReadName(),layout,HtseqNativePreparation.alignment(a,request.blocks),HtseqNativePreparation.alignment(b,request.blocks));
                var decision=accumulator.add(unit);row(ledger,new String[]{Long.toString(++units[0]),representative.getReadName(),a==null?"":a.getTransientAttribute(ORDINAL).toString(),b==null?"":b.getTransientAttribute(ORDINAL).toString(),decision.state().name(),decision.nhCheck().name(),Boolean.toString(decision.unknownChromosome()),NextflowJson.json(decision.features())});
            };
            var pairer=layout==Layout.PAIRED?new HtseqNativePreparation.Pairer(text(request.values,"order"),request.buffer,consume):null;
            boolean primaryOnly=layout==Layout.PAIRED&&config.secondary()==FlagPolicy.IGNORE&&config.supplementary()==FlagPolicy.IGNORE;
            while(read!=null) {
                NativeToolSupport.check();read.setTransientAttribute(ORDINAL,++rawRecords);if(layout==Layout.PAIRED)HtseqNativePreparation.Pairer.validate(read);
                boolean ignored=primaryOnly&&read.isSecondaryOrSupplementary();if(ignored)filteredPrimary++;
                row(raw,new String[]{Long.toString(rawRecords),read.getReadName(),Integer.toString(read.getFlags()),read.getReferenceName(),Integer.toString(read.getAlignmentStart()),read.getCigarString(),Integer.toString(read.getMappingQuality()),read.getAttribute("NH")==null?"":read.getAttribute("NH").toString(),ignored?"ignored-before-pairing":layout==Layout.PAIRED?"pairing":"single-read"});
                if(!ignored){if(pairer==null)consume.accept(read,null);else pairer.add(read);}
                read=records.hasNext()?records.next():null;
            }
            if(pairer!=null){pairer.finish();missing=pairer.missing;ambiguous=pairer.ambiguous;}result=accumulator.snapshot();
        }
        try(var output=writer(directory.resolve("counts.tsv"),written,request.outputBytes)) {
            row(output,new String[]{"feature","count"});for(var entry:result.counts().entrySet()){NativeToolSupport.check();row(output,new String[]{entry.getKey(),Double.toString(entry.getValue())});counts.add(new String[]{"feature",entry.getKey(),Double.toString(entry.getValue())});}
        }
        var statistics=result.statistics();long[] values={statistics.noFeature(),statistics.ambiguous(),statistics.tooLowAQual(),statistics.notAligned(),statistics.alignmentNotUnique()};String[] categories={"__no_feature","__ambiguous","__too_low_aQual","__not_aligned","__alignment_not_unique"};
        try(var output=writer(directory.resolve("summary.tsv"),written,request.outputBytes)){row(output,new String[]{"category","count"});for(int i=0;i<categories.length;i++){row(output,new String[]{categories[i],Long.toString(values[i])});counts.add(new String[]{"summary",categories[i],Long.toString(values[i])});}}
        try(var output=writer(directory.resolve("results.tsv"),written,request.outputBytes)){row(output,new String[]{"kind","feature","count"});for(String[] row:counts)row(output,row);}
        var report=new LinkedHashMap<String,Object>();report.put("engine","native-java");report.put("source_version","2.1.2");report.put("layout_from_first_record",layout.name());report.put("raw_alignment_records",rawRecords);report.put("ignored_before_pairing",filteredPrimary);report.put("processed_units",result.processedUnits());report.put("unknown_chromosome_units",statistics.unknownChromosome());report.put("missing_mates",missing);report.put("ambiguous_position_pairings",ambiguous);report.put("features",features.size());
        Files.writeString(directory.resolve("report.json"),NextflowJson.json(report)+"\n");
        String scope="Native HTSeq2.1.2 deterministic SAM/BAMâ€“GTF count. Source name-group and position-buffer pairing, first-record layout selection, primary-only pre-pair filtering when both secondary/supplementary policies are IGNORE, M/=/X blocks, strand inversion and NONE/ALL/FRACTION allocation are explicit. Missing NH retains the source short-circuit. Orphans are retained; positional ambiguities use original insertion order. Native SAM/BAM transport uses HTSJDK5.0.0. NH must be an exact32-bit integer; GTF is plain version2 syntax. Historical editions, random allocation, CRAM, barcode/UMI processing and normalization remain separate.";
        Files.writeString(directory.resolve("WARNINGS.md"),scope+"\nMissing mate records: "+missing+"; ambiguous positional pairings: "+ambiguous+"; unknown-chromosome units: "+statistics.unknownChromosome()+". Inspect the complete preparation and unit ledgers. The supplied genome/annotation labels are declarations, not reference validation. Buffer/features/block/unit limits and cooperative timeout are not OS RAM quotas.\n");
        if(!samPin.equals(hash(request.alignment))||!gtfPin.equals(hash(request.annotation))||!samPin.equals(hash(alignment))||!gtfPin.equals(hash(annotation))||!request.sourceHash.equals(hash(request.source)))throw new IOException("Original or captured HTSeq input/request changed during counting");
        long outputSize=0;try(var paths=Files.walk(directory)){for(Path file:paths.filter(Files::isRegularFile).toList())if(!file.startsWith(inputs))outputSize=Math.addExact(outputSize,Files.size(file));}
        if(outputSize>request.outputBytes)throw new IOException("Native HTSeq complete output capacity exceeded");
        manifest.put("scope",scope);manifest.put("input_sha256",Map.of("alignment",samPin,"annotation",gtfPin,"request",hash(request.source)));manifest.put("count_report",report);
    }
    private static BufferedWriter writer(Path path,long[] used,long maximum)throws IOException {
        OutputStream bounded=new FilterOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)) {
            private void reserve(int n)throws IOException {NativeToolSupport.check();if(n>maximum-used[0])throw new IOException("Native HTSeq output byte capacity exceeded");used[0]+=n;}
            @Override public void write(int value)throws IOException{reserve(1);out.write(value);}
            @Override public void write(byte[] data,int offset,int length)throws IOException{reserve(length);out.write(data,offset,length);}
        };
        return new BufferedWriter(new OutputStreamWriter(bounded,StandardCharsets.UTF_8));
    }
    private static String capture(Path source,Path target,long maximum)throws IOException {
        if(!Files.isRegularFile(source)||Files.size(source)>maximum)throw new IOException("Missing/excessive native HTSeq input: "+source);
        String before=hash(source);Files.copy(source,target);if(Files.size(target)>maximum||!before.equals(hash(target))||!before.equals(hash(source)))throw new IOException("Native HTSeq input changed during capture");return before;
    }
    private static void verify(Path run,Path directory,Map<String,Object> manifest)throws Exception {
        var loader=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
        Object parsed=loader.load(Files.readString(run.resolve("manifest.yaml")));
        if(!(parsed instanceof Map<?,?> saved)||!"native-htseq-count".equals(saved.get("operation"))||!Boolean.TRUE.equals(saved.get("result_admitted"))||!(saved.get("output_sha256") instanceof Map<?,?> expected))throw new IOException("Not an admitted native HTSeq count bundle");
        var expectedPortable=new java.util.TreeMap<String,String>();
        for(var entry:expected.entrySet())if(!(entry.getKey() instanceof String name)||!(entry.getValue() instanceof String sha)||expectedPortable.put(name.replace('\\','/'),sha)!=null)throw new IOException("Invalid/colliding saved native inventory entry");
        var actual=inventory(run);if(!actual.equals(expectedPortable))throw new IOException("Native HTSeq complete saved inventory differs");
        var status=read(run.resolve("analysis-status.json"));if(!Boolean.TRUE.equals(status.get("result_admitted")))throw new IOException("Saved native HTSeq status does not admit a result");
        var effective=read(run.resolve("effective-request.json"));String format=text(effective,"format");
        if(!text(effective,"alignment").equals("inputs/alignment."+format)||!text(effective,"annotation").equals("inputs/annotation.gtf"))throw new IOException("Saved native HTSeq captured paths differ");
        var request=request(run.resolve("effective-request.json"),effective);Path replay=Files.createDirectory(directory.resolve("recomputed"));execute(request,replay,new LinkedHashMap<>());
        for(String name:List.of("counts.tsv","summary.tsv","results.tsv","read-preparation.tsv","unit-dispositions.tsv","report.json","effective-request.json"))if(!hash(run.resolve(name)).equals(hash(replay.resolve(name))))throw new IOException("Native HTSeq replay differs: "+name);
        // Recheck the complete source inventory after replay, including missing/extra files.
        if(!actual.equals(inventory(run)))throw new IOException("Saved native HTSeq source changed during verification");
        Files.writeString(directory.resolve("verification.json"),"{\"status\":\"verified-native-htseq-count\",\"complete_inventory_checked\":true,\"native_file_preparation_and_count_replayed\":true,\"execution_authenticated\":false}\n");manifest.put("source_manifest_sha256",hash(run.resolve("manifest.yaml")));
    }
    private static Map<String,String> inventory(Path root)throws IOException {
        if(!Files.isDirectory(root,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Native HTSeq bundle must be an ordinary directory");
        var result=new java.util.TreeMap<String,String>();try(var paths=Files.walk(root)) {
            for(Path file:paths.toList()) {
                NativeToolSupport.check();if(Files.isSymbolicLink(file))throw new IOException("Symbolic links are outside the saved native HTSeq bundle contract");
                if(Files.isRegularFile(file)&&!file.equals(root.resolve("manifest.yaml")))result.put(root.relativize(file).toString().replace('\\','/'),hash(file));
            }
        }
        return result;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> read(Path path)throws IOException {
        if(Files.size(path)>1048576)throw new IOException("Native HTSeq JSON metadata exceeds1MiB");Object raw=SimpleJson.parse(Files.readString(path));if(!(raw instanceof Map<?,?> map))throw new IOException("Expected JSON object");return (Map<String,Object>)map;
    }
    private static void allowed(Map<String,Object> raw,Set<String> keys)throws IOException {for(String key:raw.keySet())if(!keys.contains(key))throw new IOException("Unsupported native HTSeq field: "+key);}
    private static void version(Map<String,Object> raw)throws IOException {if(!Long.valueOf(1).equals(raw.get("schema_version")))throw new IOException("Require schema_version1");}
    private static String text(Map<String,Object> raw,String key)throws IOException {Object v=raw.get(key);if(!(v instanceof String s)||s.isBlank()||s.indexOf('\n')>=0||s.indexOf('\r')>=0)throw new IOException("Require explicit text field: "+key);return s;}
    private static long integer(Map<String,Object> raw,String key,long fallback,long min,long max)throws IOException {Object v=raw.getOrDefault(key,fallback);if(!(v instanceof Long n)||n<min||n>max)throw new IOException("Require integer "+key+" in["+min+","+max+"]");return n;}
    private static void choice(Map<String,Object> raw,String key,String... choices)throws IOException {if(!List.of(choices).contains(text(raw,key)))throw new IOException("Unsupported native HTSeq "+key);}
}
