/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.jlinalg.mrpresso.MrPressoOriginal;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.MrPressoWorkflowFiles.*;

/** Bounded owned-thread calculation and explicit finite/nonfinite/conditional cells. */
final class MrPressoWorkflowEvaluation {
    private MrPressoWorkflowEvaluation() { }
    static Map<String,Object> evaluate(Path input,Map<String,Object> request)throws IOException {
        var prepared=MrPressoWorkflowData.read(input,request);var random=MrPressoWorkflowData.random(request);var options=MrPressoWorkflowData.options(request);
        long seconds=MrPressoWorkflowContracts.limit(request,"timeout_seconds"),started=System.nanoTime();
        var owned=new AtomicReference<Thread>();var pool=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"omicsmatic-mrpresso-native-"+UUID.randomUUID());owned.set(t);return t;});
        Future<MrPressoOriginal.Result> pending=pool.submit(()->MrPressoOriginal.analyze(prepared.data(),options,random));
        MrPressoOriginal.Result result;
        try{
            result=pending.get(seconds,TimeUnit.SECONDS);
            require(System.nanoTime()-started<=TimeUnit.SECONDS.toNanos(seconds),"Native calculation exceeded final deadline; no complete result admitted");
        }catch(TimeoutException expired){pending.cancel(true);throw new IOException("Native MR-PRESSO deadline exceeded; dependent result withheld. Increase an appropriate explicit timeout and retry NEW output.",expired);}
        catch(InterruptedException interrupted){pending.cancel(true);Thread.currentThread().interrupt();throw new InterruptedIOException("Native MR-PRESSO capture interrupted; no complete result");}
        catch(ExecutionException failed){throw new IOException("Native MR-PRESSO calculation refused: "+failed.getCause(),failed.getCause());}
        finally{
            pool.shutdownNow();boolean interrupted=Thread.interrupted();
            try{
                Thread thread=owned.get();if(thread!=null)thread.join(30_000);
                require(thread==null||!thread.isAlive(),"Owned native compute thread has not stopped; no result admitted; preserve this process/capture for diagnosis");
            }catch(InterruptedException again){Thread.currentThread().interrupt();throw new InterruptedIOException("Interrupted while closing the owned native thread");}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
        var raw=fit(result.raw(),false);var corrected=fit(result.corrected(),true);var outliers=new ArrayList<Object>();
        for(var value:result.outliers())outliers.add(map("row_id",value.rowName(),"retained_ordinal",value.retainedOrdinal(),
            "squared_residual",cell(value.squaredResidual()),"p_value",probability(value.probability()),"adjusted_internal_p",cell(value.adjustedInternalP())));
        Object distortion=null;boolean unreportable=!Boolean.TRUE.equals(raw.get("inference_admitted"));
        if(result.corrected()!=null)unreportable|=!Boolean.TRUE.equals(corrected.get("inference_admitted"));
        if(result.distortion()!=null){
            var value=result.distortion();boolean calculated=value.status()==MrPressoOriginal.DistortionStatus.CALCULATED;
            boolean admitted=calculated&&value.probability().reportable()&&Arrays.stream(value.coefficientPercent()).allMatch(Double::isFinite);
            unreportable|=calculated&&!admitted;
            distortion=map("status",value.status().name(),"outlier_ordinals",ints(value.outlierOrdinals()),"coefficient_percent",cells(value.coefficientPercent()),
                "p_value",probability(value.probability()),"inference_admitted",admitted);
        }
        var warnings=new ArrayList<>(result.warnings());
        if(unreportable)warnings.add("UNREPORTABLE_FIELDS: preserve the raw native source-profile diagnostics; do not use undefined, aliased, nonfinite or out-of-range inference fields.");
        boolean allow=MrPressoWorkflowContracts.bool(object(request.get("policies")),"allow_unreportable_fields",false);
        boolean admitted=!unreportable||allow;
        return map("schema_version",1,"engine","native","profile",MrPressoWorkflowContracts.PROFILE,"source_commit",MrPressoOriginal.SOURCE_COMMIT,
            "prepared",prepared.metadata(),"exposure_names",prepared.data().exposureNames(),"retained_input_ordinals",ints(result.retainedInputOrdinals()),
            "retained_row_ids",result.retainedRowNames(),"raw_mr",raw,"corrected_mr",corrected,"global_rss",cell(result.globalRss()),
            "global_p_value",probability(result.globalProbability()),"outliers",outliers,"distortion",distortion,
            "outlier_test_performed",!result.outliers().isEmpty(),"distortion_branch_performed",result.distortion()!=null,
            "rng_before",ints(result.rngBefore()),"rng_after",ints(result.rngAfter()),"warnings",warnings,
            "contains_unreportable_fields",unreportable,"allow_unreportable_fields",allow,"results_admitted",admitted,
            "original_R_executed",false,"whole_tool_qualified",false,"whole_paper_reproduced",false);
    }

    private static Map<String,Object> fit(MrPressoOriginal.Fit value,boolean conditional) {
        if(value==null)return map("status","SOURCE_NOT_ESTIMATED","literal","NA","inference_admitted",false,"conditional_unavailability",conditional);
        var beta=value.beta();var se=value.standardError();var statistics=value.statistic();var p=value.pValue();boolean admitted=value.rank()==beta.length&&value.residualDf()>0&&!value.essentiallyPerfect();
        for(int j=0;j<beta.length;j++)admitted&=Double.isFinite(beta[j])&&Double.isFinite(se[j])&&se[j]>0&&Double.isFinite(statistics[j])&&Double.isFinite(p[j])&&p[j]>=0&&p[j]<=1;
        return map("status",admitted?"FINITE_SOURCE_PROFILE_INFERENCE":"SOURCE_FIELDS_UNREPORTABLE","rank",value.rank(),"residual_df",value.residualDf(),
            "beta",cells(beta),"standard_error",cells(se),"statistic",cells(statistics),"p_value",cells(p),"essentially_perfect",value.essentiallyPerfect(),
            "native_inference_reliability_guard","WEIGHTED_RESIDUAL_NORM_OVER_FITTED_NORM_LE_1E_MINUS15","inference_admitted",admitted);
    }
    private static Map<String,Object> probability(MrPressoOriginal.Probability value) {
        return map("kind",!value.reportable()?"UNREPORTABLE":value.lessThanBound()?"STRICT_UPPER_BOUND":"EMPIRICAL",
            "exceedances",value.exceedances(),"distributions",value.distributions(),"internal_source_value",cell(value.originalValue()),
            "resolution",cell(value.resolution()),"reported_bound",value.lessThanBound()?cell(value.bound()):null,
            "inference_admitted",value.reportable());
    }
    static Map<String,Object> cell(double value){return Double.isFinite(value)?map("kind","FINITE","value",value):map("kind","NONFINITE","literal",Double.isNaN(value)?"NaN":value>0?"Inf":"-Inf");}
    private static List<Object> cells(double[] values){var out=new ArrayList<Object>();for(double value:values)out.add(cell(value));return out;}
    private static List<Object> ints(int[] values){var out=new ArrayList<Object>();for(int value:values)out.add(value);return out;}

    /** Exact structure/counts/IDs with a declared portable-FP envelope only for finite scientific cells. */
    static void compare(Object expected,Object actual,String path)throws IOException {
        if(expected instanceof Map<?,?> a&&actual instanceof Map<?,?> b){
            require(a.keySet().equals(b.keySet()),"Scientific field population differs at "+path);
            if("FINITE".equals(a.get("kind"))&&"FINITE".equals(b.get("kind"))){
                double x=decimal(a.get("value")).doubleValue(),y=decimal(b.get("value")).doubleValue();
                require(Double.isFinite(x)&&Double.isFinite(y),"Nonfinite value disguised as a finite cell at "+path);
                if(x==0||y==0)require(x==y,"Source numeric zero changed at "+path);
                else require(Math.abs(x-y)<=3e-12*Math.max(Math.abs(x),Math.abs(y))+16*Math.max(Math.ulp(x),Math.ulp(y)),"Scientific numeric contradiction at "+path);
                require(a.size()==2,"Unexpected finite-cell fields at "+path);return;
            }
            for(Object key:a.keySet())compare(a.get(key),b.get(key),path+"/"+key);return;
        }
        if(expected instanceof List<?> a&&actual instanceof List<?> b){require(a.size()==b.size(),"Scientific axis size differs at "+path);for(int i=0;i<a.size();i++)compare(a.get(i),b.get(i),path+"/"+i);return;}
        if(expected instanceof org.jlinalg.gprofiler.GprofilerJson.Numeric||expected instanceof Number){require(decimal(expected).compareTo(decimal(actual))==0,"Exact scientific count/state differs at "+path);return;}
        require(Objects.equals(expected,actual),"Scientific label/status contradiction at "+path);
    }
}
