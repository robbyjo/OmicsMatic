/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.SpyrrowCommunication;
import org.jlinalg.singlecell.SpyrrowSourceSemantics;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete packaged native readback for separately captured unchanged-source controls. */
public final class SpyrrowCommunicationMatrix {
    private SpyrrowCommunicationMatrix() { }
    private static List<String> texts(Object raw)throws Exception{var result=new ArrayList<String>();for(Object value:list(raw))result.add(text(value));return List.copyOf(result);}
    private static double number(Object raw)throws Exception{if(raw instanceof String state)return switch(state){case "NaN"->Double.NaN;case "+Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->throw new IllegalArgumentException("Unknown floating state");};return finite(raw,-Double.MAX_VALUE,Double.MAX_VALUE);}
    private static double[] vector(Object raw)throws Exception{var list=list(raw);double[] values=new double[list.size()];for(int i=0;i<values.length;i++)values[i]=number(list.get(i));return values;}
    private static double[][] matrix(Object raw)throws Exception{var list=list(raw);double[][] values=new double[list.size()][];for(int i=0;i<values.length;i++)values[i]=vector(list.get(i));return values;}
    private static Object scalar(double value){return Double.isFinite(value)?value:Double.isNaN(value)?"NaN":value>0?"+Inf":"-Inf";}
    private static List<Object> values(double[][] input){var rows=new ArrayList<Object>();for(double[] row:input){var next=new ArrayList<Object>();for(double value:row)next.add(scalar(value));rows.add(next);}return rows;}
    public static void main(String[] args)throws Exception {
        require(args.length==2,"Require complete original cases and NEW output directory");Path source=Path.of(args[0]),out=Path.of(args[1]);byte[] raw=NextflowFiles.bytes(source,67108864);var root=object(NextflowParameters.parse(NextflowFiles.utf8(raw),67108864,8000000));require(!Files.exists(out),"Existing native controls protected");Files.createDirectory(out);var results=new ArrayList<Object>();var ledgers=new LinkedHashMap<String,Object>();long operations=0;
        for(Object entry:list(root.get("cases"))){var c=object(entry);String name=text(c.get("id"));var counts=new SpyrrowCommunication.Counts(texts(c.get("cells")),texts(c.get("genes")),matrix(c.get("expression")));var database=new SpyrrowCommunication.Database(texts(c.get("ligands")),texts(c.get("receptors")));var result=SpyrrowCommunication.analyze(counts,vector(c.get("x")),vector(c.get("y")),database,number(c.get("threshold")),SpyrrowCommunication.Limits.defaults());results.add(Map.of("id",name,"index",result.pairs().stream().map(SpyrrowCommunication.Pair::sourceKey).toList(),"columns",result.columns(),"values",values(result.values()),"pairs",result.pairs().stream().map(pair->List.of(pair.firstCell(),pair.secondCell())).toList()));ledgers.put(name,Map.of("database_rows",result.databaseLedger().stream().map(r->Map.of("source_row",r.sourceRow(),"ligand",r.ligand(),"receptor",r.receptor(),"disposition",r.disposition(),"source_key",r.key())).toList(),"duplicate_genes",result.duplicateGenes(),"trailing_ligands",result.trailingLigands(),"trailing_receptors",result.trailingReceptors(),"ligand_order",result.ligandOrder(),"receptor_order",result.receptorOrder(),"warnings",result.warnings()));operations+=result.operations();}
        var hashes=new ArrayList<Long>();for(String value:texts(root.get("hash_inputs")))hashes.add(SpyrrowSourceSemantics.stringHash(value));var sets=new ArrayList<Object>();for(Object group:list(root.get("set_inputs")))sets.add(SpyrrowSourceSemantics.setOrder(texts(group)));var distances=new ArrayList<Object>();for(Object row:list(root.get("distance_inputs"))){double[] v=vector(row);require(v.length==4,"Distance control axis mismatch");distances.add(scalar(SpyrrowSourceSemantics.distance(v[0],v[1],v[2],v[3])));}
        var counts=new SpyrrowCommunication.Counts(List.of("a","b"),List.of("L","R"),new double[][]{{1,4},{9,16}});var database=new SpyrrowCommunication.Database(List.of("L"),List.of("R"));double[] x={0,.5},y={0,0};var limits=SpyrrowCommunication.Limits.defaults();var rejected=new ArrayList<String>();
        reject("no-original-pairs",()->SpyrrowCommunication.analyze(counts,x,y,database,0,limits),rejected);
        reject("duplicate-cell-ID",()->SpyrrowCommunication.analyze(new SpyrrowCommunication.Counts(List.of("a","a"),counts.genes(),counts.expression()),x,y,database,1,limits),rejected);
        reject("ambiguous-cell-pair-delimiter",()->SpyrrowCommunication.analyze(new SpyrrowCommunication.Counts(List.of("a|b","c"),counts.genes(),counts.expression()),x,y,database,1,limits),rejected);
        reject("ambiguous-gene-key",()->SpyrrowCommunication.analyze(new SpyrrowCommunication.Counts(counts.cells(),List.of("L:a","R"),counts.expression()),x,y,database,1,limits),rejected);
        reject("negative-expression",()->SpyrrowCommunication.analyze(new SpyrrowCommunication.Counts(counts.cells(),counts.genes(),new double[][]{{-1,4},{9,16}}),x,y,database,1,limits),rejected);
        reject("nonfinite-expression",()->SpyrrowCommunication.analyze(new SpyrrowCommunication.Counts(counts.cells(),counts.genes(),new double[][]{{Double.NaN,4},{9,16}}),x,y,database,1,limits),rejected);
        reject("nonfinite-coordinate",()->SpyrrowCommunication.analyze(counts,new double[]{0,Double.NaN},y,database,1,limits),rejected);
        reject("nonfinite-threshold",()->SpyrrowCommunication.analyze(counts,x,y,database,Double.POSITIVE_INFINITY,limits),rejected);
        reject("ragged-coordinate-axis",()->SpyrrowCommunication.analyze(counts,new double[]{0},y,database,1,limits),rejected);
        reject("ragged-expression",()->SpyrrowCommunication.analyze(new SpyrrowCommunication.Counts(counts.cells(),counts.genes(),new double[][]{{1},{9,16}}),x,y,database,1,limits),rejected);
        reject("complete-cell-capacity",()->SpyrrowCommunication.analyze(counts,x,y,database,1,new SpyrrowCommunication.Limits(1,10,10,10,100,1000)),rejected);
        reject("complete-output-capacity",()->SpyrrowCommunication.analyze(counts,x,y,database,1,new SpyrrowCommunication.Limits(10,10,10,10,4,1000)),rejected);
        reject("operation-capacity",()->SpyrrowCommunication.analyze(counts,x,y,database,1,new SpyrrowCommunication.Limits(10,10,10,10,100,1)),rejected);
        Thread.currentThread().interrupt();try{reject("thread-cancellation",()->SpyrrowCommunication.analyze(counts,x,y,database,1,limits),rejected);}finally{Thread.interrupted();}
        counts.expression()[0][0]=999;require(counts.expression()[0][0]==1,"Caller matrix alias mutated protected input");var result=SpyrrowCommunication.analyze(counts,x,y,database,1,limits);double expected=result.values()[0][0];result.values()[0][0]=888;require(result.values()[0][0]==expected,"Returned matrix alias mutated protected result");require(Arrays.equals(raw,NextflowFiles.bytes(source,67108864)),"Original controls changed");NextflowFiles.writeJson(out.resolve("results.json"),Map.of("cases",results,"hashes",hashes,"sets",sets,"distances",distances));NextflowFiles.writeJson(out.resolve("ledgers.json"),ledgers);NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","PASS_NATIVE_SPYRROW_COMMUNICATION_COMPONENT_ONLY","cases",results.size(),"expected_rejections",rejected,"operations",operations,"defensive_copy_controls",true,"implementation_credit",false,"whole_goal_complete",false));
    }
    @FunctionalInterface private interface Check{Object run()throws Exception;}
    private static void reject(String name,Check check,List<String> rejected)throws Exception{boolean failed=false;try{check.run();}catch(IllegalArgumentException expected){failed=true;}require(failed,"Invalid native input admitted: "+name);rejected.add(name);}
}
