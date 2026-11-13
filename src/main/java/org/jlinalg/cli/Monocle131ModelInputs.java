/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** Complete sparse expression capture; never selects or repairs gene/cell populations. */
final class Monocle131ModelInputs {
    private Monocle131ModelInputs() { }
    record Data(List<String> genes,List<String> cells,Map<String,double[]> numeric,int[] offsets,int[] columns,double[] values) {
        double[] gene(int index){double[] result=new double[cells.size()];for(int k=offsets[index];k<offsets[index+1];k++)result[columns[k]]=values[k];return result;}
    }
    static Data read(Path matrix,Path cellFile,Path geneFile,String format,String units,Map<String,Object> types,int maximumEntries)throws IOException {
        var genes=new ArrayList<String>();try(var r=reader(geneFile)){require(Arrays.equals(fields(r.readLine()),new String[]{"gene_id","gene_short_name"}),"Require gene_id and gene_short_name header");String s;while((s=r.readLine())!=null){var f=fields(s);require(f.length==2,"Ragged gene table");label(f[0]);require(f[1].length()<=32768&&f[1].chars().noneMatch(c->c<32||c==127),"Invalid literal gene_short_name");genes.add(f[0]);require(genes.size()<=1000000,"Complete gene-axis capacity exceeded");}}
        unique(genes,"gene");var cells=new ArrayList<String>();var numericLists=new LinkedHashMap<String,List<Double>>();for(var e:types.entrySet())if(text(object(e.getValue()).get("type")).equals("numeric"))numericLists.put(e.getKey(),new ArrayList<>());
        try(var r=reader(cellFile)){
            var names=fields(r.readLine());require(names.length==types.size()+1&&names[0].equals("cell_id"),"Cell metadata header population differs");var index=new LinkedHashMap<String,Integer>();for(int i=1;i<names.length;i++)require(index.put(names[i],i)==null&&types.containsKey(names[i]),"Unknown/duplicate cell metadata column");String s;
            while((s=r.readLine())!=null){var f=fields(s);require(f.length==names.length,"Ragged cell metadata");label(f[0]);cells.add(f[0]);require(cells.size()<=1000000,"Complete cell-axis capacity exceeded");for(var e:types.entrySet()){String value=f[index.get(e.getKey())];var spec=object(e.getValue());if(text(spec.get("type")).equals("numeric"))numericLists.get(e.getKey()).add(number(value));else require(NextflowFiles.strings(spec.get("levels")).contains(value),"Undeclared literal factor level: "+e.getKey());}}
        }
        unique(cells,"cell");require(cells.size()>=5,"Require at least five complete observations");
        int[] rowCounts=new int[genes.size()];var triples=new Triples(maximumEntries);
        try(var r=reader(matrix)){
            if(format.equals("TSV")){
                var header=fields(r.readLine());require(header.length==cells.size()+1&&header[0].equals("gene_id")&&Arrays.asList(header).subList(1,header.length).equals(cells),"Expression cell axis differs from cell metadata");String s;int row=0;
                while((s=r.readLine())!=null){var f=fields(s);require(row<genes.size()&&f.length==cells.size()+1&&f[0].equals(genes.get(row)),"Expression gene axis or complete row width differs");for(int j=0;j<cells.size();j++){double v=expression(f[j+1],units);if(v!=0){triples.add(row,j,v);rowCounts[row]++;}}row++;}require(row==genes.size(),"Incomplete expression gene population");
            }else{
                String header=r.readLine();require("%%MatrixMarket matrix coordinate real general".equals(header)||"%%MatrixMarket matrix coordinate integer general".equals(header),"Require real/integer general coordinate Matrix Market");String line;
                do{line=r.readLine();require(line!=null,"Missing Matrix Market dimensions");}while(line.isBlank()||line.stripLeading().startsWith("%"));
                var dims=line.strip().split("\\s+");require(dims.length==3&&index(dims[0],1000000)==genes.size()&&index(dims[1],1000000)==cells.size(),"Matrix Market dimensions differ from complete axes");int entries=index(dims[2],maximumEntries);int count=0;
                while((line=r.readLine())!=null){if(line.isBlank()||line.stripLeading().startsWith("%"))continue;var f=line.strip().split("\\s+");require(f.length==3&&count<entries,"Ragged/excess Matrix Market entries");if(header.endsWith("integer general"))require(f[2].matches("[+-]?[0-9]+"),"Integer Matrix Market entries require literal integer tokens");int i=index(f[0],genes.size())-1,j=index(f[1],cells.size())-1;require(i>=0&&j>=0,"Matrix Market indices are one-based");double v=expression(f[2],units);triples.add(i,j,v);rowCounts[i]++;count++;}require(count==entries,"Incomplete Matrix Market entry population");
            }
        }
        int[] offsets=new int[genes.size()+1];for(int i=0;i<rowCounts.length;i++)offsets[i+1]=Math.addExact(offsets[i],rowCounts[i]);int[] next=offsets.clone(),columns=new int[triples.size];double[] values=new double[triples.size];
        for(int k=0;k<triples.size;k++){int at=next[triples.rows[k]]++;columns[at]=triples.columns[k];values[at]=triples.values[k];}
        int[] marker=new int[cells.size()];Arrays.fill(marker,-1);for(int row=0;row<genes.size();row++)for(int k=offsets[row];k<offsets[row+1];k++){require(marker[columns[k]]!=row,"Duplicate Matrix Market coordinate; no implicit summing/repair");marker[columns[k]]=row;}
        var numeric=new LinkedHashMap<String,double[]>();for(var e:numericLists.entrySet())numeric.put(e.getKey(),e.getValue().stream().mapToDouble(Double::doubleValue).toArray());return new Data(List.copyOf(genes),List.copyOf(cells),Collections.unmodifiableMap(numeric),offsets,columns,values);
    }
    private static final class Triples {
        int[] rows=new int[1024],columns=new int[1024];double[] values=new double[1024];int size;final int max;
        Triples(int maximum){max=maximum;}
        void add(int row,int col,double value)throws IOException {require(size<max,"Complete nonzero/coordinate entry capacity exceeded");if(size==rows.length){int n=(int)Math.min(max,Math.max(size+1L,size*3L/2));rows=Arrays.copyOf(rows,n);columns=Arrays.copyOf(columns,n);values=Arrays.copyOf(values,n);}rows[size]=row;columns[size]=col;values[size++]=value;}
    }
    private static BufferedReader reader(Path p)throws IOException{return new BufferedReader(new InputStreamReader(Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
    private static String[] fields(String s)throws IOException {require(s!=null&&s.length()<=16777216,"Missing or oversized complete TSV row");return s.split("\t",-1);}
    private static void label(String s)throws IOException{require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Invalid literal gene/cell ID");}
    private static void unique(List<String> x,String axis)throws IOException{require(!x.isEmpty()&&new HashSet<>(x).size()==x.size(),"Empty/duplicate complete "+axis+" axis");}
    private static int index(String s,int max)throws IOException{require(s.matches("0|[1-9][0-9]*"),"Require nonnegative exact Matrix Market integer");try{long n=Long.parseLong(s);require(n<=max,"Matrix Market dimension/entry capacity exceeded");return (int)n;}catch(NumberFormatException e){throw new IOException("Matrix Market integer capacity exceeded",e);}}
    private static double expression(String s,String units)throws IOException{double x=number(s);require(x>=0,"Negative expression is outside this workflow");if(units.equals("raw-integer-counts")){require(x==Math.rint(x)&&x<=9007199254740991d,"Declared raw counts must be exact nonnegative integers");if(!s.matches("[+]?[0-9]+"))require(new java.math.BigDecimal(s).stripTrailingZeros().scale()<=0,"Fractional raw-count token is not rounded into an integer");}return x;}
    private static double number(String s)throws IOException{require(s.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"),"Invalid numeric token; missing/NaN/Inf are not repaired");try{double v=Double.parseDouble(s);require(Double.isFinite(v),"Nonfinite numeric input");return v;}catch(NumberFormatException e){throw new IOException("Numeric input outside domain",e);}}
    private static Map<?,?> object(Object x)throws IOException{return NextflowFiles.object(x);}
    private static String text(Object x)throws IOException{return NextflowFiles.text(x);}
    private static void require(boolean b,String cause)throws IOException{if(!b)throw new IOException(cause);}
}
