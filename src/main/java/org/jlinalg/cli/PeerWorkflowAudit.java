/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** R-free saved model geometry, FP32 intake, selected arithmetic and export checks. */
final class PeerWorkflowAudit {
    private PeerWorkflowAudit() { }
    private static Map<String,double[][]> state(Path path,Map<String,int[]> shapes,long cap)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>cap)throw new IOException("Missing/oversized original model state: "+path.getFileName());
        var result=new LinkedHashMap<String,double[][]>();var seen=new HashMap<String,BitSet>();var counts=new HashMap<String,Long>();
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            if(!"field\trow\tcolumn\tvalue".equals(reader.readLine()))throw new IOException("Invalid original state header");String line;
            while((line=reader.readLine())!=null) {
                if(line.length()>2048)throw new IOException("Original state line capacity exceeded");String[] fields=line.split("\t",-1);
                if(fields.length!=4||!shapes.containsKey(fields[0]))throw new IOException("Unknown/malformed original model field");int[] shape=shapes.get(fields[0]);
                int row,column;double value;try{row=Integer.parseInt(fields[1]);column=Integer.parseInt(fields[2]);value=Double.parseDouble(fields[3]);}catch(NumberFormatException failed){throw new IOException("Invalid original state coordinate/value",failed);}
                if(row<0||column<0||row>=shape[0]||column>=shape[1]||!Double.isFinite(value)||value!=(double)(float)value)throw new IOException("Out-of-range/nonfinite/non-FP32 original model value");
                if(!result.containsKey(fields[0])){result.put(fields[0],new double[shape[0]][shape[1]]);seen.put(fields[0],new BitSet());}
                int index=Math.toIntExact((long)row*shape[1]+column);if(seen.get(fields[0]).get(index))throw new IOException("Duplicate original state coordinate");
                seen.get(fields[0]).set(index);result.get(fields[0])[row][column]=value;counts.merge(fields[0],1L,Long::sum);
            }
        }
        for(var entry:shapes.entrySet()) {
            long expected=(long)entry.getValue()[0]*entry.getValue()[1];
            if(counts.getOrDefault(entry.getKey(),0L)!=expected)throw new IOException("Incomplete original state field: "+entry.getKey());
            if(expected==0)result.put(entry.getKey(),new double[entry.getValue()[0]][entry.getValue()[1]]);
        }
        return result;
    }
    private static Map<String,int[]> shapes(int n,int d,int k,int nc,boolean last) {
        var shapes=new LinkedHashMap<String,int[]>();
        for(String name:List.of("Y","Y2","measurementVariance","residual"))shapes.put(name,new int[]{n,d});
        shapes.put("covariates",new int[]{n,nc});shapes.put("X",new int[]{n,k});shapes.put("W",new int[]{d,k});
        for(String name:List.of("X2","W2","WXPrecision","XPriorPrecision"))shapes.put(name,new int[]{k,k});shapes.put("XPriorMean",new int[]{n,k});
        for(String name:List.of("alpha","alphaLn","alphaA","alphaB"))shapes.put(name,new int[]{k,1});
        for(String name:List.of("epsilon","epsilonLn","epsilonA","epsilonB"))shapes.put(name,new int[]{d,1});
        if(last){shapes.put("XCovariance",new int[]{k,k});shapes.put("WAlphaLast",new int[]{k,1});shapes.put("WEpsilonLast",new int[]{d,1});shapes.put("WX2Last",new int[]{k,k});shapes.put("WLogDetCovarianceSum",new int[]{1,1});}return shapes;
    }
    static Map<String,String> table(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>100000)throw new IOException("Missing/oversized original status/parameter table");
        var result=new LinkedHashMap<String,String>();for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)){String[] fields=line.split("\t",-1);if(fields.length!=2||result.putIfAbsent(fields[0],fields[1])!=null)throw new IOException("Malformed/repeated original status/parameter key");}return result;
    }
    private static void exact(double actual,double expected,String label)throws IOException {if(Double.doubleToLongBits(actual)!=Double.doubleToLongBits(expected)&&actual!=expected)throw new IOException("Original model "+label+" differs");}
    private static void gamma(Map<String,double[][]> values,String prefix,double prior,int plate)throws IOException {
        double a=(float)((double)(float)prior+.5*plate);double[][] mean=values.get(prefix),aa=values.get(prefix+"A"),bb=values.get(prefix+"B");
        for(int i=0;i<mean.length;i++){exact(aa[i][0],a,prefix+" posterior shape");if(!(aa[i][0]>0&&bb[i][0]>0&&mean[i][0]>0))throw new IOException("Nonpositive original precision posterior");exact(mean[i][0],(float)aa[i][0]/(float)bb[i][0],prefix+" posterior mean");}
    }
    private static void errorBound(double actual,double expected,double magnitude,int operations,String label)throws IOException {
        double u=Math.scalb(1.,-24),gamma=operations*u/(1-operations*u),bound=gamma*magnitude+Math.scalb(1.,-149)*operations;
        if(!Double.isFinite(expected)||Math.abs(actual-expected)>bound)throw new IOException("Original FP32 "+label+" exceeds its declared rounding bound");
    }
    static void outputs(Path root,Map<String,Object> r,PeerWorkflowFiles.Prepared prepared,boolean compare)throws IOException {
        int n=prepared.matrix().columns().size(),d=prepared.matrix().rows().size(),nc=prepared.known(),hidden=((Number)r.get("factors")).intValue(),k=hidden+nc;
        var initial=state(root.resolve("worker/capture/initial-state.tsv"),shapes(n,d,k,nc,false),PeerWorkflowContracts.limit(r,"maximum_output_bytes"));
        var last=state(root.resolve("worker/capture/final-state.tsv"),shapes(n,d,k,nc,true),PeerWorkflowContracts.limit(r,"maximum_output_bytes"));
        var parameters=table(root.resolve("worker/capture/parameters.tsv"));var status=table(root.resolve("worker/capture/status.tsv"));
        if(!"40bc4b2cd92459ce42f44dfe279717436395f3f6".equals(parameters.get("source_commit"))||!"4".equals(parameters.get("PMatrix_scalar_bytes")))throw new IOException("Original source/precision profile differs");
        for(String key:List.of("seed","initialization","verbose","max_iterations","tolerance","variance_tolerance")) {
            String actual=parameters.get(key);if(actual==null)throw new IOException("Missing original scientific parameter");
            if(key.equals("initialization")){if(!r.get(key).equals(actual))throw new IOException("Original initialization differs");}
            else if(Double.parseDouble(actual)!=((Number)r.get(key)).doubleValue())throw new IOException("Original parameter differs: "+key);
        }
        if(!"PASS_DENSE_ORIGINAL_RUN".equals(status.get("status"))||!"1".equals(status.get("converged"))||!"1".equals(status.get("finite_exported_means"))||Integer.parseInt(status.get("known_covariates"))!=nc||Integer.parseInt(status.get("total_factors"))!=k)throw new IOException("Original model did not complete with finite declared dimensions");
        int reported=Integer.parseInt(status.get("original_reported_iterations")),executed=Integer.parseInt(status.get("executed_updates"));
        if(reported<1||reported>=((Number)r.get("max_iterations")).intValue()||executed!=reported+1)throw new IOException("Original stopping counter/history semantics differ");
        var command=read(root.resolve("worker/command.json"));if(!same(command.get("scientific_options"),PeerWorkflowRuntime.scientificOptions(r))||!PeerWorkflowContracts.PROFILE.equals(command.get("source_profile")))throw new IOException("Recorded worker scientific options differ");
        var runtime=object(r.get("runtime"),"runtime");var bindings=read(root.resolve("worker/runtime-bindings.json"));if(!text(runtime,"sha256").equals(bindings.get("sha256")))throw new IOException("Saved runtime hash differs from requested binding");
        for(var values:List.of(initial,last)) {
            for(int i=0;i<n;i++)for(int j=0;j<d;j++) {
                float y=(float)prepared.matrix().values()[j][i],variance=prepared.variance()==null?.01f:(float)prepared.variance().values()[j][i];
                exact(values.get("Y")[i][j],y,"phenotype intake");exact(values.get("measurementVariance")[i][j],variance,"measurement variance");exact(values.get("Y2")[i][j],variance+y*y,"phenotype second moment");
            }
            for(int i=0;i<n;i++)for(int c=0;c<k;c++) {
                double expected=c<nc?(prepared.covariates()!=null&&c<prepared.covariates().columns().size()?(float)prepared.covariates().values()[i][c]:1.):0.;
                exact(values.get("XPriorMean")[i][c],expected,"known-factor prior mean");if(c<nc){exact(values.get("X")[i][c],expected,"fixed known factor");exact(values.get("covariates")[i][c],expected,"known covariate intake");}
                for(int other=0;other<k;other++)exact(values.get("XPriorPrecision")[c][other],c!=other?0.:c<nc?(float)((Number)r.get("covariate_precision")).doubleValue():1.,"factor prior precision");
            }
            double[][] x=values.get("X"),w=values.get("W");
            for(int i=0;i<n;i++)for(int j=0;j<d;j++) {
                double fitted=0,magnitude=Math.abs(values.get("Y")[i][j]);for(int c=0;c<k;c++){fitted+=x[i][c]*w[j][c];magnitude+=Math.abs(x[i][c]*w[j][c]);}
                errorBound(values.get("residual")[i][j],values.get("Y")[i][j]-fitted,magnitude,2*k+2,"residual");
            }
        }
        double[][] ix=initial.get("X"),ix2=initial.get("X2");
        for(int a=0;a<k;a++)for(int b=0;b<k;b++) {
            double sum=0,magnitude=0;for(int i=0;i<n;i++){double product=ix[i][a]*ix[i][b];sum+=product;magnitude+=Math.abs(product);}errorBound(ix2[a][b],sum,magnitude,2*n+2,"initial X second moment");
        }
        gamma(last,"alpha",((Number)r.get("alpha_a")).doubleValue(),d);gamma(last,"epsilon",((Number)r.get("epsilon_a")).doubleValue(),n);
        Path history=root.resolve("worker/capture/history.tsv");if(Files.size(history)>100_000_000)throw new IOException("Original history capacity exceeded");
        int count=0;double lastBound=Double.NaN,lastVariance=Double.NaN,previousBound=Double.NaN,previousVariance=Double.NaN;var originalHistory=new ArrayList<double[]>();
        boolean monitored=((Number)r.get("verbose")).intValue()>=2||((Number)r.get("tolerance")).doubleValue()>0;
        try(BufferedReader reader=Files.newBufferedReader(history,StandardCharsets.UTF_8)) {
            if(!"iteration\tbound_computed\tbound\tresidual_variance\tin_original_getter_history".equals(reader.readLine()))throw new IOException("Original history header differs");String line;
            while((line=reader.readLine())!=null) {
                String[] row=line.split("\t",-1);if(row.length!=5||Integer.parseInt(row[0])!=++count||!row[1].equals(monitored?"1":"0")||!row[4].equals(count<=reported?"1":"0"))throw new IOException("Original history/counter population differs");
                previousBound=lastBound;previousVariance=lastVariance;lastBound=monitored?Double.parseDouble(row[2]):Double.NaN;lastVariance=Double.parseDouble(row[3]);
                if(count>executed||!Double.isFinite(lastVariance)||(monitored&&!Double.isFinite(lastBound))||(!monitored&&!row[2].equals("NA")))throw new IOException("Nonfinite/undeclared original history");originalHistory.add(new double[]{lastBound,lastVariance});
            }
        }
        if(count!=executed||!monitored)throw new IOException("Original history is incomplete or has no monitored stopping rule");
        Path monitor=root.resolve("worker/capture/monitor.tsv");int monitorCount=0;var monitoredHistory=new ArrayList<double[]>();
        try(BufferedReader reader=Files.newBufferedReader(monitor,StandardCharsets.UTF_8)) {
            if(!"iteration\tbound_double\tresidual_variance".equals(reader.readLine()))throw new IOException("Original double-bound monitor header differs");String line;
            while((line=reader.readLine())!=null) {
                String[] row=line.split("\t",-1);if(row.length!=3||Integer.parseInt(row[0])!=++monitorCount)throw new IOException("Original double-bound monitor population differs");
                previousBound=lastBound;previousVariance=lastVariance;lastBound=Double.parseDouble(row[1]);lastVariance=Double.parseDouble(row[2]);
                if(monitorCount>executed||!Double.isFinite(lastBound)||!Double.isFinite(lastVariance))throw new IOException("Nonfinite original monitor value");
                exact((float)lastBound,originalHistory.get(monitorCount-1)[0],"original float-bound history");
                if(Math.abs(lastVariance-originalHistory.get(monitorCount-1)[1])>3*Math.ulp((float)lastVariance))throw new IOException("Original residual monitoring histories differ");monitoredHistory.add(new double[]{lastBound,lastVariance});
            }
        }
        if(monitorCount!=executed||!(Math.abs(lastBound-previousBound)<((Number)r.get("tolerance")).doubleValue()||Math.abs(lastVariance-previousVariance)<((Number)r.get("variance_tolerance")).doubleValue()))throw new IOException("Original declared stopping rule is not supported by observed double-bound history");
        int calls=0,iteration=0,next=((Number)r.get("verbose")).intValue()>=3?3:1;
        try(BufferedReader reader=Files.newBufferedReader(root.resolve("worker/capture/bound-observations.tsv"),StandardCharsets.UTF_8)) {
            if(!"call\tbound_double\tresidual_variance".equals(reader.readLine()))throw new IOException("Original bound-observer header differs");String line;
            while((line=reader.readLine())!=null) {
                String[] row=line.split("\t",-1);if(row.length!=3||Integer.parseInt(row[0])!=calls++)throw new IOException("Original bound-observer event order differs");
                double bound=Double.parseDouble(row[1]),variance=Double.parseDouble(row[2]);if(!Double.isFinite(bound)||!Double.isFinite(variance))throw new IOException("Nonfinite bound-observer event");
                if(calls==next){if(iteration>=executed)throw new IOException("Extra monitor event");exact(bound,monitoredHistory.get(iteration)[0],"double-bound monitor event");exact(variance,monitoredHistory.get(iteration)[1],"variance monitor event");iteration++;next+=((Number)r.get("verbose")).intValue()>=3?5:1;}
                if(calls>5L*executed)throw new IOException("Bound-observer capacity exceeded");
            }
        }
        if(iteration!=executed||calls!=(((Number)r.get("verbose")).intValue()>=3?3+5*(executed-1):executed))throw new IOException("Bound-observer event population differs");
        if(!compare)Files.createDirectory(root.resolve("exports"));
        export(root.resolve("exports/factors.tsv"),prepared.matrix().columns(),last.get("X"),nc,hidden,"sample_id","PEER",compare);
        export(root.resolve("exports/loadings.tsv"),prepared.matrix().rows(),last.get("W"),nc,hidden,"feature_id","PEER",compare);
        export(root.resolve("exports/residuals.tsv"),prepared.matrix().rows(),PeerWorkflowFiles.transpose(last.get("residual")),0,n,"feature_id",prepared.matrix().columns(),compare);
        var factors=new StringBuilder("source_factor_index\tkind\tprecision\n");for(int c=0;c<k;c++)factors.append(c).append('\t').append(c<nc?"known":"latent").append('\t').append(last.get("alpha")[c][0]).append('\n');
        PeerWorkflowFiles.compareOrWrite(root.resolve("exports/factor-precisions.tsv"),factors.toString().getBytes(StandardCharsets.UTF_8),compare);
        var noise=new StringBuilder("feature_id\tprecision\n");for(int j=0;j<d;j++)noise.append(prepared.matrix().rows().get(j)).append('\t').append(last.get("epsilon")[j][0]).append('\n');
        PeerWorkflowFiles.compareOrWrite(root.resolve("exports/noise-precisions.tsv"),noise.toString().getBytes(StandardCharsets.UTF_8),compare);
    }
    private static String rendered(String original,boolean wsl)throws IOException {
        String path=original.replace('\\','/');if(!wsl)return path;if(!path.matches("[A-Za-z]:/.*"))throw new IOException("Recorded WSL path is not drive-backed");return "/mnt/"+Character.toLowerCase(path.charAt(0))+path.substring(2);
    }
    static void provenance(Path root,Map<String,Object> r,Map<String,Object> manifest)throws IOException {
        var binding=read(root.resolve("worker/runtime-bindings.json"));var runtime=object(r.get("runtime"),"runtime");var command=read(root.resolve("worker/command.json"));boolean wsl=runtime.containsKey("wsl_distribution");
        if(!Objects.equals(binding.get("transport"),wsl?"explicit-wsl":"host-native")||!Objects.equals(binding.get("wsl_distribution"),wsl?runtime.get("wsl_distribution"):"")||!Objects.equals(binding.get("sha256"),runtime.get("sha256")))throw new IOException("Runtime transport/binding differs");
        String originalRoot=text(manifest,"capture_root");var expected=new ArrayList<String>();expected.add(rendered(text(binding,"executable"),wsl));expected.addAll(List.of("--data",rendered(originalRoot+"/worker/data.tsv",wsl),"--out",rendered(originalRoot+"/worker/capture",wsl)));
        if(r.containsKey("covariates"))expected.addAll(List.of("--covariates",rendered(originalRoot+"/worker/covariates.tsv",wsl)));
        if(r.containsKey("measurement_variance"))expected.addAll(List.of("--measurement-variance",rendered(originalRoot+"/worker/variance.tsv",wsl)));
        for(var entry:PeerWorkflowRuntime.scientificOptions(r).entrySet())expected.addAll(List.of("--"+entry.getKey(),entry.getValue()));
        if(!same(expected,command.get("original_arguments")))throw new IOException("Original worker command differs from scientific/input bindings");var launch=new ArrayList<String>();
        if(wsl) {
            launch.addAll(List.of("wsl.exe","-d",text(runtime,"wsl_distribution"),"--","python3",rendered(originalRoot+"/source/peer_owned_worker.py",true),"launch","--ledger",rendered(originalRoot+"/worker/owned-wsl.json",true),"--token",text(binding,"owned_wsl_token"),"--command"));
            var ledger=read(root.resolve("worker/owned-wsl.json"));var closed=read(root.resolve("worker/owned-wsl.json.closed.json"));
            if(!Objects.equals(ledger.get("token"),binding.get("owned_wsl_token"))||!same(ledger.get("pid"),ledger.get("group"))||!same(closed.get("owned_group"),ledger.get("group"))||!Boolean.TRUE.equals(closed.get("complete_cleanup"))||!list(closed.get("live_writers_after"),"closed writers").isEmpty())throw new IOException("Owned WSL closure/identity receipt differs");
        }
        launch.addAll(expected);if(!same(launch,command.get("arguments")))throw new IOException("Recorded launch command differs");
        var names=new TreeSet<>(List.of("analysis-status.json","manifest.json","source-request.json","request.json","effective-request.json","prepared.json","WARNINGS.md","capture-files.json"));
        for(String name:List.of("SOURCE-PROFILE.json","peer_capture.cpp","CMakeLists.txt","peer_owned_worker.py","PEER-LICENSE.txt"))names.add("source/"+name);
        for(String key:PeerWorkflowContracts.INPUTS)if(r.containsKey(key))names.add("inputs/"+key+".tsv");
        names.add("worker/data.tsv");if(r.containsKey("covariates"))names.add("worker/covariates.tsv");if(r.containsKey("measurement_variance"))names.add("worker/variance.tsv");
        for(String name:List.of("command.json","runtime-bindings.json","stdout.log","stderr.log"))names.add("worker/"+name);
        for(String name:List.of("parameters.tsv","initial-state.tsv","final-state.tsv","history.tsv","monitor.tsv","bound-observations.tsv","status.tsv"))names.add("worker/capture/"+name);
        for(String name:List.of("factors.tsv","loadings.tsv","residuals.tsv","factor-precisions.tsv","noise-precisions.tsv"))names.add("exports/"+name);
        if(wsl){names.add("worker/owned-wsl.json");names.add("worker/owned-wsl.json.closed.json");}
        if(manifest.containsKey("owned_wsl_cleanup")) {
            if(!wsl||!Boolean.TRUE.equals(read(root.resolve("worker/wsl-cleanup.json")).get("complete_cleanup")))throw new IOException("Invalid saved WSL cleanup");names.add("worker/wsl-cleanup.json");names.add("worker/wsl-cleanup.stderr");
        }
        if(Files.exists(root.resolve("replay-source.json"))) {
            var replay=read(root.resolve("replay-source.json"));keys(replay,Set.of("analysis","capture_sha256"));text(replay,"analysis");if(!text(replay,"capture_sha256").matches("[0-9a-f]{64}"))throw new IOException("Invalid replay lineage hash");names.add("replay-source.json");
        }
        if(!names.equals(PeerWorkflowRuntime.footprint(root).keySet()))throw new IOException("Unexpected/missing PEER capture member");
    }
    private static void export(Path path,List<String> ids,double[][] values,int start,int count,String first,String prefix,boolean compare)throws IOException {
        var columns=new ArrayList<String>();for(int c=0;c<count;c++)columns.add(prefix+(c+1));export(path,ids,values,start,count,first,columns,compare);
    }
    private static void export(Path path,List<String> ids,double[][] values,int start,int count,String first,List<String> columns,boolean compare)throws IOException {
        var text=new StringBuilder(first);for(String column:columns)text.append('\t').append(column);text.append('\n');
        for(int i=0;i<ids.size();i++){text.append(ids.get(i));for(int c=0;c<count;c++)text.append('\t').append(values[i][start+c]);text.append('\n');}PeerWorkflowFiles.compareOrWrite(path,text.toString().getBytes(StandardCharsets.UTF_8),compare);
    }
}
