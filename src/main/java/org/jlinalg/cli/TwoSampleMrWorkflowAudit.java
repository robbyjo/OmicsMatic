/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.mr.TwoSampleMrStatistics;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Saved-structure audit and independent native checks of deterministic MR fields. */
final class TwoSampleMrWorkflowAudit {
    private TwoSampleMrWorkflowAudit() { }
    static void outputs(Path root,Map<String,Object> r,boolean compare)throws IOException {
        Path worker=root.resolve("worker");var report=read(worker.resolve("report.json"));var runtime=read(worker.resolve("runtime-profile.json"));
        if(!"completed-with-warnings".equals(report.get("status"))||!Boolean.TRUE.equals(report.get("results_admitted"))||!Boolean.FALSE.equals(report.get("original_namespace_rewritten"))||integer(report,"source_functions_guarded",0,152,152)!=integer(runtime,"source_functions_guarded",0,152,152))throw new IOException("Original source guards/status differ");
        if(!"4.6.1".equals(runtime.get("R"))||!"TwoSampleMR".equals(runtime.get("package"))||!"0.7.14".equals(runtime.get("version"))||!TwoSampleMrStatistics.SOURCE_COMMIT.equals(runtime.get("commit"))||!Boolean.FALSE.equals(runtime.get("namespace_rewritten")))throw new IOException("Original runtime profile differs");
        var calls=list(report.get("calls"),"calls");if(calls.isEmpty()||calls.size()>6)throw new IOException("Original call population differs");
        var labels=new HashSet<String>();for(Object item:calls) {
            var call=object(item,"call");String label=text(call,"label"),folder=text(call,"folder");
            if(!folder.matches("objects/[0-9]{3}-[a-z_]+")||!labels.add(label)||!Boolean.TRUE.equals(call.get("complete"))||call.containsKey("error"))throw new IOException("Failed/repeated/unsafe original call");
            for(String member:List.of("arguments.rds","rng-before.rds","original-return.rds","rng-after.rds"))if(!Files.isRegularFile(worker.resolve(folder).resolve(member),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing complete original call object: "+member);
        }
        String op=text(r,"operation");var required=new HashSet<String>();
        if(!op.equals("MR"))required.add(op.equals("MV_HARMONISE")?"mv_harmonise_data":"harmonise_data");if(TwoSampleMrWorkflowContracts.mr(op))required.add("mr");
        if(!labels.containsAll(required))throw new IOException("Original operation calls missing");
        var tables=object(report.get("tables"),"tables");if(tables.isEmpty()||tables.size()>32)throw new IOException("Unexpected original export population");
        if(tables.containsKey("method-list.tsv")&&!Arrays.equals(Files.readAllBytes(worker.resolve("method-list.tsv")),TwoSampleMrWorkflowRuntime.resource("twosamplemr-workflow/METHODS.tsv")))throw new IOException("Original method-to-provider mapping differs from the source-guarded reference");
        var loaded=new HashMap<String,TwoSampleMrWorkflowFiles.Table>();for(var entry:tables.entrySet()) {
            String name=entry.getKey();if(!name.matches("[A-Za-z0-9_.-]+[.]tsv"))throw new IOException("Unsafe export path");
            var metadata=object(entry.getValue(),"table metadata");var table=TwoSampleMrWorkflowFiles.table(worker.resolve(name),r);
            if(table.rows().size()!=integer(metadata,"rows",-1,0,2000000)||!table.columns().equals(strings(metadata.get("columns"),"table columns",1,1024)))throw new IOException("Original table shape differs");
            if(list(metadata.get("types"),"types").size()!=table.columns().size()||list(metadata.get("row_names"),"row names").size()!=table.rows().size())throw new IOException("Original metadata dimensions differ");
            loaded.put(name,table);
        }
        TwoSampleMrWorkflowFiles.Table dat=op.equals("MR")?TwoSampleMrWorkflowFiles.table(root.resolve("inputs/harmonised"),r):loaded.get("harmonised.tsv");
        if(op.equals("MV_HARMONISE")) {
            var members=object(report.get("mv_members"),"original multivariable members");
            if(!members.keySet().equals(Set.of("exposure_beta","exposure_pval","exposure_se","outcome_beta","outcome_pval","outcome_se","expname","outname")))throw new IOException("Original multivariable member population differs");
            var beta=loaded.get("mv-outcome_beta.tsv");var names=loaded.get("mv-expname.tsv");if(beta==null||names==null||beta.rows().isEmpty()||names.rows().size()<2)throw new IOException("No complete multivariable instruments/exposures");
            int n=beta.rows().size(),exposures=names.rows().size();List<String> identities=null;
            for(String member:List.of("exposure_beta","exposure_pval","exposure_se")) {
                var matrix=loaded.get("mv-"+member+".tsv");if(matrix==null||matrix.rows().size()!=n||matrix.columns().size()!=exposures)throw new IOException("Original multivariable matrix dimensions differ");
                var definition=object(members.get(member),"multivariable member");if(integer(definition,"length",-1,0,200000000)!=(long)n*exposures||!same(definition.get("dimensions"),List.of(n,exposures)))throw new IOException("Original matrix metadata differs");
                var metadata=object(tables.get("mv-"+member+".tsv"),"matrix metadata");var rows=strings(metadata.get("row_names"),"instrument identities",1,1000000);
                if(identities==null)identities=rows;else if(!identities.equals(rows))throw new IOException("Original multivariable instrument order differs across matrices");
                if(!member.equals("exposure_pval"))for(var row:matrix.rows())for(String value:row){double v=TwoSampleMrWorkflowFiles.number(value);if(!Double.isFinite(v)||member.equals("exposure_se")&&v<=0)throw new IOException("Unavailable/nonpositive multivariable effect/SE field");}
            }
            for(String member:List.of("outcome_beta","outcome_se","outcome_pval")) {
                var definition=object(members.get(member),"multivariable member");boolean absent="NULL".equals(definition.get("type"));String file="mv-"+member+".tsv";
                if(member.equals("outcome_pval")){boolean supplied=TwoSampleMrWorkflowFiles.table(root.resolve("inputs/outcome"),r).columns().contains("pval.outcome");if(absent==supplied)throw new IOException("Original outcome-p-value availability differs from literal input; no fabricated vector");}
                if(absent){if(!member.equals("outcome_pval")||loaded.containsKey(file)||integer(definition,"length",-1,0,0)!=0)throw new IOException("An absent original member was fabricated");continue;}
                var vector=loaded.get(file);if(vector==null||vector.rows().size()!=n||vector.columns().size()!=1||integer(definition,"length",-1,1,1000000)!=n)throw new IOException("Original outcome-vector dimensions differ");
                if(!member.equals("outcome_pval"))for(var row:vector.rows()){double value=TwoSampleMrWorkflowFiles.number(row.get(0));if(!Double.isFinite(value)||member.equals("outcome_se")&&value<=0)throw new IOException("Unavailable/nonpositive outcome effect/SE");}
            }
            var population=object(report.get("original_population"),"original population");if(integer(population,"mv_instruments",-1,1,1000000)!=n)throw new IOException("Multivariable instrument count differs");
        } else {
            if(dat==null||dat.rows().isEmpty())throw new IOException("No complete prepared rows");
            var population=object(report.get("original_population"),"original population");long kept=0;for(var row:dat.rows())if(dat.cell(row,"mr_keep").equals("TRUE"))kept++;
            if(integer(population,"rows",-1,0,2000000)!=dat.rows().size()||integer(population,"kept",-1,0,2000000)!=kept||integer(population,"excluded",-1,0,2000000)!=dat.rows().size()-kept)throw new IOException("Original retained/excluded row population differs");
            if(!op.equals("MR"))harmonisationFlags(dat,((Number)r.get("harmonise_action")).intValue());
            if(TwoSampleMrWorkflowContracts.mr(op))statistics(dat,loaded,r);
        }
        var files=new HashSet<String>();for(String name:tables.keySet()) {Path from=worker.resolve(name),to=root.resolve("results").resolve(name);files.add(name);if(compare){if(!Arrays.equals(Files.readAllBytes(from),Files.readAllBytes(to)))throw new IOException("Export differs: "+name);}else Files.copy(from,to);}
        if(compare)try(var stream=Files.list(root.resolve("results"))){if(!new HashSet<>(stream.map(p->p.getFileName().toString()).toList()).equals(files))throw new IOException("Export file population differs");}
    }
    private static void harmonisationFlags(TwoSampleMrWorkflowFiles.Table dat,int action)throws IOException {
        for(var row:dat.rows()) {
            boolean removed=dat.cell(row,"remove").equals("TRUE"),ambiguous=dat.cell(row,"ambiguous").equals("TRUE"),palindrome=dat.cell(row,"palindromic").equals("TRUE");
            boolean finite=true;for(String name:List.of("beta.exposure","beta.outcome","se.exposure","se.outcome"))finite&=Double.isFinite(TwoSampleMrWorkflowFiles.number(dat.cell(row,name)));
            boolean expected=finite&&!removed&&(action==1||!ambiguous)&&(action!=3||!palindrome);
            if(!dat.cell(row,"mr_keep").equals(expected?"TRUE":"FALSE"))throw new IOException("Original action/completeness exclusion flags differ");
        }
    }
    private static void statistics(TwoSampleMrWorkflowFiles.Table dat,Map<String,TwoSampleMrWorkflowFiles.Table> tables,Map<String,Object> r)throws IOException {
        var mr=tables.get("mr.tsv");var methods=tables.get("method-list.tsv");if(mr==null||methods==null||mr.rows().isEmpty())throw new IOException("No complete MR table");
        var mapping=new HashMap<String,String>();for(var row:methods.rows())mapping.put(methods.cell(row,"name"),methods.cell(row,"obj"));
        var groups=new LinkedHashMap<List<String>,List<List<String>>>();for(var row:dat.rows())if(dat.cell(row,"mr_keep").equals("TRUE"))groups.computeIfAbsent(List.of(dat.cell(row,"id.exposure"),dat.cell(row,"id.outcome")),k->new ArrayList<>()).add(row);
        var seen=new HashSet<List<String>>();for(var row:mr.rows()) {
            String method=mapping.get(mr.cell(row,"method"));if(method==null||!list(r.get("methods"),"methods").contains(method))throw new IOException("Unknown/unrequested original result estimator");
            var key=List.of(mr.cell(row,"id.exposure"),mr.cell(row,"id.outcome"));var input=groups.get(key);if(input==null||!seen.add(List.of(key.get(0),key.get(1),method)))throw new IOException("Wrong/repeated result trait population");
            for(String field:List.of("b","pval","nsnp"))if(!Double.isFinite(TwoSampleMrWorkflowFiles.number(mr.cell(row,field))))throw new IOException("Unavailable/nonfinite core statistic: "+field);
            double p=TwoSampleMrWorkflowFiles.number(mr.cell(row,"pval"));if(p<0||p>1)throw new IOException("P-value outside [0,1]");
            double standardError=TwoSampleMrWorkflowFiles.number(mr.cell(row,"se"));if(!method.equals("mr_sign")&&(!Double.isFinite(standardError)||standardError<0))throw new IOException("Unavailable/nonfinite/negative standard error");
            double instrumentCount=TwoSampleMrWorkflowFiles.number(mr.cell(row,"nsnp"));if(instrumentCount<1||instrumentCount>input.size()||instrumentCount!=Math.rint(instrumentCount))throw new IOException("Original result instrument count differs from the retained population");
            if(!mr.cell(row,"exposure").equals(dat.cell(input.get(0),"exposure"))||!mr.cell(row,"outcome").equals(dat.cell(input.get(0),"outcome")))throw new IOException("Trait labels changed");
            if(method.equals("mr_weighted_median")||method.equals("mr_simple_median")) {
                int n=input.size();double[] x=new double[n],y=new double[n],sx=new double[n],sy=new double[n],ratio=new double[n],weights=new double[n];
                for(int i=0;i<n;i++){var a=input.get(i);x[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"beta.exposure"));y[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"beta.outcome"));sx[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"se.exposure"));sy[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"se.outcome"));ratio[i]=y[i]/x[i];weights[i]=1.0/n;}
                var point=method.equals("mr_weighted_median")?TwoSampleMrStatistics.weightedMedianPoint(x,y,sx,sy):TwoSampleMrStatistics.weightedMedian(ratio,weights);
                near(TwoSampleMrWorkflowFiles.number(mr.cell(row,"b")),point.value(),"median point");
            }
            TwoSampleMrStatistics.Method nativeMethod;
            try{nativeMethod=TwoSampleMrStatistics.Method.valueOf(method.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException outsideNativeScope){continue;}
            int n=input.size();double[] x=new double[n],y=new double[n],sx=new double[n],sy=new double[n];
            for(int i=0;i<n;i++){var a=input.get(i);x[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"beta.exposure"));y[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"beta.outcome"));sx[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"se.exposure"));sy[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"se.outcome"));}
            var original=TwoSampleMrStatistics.evaluate(nativeMethod,x,y,sx,sy);
            for(String field:List.of("b","se","pval","nsnp")) {
                var wanted=original.statistics().get(field);String actual=mr.cell(row,field);
                if(wanted.kind()==TwoSampleMrStatistics.Kind.NA){if(!actual.equals("NA"))throw new IOException("Unavailable statistic kind differs");}
                else near(TwoSampleMrWorkflowFiles.number(actual),wanted.value(),field);
            }
        }
        var ledger=tables.get("method-population.tsv");if(ledger==null)throw new IOException("Missing per-method admission/no-result ledger");
        var ledgerRows=new HashSet<List<String>>();var originalGroups=new LinkedHashMap<List<String>,Integer>();
        for(var row:dat.rows()){var key=List.of(dat.cell(row,"id.exposure"),dat.cell(row,"id.outcome"));originalGroups.putIfAbsent(key,0);if(dat.cell(row,"mr_keep").equals("TRUE"))originalGroups.merge(key,1,Integer::sum);}
        for(var row:ledger.rows()) {
            var key=List.of(ledger.cell(row,"id.exposure"),ledger.cell(row,"id.outcome"));String method=ledger.cell(row,"method");var id=List.of(key.get(0),key.get(1),method);
            if(!originalGroups.containsKey(key)||!list(r.get("methods"),"methods").contains(method)||!ledgerRows.add(id))throw new IOException("Wrong/repeated method ledger population");
            int n=originalGroups.get(key);boolean present=seen.contains(id);String status=present?"ESTIMATE_RETURNED":n==0?"NO_RETAINED_INSTRUMENTS":n>1&&list(r.get("methods"),"methods").size()>1&&method.equals("mr_wald_ratio")?"SOURCE_MULTISNP_WALD_OMITTED":"SOURCE_NO_ESTIMATE";
            if(TwoSampleMrWorkflowFiles.number(ledger.cell(row,"retained_instruments"))!=n||!ledger.cell(row,"status").equals(status)||!ledger.cell(row,"estimate_admitted").equals(present?"TRUE":"FALSE"))throw new IOException("Method admission/no-result status differs");
            if(n==0||status.equals("SOURCE_MULTISNP_WALD_OMITTED"))continue;
            TwoSampleMrStatistics.Method nativeMethod;try{nativeMethod=TwoSampleMrStatistics.Method.valueOf(method.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException outsideNativeScope){continue;}
            var input=groups.get(key);double[] x=new double[n],y=new double[n],sx=new double[n],sy=new double[n];
            for(int i=0;i<n;i++){var a=input.get(i);x[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"beta.exposure"));y[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"beta.outcome"));sx[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"se.exposure"));sy[i]=TwoSampleMrWorkflowFiles.number(dat.cell(a,"se.outcome"));}
            var calculated=TwoSampleMrStatistics.evaluate(nativeMethod,x,y,sx,sy);boolean available=false;for(String field:List.of("b","se","pval"))if(calculated.statistics().get(field).kind()==TwoSampleMrStatistics.Kind.FINITE)available=true;
            if(available!=present)throw new IOException("A deterministic source estimator was omitted or fabricated");
        }
        if(ledgerRows.size()!=(long)originalGroups.size()*list(r.get("methods"),"methods").size())throw new IOException("Incomplete per-method no-result/admission ledger");
    }
    private static void near(double actual,double expected,String field)throws IOException {
        if(!Double.isFinite(actual)||!Double.isFinite(expected)||Math.abs(actual-expected)>Math.max(2e-9*Math.max(Math.abs(actual),Math.abs(expected)),64*Math.max(Math.ulp(actual),Math.ulp(expected))))throw new IOException("Original/native deterministic statistic differs: "+field);
    }
}
