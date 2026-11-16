/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.jlinalg.alignment.SambambaStreamingBamAudit;

/** Actual small-heap component validation against separately executed original BAMs. */
public final class LargeAudit {
    public static void main(String[] args)throws Exception {
        Path input=Path.of(args[0]),marked=Path.of(args[1]),removed=Path.of(args[2]);String edition=args[3];
        String mark=Files.readString(Path.of(args[4])),remove=Files.readString(Path.of(args[5]));
        long pairs=Long.parseLong(args[6]),heapMb=Long.parseLong(args[7]),maximum=Runtime.getRuntime().maxMemory();
        if(maximum>(heapMb<<20))throw new AssertionError("Requested maximum heap was not enforced: "+maximum);
        var limits=new SambambaStreamingBamAudit.Limits(1L<<40,8L<<40,1L<<40,16<<20,4<<20,4<<20);
        var validated=SambambaStreamingBamAudit.inspectInput(input,limits);
        if(validated.records()!=2*pairs)throw new AssertionError("Complete input count differs");
        long[] observed={0};
        var summary=SambambaStreamingBamAudit.audit(input,marked,removed,edition,mark,remove,limits,d->{
            long index=observed[0]++;boolean best=d.name().equals("large_000000000"),first=index<pairs;
            int expected=(first?99:147)|(best?0:1024);
            if(d.ordinal()!=index||d.markedFlag()!=expected||d.originalFlag()!=(first?99:147)||!d.scoredBySource()
                    ||d.sourceQualityScore()!=(best?400:200)||d.sourceFivePrimeCoordinate()!=(first?999:2009))
                throw new java.io.IOException("Independent full decision/metric expectation differs at "+index);
            if(!d.name().equals(String.format(java.util.Locale.ROOT,"large_%09d",first?index:index-pairs)))
                throw new java.io.IOException("Complete input/marked name or order differs at "+index);
        });
        if(observed[0]!=2*pairs||summary.records()!=2*pairs||summary.markedDuplicates()!=2*(pairs-1)
                ||summary.newlyMarked()!=2*(pairs-1)||summary.cleared()!=0||summary.retainedAfterRemoval()!=2)
            throw new AssertionError("Independent complete count expectation differs: "+summary);
        for(var entry:List.of(java.util.Map.entry(input,summary.input()),java.util.Map.entry(marked,summary.marked()),java.util.Map.entry(removed,summary.removed()))) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var stream=Files.newInputStream(entry.getKey())){byte[] buffer=new byte[65536];for(int n;(n=stream.read(buffer))>=0;)if(n>0)digest.update(buffer,0,n);}
            if(entry.getValue().bytes()!=Files.size(entry.getKey())||!entry.getValue().sha256().equals(HexFormat.of().formatHex(digest.digest())))
                throw new AssertionError("Full actual compressed stream identity differs: "+entry.getKey());
        }
        long compared=SambambaStreamingBamAudit.compareActualRuns(marked,marked,mark,mark,limits);
        if(compared!=2*pairs)throw new AssertionError("Complete self-comparison count differs");
        Properties report=new Properties();report.setProperty("status","PASS_LARGE_ORIGINAL_RECORDS_WITH_EXPLICIT_SMALL_HEAP");
        report.setProperty("edition",edition);report.setProperty("records",Long.toString(summary.records()));report.setProperty("decisions_independently_checked",Long.toString(observed[0]));
        report.setProperty("marked_duplicates",Long.toString(summary.markedDuplicates()));report.setProperty("retained_records","2");
        report.setProperty("maximum_java_heap_bytes",Long.toString(maximum));report.setProperty("requested_heap_mb",Long.toString(heapMb));
        report.setProperty("input_sha256",summary.input().sha256());report.setProperty("marked_sha256",summary.marked().sha256());report.setProperty("removed_sha256",summary.removed().sha256());
        report.setProperty("BAM_comparison_records",Long.toString(compared));report.setProperty("BAI_independently_qualified","false");
        report.setProperty("whole_tool_qualified","false");report.setProperty("new_catalog_credit","0");
        Path out=Path.of(args[8]);try(var stream=Files.newOutputStream(out,java.nio.file.StandardOpenOption.CREATE_NEW)){report.store(stream,null);}
        System.out.println("PASS_LARGE_ORIGINAL_RECORDS edition="+edition+" records="+compared+" maximum_java_heap_bytes="+maximum);
    }
}
