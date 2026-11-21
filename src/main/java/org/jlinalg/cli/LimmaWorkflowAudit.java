/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import jdistlib.T;
import org.jlinalg.differential.EdgeRNormalization;
import org.jlinalg.differential.LimmaVarianceModeration;
import org.jlinalg.enrichment.FumaCelltypeAnalysis;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** R-free intake, output populations, original-model arithmetic and journal structure. */
final class LimmaWorkflowAudit {
    private LimmaWorkflowAudit() { }
    private static final String OBSERVER="Original statistical bodies retained; observational call bindings and one read-only glmgam.fit on.exit prefix with exact original-body inverse; unchanged original namespaces";
    static void outputs(Path root,Map<String,Object> r,boolean compare)throws IOException {
        var p=LimmaWorkflowFiles.prepare(root,r,true);int g=p.features().size(),n=p.samples().size(),k=p.resultIds().size();String op=text(r,"operation");Path out=root.resolve("results");
        var report=read(root.resolve("worker/report.json"));
        if(!"completed-with-warnings".equals(report.get("status"))||!Boolean.TRUE.equals(report.get("results_admitted"))||!Boolean.TRUE.equals(report.get("runtime_ready"))||!Boolean.TRUE.equals(report.get("source_guards_after"))||!OBSERVER.equals(report.get("execution_scope"))||!r.get("profile").equals(report.get("profile"))||!op.equals(report.get("operation"))||integer(report,"features_input",-1,0,g)!=g||integer(report,"samples",-1,0,n)!=n||integer(report,"source_guards_before",-1,0,1000)!=48||!list(report.get("internal_failures"),"internal failures").isEmpty())throw new IOException("Original worker status, source guard or population differs");
        if(integer(report,"contrasts",-1,0,256)!=k||integer(report,"sets",-1,0,100000)!=p.setIds().size())throw new IOException("Original comparison/set population differs");
        var runtime=read(root.resolve("worker/runtime.json"));if(!"4.6.1".equals(runtime.get("R"))||list(runtime.get("function_guards"),"source guards").size()!=48)throw new IOException("Original runtime/source identities differ");
        var versions=new HashMap<String,String>();for(Object value:list(runtime.get("packages"),"packages")){var row=object(value,"package");versions.put(text(row,"package"),text(row,"version"));}
        if(!"3.68.5".equals(versions.get("limma"))||!"4.10.7".equals(versions.get("edgeR"))||!"1.5.2".equals(versions.get("statmod")))throw new IOException("Original package edition differs");
        journals(root,r,report,g);
        var expected=new HashSet<String>();
        if(op.equals("CAMERAPR")) {expected.add("camera.tsv");camera(root,r,p);}
        else {
            expected.add("expression.tsv");double[][] expression=matrix(out.resolve("expression.tsv"),"feature_id",p.features(),p.samples());
            double[][] original=matrix(root.resolve("worker/matrix.tsv"),"feature_id",p.features(),p.samples());
            if(op.equals("NORMALIZE")) {
                if(r.get("normalize_method").equals("none"))equalMatrices(expression,original,0,"Original no-normalization values differ");
            }else if(op.equals("BATCH")) {
                expected.addAll(List.of("batch-coefficients.tsv","batch-design.tsv"));batch(root,r,p,original,expression);
            }else {
                expected.addAll(List.of("coefficients-original.tsv","stdev-original.tsv","contrasts.tsv","coefficients.tsv","stdev-unscaled.tsv","t.tsv","pvalue.tsv","padjust.tsv","variance.tsv"));
                matrix(out.resolve("coefficients-original.tsv"),"feature_id",p.features(),p.coefficients());matrix(out.resolve("stdev-original.tsv"),"feature_id",p.features(),p.coefficients());
                double[][] contrasts=matrix(out.resolve("contrasts.tsv"),"coefficient_id",p.coefficients(),p.resultIds());int j=0;
                for(Object c:list(r.get("contrasts"),"contrasts")){var values=list(object(c,"contrast").get("values"),"values");for(int i=0;i<p.coefficients().size();i++)equal(contrasts[i][j],((Number)values.get(i)).doubleValue(),0,"Declared contrast changed");j++;}
                model(out,r,p,report);
                if(object(r.get("moderation"),"moderation").get("method").equals("EBAYES")) {
                    expected.addAll(List.of("lods.tsv","F.tsv"));matrix(out.resolve("lods.tsv"),"feature_id",p.features(),p.resultIds());var f=table(out.resolve("F.tsv"),g,3);
                    schema(f,"feature_id",p.features(),List.of("F","PValue"));for(var row:f.rows()){if(value(row[1])<0)throw new IOException("Negative original F");probability(value(row[2]));}
                }
                if(LimmaWorkflowContracts.counts(op)) {
                    expected.addAll(List.of("normalization.tsv","weights.tsv"));double[][] weights=matrix(out.resolve("weights.tsv"),"feature_id",p.features(),p.samples());
                    for(var row:weights)for(double v:row)if(v<=0)throw new IOException("Nonpositive original voom weight");normalization(root,r,p,original,expression);
                }
                if(Files.exists(out.resolve("correlation.tsv"))) {
                    expected.add("correlation.tsv");var c=table(out.resolve("correlation.tsv"),1,1);
                    if(!Arrays.equals(c.header(),new String[]{"consensus_correlation"})||c.rows().size()!=1||Math.abs(value(c.rows().get(0)[0]))>=1)throw new IOException("Invalid original consensus correlation");
                    if(!op.equals("EDGER_VOOM_LMFIT")&&object(r.get("correlation"),"correlation").get("mode").equals("fixed"))equal(value(c.rows().get(0)[0]),((Number)object(r.get("correlation"),"correlation").get("value")).doubleValue(),0,"Fixed correlation changed");
                }else if(!op.equals("EDGER_VOOM_LMFIT")&&!object(r.get("correlation"),"correlation").get("mode").equals("none"))throw new IOException("Missing used original correlation");
            }
        }
        try(var stream=Files.list(out)){if(!stream.map(v->v.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expected))throw new IOException("Original result file population differs");}
    }
    private static void model(Path out,Map<String,Object> r,LimmaWorkflowFiles.Prepared p,Map<String,Object> report)throws IOException {
        int g=p.features().size(),k=p.resultIds().size();double[][] coefficient=matrix(out.resolve("coefficients.tsv"),"feature_id",p.features(),p.resultIds()),unscaled=matrix(out.resolve("stdev-unscaled.tsv"),"feature_id",p.features(),p.resultIds()),t=matrix(out.resolve("t.tsv"),"feature_id",p.features(),p.resultIds()),pv=matrix(out.resolve("pvalue.tsv"),"feature_id",p.features(),p.resultIds()),adjusted=matrix(out.resolve("padjust.tsv"),"feature_id",p.features(),p.resultIds());
        var variance=table(out.resolve("variance.tsv"),g,8);schema(variance,"feature_id",p.features(),List.of("sigma","residual_df","prior_df","prior_variance","posterior_variance","total_df","Amean"));
        double[] raw=new double[g],df=new double[g],priorDf=new double[g],prior=new double[g],post=new double[g],total=new double[g];int zero=0;double pooled=0;
        for(int i=0;i<g;i++) {
            var row=variance.rows().get(i);df[i]=value(row[2]);if(df[i]<0)throw new IOException("Negative residual df");pooled+=df[i];
            if(row[1].equals("NA")&&df[i]==0){raw[i]=0;zero++;}else {double sigma=value(row[1]);if(sigma<0)throw new IOException("Negative sigma");raw[i]=sigma*sigma;}
            priorDf[i]=row[3].equals("Inf")?Double.POSITIVE_INFINITY:value(row[3]);prior[i]=value(row[4]);post[i]=value(row[5]);total[i]=value(row[6]);value(row[7]);
            if(priorDf[i]<0||prior[i]<0||post[i]<0||total[i]<=0)throw new IOException("Invalid original prior/posterior variance/df");
        }
        if(integer(report,"source_zero_df_NA_sigma_rows",-1,0,g)!=zero)throw new IOException("Original zero-df NA sigma boundary record differs");
        double[] expected=LimmaVarianceModeration.posterior(raw,df,prior,priorDf);
        for(int i=0;i<g;i++){precise(post[i],expected[i],2e-11,"Source posterior arithmetic differs");equal(total[i],Math.min(pooled,df[i]+priorDf[i]),2e-12,"Source pooled total df cap differs");}
        var moderation=object(r.get("moderation"),"moderation");boolean treat=moderation.get("method").equals("TREAT");double lfc=treat?((Number)moderation.get("lfc")).doubleValue():0;
        for(int i=0;i<g;i++)for(int j=0;j<k;j++) {
            if(unscaled[i][j]<=0)throw new IOException("Nonpositive original unscaled SE");double se=unscaled[i][j]*Math.sqrt(post[i]),stat=coefficient[i][j]/se,prob;
            if(treat){double a=Math.abs(coefficient[i][j]);stat=Math.copySign(Math.max((a-lfc)/se,0),coefficient[i][j]);prob=T.cumulative((a-lfc)/se,total[i],false,false)+T.cumulative((a+lfc)/se,total[i],false,false);}
            else prob=2*T.cumulative(-Math.abs(stat),total[i],true,false);
            probability(pv[i][j]);probability(adjusted[i][j]);equal(t[i][j],stat,2e-11,"Source moderated t arithmetic differs");precise(pv[i][j],prob,2e-9,"Source Student-t probability differs");
        }
        String method=text(r,"adjust_method");
        if(r.get("adjustment_scope").equals("all-contrasts")) {
            double[] probabilities=new double[Math.multiplyExact(g,k)];for(int j=0;j<k;j++)for(int i=0;i<g;i++)probabilities[j*g+i]=pv[i][j];double[] a=adjust(probabilities,method,r);
            for(int j=0;j<k;j++)for(int i=0;i<g;i++)precise(adjusted[i][j],a[j*g+i],2e-12,"Whole-contrast-family adjustment differs");
        }else for(int j=0;j<k;j++){double[] probabilities=new double[g];for(int i=0;i<g;i++)probabilities[i]=pv[i][j];double[] a=adjust(probabilities,method,r);for(int i=0;i<g;i++)precise(adjusted[i][j],a[i],2e-12,"Complete per-contrast adjustment differs");}
    }
    private static void normalization(Path root,Map<String,Object> r,LimmaWorkflowFiles.Prepared p,double[][] counts,double[][] expression)throws IOException {
        var t=table(root.resolve("results/normalization.tsv"),p.samples().size(),4);schema(t,"sample_id",p.samples(),List.of("library_size","norm_factor","effective_library_size"));int n=p.samples().size();double[] libs=new double[n],factors=new double[n];
        for(int j=0;j<n;j++){var row=t.rows().get(j);libs[j]=value(row[1]);factors[j]=value(row[2]);double effective=value(row[3]);if(libs[j]<=0||factors[j]<=0)throw new IOException("Invalid original library/factor");equal(effective,libs[j]*factors[j],1e-14,"Effective library differs");}
        if(r.containsKey("libraries")){var in=table(root.resolve("worker/libraries.tsv"),n,2);for(int j=0;j<n;j++)equal(libs[j],value(in.rows().get(j)[1]),0,"Supplied library changed");}
        else for(int j=0;j<n;j++){double sum=0;for(var row:counts)sum+=row[j];equal(libs[j],sum,2e-12,"Computed library sum differs");}
        var norm=object(r.get("normalization"),"normalization");String method=text(norm,"method");
        if(method.equals("provided")){var in=table(root.resolve("worker/norm_factors.tsv"),n,2);for(int j=0;j<n;j++)equal(factors[j],value(in.rows().get(j)[1]),0,"Provided factor changed or recentered");}
        else {
            EdgeRNormalization.Method m=switch(method){case "TMM"->EdgeRNormalization.Method.TMM;case "TMMwsp"->EdgeRNormalization.Method.TMMWSP;case "RLE"->EdgeRNormalization.Method.RLE;case "upperquartile"->EdgeRNormalization.Method.UPPER_QUARTILE;default->EdgeRNormalization.Method.NONE;};
            var options=new EdgeRNormalization.Options(m,norm.containsKey("reference_column")?((Number)norm.get("reference_column")).intValue()-1:null,num(norm,"logratio_trim",.3),num(norm,"sum_trim",.05),bool(norm,"do_weighting",true),num(norm,"a_cutoff",-1e10),num(norm,"p",.75));
            double[] expected=EdgeRNormalization.normalize(counts,libs,EdgeRNormalization.Edition.EDGER_4_10_7,options).factors();for(int j=0;j<n;j++)equal(factors[j],expected[j],2e-11,"Original normalization factors differ from independent native source implementation");
        }
        if(object(r.get("voom"),"voom").get("normalize_method").equals("none"))for(int i=0;i<counts.length;i++)for(int j=0;j<n;j++)equal(expression[i][j],Math.log((counts[i][j]+.5)/(libs[j]*factors[j]+1)*1e6)/Math.log(2),2e-12,"Original log CPM differs");
    }
    private static void batch(Path root,Map<String,Object> r,LimmaWorkflowFiles.Prepared p,double[][] input,double[][] corrected)throws IOException {
        var z=table(root.resolve("results/batch-design.tsv"),p.samples().size(),((Number)r.get("maximum_design_columns")).intValue()+p.samples().size()*2+1);
        List<String> columns=Arrays.asList(z.header()).subList(1,z.header().length);var names=new ArrayList<String>();for(int j=0;j<columns.size();j++)names.add("n"+(j+1));schema(z,"sample_id",p.samples(),names);
        double[][] design=matrix(root.resolve("results/batch-design.tsv"),"sample_id",p.samples(),names),beta=matrix(root.resolve("results/batch-coefficients.tsv"),"feature_id",p.features(),names);int at=0;
        for(String key:List.of("batch","batch2"))if(r.containsKey(key)) {
            var f=table(root.resolve("worker/"+key+".tsv"),p.samples().size(),2);var levels=strings(r.get(key+"_levels"),key+" levels",2,10000);
            for(int i=0;i<p.samples().size();i++)for(int j=0;j<levels.size()-1;j++)equal(design[i][at+j],f.rows().get(i)[1].equals(levels.get(j))?1:f.rows().get(i)[1].equals(levels.get(levels.size()-1))?-1:0,0,"Original sum-contrast batch design differs");at+=levels.size()-1;
        }
        if(r.containsKey("covariates")){var c=table(root.resolve("worker/covariates.tsv"),p.samples().size(),((Number)r.get("maximum_design_columns")).intValue()+1);for(int j=1;j<c.header().length;j++){double mean=0;for(var row:c.rows())mean+=value(row[j])/c.rows().size();for(int i=0;i<c.rows().size();i++)equal(design[i][at],value(c.rows().get(i)[j])-mean,2e-12,"Original covariate centering differs");at++;}}
        if(at!=columns.size())throw new IOException("Original nuisance-column population differs");
        for(int i=0;i<input.length;i++)for(int j=0;j<p.samples().size();j++){double expected=input[i][j];for(int a=0;a<at;a++)expected-=beta[i][a]*design[j][a];equal(corrected[i][j],expected,2e-11,"Original batch-subtraction arithmetic differs");}
    }
    private static void camera(Path root,Map<String,Object> r,LimmaWorkflowFiles.Prepared p)throws IOException {
        var options=object(r.get("camera"),"camera");boolean correlation=options.get("inter_gene_cor") instanceof List<?> v&&v.size()>1,directional=bool(options,"directional",true);var columns=new ArrayList<String>(List.of("NGenes"));if(correlation)columns.add("Correlation");if(directional)columns.add("Direction");columns.add("PValue");if(p.setIds().size()>1)columns.add("FDR");
        var t=table(root.resolve("results/camera.tsv"),p.setIds().size(),columns.size()+1);var expectedHeader=new ArrayList<String>(List.of("set_id"));expectedHeader.addAll(columns);if(!Arrays.asList(t.header()).equals(expectedHeader)||t.rows().size()!=p.setIds().size())throw new IOException("Original cameraPR schema/population differs");
        var sizes=new LinkedHashMap<String,Integer>();for(String set:p.setIds())sizes.put(set,0);for(var row:table(root.resolve("worker/gene_sets.tsv"),((Number)r.get("maximum_memberships")).intValue(),2).rows())sizes.compute(row[0],(key,n)->Objects.requireNonNull(n)+1);
        int pv=expectedHeader.indexOf("PValue"),fdr=expectedHeader.indexOf("FDR");double[] probabilities=new double[t.rows().size()];var seen=new HashSet<String>();double last=-1;
        for(int i=0;i<t.rows().size();i++){var row=t.rows().get(i);if(!sizes.containsKey(row[0])||!seen.add(row[0])||value(row[1])!=sizes.get(row[0]))throw new IOException("Original set identities/memberships differ");if(directional&&!Set.of("Up","Down").contains(row[expectedHeader.indexOf("Direction")]))throw new IOException("Invalid camera direction");probabilities[i]=value(row[pv]);probability(probabilities[i]);if(bool(options,"sort",true)){if(probabilities[i]<last)throw new IOException("Original camera P sorting differs");last=probabilities[i];}else if(!row[0].equals(p.setIds().get(i)))throw new IOException("Original camera input order differs");if(correlation){int index=p.setIds().indexOf(row[0]);equal(value(row[2]),((Number)list(options.get("inter_gene_cor"),"correlations").get(index)).doubleValue(),0,"Original per-set correlation changed");}}
        if(fdr>=0){double[] adjusted=adjust(probabilities,"BH",r);for(int i=0;i<adjusted.length;i++)precise(value(t.rows().get(i)[fdr]),adjusted[i],2e-12,"Camera full-family BH differs");}
    }
    private static void journals(Path root,Map<String,Object> r,Map<String,Object> report,int genes)throws IOException {
        var snapshots=table(root.resolve("worker/objects-index.tsv"),10000,2);if(!Arrays.equals(snapshots.header(),new String[]{"object","class"})||integer(report,"object_snapshots",-1,1,10000)!=snapshots.rows().size())throw new IOException("Original object-snapshot ledger differs");var snapshotFiles=new HashSet<String>();
        for(int i=0;i<snapshots.rows().size();i++){var row=snapshots.rows().get(i);if(!row[0].startsWith(String.format(Locale.ROOT,"%05d-",i+1))||!row[0].matches("[0-9]+-[A-Za-z0-9-]+")||row[1].isBlank())throw new IOException("Invalid original snapshot identity/class");String name=row[0]+".rds";if(!snapshotFiles.add(name)||!Files.isRegularFile(root.resolve("worker/objects/"+name),LinkOption.NOFOLLOW_LINKS)||Files.size(root.resolve("worker/objects/"+name))<16)throw new IOException("Missing/empty original model snapshot");}
        try(var stream=Files.list(root.resolve("worker/objects"))){if(!stream.map(v->v.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(snapshotFiles))throw new IOException("Original snapshot file population differs");}
        int maximum=Math.toIntExact(Math.min(Integer.MAX_VALUE,(long)genes*100+1000));var index=table(root.resolve("worker/internal-model-index.tsv"),maximum,4);
        if(!Arrays.equals(index.header(),new String[]{"index","label","start","end"})||integer(report,"internal_model_frames",-1,0,maximum)!=index.rows().size())throw new IOException("Original internal-model journal index differs");long end=0;
        for(int i=0;i<index.rows().size();i++){var row=index.rows().get(i);if(integral(row[0])!=i+1||integral(row[2])!=end||integral(row[3])<=end)throw new IOException("Original model journal frame offsets differ");end=integral(row[3]);}
        if(Files.size(root.resolve("worker/internal-model-journal.bin"))!=end)throw new IOException("Original model journal extent differs");
        var c=table(root.resolve("worker/convergence.tsv"),maximum,13);if(!Arrays.equals(c.header(),new String[]{"fit_index","feature_index","iterations","maximum_iterations","tolerance","score_step","relative_deviance","damping_ratio","converged","score1","score2","step1","step2"})||integer(report,"iterative_gamma_fits",-1,0,maximum)!=c.rows().size())throw new IOException("Original convergence record count/schema differs");
        for(int i=0;i<c.rows().size();i++){var row=c.rows().get(i);if(integral(row[0])!=i+1||integral(row[1])<1||integral(row[1])>genes||value(row[8])!=1)throw new IOException("Failed/unknown original per-gene convergence");double tol=value(row[4]),score=special(row[5]),relative=special(row[6]),damping=special(row[7]);if(!(tol>0)||(!(score<tol)&&!(relative<1e-15))||damping>1e15)throw new IOException("Original source convergence criterion is not satisfied");double dot=special(row[9])*special(row[11])+special(row[10])*special(row[12]);if(Double.isFinite(score)&&Double.isFinite(dot))equal(score,dot,2e-12,"Original convergence score/step dot product differs");}
        if(!Files.isRegularFile(root.resolve("worker/objects.rds"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing original final object bundle");
    }
    private static double[][] matrix(Path file,String first,List<String> rows,List<String> columns)throws IOException {var t=table(file,rows.size(),columns.size()+1);schema(t,first,rows,columns);double[][] a=new double[rows.size()][columns.size()];for(int i=0;i<a.length;i++)for(int j=0;j<columns.size();j++)a[i][j]=value(t.rows().get(i)[j+1]);return a;}
    private static SusieWorkflowFiles.Table table(Path file,int rows,int columns)throws IOException{return SusieWorkflowFiles.table(file,rows,columns);}
    private static void schema(SusieWorkflowFiles.Table t,String first,List<String> rows,List<String> columns)throws IOException {var header=new ArrayList<String>(List.of(first));header.addAll(columns);if(!Arrays.asList(t.header()).equals(header)||t.rows().size()!=rows.size())throw new IOException("Original table schema/population differs");for(int i=0;i<rows.size();i++)if(!t.rows().get(i)[0].equals(rows.get(i)))throw new IOException("Original literal identity/order differs");}
    private static double value(String s)throws IOException{return SusieWorkflowFiles.number(s);}
    private static double special(String s)throws IOException {return switch(s){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->value(s);};}
    private static long integral(String s)throws IOException{try{return new BigDecimal(s).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw new IOException("Require an exact integral journal value",e);}}
    private static void probability(double v)throws IOException{if(v<0||v>1)throw new IOException("Invalid original probability");}
    private static double num(Map<String,Object> r,String key,double fallback){return r.get(key) instanceof Number n?n.doubleValue():fallback;}
    private static double[] adjust(double[] p,String method,Map<String,Object> r){return FumaCelltypeAnalysis.adjust(p,FumaCelltypeAnalysis.Correction.valueOf(method.toUpperCase(Locale.ROOT)),new FumaCelltypeAnalysis.Limits(p.length,1,((Number)r.get("maximum_work")).longValue()),()->{});}
    private static void equal(double actual,double expected,double tolerance,String message)throws IOException{if(!Double.isFinite(actual)||!Double.isFinite(expected)||Math.abs(actual-expected)>tolerance*Math.max(1,Math.max(Math.abs(actual),Math.abs(expected))))throw new IOException(message);}
    private static void precise(double actual,double expected,double tolerance,String message)throws IOException {if(!Double.isFinite(actual)||!Double.isFinite(expected))throw new IOException(message);if(expected==0?actual!=0:actual==0||Math.abs(actual-expected)>Math.max(64*Math.ulp(expected),tolerance*Math.abs(expected)))throw new IOException(message);}
    private static void equalMatrices(double[][] a,double[][] b,double tolerance,String message)throws IOException{for(int i=0;i<a.length;i++)for(int j=0;j<a[i].length;j++)equal(a[i][j],b[i][j],tolerance,message);}
}
