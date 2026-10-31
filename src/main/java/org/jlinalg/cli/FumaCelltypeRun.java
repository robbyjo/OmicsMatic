/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.FumaCelltypeAnalysis;
import org.jlinalg.enrichment.FumaDegEnrichment;
import org.jlinalg.genetics.MagmaTables;
import static org.jlinalg.cli.FollowupSupport.*;
import static org.jlinalg.enrichment.FumaCelltypeAnalysis.*;

/** Original MAGMA fits and independently implemented classic FUMA interpretation. */
final class FumaCelltypeRun {
    private final Path directory,raw;private final MagmaRuntime runtime;private final Map<Path,String> protectedFiles;
    private final List<String> warnings;private final Map<String,Object> session;private final FumaDegEnrichment.Check check;
    private final Limits limits;private final int timeout,maximumCalls;private final long maximumOutput;private long outputBytes,models;
    private final int geneCount;private final long maximumLedger,coverageRows;private final List<Map<String,Object>> calls=new ArrayList<>();private final List<String[]> fitLedger=new ArrayList<>(),joins=new ArrayList<>();
    FumaCelltypeRun(Path directory,Path raw,MagmaRuntime runtime,Map<Path,String> protectedFiles,List<String> warnings,Map<String,Object> session,Limits limits,FumaDegEnrichment.Check check,int timeout,int maximumCalls,long maximumOutput,int geneCount,long maximumLedger,long coverageRows) {
        this.directory=directory;this.raw=raw;this.runtime=runtime;this.protectedFiles=protectedFiles;this.warnings=warnings;this.session=session;this.limits=limits;this.check=check;this.timeout=timeout;this.maximumCalls=maximumCalls;this.maximumOutput=maximumOutput;this.geneCount=geneCount;this.maximumLedger=maximumLedger;this.coverageRows=coverageRows;session.put("model_calls",calls);
    }
    void run(List<FumaCelltypeInputs.Dataset> datasets,Map<String,Path> prepared,Correction correction,boolean step2,boolean step3)throws Exception {
        var marginal=new ArrayList<MarginalInput>();int index=0;
        for(var dataset:datasets) {
            String id=String.format(java.util.Locale.ROOT,"s1-d%04d",++index);var rows=call(id,"marginal",dataset.id(),"",prepared.get(dataset.id()),List.of("correct=all","condition-hide=Average","direction=greater"),dataset.cells().size());
            var seen=new HashSet<String>();for(var row:rows){String label=label(row);if(!dataset.cells().contains(label)||!seen.add(label))throw new IOException("Original marginal output has an unknown/repeated cell label");marginal.add(new MarginalInput(new Cell(dataset.id(),label),fit(row)));}
            if(!seen.equals(new HashSet<>(dataset.cells())))throw new IOException("Original marginal output omits requested cells; inspect native logs, missingness and collinearity");
        }
        List<Marginal> complete=FumaCelltypeAnalysis.marginal(marginal,correction,limits,check);writeMarginal(complete);session.put("complete_marginal_tests",complete.size());session.put("globally_significant_cells",complete.stream().filter(r->r.adjustedAcrossDatasets()<.05).count());
        if(!step2){session.put("step2_requested",false);session.put("step3_requested",false);finishLedgers();return;}
        session.put("step2_requested",true);var selected=new LinkedHashMap<String,List<String>>();for(var row:complete)if(row.adjustedAcrossDatasets()<.05)selected.computeIfAbsent(row.cell().dataset(),k->new ArrayList<>()).add(row.cell().label());
        long needed=0;for(var cells:selected.values())needed+=(long)cells.size()*(cells.size()-1)/2;capacity(needed);var withinPairs=new ArrayList<Pair>();var sorted=new ArrayList<>(selected.keySet());sorted.sort(String::compareTo);index=0;
        for(String dataset:sorted)if(selected.get(dataset).size()>1) {
            var cells=selected.get(dataset);var rows=call(String.format(java.util.Locale.ROOT,"s2-d%04d",++index),"within",dataset,"",prepared.get(dataset),List.of("correct=all","condition-hide=Average","direction=greater","analyse=list,"+String.join(",",cells),"joint-pairs"),(long)cells.size()*(cells.size()-1)/2);
            withinPairs.addAll(pairs(rows,dataset,Set.copyOf(cells),false));
        }
        WithinResult within=FumaCelltypeAnalysis.within(complete,withinPairs,limits,check);writeWithin(within);session.put("source_step2_summary_written",within.sourceSummaryWritten());session.put("step2_pair_models",within.pairs().size());session.put("step3_eligible_cells",within.summary().stream().filter(Summary::entersCrossDataset).count());
        if(!within.sourceSummaryWritten())warnings.add("SOURCE STEP2 SUMMARY GATE: the original does not write a conditional summary for fewer than two globally significant cells. This typed summary retains the explicit empty/singleton population. Required action: inspect step1.tsv and session.json; no missing pair fits are inferred.");warnings.addAll(within.diagnostics());
        if(!step3){session.put("step3_requested",false);finishLedgers();return;}
        session.put("step3_requested",true);var retained=new LinkedHashMap<String,List<String>>();for(var row:within.summary())if(row.entersCrossDataset())retained.computeIfAbsent(row.marginal().cell().dataset(),k->new ArrayList<>()).add(row.marginal().cell().label());
        var ids=new ArrayList<>(retained.keySet());var allDatasets=new LinkedHashMap<String,FumaCelltypeInputs.Dataset>();for(var dataset:datasets)allDatasets.put(dataset.id(),dataset);var crossPairs=new ArrayList<Pair>();var averages=new ArrayList<ConditionalAverage>();
        long projected=0;for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++){long cells=(long)retained.get(ids.get(i)).size()+retained.get(ids.get(j)).size();projected=Math.addExact(projected,Math.addExact(cells,cells*(cells-1)/2));}capacity(projected);if((long)calls.size()+(long)ids.size()*(ids.size()-1)>maximumCalls)throw new IOException("Complete cross-dataset call plan exceeds --max-calls");
        index=0;for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++) {
            check.run();String left=ids.get(i),right=ids.get(j);var joined=FumaCelltypeInputs.join(allDatasets.get(left),retained.get(left),allDatasets.get(right),retained.get(right),check);ledgerCapacity(joined.disposition().size());joins.addAll(joined.disposition());Path input=directory.resolve("prepared").resolve(String.format(java.util.Locale.ROOT,"cross-p%04d.tsv",++index));FumaCelltypeInputs.writeJoined(input,joined);protectedFiles.put(input,hash(input));
            var cells=new ArrayList<String>();for(String cell:retained.get(left))cells.add(left+":"+cell);for(String cell:retained.get(right))cells.add(right+":"+cell);
            String prefix=String.format(java.util.Locale.ROOT,"s3-p%04d",index);var rows=call(prefix+"-average","cross-average",left,right,input,List.of("condition-hide=Average1,Average2","direction=greater"),cells.size());var seen=new HashSet<Cell>();
            for(var row:rows){Cell cell=composite(label(row));if(!cells.contains(label(row))||!seen.add(cell))throw new IOException("Unknown/repeated conditional-average cell");averages.add(new ConditionalAverage(cell,cell.dataset().equals(left)?right:left,fit(row)));}if(seen.size()!=cells.size())throw new IOException("Original conditional-average output population differs");
            var rawPairs=call(prefix+"-pairs","cross-pairs",left,right,input,List.of("condition-hide=Average1,Average2","direction=greater","joint-pairs"),(long)cells.size()*(cells.size()-1)/2);crossPairs.addAll(pairs(rawPairs,null,Set.copyOf(cells),true));
        }
        CrossResult cross=FumaCelltypeAnalysis.cross(within,crossPairs,averages,limits,check);writeCross(cross);session.put("source_step3_gate_opened",cross.sourceGateOpened());session.put("step3_output_rows",cross.rows().size());long undefined=cross.rows().stream().filter(CrossRow::undefinedRatio).count();session.put("undefined_step3_ratios",undefined);
        if(!cross.sourceGateOpened())warnings.add("CROSS-DATASET ANALYSIS NOT RUN: fewer than two datasets retain eligible cells. Required action: inspect correction families and step2 states; this is an eligibility gate, not evidence of absent biological association.");
        if(undefined>0)warnings.add("UNDEFINED SOURCE PS RATIOS: "+undefined+" rows retain missing/zero-over-zero significance ratios. No lost log probabilities are reconstructed. Required action: inspect native P values and step3.tsv; do not use undefined ratios as conditional evidence.");finishLedgers();
    }
    private void capacity(long additional)throws IOException {if(additional<0||additional>limits.maximumModels()-models)throw new IOException("Complete original model population exceeds --max-models; no models are truncated");}
    private void ledgerCapacity(long additional)throws IOException {if(coverageRows+(long)fitLedger.size()+joins.size()+additional>maximumLedger)throw new IOException("Complete coverage/fit/join ledger exceeds --max-ledger-rows; no rows are truncated");}
    private List<Map<String,String>> call(String id,String phase,String left,String right,Path properties,List<String> modifiers,long count)throws Exception {
        check.run();capacity(count);ledgerCapacity(phase.equals("within")||phase.equals("cross-pairs")?2*count:count);if(calls.size()>=maximumCalls)throw new IOException("Complete model plan exceeds --max-calls");FumaCelltypeInputs.verify(protectedFiles);Path dir=directory.resolve("calls").resolve(id);Files.createDirectories(dir);var status=new LinkedHashMap<String,Object>();status.put("call",id);status.put("phase",phase);status.put("dataset",left);status.put("other_dataset",right);status.put("expected_models",count);status.put("status","running");calls.add(status);models+=count;
        var command=new ArrayList<>(List.of("--gene-results",raw.toString(),"--gene-covar",properties.toString()));if(phase.startsWith("cross-"))command.add("max-miss=0.1");command.add("--model");command.addAll(modifiers);command.addAll(List.of("--settings","gene-info","abbreviate=0","--out",dir.resolve("analysis").toString()));
        try {
            runtime.execute(command,dir,id,timeout);check.run();FumaCelltypeInputs.verify(protectedFiles);Path output=nativeOutput(dir);long bytes=0;try(var paths=Files.walk(dir)){for(Path file:paths.filter(Files::isRegularFile).toList()){bytes=Math.addExact(bytes,Files.size(file));}}outputBytes=Math.addExact(outputBytes,bytes);if(outputBytes>maximumOutput)throw new IOException("Completed original outputs exceed --max-output-bytes; retained files are partial diagnostics");
            var table=MagmaTables.table(output);if(!table.columns().containsAll(List.of("VARIABLE","TYPE","NGENES","BETA","BETA_STD","SE","P")))throw new IOException("Unexpected original MAGMA fit schema");var rows=table.rows();long expectedRows=phase.equals("within")||phase.equals("cross-pairs")?Math.multiplyExact(count,2):count;if(rows.size()!=expectedRows)throw new IOException("Original MAGMA fit population differs: expected "+expectedRows+" rows, observed "+rows.size());
            for(var row:rows){if(!"COVAR".equals(row.get("TYPE")))throw new IOException("Unexpected original tested variable type");fitLedger.add(new String[]{id,phase,left,right,row.getOrDefault("MODEL","NA"),label(row),row.get("NGENES"),row.get("BETA"),row.get("BETA_STD"),row.get("SE"),row.get("P")});}
            status.put("status","completed");status.put("fit_rows",rows.size());status.put("output_sha256",hash(output));status.put("native_output",directory.relativize(output).toString());status.put("output_bytes",bytes);return rows;
        }catch(Exception failure){status.put("status","failed");status.put("cause",failure.toString());throw failure;}finally {
            try {for(String line:Files.readAllLines(dir.resolve(id+".log"))){String lower=line.toLowerCase(java.util.Locale.ROOT);if(lower.contains("warning")||lower.stripLeading().startsWith("error"))warnings.add("UPSTREAM "+id+": "+line.strip()+(lower.contains("collinear")?" Required action: inspect duplicated/dependent cell properties and conditioning variables; choose a scientifically justified nonsingular model before rerunning. Failed models are not independent evidence.":""));}}
            catch(IOException unavailable){warnings.add("NATIVE LOG READ FAILED for "+id+": "+unavailable.getMessage()+". Required action: inspect the retained call directory and file permissions.");}
            finishLedgers();
        }
    }
    private List<Pair> pairs(List<Map<String,String>> rows,String dataset,Set<String> expected,boolean crossOnly)throws IOException {
        if(rows.size()%2!=0)throw new IOException("Incomplete source pair rows");var result=new ArrayList<Pair>();var ids=new HashSet<Integer>();var keys=new HashSet<Set<String>>();
        for(int i=0;i<rows.size();i+=2){var a=rows.get(i);var b=rows.get(i+1);if(!a.containsKey("MODEL")||!a.get("MODEL").equals(b.get("MODEL")))throw new IOException("Source pair row/model mismatch");int model=Integer.parseInt(a.get("MODEL"));String x=label(a),y=label(b);if(x.equals(y)||!expected.contains(x)||!expected.contains(y)||!ids.add(model)||!keys.add(Set.of(x,y)))throw new IOException("Unknown, repeated or incomplete source pair identity");Cell first=dataset==null?composite(x):new Cell(dataset,x),second=dataset==null?composite(y):new Cell(dataset,y);if(!crossOnly||!first.dataset().equals(second.dataset()))result.add(new Pair(model,first,fit(a),second,fit(b)));}return result;
    }
    private Fit fit(Map<String,String> row)throws IOException {int genes=row.get("NGENES").equals("NA")?-1:Integer.parseInt(row.get("NGENES"));if(genes>geneCount)throw new IOException("Original fit NGENES exceeds sealed gene universe");return new Fit(genes,n(row.get("BETA")),n(row.get("BETA_STD")),n(row.get("SE")),n(row.get("P")));}
    private static double n(String value)throws IOException {if(value.equals("NA")||value.equals("NaN"))return Double.NaN;return MagmaTables.number(value);}
    private static String label(Map<String,String> row){return row.getOrDefault("FULL_NAME",row.get("VARIABLE"));}
    private static Cell composite(String label)throws IOException {String[] values=label.split(":",-1);if(values.length!=2)throw new IOException("Ambiguous original dataset/cell composite label");return new Cell(values[0],values[1]);}
    private static Path nativeOutput(Path dir)throws IOException {Path a=dir.resolve("analysis.gsa.out.txt"),b=dir.resolve("analysis.gsa.out");if(Files.exists(a)&&Files.exists(b))throw new IOException("Ambiguous original output family");return RnaToolRuntime.file((Files.exists(a)?a:b).toString());}
    private void finishLedgers()throws IOException {table(directory.resolve("all-original-fits.tsv"),List.of("call","phase","dataset","other_dataset","source_model","full_label","NGENES","BETA","BETA_STD","SE","P"),fitLedger);table(directory.resolve("step3-joins.tsv"),List.of("left_dataset","right_dataset","gene","disposition"),joins);session.put("requested_original_models",models);session.put("completed_call_output_bytes",outputBytes);}
    private static String number(double value){return Double.isNaN(value)?"NA":value==Double.POSITIVE_INFINITY?"Inf":value==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(value);}
    private static void fitFields(List<String> fields,Fit fit){fields.add(fit.genes()<0?"NA":""+fit.genes());fields.add(number(fit.beta()));fields.add(number(fit.standardizedBeta()));fields.add(number(fit.se()));fields.add(number(fit.p()));}
    private void writeMarginal(List<Marginal> rows)throws IOException {var out=new ArrayList<String[]>();for(var row:rows){var f=new ArrayList<>(List.of(row.cell().dataset(),row.cell().label()));fitFields(f,row.fit());f.add(number(row.adjustedPerDataset()));f.add(number(row.adjustedAcrossDatasets()));out.add(f.toArray(String[]::new));}table(directory.resolve("step1.tsv"),List.of("Dataset","Cell_type","NGENES","BETA","BETA_STD","SE","P","P.adj.pds","P.adj"),out);}
    private void writeWithin(WithinResult within)throws IOException {
        var summary=new ArrayList<String[]>();var marginal=new LinkedHashMap<Cell,Double>();for(var row:within.summary()){var f=new ArrayList<>(List.of(row.marginal().cell().dataset(),row.marginal().cell().label()));fitFields(f,row.marginal().fit());f.add(number(row.marginal().adjustedPerDataset()));f.add(number(row.marginal().adjustedAcrossDatasets()));f.add(String.join(";",row.states()));f.add(row.partners().isEmpty()?"NA":String.join(";",row.partners().stream().map(Cell::label).toList()));f.add(row.entersCrossDataset()?"1":"0");summary.add(f.toArray(String[]::new));marginal.put(row.marginal().cell(),row.marginal().fit().p());}
        table(directory.resolve("step1-2-summary.tsv"),List.of("Dataset","Cell_type","NGENES","BETA","BETA_STD","SE","P","P.adj.pds","P.adj","cond_state","cond_cell_type","step3"),summary);var pairs=new ArrayList<String[]>();int model=0;for(var row:within.pairs()){model++;for(int side=0;side<2;side++){Cell cell=side==0?row.pair().first():row.pair().second();var f=new ArrayList<>(List.of(cell.dataset(),cell.label(),""+model));fitFields(f,side==0?row.pair().firstFit():row.pair().secondFit());f.add(number(marginal.get(cell)));f.add(number(side==0?row.firstPS():row.secondPS()));pairs.add(f.toArray(String[]::new));}}
        table(directory.resolve("step2.tsv"),List.of("Dataset","Cell_type","MODEL","NGENES","BETA","BETA_STD","SE","P","Marginal.P","PS"),pairs);
    }
    private void writeCross(CrossResult cross)throws IOException {var rows=new ArrayList<String[]>();for(var row:cross.rows()){var f=new ArrayList<>(List.of(row.cell().dataset(),row.cell().label(),""+row.model()));fitFields(f,row.conditional());Fit average=row.conditionalAverage();f.add(average==null?"NA":number(average.beta()));f.add(average==null?"NA":number(average.standardizedBeta()));f.add(average==null?"NA":number(average.se()));f.add(average==null?"NA":number(average.p()));f.add(average==null?"NA":row.otherDataset());f.add(number(row.marginalP()));f.add(number(row.ps()));f.add(number(row.averagePS()));rows.add(f.toArray(String[]::new));}table(directory.resolve("step3.tsv"),List.of("Dataset","Cell_type","MODEL","NGENES","BETA","BETA_STD","SE","P","CDM.BETA","CDM.BETA_STD","CDM.SE","CDM.P","CDM.ds","Marginal.P","PS","PS.avg"),rows);}
}
