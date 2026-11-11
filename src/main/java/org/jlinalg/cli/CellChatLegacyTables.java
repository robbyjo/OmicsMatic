/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.CellChatLegacyRequest.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Literal input validation and complete quoted-TSV output transport. */
final class CellChatLegacyTables {
    private CellChatLegacyTables() { }
    record Population(List<String> genes,List<String> cells,Map<String,String[]> metadata) { }
    static Population inputs(Path expression,Path metadata,Request request)throws IOException {
        List<String> expectedGroups=names(request.analysis().get("group_order"),2,10000),expectedConditions=names(request.analysis().get("condition_order"),2,2);
        var rows=new LinkedHashMap<String,String[]>();var groupSet=new HashSet<String>();var conditionSet=new HashSet<String>();
        try(var reader=Files.newBufferedReader(metadata,StandardCharsets.UTF_8)) {
            require("cell_id\tgroup\tcondition".equals(reader.readLine()),"Exact metadata header cell_id,group,condition required");
            String line;while((line=reader.readLine())!=null) {
                String[] row=line.split("\t",-1);require(row.length==3&&rows.size()<request.limits().cells(),"Invalid metadata width/cell capacity");
                for(String id:row)identifier(id);require(rows.put(row[0],row)==null,"Duplicate cell identifier");
                groupSet.add(row[1]);conditionSet.add(row[2]);
            }
        }
        require(groupSet.equals(new HashSet<>(expectedGroups))&&conditionSet.equals(new HashSet<>(expectedConditions)),"Declared group/condition order must cover exact observed populations");
        var genes=new ArrayList<String>();var geneSet=new HashSet<String>();var cells=new ArrayList<String>();double max=0;
        try(var reader=Files.newBufferedReader(expression,StandardCharsets.UTF_8)) {
            String first=reader.readLine();require(first!=null,"Empty expression input");String[] header=first.split("\t",-1);require(header.length==rows.size()+1&&header[0].equals("gene_id"),"Expression header/cell population differs");
            cells.addAll(List.of(header).subList(1,header.length));require(cells.equals(new ArrayList<>(rows.keySet())),"Expression and phenotype-derived metadata cell order differ");
            String line;while((line=reader.readLine())!=null) {
                String[] row=line.split("\t",-1);require(row.length==header.length&&genes.size()<request.limits().genes(),"Expression width/gene capacity differs");identifier(row[0]);require(geneSet.add(row[0]),"Duplicate expression gene identifier");genes.add(row[0]);
                for(int i=1;i<row.length;i++) {
                    require(row[i].matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Expression requires explicit nonnegative numeric tokens; no NA/NaN/Inf or repairs");
                    double value;try{value=Double.parseDouble(row[i]);}catch(NumberFormatException bad){throw new IOException("Invalid expression number",bad);}
                    require(Double.isFinite(value)&&value>=0,"Nonfinite/negative expression");max=Math.max(max,value);
                }
            }
        }
        require(genes.size()>=3&&cells.size()>=4&&max>0,"Insufficient or all-zero expression");return new Population(List.copyOf(genes),List.copyOf(cells),Map.copyOf(rows));
    }
    static List<Map<String,String>> read(Path file,int maximumRows)throws IOException {
        var records=new ArrayList<List<String>>();var row=new ArrayList<String>();var field=new StringBuilder();boolean quoted=false,closed=false,started=false;
        try(var reader=new PushbackReader(Files.newBufferedReader(file,StandardCharsets.UTF_8),1)) {
            for(int raw;(raw=reader.read())!=-1;) {
                char c=(char)raw;
                if(quoted) {
                    if(c=='"') {int next=reader.read();if(next=='"')field.append('"');else{quoted=false;closed=true;if(next!=-1)reader.unread(next);}}
                    else field.append(c);
                } else if(c=='"') {require(!started&&!closed&&field.length()==0,"Malformed quoted TSV");quoted=true;started=true;}
                else if(c=='\t'||c=='\n'||c=='\r') {
                    row.add(field.toString());field.setLength(0);started=false;closed=false;
                    if(c!='\t') {
                        if(c=='\r'){int next=reader.read();if(next!='\n'&&next!=-1)reader.unread(next);}
                        records.add(List.copyOf(row));row.clear();require(records.size()<=maximumRows+1,"Output row capacity exceeded");
                    }
                } else {require(!closed,"Characters after closing TSV quote");field.append(c);started=true;}
                require(field.length()<=16777216,"Output field capacity exceeded");
            }
        }
        require(!quoted,"Unclosed TSV quote");if(started||closed||!row.isEmpty()){row.add(field.toString());records.add(List.copyOf(row));}
        require(!records.isEmpty(),"Missing TSV header");List<String> header=records.get(0);require(new HashSet<>(header).size()==header.size(),"Duplicate output column");
        var result=new ArrayList<Map<String,String>>();for(int i=1;i<records.size();i++) {
            require(records.get(i).size()==header.size(),"R output TSV width changed");var value=new LinkedHashMap<String,String>();
            for(int j=0;j<header.size();j++)value.put(header.get(j),records.get(i).get(j));result.add(Map.copyOf(value));
        }
        return result;
    }
    static double finite(String value)throws IOException {
        double result;try{result=Double.parseDouble(value.strip());}catch(RuntimeException bad){throw new IOException("Invalid original numeric transport",bad);}
        require(Double.isFinite(result),"Unexpected nonfinite probability/network transport");return result;
    }
}
