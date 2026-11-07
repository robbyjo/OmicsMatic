/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Literal axis validation and dense/sparse response validation; no aggregation or identifier repair. */
final class Monocle2DifferentialInputs {
    private Monocle2DifferentialInputs() { }
    record Population(List<String> genes,List<String> cells,long storedEntries,long explicitZeros) { }
    static Population validate(Path inputs,Monocle2DifferentialRequest.Request r)throws IOException {
        var gm=tsv(inputs.resolve("gene-metadata.tsv"),r);require(Arrays.equals(gm.get(0),new String[]{"gene_id","gene_short_name"}),"Require gene_id<TAB>gene_short_name header");require(gm.size()>1&&gm.size()-1<=r.maximumGenes(),"Gene population exceeds capacity or is empty");
        var genes=new ArrayList<String>();var seen=new HashSet<String>();for(int i=1;i<gm.size();i++){var row=gm.get(i);require(row.length==2,"Ragged gene metadata");require(seen.add(row[0]),"Duplicate literal gene ID");genes.add(row[0]);}
        var cm=tsv(inputs.resolve("cell-metadata.tsv"),r);var header=cm.get(0);require(header[0].equals("cell_id")&&header.length==r.types().size()+1,"Cell header requires cell_id and exactly all declared typed covariates");
        var columns=new HashSet<String>();for(int j=1;j<header.length;j++)require(columns.add(header[j]),"Duplicate covariate header");require(columns.equals(r.types().keySet()),"Metadata header and metadata_types differ");require(cm.size()>=3&&cm.size()-1<=r.maximumCells(),"Cell population exceeds capacity or has fewer than two cells");
        var cells=new ArrayList<String>();seen.clear();
        for(int i=1;i<cm.size();i++){
            var row=cm.get(i);require(row.length==header.length&&seen.add(row[0]),"Ragged metadata row or duplicate cell ID");cells.add(row[0]);
            for(int j=1;j<header.length;j++){var spec=object(r.types().get(header[j]));if(text(spec.get("type")).equals("factor"))require(strings(spec.get("levels"),1024).contains(row[j]),"Undeclared literal factor level");else numeric(row[j],"continuous");}
        }
        long[] counts=r.format().equals("dense-tsv")?dense(inputs.resolve("expression"),r,genes,cells):sparse(inputs.resolve("expression"),r,genes.size(),cells.size());
        return new Population(List.copyOf(genes),List.copyOf(cells),counts[0],counts[1]);
    }
    private static long[] dense(Path path,Monocle2DifferentialRequest.Request r,List<String> genes,List<String> cells)throws IOException {
        require((long)genes.size()*cells.size()<=r.denseEntries(),"Matrix exceeds maximum_dense_entries");var rows=tsv(path,r);require(rows.size()==genes.size()+1,"Dense expression gene population differs");var h=rows.get(0);
        require(h.length==cells.size()+1&&h[0].equals("gene_id")&&Arrays.asList(h).subList(1,h.length).equals(cells),"Dense expression cell IDs must exactly match metadata order");double[] totals=new double[cells.size()];long zeros=0;
        for(int i=1;i<rows.size();i++){var row=rows.get(i);require(row.length==h.length&&row[0].equals(genes.get(i-1)),"Dense expression gene IDs must exactly match metadata order; no reordering");for(int j=1;j<row.length;j++){double x=numeric(row[j],r.units());totals[j-1]+=x;if(x==0)zeros++;}}
        totals(totals,r);return new long[]{(long)genes.size()*cells.size(),zeros};
    }
    private static long[] sparse(Path path,Monocle2DifferentialRequest.Request r,int genes,int cells)throws IOException {
        String content=NextflowFiles.utf8(NextflowFiles.bytes(path,(int)r.inputBytes())).replace("\r\n","\n");require(!content.contains("\r"),"Matrix Market requires LF/CRLF");var lines=content.lines().iterator();require(lines.hasNext(),"Missing Matrix Market banner");String banner=lines.next();
        require(Set.of("%%MatrixMarket matrix coordinate integer general","%%MatrixMarket matrix coordinate real general").contains(banner),"Only general coordinate integer/real Matrix Market is supported; no symmetric expansion");
        String dimensions=next(lines);String[] shape=dimensions.trim().split("\\s+");require(shape.length==3,"Matrix Market needs row/column/entry dimensions");long nr=exact(shape[0],1,r.maximumGenes()),nc=exact(shape[1],2,r.maximumCells()),nnz=exact(shape[2],0,r.sparseEntries());require(nr==genes&&nc==cells,"Sparse dimensions must match declared ordered gene/cell metadata");
        var coordinates=new HashSet<Long>();double[] totals=new double[cells];long zeros=0,count=0;
        while(lines.hasNext()){
            String line=lines.next();if(line.startsWith("%"))continue;require(!line.isBlank()&&count<nnz,"Blank or excessive sparse entry");var fields=line.trim().split("\\s+");require(fields.length==3,"Sparse entries require row column value");long row=exact(fields[0],1,nr),col=exact(fields[1],1,nc);
            require(coordinates.add((row-1)*nc+col-1),"Duplicate sparse coordinates are refused; no implicit summation");double x=numeric(fields[2],r.units());
            if(banner.contains(" integer "))try{new BigDecimal(fields[2]).toBigIntegerExact();}catch(ArithmeticException e){throw new IOException("Integer Matrix Market banner cannot contain fractional entries",e);}
            totals[(int)col-1]+=x;if(x==0)zeros++;count++;
        }
        require(count==nnz,"Sparse entry count differs from declared nnz");totals(totals,r);return new long[]{count,zeros};
    }
    private static String next(Iterator<String> lines)throws IOException {while(lines.hasNext()){String s=lines.next();if(!s.startsWith("%")){require(!s.isBlank(),"Blank Matrix Market dimension record");return s;}}throw new IOException("Missing Matrix Market dimensions");}
    private static long exact(String text,long min,long max)throws IOException {require(text.matches("0|[1-9][0-9]*"),"Require an exact nonnegative Matrix Market integer");try{long value=Long.parseLong(text);require(value>=min&&value<=max,"Matrix Market integer outside capacity");return value;}catch(NumberFormatException e){throw new IOException("Matrix Market integer overflow",e);}}
    private static void totals(double[] totals,Monocle2DifferentialRequest.Request r)throws IOException {boolean sf=false;for(var a:r.analyses())sf|=bool(a.get("estimate_size_factors"));if(sf)for(double x:totals)require(x>0&&Double.isFinite(x),"Size-factor estimation requires positive finite column totals; no deletion/imputation");}
    static double numeric(String value,String units)throws IOException {
        require(value.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"),"Response/covariate must be an explicit finite number; no missing-value tokens");double d;try{d=Double.parseDouble(value);}catch(NumberFormatException e){throw new IOException("Invalid numeric response",e);}require(Double.isFinite(d),"Nonfinite response/covariate");
        if(units.equals("raw-integer-counts")){
            try{var integer=new BigDecimal(value).toBigIntegerExact();require(integer.signum()>=0&&integer.compareTo(java.math.BigInteger.valueOf(9007199254740991L))<=0,"Raw count outside exact nonnegative binary64 integer domain");}catch(ArithmeticException e){throw new IOException("Raw counts must be exact integers",e);}
        }
        if(units.equals("binary"))require(d==0||d==1,"Binary responses require literal numeric 0/1 values; no dichotomization");
        if(units.equals("proportions"))require(d>=0&&d<=1,"Proportions must lie in [0,1]; no trial counts are inferred");return d;
    }
    private static List<String[]> tsv(Path path,Monocle2DifferentialRequest.Request r)throws IOException {String value=NextflowFiles.utf8(NextflowFiles.bytes(path,(int)r.inputBytes())).replace("\r\n","\n");require(!value.contains("\r"),"TSV requires LF/CRLF");var result=new ArrayList<String[]>();var lines=value.lines().iterator();while(lines.hasNext()){String line=lines.next();require(!line.isEmpty(),"Blank TSV record");String[] row=line.split("\t",-1);for(String field:row)label(field);result.add(row);require(result.size()<=Math.max(r.maximumGenes(),r.maximumCells())+1,"TSV record capacity exceeded");}require(!result.isEmpty(),"Empty TSV");return result;}
}
