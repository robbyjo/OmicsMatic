/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.jlinalg.genetics.SmartpcaAnalysis;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;

/** Owned bounded execution, exact integer semantics and explicit nonfinite diagnostics. */
final class SmartpcaWorkflowEvaluation {
    private SmartpcaWorkflowEvaluation() { }
    record Evaluated(SmartpcaWorkflowData.Prepared data,SmartpcaAnalysis.Result analysis,
            Map<String,Object> result,double[][] coordinates,List<Boolean> coordinateAdmission) { }
    static Evaluated evaluate(Path root,Map<String,Object> request,SmartpcaWorkflowTables.Budget budget)throws IOException {
        long timeout=SmartpcaWorkflowContracts.limit(request,"timeout_seconds"),started=System.nanoTime();
        var owned=new AtomicReference<Thread>();
        var pool=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"omicsmatic-smartpca-native-"+java.util.UUID.randomUUID());owned.set(thread);return thread;});
        var future=pool.submit(()->compute(root,request,budget));boolean deadline=false,returned=false;
        try {
            Evaluated evaluated=future.get(timeout,TimeUnit.SECONDS);
            require(System.nanoTime()-started<=TimeUnit.SECONDS.toNanos(timeout),"Native read/calculation exceeded final deadline; no complete result");returned=true;return evaluated;
        }catch(TimeoutException expired){deadline=true;future.cancel(true);throw new IOException("Native smartpca read/calculation deadline exceeded. Preserve partial evidence; review resources and retry NEW output with an appropriate explicit timeout.",expired);}
        catch(InterruptedException interrupted){future.cancel(true);Thread.currentThread().interrupt();throw new InterruptedIOException("Native smartpca capture interrupted; no complete result");}
        catch(ExecutionException failed){throw new IOException("Native smartpca calculation refused: "+failed.getCause(),failed.getCause());}
        finally {
            pool.shutdownNow();boolean interrupted=Thread.interrupted();Thread worker=owned.get();boolean stopped=false;
            try {
                if(worker!=null)worker.join(30_000);stopped=worker==null||!worker.isAlive();
                if(budget!=null)write(root.resolve("compute-process.json"),map("timeout_seconds",timeout,"owned_thread",worker==null?null:worker.getName(),
                    "owned_thread_stopped",stopped,"deadline_exceeded",deadline,"calculation_returned",returned,"elapsed_nanoseconds",System.nanoTime()-started));
                require(stopped,"Owned native thread has not stopped; no result admitted. Preserve this process/capture for diagnosis.");
            }catch(InterruptedException again){Thread.currentThread().interrupt();throw new InterruptedIOException("Interrupted while closing the owned native calculation");}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    private static Evaluated compute(Path root,Map<String,Object> request,SmartpcaWorkflowTables.Budget budget)throws IOException {
        var data=SmartpcaWorkflowData.read(root,request);var options=SmartpcaWorkflowData.options(request);
        if(budget!=null){budget.json(root.resolve("input-summary.json"),data.summary());SmartpcaWorkflowTables.inputSamples(root,data,budget);}
        var analysis=SmartpcaAnalysis.analyze(data.calls(),data.basisIndices(),options,pass->{
            if(budget!=null)try{budget.json(root.resolve(String.format(java.util.Locale.ROOT,"passes/%04d.json",pass.iteration())),pass(pass));}
            catch(IOException failed){throw new UncheckedIOException(failed);}
        });
        boolean invalidOutlier=analysis.passes().stream().anyMatch(SmartpcaAnalysis.Pass::nonfiniteOutlierDiagnostics);
        boolean allow=Boolean.TRUE.equals(object(request.get("policies")).get("allow_unreportable_fields"));
        var admissions=new ArrayList<Boolean>();int admitted=0,conditional=0,sourceReturnedRemoved=0,noObserved=0;
        int[] observed=analysis.observedUsedMarkers();
        for(int i=0;i<data.samples().size();i++) {
            var status=analysis.sampleStatus().get(i);boolean use=status==SmartpcaAnalysis.SampleStatus.COMPUTED&&observed[i]>0&&!invalidOutlier;
            admissions.add(use);if(use)admitted++;
            if(status==SmartpcaAnalysis.SampleStatus.INSUFFICIENT_NONMISSING_MARKERS||status==SmartpcaAnalysis.SampleStatus.REMOVED_OUTLIER)conditional++;
            if(status==SmartpcaAnalysis.SampleStatus.SOURCE_SHRINKAGE_OUTPUT_FOR_REMOVED_OUTLIER)sourceReturnedRemoved++;
            if(observed[i]==0)noObserved++;
        }
        var passes=new ArrayList<Object>();for(var value:analysis.passes())passes.add(pass(value));
        var removals=new ArrayList<Object>();for(var value:analysis.removals())removals.add(map("original_sample_ordinal",value.originalSampleIndex(),
            "sample_id",data.samples().get(value.originalSampleIndex()).id(),"iteration",value.iteration(),"zero_based_axis",value.zeroBasedAxis(),"signed_deviation",cell(value.signedDeviation())));
        var shrink=analysis.shrinkage();long badDelta=0,negativeMultiplier=0;
        if(shrink!=null)for(var value:shrink.perturbations()){if(value.originalBadDeltaBranch())badDelta++;if(value.multiplier()<0)negativeMultiplier++;}
        var summary=map("schema_version",1,"profile",SmartpcaWorkflowContracts.PROFILE,"input",data.summary(),"options",request.get("options"),
            "final_basis_indices",integers(analysis.basisIndices()),"final_panel_rows",analysis.markerIndices().length,"ordered_final_marker_ordinals",ordered(analysis.markerIndices()),
            "all_eigenvalues",cells(analysis.eigenvalues()),"requested_axes",options.pca().components(),"unrequested_eigenvalues_are_raw_numerical_diagnostics",true,
            "passes",passes,"removals",removals,"coordinate_chunk_rows",500,"coordinate_chunks",(data.samples().size()+499)/500,
            "coordinate_rows",data.samples().size(),"coordinate_admitted_rows",admitted,"conditionally_unestimated_or_removed_rows",conditional,
            "source_shrinkage_rows_for_removed_outliers",sourceReturnedRemoved,"rows_with_no_observed_used_markers",noObserved,
            "shrinkage",map("enabled",shrink!=null,"perturbations",shrink==null?0:shrink.perturbations().size(),"source_bad_delta_branches",badDelta,"negative_source_multipliers",negativeMultiplier),
            "unreportable_outlier_diagnostics",invalidOutlier,"allow_unreportable_fields",allow,"results_admitted",!invalidOutlier||allow,
            "original_C_executed",false,"whole_tool_qualified",false,"whole_paper_reproduced",false);
        return new Evaluated(data,analysis,summary,analysis.scores(),List.copyOf(admissions));
    }
    static Map<String,Object> pass(SmartpcaAnalysis.Pass value) {
        return map("iteration",value.iteration(),"basis_indices",integers(value.basisIndices()),"panel_rows",value.markerIndices().length,
            "ordered_panel_ordinals",ordered(value.markerIndices()),"used_markers",value.usedMarkers(),"all_eigenvalues",cells(value.eigenvalues()),
            "removed_original_sample_ordinals",value.removals().stream().map(SmartpcaAnalysis.Removal::originalSampleIndex).toList(),
            "nonfinite_outlier_diagnostics",value.nonfiniteOutlierDiagnostics());
    }
    static List<Object> cells(double[] values){var list=new ArrayList<Object>(values.length);for(double value:values)list.add(cell(value));return list;}
    static Object cell(double value){return Double.isFinite(value)?value:Double.isNaN(value)?"NaN":value>0?"Inf":"-Inf";}
    static List<Integer> integers(int[] values){return Arrays.stream(values).boxed().toList();}
    private static Map<String,Object> ordered(int[] values) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");var word=ByteBuffer.allocate(4);
            for(int value:values){word.clear();word.putInt(value);digest.update(word.array());}
            return map("count",values.length,"encoding","SIGNED_INT32_BIG_ENDIAN_ORIGINAL_ORDINALS","sha256",HexFormat.of().formatHex(digest.digest()));
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static void compare(Object saved,Object evaluated,String path)throws IOException {
        if(evaluated instanceof Map<?,?>) {
            var a=object(saved);var b=object(evaluated);require(a.keySet().equals(b.keySet()),"Scientific object fields differ: "+path);
            for(String key:b.keySet())compare(a.get(key),b.get(key),path+"."+key);return;
        }
        if(evaluated instanceof List<?>) {
            var a=list(saved);var b=list(evaluated);require(a.size()==b.size(),"Complete scientific population differs: "+path);
            for(int i=0;i<b.size();i++)compare(a.get(i),b.get(i),path+"["+i+"]");return;
        }
        if(evaluated instanceof Byte||evaluated instanceof Short||evaluated instanceof Integer||evaluated instanceof Long) {
            require(decimal(saved).compareTo(decimal(evaluated))==0,"Integer count/index differs: "+path);return;
        }
        if(evaluated instanceof Number||evaluated instanceof GprofilerJson.Numeric) {
            double a=decimal(saved).doubleValue(),b=decimal(evaluated).doubleValue();
            require(Double.isFinite(a)&&Double.isFinite(b),"Nonfinite cell mislabeled numeric: "+path);
            require(b==0?a==0:Math.abs(a-b)<=3e-12*Math.abs(b)+16*Math.ulp(b),"Finite scientific value differs: "+path);return;
        }
        require(java.util.Objects.equals(saved,evaluated),"Literal identifier/status/conditional field differs: "+path);
    }
}
