/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.sequence.QuantileNormalization;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native finite-double, column-wise quantile normalization with exact IDs. */
final class QuantileNormalizeCli {
    private QuantileNormalizeCli() { }
    static int run(String[] arguments, PrintStream out, PrintStream err) {
        if (Arrays.asList(arguments).contains("--help")) { out.println(help()); return 0; }
        try {
            var o=options(arguments,Set.of("matrix","id-column","columns","axis","input-kind","target","target-column","max-cells","max-bytes","out"));
            for(String key:List.of("matrix","id-column","columns","axis","input-kind"))required(o,key);
            if(!o.get("axis").equals("columns"))throw new IllegalArgumentException("--axis columns is the supported qnorm float64 subset; ndarray axis0 orientation and row normalization are not implemented");
            if(!Set.of("continuous","raw-counts","estimated-counts").contains(o.get("input-kind")))throw new IllegalArgumentException("--input-kind requires continuous,raw-counts or estimated-counts");
            if(o.containsKey("target")!=o.containsKey("target-column"))throw new IllegalArgumentException("--target and --target-column must be supplied together");
            var columns=CliNumericTable.columns(o.get("columns"));
            if(new HashSet<>(columns).size()!=columns.size()||columns.contains(o.get("id-column")))throw new IllegalArgumentException("Numeric columns must be unique and exclude --id-column");
            int maxCells=integer(o,"max-cells",10_000_000,1,100_000_000),maxBytes=integer(o,"max-bytes",268_435_456,1,Integer.MAX_VALUE);
            int[] code={0};
            transaction(o,"quantile-normalize-columns",(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();var sources=new LinkedHashMap<Path,String>();
                session.put("engine","native-java");session.put("axis","columns");session.put("input_kind",o.get("input-kind"));session.put("numeric_columns",columns);
                session.put("scope","Finite double column-wise normalization; original row/sample order. qnorm0.8.0 axis1 semantics only; no dtype32,axis0,pandas,incremental,multiprocessing or full-paper parity. No imputation or inverse-normal transform.");
                try {
                    Path matrix=source(o.get("matrix"),sources,maxBytes);var data=DelimitedData.read(matrix);int id=data.column(o.get("id-column"));
                    if((long)data.rows().size()*columns.size()>maxCells)throw new IOException("Matrix exceeds --max-cells; use an explicit larger permitted limit or a smaller declared cohort");
                    for(String header:columns)textField(header,"Numeric column name");textField(o.get("id-column"),"ID column name");
                    var ids=new HashSet<String>();
                    for(String[] row:data.rows())if(!ids.add(textField(row[id],"Row ID")))throw new IOException("Duplicate row ID:"+row[id]);
                    double[][] input=numericMatrix(data,columns,o.get("input-kind"));
                    for(double[] row:input)for(double value:row)if(!o.get("input-kind").equals("continuous")&&(value<0||o.get("input-kind").equals("raw-counts")&&value!=Math.rint(value)))throw new IOException("Count inputs must be nonnegative; raw-counts additionally require integers");
                    double[] target=null;
                    if(o.containsKey("target")){Path path=source(o.get("target"),sources,maxBytes);target=CliNumericTable.read(path).column(o.get("target-column"));session.put("target_origin","Supplied numeric values sorted by rank; target row IDs are not matched to matrix features");}
                    else session.put("target_origin","Mean of each rank across selected columns; ties average the occupied target ranks");
                    var normalized=QuantileNormalization.normalizeColumns(input,target);double[][] values=normalized.normalized();double[] ranks=normalized.target();
                    for(var e:sources.entrySet())if(!hash(e.getKey()).equals(e.getValue()))throw new IOException("Source changed during normalization:"+e.getKey());
                    var headers=new ArrayList<String>();headers.add(o.get("id-column"));headers.addAll(columns);var rows=new ArrayList<String[]>();
                    for(int row=0;row<values.length;row++){String[] fields=new String[columns.size()+1];fields[0]=data.rows().get(row)[id];for(int column=0;column<columns.size();column++)fields[column+1]=Double.toString(values[row][column]);rows.add(fields);}
                    table(dir.resolve("results.tsv"),headers,rows);var targetRows=new ArrayList<String[]>();for(int rank=0;rank<ranks.length;rank++)targetRows.add(new String[]{Integer.toString(rank+1),Double.toString(ranks[rank])});table(dir.resolve("target.tsv"),List.of("rank_one_based","target"),targetRows);
                    session.put("rows",values.length);session.put("columns",columns.size());
                    warnings.add("NORMALIZATION CHANGES DISTRIBUTIONS: selected columns share a rank target. Verify that distributional matching is scientifically appropriate. Output is continuous normalized data,not raw counts for a negative-binomial count model.");
                    warnings.add("TIE AND PRECISION CONTRACT: tied inputs receive the average of their occupied target ranks. Marginal distributions can therefore differ when tie patterns differ. Double arithmetic is validated against original qnorm0.8.0 references; bitwise NumPy/Numba equivalence and float32/int32 behavior are not claimed.");
                    manifest.put("analysis_status","completed-with-warnings");
                } catch(Exception failure) {
                    code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(dir);warnings.add(0,"ANALYSIS FAILED:"+failure.getMessage());
                }
                var hashes=new LinkedHashMap<String,String>();sources.forEach((path,digest)->hashes.put(path.toString(),digest));session.put("source_sha256",hashes);session.put("warnings",warnings);
                Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
                Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
                Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==0?"QUANTILE NORMALIZATION WARNINGS REQUIRE REVIEW":"QUANTILE NORMALIZATION FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action:"+(code[0]==0?"Review column orientation,shared-target assumptions and downstream model/input units. Retain the fitted target and exact IDs; original sources remain unchanged.":"Correct the named matrix/target/dimension/count/finite-value or resource limit. Missing values require an explicit upstream preprocessing decision. Rescale extreme values if finite rank/tie sums overflow. Rerun to a new directory; failed partials remain under partial/ and are not successful output.")+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS:":"ANALYSIS FAILED:")+"see "+Path.of(required(o,"out"),"WARNINGS.md"));
            if(code[0]==0)out.println("Native column-wise quantile normalization completed:"+o.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg:"+failure.getMessage());return 2;}
    }
    private static Path source(String name,Map<Path,String> hashes,int maxBytes)throws IOException {
        Path path=Path.of(name).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>maxBytes)throw new IOException("Source missing or exceeds --max-bytes:"+path);hashes.put(path,hash(path));return path;
    }
    private static String textField(String value,String name)throws IOException {
        if(value.isBlank()||value.indexOf('\t')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IOException(name+" must be nonblank and contain no tab/newline");return value;
    }
    private static double[][] numericMatrix(DelimitedData data,List<String> columns,String kind)throws IOException {
        int[] indexes=columns.stream().mapToInt(data::column).toArray();double[][] result=new double[data.rows().size()][columns.size()];
        for(int row=0;row<result.length;row++)for(int column=0;column<indexes.length;column++){
            String text=data.rows().get(row)[indexes[column]].trim();
            if(kind.equals("raw-counts")){
                try{BigInteger count=new BigDecimal(text).toBigIntegerExact();if(count.signum()<0||count.compareTo(BigInteger.valueOf(9_007_199_254_740_991L))>0)throw new ArithmeticException("outside safe double integer range");}
                catch(NumberFormatException|ArithmeticException failure){throw new IOException("Raw count at row"+(row+2)+",column"+columns.get(column)+" must be an exact integer in0..9007199254740991; fractional/rounded counts require explicit upstream correction",failure);}
            }
            try{double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new NumberFormatException("not finite");result[row][column]=value;}
            catch(NumberFormatException failure){throw new IOException("Nonfinite/nonnumeric value at row"+(row+2)+",column"+columns.get(column)+"; explicit preprocessing is required",failure);}
        }
        return result;
    }
    private static String help(){return """
        Native column-wise quantile normalization (finite double qnorm0.8.0 axis1 subset).
        quantile-normalize --matrix TABLE --id-column ID --columns S1,S2 --axis columns
          --input-kind continuous|raw-counts|estimated-counts --out NEW_DIRECTORY
          [--target TABLE --target-column VALUE] [--max-cells 10000000] [--max-bytes 268435456]
        Rows are shared observations and selected columns are samples; order/IDs are retained.
        Supplied target values are sorted by rank; their row IDs are not feature matches.
        Output is continuous normalized data, not raw counts. Review WARNINGS.md and target.tsv.
        Raw-count text must be an exact integer in0..9007199254740991; silent integer rounding is rejected.
        No imputation,inverse-normal scoring,row normalization,float32 or whole-package parity.
        """;}
}
