/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit FID/IID and selected-column contracts for REGENIE phenotype/covariate input. */
public final class RegenieTables {
    private RegenieTables() { }
    /** A declared model/coding; quantitative mode never infers binary status from values. */
    public enum Kind { QUANTITATIVE, BINARY_ZERO_ONE, BINARY_ONE_TWO, COVARIATES }
    /** Exact pair identity; the upstream underscore-joined key is checked separately for collisions. */
    public record Sample(String fid,String iid) {
        public Sample {
            if(!token(fid)||!token(iid))throw new IllegalArgumentException("FID/IID must be nonempty tokens without whitespace/control characters");
        }
        /** Exact key used by the pinned upstream table/LOCO readers. */
        public String upstreamKey(){return fid+"_"+iid;}
    }
    /** Immutable original selected cells; NA remains distinct from every numeric/category value. */
    public record Row(Sample sample,List<String> cells) {
        public Row {cells=List.copyOf(cells);}
    }
    /** Selected data in original sample order, without numeric rescaling, sorting or rank transformation. */
    public record Table(List<String> columns,List<Row> rows,Map<String,Long> missing,Set<String> categorical,Kind kind) {
        public Table {columns=List.copyOf(columns);rows=List.copyOf(rows);missing=Map.copyOf(missing);categorical=Set.copyOf(categorical);}
        /** Write only the explicitly selected columns; preserves the original token values and row order. */
        public void write(Path path)throws IOException {
            try(var writer=Files.newBufferedWriter(path)){
                writer.write("FID\tIID");for(String column:columns){writer.write('\t');writer.write(column);}writer.write('\n');
                for(Row row:rows){writer.write(row.sample().fid());writer.write('\t');writer.write(row.sample().iid());for(String cell:row.cells()){writer.write('\t');writer.write(cell);}writer.write('\n');}
            }
        }
        /** Exact selected sample pairs, including entries with explicitly missing cells. */
        public List<Sample> samples(){return rows.stream().map(Row::sample).toList();}
    }

    /** Read a whitespace-delimited FID IID table; no caller defaults or phenotype-type guessing. */
    public static Table read(Path path,List<String> selected,Set<String> categorical,Kind kind)throws IOException {
        if(kind==null||selected==null||selected.isEmpty()||categorical==null)throw new IllegalArgumentException("Require selected columns, categorical declaration and a model/coding");
        if(new HashSet<>(selected).size()!=selected.size())throw new IllegalArgumentException("Selected columns contain duplicates");
        if(!selected.containsAll(categorical)||kind!=Kind.COVARIATES&&!categorical.isEmpty())throw new IllegalArgumentException("Categorical columns must be explicitly selected covariates");
        for(String column:selected)if(!column.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")||Set.of("FID","IID").contains(column))throw new IllegalArgumentException("Selected column names must be safe nonempty tokens distinct from FID/IID");
        var rows=new ArrayList<Row>();var counts=new LinkedHashMap<String,Long>();for(String column:selected)counts.put(column,0L);
        var keys=new HashSet<String>();
        try(var input=Files.newBufferedReader(path)){
            String line=input.readLine();if(line==null)throw new IOException("Empty phenotype/covariate table: "+path);
            String[] header=fields(line);if(header.length<3||!header[0].equals("FID")||!header[1].equals("IID"))throw new IOException("Table header must begin FID IID and contain named data columns: "+path);
            var names=new HashSet<String>();for(String name:header)if(!names.add(name))throw new IOException("Duplicate table header: "+name);
            int[] indices=new int[selected.size()];for(int j=0;j<indices.length;j++){indices[j]=-1;for(int k=2;k<header.length;k++)if(header[k].equals(selected.get(j)))indices[j]=k;if(indices[j]<0)throw new IOException("Selected column is absent: "+selected.get(j));}
            long number=1;
            while((line=input.readLine())!=null){
                number++;String[] values=fields(line);if(values.length!=header.length)throw new IOException("Table width differs from header at row "+number+"; use explicit NA for missing cells");
                Sample sample;
                try{sample=new Sample(values[0],values[1]);}catch(IllegalArgumentException failure){throw new IOException("Invalid FID/IID at row "+number,failure);}
                if(!keys.add(sample.upstreamKey()))throw new IOException("Duplicate or colliding FID_IID key at row "+number+": "+sample.upstreamKey()+"; distinct pairs must not collapse when joined with an underscore");
                var cells=new ArrayList<String>();
                for(int j=0;j<indices.length;j++){
                    String cell=values[indices[j]],column=selected.get(j);cells.add(cell);
                    if(cell.equals("NA")){counts.put(column,counts.get(column)+1);continue;}
                    if(categorical.contains(column))continue;
                    double value;
                    try{value=Double.parseDouble(cell);}catch(NumberFormatException failure){throw new IOException("Invalid numeric token at row "+number+", column "+column+"; trailing text is not accepted",failure);}
                    if(!Double.isFinite(value))throw new IOException("Nonfinite value at row "+number+", column "+column+"; missing values must explicitly be NA");
                    if(value==-999)throw new IOException("Reserved upstream missing sentinel -999 at row "+number+", column "+column+"; use NA for missingness or explicitly rescale a legitimate value before analysis");
                    if(kind==Kind.BINARY_ZERO_ONE&&value!=0&&value!=1||kind==Kind.BINARY_ONE_TWO&&value!=1&&value!=2)throw new IOException("Binary value differs from the declared coding at row "+number+", column "+column);
                }
                rows.add(new Row(sample,cells));
            }
        }
        if(rows.isEmpty())throw new IOException("Phenotype/covariate table contains no data rows");
        for(String column:selected)if(counts.get(column)==rows.size())throw new IOException("Selected column is entirely missing: "+column);
        return new Table(selected,rows,counts,categorical,kind);
    }
    private static String[] fields(String line){return line.isBlank()?new String[0]:line.strip().split("\\s+");}
    private static boolean token(String value){return value!=null&&!value.isEmpty()&&value.chars().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c));}
}
