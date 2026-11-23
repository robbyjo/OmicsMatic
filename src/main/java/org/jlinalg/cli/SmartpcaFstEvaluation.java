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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.jlinalg.genetics.SmartpcaFst;
import org.jlinalg.genetics.SmartpcaPrepared;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;

/** Owned native FST calculation with streaming contribution verification and explicit admission. */
final class SmartpcaFstEvaluation {
    private SmartpcaFstEvaluation() { }
    record Evaluated(SmartpcaWorkflowData.Prepared data,List<String> populations,int[] samplePopulation,boolean[] contributing,SmartpcaFst.Result analysis,Map<String,Object> result,Map<String,Object> inputSummary,Map<String,Object> locusWrittenIdentity) { }
    static Evaluated evaluate(Path root,Map<String,Object> request,SmartpcaWorkflowTables.Budget budget)throws IOException {
        long timeout=SmartpcaWorkflowContracts.limit(request,"timeout_seconds"),start=System.nanoTime();var owned=new AtomicReference<Thread>();
        var pool=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"omicsmatic-smartpca-fst-"+java.util.UUID.randomUUID());owned.set(thread);return thread;});
        var future=pool.submit(()->compute(root,request,budget));boolean expired=false,returned=false;
        try{var value=future.get(timeout,TimeUnit.SECONDS);require(System.nanoTime()-start<=TimeUnit.SECONDS.toNanos(timeout),"Native FST calculation exceeded final deadline");returned=true;return value;}
        catch(TimeoutException error){expired=true;future.cancel(true);throw new IOException("Native FST read/calculation deadline exceeded; preserve partial diagnostics and retry NEW output with appropriate explicit resources",error);}
        catch(InterruptedException error){future.cancel(true);Thread.currentThread().interrupt();throw new InterruptedIOException("Native FST interrupted; no complete result");}
        catch(ExecutionException error){throw new IOException("Native source FST refused: "+error.getCause(),error.getCause());}
        finally {
            pool.shutdownNow();boolean interrupted=Thread.interrupted();Thread thread=owned.get();
            try{if(thread!=null)thread.join(30_000);boolean stopped=thread==null||!thread.isAlive();
                if(budget!=null)write(root.resolve("compute-process.json"),map("timeout_seconds",timeout,"owned_thread",thread==null?null:thread.getName(),"owned_thread_stopped",stopped,"deadline_exceeded",expired,"calculation_returned",returned,"elapsed_nanoseconds",System.nanoTime()-start));
                require(stopped,"Owned FST worker has not stopped; preserve this process/capture for diagnosis; no complete result");
            }catch(InterruptedException error){Thread.currentThread().interrupt();throw new InterruptedIOException("Interrupted while closing native FST owner");}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    private static Evaluated compute(Path root,Map<String,Object> request,SmartpcaWorkflowTables.Budget budget)throws IOException {
        var data=SmartpcaFstContracts.data(root,request);var names=SmartpcaFstContracts.populations(data,request);int[] population=new int[data.samples().size()];
        for(var sample:data.samples())population[sample.ordinal()]=names.indexOf(sample.population());
        var positions=new ArrayList<SmartpcaFst.Position>();
        for(var marker:data.markers()) {
            require(marker.physicalPosition().matches("[0-9]+"),"Source FST physical positions require literal nonnegative int32 decimal integers; no floating-point/atoi coercion");
            int physical;try{physical=Integer.parseInt(marker.physicalPosition());}catch(NumberFormatException error){throw new IOException("Source FST physical position exceeds int32: "+marker.id(),error);}
            positions.add(new SmartpcaFst.Position(marker.chromosome(),Double.parseDouble(marker.geneticPosition()),physical));
        }
        // Raw non-autosomal calls remain captured and validated. Source excludes
        // them before counting; dummy rows supply no scientific observations.
        byte[] excluded=new byte[population.length];Arrays.fill(excluded,(byte)9);byte[][] full=new byte[positions.size()][];int active=0;
        for(var marker:data.markers())full[marker.ordinal()]=marker.autosomal()?data.calls()[active++]:excluded;
        var input=new java.util.LinkedHashMap<>(data.summary());input.put("populations",input.remove("reference_populations"));input.put("initial_selected_samples",input.remove("initial_basis_samples"));input.put("PCA_marker_filters_applied",false);
        if(budget!=null)budget.json(root.resolve("input-summary.json"),input);
        var options=object(request.get("options"));var bounds=new SmartpcaPrepared.Limits(SmartpcaWorkflowContracts.limit(request,"maximum_estimated_work"),SmartpcaWorkflowContracts.limit(request,"maximum_retained_bytes"));
        SmartpcaFst.Result analysis;Map<String,Object> locusIdentity;
        try(var sink=SmartpcaFstTables.loci(root,data,names,budget)) {
            analysis=SmartpcaFst.fit(full,population,names.size(),positions,new SmartpcaFst.Options((Boolean)options.get("inbreed"),decimal(options.get("blgsize")).doubleValue(),bounds),locus->{try{sink.accept(locus);}catch(IOException error){throw new UncheckedIOException(error);}});
            locusIdentity=budget==null?null:sink.identity();
        }
        boolean[] contributing=new boolean[population.length];
        for(var marker:analysis.markers())if(marker.status()==SmartpcaFst.MarkerStatus.USED)for(int i=0;i<population.length;i++)if(population[i]>=0&&full[marker.ordinal()][i]!=9)contributing[i]=true;
        var pairs=new ArrayList<Object>();var inbreeding=new ArrayList<Object>();long invalid=0,negative=0,zeroSE=0;
        for(var estimate:analysis.pairs()){pairs.add(estimate(estimate,names));if(estimate.admission()!=SmartpcaFst.Admission.COMPUTED)invalid++;if(estimate.estimate()<0)negative++;if(estimate.standardError()==0)zeroSE++;}
        for(var estimate:analysis.inbreeding()){inbreeding.add(estimate(estimate,names));if(estimate.admission()!=SmartpcaFst.Admission.COMPUTED)invalid++;}
        boolean allow=Boolean.TRUE.equals(object(request.get("policies")).get("allow_unreportable_fields"));
        var result=map("schema_version",1,"profile",SmartpcaFstContracts.PROFILE,"input",input,"options",options,"populations",names,"position_convention",analysis.positionConvention().name(),
            "blocks",ordered(analysis.blockMarkerCounts()),"selected_contributing_samples_by_population",Arrays.stream(analysis.selectedContributingSamplesByPopulation()).boxed().toList(),
            "retained_markers",analysis.markers().stream().filter(m->m.status()==SmartpcaFst.MarkerStatus.USED).count(),"pairs",pairs,"inbreeding",inbreeding,
            "unreportable_estimate_rows",invalid,"negative_FST_estimates_retained",negative,"zero_source_standard_errors",zeroSE,"allow_unreportable_fields",allow,"results_admitted",invalid==0||allow,
            "source_epsilon",1e-10,"global_marker_count_block_weights",true,"original_C_executed",false,"whole_tool_qualified",false,"whole_paper_reproduced",false);
        return new Evaluated(data,names,population,contributing,analysis,result,input,locusIdentity);
    }
    static Map<String,Object> estimate(SmartpcaFst.Estimate e,List<String> names) {
        return map("first_population_ordinal",e.firstPopulation(),"second_population_ordinal",e.secondPopulation(),"first_population",names.get(e.firstPopulation()),"second_population",names.get(e.secondPopulation()),
            "marker_order_ratio",cell(e.markerOrderRatio()),"block_order_ratio",cell(e.blockOrderRatio()),"estimate",cell(e.estimate()),"standard_error",cell(e.standardError()),"total_numerator",cell(e.totalNumerator()),"total_denominator",cell(e.totalDenominator()),
            "valid_loci",e.validLoci(),"positive_denominator_loci",e.positiveDenominatorLoci(),"source_status",e.admission().name(),"fields_admitted",e.admission()==SmartpcaFst.Admission.COMPUTED);
    }
    static Object cell(double value){return SmartpcaWorkflowEvaluation.cell(value);}
    private static Map<String,Object> ordered(int[] values) {
        try{var digest=MessageDigest.getInstance("SHA-256");var word=ByteBuffer.allocate(4);for(int value:values){word.clear();word.putInt(value);digest.update(word.array());}return map("count",values.length,"encoding","SIGNED_INT32_BIG_ENDIAN_MARKER_COUNTS","sha256",java.util.HexFormat.of().formatHex(digest.digest()));}
        catch(NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
}
