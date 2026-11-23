/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.enrichment.PyScenicAucell;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Stream activity rows through the qualified native kernel without densifying all cells. */
final class ScenicWorkflowActivity {
    private ScenicWorkflowActivity(){ }
    private static final java.util.regex.Pattern INPUT_NUMBER=java.util.regex.Pattern.compile("(?:[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|NaN|[+-]?Infinity)");
    private record Batch(PyScenicAucell.Ranked ranked,PyScenicAucell.Result result) { }
    /** Only this owner's math thread is interrupted; the native kernels poll interruption. */
    static final class MathOwner implements AutoCloseable {
        private final java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor(task->new Thread(task,"scenic-native-batch"));
        <T>T execute(java.util.concurrent.Callable<T> calculation,long deadline)throws IOException {
            long remaining=deadline-System.nanoTime();require(remaining>0,"Native batch deadline expired before calculation");var future=executor.submit(calculation);
            try{return future.get(remaining,java.util.concurrent.TimeUnit.NANOSECONDS);}
            catch(java.util.concurrent.TimeoutException error){future.cancel(true);throw new IOException("Native batch deadline expired; owned calculation interrupted, no result admitted",error);}
            catch(InterruptedException error){future.cancel(true);Thread.currentThread().interrupt();throw new java.io.InterruptedIOException("Native activity owner interrupted; partial capture retained");}
            catch(java.util.concurrent.ExecutionException error){throw new IOException("Native source calculation refused or exhausted resources: "+error.getCause(),error.getCause());}
        }
        @Override public void close()throws IOException {
            executor.shutdownNow();boolean interrupted=Thread.interrupted();
            try{require(executor.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS),"Owned native math thread did not close; retain the unadmitted partial capture");}
            catch(InterruptedException error){interrupted=true;throw new java.io.InterruptedIOException("Interrupted while closing owned native calculation");}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    static final class Lines implements AutoCloseable {
        private final Reader reader;private final int maximum;
        Lines(Path path,long maximum)throws IOException {this.maximum=Math.toIntExact(maximum);reader=new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
        String next()throws IOException {
            var line=new StringBuilder();for(int value;(value=reader.read())>=0;){
                if(value=='\n'){if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);String result=line.toString();require(result.getBytes(StandardCharsets.UTF_8).length<=maximum,"Expression line byte capacity exceeded");return result;}
                require(line.length()<maximum,"Expression line capacity exceeded");line.append((char)value);
            }
            require(line.isEmpty(),"Prepared expression TSV requires a final newline");return null;
        }
        @Override public void close()throws IOException{reader.close();}
    }
    static List<PyScenicAucell.Signature> signatures(Path root)throws IOException {
        var result=new ArrayList<PyScenicAucell.Signature>();var names=new HashSet<String>();
        try(var records=new Lines(root.resolve("worker/out/regulons.jsonl"),67_108_864)){
        for(String line;(line=records.next())!=null;){
            var row=object(org.jlinalg.gprofiler.GprofilerJson.read(line.getBytes(StandardCharsets.UTF_8)));String name=ScenicWorkflowContracts.label(row.get("name"));require(names.add(name),"Duplicate original regulon names");var weights=new LinkedHashMap<String,Double>();
            for(Object pair:list(row.get("gene_weights"))){var fields=list(pair);require(fields.size()==2,"Invalid original gene weight pair");String gene=ScenicWorkflowContracts.label(fields.get(0));require(weights.put(gene,numeric(fields.get(1)))==null,"Duplicate original regulon gene");}
            result.add(new PyScenicAucell.Signature(name,weights));
        }}return result;
    }
    static double numeric(Object value)throws IOException {
        if(value instanceof Map<?,?>){var tagged=object(value);keys(tagged,Set.of("numeric"));return switch(text(tagged.get("numeric"),"numeric tag")){case "NaN"->Double.NaN;case "Infinity"->Double.POSITIVE_INFINITY;case "-Infinity"->Double.NEGATIVE_INFINITY;default->throw new IOException("Unknown nonfinite source tag");};}
        return decimal(value).doubleValue();
    }
    static Object tagged(double value){return Double.isFinite(value)?value:map("numeric",Double.isNaN(value)?"NaN":value>0?"Infinity":"-Infinity");}
    static int unicodeCompare(String a,String b){var left=a.codePoints().iterator();var right=b.codePoints().iterator();while(left.hasNext()&&right.hasNext()){int compare=Integer.compare(left.nextInt(),right.nextInt());if(compare!=0)return compare;}return Boolean.compare(left.hasNext(),right.hasNext());}
    static Map<String,Object> compute(Path root,Map<String,Object> request,long deadline)throws IOException {
        Files.createDirectory(root.resolve("activity"));Path output=root.resolve("activity");
        long modelEstimate=Math.multiplyExact(Files.size(root.resolve("worker/out/regulons.jsonl")),12L);
        require(modelEstimate<ScenicWorkflowContracts.cap(request,"maximum_working_bytes"),"Parsed signature memory estimate exceeds declared numeric workspace; increase explicit capacity or retain this failure");
        var signatures=signatures(root);var activity=object(object(request.get("options")).get("activity"));
        if(signatures.isEmpty()){
            var result=map("profile",PyScenicAucell.PROFILE,"outcome","NO_SCORABLE_REGULONS","activity_computed",false,"cells",0,"regulons",0,"ineligible_fields",0,"results_admitted",false);
            write(output.resolve("SUMMARY.json"),result);return result;
        }
        long numericBudget=ScenicWorkflowContracts.cap(request,"maximum_working_bytes")-modelEstimate;
        var limits=new PyScenicAucell.Limits(ScenicWorkflowContracts.cap(request,"maximum_dense_entries"),ScenicWorkflowContracts.cap(request,"maximum_native_work"),numericBudget);
        var options=new PyScenicAucell.Options(decimal(activity.get("auc_threshold")).doubleValue(),(Boolean)activity.get("no_weights"),false,integer(activity.get("seed"),0,0xffff_ffffL),PyScenicAucell.Layout.valueOf(activity.get("layout").toString()),limits);
        var cells=new ArrayList<String>();var distinct=new HashSet<String>();List<String> regulons=null,genes;List<PyScenicAucell.Regulon> diagnostics=null;double[] maxima=null;boolean[] rawNonfinite=null;double work=0;int batches=0,effectiveBatch=0;
        try(var owner=new MathOwner();var input=new Lines(root.resolve("inputs/activity.tsv"),ScenicWorkflowContracts.cap(request,"maximum_line_bytes"));
                var scores=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output.resolve("raw-scores.bin"),StandardOpenOption.CREATE_NEW)));
                var ranks=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output.resolve("ranks.bin"),StandardOpenOption.CREATE_NEW)))){
            String first=input.next();require(first!=null,"Missing activity expression header");String[] header=first.split("\t",-1);require(header.length>=2&&header[0].equals("cell"),"Activity TSV header must start with cell");
            genes=new ArrayList<>();for(int i=1;i<header.length;i++)genes.add(ScenicWorkflowContracts.label(header[i]));require(new HashSet<>(genes).size()==genes.size()&&genes.size()<=ScenicWorkflowContracts.cap(request,"maximum_features"),"Duplicate activity genes or feature capacity exceeded");
            double rowWork=genes.size()*Math.max(1,Math.log(genes.size())/Math.log(2));for(var signature:signatures)rowWork+=signature.geneWeights().size()*Math.max(1,Math.log(signature.geneWeights().size())/Math.log(2));
            long rowBytes=Math.addExact(Math.multiplyExact((long)genes.size(),160),Math.addExact(Math.multiplyExact((long)signatures.size(),64),2048));
            effectiveBatch=Math.toIntExact(Math.min(ScenicWorkflowContracts.cap(request,"maximum_activity_batch_cells"),Math.min(numericBudget/rowBytes,limits.maxEntries()/((long)genes.size()+signatures.size()))));
            require(effectiveBatch>=1,"Declared workspace/entry capacity cannot hold even one activity row and the complete signature population");
            boolean exhausted=false;while(!exhausted){
                var batchCells=new ArrayList<String>();var expressions=new ArrayList<double[]>();
                for(int at=0;at<effectiveBatch;at++){
                    String line=input.next();if(line==null){exhausted=true;break;}
                    require(System.nanoTime()<=deadline&&!Thread.currentThread().isInterrupted(),"Native activity deadline expired or calculation interrupted");work+=rowWork;require(work<=ScenicWorkflowContracts.cap(request,"maximum_native_work"),"Complete native activity work estimate exceeds capacity");
                    String[] fields=line.split("\t",-1);require(fields.length==header.length,"Ragged activity expression row");String cell=ScenicWorkflowContracts.label(fields[0]);require(distinct.add(cell),"Duplicate literal activity cell");cells.add(cell);batchCells.add(cell);
                    require(cells.size()<=ScenicWorkflowContracts.cap(request,"maximum_cells")&&(long)cells.size()*signatures.size()<=ScenicWorkflowContracts.cap(request,"maximum_activity_entries"),"Activity output population exceeds explicit capacity");
                    double[] expression=new double[genes.size()];for(int i=0;i<expression.length;i++)expression[i]=inputNumber(fields[i+1]);expressions.add(expression);
                }
                if(batchCells.isEmpty())break;
                work+=(double)genes.size()*signatures.size();require(work<=ScenicWorkflowContracts.cap(request,"maximum_native_work"),"Complete activity preparation/scoring work estimate exceeds capacity");batches++;
                require(Math.multiplyExact(Math.multiplyExact((long)cells.size(),genes.size()),4L)<=ScenicWorkflowContracts.cap(request,"maximum_member_bytes")&&Math.multiplyExact(Math.multiplyExact((long)cells.size(),signatures.size()),8L)<=ScenicWorkflowContracts.cap(request,"maximum_member_bytes"),"Complete rank/score binary exceeds declared member capacity");
                List<String> batchGenes=genes;double[][] batchExpression=expressions.toArray(double[][]::new);
                var calculated=owner.execute(()->{var ranked=PyScenicAucell.rank(batchCells,batchGenes,batchExpression,options.seed(),limits);return new Batch(ranked,PyScenicAucell.score(ranked,signatures,options));},deadline);
                var ranked=calculated.ranked();var result=calculated.result();
                if(regulons==null){
                    regulons=result.regulons();diagnostics=result.diagnostics();maxima=new double[regulons.size()];rawNonfinite=new boolean[regulons.size()];Arrays.fill(maxima,Double.NEGATIVE_INFINITY);
                    var permutation=new ArrayList<Object>();for(int index:ranked.permutation())permutation.add(index);
                    write(output.resolve("rank-axes.json"),map("dtype","big-endian-int32","zero_based",true,"gene_axis",ranked.genes(),"input_gene_permutation",permutation,"cell_axis","input_activity_order"));
                }else require(regulons.equals(result.regulons()),"Native regulon order changed between batches");
                for(int[] row:ranked.ranks())for(int rank:row)ranks.writeInt(rank);
                var positions=new HashMap<String,Integer>();for(int row=0;row<result.cells().size();row++)positions.put(result.cells().get(row),row);double[][] batchRaw=result.raw();
                for(String cell:batchCells){double[] values=batchRaw[positions.get(cell)];for(int col=0;col<values.length;col++){scores.writeDouble(values[col]);maxima[col]=Math.max(maxima[col],values[col]);rawNonfinite[col]|=!Double.isFinite(values[col]);}}
                ScenicWorkflowProcess.footprint(root,request);
            }
        }
        require(!cells.isEmpty()&&regulons!=null&&diagnostics!=null&&maxima!=null&&rawNonfinite!=null,"No activity cells were supplied");
        write(output.resolve("input-cell-axis.json"),cells);var order=new ArrayList<Integer>();for(int row=0;row<cells.size();row++)order.add(row);
        if(options.layout()==PyScenicAucell.Layout.SERIAL_UNSTACK)order.sort((a,b)->unicodeCompare(cells.get(a),cells.get(b)));
        boolean normalize=(Boolean)activity.get("normalize");long ineligible=0;boolean[] nonfinite=new boolean[regulons.size()];
        try(var raw=new RandomAccessFile(output.resolve("raw-scores.bin").toFile(),"r");
                var table=Files.newBufferedWriter(output.resolve("scores.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            table.write("cell");for(String name:regulons){table.write('\t');table.write(name);}table.write('\n');
            for(int row:order){
                require(System.nanoTime()<=deadline&&!Thread.currentThread().isInterrupted(),"Native activity deadline expired during final export");raw.seek(Math.multiplyExact(Math.multiplyExact((long)row,regulons.size()),8L));table.write(cells.get(row));
                for(int col=0;col<regulons.size();col++){
                    double score=raw.readDouble();if(normalize)score/=maxima[col];nonfinite[col]|=!Double.isFinite(score);
                    table.write('\t');table.write(Double.toString(score));
                }table.write('\n');
            }
        }
        var records=new ArrayList<Object>();var eligibleColumns=new boolean[regulons.size()];for(int col=0;col<regulons.size();col++){
            var d=diagnostics.get(col);var rawStatus=d.cutoffApplied()&&rawNonfinite[col]?PyScenicAucell.Status.NONFINITE_NORMALIZATION:d.status();var status=d.cutoffApplied()&&nonfinite[col]?PyScenicAucell.Status.NONFINITE_NORMALIZATION:rawStatus;
            eligibleColumns[col]=status==PyScenicAucell.Status.COMPUTED;if(!eligibleColumns[col])ineligible=Math.addExact(ineligible,cells.size());
            records.add(map("name",d.name(),"signature_genes",d.signatureGenes(),"present_genes",d.presentGenes(),"coverage",d.coverage(),"cutoff_applied",d.cutoffApplied(),
                "reduced_rank_cutoff",d.reducedRankCutoff(),"raw_column_maximum",tagged(maxima[col]),"negative_effective_weights",d.negativeEffectiveWeights(),"raw_status",rawStatus.name(),"status",status.name()));
        }
        try(var flags=new BufferedOutputStream(Files.newOutputStream(output.resolve("eligibility.bin"),StandardOpenOption.CREATE_NEW))){for(int row:order)for(boolean eligible:eligibleColumns)flags.write(eligible?1:0);}
        var orderedCells=new ArrayList<String>();for(int row:order)orderedCells.add(cells.get(row));
        write(output.resolve("axes.json"),map("cells",orderedCells,"regulons",regulons,"raw_score_dtype","big-endian-float64","raw_cell_axis","input-cell-axis.json","eligibility_dtype","uint8 0=ineligible 1=eligible","eligibility_axes","scores.tsv row/column order","normalize",normalize,"layout",options.layout().name()));
        write(output.resolve("diagnostics.json"),map("regulons",records));
        var result=map("profile",PyScenicAucell.PROFILE,"outcome",ineligible==0?"ACTIVITY_NUMERICALLY_ELIGIBLE":"ACTIVITY_HAS_UNREPORTABLE_FIELDS","activity_computed",true,"cells",cells.size(),"genes",genes.size(),"regulons",regulons.size(),"ineligible_fields",ineligible,"conservative_native_work",work,"effective_batch_cells",effectiveBatch,"numeric_batches",batches,"results_admitted",false);
        write(output.resolve("SUMMARY.json"),result);ScenicWorkflowProcess.footprint(root,request);return result;
    }
    static double inputNumber(String value)throws IOException {
        require(INPUT_NUMBER.matcher(value).matches(),"Require a literal decimal, NaN or signed Infinity; numeric whitespace, hexadecimal and shorthand inf are not accepted");
        double parsed=Double.parseDouble(value);require(Double.isFinite(parsed)||Set.of("NaN","Infinity","+Infinity","-Infinity").contains(value),"Finite decimal overflows FP64; supply explicit Infinity if intended");return parsed;
    }
}
