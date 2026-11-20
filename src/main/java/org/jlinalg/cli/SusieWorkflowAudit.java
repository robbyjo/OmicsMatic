/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** R-free exported-structure audit; does not deserialize RDS or independently refit SuSiE. */
final class SusieWorkflowAudit {
    private SusieWorkflowAudit() { }
    static void outputs(Path root,Map<String,Object> request)throws IOException {
        Path worker=root.resolve("worker");var p=read(worker.resolve("parameters.json"));var report=read(worker.resolve("report.json"),4_000_000);
        if(!"completed-with-warnings".equals(report.get("status"))||!Boolean.TRUE.equals(report.get("results_admitted"))||
            !"original-susieR".equals(report.get("statistical_engine"))||!Boolean.FALSE.equals(report.get("scientific_source_overlay"))||
            !Boolean.FALSE.equals(report.get("full_host_or_DLL_source_authentication"))||!Boolean.FALSE.equals(report.get("independent_statistical_refit_by_verifier"))||
            !Boolean.TRUE.equals(report.get("original_call_started"))||!Boolean.TRUE.equals(report.get("original_call_returned"))||
            !Boolean.TRUE.equals(report.get("converged"))||!Objects.equals(request.get("model"),report.get("model"))||!Objects.equals(request.get("profile"),report.get("profile")))throw new IOException("Worker did not admit a converged original fit with the declared scope/model");
        int count=(int)integer(report,"variables",0,1,20000),effects=(int)integer(report,"effects",0,1,count);
        if(count!=integer(p,"variables",0,1,20000)||effects>Math.min(count,integer(request,"effects",0,1,10000)))throw new IOException("Original fitted dimensions differ");
        integer(report,"niter",0,1,1_000_000_000L);positive(report.get("residual_variance"),"residual variance");
        boolean missing=Boolean.TRUE.equals(report.get("intercept_source_missing"));String intercept=text(report,"intercept_token");
        if(missing?!intercept.equals("NA"):!Double.isFinite(SusieWorkflowFiles.number(intercept)))throw new IOException("Invalid typed original intercept");
        String model=text(request,"model");String method=switch(model){case "INDIVIDUAL"->"susie";case "SUFFICIENT","SUFFICIENT_BETA_SE"->"susie_suff_stat";default->"susie_rss";};
        var call=read(worker.resolve("original-call.json"));if(!method.equals(call.get("method"))||!method.equals(report.get("original_method"))||!Boolean.TRUE.equals(call.get("no_source_function_environment_changed")))throw new IOException("Actual original entry point differs");
        var profile=object(object(read(root.resolve("source/reference/SOURCE-PROFILE.json"),4_000_000).get("profiles"),"profiles").get(text(request,"profile")),"profile");
        var runtime=read(worker.resolve("runtime.json"),4_000_000);
        if(!same(profile.get("required_packages"),runtime.get("packages"))||!Objects.equals(profile.get("R_version"),runtime.get("R_version"))||
           !Objects.equals(request.get("profile"),runtime.get("profile"))||!Boolean.TRUE.equals(runtime.get("source_bodies_formals_equal"))||
           !same(profile.get("guarded_functions"),runtime.get("guarded_functions"))||
           integer(report,"source_function_guards",0,1,10000)!=list(profile.get("guarded_functions"),"source functions").size())throw new IOException("Original runtime/source guard inventory differs");
        if(!Files.isRegularFile(worker.resolve("original-fit.rds"),LinkOption.NOFOLLOW_LINKS)||Files.size(worker.resolve("original-fit.rds"))==0||
           !Files.isRegularFile(worker.resolve("original-function-bodies.txt")))throw new IOException("Original raw model/source capture is missing");
        var prepared=SusieWorkflowFiles.table(worker.resolve("variants.tsv"),count,4);if(prepared.rows().size()!=count)throw new IOException("Prepared identity count differs");
        var ids=prepared.rows().stream().map(row->row[0]).toList();
        if(!same(call.get("variable_order"),ids))throw new IOException("Original-call identity order differs");
        var variants=readTable(worker,"variant-results.tsv",count,4,"variant_index","variant_id","pip","posterior_mean");
        if(variants.rows().size()!=count)throw new IOException("Incomplete variant output");
        for(int j=0;j<count;j++){String[] row=variants.rows().get(j);index(row[0],j+1);if(!row[1].equals(ids.get(j)))throw new IOException("Variant output identity changed");probability(row[2],false);SusieWorkflowFiles.number(row[3]);}
        long expected=(long)count*effects;if(expected>Integer.MAX_VALUE)throw new IOException("Effect output row capacity exceeded");
        var values=readTable(worker,"effect-results.tsv",(int)expected,7,"effect_index","variant_index","variant_id","alpha","mu","mu2","log_bayes_factor");
        if(values.rows().size()!=expected)throw new IOException("Incomplete effect-by-variant output");
        double[][] alpha=new double[effects][count];
        for(int l=0;l<effects;l++){
            double sum=0;for(int j=0;j<count;j++){String[] row=values.rows().get(l*count+j);index(row[0],l+1);index(row[1],j+1);if(!row[2].equals(ids.get(j)))throw new IOException("Effect variant identity changed");alpha[l][j]=probability(row[3],false);sum+=alpha[l][j];SusieWorkflowFiles.number(row[4]);if(SusieWorkflowFiles.number(row[5])<0)throw new IOException("Negative posterior second moment");logValue(row[6]);}
            if(Math.abs(sum-1)>1e-8)throw new IOException("Original alpha row does not sum to one");
        }
        var summary=readTable(worker,"effect-summary.tsv",effects,3,"effect_index","prior_variance","log_bayes_factor");if(summary.rows().size()!=effects)throw new IOException("Incomplete effect summary");
        for(int l=0;l<effects;l++){String[] row=summary.rows().get(l);index(row[0],l+1);if(SusieWorkflowFiles.number(row[1])<0)throw new IOException("Negative prior variance");logValue(row[2]);}
        int trace=(int)integer(report,"elbo_entries",0,1,1_000_000);
        var elbo=readTable(worker,"elbo.tsv",trace,2,"iteration","elbo");if(elbo.rows().size()!=trace)throw new IOException("Incomplete objective trace");
        for(int i=0;i<trace;i++){index(elbo.rows().get(i)[0],i+1);SusieWorkflowFiles.number(elbo.rows().get(i)[1]);}
        int sets=(int)integer(report,"credible_sets",0,0,effects);
        var cs=readTable(worker,"credible-set-summary.tsv",sets,8,"set_index","set_id","effect_index","size","coverage","min_abs_corr","mean_abs_corr","median_abs_corr");
        if(cs.rows().size()!=sets)throw new IOException("Incomplete credible-set summary");
        var members=readTable(worker,"credible-sets.tsv",(int)expected,6,"set_index","set_id","effect_index","variant_index","variant_id","alpha");
        int offset=0;var seenSetNames=new HashSet<String>();var seenEffects=new HashSet<Integer>();
        for(int k=0;k<sets;k++){
            String[] row=cs.rows().get(k);index(row[0],k+1);SusieWorkflowFiles.exactIdentity(row[1]);
            int effect=boundedIndex(row[2],effects),size=boundedIndex(row[3],count);
            if(!seenSetNames.add(row[1])||!seenEffects.add(effect))throw new IOException("Duplicate credible-set identity/effect");
            double coverage=probability(row[4],true);if(coverage+1e-8<((Number)request.get("coverage")).doubleValue())throw new IOException("Credible-set mass below requested coverage");
            double minimum=probability(row[5],false);probability(row[6],false);probability(row[7],false);
            if(minimum+1e-10<((Number)request.get("min_abs_corr")).doubleValue())throw new IOException("Credible-set purity below requested filter");
            var selected=new HashSet<Integer>();double mass=0;
            for(int i=0;i<size;i++){
                if(offset>=members.rows().size())throw new IOException("Incomplete credible-set membership");String[] m=members.rows().get(offset++);index(m[0],k+1);
                if(!m[1].equals(row[1]))throw new IOException("Credible-set label changed");index(m[2],effect);int j=boundedIndex(m[3],count);
                if(!selected.add(j)||!m[4].equals(ids.get(j-1)))throw new IOException("Credible-set variant identity changed");
                double value=probability(m[5],false);if(Double.doubleToLongBits(value)!=Double.doubleToLongBits(alpha[effect-1][j-1]))throw new IOException("Credible-set probability differs from original effect row");mass+=value;
            }
            if(Math.abs(mass-coverage)>1e-8)throw new IOException("Credible-set mass differs from member alpha values");
        }
        if(offset!=members.rows().size())throw new IOException("Extra credible-set membership rows");
        list(report.get("warnings"),"warning ledger");
    }
    private static SusieWorkflowFiles.Table readTable(Path worker,String file,int rows,int columns,String...header)throws IOException {
        var table=SusieWorkflowFiles.table(worker.resolve(file),rows,columns);if(!Arrays.equals(table.header(),header))throw new IOException("Invalid output columns: "+file);return table;
    }
    private static double positive(Object value,String field)throws IOException {if(!(value instanceof Number number)||!(number.doubleValue()>0)||!Double.isFinite(number.doubleValue()))throw new IOException("Invalid source "+field);return number.doubleValue();}
    private static void index(String value,int expected)throws IOException {if(!value.equals(Integer.toString(expected)))throw new IOException("Original source row index/order changed");}
    private static int boundedIndex(String value,int maximum)throws IOException {try{int x=Integer.parseInt(value);if(x<1||x>maximum||!value.equals(Integer.toString(x)))throw new NumberFormatException();return x;}catch(NumberFormatException error){throw new IOException("Invalid original index",error);}}
    private static double probability(String value,boolean mass)throws IOException {double x=SusieWorkflowFiles.number(value);if(x<0||x>(mass?1+1e-8:1))throw new IOException("Probability outside the admitted source range");return x;}
    private static void logValue(String value)throws IOException {if(!value.equals("-Inf"))SusieWorkflowFiles.number(value);}
}
