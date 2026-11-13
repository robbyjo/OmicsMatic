/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Validate every signed prepared value without retaining an unnecessary dense/CSR copy. */
final class FastMnn129Inputs {
    private FastMnn129Inputs() { }
    private static final Pattern NUMBER=Pattern.compile("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?");
    record Data(List<String> features,List<String> cells,List<String> batches,long nonzeroOrCoordinates) { }
    static Data read(Path matrix,Path cellFile,Path featureFile,String format,String units,List<String> levels,int maximumEntries)throws IOException {
        require(Set.of("prepared-log-expression","prepared-asinh-protein").contains(units),"Declare prepared log-expression or signed asinh-protein units");
        var features=new ArrayList<String>();try(var r=reader(featureFile)){
            require(Arrays.equals(fields(r.readLine()),new String[]{"feature_id","feature_name"}),"Require feature_id and feature_name header");String s;
            while((s=r.readLine())!=null){var f=fields(s);require(f.length==2,"Ragged feature table");label(f[0]);require(f[1].length()<=32768&&f[1].chars().noneMatch(c->c<32||c==127),"Invalid literal feature_name");features.add(f[0]);require(features.size()<=1000000,"Complete feature-axis capacity exceeded");}
        }
        unique(features,"feature");var cells=new ArrayList<String>();var batches=new ArrayList<String>();var batchIds=new HashMap<String,String>();for(String level:levels)batchIds.put(level,level);
        try(var r=reader(cellFile)){
            require(Arrays.equals(fields(r.readLine()),new String[]{"cell_id","batch"}),"Require exact cell_id/batch metadata header");String s;
            while((s=r.readLine())!=null){var f=fields(s);require(f.length==2,"Ragged complete cell metadata");label(f[0]);require(batchIds.containsKey(f[1]),"Undeclared literal batch label");cells.add(f[0]);batches.add(batchIds.get(f[1]));require(cells.size()<=1000000,"Complete cell-axis capacity exceeded");}
        }
        unique(cells,"cell");require(cells.size()>=2&&new HashSet<>(batches).equals(batchIds.keySet()),"Require all declared nonempty batches and complete cells");
        long entries=0;
        try(var r=reader(matrix)){
            if(format.equals("TSV")){
                var header=fields(r.readLine());require(header.length==cells.size()+1&&header[0].equals("feature_id")&&Arrays.asList(header).subList(1,header.length).equals(cells),"Prepared matrix cell axis differs from complete metadata");String s;int row=0;
                while((s=r.readLine())!=null){var f=fields(s);require(row<features.size()&&f.length==cells.size()+1&&f[0].equals(features.get(row)),"Prepared matrix feature axis or full row width differs");for(int j=1;j<f.length;j++){double value=number(f[j]);if(value!=0){entries++;require(entries<=maximumEntries,"Complete nonzero-entry capacity exceeded; no prefix accepted");}}row++;}
                require(row==features.size(),"Incomplete prepared feature population");
            }else{
                require(format.equals("MATRIX_MARKET"),"Require TSV or MATRIX_MARKET");String header=r.readLine();require("%%MatrixMarket matrix coordinate real general".equals(header)||"%%MatrixMarket matrix coordinate integer general".equals(header),"Require real/integer general coordinate Matrix Market");String s;
                do{s=r.readLine();require(s!=null,"Missing Matrix Market dimensions");}while(s.isBlank()||s.stripLeading().startsWith("%"));
                var dimensions=s.strip().split("\\s+");require(dimensions.length==3&&index(dimensions[0],1000000)==features.size()&&index(dimensions[1],1000000)==cells.size(),"Matrix Market dimensions differ from complete axes");int count=index(dimensions[2],maximumEntries);var coordinates=new long[count];int seen=0;
                while((s=r.readLine())!=null){if(s.isBlank()||s.stripLeading().startsWith("%"))continue;var f=s.strip().split("\\s+");require(f.length==3&&seen<count,"Ragged/excess Matrix Market entry");int row=index(f[0],features.size())-1,column=index(f[1],cells.size())-1;require(row>=0&&column>=0,"Matrix Market indices must be one-based");if(header.endsWith("integer general"))require(f[2].matches("[+-]?[0-9]+"),"Integer coordinates require literal integer tokens");number(f[2]);coordinates[seen++]=(long)row*cells.size()+column;}
                require(seen==count,"Incomplete Matrix Market coordinate population");Arrays.sort(coordinates);for(int i=1;i<count;i++)require(coordinates[i]!=coordinates[i-1],"Duplicate Matrix Market coordinate; no implicit summing or repair");entries=count;
            }
        }
        return new Data(List.copyOf(features),List.copyOf(cells),List.copyOf(batches),entries);
    }
    private static BufferedReader reader(Path p)throws IOException{return new BufferedReader(new InputStreamReader(Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
    private static String[] fields(String s)throws IOException{require(s!=null&&s.length()<=268435456,"Missing/oversized complete TSV row");return s.split("\t",-1);}
    private static void label(String s)throws IOException{require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Invalid literal feature/cell ID");}
    private static void unique(List<String> x,String axis)throws IOException{require(!x.isEmpty()&&new HashSet<>(x).size()==x.size(),"Empty/duplicate complete "+axis+" axis");}
    private static int index(String s,int maximum)throws IOException{require(s.matches("0|[1-9][0-9]*"),"Require exact nonnegative Matrix Market integer");try{long value=Long.parseLong(s);require(value<=maximum,"Matrix Market dimension/entry capacity exceeded");return (int)value;}catch(NumberFormatException e){throw new IOException("Matrix Market integer capacity exceeded",e);}}
    private static double number(String s)throws IOException{require(NUMBER.matcher(s).matches(),"Invalid numeric token; NaN/Inf/missing values are not repaired");try{double value=Double.parseDouble(s);require(Double.isFinite(value),"Nonfinite numeric value");return value;}catch(NumberFormatException e){throw new IOException("Numeric input outside the finite double domain",e);}}
    private static void require(boolean value,String message)throws IOException{if(!value)throw new IOException(message);}
}
