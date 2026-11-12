/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.CutadaptTrimmer;
import org.jlinalg.sequence.FastqIO;
import static org.jlinalg.cli.FollowupSupport.*;

/** Streaming single/paired native adapter trimming with a complete per-read disposition ledger. */
final class NativeAdapterTrim {
    private NativeAdapterTrim() { }
    static void trim(Map<String,String> options,Path directory,Map<String,Object> manifest)throws Exception {
        if(options.containsKey("python")||options.containsKey("cores"))throw new IOException("Native adapter trimming does not accept --python or --cores");
        Path first=Path.of(required(options,"reads")).toAbsolutePath().normalize(),second=options.containsKey("reads2")?Path.of(options.get("reads2")).toAbsolutePath().normalize():null;
        if(second!=null&&Files.isSameFile(first,second))throw new IOException("FASTQ mates must be distinct files");
        if(second==null&&options.containsKey("adapter3-2"))throw new IOException("adapter3-2 requires reads2");
        int overlap=integer(options,"overlap",3,1,10000),minimum=integer(options,"minimum-length",0,0,10000000),quality=integer(options,"quality-cutoff",0,0,93),left=integer(options,"cut-left",0,0,10000000);
        double rate=number(options,"error-rate",0.1);var a=new CutadaptTrimmer(required(options,"adapter3"),rate,overlap);var b=second==null?null:new CutadaptTrimmer(required(options,"adapter3-2"),rate,overlap);
        long maximum=integer(options,"alignment-operations",1000000000,1,Integer.MAX_VALUE);long[] operations={0};
        CutadaptTrimmer.Budget budget=cells->{NativeToolSupport.check();operations[0]=Math.addExact(operations[0],cells);if(operations[0]>maximum)throw new IOException("Native adapter alignment-operation capacity exceeded; no partial results admitted");};
        var pins=new LinkedHashMap<String,String>();pins.put("reads",hash(first));if(second!=null)pins.put("reads2",hash(second));
        long input=0,output=0,adapter1=0,adapter2=0,shortPairs=0;
        try(var reader=new FastqIO.Reader(first);var mate=second==null?null:new FastqIO.Reader(second);
                var writer=new FastqIO.Writer(directory.resolve("trimmed_R1.fastq.gz"));var writer2=second==null?null:new FastqIO.Writer(directory.resolve("trimmed_R2.fastq.gz"));
                var ledger=Files.newBufferedWriter(directory.resolve("read-dispositions.tsv"))) {
            row(ledger,new String[]{"record","mate","header","input_length","fixed_left","quality_right","adapter_start","adapter_end","read_start","read_end","alignment_score","errors","retained_length","pair_retained"});
            FastqIO.Read read;
            while((read=reader.next())!=null) {
                NativeToolSupport.check();input++;var other=mate==null?null:mate.next();
                if(mate!=null&&(other==null||!read.pairId().equals(other.pairId())))throw new IOException("FASTQ mate count/identity/order mismatch at record "+input);
                var x=a.trim(read,left,quality,budget);var y=b==null?null:b.trim(other,left,quality,budget);
                if(x.match()!=null)adapter1++;if(y!=null&&y.match()!=null)adapter2++;
                boolean keep=x.read().sequence().length()>=minimum&&(y==null||y.read().sequence().length()>=minimum);
                if(keep){writer.write(x.read());if(writer2!=null)writer2.write(y.read());output++;}else shortPairs++;
                disposition(ledger,input,1,read,x,keep);if(y!=null)disposition(ledger,input,2,other,y,keep);
            }
            if(mate!=null&&mate.next()!=null)throw new IOException("FASTQ mate has extra records");
        }
        if(!pins.get("reads").equals(hash(first))||second!=null&&!pins.get("reads2").equals(hash(second)))throw new IOException("Original FASTQ changed during native adapter trimming");
        var counts=new LinkedHashMap<String,Object>();counts.put("input",input);counts.put("output",output);counts.put("read1_with_adapter",adapter1);counts.put("read2_with_adapter",second==null?null:adapter2);counts.put("filtered",Map.of("too_short",shortPairs));
        Files.writeString(directory.resolve("cutadapt.json"),NextflowJson.json(Map.of("tag","OmicsMatic trimming report","engine","native-java","source_algorithm","Cutadapt5.2 regular 3-prime","read_counts",counts,"alignment_operations",operations[0],"pair_filter","any"))+"\n");
        var metrics=new ArrayList<String[]>();metrics.add(new String[]{"input_reads_or_pairs",Long.toString(input)});metrics.add(new String[]{"output_reads_or_pairs",Long.toString(output)});metrics.add(new String[]{"read1_with_adapter",Long.toString(adapter1)});if(second!=null)metrics.add(new String[]{"read2_with_adapter",Long.toString(adapter2)});
        table(directory.resolve("results.tsv"),List.of("metric","value"),metrics);
        manifest.put("input_sha256",pins);manifest.put("source_algorithm","Cutadapt5.2");manifest.put("alignment_operations",operations[0]);manifest.put("input_reads_or_pairs",input);manifest.put("output_reads_or_pairs",output);
        String scope="Native Cutadapt5.2 regular3-prime alignment, source indel/tie/wildcard rules, Phred+33 quality trimming after fixed left cutting and pair-filter=any minimum length. Complete headers, bases and qualities are preserved within each retained span. No Cutadapt process, linked/5-prime adapter, demultiplexing or reverse-complement search is used. error-rate1 means one absolute error under the source interface; overlap is capped to adapter length.";
        manifest.put("scope",scope);Files.writeString(directory.resolve("WARNINGS.md"),scope+"\n"+(input==0?"No input records were supplied.\n":output==0?"All input reads/pairs were filtered; do not infer biological absence.\n":"")+"Read read-dispositions.tsv and the explicit settings before downstream use. Logical alignment-operation limits and cooperative timeouts are not OS memory/CPU quotas.\n");
    }
    private static void disposition(java.io.Writer ledger,long record,int mate,FastqIO.Read original,CutadaptTrimmer.Result result,boolean retained)throws IOException {
        var m=result.match();row(ledger,new String[]{Long.toString(record),Integer.toString(mate),original.header(),Integer.toString(original.sequence().length()),Integer.toString(result.fixedLeft()),Integer.toString(result.qualityRight()),m==null?"":Integer.toString(m.adapterStart()),m==null?"":Integer.toString(m.adapterEnd()),m==null?"":Integer.toString(m.readStart()),m==null?"":Integer.toString(m.readEnd()),m==null?"":Integer.toString(m.score()),m==null?"":Integer.toString(m.errors()),Integer.toString(result.read().sequence().length()),Boolean.toString(retained)});
    }
}
