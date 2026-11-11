/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import org.jlinalg.shrinkage.MashFit;
import org.jlinalg.shrinkage.MashGaussian;
import org.jlinalg.shrinkage.MashMixtureWeights;

/**
 * Development file executor for native mash fitting and prediction. All populations,
 * scales and optimizer choices are explicit. This is not public study-bundle admission.
 */
public final class MashNativeExecution {
    private MashNativeExecution() { }
    public record Limits(int rows,long inputBytes,long outputBytes,MashFit.Limits fit) {
        public Limits {if(rows<1||inputBytes<1024||outputBytes<1024||fit==null)throw new IllegalArgumentException("Invalid native execution limits");}
        public static Limits defaults(){return new Limits(100_000,16_777_216,134_217_728,MashFit.Limits.defaults());}
    }
    public record Result(String operation,int trainingRows,int targetRows,int conditions,int components,
                         String fitStatus,long gaussianWork,long weightCells) { }
    private record Population(String id,boolean train,boolean strong,boolean target) { }
    private record Data(List<String> conditions,List<Population> populations,double[][] effects,double[][] errors) { }
    private record Pin(long bytes,String sha256) { }
    private static final Set<String> GAUSSIAN=Set.of("alpha","posterior_threshold","covariance_tolerance","minimum_se","numeric_sensitivity");
    private static final String SCOPE="DEVELOPMENT ONLY: native plain EM and explicit fully expanded models. PCA/extreme-deconvolution learning, public runtime/bundle admission and whole-paper reproduction are not provided by this executor.";
    private static final String RECOVERY="Required action: preserve this output and its partial diagnostics. Correct the named input, numerical, convergence or capacity failure; increase only permitted limits if necessary. Retry into a NEW output directory. No partial analysis is complete.";

    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream stdout,PrintStream stderr){
        try {
            if(args.length!=4||!args[0].equals("--input")||!args[2].equals("--out"))throw new IllegalArgumentException("Usage: MashNativeExecution --input DIRECTORY --out NEW_DIRECTORY");
            Result result=execute(Path.of(args[1]),Path.of(args[3]),Limits.defaults(),()->{if(Thread.currentThread().isInterrupted())throw new CancellationException("Native mash execution interrupted");});
            stdout.println("NATIVE_MASH_DEVELOPMENT_COMPLETE: "+result.trainingRows()+" training rows, "+result.targetRows()+" target rows; "+result.fitStatus());stderr.println(SCOPE);return 0;
        } catch(Exception error){stderr.println("FAIL2_NATIVE_MASH: "+message(error));stderr.println(RECOVERY);return 2;}
    }

    /** A fresh directory is mandatory. Failed attempts retain inputs and completed diagnostics. */
    public static Result execute(Path input,Path output,Limits limits,Runnable check)throws IOException {
        input=input.toAbsolutePath().normalize();output=output.toAbsolutePath().normalize();
        if(output.startsWith(input)||input.startsWith(output))throw new IOException("Input and output directories must not overlap");
        noLinks(input);noLinks(output.getParent());
        if(!Files.isDirectory(input,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing native input directory");
        Files.createDirectories(output.getParent());Files.createDirectory(output);var sink=new Sink(output,limits.outputBytes(),check);
        try {
            sink.text("WARNINGS.md","# Native mash development execution\n\n"+SCOPE+"\n");
            Map<String,Pin> original=inventory(input,limits.inputBytes(),check);Path snapshot=output.resolve("inputs");Files.createDirectory(snapshot);
            for(String name:original.keySet()){check.run();Path target=snapshot.resolve(name);Files.createDirectories(target.getParent());sink.reserve(original.get(name).bytes());Files.copy(input.resolve(name),target);}
            if(!original.equals(inventory(snapshot,limits.inputBytes(),check)))throw new IOException("Input changed while its complete snapshot was copied");
            Map<String,String> settings=settings(snapshot.resolve("settings.tsv"));
            var consumed=new java.util.HashSet<>(Set.of("settings.tsv","effects.tsv","standard-errors.tsv","populations.tsv"));
            if(!"2".equals(settings.get("schema_version")))throw new IOException("Require native settings schema_version=2; original mashr optimizer settings cannot be silently reinterpreted");
            String operation=required(settings,"operation");boolean fit=operation.equals("fit_predict");
            if(!fit&&!operation.equals("predict"))throw new IOException("operation must be fit_predict or predict");
            var allowed=new java.util.HashSet<>(Set.of("schema_version","operation"));
            if(fit){allowed.addAll(GAUSSIAN);allowed.addAll(Set.of("basis","grid","normalize","point_mass","prior","optimizer","weight_maxiter","weight_dual_gap","residual","residual_maxiter","residual_tolerance"));
                if("simple".equals(settings.get("residual")))allowed.addAll(Set.of("null_z_threshold","null_correlation"));}
            if(!settings.keySet().equals(allowed))throw new IOException("Native settings have missing or unexpected keys: expected "+new java.util.TreeSet<>(allowed));
            Data data=data(snapshot,limits,check);int n=data.conditions().size();
            int training=(int)data.populations().stream().filter(Population::train).count(),targets=(int)data.populations().stream().filter(Population::target).count();
            if(targets==0||fit&&training==0)throw new IOException("Require every requested target and, for fitting, at least one explicit training row");
            if(!fit&&data.populations().stream().anyMatch(p->p.train()||p.strong()))throw new IOException("Prediction cannot silently ignore training or strong flags; request only target rows");
            writeIds(sink,data);MashGaussian.Options gaussian;MashGaussian.Basis expanded;double[] proportions;double[][] residual;
            MashGaussian.Model model;String fitStatus="IMPORTED_EXPANDED_MODEL";long gaussianWork,weightCells=0;
            var fitting=limits.fit();
            if(fit){
                gaussian=gaussian(settings);String kind=required(settings,"basis");MashGaussian.Basis basis;
                if(kind.equals("canonical"))basis=MashGaussian.canonical(data.conditions(),fitting.gaussian(),check);
                else if(kind.equals("supplied")){basis=basis(snapshot.resolve("basis"),n,limits,data.populations().size(),check);consumed.add("basis/component-names.tsv");for(int i=1;i<=basis.names().size();i++)consumed.add("basis/component-"+i+".tsv");}
                else throw new IOException("Fit basis must be canonical or supplied; a fully expanded model is used only by predict");
                expanded=MashFit.expand(basis,grid(required(settings,"grid")),bool(settings,"normalize"),bool(settings,"point_mass"),fitting,check);
                int k=expanded.names().size();completeCapacity(data.populations().size(),n,k,limits);
                if(!"native_em".equals(settings.get("optimizer")))throw new IOException("This executor requires optimizer=native_em; upstream mixSQP/SQUAREM is not silently substituted");
                double[] prior=new double[k];Arrays.fill(prior,1);switch(required(settings,"prior")){
                    case "uniform" -> { }
                    case "nullbiased" -> {if(!bool(settings,"point_mass"))throw new IOException("nullbiased prior requires explicit point_mass=true");prior[0]=10;}
                    case "supplied" -> {prior=vector(snapshot.resolve("prior.tsv"),k);consumed.add("prior.tsv");}
                    default -> throw new IOException("prior must be uniform, nullbiased or supplied");
                }
                String residualMode=required(settings,"residual");residual=null;
                double[][] trainEffects=select(data.effects(),data.populations(),true),trainErrors=select(data.errors(),data.populations(),true);
                if(residualMode.equals("simple")){
                    var estimate=MashFit.estimateNull(trainEffects,trainErrors,number(settings,"null_z_threshold"),bool(settings,"null_correlation"),fitting,check);residual=estimate.covariance();
                    var trainingPopulation=data.populations().stream().filter(Population::train).toList();var ids=new ArrayList<String>();for(int row:estimate.selectedRows())ids.add(trainingPopulation.get(row).id());sink.lines("fit/null-selected-ids.tsv",ids);
                    sink.matrix("fit/null-estimated-residual.tsv",residual);
                } else {residual=matrix(snapshot.resolve("residual.tsv"),n,n);consumed.add("residual.tsv");}
                if(!consumed.equals(original.keySet()))throw new IOException("Unexpected/unconsumed input files; every supplied input must have an explicit role");
                MashFit.ResidualMode mode=switch(residualMode){case "fixed","simple" -> MashFit.ResidualMode.FIXED;case "em_correlation" -> MashFit.ResidualMode.EM_CORRELATION;case "em_covariance" -> MashFit.ResidualMode.EM_COVARIANCE;default -> throw new IOException("Unknown explicit residual mode");};
                var options=new MashFit.Options(gaussian,new MashMixtureWeights.Options(integer(settings,"weight_maxiter"),number(settings,"weight_dual_gap"),true),mode,integer(settings,"residual_maxiter"),number(settings,"residual_tolerance"));
                long reserved=work(n,k,training+(long)targets,0);if(reserved>=fitting.totalGaussianWork())throw new IOException("Complete native training/prediction exceeds the Gaussian work budget");
                var fitLimits=new MashFit.Limits(fitting.rows(),fitting.gaussian(),fitting.weights(),fitting.cells(),fitting.ownedBytes(),fitting.totalGaussianWork()-reserved,fitting.totalWeightCells());
                var expandedMatrices=expanded.covariances();sink.lines("fit/expanded-component-names.tsv",expanded.names());for(int i=0;i<k;i++)sink.matrix("fit/expanded-component-"+(i+1)+".tsv",expandedMatrices.get(i));sink.vector("fit/dirichlet.tsv",prior);
                if(data.populations().stream().anyMatch(Population::strong))sink.warning("STRONG_POPULATION_RECORDED: strong rows are recorded, but this native execution uses only canonical or explicitly supplied covariance bases. It does not learn PCA/ED covariances.");
                var listener=new Evidence(sink);MashFit.Fit fitted;
                try {fitted=MashFit.fit(trainEffects,trainErrors,expanded.covariances(),prior,residual,options,fitLimits,listener,check);}catch(EvidenceFailure error){throw error.io;}
                sink.text("fit/final.tsv","key\tvalue\nstatus\t"+fitted.status()+"\nproposals\t"+fitted.proposals()+"\naccepted_proposals\t"+fitted.acceptedProposals()+"\nlog_likelihood\t"+fitted.logLikelihood()+"\ngaussian_work\t"+fitted.gaussianWork()+"\nweight_cells\t"+fitted.weightCells()+"\n");
                for(String note:fitted.notes())sink.warning(note);fitted.requireUsable();
                model=fitted.model();residual=fitted.residual();proportions=model.proportions();fitStatus=fitted.status().name();gaussianWork=Math.addExact(fitted.gaussianWork(),reserved);weightCells=fitted.weightCells();
                exportModel(sink,data.conditions(),expanded,residual,proportions,gaussian,check);
            } else {
                Path imported=snapshot.resolve("model");verifyManifest(imported,check);
                if(!settings(imported.resolve("representation.tsv")).equals(Map.of("schema_version","1","representation","fully_expanded")))throw new IOException("Require explicitly fully_expanded model representation; no scale expansion is inferred");
                if(!labels(imported.resolve("conditions.tsv"),fitting.gaussian().conditions(),true).equals(data.conditions()))throw new IOException("Imported model condition identities/order differ from the effects");
                Map<String,String> options=settings(imported.resolve("settings.tsv"));if(!options.keySet().equals(GAUSSIAN))throw new IOException("Unexpected/missing imported Gaussian settings");gaussian=gaussian(options);
                expanded=basis(imported,n,limits,data.populations().size(),check);int k=expanded.names().size();completeCapacity(data.populations().size(),n,k,limits);proportions=vector(imported.resolve("proportions.tsv"),k);residual=matrix(imported.resolve("residual.tsv"),n,n);
                for(String name:List.of("representation.tsv","conditions.tsv","component-names.tsv","settings.tsv","proportions.tsv","residual.tsv","SHA256SUMS"))consumed.add("model/"+name);for(int i=1;i<=k;i++)consumed.add("model/component-"+i+".tsv");
                if(!consumed.equals(original.keySet()))throw new IOException("Unexpected/unconsumed input files in the fully expanded prediction request");
                gaussianWork=work(n,k,targets,1);if(gaussianWork>fitting.totalGaussianWork())throw new IOException("Complete native prediction exceeds Gaussian work budget");
                model=MashGaussian.prepare(expanded.covariances(),proportions,residual,gaussian,fitting.gaussian(),check);
                exportModel(sink,data.conditions(),expanded,residual,model.proportions(),gaussian,check);
            }
            posterior(sink,data,model,check);
            if(!original.equals(inventory(input,limits.inputBytes(),check)))throw new IOException("Original inputs changed during execution; the retained snapshot is not admitted as a completed request");
            Result result=new Result(operation,training,targets,n,expanded.names().size(),fitStatus,gaussianWork,weightCells);
            sink.text("result.tsv","key\tvalue\nstatus\tNATIVE_MASH_DEVELOPMENT_COMPLETE\noperation\t"+operation+"\ntraining_rows\t"+training+"\ntarget_rows\t"+targets+"\nconditions\t"+n+"\ncomponents\t"+expanded.names().size()+"\nfit_status\t"+fitStatus+"\ngaussian_work\t"+gaussianWork+"\nweight_cells\t"+weightCells+"\npublic_bundle_admitted\tfalse\nwhole_paper_reproduced\tfalse\n");
            check.run();manifest(output,sink,"SHA256SUMS",check);return result;
        } catch(IOException|RuntimeException error){
            // These small failure records must survive an exhausted scientific-output quota.
            Files.writeString(output.resolve("FAILURE.txt"),"FAIL2_NATIVE_MASH: "+message(error)+"\n\n"+RECOVERY+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.writeString(output.resolve("WARNINGS.md"),"\n## FAIL2: analysis incomplete\n\n"+message(error)+"\n\n"+RECOVERY+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            Files.deleteIfExists(output.resolve("result.tsv"));Files.deleteIfExists(output.resolve("SHA256SUMS"));throw error;
        }
    }

    private static long work(int n,int k,long rows,int prepares){return Math.multiplyExact(Math.multiplyExact((long)n*n*n,k),Math.addExact(Math.multiplyExact(12,rows),3L*prepares));}
    private static void completeCapacity(int rows,int n,int k,Limits limits)throws IOException {
        var fit=limits.fit();long cells=Math.addExact(Math.multiplyExact(2L*rows,n),Math.multiplyExact((long)k,n*(long)n));long bytes=Math.addExact(65536,Math.multiplyExact(192,cells));
        if(rows>limits.rows()||rows>fit.rows()||n>fit.gaussian().conditions()||k>fit.gaussian().components()||cells>fit.cells()||bytes>fit.ownedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IOException("Complete native input/model allocation exceeds declared capacities or half the heap; no population prefix is used");
    }
    private static Data data(Path root,Limits limits,Runnable check)throws IOException {
        var populations=new ArrayList<Population>();var seen=new java.util.HashSet<String>();
        try(var reader=reader(root.resolve("populations.tsv"))){if(!"feature\ttrain\tstrong\ttarget".equals(reader.readLine()))throw new IOException("Invalid populations.tsv header");for(String line;(line=reader.readLine())!=null;){check.run();String[] f=fields(line,4);label(f[0]);if(!seen.add(f[0])||populations.size()>=limits.rows())throw new IOException("Duplicate feature identity or complete row capacity exceeded");for(int c=1;c<4;c++)if(!f[c].equals("0")&&!f[c].equals("1"))throw new IOException("Population flags must be literal 0/1");if(f[1].equals("0")&&f[2].equals("0")&&f[3].equals("0"))throw new IOException("Unassigned population row cannot be dropped");populations.add(new Population(f[0],f[1].equals("1"),f[2].equals("1"),f[3].equals("1")));}}
        if(populations.isEmpty())throw new IOException("Empty native population");List<String> conditions;
        try(var reader=reader(root.resolve("effects.tsv"))){String line=reader.readLine();if(line==null)throw new IOException("Empty effects.tsv");String[] header=line.split("\t",-1);if(header.length<2||!header[0].equals("feature"))throw new IOException("Invalid effects.tsv header");conditions=List.of(Arrays.copyOfRange(header,1,header.length));seen.clear();for(String condition:conditions){label(condition);if(!seen.add(condition))throw new IOException("Duplicate condition identity");}}
        completeCapacity(populations.size(),conditions.size(),1,limits);
        return new Data(conditions,List.copyOf(populations),wide(root.resolve("effects.tsv"),conditions,populations,false,check),wide(root.resolve("standard-errors.tsv"),conditions,populations,true,check));
    }
    private static double[][] wide(Path file,List<String> conditions,List<Population> populations,boolean se,Runnable check)throws IOException {
        double[][] values=new double[populations.size()][conditions.size()];try(var reader=reader(file)){if(!("feature\t"+String.join("\t",conditions)).equals(reader.readLine()))throw new IOException("Effect/SE condition identities/order differ");for(int row=0;row<populations.size();row++){check.run();String[] f=fields(reader.readLine(),conditions.size()+1);if(!f[0].equals(populations.get(row).id()))throw new IOException("Effect/SE feature identities/order differ");for(int c=0;c<conditions.size();c++){values[row][c]=numeric(f[c+1]);if(se&&values[row][c]<=0)throw new IOException("Require positive finite SE; no missingness or zero filling is inferred");}}if(reader.readLine()!=null)throw new IOException("Unexpected complete matrix rows");}return values;
    }
    private static double[][] select(double[][] values,List<Population> rows,boolean training){var selected=new ArrayList<double[]>();for(int r=0;r<rows.size();r++)if(training?rows.get(r).train():rows.get(r).target())selected.add(values[r]);return selected.toArray(double[][]::new);}
    private static void writeIds(Sink sink,Data data)throws IOException {sink.lines("conditions.tsv",data.conditions());for(String kind:List.of("training","strong","target")){var ids=new ArrayList<String>();for(Population p:data.populations())if(switch(kind){case "training"->p.train();case "strong"->p.strong();default->p.target();})ids.add(p.id());sink.lines(kind+"-ids.tsv",ids);}}
    private static MashGaussian.Options gaussian(Map<String,String> settings)throws IOException {return new MashGaussian.Options(number(settings,"alpha"),number(settings,"posterior_threshold"),number(settings,"covariance_tolerance"),number(settings,"minimum_se"),number(settings,"numeric_sensitivity"),true);}
    private static double[] grid(String text)throws IOException {String[] f=text.split(",",-1);if(f.length>4096)throw new IOException("Complete scale grid exceeds capacity");double[] result=new double[f.length];for(int i=0;i<f.length;i++)result[i]=numeric(f[i]);return result;}
    private static MashGaussian.Basis basis(Path root,int n,Limits limits,int rows,Runnable check)throws IOException {List<String> names=labels(root.resolve("component-names.tsv"),limits.fit().gaussian().components(),false);completeCapacity(rows,n,names.size(),limits);var matrices=new ArrayList<double[][]>();for(int k=0;k<names.size();k++){check.run();matrices.add(matrix(root.resolve("component-"+(k+1)+".tsv"),n,n));}return new MashGaussian.Basis(names,matrices);}
    private static void exportModel(Sink sink,List<String> conditions,MashGaussian.Basis expanded,double[][] residual,double[] proportions,MashGaussian.Options options,Runnable check)throws IOException {
        sink.text("model/representation.tsv","key\tvalue\nschema_version\t1\nrepresentation\tfully_expanded\n");sink.lines("model/conditions.tsv",conditions);sink.lines("model/component-names.tsv",expanded.names());
        var matrices=expanded.covariances();for(int i=0;i<matrices.size();i++)sink.matrix("model/component-"+(i+1)+".tsv",matrices.get(i));sink.matrix("model/residual.tsv",residual);sink.vector("model/proportions.tsv",proportions);
        sink.text("model/settings.tsv","key\tvalue\nalpha\t"+options.alpha()+"\nposterior_threshold\t"+options.minimumPriorWeight()+"\ncovariance_tolerance\t"+options.covarianceTolerance()+"\nminimum_se\t"+options.minimumStandardError()+"\nnumeric_sensitivity\t"+options.maximumLogScoreUlp()+"\n");
        manifest(sink.root.resolve("model"),sink,"model/SHA256SUMS",check);
    }
    private static void posterior(Sink sink,Data data,MashGaussian.Model model,Runnable check)throws IOException {
        String[] names={"mean","sd","negative","zero","lfsr","covariance-column-major","weights","component-loglik","standardized-residual-mean","loglik"};var writers=new ArrayList<BufferedWriter>();
        try {
            for(String name:names)writers.add(sink.writer("posterior/"+name+".tsv"));BufferedWriter training=sink.writer("fit/final-training-component-loglik.tsv");writers.add(training);
            for(int row=0;row<data.populations().size();row++){
                check.run();Population p=data.populations().get(row);if(!p.train()&&!p.target())continue;
                var evaluation=model.evaluateWithResidual(data.effects()[row],data.errors()[row],check);var value=evaluation.posterior();
                for(String warning:value.numericalNotes())sink.warning("Feature "+p.id()+": "+warning);
                if(p.train())sink.row(training,value.componentLogLikelihoods());
                if(!p.target())continue;
                double[][] covariance=value.covariance();double[] flattened=new double[model.conditions()*model.conditions()];for(int j=0;j<model.conditions();j++)for(int i=0;i<model.conditions();i++)flattened[j*model.conditions()+i]=covariance[i][j];
                double[][] fields={value.mean(),value.standardDeviation(),value.negativeProbability(),value.zeroProbability(),value.lfsr(),flattened,value.posteriorWeights(),value.componentLogLikelihoods(),evaluation.standardizedResidualMean(),new double[]{value.logLikelihood(),value.scaledLogLikelihood(),value.retainedPriorMass()}};
                for(int i=0;i<fields.length;i++)sink.row(writers.get(i),fields[i]);
            }
        } finally {IOException failure=null;for(var writer:writers)try{writer.close();}catch(IOException error){failure=error;}if(failure!=null)throw failure;}
    }
    private static final class Evidence implements MashFit.Listener {
        private final Sink sink;private int fits;
        Evidence(Sink sink){this.sink=sink;}
        @Override public void proposal(int proposal,double[][] residual){io(()->sink.matrix("fit/proposal-"+proposal+"/residual.tsv",residual));}
        @Override public void weights(double[][] residual,MashMixtureWeights.Fit fit){int index=fits++;io(()->{
            String prefix="fit/weights-"+index+"/";sink.matrix(prefix+"residual.tsv",residual);sink.vector(prefix+"proportions.tsv",fit.proportions());sink.text(prefix+"diagnostics.tsv","key\tvalue\nconverged\t"+fit.converged()+"\niterations\t"+fit.iterations()+"\nlog_likelihood\t"+fit.logLikelihood()+"\nrelative_objective\t"+fit.relativeObjective()+"\nrelative_dual_gap\t"+fit.relativeDualGap()+"\nevaluated_cells\t"+fit.evaluatedCells()+"\n");
            try(var writer=sink.writer(prefix+"history.tsv")){sink.line(writer,"iteration\trelative_objective\trelative_dual_gap\tmaximum_weight_change");for(var step:fit.history())sink.line(writer,step.index()+"\t"+step.relativeObjective()+"\t"+step.relativeDualGap()+"\t"+step.maximumWeightChange());}for(String note:fit.numericalNotes())sink.warning("Weight fit "+index+": "+note);
        });}
        @Override public void step(MashFit.Step step,MashMixtureWeights.Fit weights){io(()->sink.text("fit/step-"+step.proposal()+".tsv","key\tvalue\nproposal\t"+step.proposal()+"\naccepted\t"+step.accepted()+"\nlog_likelihood\t"+step.logLikelihood()+"\ndelta_log_likelihood\t"+step.deltaLogLikelihood()+"\n"));}
        @Override public void warning(String phase,int row,String warning){io(()->sink.warning(phase+" row "+row+": "+warning));}
        private static void io(IO action){try{action.run();}catch(IOException error){throw new EvidenceFailure(error);}}
    }
    @FunctionalInterface private interface IO {void run()throws IOException;}
    private static final class EvidenceFailure extends RuntimeException {private static final long serialVersionUID=1;final IOException io;EvidenceFailure(IOException io){super(io);this.io=io;}}
    private static final class Sink {
        private final Path root;private final long maximum;private final Runnable check;private long written;
        Sink(Path root,long maximum,Runnable check){this.root=root;this.maximum=maximum;this.check=check;}
        void reserve(long bytes)throws IOException {check.run();written=Math.addExact(written,bytes);if(written>maximum)throw new IOException("Complete native output exceeds its declared byte budget; no output prefix is complete");}
        BufferedWriter writer(String name)throws IOException {Path path=root.resolve(name);Files.createDirectories(path.getParent());return Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
        void line(BufferedWriter writer,String text)throws IOException {reserve(text.getBytes(StandardCharsets.UTF_8).length+1L);writer.write(text);writer.write('\n');}
        void row(BufferedWriter writer,double[] values)throws IOException {StringBuilder line=new StringBuilder();for(double value:values){if(!Double.isFinite(value))throw new IOException("Unreportable nonfinite native output");if(!line.isEmpty())line.append('\t');line.append(value);}line(writer,line.toString());}
        void text(String name,String text)throws IOException {reserve(text.getBytes(StandardCharsets.UTF_8).length);try(var writer=writer(name)){writer.write(text);}}
        void lines(String name,List<String> lines)throws IOException {try(var writer=writer(name)){for(String line:lines)line(writer,line);}}
        void matrix(String name,double[][] values)throws IOException {try(var writer=writer(name)){for(double[] row:values)row(writer,row);}}
        void vector(String name,double[] values)throws IOException {try(var writer=writer(name)){for(double value:values)row(writer,new double[]{value});}}
        void warning(String warning)throws IOException {String text="\n- "+warning+"\n";reserve(text.getBytes(StandardCharsets.UTF_8).length);Files.writeString(root.resolve("WARNINGS.md"),text,StandardCharsets.UTF_8,StandardOpenOption.APPEND);}
    }
    private static Map<String,String> settings(Path path)throws IOException {var settings=new LinkedHashMap<String,String>();try(var reader=reader(path)){if(!"key\tvalue".equals(reader.readLine()))throw new IOException("Invalid settings header: "+path.getFileName());for(String line;(line=reader.readLine())!=null;){String[] f=fields(line,2);if(f[0].isBlank()||settings.size()>=100||settings.putIfAbsent(f[0],f[1])!=null)throw new IOException("Duplicate/oversized native settings");}}return settings;}
    private static String required(Map<String,String> settings,String key)throws IOException {String value=settings.get(key);if(value==null||value.isBlank())throw new IOException("Missing explicit setting: "+key);return value;}
    private static double number(Map<String,String> settings,String key)throws IOException {return numeric(required(settings,key));}
    private static int integer(Map<String,String> settings,String key)throws IOException {String text=required(settings,key);try {int value=Integer.parseInt(text);if(!text.equals(Integer.toString(value)))throw new NumberFormatException();return value;}catch(NumberFormatException error){throw new IOException("Invalid literal integer setting: "+key,error);}}
    private static boolean bool(Map<String,String> settings,String key)throws IOException {return switch(required(settings,key)){case "true"->true;case "false"->false;default->throw new IOException("Require literal true/false: "+key);};}
    private static double numeric(String text)throws IOException {try{if(!text.equals(text.strip())||text.length()>128)throw new NumberFormatException();double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new NumberFormatException();return value;}catch(NumberFormatException error){throw new IOException("Invalid/nonfinite native numeric value",error);}}
    private static String[] fields(String line,int width)throws IOException {if(line==null||line.length()>1_048_576)throw new IOException("Truncated/oversized native row");String[] f=line.split("\t",-1);if(f.length!=width)throw new IOException("Native row has wrong complete width");return f;}
    private static void label(String value)throws IOException {if(value.isBlank()||value.length()>4096||value.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid native identity");}
    private static List<String> labels(Path path,int maximum,boolean unique)throws IOException {var result=new ArrayList<String>();var seen=new java.util.HashSet<String>();try(var reader=reader(path)){for(String line;(line=reader.readLine())!=null;){label(line);if(result.size()>=maximum||unique&&!seen.add(line))throw new IOException("Duplicate identity or complete component capacity exceeded");result.add(line);}}if(result.isEmpty())throw new IOException("Empty complete identity table");return List.copyOf(result);}
    private static double[][] matrix(Path path,int rows,int columns)throws IOException {double[][] result=new double[rows][columns];try(var reader=reader(path)){for(int i=0;i<rows;i++){String[] f=fields(reader.readLine(),columns);for(int j=0;j<columns;j++)result[i][j]=numeric(f[j]);}if(reader.readLine()!=null)throw new IOException("Unexpected native matrix rows");}return result;}
    private static double[] vector(Path path,int rows)throws IOException {double[][] values=matrix(path,rows,1);double[] result=new double[rows];for(int i=0;i<rows;i++)result[i]=values[i][0];return result;}
    private static BufferedReader reader(Path path)throws IOException {noLinks(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing/nonregular native input: "+path);return Files.newBufferedReader(path,StandardCharsets.UTF_8);}
    private static void noLinks(Path path)throws IOException {for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked native input/output path is not allowed: "+p);}
    private static Map<String,Pin> inventory(Path root,long maximum,Runnable check)throws IOException {
        var pins=new TreeMap<String,Pin>();long total=0;try(var files=Files.walk(root)){var iterator=files.iterator();while(iterator.hasNext()){check.run();Path path=iterator.next();noLinks(path);if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular file in native evidence tree");long size=Files.size(path);total=Math.addExact(total,size);if(total>maximum||pins.size()>=10000)throw new IOException("Complete native evidence tree exceeds byte/file capacity");String name=root.relativize(path).toString().replace('\\','/');if(name.chars().anyMatch(Character::isISOControl))throw new IOException("Control character in evidence path");pins.put(name,pin(path,check));}}return pins;
    }
    private static Pin pin(Path path,Runnable check)throws IOException {try {var hash=MessageDigest.getInstance("SHA-256");long bytes=0;byte[] buffer=new byte[65536];try(var input=Files.newInputStream(path)){for(int n;(n=input.read(buffer))!=-1;){check.run();bytes+=n;hash.update(buffer,0,n);}}return new Pin(bytes,HexFormat.of().formatHex(hash.digest()));}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
    private static void manifest(Path folder,Sink sink,String name,Runnable check)throws IOException {var pins=inventory(folder,sink.maximum,check);try(var writer=sink.writer(name)){for(var item:pins.entrySet()){if(item.getKey().equals("SHA256SUMS"))continue;sink.line(writer,item.getValue().sha256()+"  "+item.getKey());}}}
    private static void verifyManifest(Path root,Runnable check)throws IOException {
        var actual=inventory(root,16_777_216,check);var expected=new TreeMap<String,String>();try(var reader=reader(root.resolve("SHA256SUMS"))){for(String line;(line=reader.readLine())!=null;){if(line.length()<67||!line.substring(0,64).matches("[0-9a-f]{64}")||!line.substring(64,66).equals("  ")||expected.putIfAbsent(line.substring(66),line.substring(0,64))!=null)throw new IOException("Invalid/duplicate expanded model manifest");}}
        actual.remove("SHA256SUMS");if(!actual.keySet().equals(expected.keySet()))throw new IOException("Expanded model manifest does not cover the complete model");for(var item:actual.entrySet())if(!item.getValue().sha256().equals(expected.get(item.getKey())))throw new IOException("Expanded model integrity mismatch: "+item.getKey());
    }
    private static String message(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
}
