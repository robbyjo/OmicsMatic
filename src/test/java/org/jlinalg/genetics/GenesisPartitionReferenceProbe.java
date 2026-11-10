/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Development consumer only: original fixtures are never read as native answers. */
public final class GenesisPartitionReferenceProbe {
    private GenesisPartitionReferenceProbe() { }
    private static final HexFormat HEX = HexFormat.of();
    private static List<String[]> table(Path path) throws Exception {
        List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);List<String[]> out=new ArrayList<>();
        for(int i=1;i<lines.size();i++)out.add(lines.get(i).split("\t",-1));return out;
    }
    private static List<String> ids(Path file) throws Exception {
        if(!Files.exists(file))return null;List<String> ids=new ArrayList<>();
        for(String[] row:table(file)){if(Integer.parseInt(row[0])!=ids.size()+1)throw new IllegalArgumentException("Input ID order changed");ids.add(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(HEX.parseHex(row[1]))).toString());}return ids;
    }
    private static double number(String value) {return switch(value){case "Inf" -> Double.POSITIVE_INFINITY;case "-Inf" -> Double.NEGATIVE_INFINITY;case "NaN","NA" -> Double.NaN;default -> Double.parseDouble(value);};}
    private static GenesisPcairPartition.Matrix matrix(Path folder,String name) throws Exception {
        List<String> ids=ids(folder.resolve(name+"-ids.tsv"));if(ids==null)return null;
        double[][] values=new double[ids.size()][ids.size()];boolean[][] seen=new boolean[ids.size()][ids.size()];
        for(String[] row:table(folder.resolve(name+".tsv"))){int i=Integer.parseInt(row[0])-1,j=Integer.parseInt(row[1])-1;if(seen[i][j])throw new IllegalArgumentException("Duplicate matrix cell");seen[i][j]=true;values[i][j]=number(row[2]);}
        for(boolean[] row:seen)for(boolean value:row)if(!value)throw new IllegalArgumentException("Incomplete matrix");
        return new GenesisPcairPartition.Matrix(ids,values);
    }
    private static void rows(List<String> output,String kind,List<String> ids){for(int i=0;i<ids.size();i++)output.add(kind+"\t"+(i+1)+"\t"+HEX.formatHex(ids.get(i).getBytes(StandardCharsets.UTF_8)));}
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("REFERENCE_ROOT NEW_OUTPUT");Path reference=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);
        List<String> statuses=new ArrayList<>();statuses.add("case\tstatus\terror_code");
        for(String[] declared:table(reference.resolve("original-case-dispositions.tsv"))){
            String name=declared[0];Path folder=reference.resolve(name),home=out.resolve(name);Files.createDirectory(home);List<String> output=new ArrayList<>();output.add("kind\tresult_row\tid_utf8_hex");
            try{
                GenesisPcairPartition.Matrix kin=matrix(folder,"kinship"),div=matrix(folder,"divergence");
                Map<String,Double> options=new HashMap<>();for(String[] row:table(folder.resolve("options.tsv")))options.put(row[0],number(row[1]));
                var request=new GenesisPcairPartition.Options(options.get("kinship_threshold_hex"),options.get("divergence_threshold_hex"),ids(folder.resolve("forced-ids.tsv")),ids(folder.resolve("included-ids.tsv")));
                var result=GenesisPcairPartition.partition(kin,div,request,new GenesisPcairPartition.Limits(5000,1000000000L,2000000000L));
                rows(output,"related",result.related());rows(output,"unrelated",result.unrelated());rows(output,"warning",result.warnings());
                Files.writeString(home.resolve("native-metadata.tsv"),"selected\titerations\tunrelated_pairs_above_threshold\n"+result.selectedSamples()+"\t"+result.iterations()+"\t"+result.unrelatedPairsAboveThreshold()+"\n",StandardCharsets.UTF_8);
                statuses.add(name+"\tNATIVE_SUCCESS\t");
            }catch(GenesisPcairPartition.SourceFailure failure){rows(output,"warning",failure.warnings());Files.writeString(home.resolve("native-error.txt"),failure.getMessage(),StandardCharsets.UTF_8);statuses.add(name+"\tSOURCE_REFUSAL\t"+failure.sourceCode());
            }catch(IllegalArgumentException failure){Files.writeString(home.resolve("native-error.txt"),failure.getMessage(),StandardCharsets.UTF_8);statuses.add(name+"\tUNSUPPORTED_OR_INVALID_PROFILE\t");}
            Files.write(home.resolve("native-partition.tsv"),output,StandardCharsets.UTF_8);
        }
        Files.write(out.resolve("native-case-dispositions.tsv"),statuses,StandardCharsets.UTF_8);
        List<String> sums=new ArrayList<>();sums.add("case\tcount\tnative_sum_hex");
        for(String[] row:table(reference.resolve("original-scalar-sums.tsv"))){var sum=new GenesisPcairPartition.Sum64();List<String> values=Files.readAllLines(reference.resolve("scalar-sums/"+row[0]+".hex"),StandardCharsets.UTF_8);for(String value:values)sum.add(number(value));sums.add(row[0]+"\t"+values.size()+"\t"+Double.toHexString(sum.value()));}
        Files.write(out.resolve("native-scalar-sums.tsv"),sums,StandardCharsets.UTF_8);System.out.println("NATIVE_COMPONENT_CONSUMER_ONLY "+(statuses.size()-1)+" partitions "+(sums.size()-1)+" sums");
    }
}
