/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;

/** Native source-style fragment splitting before grouped Signac/MACS peak calling. */
public final class SignacPeakPreparation {
    private SignacPeakPreparation() { }
    public record CellGroup(String cell,String group) {
        public CellGroup {SignacFragments.identity(cell);SignacFragments.identity(group);}
    }
    public record Limits(int cells,int groups,int bufferLength,long recordsPerFile,long decodedBytesPerFile,long outputBytes) {
        public Limits {if(cells<1||cells>2_000_000||groups<1||groups>512||bufferLength<32||bufferLength>4096||recordsPerFile<1||decodedBytesPerFile<1||outputBytes<1)throw new IllegalArgumentException("Invalid bounded fragment-splitting limits");}
        public static Limits defaults(){return new Limits(2_000_000,128,256,1_000_000_000L,250_000_000_000L,250_000_000_000L);}
    }
    /** sourceName is the original Linux space/slash substitution; file uses an explicit safe code. */
    public record Group(String sourceName,List<String> originalLabels,Path file,long records) {
        public Group {originalLabels=List.copyOf(originalLabels);}
    }
    public record Result(List<Group> groups,List<SignacFragments.Scan> scans,List<SignacFragments.Warning> warnings,long outputBytes) {
        public Result {groups=List.copyOf(groups);scans=List.copyOf(scans);warnings=List.copyOf(warnings);}
    }
    /**
     * Splits in source/file order, retaining exact selected data-line bytes and
     * line endings. Headers are excluded. The read-support field never weights
     * a record. Source maps are object-cell to file-barcode mappings; a null map
     * explicitly declares identical object-cell and raw-barcode identities.
     * destination must be NEW. Failure preserves its owned partial files.
     */
    public static Result split(List<SignacFragments.Source> sources,List<CellGroup> cells,Path destination,Limits limits,BooleanSupplier cancelled)throws IOException {
        check(cancelled);Objects.requireNonNull(limits);if(sources.isEmpty()||cells.isEmpty()||cells.size()>limits.cells())throw new IllegalArgumentException("Require fragment sources and bounded selected object cells");
        var cellGroups=new LinkedHashMap<String,String>();var groups=new LinkedHashMap<String,LinkedHashSet<String>>();
        for(var row:cells){check(cancelled);if(cellGroups.put(row.cell(),linuxSourceName(row.group()))!=null)throw new IllegalArgumentException("Repeated selected object cell");groups.computeIfAbsent(linuxSourceName(row.group()),k->new LinkedHashSet<>()).add(row.group());}
        if(groups.size()>limits.groups())throw new IllegalArgumentException("Group count exceeds the open-file limit");
        List<String> names=new ArrayList<>(groups.keySet());Map<String,Integer> byName=new HashMap<>();for(int i=0;i<names.size();i++)byName.put(names.get(i),i);
        List<SignacFragments.Warning> warnings=new ArrayList<>();
        for(var e:groups.entrySet()){
            if(e.getValue().stream().anyMatch(s->!s.equals(e.getKey())))warnings.add(new SignacFragments.Warning("SOURCE_GROUP_NAME_SUBSTITUTION","Original Linux Signac replaces spaces/slashes by underscores: "+e.getValue()+" -> "+e.getKey(),"Review the retained original-to-source label map; transport file codes do not rename biological labels."));
            if(e.getValue().size()>1)warnings.add(new SignacFragments.Warning("SOURCE_GROUP_LABEL_COLLISION","Distinct declared groups collapse into one source peak-calling group: "+e.getValue(),"Review this source grouping before analysis. Choose distinct declared groups deliberately if pooling was not intended; do not interpret separate tests for collapsed labels."));
        }
        warnings.add(new SignacFragments.Warning("SPLIT_FRAGMENT_BYTES","Selected fragment records retain their exact bytes, including read support, strand and line endings; records from successive sources are appended in order.","The fifth field does not multiply records. A missing terminal newline can join records across sources and must not be silently repaired. Validate downstream source results."));
        Files.createDirectory(destination);List<OutputStream> outputs=new ArrayList<>();List<Path> paths=new ArrayList<>();List<SignacFragments.Scan> scans=new ArrayList<>();long[] written=new long[names.size()],total={0};
        try {
            for(int i=0;i<names.size();i++){Path path=destination.resolve("g"+(i+1)+".bed");paths.add(path);outputs.add(new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW),65536));}
            for(var source:sources){
                check(cancelled);Map<String,Integer> lookup=new HashMap<>();
                if(source.map()==null){for(var e:cellGroups.entrySet())lookup.put(e.getKey(),byName.get(e.getValue()));}
                else for(var row:source.map()){String group=cellGroups.get(row.cell());if(group!=null)lookup.put(row.barcode(),byName.get(group));}
                long records=0,decoded=0;boolean data=false,compressed;int columns=0;var chromosomes=new LinkedHashSet<String>();
                try(var physical=new BufferedInputStream(Files.newInputStream(source.path()),65536)) {
                    physical.mark(2);int a=physical.read(),b=physical.read();physical.reset();compressed=a==31&&b==139;
                    try(InputStream input=compressed?new BufferedInputStream(new GZIPInputStream(physical,65536),65536):physical) {
                        while(true){
                            check(cancelled);var raw=new ByteArrayOutputStream(128);int value;
                            while((value=input.read())>=0){if(++decoded>limits.decodedBytesPerFile())throw new IOException("Fragment decoded-byte capacity exceeded");if(raw.size()>=limits.bufferLength()-1)throw new IOException("Fragment/header exceeds original split buffer_length-1; no truncated record admitted");raw.write(value);if(value=='\n')break;}
                            if(raw.size()==0)break;byte[] bytes=raw.toByteArray();int length=bytes.length;if(bytes[length-1]=='\n')length--;if(length>0&&bytes[length-1]=='\r')length--;
                            String line=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,0,length)).toString();
                            if(line.startsWith("#")){if(data)throw new IOException("Fragment headers must precede data");continue;}
                            data=true;String[] f=line.split("\t",-1);if(columns==0)columns=f.length;if((columns!=5&&columns!=6)||f.length!=columns)throw new IOException("Require consistently five/six tab-separated fragment fields");
                            SignacFragments.identity(f[0]);SignacFragments.identity(f[3]);int start=number(f[1]),end=number(f[2]);number(f[4]);if(end<=start)throw new IOException("Require nonempty zero-based half-open fragment ranges");if(columns==6&&!Set.of("+","-",".").contains(f[5]))throw new IOException("Invalid explicit fragment strand");
                            chromosomes.add(f[0]);if(++records>limits.recordsPerFile())throw new IOException("Fragment record capacity exceeded");Integer group=lookup.get(f[3]);
                            if(group!=null){if(bytes.length>limits.outputBytes()-total[0])throw new IOException("Split output-byte capacity exceeded");outputs.get(group).write(bytes);total[0]+=bytes.length;written[group]++;}
                        }
                    }
                }
                if(!data)throw new IOException("Original split source has no data records");scans.add(new SignacFragments.Scan(records,decoded,compressed,false,new ArrayList<>(chromosomes)));
            }
        } finally {
            IOException failure=null;for(var output:outputs)try{output.close();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}if(failure!=null)throw failure;
        }
        List<Group> result=new ArrayList<>();for(int i=0;i<names.size();i++)result.add(new Group(names.get(i),new ArrayList<>(groups.get(names.get(i))),paths.get(i),written[i]));
        return new Result(result,scans,warnings,total[0]);
    }
    /** Exact Linux source substitution; collisions are represented explicitly rather than repaired. */
    public static String linuxSourceName(String value){SignacFragments.identity(value);return value.replace(' ','_').replace('/','_');}
    private static int number(String value)throws IOException {try{if(!value.matches("[0-9]+"))throw new NumberFormatException();return Integer.parseInt(value);}catch(NumberFormatException e){throw new IOException("Require nonnegative decimal fragment integers within upstream int range",e);}}
    private static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||cancelled!=null&&cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException("Peak preparation cancelled");}
}
