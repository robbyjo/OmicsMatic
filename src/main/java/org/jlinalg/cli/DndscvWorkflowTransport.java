/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete typed17-digit tables and lossless IEEE arrays; each TSV cell is checked against JSON. */
final class DndscvWorkflowTransport {
    private DndscvWorkflowTransport() { }
    record Table(List<String> columns,List<String> rowNames,List<List<String>> values,List<List<String>> kinds){
        int rows(){return values.size();}int column(String name)throws IOException{int i=columns.indexOf(name);require(i>=0,"Missing original table column: "+name);return i;}
        String cell(int row,String name)throws IOException{return values.get(row).get(column(name));}
        String kind(int row,String name)throws IOException{return kinds.get(row).get(column(name));}
        List<String> strings(String name)throws IOException{int c=column(name);return values.stream().map(row->row.get(c)).toList();}
    }
    static Map<String,Object> read(Path path,DndscvWorkflowRequest.Request r)throws IOException{return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(path,r.jsonBytes())),r.jsonBytes(),r.jsonNodes()));}
    static Table table(Path path,DndscvWorkflowRequest.Request r,DndscvWorkflowInputs.Budget meter)throws IOException{
        var root=read(path,r);int count=(int)integer(root.get("rows"),0,r.matrixEntries());List<String> columns=strings(root.get("column_names"),10000);require(new HashSet<>(columns).size()==columns.size(),"Duplicate original table column");require((long)count*columns.size()<=r.matrixEntries(),"Complete original table exceeds maximum_matrix_entries");meter.add((long)count*columns.size());var names=new ArrayList<String>();var values=new ArrayList<List<String>>();var kinds=new ArrayList<List<String>>();var descriptors=new ArrayList<Map<String,Object>>();
        for(String part:strings(root.get("parts"),r.outputFiles())){
            require(!part.contains("/")&&!part.contains("\\")&&!part.equals(".")&&!part.equals(".."),"Table part must be a literal owned filename");var data=read(path.getParent().resolve(part),r);int n=(int)integer(data.get("rows"),0,r.chunkRows());var rawColumns=list(data.get("columns"));require(rawColumns.size()==columns.size(),"Original chunk column width differs");var rows=strings(data.get("row_names"),r.chunkRows());require(rows.size()==n,"Original row-name population differs");var tokens=new ArrayList<List<String>>();var flags=new ArrayList<List<String>>();
            for(int c=0;c<columns.size();c++){
                var column=object(rawColumns.get(c));require(text(column.get("name")).equals(columns.get(c)),"Original chunk column order differs");var meta=new LinkedHashMap<String,Object>();for(String key:List.of("name","class","type","levels","original_dim","original_dimnames"))meta.put(key,column.get(key));if(column.get("original_dim")!=null){var shape=list(column.get("original_dim"));require(shape.size()==1&&integer(shape.get(0),0,r.matrixEntries())==count,"Original array column must have one element per complete table row");if(column.get("original_dimnames")!=null){var dimensionNames=list(column.get("original_dimnames"));require(dimensionNames.size()==1,"Original column dimension names differ");if(dimensionNames.get(0)!=null)require(rawStrings(dimensionNames.get(0),count).size()==count,"Incomplete original column dimension names");}}else require(column.get("original_dimnames")==null,"Original column names require declared dimensions");if(descriptors.size()<columns.size())descriptors.add(meta);else require(meta.equals(descriptors.get(c)),"Original chunk column type/levels/dimensions differ");var tt=rawStrings(column.get("tokens"),n);var kk=rawStrings(column.get("kinds"),n);for(int i=0;i<n;i++){String k=kk.get(i);require(Set.of("value","NA","NaN","positive-infinity","negative-infinity").contains(k),"Unknown original token kind");if(!k.equals("value"))require(tt.get(i).equals(switch(k){case "positive-infinity"->"Inf";case "negative-infinity"->"-Inf";default->k;}),"Original missing/nonfinite token was recoded");}tokens.add(tt);flags.add(kk);
            }
            for(int i=0;i<n;i++){var row=new ArrayList<String>();var flag=new ArrayList<String>();for(int c=0;c<columns.size();c++){row.add(tokens.get(c).get(i));flag.add(flags.get(c).get(i));}values.add(List.copyOf(row));kinds.add(List.copyOf(flag));}names.addAll(rows);require(values.size()<=count,"Duplicate/excessive original table parts");
        }
        require(values.size()==count&&names.size()==count,"Incomplete original table population");String basename=path.getFileName().toString();require(basename.endsWith(".json"),"Expected original JSON table");Path tsv=path.resolveSibling(basename.substring(0,basename.length()-5)+".tsv");require(Files.size(tsv)<=r.outputBytes(),"Original TSV exceeds output capacity");
        try(var stream=Files.newInputStream(tsv,LinkOption.NOFOLLOW_LINKS);var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))){
            var expected=new ArrayList<String>();expected.add("__original_R_row_name__");expected.addAll(columns);require(String.join("\t",expected).equals(reader.readLine()),"Original TSV/JSON header mismatch: "+basename);for(int i=0;i<count;i++){String line=reader.readLine();require(line!=null&&line.length()<=r.jsonBytes(),"Incomplete/oversized original TSV row");String[] row=line.split("\t",-1);require(row.length==columns.size()+1&&row[0].equals(names.get(i)),"Original TSV row names differ");for(int c=0;c<columns.size();c++)require(row[c+1].equals(values.get(i).get(c)),"Original TSV/JSON cell mismatch: "+basename+" row"+i+" "+columns.get(c));}require(reader.readLine()==null,"Extra original TSV rows");
        }
        require(Files.isRegularFile(path.resolveSibling(basename.substring(0,basename.length()-5)+".rds"),LinkOption.NOFOLLOW_LINKS),"Original complete RDS table absent");return new Table(columns,List.copyOf(names),List.copyOf(values),List.copyOf(kinds));
    }
    static DataInputStream doubles(Path path,DndscvWorkflowRequest.Request r)throws IOException{require(Files.size(path)<=r.outputBytes(),"Original IEEE array exceeds output capacity");return new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS))));}
    static double number(Table table,int row,String column)throws IOException{require(table.kind(row,column).equals("value"),"Required original numerical identity is nonfinite: "+column);double n;try{n=Double.parseDouble(table.cell(row,column));}catch(NumberFormatException e){throw new IOException("Malformed original numeric token",e);}require(Double.isFinite(n),"Nonfinite required original numerical identity");return n;}
    private static List<String> rawStrings(Object value,int n)throws IOException{var result=new ArrayList<String>();for(Object raw:list(value)){String s=text(raw);require(s.length()<=32768&&s.indexOf('\0')<0&&s.indexOf('\t')<0&&s.indexOf('\r')<0&&s.indexOf('\n')<0,"Invalid literal original table token");result.add(s);}require(result.size()==n,"Incomplete original token population");return List.copyOf(result);}
}
