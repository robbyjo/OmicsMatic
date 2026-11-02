/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Bounded disk sorting for complete FASTQ populations; successful merge scratch is owned and disposable. */
final class FastpSort {
    private FastpSort() { }
    record Limits(long memoryBytes,int lineCharacters,long records,int maximumParts) {
        Limits {if(memoryBytes<1_048_576||memoryBytes>1_073_741_824L||lineCharacters<32||lineCharacters>100_000_000||records<1||records>10_000_000_000L||maximumParts<2||maximumParts>100_000)throw new IllegalArgumentException("Invalid explicit fastp external-sort capacities");}
    }
    record Sorted(Path path,long records) { }
    private record Entry(String value,int input) { }
    static Sorted sort(Path input,Path newDirectory,Limits limits,FastpProcess.Check budget,Runnable check)throws IOException {
        if(limits.memoryBytes()>Runtime.getRuntime().maxMemory()/4)throw new IOException("fastp external-sort memory exceeds one quarter of current Java heap");
        Path directory=newDirectory.toAbsolutePath().normalize();if(!directory.getParent().toRealPath().equals(directory.getParent()))throw new IOException("fastp sorting requires a canonical NEW directory");Files.createDirectory(directory);
        var parts=new ArrayList<Path>();var rows=new ArrayList<String>();long held=0,count=0,largest=1;int serial=0;
        try(var in=Files.newBufferedReader(input,StandardCharsets.US_ASCII)){
            for(String line=next(in,limits.lineCharacters());line!=null;line=next(in,limits.lineCharacters())){
                if(++count>limits.records())throw new IOException("Complete fastp sorting population exceeds declared record capacity");long cost=Math.addExact(256,8L*line.length());largest=Math.max(largest,cost);if(cost+32768>limits.memoryBytes()/4)throw new IOException("fastp index row exceeds bounded merge memory;raise heap/sort capacity rather than truncating");
                if(held+cost>limits.memoryBytes()){
                    if(parts.size()==limits.maximumParts())throw new IOException("fastp sort-run count exceeds explicit capacity");Path part=directory.resolve("run-"+(serial++)+".tsv");chunk(rows,part,budget,check);parts.add(part);rows.clear();held=0;
                }
                rows.add(line);held+=cost;if((count&1023)==0){check.run();budget.verify();}
            }
        }
        if(!rows.isEmpty()){if(parts.size()==limits.maximumParts())throw new IOException("fastp sort-run count exceeds explicit capacity");Path part=directory.resolve("run-"+(serial++)+".tsv");chunk(rows,part,budget,check);parts.add(part);}
        int fanIn=(int)Math.min(32,limits.memoryBytes()/(largest+32768));
        while(parts.size()>fanIn){var merged=new ArrayList<Path>();for(int from=0;from<parts.size();from+=fanIn){var batch=new ArrayList<>(parts.subList(from,Math.min(from+fanIn,parts.size())));Path target=directory.resolve("merge-"+(serial++)+".tsv");merge(batch,target,limits,budget,check);merged.add(target);for(Path old:batch)remove(directory,old);}parts=merged;}
        Path target=directory.resolve("sorted.tsv");long actual=merge(parts,target,limits,budget,check);if(actual!=count)throw new IOException("fastp external sort changed complete record count");for(Path old:parts)remove(directory,old);budget.verify();return new Sorted(target,count);
    }
    private static void chunk(List<String> rows,Path target,FastpProcess.Check budget,Runnable check)throws IOException {
        check.run();rows.sort(String::compareTo);try(var out=writer(target)){long n=0;for(String row:rows){out.write(row);out.write('\n');if((++n&1023)==0){out.flush();check.run();budget.verify();}}}budget.verify();
    }
    private static long merge(List<Path> parts,Path target,Limits limits,FastpProcess.Check budget,Runnable check)throws IOException {
        var readers=new ArrayList<BufferedReader>();var queue=new PriorityQueue<Entry>(Comparator.comparing(Entry::value).thenComparingInt(Entry::input));long count=0;
        try(var out=writer(target)){
            for(Path part:parts){var in=Files.newBufferedReader(part,StandardCharsets.US_ASCII);readers.add(in);String row=next(in,limits.lineCharacters());if(row!=null)queue.add(new Entry(row,readers.size()-1));}
            while(!queue.isEmpty()){
                var entry=queue.remove();out.write(entry.value());out.write('\n');if(++count>limits.records())throw new IOException("Complete merged fastp population exceeds record capacity");String row=next(readers.get(entry.input()),limits.lineCharacters());if(row!=null)queue.add(new Entry(row,entry.input()));if((count&1023)==0){out.flush();check.run();budget.verify();}
            }
        }finally{IOException failure=null;for(var in:readers)try{in.close();}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}if(failure!=null)throw failure;}
        budget.verify();return count;
    }
    static BufferedWriter writer(Path path)throws IOException {return Files.newBufferedWriter(path,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);}
    static String next(BufferedReader input,int maximum)throws IOException {
        var value=new StringBuilder(Math.min(maximum,256));for(int c=input.read();c>=0;c=input.read()){if(c=='\n')return value.toString();if(c<32&&c!='\t'||c>126)throw new IOException("Invalid character in owned ASCII fastp index");if(value.length()==maximum)throw new IOException("Complete fastp index line exceeds character capacity");value.append((char)c);}return value.isEmpty()?null:value.toString();
    }
    private static void remove(Path directory,Path target)throws IOException {
        if(!target.getParent().equals(directory)||!target.toRealPath().equals(target)||!Files.isRegularFile(target,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Refuse removal of an unexpected fastp merge scratch path");Files.delete(target);
    }
}
