/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Properties;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.jlinalg.alignment.BamBaiAudit;
import org.jlinalg.alignment.SambambaStreamingBamAudit;

/** Independent HTSJDK indexed-query observations against supplied geometric expectations. */
public final class BaiQueryProbe {
    private static SamReaderFactory factory(){
        return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
            .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS,SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX)
            .disable(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES);
    }
    private static void save(Path path,Properties report)throws IOException {
        try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){report.store(out,"Actual runtime observation; no data repaired");}
    }
    private static void audit(Path bam,Path bai,Path output)throws Exception {
        Properties report=new Properties();
        try{
            var summary=BamBaiAudit.audit(bam,bai,new SambambaStreamingBamAudit.Limits(1L<<40,8L<<40,1L<<40,16<<20,4<<20,4<<20),new BamBaiAudit.Limits(64L<<20,1_000_000,1_000_000,600_000));
            report.setProperty("status","PASS_NATIVE_COMPLETE_BAM_BAI_AUDIT");
            report.setProperty("records",Long.toString(summary.records()));report.setProperty("mapped",Long.toString(summary.placedMapped()));
            report.setProperty("placed_unmapped",Long.toString(summary.placedUnmapped()));report.setProperty("unplaced",Long.toString(summary.unplaced()));
            report.setProperty("references",Integer.toString(summary.references()));report.setProperty("data_chunks",Long.toString(summary.dataChunks()));
            report.setProperty("linear_entries",Long.toString(summary.linearEntries()));report.setProperty("equivalent_offset_aliases",Long.toString(summary.equivalentOffsetAliases()));
            report.setProperty("bam_bytes",Long.toString(summary.bam().bytes()));report.setProperty("bam_sha256",summary.bam().sha256());
            report.setProperty("bai_bytes",Long.toString(summary.bai().bytes()));report.setProperty("bai_sha256",summary.bai().sha256());save(output,report);
        }catch(Exception failure){
            report.setProperty("status","FAIL2_NO_ACCEPTED_BAI_AUDIT");report.setProperty("error_class",failure.getClass().getName());
            report.setProperty("error",String.valueOf(failure.getMessage()));report.setProperty("action","Retain the original pair; investigate this exact cause. No offset or metadata is silently repaired.");save(output,report);throw failure;
        }
    }
    private static void index(Path bam,Path bai,Path report)throws Exception {
        if(Files.exists(bai))throw new IOException("Refuse existing index destination");
        try(var reader=factory().open(bam)){BAMIndexer.createIndex(reader,bai);}
        var properties=new Properties();properties.setProperty("status","NATIVE_HTSJDK_INDEX_CREATED_NOT_YET_AUDITED");save(report,properties);
    }
    private static void query(Path bam,Path bai,Path queries,Path observed,Path report)throws Exception {
        long checked=0;var summary=new Properties();
        try(var reader=factory().open(SamInputResource.of(bam).index(bai));var input=Files.newBufferedReader(queries,StandardCharsets.UTF_8);
            var out=Files.newBufferedWriter(observed,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            if(!reader.hasIndex())throw new IOException("Explicit selected BAI was not opened");
            out.write("id\treference\tstart\tend\texpected_mapped_names\tobserved_mapped_names\tmatch\n");
            for(String line;(line=input.readLine())!=null;){
                String[] fields=line.split("\t",-1);if(fields.length!=5)throw new IOException("Invalid independent query row");
                var names=new ArrayList<String>();
                try(var records=reader.queryOverlapping(fields[1],Integer.parseInt(fields[2]),Integer.parseInt(fields[3]))){
                    while(records.hasNext()){var record=records.next();if(!record.getReadUnmappedFlag())names.add(record.getReadName());}
                }
                String actual=String.join(",",names);boolean equal=fields[4].equals(actual);
                out.write(line+"\t"+actual+"\t"+equal+"\n");out.flush();
                if(!equal)throw new IOException("Indexed mapped-record query differs at independent query "+fields[0]+" "+fields[1]+":"+fields[2]+"-"+fields[3]);
                checked++;
            }
            summary.setProperty("status","PASS_INDEPENDENT_GEOMETRIC_MAPPED_QUERY_EXPECTATIONS");summary.setProperty("queries",Long.toString(checked));
            summary.setProperty("scope","Ordered mapped-record reference-span overlap; explicitly excludes placed-unmapped records");save(report,summary);
        }catch(Exception failure){
            summary.setProperty("status","FAIL2_INDEXED_QUERY_EXPECTATIONS_DIFFER");summary.setProperty("queries_before_failure",Long.toString(checked));summary.setProperty("error",String.valueOf(failure.getMessage()));
            if(!Files.exists(report))save(report,summary);throw failure;
        }
    }
    private static void offsets(Path bam,Path output)throws Exception {
        try(var reader=factory().open(bam);var out=Files.newBufferedWriter(output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("name\tflag\treference\tstart\tend\tvirtual_start\tvirtual_end\n");
            for(var record:reader){
                var span=(BAMFileSpan)record.getFileSource().getFilePointer();var chunks=span.getChunks();if(chunks.size()!=1)throw new IOException("Require exact single-record span");
                out.write(record.getReadName()+"\t"+record.getFlags()+"\t"+record.getReferenceIndex()+"\t"+record.getAlignmentStart()+"\t"+record.getAlignmentEnd()+"\t"+Long.toUnsignedString(chunks.get(0).getChunkStart())+"\t"+Long.toUnsignedString(chunks.get(0).getChunkEnd())+"\n");
            }
        }
    }
    public static void main(String[] args){
        try{
            if(args.length==4&&args[0].equals("audit"))audit(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            else if(args.length==4&&args[0].equals("index"))index(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            else if(args.length==6&&args[0].equals("query"))query(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),Path.of(args[4]),Path.of(args[5]));
            else if(args.length==3&&args[0].equals("offsets"))offsets(Path.of(args[1]),Path.of(args[2]));
            else throw new IllegalArgumentException("Use audit BAM BAI REPORT; index BAM NEW_BAI REPORT; query BAM BAI QUERIES_TSV OBSERVED_TSV REPORT; offsets BAM NEW_TSV");
        }catch(Exception failure){System.err.println("FAIL2_BAI_PROBE_NO_ACCEPTED_RESULT: "+failure);System.exit(2);}
    }
}
