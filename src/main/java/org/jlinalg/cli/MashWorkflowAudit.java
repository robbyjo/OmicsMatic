/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jlinalg.shrinkage.MashGaussian;

/**
 * Complete numerical audit of a typed original mashr development-driver result.
 * This verifies numeric consistency, not runtime provenance or bundle integrity;
 * scientific admission requires those separate gates as well.
 */
public final class MashWorkflowAudit {
    private MashWorkflowAudit() { }
    /** Complete-population capacities, numerical comparison tolerances and work budget. */
    public record Limits(int rows,int conditions,int components,long numericCells,long estimatedOwnedBytes,long estimatedWork,
                         double absoluteTolerance,double relativeTolerance) {
        public Limits {
            if(rows<1||conditions<2||conditions>2048||components<1||numericCells<1||estimatedOwnedBytes<65536||estimatedWork<1
                    ||!Double.isFinite(absoluteTolerance)||absoluteTolerance<=0||!Double.isFinite(relativeTolerance)||relativeTolerance<=0)
                throw new IllegalArgumentException("Invalid complete mashr audit limits");
        }
        public static Limits defaults(){return new Limits(1_000_000,256,4096,10_000_000,268_435_456,100_000_000_000L,1e-8,2e-8);}
    }
    /** Numeric coverage only; no process, source, runtime or whole-paper certification is implied. */
    public record Result(int trainingRows,int targetRows,int conditions,int components,long checkedNumericValues,
                         double maximumAbsoluteError,double maximumScaledError,double trainingRelativeDualGap,List<String> warnings) {
        public Result {warnings=List.copyOf(warnings);}
    }
    /** Audit all supplied training and target rows, with no sampling or prefix success. */
    public static Result audit(Path input,Path output,Limits limits,Runnable check)throws IOException {
        if(input==null||output==null||limits==null||check==null)throw new NullPointerException();check.run();
        Path root=output.toAbsolutePath().normalize(),inputs=input.toAbsolutePath().normalize();
        Path statusFile=file(root,"source-status.tsv");if(Files.size(statusFile)>16384)throw new IOException("Oversized original process status");
        var status=Files.readAllLines(statusFile,StandardCharsets.UTF_8);
        if(!status.equals(List.of("\"status\"\t\"stage\"\t\"message\"","\"ORIGINAL_PROCESS_COMPLETE_UNAUDITED\"\t\"original-process-complete-awaiting-independent-audit\"\t\"All original stages returned; no scientific result is admitted by this process alone\"")))throw new IOException("No complete original-process result; inspect source-status.tsv and WARNINGS.md. No scientific result is admitted.");
        var settings=settings(root);double alpha=number(settings,"alpha"),threshold=number(settings,"pi_threshold"),fitTolerance=number(settings,"fit_dual_gap_tolerance");
        if(!"1".equals(settings.get("schema_version")))throw new IOException("Unsupported source workflow schema");
        if(fitTolerance<=0||fitTolerance>.01)throw new IOException("Invalid declared training dual-gap tolerance");
        if(!settings.equals(settings(inputs)))throw new IOException("Source settings differ from the original request");
        var conditionNames=ids(root,"condition-ids.tsv",limits.conditions());var trainIds=ids(root,"training-ids.tsv",limits.rows());var targetIds=ids(root,"target-ids.tsv",limits.rows());var componentNames=labels(root,"model/component-names.tsv",limits.components(),false);
        int n=conditionNames.size(),k=componentNames.size(),training=trainIds.size(),targets=targetIds.size();if(n<2)throw new IOException("The multivariate workflow requires at least two conditions");
        long rows=Math.addExact((long)training,targets),cells=Math.addExact(Math.multiplyExact(rows,k+2L*n),Math.addExact(Math.multiplyExact((long)targets,n*(long)n+5L*n+k),Math.multiplyExact((long)k,n*(long)n)));
        long bytes=Math.addExact(65536,Math.multiplyExact(128L,cells));
        long work=Math.multiplyExact(Math.multiplyExact(Math.multiplyExact(Math.multiplyExact(rows,k),n),n),12L*n);
        if(cells>limits.numericCells()||bytes>limits.estimatedOwnedBytes()||bytes>Runtime.getRuntime().maxMemory()/2||work>limits.estimatedWork())throw new IOException("Complete mashr numeric audit exceeds declared allocation/work capacity or half the Java heap. Increase a permitted budget/heap; no population prefix is admitted.");
        var population=population(inputs,limits.rows());
        if(!population.equals(population(root,limits.rows()))||!trainIds.equals(population.stream().filter(Population::train).map(Population::id).toList())||!targetIds.equals(population.stream().filter(Population::target).map(Population::id).toList()))throw new IOException("Training/target identities differ from the complete declared population");
        double[] pi=matrix(root,"model/proportions.tsv",1,k)[0],prior=matrix(root,"model/dirichlet.tsv",1,k)[0];double[][] residual=matrix(root,"model/residual.tsv",n,n);
        var priors=new ArrayList<double[][]>();for(int c=0;c<k;c++){check.run();priors.add(matrix(root,"model/component-"+c+".tsv",n,n));}
        var model=MashGaussian.prepare(priors,pi,residual,new MashGaussian.Options(alpha,threshold,1e-12,Math.ulp(1.),1e-7,true),
            new MashGaussian.Limits(limits.conditions(),limits.components(),limits.estimatedWork(),limits.estimatedOwnedBytes()),check);
        var statistics=new Statistics(limits);var warnings=new ArrayList<String>();
        double[][] trainBhat=matrix(root,"training-bhat.tsv",training,n),trainShat=matrix(root,"training-shat.tsv",training,n),trainLikelihood=matrix(root,"training-component-loglik.tsv",training,k);
        double[][] bhat=matrix(root,"target-bhat.tsv",targets,n),shat=matrix(root,"target-shat.tsv",targets,n);
        verifyInput(inputs,"effects.tsv",conditionNames,population,trainBhat,bhat,false,check);
        verifyInput(inputs,"standard-errors.tsv",conditionNames,population,trainShat,shat,true,check);
        double[] logPi=new double[k],logGradient=new double[k];java.util.Arrays.fill(logGradient,Double.NEGATIVE_INFINITY);
        double pseudoSum=0,penalty=0;for(int c=0;c<k;c++){
            if(prior[c]<1)throw new IOException("Dirichlet prior is outside the declared concave fit");double pseudo=prior[c]-1;pseudoSum+=pseudo;
            logPi[c]=pi[c]==0?Double.NEGATIVE_INFINITY:Math.log(pi[c]);if(pseudo>0){if(pi[c]<=0)throw new IOException("Positive Dirichlet pseudocount lost support");penalty+=pseudo*logPi[c];}
        }
        double relativeLikelihood=0,fullLikelihood=0;
        for(int row=0;row<training;row++){
            check.run();var nativeRow=model.evaluate(trainBhat[row],trainShat[row],check);double[] ll=nativeRow.componentLogLikelihoods();double max=Double.NEGATIVE_INFINITY,norm=Double.NEGATIVE_INFINITY;
            for(int c=0;c<k;c++){statistics.near("training "+trainIds.get(row)+" component "+c,trainLikelihood[row][c],ll[c]);max=Math.max(max,ll[c]);norm=logAdd(norm,ll[c]+logPi[c]);}
            relativeLikelihood+=norm-max;fullLikelihood+=nativeRow.logLikelihood();
            for(int c=0;c<k;c++)logGradient[c]=logAdd(logGradient[c],ll[c]-norm);
            for(String warning:nativeRow.numericalNotes())warnings.add("Training "+trainIds.get(row)+": "+warning);
        }
        double lambda=training+pseudoSum,maximum=Double.NEGATIVE_INFINITY;
        for(int c=0;c<k;c++){if(prior[c]>1)logGradient[c]=logAdd(logGradient[c],Math.log(prior[c]-1)-logPi[c]);maximum=Math.max(maximum,logGradient[c]);}
        double gap=Math.max(0,Math.expm1(maximum-Math.log(lambda)));
        if(!Double.isFinite(gap)||gap>fitTolerance)throw new IOException("MIXTURE_NONCONVERGED: independent Java training dual-gap check failed ("+gap+"). Preserve the fitted model; increase optimizer limits or resolve the numerical discrepancy before admission.");
        double[] sourceFit=matrix(root,"training-objective-gap-lambda.tsv",1,3)[0];statistics.near("training relative objective",sourceFit[0],relativeLikelihood+penalty);statistics.near("training dual gap",sourceFit[1],gap);statistics.near("training gradient scale",sourceFit[2],lambda);
        double[] sourceLikelihood=matrix(root,"training-source-and-recomputed-loglik.tsv",1,2)[0];for(double value:sourceLikelihood)statistics.near("complete training log likelihood",value,fullLikelihood);
        var fields=new HashMap<String,double[][]>();for(String field:List.of("mean","sd","negative","zero","lfsr"))fields.put(field,matrix(root,"posterior/"+field+".tsv",targets,n));
        double[][] covariance=matrix(root,"posterior/covariance-column-major.tsv",targets,n*n),weights=matrix(root,"posterior/weights.tsv",targets,k),component=matrix(root,"posterior/component-loglik.tsv",targets,k);double[] likelihood=matrix(root,"posterior/loglik.tsv",1,targets)[0];
        for(int row=0;row<targets;row++){
            check.run();var result=model.evaluate(bhat[row],shat[row],check);String name="target "+targetIds.get(row);
            for(var field:List.of(new Field("mean",result.mean()),new Field("sd",result.standardDeviation()),new Field("negative",result.negativeProbability()),new Field("zero",result.zeroProbability()),new Field("lfsr",result.lfsr())))
                for(int c=0;c<n;c++)statistics.near(name+" "+field.name()+" "+conditionNames.get(c),fields.get(field.name())[row][c],field.values()[c]);
            double[][] nativeCovariance=result.covariance();for(int i=0;i<n;i++)for(int j=0;j<n;j++)statistics.near(name+" covariance["+i+","+j+"]",covariance[row][i+j*n],nativeCovariance[i][j]);
            double[] nativeWeights=result.posteriorWeights(),nativeComponents=result.componentLogLikelihoods();
            for(int c=0;c<k;c++){statistics.near(name+" weight "+c,weights[row][c],nativeWeights[c]);statistics.near(name+" component log likelihood "+c,component[row][c],nativeComponents[c]);}
            statistics.near(name+" mixture log likelihood",likelihood[row],result.logLikelihood());
            for(String warning:result.numericalNotes())warnings.add("Target "+targetIds.get(row)+": "+warning);
        }
        check.run();return new Result(training,targets,n,k,statistics.count,statistics.absolute,statistics.scaled,gap,warnings);
    }
    private record Field(String name,double[] values) { }
    private record Population(String id,boolean train,boolean strong,boolean target) { }
    private static List<Population> population(Path root,int maximum)throws IOException {
        var result=new ArrayList<Population>();var seen=new HashSet<String>();
        try(var reader=Files.newBufferedReader(file(root,"populations.tsv"),StandardCharsets.UTF_8)){
            if(!"feature\ttrain\tstrong\ttarget".equals(reader.readLine()))throw new IOException("Invalid complete population header");
            for(String line;(line=reader.readLine())!=null;){String[] fields=line.split("\t",-1);
                if(fields.length!=4||fields[0].isBlank()||fields[0].length()>65536||!seen.add(fields[0])||result.size()>=maximum)throw new IOException("Invalid/duplicate/oversized complete population identity");
                for(int i=1;i<4;i++)if(!fields[i].equals("0")&&!fields[i].equals("1"))throw new IOException("Population flags must be literal0/1");
                if(fields[1].equals("0")&&fields[2].equals("0")&&fields[3].equals("0"))throw new IOException("Unassigned population row cannot be silently dropped");
                result.add(new Population(fields[0],fields[1].equals("1"),fields[2].equals("1"),fields[3].equals("1")));
            }
        }return List.copyOf(result);
    }
    private static void verifyInput(Path input,String name,List<String> conditions,List<Population> population,double[][] training,double[][] targets,boolean errors,Runnable check)throws IOException {
        try(var reader=Files.newBufferedReader(file(input,name),StandardCharsets.UTF_8)){
            if(!("feature\t"+String.join("\t",conditions)).equals(reader.readLine()))throw new IOException("Original matrix condition identities differ: "+name);
            int train=0,target=0;
            for(var row:population){check.run();String line=reader.readLine();if(line==null)throw new IOException("Truncated original matrix: "+name);String[] fields=line.split("\t",-1);
                if(fields.length!=conditions.size()+1||!row.id().equals(fields[0]))throw new IOException("Original feature identity/order differs: "+name);
                for(int c=0;c<conditions.size();c++){
                    double value;try{value=Double.parseDouble(fields[c+1]);}catch(NumberFormatException error){throw new IOException("Invalid original numeric matrix: "+name,error);}
                    if(!Double.isFinite(value)||errors&&value<=Math.ulp(1.))throw new IOException("Missing/nonfinite original effects or invalid original SE: "+name);
                    if(row.train()&&value!=training[train][c]||row.target()&&value!=targets[target][c])throw new IOException("Original effect/SE differs from the complete selected population: "+name+" "+row.id());
                }if(row.train())train++;if(row.target())target++;
            }if(reader.readLine()!=null)throw new IOException("Unexpected rows in original matrix: "+name);
        }
    }
    private static Path file(Path root,String name)throws IOException {
        Path path=root.resolve(name).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))throw new IOException("Missing/nonregular original workflow file: "+name);
        for(Path parent=path.getParent();parent!=null&&parent.startsWith(root);parent=parent.getParent())if(Files.isSymbolicLink(parent))throw new IOException("Linked original workflow directory: "+name);
        return path;
    }
    private static List<String> ids(Path root,String name,int maximum)throws IOException {
        return labels(root,name,maximum,true);
    }
    private static List<String> labels(Path root,String name,int maximum,boolean unique)throws IOException {
        var result=new ArrayList<String>();var seen=new HashSet<String>();try(BufferedReader reader=Files.newBufferedReader(file(root,name),StandardCharsets.UTF_8)){
            for(String line;(line=reader.readLine())!=null;){if(line.isBlank()||line.length()>65536||line.indexOf('\t')>=0||unique&&!seen.add(line)||result.size()>=maximum)throw new IOException("Invalid/duplicate/oversized complete identity table: "+name);result.add(line);}
        }if(result.isEmpty())throw new IOException("Empty required identity table: "+name);return List.copyOf(result);
    }
    private static Map<String,String> settings(Path root)throws IOException {
        var result=new HashMap<String,String>();try(BufferedReader reader=Files.newBufferedReader(file(root,"settings.tsv"),StandardCharsets.UTF_8)){
            if(!"key\tvalue".equals(reader.readLine()))throw new IOException("Invalid source settings header");
            for(String line;(line=reader.readLine())!=null;){String[] fields=line.split("\t",-1);if(fields.length!=2||fields[0].isBlank()||result.putIfAbsent(fields[0],fields[1])!=null||result.size()>100)throw new IOException("Invalid/duplicate complete source setting");}
        }return result;
    }
    private static double number(Map<String,String> settings,String key)throws IOException {try{double value=Double.parseDouble(settings.get(key));if(!Double.isFinite(value))throw new NumberFormatException();return value;}catch(RuntimeException error){throw new IOException("Missing/invalid numeric source setting: "+key,error);}}
    private static double[][] matrix(Path root,String name,int rows,int columns)throws IOException {
        var values=new double[rows][columns];try(BufferedReader reader=Files.newBufferedReader(file(root,name),StandardCharsets.UTF_8)){
            for(int row=0;row<rows;row++){String line=reader.readLine();if(line==null)throw new IOException("Truncated complete matrix: "+name);String[] fields=line.split("\t",-1);if(fields.length!=columns)throw new IOException("Wrong complete matrix width: "+name);
                for(int c=0;c<columns;c++)try{double value=Double.parseDouble(fields[c]);if(!Double.isFinite(value))throw new NumberFormatException();values[row][c]=value;}catch(NumberFormatException error){throw new IOException("Nonfinite/invalid matrix cell: "+name,error);}}
            if(reader.readLine()!=null)throw new IOException("Unexpected rows in complete matrix: "+name);
        }return values;
    }
    private static double logAdd(double a,double b){if(a==Double.NEGATIVE_INFINITY)return b;if(b==Double.NEGATIVE_INFINITY)return a;double max=Math.max(a,b);return max+Math.log1p(Math.exp(Math.min(a,b)-max));}
    private static final class Statistics {
        private final Limits limits;private long count;private double absolute,scaled;
        Statistics(Limits limits){this.limits=limits;}
        void near(String label,double expected,double actual)throws IOException {
            double error=Math.abs(expected-actual);count++;absolute=Math.max(absolute,error);scaled=Math.max(scaled,error/Math.max(1,Math.abs(expected)));
            if(!Double.isFinite(expected)||!Double.isFinite(actual)||error>limits.absoluteTolerance()+limits.relativeTolerance()*Math.abs(expected))throw new IOException("MASHR NUMERIC FAIL2: "+label+"; original="+expected+", native="+actual+". Preserve the complete evidence and resolve the source/numerical boundary; no sampled or partial success is admitted.");
        }
    }
}
