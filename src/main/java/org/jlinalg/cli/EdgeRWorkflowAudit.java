/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.differential.EdgeRNormalization;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** R-free population, intake, CPM arithmetic and exported-table consistency checks. */
final class EdgeRWorkflowAudit {
    private EdgeRWorkflowAudit() { }
    static void outputs(Path root,Map<String,Object> r,boolean compare)throws IOException {
        var p=EdgeRWorkflowFiles.prepare(root,r,true);int genes=p.features().size(),samples=p.samples().size();
        Path worker=root.resolve("worker");var report=read(worker.resolve("report.json"));
        if(!"completed-with-warnings".equals(report.get("status"))||!Boolean.TRUE.equals(report.get("results_admitted"))||!Boolean.TRUE.equals(report.get("runtime_ready"))||
            !r.get("profile").equals(report.get("profile"))||!r.get("model").equals(report.get("model"))||integer(report,"features_input",-1,0,genes)!=genes||
            integer(report,"samples",-1,0,samples)!=samples||integer(report,"results",-1,0,256)!=p.resultIds().size()||integer(report,"source_function_guards",-1,0,1000)!=24)
            throw new IOException("Original worker status/model/population/source guards differ");
        if(!"Original unchanged bodies; observational DGEList/matrix glmFit/predFC/glmQLFit dispatch; unchanged namespace".equals(report.get("test_environment"))||
            integer(report,"observed_internal_fits",-1,r.get("model").equals("EXACT")?0:1,100000)<0||
            integer(report,"internal_source_zero_boundaries",-1,0,100000)<0)throw new IOException("Original observational test environment or internal-fit record differs");
        var mask=table(worker,"keep.tsv",genes,2);if(!Arrays.equals(mask.header(),new String[]{"feature_index","keep"})||mask.rows().size()!=genes)throw new IOException("Original filter mask population/header differs");
        var kept=new ArrayList<Integer>();var keepText=new StringBuilder("feature_id\tkeep\n");
        for(int i=0;i<genes;i++) {
            String[] row=mask.rows().get(i);if(!row[0].equals("f"+(i+1))||!Set.of("TRUE","FALSE").contains(row[1]))throw new IOException("Original filter mask identity/value differs");
            if(row[1].equals("TRUE"))kept.add(i);keepText.append(p.features().get(i)).append('\t').append(row[1]).append('\n');
        }
        if(kept.isEmpty()||integer(report,"features_retained",-1,1,genes)!=kept.size())throw new IOException("Original retained-feature population differs");
        if(object(r.get("filter"),"filter").get("method").equals("none")&&kept.size()!=genes)throw new IOException("No-filter request lost original features");
        Path published=root.resolve("results");if(!compare)Files.createDirectory(published);else if(!Files.isDirectory(published,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing published results");
        output(published,"filter.tsv",keepText.toString(),compare);
        var original=table(worker,"counts.tsv",genes,samples+1);var prepared=matrix(worker,"counts-prepared.tsv",kept,samples,"s");
        for(int i=0;i<kept.size();i++)for(int j=0;j<samples;j++)equal(prepared.values()[i][j],value(original.rows().get(kept.get(i))[j+1]),0,"Retained counts changed");
        output(published,"counts.tsv",restore(prepared.table(),kept,p.features(),p.samples()),compare);
        var normalization=table(worker,"sample-normalization.tsv",samples,4);
        if(!Arrays.equals(normalization.header(),new String[]{"sample_index","library_size","norm_factor","effective_library_size"})||normalization.rows().size()!=samples)throw new IOException("Original normalization schema differs");
        double[] effective=new double[samples];var normText=new StringBuilder("sample_id\tlibrary_size\tnorm_factor\teffective_library_size\n");
        for(int j=0;j<samples;j++) {
            var row=normalization.rows().get(j);if(!row[0].equals("s"+(j+1)))throw new IOException("Original sample order differs");
            double lib=value(row[1]),factor=value(row[2]);effective[j]=value(row[3]);
            if(!(lib>0&&factor>0&&effective[j]>0))throw new IOException("Nonpositive original library/factor");equal(effective[j],lib*factor,0,"Effective library arithmetic differs");
            normText.append(p.samples().get(j)).append('\t').append(String.join("\t",Arrays.copyOfRange(row,1,4))).append('\n');
        }
        output(published,"normalization.tsv",normText.toString(),compare);
        var edition=EdgeRNormalization.Edition.valueOf("EDGER_"+text(r,"profile").split("-")[1].replace('.','_'));
        for(String name:List.of("cpm.tsv","log-cpm.tsv")) {
            var actual=matrix(worker,name,kept,samples,"s");double[][] expected=EdgeRNormalization.cpm(prepared.values(),effective,edition,name.startsWith("log"),2);
            for(int i=0;i<kept.size();i++)for(int j=0;j<samples;j++)equal(actual.values()[i][j],expected[i][j],5e-13,"Original CPM arithmetic differs");
            output(published,name,restore(actual.table(),kept,p.features(),p.samples()),compare);
        }
        var dispersion=table(worker,"dispersion.tsv",kept.size(),4);identities(dispersion,kept);
        if(!Arrays.equals(dispersion.header(),new String[]{"feature_index","common","trended","tagwise"}))throw new IOException("Dispersion header differs");
        for(var row:dispersion.rows())for(int j=1;j<row.length;j++)if(!row[j].equals("NA")&&value(row[j])<0)throw new IOException("Negative original dispersion");
        output(published,"dispersion.tsv",restore(dispersion,kept,p.features(),List.of("common","trended","tagwise")),compare);
        Matrix coefficients=null;
        if(!r.get("model").equals("EXACT")) {
            coefficients=matrix(worker,"coefficients.tsv",kept,p.coefficients().size(),"d");
            output(published,"coefficients.tsv",restore(coefficients.table(),kept,p.features(),p.coefficients()),compare);
            var diagnostics=table(worker,"fit-diagnostics.tsv",kept.size(),8);identities(diagnostics,kept);
            if(!Arrays.equals(diagnostics.header(),new String[]{"feature_index","deviance","residual_df","iterations","failed","prior_df","posterior_variance","residual_df_zeros"}))throw new IOException("Fit diagnostics header differs");
            for(var row:diagnostics.rows()) {
                if(!Set.of("NA","FALSE").contains(row[4]))throw new IOException("Original full fit failed flag is not admitted");
                if(value(row[1])<0||value(row[2])<=0)throw new IOException("Invalid original fit deviance/residual degrees of freedom");
                for(int j:List.of(3,5,6,7))if(!Set.of("NA","Inf").contains(row[j])&&value(row[j])<0)throw new IOException("Invalid original fit diagnostic");
                if(!row[3].equals("NA")&&value(row[3])>250)throw new IOException("Original full fit exhausted the source 250-iteration limit");
            }
            output(published,"fit-diagnostics.tsv",restore(diagnostics,kept,p.features(),Arrays.asList(diagnostics.header()).subList(1,8)),compare);
        }
        Path comparisonPath=worker.resolve("comparisons.json");if(Files.size(comparisonPath)>2000000)throw new IOException("Comparison ledger exceeds two-million-byte metadata capacity");
        var comparisons=list(SimpleJson.parse(Files.readString(comparisonPath,StandardCharsets.UTF_8)),"comparisons");if(comparisons.size()!=p.resultIds().size())throw new IOException("Original comparison population differs");
        var index=new StringBuilder("result_id\tfile\n");int k=0;
        for(Object raw:list(r.get("results"),"results")) {
            var selector=object(raw,"result");k++;String file="result-"+k+".tsv";var result=table(worker,file,kept.size(),p.coefficients().size()+6);identities(result,kept);
            List<String> header=Arrays.asList(result.header());int pv=header.indexOf("PValue");
            if(pv<2||!header.get(pv-1).equals(r.get("model").equals("EXACT")?"logCPM":r.get("model").equals("GLM_LRT")?"LR":"F")||!header.contains("logCPM"))throw new IOException("Original result schema differs");
            String adjustment=text(r,"adjust_method");int adjusted=adjustment.equals("none")?-1:header.indexOf(Set.of("BH","BY").contains(adjustment)?"FDR":"FWER");
            if(header.size()!=pv+1+(adjusted<0?0:1)||!adjustment.equals("none")&&adjusted!=pv+1)throw new IOException("Original adjustment schema differs");
            var expectedHeader=new ArrayList<String>();expectedHeader.add("feature_index");
            List<?> selectedCoefficients=selector.containsKey("coefficients")?list(selector.get("coefficients"),"coefficients"):List.of();
            if(selectedCoefficients.size()>1)for(Object c:selectedCoefficients)expectedHeader.add("logFC.d"+((Number)c).intValue());else expectedHeader.add("logFC");
            expectedHeader.add("logCPM");if(!r.get("model").equals("EXACT"))expectedHeader.add(r.get("model").equals("GLM_LRT")?"LR":"F");expectedHeader.add("PValue");
            if(adjusted>=0)expectedHeader.add(Set.of("BH","BY").contains(adjustment)?"FDR":"FWER");if(!expectedHeader.equals(header))throw new IOException("Original statistic/selector headers differ");
            var comparison=object(comparisons.get(k-1),"comparison");keys(comparison,Set.of("result_index","comparison","columns","df_test_unique"));
            if(integer(comparison,"result_index",-1,1,256)!=k||!same(comparison.get("columns"),header.subList(1,header.size())))throw new IOException("Original comparison index/columns differ");
            strings(comparison.get("comparison"),"original comparison terms",1,1024);
            if(r.get("model").equals("EXACT")){if(comparison.get("df_test_unique")!=null)throw new IOException("Exact test has no GLM test degrees of freedom");}
            else {var df=list(comparison.get("df_test_unique"),"df_test_unique");if(df.size()!=1||!(df.get(0) instanceof Number n)||n.doubleValue()!=Math.max(1,selectedCoefficients.size()))throw new IOException("Original test degrees of freedom differ from the declared selector");}
            double[] probabilities=new double[kept.size()];
            for(int i=0;i<kept.size();i++) {
                var row=result.rows().get(i);for(int j=1;j<row.length;j++)value(row[j]);probabilities[i]=value(row[pv]);
                if(probabilities[i]<0||probabilities[i]>1||adjusted>=0&&(value(row[adjusted])<0||value(row[adjusted])>1))throw new IOException("Original probability outside[0,1]");
                if(coefficients!=null) {
                    List<?> selected=selector.containsKey("coefficients")?list(selector.get("coefficients"),"coefficients"):List.of();
                    if(!selected.isEmpty())for(int j=0;j<selected.size();j++)equal(value(row[j+1]),coefficients.values()[i][((Number)selected.get(j)).intValue()-1]/Math.log(2),5e-12,"Coefficient/logFC relation differs");
                    else {double beta=0;var contrast=list(selector.get("contrast"),"contrast");for(int j=0;j<contrast.size();j++)beta+=coefficients.values()[i][j]*((Number)contrast.get(j)).doubleValue();equal(value(row[1]),beta/Math.log(2),5e-12,"Contrast/logFC relation differs");}
                }
            }
            if(adjusted>=0&&Set.of("BH","BY","bonferroni").contains(adjustment)) {
                double[] expected=adjust(probabilities,adjustment);for(int i=0;i<expected.length;i++)equal(value(result.rows().get(i)[adjusted]),expected[i],5e-13,"Full-tested-population adjustment differs");
            }
            var columns=new ArrayList<>(header.subList(1,header.size()));
            for(int j=0;j<columns.size();j++)if(columns.get(j).matches("logFC[.]d[0-9]+"))columns.set(j,"logFC."+p.coefficients().get(Integer.parseInt(columns.get(j).substring(7))-1));
            output(published,file,restore(result,kept,p.features(),columns),compare);index.append(p.resultIds().get(k-1)).append('\t').append(file).append('\n');
        }
        output(published,"index.tsv",index.toString(),compare);
        var runtime=read(worker.resolve("runtime.json"));
        if(!"4.6.1".equals(runtime.get("R"))||!text(r,"profile").split("-")[1].equals(runtime.get("edgeR"))||list(runtime.get("function_guards"),"function_guards").size()!=24)throw new IOException("Recorded runtime identity differs");
        var status=Files.readAllLines(worker.resolve("worker-status.tsv"),StandardCharsets.UTF_8);
        if(status.size()!=2||!status.get(0).equals("status\tdetail\trequired_action")||!status.get(1).startsWith("completed-with-warnings\t"))throw new IOException("Original final status differs");
        Set<String> allowed=new HashSet<>(Set.of("runtime-paths.txt","runtime-bindings.json","parameters.json","counts.tsv","groups.tsv","stdout.log","stderr.log",
            "runtime.json","SESSION.txt","objects.rds","rng-before.rds","rng-after.rds","keep.tsv","counts-prepared.tsv","sample-normalization.tsv","cpm.tsv","log-cpm.tsv",
            "dispersion.tsv","comparisons.json","warnings.txt","messages.txt","report.json","worker-status.tsv"));
        for(String optional:List.of("design","libraries","norm_factors"))if(r.containsKey(optional))allowed.add(optional+".tsv");
        if(!r.get("model").equals("EXACT")){allowed.add("coefficients.tsv");allowed.add("fit-diagnostics.tsv");}
        for(int i=1;i<=p.resultIds().size();i++)allowed.add("result-"+i+".tsv");
        if(!EdgeRWorkflowRuntime.footprint(worker).keySet().equals(allowed))throw new IOException("Original worker file population differs");
        Set<String> publicFiles=new HashSet<>(Set.of("filter.tsv","counts.tsv","normalization.tsv","cpm.tsv","log-cpm.tsv","dispersion.tsv","index.tsv"));
        if(!r.get("model").equals("EXACT")){publicFiles.add("coefficients.tsv");publicFiles.add("fit-diagnostics.tsv");}
        for(int i=1;i<=p.resultIds().size();i++)publicFiles.add("result-"+i+".tsv");
        if(!EdgeRWorkflowRuntime.footprint(published).keySet().equals(publicFiles))throw new IOException("Published result file population differs");
        if(compare) {
            Set<String> inputs=new HashSet<>();for(String key:EdgeRWorkflowContracts.INPUTS)if(r.containsKey(key))inputs.add(key);
            if(!EdgeRWorkflowRuntime.footprint(root.resolve("inputs")).keySet().equals(inputs))throw new IOException("Captured raw input population differs");
            if(!EdgeRWorkflowRuntime.footprint(root.resolve("source")).keySet().equals(Set.of("reference/source.zip","reference/SOURCE-PROFILE.json","edger_workflow.R")))throw new IOException("Captured source population differs");
            Set<String> top=new HashSet<>(Set.of("source-request.json","request.json","effective-request.json","analysis-status.json","manifest.json","WARNINGS.md","capture-files.json","inputs","worker","source","results"));
            if(Files.exists(root.resolve("replay-source.json"))) {
                var replay=read(root.resolve("replay-source.json"));keys(replay,Set.of("analysis","seal_sha256"));text(replay,"analysis");
                if(!text(replay,"seal_sha256").matches("[0-9a-f]{64}"))throw new IOException("Invalid replay parent seal");top.add("replay-source.json");
            }
            try(var paths=Files.list(root)){if(!paths.map(path->path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(top))throw new IOException("Capture root population differs");}
        }
    }
    private record Matrix(SusieWorkflowFiles.Table table,double[][] values) { }
    private static Matrix matrix(Path worker,String name,List<Integer> kept,int columns,String prefix)throws IOException {
        var table=table(worker,name,kept.size(),columns+1);identities(table,kept);
        if(table.header().length!=columns+1)throw new IOException("Original matrix width differs");
        for(int j=0;j<columns;j++)if(!table.header()[j+1].equals(prefix+(j+1)))throw new IOException("Original matrix column identity differs");
        double[][] result=new double[kept.size()][columns];for(int i=0;i<kept.size();i++)for(int j=0;j<columns;j++)result[i][j]=value(table.rows().get(i)[j+1]);return new Matrix(table,result);
    }
    private static SusieWorkflowFiles.Table table(Path root,String name,int rows,int columns)throws IOException {return SusieWorkflowFiles.table(root.resolve(name),rows,columns);}
    private static void identities(SusieWorkflowFiles.Table table,List<Integer> kept)throws IOException {
        if(!table.header()[0].equals("feature_index")||table.rows().size()!=kept.size())throw new IOException("Original retained feature schema/population differs");
        for(int i=0;i<kept.size();i++)if(!table.rows().get(i)[0].equals("f"+(kept.get(i)+1)))throw new IOException("Original retained feature identity/order differs");
    }
    private static String restore(SusieWorkflowFiles.Table table,List<Integer> kept,List<String> features,List<String> columns) {
        var text=new StringBuilder("feature_id\t").append(String.join("\t",columns)).append('\n');
        for(int i=0;i<kept.size();i++)text.append(features.get(kept.get(i))).append('\t').append(String.join("\t",Arrays.copyOfRange(table.rows().get(i),1,table.header().length))).append('\n');return text.toString();
    }
    private static double value(String text)throws IOException{return SusieWorkflowFiles.number(text);}
    private static void equal(double actual,double expected,double tolerance,String message)throws IOException {if(!Double.isFinite(expected)||Math.abs(actual-expected)>tolerance*Math.max(1,Math.max(Math.abs(actual),Math.abs(expected))))throw new IOException(message);}
    private static double[] adjust(double[] p,String method) {
        double[] answer=new double[p.length];if(method.equals("bonferroni")){for(int i=0;i<p.length;i++)answer[i]=Math.min(1,p[i]*p.length);return answer;}
        double correction=1;if(method.equals("BY")){correction=0;for(int i=1;i<=p.length;i++)correction+=1d/i;}
        Integer[] order=new Integer[p.length];for(int i=0;i<p.length;i++)order[i]=i;Arrays.sort(order,Comparator.comparingDouble(i->p[i]));
        double minimum=1;for(int i=p.length-1;i>=0;i--){minimum=Math.min(minimum,p[order[i]]*p.length/(i+1)*correction);answer[order[i]]=minimum;}return answer;
    }
    private static void output(Path root,String name,String content,boolean compare)throws IOException {
        Path path=root.resolve(name);byte[] raw=content.getBytes(StandardCharsets.UTF_8);
        if(compare){if(!Arrays.equals(raw,Files.readAllBytes(path)))throw new IOException("Published literal result differs: "+name);}
        else Files.write(path,raw,StandardOpenOption.CREATE_NEW);
    }
}
