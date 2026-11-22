/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded external joins for complete generated candidate populations. */
final class HomerGeneratedSort {
    private static final int FAN_IN=32;
    record Sorted(Path path,long records) { }
    private record Row(String text,int input) { }
    private static final class Readers implements AutoCloseable {
        final List<BufferedReader> values=new ArrayList<>();
        @Override public void close()throws IOException{
            IOException failure=null;for(var reader:values)try{reader.close();}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}if(failure!=null)throw failure;
        }
    }
    private final Path directory;
    private final HomerCoreData.Budget budget;
    private final long memory,recordLimit;
    private final int lineLimit;
    private final HomerRuntime.Verify check;
    private final Set<String> used=new HashSet<>();
    private int serial;
    HomerGeneratedSort(Path directory,HomerCoreData.Budget budget,long memory,int lineLimit,long recordLimit,HomerRuntime.Verify check)throws IOException{
        if(memory<1048576||memory>Runtime.getRuntime().maxMemory()/4||lineLimit<256||recordLimit<1)throw new IOException("Generated sort memory/line/record capacity invalid;review explicit heap and sort-memory-bytes");
        this.directory=directory.toAbsolutePath().normalize();this.budget=budget;this.memory=memory;this.lineLimit=lineLimit;this.recordLimit=recordLimit;this.check=check;Files.createDirectory(this.directory);
    }
    Sorted sort(Path input,String stem)throws IOException{
        if(!stem.matches("[a-z][a-z0-9-]{0,40}")||!used.add(stem))throw new IOException("Invalid or repeated generated sort identity");
        var parts=new ArrayList<Path>();var rows=new ArrayList<String>();long held=0,count=0;
        try(var reader=HomerCoreAudit.reader(input)){
            for(String line;(line=HomerCoreAudit.next(reader,lineLimit))!=null;){
                if(line.indexOf('\t')<1)throw new IOException("Generated join row lacks an identity/data delimiter");
                if(++count>recordLimit)throw new IOException("Generated candidate join record capacity exceeded");long cost=Math.addExact(160,2L*line.length());
                if(cost>memory)throw new IOException("Generated join row exceeds sort memory;review explicit line/heap/sort capacities");
                if(held+cost>memory){parts.add(chunk(rows,stem));rows.clear();held=0;}
                rows.add(line);held+=cost;if((count&1023)==0)check.run();
            }
        }
        if(!rows.isEmpty())parts.add(chunk(rows,stem));
        while(parts.size()>FAN_IN){
            var next=new ArrayList<Path>();for(int i=0;i<parts.size();i+=FAN_IN){var group=new ArrayList<>(parts.subList(i,Math.min(parts.size(),i+FAN_IN)));Path target=part(stem);merge(group,target,false);next.add(target);for(Path path:group)Files.delete(path);}parts=next;
        }
        Path target=directory.resolve(stem+".sorted.tsv");long merged=merge(parts,target,true);
        if(count!=merged)throw new IOException("Generated join sort changed complete row count");for(Path path:parts)Files.delete(path);check.run();return new Sorted(target,count);
    }
    private Path part(String stem){return directory.resolve(stem+".part-"+(serial++)+".tsv");}
    private Path chunk(List<String> rows,String stem)throws IOException{
        check.run();rows.sort(String::compareTo);Path path=part(stem);try(var writer=budget.writer(path)){for(String row:rows){writer.write(row);writer.write('\n');}}return path;
    }
    private long merge(List<Path> paths,Path target,boolean unique)throws IOException{
        check.run();long count=0;String previous=null;
        var queue=new PriorityQueue<Row>(Comparator.comparing(Row::text).thenComparingInt(Row::input));
        try(var readers=new Readers();var writer=budget.writer(target)){
            for(Path path:paths){var reader=HomerCoreAudit.reader(path);readers.values.add(reader);String line=HomerCoreAudit.next(reader,lineLimit);if(line!=null)queue.add(new Row(line,readers.values.size()-1));}
            while(!queue.isEmpty()){
                Row row=queue.remove();int tab=row.text.indexOf('\t');if(tab<1)throw new IOException("Malformed generated sorted identity");String identity=row.text.substring(0,tab);
                if(unique&&identity.equals(previous))throw new IOException("Duplicate generated candidate identity: "+identity);previous=identity;writer.write(row.text);writer.write('\n');count++;
                String next=HomerCoreAudit.next(readers.values.get(row.input),lineLimit);if(next!=null)queue.add(new Row(next,row.input));if((count&1023)==0)check.run();
            }
        }
        return count;
    }
}
