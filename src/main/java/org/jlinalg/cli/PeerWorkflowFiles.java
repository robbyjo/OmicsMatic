/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal identities, recorded FP32 conversion and deterministic worker preparation. */
final class PeerWorkflowFiles {
    private PeerWorkflowFiles() { }
    record Matrix(List<String> rows,List<String> columns,double[][] values) { }
    record Prepared(Matrix matrix,Matrix covariates,Matrix variance,int known,Map<String,Object> summary) { }
    private static List<String[]> rows(Path path,Map<String,Object> r)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>PeerWorkflowContracts.limit(r,"maximum_input_bytes"))throw new IOException("Require a bounded regular matrix: "+path);
        var rows=new ArrayList<String[]>();long cells=0;
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("PEER input interrupted");
                if(line.getBytes(StandardCharsets.UTF_8).length>PeerWorkflowContracts.limit(r,"maximum_line_bytes"))throw new IOException("maximum_line_bytes exceeded");
                String[] fields=line.split("\t",-1);if(rows.isEmpty()&&fields.length<2)throw new IOException("Require named matrix columns");
                if(!rows.isEmpty()&&fields.length!=rows.get(0).length)throw new IOException("Nonrectangular matrix; no blank-cell repair");
                cells+=fields.length;if(cells>PeerWorkflowContracts.limit(r,"maximum_cells")+1_000_000L)throw new IOException("Input cell capacity exceeded");rows.add(fields);
            }
        }
        if(rows.size()<2)throw new IOException("Require header and nonempty matrix");return rows;
    }
    private static Matrix matrix(Path path,Map<String,Object> r,String first)throws IOException {
        var lines=rows(path,r);if(!lines.get(0)[0].equals(first))throw new IOException("Require exact first column "+first);
        var columns=new ArrayList<String>();var unique=new HashSet<String>();
        for(int j=1;j<lines.get(0).length;j++){String id=lines.get(0)[j];identity(id);if(!unique.add(id))throw new IOException("Duplicate column identity");columns.add(id);}
        var ids=new ArrayList<String>();unique.clear();double[][] values=new double[lines.size()-1][columns.size()];
        if((long)values.length*columns.size()>PeerWorkflowContracts.limit(r,"maximum_cells"))throw new IOException("maximum_cells exceeded");
        for(int i=1;i<lines.size();i++) {
            String[] row=lines.get(i);identity(row[0]);if(!unique.add(row[0]))throw new IOException("Duplicate row identity");ids.add(row[0]);
            for(int j=1;j<row.length;j++) {
                if(!row[j].equals(row[j].strip())||row[j].isEmpty())throw new IOException("Numeric cells must be explicit without surrounding whitespace");
                double value;try{value=Double.parseDouble(row[j]);}catch(NumberFormatException failed){throw new IOException("Invalid numeric matrix cell",failed);}
                if(!Double.isFinite(value)||!Float.isFinite((float)value))throw new IOException("Nonfinite/out-of-range original FP32 input; no replacement");values[i-1][j-1]=value;
            }
        }
        return new Matrix(List.copyOf(ids),List.copyOf(columns),values);
    }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        Matrix y=matrix(root.resolve("inputs/matrix.tsv"),r,"feature_id");Matrix cov=null,var=null;
        if(r.containsKey("covariates")) {
            cov=matrix(root.resolve("inputs/covariates.tsv"),r,"sample_id");
            if(!cov.rows.equals(y.columns))throw new IOException("Covariate sample identities/order must exactly match expression columns; no alignment repair");
        }
        if(r.containsKey("measurement_variance")) {
            var=matrix(root.resolve("inputs/measurement_variance.tsv"),r,"feature_id");
            if(!var.rows.equals(y.rows)||!var.columns.equals(y.columns))throw new IOException("Measurement variance identities/order differ");
            for(double[] row:var.values)for(double value:row)if(value<0)throw new IOException("Measurement variances must be nonnegative");
        }
        int known=(cov==null?0:cov.columns.size())+(Boolean.TRUE.equals(r.get("add_mean"))?1:0);
        long total=known+PeerWorkflowContracts.limit(r,"factors");if(total>=y.rows.size()||total>=y.columns.size())throw new IOException("Original total factors must be less than samples and features");
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("samples",y.columns);summary.put("features",y.rows);summary.put("covariate_columns",cov==null?List.of():cov.columns);
        summary.put("known_factors_including_optional_mean",known);summary.put("total_factors",total);summary.put("source_matrix_scalar","IEEE754-binary32");
        var conversions=new LinkedHashMap<String,Object>();conversions.put("matrix",rounding(y));if(cov!=null)conversions.put("covariates",rounding(cov));if(var!=null)conversions.put("measurement_variance",rounding(var));summary.put("original_FP32_conversion",conversions);
        worker(root.resolve("worker/data.tsv"),transpose(y.values),compare);if(cov!=null)worker(root.resolve("worker/covariates.tsv"),cov.values,compare);if(var!=null)worker(root.resolve("worker/variance.tsv"),transpose(var.values),compare);
        compareOrWrite(root.resolve("prepared.json"),RsemJson.bytes(summary),compare);return new Prepared(y,cov,var,known,summary);
    }
    private static Map<String,Object> rounding(Matrix matrix) {
        long changed=0,underflow=0;double maximum=0;
        for(double[] row:matrix.values)for(double value:row){double cast=(float)value;if(Double.doubleToLongBits(cast)!=Double.doubleToLongBits(value))changed++;if(value!=0&&cast==0)underflow++;maximum=Math.max(maximum,Math.abs(cast-value));}
        return Map.of("rounded_cells",changed,"underflowed_nonzero_cells",underflow,"maximum_absolute_change",maximum);
    }
    static double[][] transpose(double[][] values) {double[][] result=new double[values[0].length][values.length];for(int i=0;i<values.length;i++)for(int j=0;j<values[0].length;j++)result[j][i]=values[i][j];return result;}
    private static void worker(Path path,double[][] values,boolean compare)throws IOException {
        var text=new StringBuilder();for(double[] row:values){for(int j=0;j<row.length;j++){if(j>0)text.append('\t');text.append(Double.toString(row[j]));}text.append('\n');}compareOrWrite(path,text.toString().getBytes(StandardCharsets.UTF_8),compare);
    }
    static void compareOrWrite(Path path,byte[] bytes,boolean compare)throws IOException {
        if(compare){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(Files.readAllBytes(path),bytes))throw new IOException("Deterministic prepared/export bytes differ: "+path.getFileName());}
        else Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
}
