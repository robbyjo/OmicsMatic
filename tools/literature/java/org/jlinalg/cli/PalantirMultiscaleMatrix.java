/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.jlinalg.embedding.PalantirMultiscale;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Compare entire unchanged-original output families, including signed zeros and nonfinite states. */
public final class PalantirMultiscaleMatrix {
    private PalantirMultiscaleMatrix() { }
    private static double value(Object x)throws Exception {return Double.longBitsToDouble(Long.parseUnsignedLong(text(x),16));}
    private static double[] vector(Object x)throws Exception {var raw=list(x);double[] out=new double[raw.size()];for(int i=0;i<out.length;i++)out[i]=value(raw.get(i));return out;}
    private static double[][] matrix(Object x)throws Exception {var raw=list(x);double[][] out=new double[raw.size()][];for(int i=0;i<out.length;i++)out[i]=vector(raw.get(i));return out;}
    private static long comparisons;
    private static void same(double actual,double expected,String where)throws Exception {require(Double.isNaN(actual)&&Double.isNaN(expected)||Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits(expected),"Original complete float64 state differs: "+where);comparisons++;}
    public static void main(String[] args)throws Exception {
        require(args.length==2,"Require source cases JSON and NEW result JSON");Path path=Path.of(args[0]),out=Path.of(args[1]);require(!Files.exists(out),"Existing native evidence protected");var source=CellPhoneDbWorkflowRuntime.read(path,67108864);var results=new ArrayList<Object>();int numericCases=0,sourceErrors=0,deferrals=0,sourceOnly=0;long nans=0,infinities=0,operations=0;
        for(Object item:list(source.get("cases"))){
            var row=object(item);String name=text(row.get("id")),scope=text(row.get("native_scope"));
            if(scope.startsWith("source-only")){sourceOnly++;results.add(Map.of("id",name,"status","retained-source-only-profile-no-native-parity-claimed"));continue;}
            var labels=list(row.get("cells"));String[] cells=new String[labels.size()];for(int i=0;i<cells.length;i++)cells[i]=text(labels.get(i));double[][] vectors=matrix(row.get("eigenvectors_bits"));double[] eigenvalues=vector(row.get("eigenvalues_bits"));Integer count=row.get("n_eigs")==null?null:Math.toIntExact(integer(row.get("n_eigs"),Integer.MIN_VALUE,Integer.MAX_VALUE));boolean rejected=false;
            if(text(row.get("source_status")).equals("original-error")||scope.equals("requires-upstream-tie-order")){
                String reason="";try{PalantirMultiscale.scale(cells,vectors,eigenvalues,count,PalantirMultiscale.Limits.defaults());}catch(IllegalArgumentException failure){rejected=true;reason=failure.toString();}
                require(rejected,"Unsupported/source error was silently admitted: "+name);if(scope.equals("requires-upstream-tie-order")){require(reason.contains("Tied automatic eigengaps"),"Unexpected tie-deferral cause");deferrals++;}else sourceErrors++;results.add(Map.of("id",name,"status","rejected-no-native-result","reason",reason));continue;
            }
            var result=PalantirMultiscale.scale(cells,vectors,eigenvalues,count,PalantirMultiscale.Limits.defaults());double[][] expected=matrix(row.get("coordinates_bits")),actual=result.coordinates();double[] weights=vector(row.get("weights_bits"));int columns=Math.toIntExact(integer(row.get("output_columns"),0,1000000));require(actual.length==expected.length&&Arrays.equals(result.cells(),cells)&&result.selectedComponents().length==columns&&weights.length==columns,"Complete source axes differ: "+name);
            for(int i=0;i<actual.length;i++){require(actual[i].length==columns&&expected[i].length==columns,"Incomplete source row");for(int j=0;j<columns;j++)same(actual[i][j],expected[i][j],name+"["+i+","+j+"]");}
            double[] actualWeights=result.weights();int[] selected=result.selectedComponents();for(int j=0;j<columns;j++){same(actualWeights[j],weights[j],name+" weight "+j);require(selected[j]==j+1,"Leading component was not excluded positionally");}
            require(result.nEigs()==integer(row.get("actual_n_eigs"),Integer.MIN_VALUE,Integer.MAX_VALUE)&&result.automatic()==(count==null),"Source bound/selection state changed");nans+=result.nanOutputs();infinities+=result.infiniteOutputs();operations+=result.operations();numericCases++;
            // Returned arrays and caller-owned input identities cannot mutate a completed result.
            if(cells.length>0){String original=result.cells()[0];cells[0]="caller-change";require(result.cells()[0].equals(original),"Input label was retained by reference");String[] altered=result.cells();altered[0]="getter-change";require(result.cells()[0].equals(original),"Getter exposes labels");}
            if(actual.length>0&&columns>0){double original=actual[0][0];actual[0][0]=12345;vectors[0][1]=12345;same(result.coordinates()[0][0],original,"defensive coordinates");actualWeights[0]=12345;selected[0]=99;require(result.weights()[0]!=12345&&result.selectedComponents()[0]==1,"Getter exposes selection/weights");}
            results.add(Map.of("id",name,"status","matched-complete-original-float64-family","coordinates",(long)actual.length*columns,"operations",result.operations(),"nan_outputs",result.nanOutputs(),"infinite_outputs",result.infiniteOutputs()));
        }
        int invalid=0;String[] cells={"a","b"};double[][] vectors={{1,2,3},{4,5,6}};double[] eigenvalues={1,.8,.4};
        for(String kind:List.of("rows","ragged","null-label","control-label","surrogate","cell-cap","component-cap","value-cap","operation-cap","cancelled")){
            boolean rejected=false;
            try{
                String[] ids=cells.clone();double[][] data={vectors[0].clone(),vectors[1].clone()};var limits=PalantirMultiscale.Limits.defaults();
                switch(kind){case "rows" -> ids=new String[]{"a"};case "ragged" -> data[1]=new double[]{4};case "null-label" -> ids[0]=null;case "control-label" -> ids[0]="a\nb";case "surrogate" -> ids[0]="\uD800";case "cell-cap" -> limits=new PalantirMultiscale.Limits(1,3,100,100);case "component-cap" -> limits=new PalantirMultiscale.Limits(2,2,100,100);case "value-cap" -> limits=new PalantirMultiscale.Limits(2,3,1,100);case "operation-cap" -> limits=new PalantirMultiscale.Limits(2,3,100,1);case "cancelled" -> Thread.currentThread().interrupt();default -> throw new AssertionError(kind);}
                PalantirMultiscale.scale(ids,data,eigenvalues,3,limits);
            }catch(IllegalArgumentException|NullPointerException|CancellationException failure){rejected=true;}finally{if(kind.equals("cancelled"))require(Thread.interrupted(),"Cancellation flag was silently cleared");}
            require(rejected,"Native invalid input/capacity admitted: "+kind);invalid++;
        }
        NextflowFiles.writeJson(out,Map.of("status","PASS_COMPLETE_ORIGINAL_PALANTIR_MULTISCALE_COMPONENT_ONLY","edition",source.get("edition"),"numeric_cases",numericCases,"source_errors_rejected",sourceErrors,"ambiguous_ties_deferred",deferrals,"source_only_profiles_retained",sourceOnly,"numeric_state_comparisons",comparisons,"native_rejections",invalid,"nonfinite_outputs",Map.of("nan",nans,"infinite",infinities),"cases",results));System.out.println("PASS "+numericCases+" complete source cases, "+comparisons+" exact numeric states, "+invalid+" native rejections; full Palantir remains separate");
    }
}
