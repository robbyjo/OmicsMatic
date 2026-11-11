/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.LeafCutterModels;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Actual-JAR evaluation of every original fixed/fitted objective and effect. */
public final class LeafCutterModelsMatrix {
    private LeafCutterModelsMatrix() { }
    private static double number(Object v)throws Exception{return finite(v,-Double.MAX_VALUE,Double.MAX_VALUE);}
    private static List<Double> vector(Object v)throws Exception{var result=new ArrayList<Double>();for(Object x:list(v))result.add(number(x));return List.copyOf(result);}
    private static double[][] matrix(Object v)throws Exception{var rows=list(v);double[][] result=new double[rows.size()][];for(int i=0;i<rows.size();i++)result[i]=vector(rows.get(i)).stream().mapToDouble(Double::doubleValue).toArray();return result;}
    private static long[][] counts(Object v)throws Exception{var rows=list(v);long[][] result=new long[rows.size()][];for(int i=0;i<rows.size();i++){var row=list(rows.get(i));result[i]=new long[row.size()];for(int j=0;j<row.size();j++)result[i][j]=integer(row.get(j),0,9007199254740992L);}return result;}
    private static LeafCutterModels.Parameters parameters(Object v)throws Exception{var p=object(v);var rows=new ArrayList<List<Double>>();for(Object row:list(p.get("beta_raw")))rows.add(vector(row));return new LeafCutterModels.Parameters(rows,vector(p.get("beta_scale")),vector(p.get("conc")),p.containsKey("outlier_prob")?number(p.get("outlier_prob")):null);}
    public static void main(String[] args)throws Exception {
        require(args.length==2,"Need complete model cases and NEW output directory");Path request=Path.of(args[0]),out=Path.of(args[1]);byte[] original=NextflowFiles.bytes(request,16777216);var root=object(NextflowParameters.parse(NextflowFiles.utf8(original),16777216,2000000));require(!Files.exists(out),"Existing model outputs protected");Files.createDirectory(out);var results=new ArrayList<Object>();
        for(Object raw:list(root.get("cases"))){var c=object(raw);String id=text(c.get("id")),kind=text(c.get("kind"));var result=new LinkedHashMap<String,Object>();result.put("id",id);result.put("kind",kind);
            switch(kind){
                case "objective" -> {var prior=object(c.get("priors"));var p=new LeafCutterModels.Priors(number(prior.get("shape")),number(prior.get("rate")),number(prior.get("outlier_a")),number(prior.get("outlier_b")));result.put("objective",LeafCutterModels.logPosterior(counts(c.get("counts")),matrix(c.get("design")),parameters(c.get("parameters")),p,bool(c.get("robust"))));}
                case "effects" -> {var e=LeafCutterModels.effectSizes(parameters(c.get("parameters")));result.put("log_effects",e.logEffects());result.put("baseline",e.baseline());result.put("perturbed",e.perturbed());result.put("delta_psi",e.deltaPsi());}
                case "anova" -> {var a=LeafCutterModels.anova(number(c.get("null_objective")),number(c.get("full_objective")),Math.toIntExact(integer(c.get("null_columns"),1,1000)),Math.toIntExact(integer(c.get("full_columns"),2,1000)),Math.toIntExact(integer(c.get("introns"),2,1000)));result.put("log_ratio",a.logRatio());result.put("df",a.degreesOfFreedom());result.put("p",a.pValue());}
                default -> throw new IllegalArgumentException("Unknown audit case");
            }
            results.add(result);
        }
        var rejected=new ArrayList<String>();var good=new LeafCutterModels.Parameters(List.of(List.of(.5,.5),List.of(.2,.8)),List.of(1.0,2.0),List.of(10.0,20.0),.05);var defaults=LeafCutterModels.Priors.sourceDefaults();long[][] y={{10,20},{20,10}};double[][] x={{1,0},{1,1}};
        reject("negative-original-count",()->LeafCutterModels.logPosterior(new long[][]{{-1,20},{20,10}},x,good,defaults,false),rejected);
        reject("count-above-exact-Stan-double-profile",()->LeafCutterModels.logPosterior(new long[][]{{9007199254740993L,20},{20,10}},x,good,defaults,false),rejected);
        reject("total-above-exact-Stan-double-profile",()->LeafCutterModels.logPosterior(new long[][]{{9007199254740992L,20},{20,10}},x,good,defaults,false),rejected);
        reject("ragged-counts",()->LeafCutterModels.logPosterior(new long[][]{{10},{20,10}},x,good,defaults,false),rejected);
        reject("ragged-design",()->LeafCutterModels.logPosterior(y,new double[][]{{1},{1,1}},good,defaults,false),rejected);
        reject("nonfinite-design",()->LeafCutterModels.logPosterior(y,new double[][]{{1,Double.NaN},{1,1}},good,defaults,false),rejected);
        reject("nonpositive-prior",()->new LeafCutterModels.Priors(0,1,1,1),rejected);
        reject("non-simplex-coefficients",()->LeafCutterModels.coefficientRows(new LeafCutterModels.Parameters(List.of(List.of(.2,.2)),List.of(1.0),List.of(1.0,1.0),null)),rejected);
        reject("boundary-simplex",()->LeafCutterModels.coefficientRows(new LeafCutterModels.Parameters(List.of(List.of(0.0,1.0)),List.of(1.0),List.of(1.0,1.0),null)),rejected);
        reject("nonpositive-concentration",()->LeafCutterModels.coefficientRows(new LeafCutterModels.Parameters(List.of(List.of(.5,.5)),List.of(1.0),List.of(0.0,1.0),null)),rejected);
        reject("missing-robust-outlier-parameter",()->LeafCutterModels.logPosterior(y,x,new LeafCutterModels.Parameters(good.betaRaw(),good.betaScale(),good.concentration(),null),defaults,true),rejected);
        reject("outlier-boundary",()->LeafCutterModels.logPosterior(y,x,new LeafCutterModels.Parameters(good.betaRaw(),good.betaScale(),good.concentration(),1.0),defaults,true),rejected);
        reject("non-nested-model-degrees-of-freedom",()->LeafCutterModels.anova(-10,-9,2,2,2),rejected);
        reject("source-effect-exp-overflow",()->LeafCutterModels.effectSizes(new LeafCutterModels.Parameters(good.betaRaw(),List.of(1e8,1e8),good.concentration(),null)),rejected);
        require(LeafCutterModels.anova(-10,-11,1,2,2).pValue()==1,"Retain the original negative objective-difference upper tail");
        require(Arrays.equals(original,NextflowFiles.bytes(request,16777216)),"Original model cases changed");NextflowFiles.writeJson(out.resolve("results.json"),Map.of("cases",results));NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","PASS_NATIVE_LEAFCUTTER_MODEL_EVALUATIONS_ONLY","cases",results.size(),"expected_native_rejections",rejected,"negative_source_objective_difference_retained",true,"implementation_credit",false,"whole_goal_complete",false));
    }
    @FunctionalInterface private interface Check {Object run()throws Exception;}
    private static void reject(String name,Check action,List<String> rejected)throws Exception{boolean bad=false;try{action.run();}catch(IllegalArgumentException expected){bad=true;}require(bad,"Invalid original model quantities admitted: "+name);rejected.add(name);}
}
