/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.jlinalg.alignment.BamBaiAudit;
import org.jlinalg.alignment.SambambaStreamingBamAudit;

/** All-row checks against the independent four-million-record generator's exact pattern. */
public final class LargeIndexProbe {
    private static SamReaderFactory factory(){return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
        .enable(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX).disable(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES);}
    private static void check(SAMRecord record,long ordinal,boolean first,String kind)throws IOException {
        String digits=Long.toString(ordinal),name="large_"+"000000000".substring(digits.length())+digits;
        int flags=(first?99:147)+(kind.equals("mark")&&ordinal>0?1024:0),position=first?1000:2000;
        if(!record.getReadName().equals(name)||record.getFlags()!=flags||!record.getReferenceName().equals("chr1")
            ||record.getAlignmentStart()!=position||record.getAlignmentEnd()!=position+9||!record.getCigarString().equals("10M")
            ||!record.getReadString().equals("AAAAAAAAAA"))throw new IOException("Exact independent large-record expectation differs: ordinal="+ordinal+", first="+first);
        byte[] qualities=record.getBaseQualities();if(qualities.length!=10)throw new IOException("Quality length differs");
        for(byte quality:qualities)if(quality!=(ordinal==0?40:20))throw new IOException("Independent quality pattern differs");
    }
    public static void main(String[] args){
        try{
            if(args.length!=5)throw new IllegalArgumentException("Use BAM BAI PAIRS input|mark|remove NEW_REPORT");
            Path bam=Path.of(args[0]),bai=Path.of(args[1]),output=Path.of(args[4]);long pairs=Long.parseLong(args[2]);String kind=args[3];
            if(pairs<1||pairs>20_000_000||!java.util.Set.of("input","mark","remove").contains(kind))throw new IllegalArgumentException("Invalid independent generator parameters");
            long heap=Runtime.getRuntime().maxMemory();if(heap>64L<<20)throw new IllegalArgumentException("This control requires an actual Java heap no larger than64MiB");
            var summary=BamBaiAudit.audit(bam,bai,new SambambaStreamingBamAudit.Limits(1L<<40,8L<<40,1L<<40,16<<20,4<<20,4<<20),new BamBaiAudit.Limits(64L<<20,1_000_000,1_000_000,600_000));
            long count=kind.equals("remove")?1:pairs;
            if(summary.records()!=2*count||summary.placedMapped()!=2*count||summary.unplaced()!=0||summary.placedUnmapped()!=0)throw new IOException("Complete audited large-record counts differ");
            long sequential=0,indexed=0;
            try(var reader=factory().open(bam)){
                for(var record:reader){if(sequential>=2*count)throw new IOException("Extra sequential record");check(record,sequential%count,sequential<count,kind);sequential++;}
            }
            if(sequential!=2*count)throw new IOException("Missing sequential record");
            try(var reader=factory().open(SamInputResource.of(bam).index(bai))){
                for(boolean first:new boolean[]{true,false}){
                    long seen=0;int position=first?1005:2005;
                    try(var records=reader.queryOverlapping("chr1",position,position)){
                        while(records.hasNext()){
                            if(seen>=count)throw new IOException("Extra indexed record");check(records.next(),seen++,first,kind);indexed++;
                        }
                    }
                    if(seen!=count)throw new IOException("Missing indexed record at "+position+": "+seen+" of "+count);
                }
                try(var records=reader.queryOverlapping("chr1",1500,1500)){if(records.hasNext())throw new IOException("Nonempty independent empty-region expectation");}
            }
            var report=new Properties();report.setProperty("status","PASS_LARGE_BAI_AUDIT_SEQUENTIAL_AND_INDEXED_EXACT_EXPECTATIONS");
            report.setProperty("maximum_java_heap_bytes",Long.toString(heap));report.setProperty("audited_records",Long.toString(summary.records()));
            report.setProperty("sequential_records_checked",Long.toString(sequential));report.setProperty("indexed_records_checked",Long.toString(indexed));
            report.setProperty("region_queries","3");report.setProperty("kind",kind);report.setProperty("bam_bytes",Long.toString(summary.bam().bytes()));
            report.setProperty("bam_sha256",summary.bam().sha256());report.setProperty("bai_bytes",Long.toString(summary.bai().bytes()));report.setProperty("bai_sha256",summary.bai().sha256());
            try(var out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)){report.store(out,"All records checked; no sampling or scientific repair");}
        }catch(Exception failure){System.err.println("FAIL2_LARGE_BAI_OR_QUERY_EXPECTATIONS_UNVERIFIED: "+failure);System.exit(2);}
    }
}
