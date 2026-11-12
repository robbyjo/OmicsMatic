/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import htsjdk.samtools.util.SortingCollection;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.SamFlagStatistics;
import static org.jlinalg.cli.FollowupSupport.*;

/** SAM/BAM filtering, sorting, indexing and complete flagstat without a subprocess. */
final class NativeHtsCli {
    private NativeHtsCli() { }
    static int run(Map<String,String> options,PrintStream out,PrintStream err) {
        String method=options.get("method");
        if(!Set.of("sam-view","sam-sort","sam-index","sam-flagstat").contains(method)) {
            err.println("Native HTS supports sam-view, sam-sort, sam-index and sam-flagstat; select --engine upstream explicitly for the other operations.");return 2;
        }
        if(options.containsKey("samtools")||options.containsKey("bcftools")||options.containsKey("threads")||options.containsKey("memory-mb")) {
            err.println("Native HTS does not accept external executables, --threads or --memory-mb. Sorting uses explicit --max-records-in-ram (not an OS byte quota).");return 2;
        }
        return NativeToolSupport.run(options,"native-hts-"+method,(directory,manifest)->{
            Path input=Path.of(required(options,"input")).toAbsolutePath().normalize();
            if(!Files.isRegularFile(input)||!input.toString().toLowerCase(Locale.ROOT).matches(".*\\.(sam|bam)$"))throw new IOException("Require a local .sam or .bam input; no CRAM/remote input");
            String before=hash(input);long deadline=System.nanoTime()+integer(options,"timeout",3600,1,86400)*1000000000L;
            long count=0,retained=0;var stats=new SamFlagStatistics();
            Path bam=directory.resolve("alignment.bam");
            if(method.equals("sam-index")) {
                if(!input.toString().toLowerCase(Locale.ROOT).endsWith(".bam"))throw new IOException("Indexing requires BAM input");
                count=checkCoordinate(input,deadline);index(input,directory.resolve("alignment.bai"));retained=count;
            }else try(var reader=factory().open(input)) {
                if(method.equals("sam-flagstat")) {
                    for(var record:reader){check(deadline);stats.add(record);count++;}
                    Files.writeString(directory.resolve("flagstat.json"),NextflowJson.json(stats.groups())+"\n");retained=count;
                }else if(method.equals("sam-view")) {
                    int mapq=integer(options,"min-mapq",0,0,255),required=integer(options,"require-flags",0,0,4095),excluded=integer(options,"exclude-flags",0,0,4095);
                    var region=options.containsKey("region")?region(options.get("region"),reader.getFileHeader()):null;
                    try(var writer=new SAMFileWriterFactory().setUseAsyncIo(false).makeBAMWriter(reader.getFileHeader(),true,bam)) {
                        for(var record:reader) {
                            check(deadline);count++;
                            if(record.getMappingQuality()<mapq||(record.getFlags()&required)!=required||(record.getFlags()&excluded)!=0)continue;
                            if(region!=null&&(record.getReadUnmappedFlag()||!record.getReferenceName().equals(region.contig())||record.getAlignmentStart()>region.end()||record.getAlignmentEnd()<region.start()))continue;
                            writer.addAlignment(record);retained++;
                        }
                    }
                }else {
                    String order=options.getOrDefault("sort-order","coordinate");
                    if(!Set.of("coordinate","queryname").contains(order))throw new IOException("sort-order must be coordinate or queryname");
                    boolean createIndex=bool(options,"create-index",false);
                    if(createIndex&&!order.equals("coordinate"))throw new IOException("BAM indexing requires coordinate order");
                    var header=reader.getFileHeader().clone();header.setSortOrder(SAMFileHeader.SortOrder.valueOf(order));header.setGroupOrder(SAMFileHeader.GroupOrder.none);
                    header.setAttribute("SS",order.equals("queryname")?"queryname:lexicographical":null);
                    Comparator<SAMRecord> comparator=order.equals("coordinate")?new SAMRecordCoordinateComparator():Comparator.comparing(SAMRecord::getReadName);
                    Path tmp=Files.createDirectory(directory.resolve("sort-tmp"));
                    var collection=SortingCollection.newInstance(SAMRecord.class,new BAMRecordCodec(header),comparator,integer(options,"max-records-in-ram",100000,1,10000000),tmp);
                    try {
                        for(var record:reader){check(deadline);collection.add(record);count++;}
                        collection.doneAdding();check(deadline);
                        try(var records=collection.iterator();var writer=new SAMFileWriterFactory().setUseAsyncIo(false).makeBAMWriter(header,true,bam)) {
                            while(records.hasNext()){check(deadline);writer.addAlignment(records.next());retained++;}
                        }
                    }finally{collection.cleanup();}
                    Files.delete(tmp);
                    if(createIndex){checkCoordinate(bam,deadline);index(bam,directory.resolve("alignment.bai"));}
                }
            }
            check(deadline);if(!before.equals(hash(input)))throw new IOException("Input changed during native HTS operation");
            manifest.put("input_sha256",Map.of("input",before));manifest.put("input_records",count);manifest.put("output_records",retained);
            manifest.put("scope","HTSJDK5.0.0 Java SAM/BAM I/O; lexical read-name or coordinate sorting; BAI indexing; SAMtools1.24 flag categories. BAM compression, header serialization and equal-key sorting are not byte-identical SAMtools outputs. Region filtering streams the full input and requires no source index.");
            table(directory.resolve("results.tsv"),List.of("metric","value"),List.of(new String[]{"input_records",Long.toString(count)},new String[]{"output_records",Long.toString(retained)}));
            Files.writeString(directory.resolve("WARNINGS.md"),manifest.get("scope")+"\nSorting capacity is a record count, not a hard RAM quota. Empty selections are not evidence of biological absence.\n");
        },out,err);
    }
    private record Region(String contig,int start,int end) { }
    private static Region region(String text,SAMFileHeader header)throws IOException {
        var matcher=java.util.regex.Pattern.compile("([^:\\s]+):([0-9]+)-([0-9]+)").matcher(text);
        if(!matcher.matches())throw new IOException("Region requires exact CHR:START-END, one-based inclusive");
        int start=Integer.parseInt(matcher.group(2)),end=Integer.parseInt(matcher.group(3));var sequence=header.getSequence(matcher.group(1));
        if(start<1||end<start||sequence==null||end>sequence.getSequenceLength())throw new IOException("Region is absent from/outside the input reference dictionary");
        return new Region(sequence.getSequenceName(),start,end);
    }
    private static SamReaderFactory factory() {
        return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
            .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS,SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX).disable(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES);
    }
    private static void check(long deadline)throws IOException {NativeToolSupport.check();if(System.nanoTime()-deadline>=0)throw new IOException("Native HTS timeout exceeded");}
    private static long checkCoordinate(Path bam,long deadline)throws IOException {
        try(var reader=factory().open(bam)) {
            if(reader.getFileHeader().getSortOrder()!=SAMFileHeader.SortOrder.coordinate)throw new IOException("BAM index requires declared coordinate order");
            var comparator=new SAMRecordCoordinateComparator();SAMRecord prior=null;long count=0;
            for(var record:reader){check(deadline);if(prior!=null&&comparator.fileOrderCompare(prior,record)>0)throw new IOException("BAM records are not actually coordinate sorted");prior=record;count++;}
            return count;
        }
    }
    private static void index(Path bam,Path index)throws IOException {
        try(var reader=factory().open(bam)){BAMIndexer.createIndex(reader,index);}
        try(var reader=factory().open(SamInputResource.of(bam).index(index))){if(!reader.hasIndex())throw new IOException("Native BAI output is unreadable");}
    }
}
