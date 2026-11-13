/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.Monocle131ModelRequest.*;

/** Complete factor identity and optional full reconstruction checking with bounded working matrices. */
final class FastMnn129StreamAudit {
    private FastMnn129StreamAudit() { }
    private static final Pattern NUMBER=Pattern.compile("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?");

    static Map<String,Object> audit(Map<String,Object> m,Path directory,long budget,long byteBudget)throws IOException {
        require(integer(m.get("schema_version"),2,2)==2&&text(m.get("matrix_encoding")).equals("COMPLETE_TSV"),"Require source-bound streaming FastMNN schema2");
        require(text(m.get("status")).equals("PASS_ORIGINAL_FASTMNN129_COMPLETE_PREPARED_BATCH_CORRECTION"),"Require complete original FastMNN result");
        require(bool(m.get("complete_required_model_roundtrip"))&&!bool(m.get("input_preparation_transform_applied"))&&!bool(m.get("paper_patch_recovered"))&&!bool(m.get("whole_goal_complete")),"Unsupported model/transform/paper claim");
        require(text(m.get("original_seed_class")).equals("LowRankMatrixSeed")&&text(m.get("original_seed_package")).equals("BiocSingular")&&NextflowFiles.strings(m.get("original_seed_slots")).equals(List.of("rotation","components")),"Unknown original low-rank model representation");
        var cells=labels(m.get("cell_ids"),1000000);require(cells.size()>=2,"Require at least two complete cells");var features=labels(m.get("feature_ids"),1000000);require(NextflowFiles.strings(m.get("batches")).size()==cells.size(),"Incomplete cell/batch population");
        int dimensions=(int)integer(m.get("dimensions"),1,1000000);String mode=text(m.get("reconstruction_audit"));require(Set.of("FACTORS","ALL_VALUES").contains(mode),"Unknown reconstruction audit mode");
        long factorValues=Math.multiplyExact(Math.addExact((long)cells.size(),features.size()),dimensions);long products=mode.equals("ALL_VALUES")?Math.multiplyExact(Math.multiplyExact((long)cells.size(),features.size()),dimensions):0;
        require(Math.addExact(Math.multiplyExact(2,factorValues),products)<=budget,"Complete native factor/reconstruction capacity exceeded; no prefix comparison");
        var names=new ArrayList<>(List.of("corrected.tsv","rotation.tsv","assay-components.tsv","assay-rotation.tsv"));if(mode.equals("ALL_VALUES"))names.add("reconstructed-cells.tsv");
        var pins=new LinkedHashMap<Path,NextflowFiles.Pin>();long bytes=0;
        for(String name:names){Path path=directory.resolve(name);var pin=NextflowFiles.pin(path,byteBudget,()->{});bytes=Math.addExact(bytes,pin.bytes());require(bytes<=byteBudget,"Complete audit table byte capacity exceeded");pins.put(path,pin);}
        pair(directory.resolve("corrected.tsv"),directory.resolve("assay-components.tsv"),"cell_id",cells,dimensions,false);
        double[][] rotation=pair(directory.resolve("rotation.tsv"),directory.resolve("assay-rotation.tsv"),"feature_id",features,dimensions,mode.equals("ALL_VALUES"));
        double largest=0;
        if(mode.equals("ALL_VALUES")){
            try(var corrected=reader(directory.resolve("corrected.tsv"));var reconstructed=reader(directory.resolve("reconstructed-cells.tsv"))){
                header(corrected,"cell_id",dimensions,null);header(reconstructed,"cell_id",features.size(),features);
                for(int c=0;c<cells.size();c++){
                    var scoreFields=row(corrected,cells.get(c),dimensions);double[] scores=new double[dimensions];for(int d=0;d<dimensions;d++)scores[d]=number(scoreFields[d+1]);var values=row(reconstructed,cells.get(c),features.size());
                    for(int f=0;f<features.size();f++){
                        double sum=0,compensation=0,magnitude=0;
                        for(int d=0;d<dimensions;d++){double product=rotation[f][d]*scores[d];require(Double.isFinite(product),"Nonfinite native reconstruction product");double adjusted=product-compensation,next=sum+adjusted;compensation=(next-sum)-adjusted;sum=next;magnitude+=Math.abs(product);}
                        double difference=Math.abs(sum-number(values[f+1])),bound=1e-11+64*Math.ulp(1d)*dimensions*magnitude;
                        require(Double.isFinite(sum)&&Double.isFinite(magnitude)&&difference<=bound,"Complete reconstructed value differs at feature "+f+", cell "+c);largest=Math.max(largest,difference);
                    }
                }
                require(corrected.readLine()==null&&reconstructed.readLine()==null,"Excess complete matrix rows");
            }
        }
        var fingerprints=new LinkedHashMap<String,Object>();
        for(var e:pins.entrySet()){require(e.getValue().equals(NextflowFiles.pin(e.getKey(),e.getValue().bytes(),()->{})),"Source matrix changed during audit: "+e.getKey());fingerprints.put(e.getKey().getFileName().toString(),e.getValue());}
        var result=new LinkedHashMap<String,Object>();result.put("status",mode.equals("ALL_VALUES")?"PASS_COMPLETE_FASTMNN_STREAMED_RECONSTRUCTION_AND_SEED_FACTORS":"PASS_COMPLETE_FASTMNN_LAZY_MODEL_SEED_FACTORS");result.put("reconstruction_audit",mode);result.put("complete_cells",cells.size());result.put("complete_features",features.size());result.put("dimensions",dimensions);result.put("complete_factor_values_compared",factorValues);result.put("native_multiply_adds",products);result.put("numeric_reconstruction_values_checked",mode.equals("ALL_VALUES")?(long)cells.size()*features.size():0);result.put("maximum_absolute_difference",mode.equals("ALL_VALUES")?Double.valueOf(largest):null);result.put("complete_original_low_rank_seed_factors_equal",true);result.put("reconstruction_preserved_in_original_RDS",true);result.put("input_matrix_files",fingerprints);result.put("working_matrix_scope",mode.equals("ALL_VALUES")?"rotation plus one cell row; no cell-by-feature reconstruction array":"one factor row at a time; no dense reconstructed matrix");result.put("original_fitting_repeated",false);result.put("producer_authenticated",false);result.put("original_analysis_admitted",false);result.put("whole_goal_complete",false);return result;
    }

    private static double[][] pair(Path first,Path second,String axis,List<String> ids,int columns,boolean retain)throws IOException {
        double[][] values=retain?new double[ids.size()][columns]:null;
        try(var a=reader(first);var b=reader(second)){
            header(a,axis,columns,null);header(b,axis,columns,null);
            for(int i=0;i<ids.size();i++){var x=row(a,ids.get(i),columns);var y=row(b,ids.get(i),columns);for(int j=0;j<columns;j++){double left=number(x[j+1]),right=number(y[j+1]);require(Double.doubleToLongBits(left)==Double.doubleToLongBits(right),"Original low-rank seed factor differs from the complete public model factors");if(retain)values[i][j]=left;}}
            require(a.readLine()==null&&b.readLine()==null,"Excess original factor rows");
        }
        return values;
    }
    private static void header(BufferedReader reader,String axis,int columns,List<String> ids)throws IOException {var f=fields(reader.readLine());require(f.length==columns+1&&f[0].equals(axis),"Original matrix header/column count differs");if(ids!=null)require(Arrays.asList(f).subList(1,f.length).equals(ids),"Original reconstructed feature-column population differs");}
    private static String[] row(BufferedReader reader,String id,int columns)throws IOException {var f=fields(reader.readLine());require(f.length==columns+1&&f[0].equals(id),"Incomplete or reordered original matrix row/axis");return f;}
    private static String[] fields(String line)throws IOException {require(line!=null&&line.length()<=268435456,"Missing/oversized complete matrix row");return line.split("\t",-1);}
    private static double number(String token)throws IOException {require(NUMBER.matcher(token).matches(),"Invalid/nonfinite original matrix token");try{double value=Double.parseDouble(token);require(Double.isFinite(value),"Nonfinite original matrix value");return value;}catch(NumberFormatException e){throw new IOException("Original numeric matrix capacity exceeded",e);}}
    private static BufferedReader reader(Path path)throws IOException{return new BufferedReader(new InputStreamReader(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
}
