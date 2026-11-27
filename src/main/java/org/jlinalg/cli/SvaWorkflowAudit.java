/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** R-free structural and numerical checks. This is not an independent model refit. */
final class SvaWorkflowAudit {
    private SvaWorkflowAudit() { }
    static void outputs(Path root,Map<String,Object> r,boolean saved)throws IOException {
        var prepared=SvaWorkflowFiles.prepare(root,r,true);int n=prepared.samples().size(),g=prepared.features().size();
        var report=read(root.resolve("worker/report.json"));
        if(!"completed-with-warnings".equals(report.get("status"))||!Boolean.TRUE.equals(report.get("results_admitted"))||!Boolean.TRUE.equals(report.get("namespace_unchanged"))||!Boolean.TRUE.equals(report.get("package_files_unchanged"))||!r.get("operation").equals(report.get("operation")))throw new IOException("Original worker did not admit the unchanged requested operation");
        if(!same(read(root.resolve("worker/package-files-before.json"),16_000_000),read(root.resolve("worker/package-files-after.json"),16_000_000)))throw new IOException("Original package files changed");
        var runtime=object(report.get("runtime"),"runtime");var profile=read(root.resolve("source/SOURCE-PROFILE.json"));
        int qualified=object(profile.get("qualified_package_files"),"qualified package files").size();if(integer(report,"qualified_package_files_checked",-1,qualified,qualified)!=qualified)throw new IOException("Original package file guards were not completed");
        if(!profile.get("platform").equals(runtime.get("platform"))||!same(profile.get("functions"),runtime.get("body_formals_guard"))||!same(profile.get("sva_source"),runtime.get("source_archive")))throw new IOException("Runtime/source guard record differs");
        var packages=object(runtime.get("packages"),"runtime packages");for(var e:object(profile.get("packages"),"qualified packages").entrySet())if(!e.getValue().equals(packages.get(e.getKey())))throw new IOException("Unqualified saved package version");
        var rng=object(report.get("rng"),"rng");if(!same(rng.get("project_seed"),r.get("seed"))||!Boolean.FALSE.equals(rng.get("author_seed_inferred"))||!same(rng.get("kind"),List.of("Mersenne-Twister","Inversion","Rejection")))throw new IOException("Declared RNG differs");
        var selected=SusieWorkflowFiles.table(root.resolve("results/selection.tsv"),g,2);
        if(!Arrays.equals(selected.header(),new String[]{"source_index","feature_id"}))throw new IOException("Selection header differs");
        int previous=0;for(var row:selected.rows()){int index=exact(row[0],1,g);if(index<=previous||!prepared.features().get(index-1).equals(row[1]))throw new IOException("Selected source identity/order differs");previous=index;}
        if(!r.containsKey("vfilter")&&selected.rows().size()!=g)throw new IOException("Unexpected feature filtering");
        if(integer(report,"source_rows",0,g,g)!=g||integer(report,"selected_rows",-1,0,g)!=selected.rows().size())throw new IOException("Source selection dimensions differ");
        var shapes=read(root.resolve("results/shapes.json"));Set<String> expected=r.get("operation").equals("NUM_SV")?Set.of("n.sv"):Set.of("sv","pprob.gam","pprob.b","n.sv");
        if(!shapes.keySet().equals(expected))throw new IOException("Original component population differs");
        var values=new LinkedHashMap<String,List<Double>>();long total=0;
        for(String name:shapes.keySet()){
            var shape=object(shapes.get(name),"shape");keys(shape,Set.of("class","typeof","length","dim","names","dimnames"));
            int length=(int)integer(shape,"length",-1,0,Math.max((long)g*n,Math.max(n,g)));total+=length;
            if(shape.get("dim")!=null){long product=1;for(Object v:list(shape.get("dim"),"dimensions"))product=Math.multiplyExact(product,integer(Map.of("v",v),"v",-1,0,Math.max(g,n)));if(product!=length)throw new IOException("Original component dimensions differ");}
            String type=text(shape,"typeof");if(!Set.of("double","integer","logical","NULL").contains(type)||type.equals("NULL")&&length!=0)throw new IOException("Unexpected original component type");
            values.put(name,new ArrayList<>());
        }
        if(total>Integer.MAX_VALUE)throw new IOException("Exported component count exceeds audit capacity");
        var exported=SusieWorkflowFiles.table(root.resolve("results/components.tsv"),(int)Math.max(1,total),3);
        if(!Arrays.equals(exported.header(),new String[]{"component","index","value"})||exported.rows().size()!=total)throw new IOException("Original numeric export population differs");
        for(var row:exported.rows()){
            var v=values.get(row[0]);if(v==null||exact(row[1],1,(int)Math.max(1,total))!=v.size()+1)throw new IOException("Component identity/index differs");
            String type=text(object(shapes.get(row[0]),"shape"),"typeof");double x;
            if(type.equals("logical")){if(!Set.of("TRUE","FALSE").contains(row[2]))throw new IOException("Invalid logical original output");x=row[2].equals("TRUE")?1:0;}
            else x=SusieWorkflowFiles.number(row[2]);v.add(x);
            if(row[0].startsWith("pprob.")&&(x<0||x>1))throw new IOException("Original posterior outside [0,1]; preserve diagnostics without admission");
        }
        for(String name:shapes.keySet())if(values.get(name).size()!=integer(object(shapes.get(name),"shape"),"length",-1,0,Integer.MAX_VALUE))throw new IOException("Component export length differs");
        var count=values.get("n.sv");if(count.size()!=1)throw new IOException("Original n.sv must remain scalar");double x=count.get(0);if(x!=Math.rint(x)||x<0||x>n||!same(report.get("n_sv"),x))throw new IOException("Invalid original factor count");int k=(int)x;
        if(!r.get("operation").equals("NUM_SV")) {
            String method=r.containsKey("controls")?"supervised":text(r,"method");if(!method.equals(report.get("method_effective"))||!r.get("method").equals(report.get("method_requested")))throw new IOException("Effective original method differs");
            if(r.get("n_sv") instanceof Number fixed&&(k>fixed.intValue()||!method.equals("two-step")&&k!=fixed.intValue()))throw new IOException("Original explicit factor count changed");
            var shape=object(shapes.get("sv"),"sv shape");var sv=values.get("sv");
            if(k>0){if(!same(shape.get("dim"),List.of(n,k))||sv.size()!=n*k)throw new IOException("Original factor matrix dimensions differ");for(int i=0;i<k;i++)for(int j=0;j<=i;j++){if(method.equals("two-step")&&i!=j)continue;double dot=0;for(int s=0;s<n;s++)dot+=sv.get(i*n+s)*sv.get(j*n+s);if(Math.abs(dot-(i==j?1:0))>1e-7)throw new IOException("Original factor norm/Gram check failed; two-step columns need not be mutually orthogonal");}}
            else if(!sv.isEmpty()&&(sv.size()!=1&&sv.size()!=n||sv.stream().anyMatch(v->v!=0)))throw new IOException("Unexpected original zero-factor shape/value");
            int selectedCount=selected.rows().size();for(String name:List.of("pprob.gam","pprob.b")){int length=values.get(name).size();if(length!=0&&length!=selectedCount&&!(k==0&&length==1))throw new IOException("Posterior/source population differs");}
        }
    }
    private static int exact(String s,int min,int max)throws IOException {try{int n=Integer.parseInt(s);if(n<min||n>max||!s.equals(Integer.toString(n)))throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IOException("Require exact bounded positive export index",e);}}
}
