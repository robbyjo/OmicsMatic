/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete chunked original transports with literal TSV/Matrix Market coherence checks. */
final class SoupXWorkflowTransport {
    private SoupXWorkflowTransport() { }
    static final class Meter {
        final long maximum;long used;
        Meter(long maximum){this.maximum=maximum;}
        void spend(long n)throws IOException{require(n>=0&&n<=maximum-used,"SoupX native audit operation capacity exceeded");require(!Thread.currentThread().isInterrupted(),"SoupX audit interrupted");used+=n;}
    }
    record Column(String name,String type,List<String> classes,List<String> levels,List<String> tokens,List<String> kinds){ }
    record Table(int rows,Map<String,Column> columns){
        List<String> tokens(String name)throws IOException{Column c=columns.get(name);require(c!=null,"Missing original table column: "+name);return c.tokens;}
        double number(String name,int row)throws IOException{Column c=columns.get(name);require(c!=null&&Set.of("double","integer").contains(c.type)&&c.kinds.get(row).equals("value"),"Missing/nonnumeric/nonfinite original field: "+name);return SoupXWorkflowTransport.number(c.tokens.get(row));}
        boolean logical(String name,int row)throws IOException{Column c=columns.get(name);require(c!=null&&c.type.equals("logical")&&c.kinds.get(row).equals("value"),"Missing/nonlogical original field: "+name);String value=c.tokens.get(row);require(value.equals("TRUE")||value.equals("FALSE"),"Invalid original logical value");return value.equals("TRUE");}
    }
    record Sparse(List<String> rows,List<String> columns,Map<Long,Double> values){double get(int row,int column){return values.getOrDefault((long)row*columns.size()+column,0.0);}}
    static Map<String,Object> read(Path path,SoupXWorkflowRequest.Request r)throws IOException{return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(path,r.jsonBytes())),r.jsonBytes(),r.jsonNodes()));}
    static Table table(Path path,SoupXWorkflowRequest.Request r,Meter meter)throws IOException{return table(path,r,meter,true);}
    private static Table table(Path path,SoupXWorkflowRequest.Request r,Meter meter,boolean allowParts)throws IOException{
        var m=read(path,r);int rows=(int)integer(m.get("rows"),0,r.intermediateEntries());var columns=new LinkedHashMap<String,Column>();
        if(m.containsKey("parts")){
            require(allowParts,"Nested original table chunks refused");var parts=strings(m.get("parts"),r.outputFiles());require(!parts.isEmpty()&&new HashSet<>(parts).size()==parts.size(),"Invalid/duplicate table chunks");long total=0;
            for(String part:parts){Table chunk=table(member(path.getParent(),part),r,meter,false);require(chunk.rows>0&&chunk.rows<=r.chunkEntries(),"Chunk rows outside transport capacity");total+=chunk.rows;require(total<=rows,"Excess original table rows");if(columns.isEmpty())for(Column c:chunk.columns.values())columns.put(c.name,new Column(c.name,c.type,c.classes,c.levels,new ArrayList<>(),new ArrayList<>()));require(columns.keySet().equals(chunk.columns.keySet()),"Original chunk columns differ");for(var e:columns.entrySet()){Column a=e.getValue(),b=chunk.columns.get(e.getKey());require(a.type.equals(b.type)&&a.classes.equals(b.classes)&&Objects.equals(a.levels,b.levels),"Original chunk type/class/levels differ");a.tokens.addAll(b.tokens);a.kinds.addAll(b.kinds);}}
            require(total==rows,"Incomplete original table chunks");
        }else{
            for(Object raw:list(m.get("columns"))){var c=object(raw);String name=label(c.get("name")),type=text(c.get("type"));require(Set.of("double","integer","logical","character").contains(type),"Unsupported original atomic type");var values=strings(c.get("tokens"),r.intermediateEntries());var kinds=strings(c.get("kinds"),r.intermediateEntries());require(values.size()==rows&&kinds.size()==rows,"Ragged original atomic column");for(int i=0;i<rows;i++){meter.spend(1);String k=kinds.get(i);require(Set.of("value","NA","NaN","positive-infinity","negative-infinity").contains(k),"Unknown original value kind");if(!k.equals("value"))require(values.get(i).equals(Map.of("NA","NA","NaN","NaN","positive-infinity","Inf","negative-infinity","-Inf").get(k)),"Original kind/token disagree");}Column column=new Column(name,type,strings(c.get("class"),64),c.get("levels")==null?null:strings(c.get("levels"),r.intermediateEntries()),values,kinds);require(columns.putIfAbsent(name,column)==null,"Duplicate original table column");}
        }
        require(!columns.isEmpty()&&(long)rows*columns.size()<=r.intermediateEntries(),"Complete original table cells exceed maximum_intermediate_entries");var result=new Table(rows,Collections.unmodifiableMap(columns));Path tsv=path.resolveSibling(path.getFileName().toString().replaceFirst("[.]json$",".tsv"));require(Files.isRegularFile(tsv,LinkOption.NOFOLLOW_LINKS),"Missing original table TSV");
        try(var reader=Files.newBufferedReader(tsv,StandardCharsets.UTF_8)){require(Objects.equals(reader.readLine(),String.join("\t",columns.keySet())),"Original TSV/JSON header differs: "+path.getFileName());for(int i=0;i<rows;i++){String line=reader.readLine();require(line!=null,"Incomplete original table TSV: "+path.getFileName());String[] fields=line.split("\t",-1);require(fields.length==columns.size(),"Original TSV width differs: "+path.getFileName());int j=0;for(Column c:columns.values()){meter.spend(1);require(fields[j++].equals(c.tokens.get(i)),"Original TSV/JSON cell differs: "+path.getFileName()+" row="+(i+1)+" column="+c.name);}}require(reader.readLine()==null,"Excess original table TSV rows: "+path.getFileName());}
        return result;
    }
    static Sparse sparse(Path path,SoupXWorkflowRequest.Request r,Meter meter)throws IOException{
        var m=read(path,r);var rows=strings(m.get("row_ids"),r.maximumGenes());var columns=strings(m.get("column_ids"),Math.max(r.maximumCells(),r.maximumDroplets()));require(!rows.isEmpty()&&!columns.isEmpty()&&new HashSet<>(rows).size()==rows.size()&&new HashSet<>(columns).size()==columns.size(),"Invalid original sparse axes");long count=integer(m.get("entry_count"),0,r.intermediateEntries());var parts=strings(m.get("entry_parts"),r.outputFiles());require(new HashSet<>(parts).size()==parts.size(),"Duplicate sparse chunks");var entries=new LinkedHashMap<Long,Double>();var ordered=new ArrayList<String>();
        for(String part:parts){var chunk=read(member(path.getParent(),part),r);var values=list(chunk.get("entries"));require(!values.isEmpty()&&values.size()<=r.chunkEntries(),"Sparse chunk outside capacity");for(Object raw:values){meter.spend(1);var e=object(raw);int i=(int)integer(e.get("row"),1,rows.size())-1,j=(int)integer(e.get("column"),1,columns.size())-1;String token=text(e.get("value"));require(text(e.get("kind")).equals("value"),"Original nonfinite corrected/count value retained but not admitted");double value=number(token);require(entries.size()<count&&entries.putIfAbsent((long)i*columns.size()+j,value)==null,"Excess/duplicate original sparse coordinate");ordered.add((i+1)+" "+(j+1)+" "+token);}}
        require(entries.size()==count,"Incomplete original sparse entry population");Path mtx=path.resolveSibling(path.getFileName().toString().replaceFirst("[.]json$",".mtx"));require(Files.isRegularFile(mtx,LinkOption.NOFOLLOW_LINKS),"Missing original Matrix Market output");try(var reader=Files.newBufferedReader(mtx,StandardCharsets.UTF_8)){require(Objects.equals(reader.readLine(),"%%MatrixMarket matrix coordinate real general")&&Objects.equals(reader.readLine(),rows.size()+" "+columns.size()+" "+count),"Original Matrix Market header/dimensions differ");for(String line:ordered){meter.spend(1);require(Objects.equals(reader.readLine(),line),"Original Matrix Market/JSON coordinate differs");}require(reader.readLine()==null,"Excess Matrix Market output");}return new Sparse(rows,columns,Collections.unmodifiableMap(entries));
    }
    static Path member(Path parent,String name)throws IOException{NextflowFiles.member(name);require(name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,255}"),"Nonlocal original transport member");Path path=parent.resolve(name);require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular original transport member");return path;}
    static double number(String token)throws IOException{try{double value=Double.parseDouble(token);require(Double.isFinite(value),"Nonfinite original numeric token");return value;}catch(NumberFormatException e){throw new IOException("Invalid original numeric token",e);}}
    static void near(double value,double expected,String cause)throws IOException{require(Double.isFinite(value)&&Double.isFinite(expected)&&Math.abs(value-expected)<=Math.max(128*Math.ulp(expected),5e-11*Math.max(Math.abs(expected),1e-300)),cause+": original="+value+", native="+expected);}
}
