/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed local workflow connecting existing FUMA modules and the original MAGMA adapter. */
final class FumaWorkflowCli {
    private FumaWorkflowCli() { }
    private static final Set<String> STAGES=Set.of("loci","mapping","gene_set","expression","cell_type","variant_annotation","magma_report","locus_novelty","known_associations","fisher_enrichment","magma_annotate","magma_gene","magma_enrichment");
    private static final Set<String> ROOT=Set.of("schema_version","build","out","max_bytes","max_rows","loci","mapping","variant_resources","gene_set","gene_set_query_policy","expression","cell_type","variant_annotation","magma_report","locus_novelty","known_associations","fisher_enrichment","fisher_known_source","magma_annotate","magma_gene","magma_enrichment");
    private static final Set<String> PATHS=Set.of("gwas","reference","ld","regions","predefined","genes","candidates","annotations","consequences","eqtls","interactions","ci-enhancers","ci-promoters","gene-scores","query","background","gmt-manifest","symbols","log2","norm","deg-sets","home","snps","annotation","reference-panel","pvalues","gene-bundle","datasets","sets","properties","joint","candidate_score_file","independent","reference-counts","bed-manifest","gene-results","gsa","leads","known","coverage","sources","families","members","tests","plan","counts","groups","comparisons","known-bundle");
    private static final String SCOPE="TYPED SUPPLIED-RESOURCE WORKFLOW ONLY. Existing native locus/mapping/GENE2FUNC modules and original MAGMA run with explicit inputs. No annotation or interaction discovery,score calculation,automatic installation,genome build inference or full hosted FUMA reconstruction. MAGMA uses separately supplied genome-wide SNP/GWAS inputs;it is never silently restricted to selected FUMA candidates. Required action: inspect every stage warning,source hash,resource-disposition and model/background choice.";
    @FunctionalInterface private interface Action {int run(Path out,PrintStream stdout,PrintStream stderr)throws Exception;}
    @FunctionalInterface private interface Child {int run(String[] args,PrintStream stdout,PrintStream stderr);}
    static int run(String[] args,PrintStream stdout,PrintStream stderr){
        if(Arrays.asList(args).contains("--help")){stdout.println(help());return 0;}
        Path destination=null;Runner runner=null;
        try{
            var options=options(args,Set.of("method","workflow-options-file","out"));if(options.containsKey("method")&&!options.get("method").equals("workflow"))throw new IOException("--method must be workflow");Path recipe=Path.of(required(options,"workflow-options-file")).toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(recipe,8388608);var root=AdmixtureInputs.decode(raw);for(Object key:root.keySet())if(!ROOT.contains(key))throw new IOException("Unknown workflow field: "+key);if(!(root.get("schema_version") instanceof Number n)||n.doubleValue()!=1)throw new IOException("workflow schema_version must be numeric1");String build=text(root,"build");if(!build.matches("[A-Za-z0-9_.-]{1,80}"))throw new IOException("Declare explicit simple workflow build identity;no build inference/liftover");
            var selected=new LinkedHashMap<String,Map<String,String>>();for(String name:STAGES)if(root.containsKey(name))selected.put(name,section(root.get(name),recipe));if(selected.isEmpty())throw new IOException("Select at least one typed workflow stage");
            boolean bridge=selected.containsKey("loci")&&selected.containsKey("mapping");if(bridge!=root.containsKey("variant_resources"))throw new IOException("variant_resources is required exactly when connecting loci to mapping");Map<String,String> resources=bridge?section(root.get("variant_resources"),recipe):Map.of();
            if(bridge)for(String key:List.of("candidates","annotations"))if(selected.get("mapping").containsKey(key))throw new IOException("Connected mapping "+key+" is generated from variant_resources;do not supply a conflicting override");
            String queryPolicy=root.containsKey("gene_set_query_policy")?text(root,"gene_set_query_policy"):null;if(selected.containsKey("gene_set")){if(queryPolicy==null||!Set.of("actual-links","external").contains(queryPolicy))throw new IOException("Select gene_set_query_policy actual-links or external");if(queryPolicy.equals("actual-links")&&(!selected.containsKey("mapping")||selected.get("gene_set").containsKey("query")))throw new IOException("actual-links query requires mapping and forbids an external query override");}else if(queryPolicy!=null)throw new IOException("gene_set_query_policy requires gene_set stage");
            if(selected.containsKey("magma_gene")&&selected.containsKey("magma_annotate")&&selected.get("magma_gene").containsKey("annotation"))throw new IOException("Connected MAGMA annotation is generated;do not override annotation");if(selected.containsKey("magma_enrichment")&&selected.containsKey("magma_gene")&&selected.get("magma_enrichment").containsKey("gene-bundle"))throw new IOException("Connected MAGMA gene bundle is generated;do not override gene-bundle");
            if(selected.containsKey("cell_type")&&selected.containsKey("magma_gene")&&selected.get("cell_type").containsKey("gene-bundle"))throw new IOException("Connected cell-type gene bundle is generated; do not override gene-bundle");
            boolean fisherBridge=root.containsKey("fisher_known_source");if(fisherBridge&&(!text(root,"fisher_known_source").equals("known-associations-stage")||!selected.containsKey("known_associations")||!selected.containsKey("fisher_enrichment")||!"groups".equals(selected.get("fisher_enrichment").get("mode"))||selected.get("fisher_enrichment").containsKey("known-bundle")))throw new IOException("fisher_known_source=known-associations-stage requires both stages,Fisher groups mode and no external known-bundle override");
            for(var e:selected.entrySet()){var stage=e.getValue();String fixed=switch(e.getKey()){case "loci"->"loci";case "mapping"->"map-genes";case "gene_set"->"gene-set";case "expression"->"expression";case "cell_type"->"cell-type";case "variant_annotation"->"annotate-variants";case "magma_report"->"magma-report";case "locus_novelty"->"locus-novelty";case "known_associations"->"known-associations";case "fisher_enrichment"->"fisher-enrichment";case "magma_gene"->"gene";default->null;};if(fixed!=null&&stage.containsKey("method")&&!stage.get("method").equals(fixed))throw new IOException("Typed method override differs for "+e.getKey());if(stage.containsKey("build")&&!stage.get("build").equals(build))throw new IOException("Stage build differs from workflow build");}
            if(selected.containsKey("magma_annotate")&&!Set.of("annotate","annotate-native").contains(required(selected.get("magma_annotate"),"method")))throw new IOException("magma_annotate method must be annotate or annotate-native");if(selected.containsKey("magma_enrichment")&&!Set.of("sets","properties","gsa").contains(required(selected.get("magma_enrichment"),"method")))throw new IOException("magma_enrichment method must be sets,properties or gsa");if(selected.containsKey("magma_gene"))required(selected.get("magma_gene"),"model");
            destination=options.containsKey("out")?Path.of(options.get("out")).toAbsolutePath().normalize():resolve(recipe,text(root,"out"));if(Files.exists(destination))throw new IOException("Workflow output already exists: "+destination);Files.createDirectories(destination.getParent());Files.createDirectory(destination);
            runner=new Runner(destination,build,recipe,AdmixtureInputs.digest(raw),new FumaWorkflowInputs.Limits(limit(root,"max_bytes",268435456,1,1073741824),limit(root,"max_rows",1000000,1,2000000)));Files.write(destination.resolve("workflow-config.json"),raw);Files.writeString(destination.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status","running","process_id",ProcessHandle.current().pid()))+"\n");
            if(selected.containsKey("variant_annotation"))runner.plan("variant-annotation",List.of());if(selected.containsKey("loci"))runner.plan("loci",List.of());if(bridge)runner.plan("variant-preparation",List.of("loci"));if(selected.containsKey("mapping"))runner.plan("mapping",bridge?List.of("variant-preparation"):List.of());boolean derive=selected.containsKey("gene_set")&&"actual-links".equals(queryPolicy);if(derive)runner.plan("gene-set-query",List.of("mapping"));if(selected.containsKey("gene_set"))runner.plan("gene-set",derive?List.of("gene-set-query"):List.of());if(selected.containsKey("expression"))runner.plan("expression",List.of());if(selected.containsKey("magma_annotate"))runner.plan("magma-annotation",List.of());if(selected.containsKey("magma_gene"))runner.plan("magma-gene",selected.containsKey("magma_annotate")?List.of("magma-annotation"):List.of());if(selected.containsKey("magma_enrichment"))runner.plan("magma-enrichment",selected.containsKey("magma_gene")?List.of("magma-gene"):List.of());if(selected.containsKey("cell_type"))runner.plan("cell-type",selected.containsKey("magma_gene")?List.of("magma-gene"):List.of());if(selected.containsKey("magma_report"))runner.plan("magma-report",List.of());if(selected.containsKey("locus_novelty"))runner.plan("locus-novelty",List.of());if(selected.containsKey("known_associations"))runner.plan("known-associations",List.of());if(selected.containsKey("fisher_enrichment"))runner.plan("fisher-enrichment",fisherBridge?List.of("known-associations"):List.of());runner.journal("running");
            if(selected.containsKey("locus_novelty")){var opts=selected.get("locus_novelty");opts.putIfAbsent("build",build);runner.child("locus-novelty",List.of(),opts,"locus-novelty",FumaNoveltyCli::run);}
            if(selected.containsKey("variant_annotation")){var opts=selected.get("variant_annotation");opts.putIfAbsent("build",build);runner.child("variant-annotation",List.of(),opts,"annotate-variants",FumaAnnotationCli::run);}
            if(selected.containsKey("loci"))runner.child("loci",List.of(),selected.get("loci"),"loci",FumaLociCli::run);
            if(bridge){var mapping=selected.get("mapping");var r=runner;runner.stage("variant-preparation",List.of("loci"),(dir,out,err)->{Files.createDirectory(dir);FumaWorkflowInputs.prepareVariants(r.out.resolve("loci"),resources,mapping,dir,r.hashes,r.limits);return 0;},true);}
            if(selected.containsKey("mapping"))runner.child("mapping",bridge?List.of("variant-preparation"):List.of(),selected.get("mapping"),"map-genes",FumaMappingCli::run);
            if(selected.containsKey("gene_set")){
                var geneSet=selected.get("gene_set");boolean derived=queryPolicy.equals("actual-links");if(derived){var r=runner;runner.stage("gene-set-query",List.of("mapping"),(dir,out,err)->{Files.createDirectory(dir);Path file=FumaWorkflowInputs.prepareQuery(r.out.resolve("mapping"),geneSet,dir,r.hashes,r.limits);geneSet.put("query",file.toString());return 0;},true);}runner.child("gene-set",derived?List.of("gene-set-query"):List.of(),geneSet,"gene-set",FumaToolsCli::run);
            }
            if(selected.containsKey("expression")){var opts=selected.get("expression");opts.putIfAbsent("build",build);runner.child("expression",List.of(),opts,"expression",FumaExpressionCli::run);}
            if(selected.containsKey("magma_annotate")){var opts=selected.get("magma_annotate");String method=required(opts,"method");if(!Set.of("annotate","annotate-native").contains(method))throw new IOException("magma_annotate method must be annotate or annotate-native");runner.magmaBuild(opts);runner.child("magma-annotation",List.of(),opts,method,MagmaToolsCli::run);}
            if(selected.containsKey("magma_gene")){var opts=selected.get("magma_gene");runner.magmaBuild(opts);required(opts,"model");if(selected.containsKey("magma_annotate"))opts.put("annotation",runner.out.resolve("magma-annotation").toString());if(opts.getOrDefault("input-mode","").equals("summary")&&opts.containsKey("pvalues")&&Path.of(opts.get("pvalues")).startsWith(runner.out))throw new IOException("MAGMA summary P-values must be a separately supplied genome-wide input outside generated locus/mapping outputs");runner.child("magma-gene",selected.containsKey("magma_annotate")?List.of("magma-annotation"):List.of(),opts,"gene",MagmaToolsCli::run);}
            if(selected.containsKey("magma_enrichment")){var opts=selected.get("magma_enrichment");runner.magmaBuild(opts);String method=required(opts,"method");if(!Set.of("sets","properties","gsa").contains(method))throw new IOException("magma_enrichment method must be sets,properties or gsa");if(selected.containsKey("magma_gene"))opts.put("gene-bundle",runner.out.resolve("magma-gene").toString());runner.child("magma-enrichment",selected.containsKey("magma_gene")?List.of("magma-gene"):List.of(),opts,method,MagmaToolsCli::run);}
            if(selected.containsKey("cell_type")){var opts=selected.get("cell_type");opts.putIfAbsent("build",build);if(selected.containsKey("magma_gene"))opts.put("gene-bundle",runner.out.resolve("magma-gene").toString());runner.child("cell-type",selected.containsKey("magma_gene")?List.of("magma-gene"):List.of(),opts,"cell-type",FumaCelltypeCli::run);}
            if(selected.containsKey("magma_report")){var opts=selected.get("magma_report");opts.putIfAbsent("build",build);runner.child("magma-report",List.of(),opts,"magma-report",FumaMagmaReportCli::run);}
            if(selected.containsKey("known_associations")){var opts=selected.get("known_associations");opts.putIfAbsent("build",build);runner.child("known-associations",List.of(),opts,"known-associations",FumaAssociationCli::run);}
            if(selected.containsKey("fisher_enrichment")){var opts=selected.get("fisher_enrichment");opts.putIfAbsent("build",build);if(fisherBridge)opts.put("known-bundle",runner.out.resolve("known-associations").toString());runner.child("fisher-enrichment",fisherBridge?List.of("known-associations"):List.of(),opts,"fisher-enrichment",FumaFisherCli::run);}
            int code=runner.finish(null);stderr.println((code==0?"WORKFLOW WARNINGS: ":code==3?"WORKFLOW PARTIALLY SKIPPED: ":"WORKFLOW FAILED: ")+destination.resolve("WARNINGS.md"));if(code==0)stdout.println("Typed local FUMA workflow completed: "+destination);return code;
        }catch(Exception failure){if(runner!=null)try{runner.finish(failure.toString());}catch(Exception reportFailure){stderr.println("Workflow diagnostic publication failed: "+reportFailure);}stderr.println("WORKFLOW FAILED: "+failure.getMessage()+"\nRequired action: inspect retained workflow/stage diagnostics,correct the named configuration/resource cause and rerun to a new directory. No completed workflow is admitted.\n"+SCOPE);return 2;}
    }
    private static final class Runner {
        final Path out,recipe;final String build;final FumaWorkflowInputs.Limits limits;final Map<Path,String> hashes=new LinkedHashMap<>();final LinkedHashMap<String,Map<String,Object>> stages=new LinkedHashMap<>();final String started=Instant.now().toString();
        Runner(Path out,String build,Path recipe,String digest,FumaWorkflowInputs.Limits limits)throws IOException{this.out=out;this.build=build;this.recipe=recipe;this.limits=limits;FumaWorkflowInputs.record(hashes,recipe,digest);Files.createDirectory(out.resolve("logs"));}
        void plan(String id,List<String> dependencies){var row=new LinkedHashMap<String,Object>();row.put("stage",id);row.put("directory",id);row.put("dependencies",dependencies);row.put("status","pending");stages.put(id,row);}
        void magmaBuild(Map<String,String> options)throws IOException{String declared=options.putIfAbsent("build",build);if(declared!=null&&!declared.equals(build))throw new IOException("MAGMA build differs from the declared workflow build");}
        void child(String id,List<String> dependencies,Map<String,String> input,String method,Child child)throws Exception{
            var options=new LinkedHashMap<>(input);if(options.containsKey("method")&&!options.get("method").equals(method))throw new IOException("Stage "+id+" method override differs from typed method "+method);options.put("method",method);options.put("out",out.resolve(id).toString());stage(id,dependencies,(dir,stdout,stderr)->{var args=new ArrayList<String>();options.forEach((k,v)->{args.add("--"+k);args.add(v);});return child.run(args.toArray(String[]::new),stdout,stderr);},false);
        }
        void stage(String id,List<String> dependencies,Action action,boolean preparation)throws Exception{
            Path directory=out.resolve(id);var record=new LinkedHashMap<String,Object>();record.put("stage",id);record.put("dependencies",dependencies);record.put("directory",id);record.put("started_at",Instant.now().toString());record.put("status","running");stages.put(id,record);journal("running");
            for(String dependency:dependencies){var prior=stages.get(dependency);if(prior==null||!Integer.valueOf(0).equals(prior.get("exit_code"))){Files.createDirectory(directory);String cause="Required stage "+dependency+" did not complete. No dependent analysis ran.";record.put("status","skipped-upstream");record.put("exit_code",3);record.put("cause",cause);Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status","skipped-upstream","cause",cause))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# DEPENDENT ANALYSIS SKIPPED\n\n"+cause+"\n\nRequired action: inspect the upstream named cause,provide permitted resources/software or correct the scientific/configuration failure,and rerun to a new workflow directory.\n");Files.writeString(out.resolve("logs").resolve(id+".stderr"),cause+"\n");Files.writeString(out.resolve("logs").resolve(id+".stdout"),"");record.put("completed_at",Instant.now().toString());collect(directory);journal("running");return;}}
            int code;String failure=null;var standard=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();try(var stdout=new PrintStream(standard,true,StandardCharsets.UTF_8);var stderr=new PrintStream(errors,true,StandardCharsets.UTF_8)){
                try{code=action.run(directory,stdout,stderr);if(!Set.of(0,2,3).contains(code)){failure="Unexpected child exit code "+code;code=2;}}
                catch(Exception error){code=error instanceof FumaWorkflowInputs.NoEligibleInput?3:2;failure=error.toString();stderr.println(failure);if(preparation&&Files.exists(directory))RnaToolRuntime.quarantine(directory);}
            }
            Files.write(out.resolve("logs").resolve(id+".stdout"),standard.toByteArray());Files.write(out.resolve("logs").resolve(id+".stderr"),errors.toByteArray());if(!Files.exists(directory))Files.createDirectory(directory);
            if(preparation||!Files.exists(directory.resolve("analysis-status.json")))Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",code==0?"prepared":code==3?"skipped-required-input":"failed"))+"\n");
            if(!Files.exists(directory.resolve("WARNINGS.md")))Files.writeString(directory.resolve("WARNINGS.md"),"# WORKFLOW STAGE "+(code==0?"REVIEW":"NOT COMPLETED")+"\n\n"+(failure==null?errors.toString(StandardCharsets.UTF_8):failure)+"\n\nRequired action: "+(code==0?"Review supplied input identities and every disposition;preparation/annotation is not association.":"Inspect this stage's logs and named cause;correct inputs or supply permitted software and rerun to a new workflow directory.")+"\n");
            record.put("exit_code",code);record.put("status",code==0?"completed":code==3?"skipped":"failed");if(failure!=null)record.put("cause",failure);record.put("completed_at",Instant.now().toString());
            try{collect(directory);}catch(Exception integrity){record.put("exit_code",2);record.put("status","failed-integrity");record.put("cause",integrity.toString());}
            journal("running");
        }
        void collect(Path directory)throws Exception{
            Path manifest=directory.resolve("manifest.yaml");if(Files.isRegularFile(manifest)){
                var loader=new LoaderOptions();loader.setCodePointLimit(33554432);Object value=new Yaml(new SafeConstructor(loader)).load(Files.readString(manifest));if(!(value instanceof Map<?,?> metadata))throw new IOException("Invalid child manifest: "+manifest);
                for(String field:List.of("input_sha256","source_sha256"))if(metadata.get(field) instanceof Map<?,?> input)for(var e:input.entrySet()){Path file=Path.of(e.getKey().toString());if(!file.isAbsolute()&&metadata.get("options") instanceof Map<?,?> options&&options.get(e.getKey()) instanceof String path)file=Path.of(path);if(file.isAbsolute())FumaWorkflowInputs.record(hashes,file,e.getValue().toString());}
            }
            try(var files=Files.walk(directory)){for(Path p:files.filter(Files::isRegularFile).toList()){if(Files.isSymbolicLink(p))throw new IOException("Linked completed-stage output is not admitted: "+p);FumaWorkflowInputs.record(hashes,p,hash(p));}}
        }
        void journal(String status)throws IOException{
            var record=new LinkedHashMap<String,Object>();record.put("schema_version",1);record.put("status",status);record.put("started_at",started);record.put("updated_at",Instant.now().toString());record.put("process_id",ProcessHandle.current().pid());record.put("build",build);record.put("stages",stages);Path temporary=out.resolve(".workflow-journal.tmp");Files.writeString(temporary,RegenieRuntime.json(record)+"\n");try{Files.move(temporary,out.resolve("workflow-journal.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,out.resolve("workflow-journal.json"),StandardCopyOption.REPLACE_EXISTING);}
        }
        int finish(String fatal)throws Exception{
            for(var stage:stages.values())if(!stage.containsKey("exit_code")){stage.put("exit_code",3);stage.put("status","not-run");stage.put("cause",fatal==null?"Selected stage did not run":fatal);if(fatal==null)fatal="One or more selected stages did not run";}
            if(fatal==null)for(var e:hashes.entrySet())if(!Files.isRegularFile(e.getKey())||!hash(e.getKey()).equals(e.getValue())){fatal="Workflow input/completed-stage output changed: "+e.getKey();break;}
            boolean failed=fatal!=null||stages.values().stream().anyMatch(s->Integer.valueOf(2).equals(s.get("exit_code"))),skipped=stages.values().stream().anyMatch(s->Integer.valueOf(3).equals(s.get("exit_code")));int code=failed?2:skipped?3:0;String status=code==0?"completed-with-review-warnings":code==3?"partially-skipped":"failed";var source=new LinkedHashMap<String,String>();hashes.forEach((p,h)->source.put(p.toString(),h));var record=new LinkedHashMap<String,Object>();record.put("schema_version",1);record.put("status",status);record.put("exit_code",code);record.put("build",build);record.put("started_at",started);record.put("completed_at",Instant.now().toString());record.put("source_and_stage_sha256",source);record.put("stages",stages);record.put("scope",SCOPE);record.put("stable_stage_paths",true);if(fatal!=null)record.put("error",fatal);
            var warnings=new ArrayList<String>();warnings.add("# FUMA WORKFLOW "+status.toUpperCase(java.util.Locale.ROOT));warnings.add(SCOPE);warnings.add("STABLE MULTI-STAGE OUTPUT: this new directory is a journaled workspace,not an atomically renamed single analysis. Stage bundles keep their actual execution paths. A running/interrupted/failed/partially-skipped journal is not a completed workflow. Require both completed final manifest and analysis-status markers. Completed independent stage outputs remain retained;review their own hashes and warnings before any independent use.");for(var e:stages.entrySet())warnings.add(e.getKey()+": "+e.getValue().get("status")+"; exit="+e.getValue().get("exit_code")+". Read "+e.getKey()+"/WARNINGS.md and logs/"+e.getKey()+".stderr.");if(fatal!=null)warnings.add("WORKFLOW NOT ADMITTED: "+fatal);warnings.add("Required action: review every stage/input/resource/namespace/model/background choice. For missing or forbidden MAGMA software,skip dependent analyses and use the exact installation/recovery named by its warning. Correct failed data/identity/budget contracts and rerun to a NEW workflow directory;no automatic retry,installation or candidate/score synthesis occurs. Cell Ranger remains excluded.");Files.writeString(out.resolve("WARNINGS.md"),String.join("\n\n",warnings)+"\n");journal(status);var outputs=new LinkedHashMap<String,String>();try(var files=Files.walk(out)){for(Path p:files.filter(Files::isRegularFile).toList())if(!p.equals(out.resolve("manifest.json"))&&!p.equals(out.resolve("analysis-status.json")))outputs.put(out.relativize(p).toString(),hash(p));}record.put("output_sha256",outputs);Files.writeString(out.resolve("manifest.json"),RegenieRuntime.json(record)+"\n");Files.writeString(out.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",status,"exit_code",code))+"\n");return code;
        }
    }
    private static Map<String,String> section(Object value,Path recipe)throws IOException{
        if(!(value instanceof Map<?,?> map))throw new IOException("Workflow stages/resources must be JSON objects of string option values");var result=new LinkedHashMap<String,String>();for(var e:map.entrySet()){if(!(e.getKey() instanceof String key)||!(e.getValue() instanceof String text)||text.isBlank()||text.codePoints().anyMatch(Character::isISOControl)||key.equals("out")||key.endsWith("-options-file"))throw new IOException("Unknown type,empty/control value,reserved out or recursive carrier in workflow section");if(PATHS.contains(key)&&!text.equals("none"))text=resolve(recipe,text).toString();result.put(key,text);}return result;
    }
    private static Path resolve(Path recipe,String value){Path path=Path.of(value);return (path.isAbsolute()?path:recipe.getParent().resolve(path)).toAbsolutePath().normalize();}
    private static String text(Map<?,?> map,String key)throws IOException{if(!(map.get(key) instanceof String value)||value.isBlank()||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require explicit workflow string "+key);return value;}
    private static int limit(Map<?,?> root,String key,int fallback,int low,int high)throws IOException{if(!root.containsKey(key))return fallback;Object v=root.get(key);if(!(v instanceof Number n)||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<low||n.doubleValue()>high)throw new IOException("Invalid workflow "+key+" bound");return n.intValue();}
    static String help(){return """
        fuma-tools --method workflow --workflow-options-file WORKFLOW.json [--out NEW_DIRECTORY]
        Supplied-reference novelty uses independent locus_novelty with explicit leads/known/coverage,
          known-build,catalog-id,analysis-id,radius,boundary,trait-policy. No automatic loci bridge.
        Known association unions use independent known_associations with explicit sources,
          families,members,tests,cutoff,matching,absence and gene/trait namespaces.
          Per-source annotation/build labels are preserved; no coordinate join is performed.
        Fisher enrichment uses fisher_enrichment with explicit counts or groups mode,
          families,plan,cutoff and scientific policies. It is independent by default.
          To import this workflow's completed known_associations stage,declare root
          fisher_known_source=known-associations-stage and Fisher mode=groups;
          do not override known-bundle. A failed/skipped upstream stage skips that
          dependent Fisher analysis. External bundles retain their own verified hashes.
        JSON schema_version=1,explicit build,and selected stages:
          loci,mapping,gene_set,expression,cell_type,variant_annotation,magma_report,locus_novelty,known_associations,fisher_enrichment,magma_annotate,magma_gene,magma_enrichment.
        Stage options are string values;paths are relative to the workflow file.
        loci+mapping requires variant_resources annotations(uid,chr,pos,CADD,RDB),
          candidate_score_source=annotations|file,optional candidate_score_file,
          extra_rows=error|exclude-with-audit. Generated candidate/annotation paths
          and LD memberships are audited;no variant annotation is inferred.
        gene_set_query_policy=actual-links derives supported Ensembl IDs from mapping;
          external uses explicitly supplied query. No declaration-only query genes.
        expression uses explicit resolved-ID query/background and supplied log2/norm/DEG
          resources; no automatic query bridge or expression/DEG generation.
        cell_type uses an explicit sealed gene-bundle or the selected magma_gene output;
          supplied dataset properties, correction, missingness and step flags are required.
        magma_report postprocesses explicitly supplied gene/set results independently;
          Pbon means Holm, with explicit missing-P and report-placeholder policies.
          It does not silently connect generated fits or infer a paper correction family.
        variant_annotation postprocesses explicit supplied SNP/consequence/score/state/LD/
          reference inputs. It is an independent stage; no raw-label projection to numeric
          mapping scores, annotation prediction or automatic locus/mapping bridge is inferred.
        MAGMA annotation and genome-wide GWAS/reference inputs are separate from
          locus selection;explicit methods,model,ancestry,build and runtime required.
        New output is a stable journaled workspace;child bundles remain at actual paths.
        Existing outputs never overwritten;no resume/automatic install. Inspect journal,
          manifest,root/stage WARNINGS and logs. Exit0 all selected stages completed;
          3 partial skips;2 any failure. Independent branches can complete after a skip.
        """+SCOPE;
    }
}
