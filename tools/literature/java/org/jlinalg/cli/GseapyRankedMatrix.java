/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.enrichment.GseapyRankedStatistics;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Audit complete pre-formatting original ranked families, conditional on original null draws. */
public final class GseapyRankedMatrix {
    private GseapyRankedMatrix(){ }
    private static long numeric,walks,nullFamilies,singleFamilies;
    private static double scalar(Object value)throws Exception{return ScfatesWorkflowData.scalar(value);}
    private static double[] vector(Object value)throws Exception{var raw=list(value);double[] out=new double[raw.size()];for(int i=0;i<out.length;i++)out[i]=scalar(raw.get(i));return out;}
    private static void same(double actual,double expected,boolean exact,String where)throws Exception{
        require(Double.isNaN(expected)?Double.isNaN(actual):!Double.isFinite(expected)?actual==expected:exact?Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits(expected):Double.isFinite(actual)&&Math.abs(actual-expected)<=2e-12*Math.max(1,Math.abs(expected)),"Original ranked state differs: "+where+" "+actual+" != "+expected);numeric++;
    }
    public static void main(String[] args)throws Exception{
        require(args.length==2,"Require complete original controls and NEW output JSON");Path base=Path.of(args[0]),out=Path.of(args[1]);require(!Files.exists(out),"Existing ranked audit protected");var original=GseapyWorkflowRuntime.read(base.resolve("COMPLETED.json"),16777216);require(text(original.get("status")).equals("passed-33-complete-original-GSEApy-workflow-controls-only"),"Complete original controls unavailable");int cases=0;
        for(String edition:List.of("1.0.4","1.1.2","1.1.3","1.1.8")){
            var request=GseapyWorkflowRuntime.read(base.resolve(edition).resolve("request.json"),16777216);
            for(Object raw:list(request.get("analyses"))){
                var specification=object(raw);String method=text(specification.get("method"));if(method.equals("enrichr"))continue;Path home=base.resolve(edition).resolve(text(specification.get("id")));var observed=GseapyWorkflowRuntime.read(home.resolve("observed-backend-summary.json"),67108864);var summary=list(observed.get("summaries"));var metric=object(observed.get("metric"));var metricNames=object(observed.get("metric_dict"));var options=object(specification.get("options"));
                if(!method.equals("gsva")){
                    double exponent=finite(options.get("weight"),0,8);double[] scores=new double[summary.size()];
                    for(int j=0;j<summary.size();j++){
                        var s=object(summary.get(j));String name=s.get("index")==null?method:text(metricNames.get(Long.toString(integer(s.get("index"),0,1000000))));var series=object(metric.get(name));var values=ScfatesWorkflowData.vector(home,series.get("data"),10000000);double[] weights=new double[values.size()];for(int k=0;k<weights.length;k++)weights[k]=Math.pow(Math.abs(values.number(k)),exponent);var rawHits=list(s.get("hits"));int[] hits=new int[rawHits.size()];for(int k=0;k<hits.length;k++)hits[k]=Math.toIntExact(integer(rawHits.get(k),0,weights.length-1));var computed=GseapyRankedStatistics.walk(weights,hits);double[] expected=vector(s.get("run_es")),actual=computed.running();
                        if(method.equals("gsea")&&!edition.equals("1.1.8")&&text(options.get("permutation_type")).equals("phenotype")){double[] signed=new double[values.size()];for(int k=0;k<signed.length;k++)signed[k]=values.number(k);actual=GseapyRankedStatistics.rawRunning(signed,hits);}
                        if(expected.length>0){require(expected.length==actual.length,"Original dense running family incomplete");for(int k=0;k<actual.length;k++)same(actual[k],expected[k],false,"dense walk "+edition+"/"+specification.get("id")+"["+k+"]");}
                        scores[j]=scalar(s.get("es"));same(method.equals("ssgsea")?computed.singleSampleScore():computed.sparseScore(),scores[j],false,"observed ES "+edition+"/"+method);walks++;
                    }
                    if(method.equals("ssgsea")&&integer(options.get("permutation_num"),0,1000000)==0){double[] normalized=GseapyRankedStatistics.normalizeSingleSample(scores);for(int j=0;j<summary.size();j++)same(normalized[j],scalar(object(summary.get(j)).get("nes")),true,"complete ssGSEA normalization");singleFamilies++;}
                }
                if(!method.equals("gsva")&&integer(options.get("permutation_num"),0,1000000)>0){
                    double[] es=new double[summary.size()];double[][] nulls=new double[summary.size()][];for(int j=0;j<summary.size();j++){var s=object(summary.get(j));es[j]=scalar(s.get("es"));nulls[j]=vector(s.get("esnull"));}
                    var computed=GseapyRankedStatistics.fromNulls(es,nulls);double[] nes=computed.normalizedScores(),p=computed.nominalP(),q=computed.fdr(),fwer=computed.familywiseP();
                    for(int j=0;j<summary.size();j++){var s=object(summary.get(j));same(nes[j],scalar(s.get("nes")),true,"NES");same(p[j],scalar(s.get("pval")),true,"nominal P");same(q[j],scalar(s.get("fdr")),true,"FDR");same(fwer[j],scalar(s.get("fwerp")),true,"FWER");}nullFamilies++;
                }
                cases++;
            }
        }
        require(cases==29&&walks==228&&nullFamilies==12&&singleFamilies==8,"Complete ranked families missing: "+cases+"/"+walks+"/"+nullFamilies+"/"+singleFamilies);NextflowFiles.writeJson(out,Map.of("status","passed-native-observed-ranked-and-original-null-arithmetic-only","ranked_cases",cases,"walks",walks,"null_families",nullFamilies,"single_sample_families",singleFamilies,"numeric_states",numeric,"independent_permutations",false,"independent_gsva_kernel",false,"full_adapter_admitted",false));System.out.println("Validated "+numeric+" observed ranked/null states; original draws and GSVA kernel remain upstream");
    }
}
