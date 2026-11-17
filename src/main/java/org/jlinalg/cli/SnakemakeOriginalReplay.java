/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.*;

/** Fresh original-engine reruns from captured inputs, with a standalone parent/result comparison chain. */
final class SnakemakeOriginalReplay {
    record Compared(String root,String path,String kind,String comparison,int members,int measurementRows,boolean byteEqual,int permissionDifferences) { }
    private SnakemakeOriginalReplay() { }
    static SnakemakeOriginalBundle.Verified execute(Path supplied,Path runtime,Path output,Runnable check)throws IOException {
        Path root=output.toAbsolutePath().normalize();if(root.getParent()==null)throw new IOException("Require a NEW named replay directory");directory(root.getParent());Files.createDirectory(root);Files.writeString(root.resolve("WARNINGS.md"),"# Original Snakemake rerun pending\n\nNo comparison result is admitted until the final replay bundle completes. Keep all failures and source diagnostics.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try {
            var parent=SnakemakeOriginalBundle.verify(supplied,check);var request=parent.request();SnakemakeOriginalRuntime.open(runtime,check);tools(parent,check);managedPrerequisites(parent,check);
            Path bundle=Files.createDirectory(root.resolve("bundle"));copy(parent.root(),bundle.resolve("parent"),request,check);var savedParent=SnakemakeOriginalBundle.verify(bundle.resolve("parent"),check);
            if(!parent.manifest().equals(savedParent.manifest()))throw new IOException("Parent changed while preserving replay evidence");
            Path stage=Files.createDirectory(root.resolve("stage")),objects=Files.createDirectory(stage.resolve("objects"));var pins=new TreeMap<String,Pin>();capturePins(parent.inputs(),pins);
            for(var pin:pins.values())SnakemakeOriginalCapture.store(objects,parent.executionRoot().resolve("objects").resolve(pin.sha256()),pin,request.capture().fileBytes(),check);
            var locations=SnakemakeOriginalCapture.materialize(stage,parent.inputs(),stage.resolve("resources"),request.capture(),check);var bindings=new TreeMap<String,String>();for(var row:locations.entrySet())bindings.put(row.getKey(),row.getValue().toString());
            Map<String,Object> recipe=rebound(request.declared(),bindings);Path newRecipe=root.resolve("replay-request.json");Files.writeString(newRecipe,SnakemakeOriginalJson.parameters(recipe)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var execution=request.managed()==null?SnakemakeOriginalWorkflow.run(newRecipe,runtime,root.resolve("execution"),check):SnakemakeOriginalManagedBundle.run(newRecipe,runtime,root.resolve("execution"),check);copy(execution.root(),bundle.resolve("result"),request,check);var savedResult=execution(bundle.resolve("result"),check);
            Map<String,Object> comparison=comparison(savedParent,savedResult,root.toString());writeJson(bundle.resolve("comparison.json"),comparison);Files.writeString(bundle.resolve("WARNINGS.md"),warnings(savedParent,savedResult,comparison),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var inventory=SnakemakeOriginalBundle.inventory(bundle,request,Set.of(),check);writeJson(bundle.resolve("manifest.json"),Map.of("schema_version",1,"tool","Snakemake","files",inventory.files(),"directories",inventory.directories()));Pin manifest=pin(bundle.resolve("manifest.json"),request.metadataBytes(),check);
            writeJson(bundle.resolve("CANDIDATE.json"),marker(manifest,"candidate-genuine-rerun"));var verified=verify(bundle,"CANDIDATE.json",check,0);byte[] complete=(SnakemakeOriginalJson.json(marker(manifest,"completed-genuine-rerun"))+"\n").getBytes(StandardCharsets.UTF_8);if(request.managed()==null)SnakemakeOriginalProcess.checkCapacity(root,request.process(),complete.length,1);else SnakemakeOriginalProcess.checkManagedCapacity(root,request.process(),complete.length,1,List.of(root.resolve("execution/managed")));
            Files.write(bundle.resolve("COMPLETED.new.json"),complete,StandardOpenOption.CREATE_NEW);Files.move(bundle.resolve("COMPLETED.new.json"),bundle.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);Files.delete(bundle.resolve("CANDIDATE.json"));return verified;
        }catch(IOException|RuntimeException failed) {
            int code=failed instanceof SnakemakeOriginalRuntime.Unavailable||failed instanceof SnakemakeOriginalProcess.Unavailable?3:2;
            try{writeJson(root.resolve("FAILURE.json"),Map.of("status",code==3?"SKIP3-analysis-skipped":"FAIL2-no-comparison-result-admitted","exit_code",code,"error",failed.toString(),"required_action","Retain the complete parent, new execution, partial outputs and diagnostics. Restore the named permitted prerequisite or resolve the discrepancy and retry NEW output. Never replace an unmatched result with an older success."));Files.writeString(root.resolve("FAILURE_WARNINGS.md"),"# Snakemake "+(code==3?"SKIP3 rerun skipped":"FAIL2 rerun/comparison failed")+"\n\n"+failed+"\n\nKeep all evidence and resolve the named cause before retrying NEW output.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){failed.addSuppressed(diagnostic);}
            if(failed instanceof IOException io)throw io;throw new IOException("Rerun failed; no comparison admitted",failed);
        }
    }
    private static Map<String,Object> marker(Pin manifest,String status){return Map.of("schema_version",1,"tool","Snakemake","kind","genuine-rerun","status",status,"manifest",manifest,"biological_accuracy_certified",false,"whole_paper_reproduced",false);}
    static SnakemakeOriginalBundle.Verified verify(Path root,String markerName,Runnable check,int depth)throws IOException {
        if(depth>64)throw new IOException("Replay lineage exceeds the explicit 64-generation verification capacity");
        var result=execution(root.resolve("result"),check);var request=result.request();Map<?,?> complete=object(json(root.resolve(markerName),65536));keys(complete,"schema_version","tool","kind","status","manifest","biological_accuracy_certified","whole_paper_reproduced");integer(complete.get("schema_version"),1,1);
        if(!"Snakemake".equals(complete.get("tool"))||!"genuine-rerun".equals(complete.get("kind"))||!(markerName.equals("CANDIDATE.json")?"candidate-genuine-rerun":"completed-genuine-rerun").equals(complete.get("status"))||!Boolean.FALSE.equals(complete.get("biological_accuracy_certified"))||!Boolean.FALSE.equals(complete.get("whole_paper_reproduced")))throw new IOException("Invalid genuine rerun completion claim");
        Pin manifest=pin(root.resolve("manifest.json"),request.metadataBytes(),check);if(!manifest.equals(fingerprint(complete.get("manifest"),request.metadataBytes())))throw new IOException("Replay manifest binding differs");Map<?,?> declared=object(json(root.resolve("manifest.json"),request.metadataBytes()));keys(declared,"schema_version","tool","files","directories");integer(declared.get("schema_version"),1,1);if(!"Snakemake".equals(declared.get("tool")))throw new IOException("Wrong replay manifest tool");
        var inventory=SnakemakeOriginalBundle.inventory(root,request,Set.of("manifest.json",markerName),check);var files=new TreeMap<String,Pin>();for(var row:object(declared.get("files")).entrySet())files.put(member(text(row.getKey())),fingerprint(row.getValue(),request.capture().fileBytes()));if(!files.equals(inventory.files())||!new TreeSet<>(strings(declared.get("directories"))).equals(inventory.directories()))throw new IOException("Complete replay file/directory census differs");
        var parent=SnakemakeOriginalBundle.verify(root.resolve("parent"),check,depth+1);Map<?,?> saved=object(json(root.resolve("comparison.json"),request.metadataBytes()));String producer=absolute(text(saved.get("producer_root")));Map<String,Object> expected=comparison(parent,result,producer);equal(expected,saved,"independent genuine rerun comparison");if(!warnings(parent,result,expected).equals(utf8(bytes(root.resolve("WARNINGS.md"),request.metadataBytes()))))throw new IOException("Replay warnings differ");
        return new SnakemakeOriginalBundle.Verified(root,manifest,result.executionRoot(),result.producer(),result.request(),result.inputs(),result.outputs(),result.events());
    }
    private static Map<String,Object> rebound(Map<?,?> original,Map<String,String> bindings)throws IOException {
        var result=SnakemakeOriginalRecorded.copy(original);var workflow=SnakemakeOriginalRecorded.copy(object(original.get("workflow")));workflow.put("path",bindings.get("workflow"));result.put("workflow",workflow);var resources=new java.util.LinkedHashMap<String,Object>();for(var row:object(original.get("resources")).entrySet()){String id=text(row.getKey());resources.put(id,Map.of("path",bindings.get(id)));}result.put("resources",resources);return result;
    }
    private static SnakemakeOriginalBundle.Verified execution(Path root,Runnable check)throws IOException {
        Map<?,?> complete=object(json(root.resolve("COMPLETED.json"),65536));
        return "declared-managed-workflow".equals(complete.get("kind"))?SnakemakeOriginalManagedBundle.verify(root,"COMPLETED.json",check):SnakemakeOriginalBundle.verifyLocal(root,"COMPLETED.json",check);
    }
    private static void managedPrerequisites(SnakemakeOriginalBundle.Verified parent,Runnable check)throws IOException {
        if(parent.request().managed()==null)return;
        SnakemakeOriginalDeployment.open(Path.of(parent.request().managed().providers()),check);
        if(parent.request().managed().environments()==null&&parent.request().managed().images()==null)return;
        var saved=SnakemakeOriginalSoftwareCapture.decode(json(parent.executionRoot().resolve("borrowed-software-before.json"),parent.request().metadataBytes()),parent.request().capture());
        for(var tree:saved.roots().values())if(!Files.isDirectory(Path.of(tree.source()),LinkOption.NOFOLLOW_LINKS))throw new SnakemakeOriginalRuntime.Unavailable("Managed replay software is unavailable: "+tree.source()+". Skip this dependent analysis; restore the complete permitted software at its original prefix, then retry NEW output. An installed Conda environment cannot be relocated.");
        SnakemakeOriginalSoftwareCapture.verifyLive(saved,parent.request().capture(),check);
    }
    private static Map<String,Object> comparison(SnakemakeOriginalBundle.Verified parent,SnakemakeOriginalBundle.Verified result,String producer)throws IOException {
        if(!result.producer().equals(producer+"/execution"))throw new IOException("Rerun result does not belong to its recorded execution directory");var bindings=new TreeMap<String,String>();for(var row:parent.inputs().aliases().entrySet())bindings.put(row.getKey(),producer+"/stage/resources/"+row.getValue());Map<String,Object> expected=rebound(parent.request().declared(),bindings);equal(expected,result.request().declared(),"unchanged replay settings with captured path rebinding");
        if(!SnakemakeOriginalJson.parameters(parent.request().parameters()).equals(SnakemakeOriginalJson.parameters(result.request().parameters())))throw new IOException("Replay changed parameter values, numeric lexemes or object order");
        Map<String,Object> inputs=projection(parent);equal(inputs,projection(result),"complete replay input bytes, modes and internal aliases");
        List<?> oldTools=list(json(parent.executionRoot().resolve("tools.json"),parent.request().metadataBytes())),newTools=list(json(result.executionRoot().resolve("tools.json"),result.request().metadataBytes()));if(oldTools.size()!=newTools.size())throw new IOException("Rerun tool family differs");for(int i=0;i<oldTools.size();i++){Map<?,?> a=object(oldTools.get(i)),b=object(newTools.get(i));if(!a.get("name").equals(b.get("name"))||!fingerprint(object(a.get("identity")).get("pin"),1_073_741_824).equals(fingerprint(object(b.get("identity")).get("pin"),1_073_741_824)))throw new IOException("Declared external executable bytes changed for the rerun");}
        if(parent.outputs().size()!=result.outputs().size())throw new IOException("Rerun output declaration family differs");var compared=new ArrayList<Compared>();
        for(int i=0;i<parent.outputs().size();i++) {
            var a=parent.outputs().get(i);var b=result.outputs().get(i);if(!a.root().equals(b.root())||!a.path().equals(b.path())||!a.kind().equals(b.kind())||!a.comparison().equals(b.comparison())||!a.members().keySet().equals(b.members().keySet()))throw new IOException("Rerun output contract or complete member census differs");boolean byteEqual=true;int permissions=0,rows=0;
            for(String name:a.members().keySet()) {var x=a.members().get(name);var y=b.members().get(name);if(!x.kind().equals(y.kind()))throw new IOException("Rerun output member kind differs");if(x.mode()!=y.mode())permissions++;if(x.kind().equals("file")&&!x.pin().equals(y.pin()))byteEqual=false;}
            if(a.comparison().equals("exact")&&!byteEqual)throw new IOException("Fresh original-engine outputs differ byte-for-byte: "+a.root()+"/"+a.path()+". The new execution and both results are retained; no matching comparison is admitted.");
            if(a.comparison().equals("benchmark-measurement")) {
                Path oldFile=parent.executionRoot().resolve("objects").resolve(a.members().get("").pin().sha256()),newFile=result.executionRoot().resolve("objects").resolve(b.members().get("").pin().sha256());var limits=new org.jlinalg.workflow.SnakemakeBenchmarks.Limits(parent.request().capture().fileBytes(),parent.request().eventRows(),parent.request().metadataBytes());var x=org.jlinalg.workflow.SnakemakeBenchmarks.read(oldFile,limits);var y=org.jlinalg.workflow.SnakemakeBenchmarks.read(newFile,limits);rows=x.size();
                try(var oldReader=Files.newBufferedReader(oldFile,StandardCharsets.UTF_8);var newReader=Files.newBufferedReader(newFile,StandardCharsets.UTF_8)){if(rows!=y.size()||!oldReader.readLine().equals(newReader.readLine()))throw new IOException("Rerun benchmark schema/repeat-row count differs");}
            }
            compared.add(new Compared(a.root(),a.path(),a.kind(),a.comparison(),a.members().size(),rows,byteEqual,permissions));
        }
        var record=new TreeMap<String,Object>();record.put("schema_version",1);record.put("producer_root",producer);record.put("parent_manifest",parent.manifest());record.put("result_manifest",result.manifest());record.put("input_projection_sha256",sha(SnakemakeOriginalJson.json(inputs).getBytes(StandardCharsets.UTF_8)));record.put("comparisons",compared);record.put("parent_jobs",parent.events().jobs().size());record.put("result_jobs",result.events().jobs().size());record.put("parent_diagnostics",parent.events().diagnostics().size());record.put("result_diagnostics",result.events().diagnostics().size());return record;
    }
    private static Map<String,Object> projection(SnakemakeOriginalBundle.Verified verified)throws IOException {
        var source=verified.request().sources();var items=new TreeMap<String,SnakemakeOriginalCapture.Item>();for(var root:verified.inputs().roots())for(var row:root.items().entrySet())items.put(join(root.source(),row.getKey()),row.getValue());var tokens=new TreeMap<String,String>();for(String path:items.keySet())tokens.put(path,token(path,source));
        var anchors=new HashMap<String,String>();for(var row:items.entrySet())if(row.getValue().kind().equals("file")){String key=row.getValue().original().fileKey();if(key.equals("null"))key=row.getKey();String name=tokens.get(row.getKey());anchors.merge(key,name,(a,b)->a.compareTo(b)<=0?a:b);}
        var result=new TreeMap<String,Object>();for(var row:items.entrySet()) {var item=row.getValue();var value=new TreeMap<String,Object>();value.put("kind",item.kind());value.put("mode",item.mode());value.put("pin",item.original()==null?null:item.original().pin());value.put("target",item.kind().equals("symlink")?token(item.resolved(),source):null);if(item.kind().equals("file")){String key=item.original().fileKey();if(key.equals("null"))key=row.getKey();value.put("hardlink_group",anchors.get(key));}result.put(tokens.get(row.getKey()),value);}return result;
    }
    private static String token(String path,Map<String,String> sources)throws IOException {String token=null;for(var row:sources.entrySet())if(within(path,row.getValue())){String candidate=row.getKey()+path.substring(row.getValue().length());if(token==null||candidate.compareTo(token)<0)token=candidate;}if(token==null)throw new IOException("Input target is not in the captured replay family");return token;}
    private static void tools(SnakemakeOriginalBundle.Verified parent,Runnable check)throws IOException {
        for(Object row:list(json(parent.executionRoot().resolve("tools.json"),parent.request().metadataBytes()))) {Map<?,?> tool=object(row);Path supplied=Path.of(text(tool.get("supplied")));if(!Files.isExecutable(supplied))throw new SnakemakeOriginalRuntime.Unavailable("Rerun executable unavailable/forbidden: "+supplied+". Restore the declared permitted executable or skip this analysis; no tool/version fallback.");if(!pin(supplied.toRealPath(),1_073_741_824,check).equals(fingerprint(object(tool.get("identity")).get("pin"),1_073_741_824)))throw new IOException("Rerun executable bytes differ from the original declared tool");}
    }
    private static String warnings(SnakemakeOriginalBundle.Verified parent,SnakemakeOriginalBundle.Verified result,Map<?,?> comparison)throws IOException {
        String managed=parent.request().managed()==null?"":"\nWARNING: Managed replay executes the original deployers again under the unchanged installation permission. Borrowed software must retain its complete captured state at the original prefix. Permitted fresh installation uses NEW owned cache prefixes; software-tree bytes, timestamps and installation prefixes need not match. No installed Conda prefix is relocated. Literal software payload links/devices are never interpreted as host paths by portable verification. Public analysis and paper-role admission remain under qualification.\n";
        return "# Genuine original Snakemake rerun: scope and warnings\n\nFresh original-engine execution used the complete captured workflow/data family and unchanged declared settings, with recorded path rebinding. Exact comparisons require identical member names, kinds and file bytes. Benchmark comparisons require the same selected schema/repeat-row count and finite required measurements; timing/CPU/RSS values are freshly measured and are not required to match. Raw measurements and both complete output families are retained.\n\nWARNING: This is not an identical OS/author environment or a whole-paper/biological validation. Output permission differences are retained in comparisons and the two output records; byte comparison does not assert metadata equality.\n\nWARNING: Inspect parent and result WARNINGS.md, DIAGNOSTICS.json and source stderr, including recovered failures, before interpreting results. Original diagnostics: "+parent.events().diagnostics().size()+"; rerun diagnostics: "+result.events().diagnostics().size()+".\n\nLogical source jobs: original "+parent.events().jobs().size()+"; rerun "+result.events().jobs().size()+". A matching declared output family is not a claim that nondeterministic scheduling or transient jobs are identical.\n"+managed+"\n"+SnakemakeOriginalJson.json(comparison)+"\n";
    }
    private static void copy(Path source,Path target,SnakemakeOriginalRecipe.Request request,Runnable check)throws IOException {
        Files.createDirectory(target);Files.walkFileTree(source,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {check.run();if(!path.equals(source))Files.createDirectory(target.resolve(source.relativize(path)));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {check.run();if(!attrs.isRegularFile())throw new IOException("Only verified regular bundle files can be preserved in a replay");Pin before=pin(path,request.capture().fileBytes(),check);Path copy=target.resolve(source.relativize(path));Files.copy(path,copy);if(!before.equals(pin(copy,request.capture().fileBytes(),check))||!before.equals(pin(path,request.capture().fileBytes(),check)))throw new IOException("Parent/result bytes changed while preserving replay evidence");return FileVisitResult.CONTINUE;}
        });
    }
}
