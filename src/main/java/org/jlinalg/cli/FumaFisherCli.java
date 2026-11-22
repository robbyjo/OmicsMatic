/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FumaFisherExact;
import org.jlinalg.genetics.FumaFisherFamilies;
import org.jlinalg.genetics.FumaFisherGroups;
import org.jlinalg.genetics.FumaKnownAssociations;
import static org.jlinalg.cli.FollowupSupport.*;

/** Bounded complete-family Fisher enrichment with original counts and grouping evidence. */
final class FumaFisherCli {
    private FumaFisherCli() { }
    private static final Set<String> ALLOWED=Set.of("method","fisher-options-file","mode","analysis-id","build","families","plan","counts","known-bundle","groups","comparisons","gene-namespace","trait-namespace","matching","unassessable","cutoff","max-bytes","max-rows","max-line-bytes","max-cells","max-snapshot-bytes","max-output-bytes","max-output-rows","max-bundle-files","max-population","max-support","max-support-states","max-operations","max-root-iterations","max-evidence","timeout","out");
    private static final Set<String> PATHS=Set.of("families","plan","counts","known-bundle","groups","comparisons","out");
    private static final String SCOPE="DECLARED FISHER ENRICHMENT. Rows are finer/comparator and columns novel/known. Original integer counts,explicit cell pseudocounts,conditional odds/central confidence intervals and complete correction families remain visible. Required action: verify all planned IDs,group membership and independence,original classification/matching keys,missing/empty/overlap policies,correction arithmetic and unrounded root diagnostics. Numerical consistency does not identify an unreported historical paper call. No historical Bonferroni/BH,grouping,build conversion or full FUMA implementation is inferred. Cell Ranger remains excluded.";
    private record RootRow(int table,FumaFisherExact.RootAudit root) { }
    private record WarningRow(int table,String warning) { }
    private record Planned(String family,String id) { }
    private record Contributor(int key,int input) { }
    private record SharedContributor(int key,String side,int input) { }
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        if(Arrays.asList(args).contains("--help")){stdout.println(help());return 0;}
        try {
            var options=parse(args);if(options.containsKey("method")&&!options.get("method").equals("fisher-enrichment"))throw new IOException("--method must be fisher-enrichment");
            for(String key:List.of("mode","analysis-id","families","plan","cutoff","out"))required(options,key);
            String mode=options.get("mode");if(!Set.of("counts","groups").contains(mode))throw new IOException("--mode must be counts or groups");
            var groupKeys=List.of("known-bundle","groups","comparisons","gene-namespace","trait-namespace","matching","unassessable");
            if(mode.equals("counts")){required(options,"counts");for(String key:groupKeys)if(options.containsKey(key))throw new IOException("--"+key+" is unused/forbidden in counts mode");}
            else {for(String key:groupKeys)required(options,key);if(options.containsKey("counts"))throw new IOException("Groups mode forbids supplied --counts;count tables are generated from classified source keys");}
            int[] code={0};externalTransaction(options,"fuma-fisher-enrichment",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();warnings.add(SCOPE);session.put("external_runtime_required",false);session.put("historical_paper_defaults_inferred",false);session.put("mode",mode);
                FumaFisherInputs input=null;String phase="options and input preparation";
                try {
                    FumaFisherInputs.identity(options.get("analysis-id"));session.put("analysis-id",options.get("analysis-id"));if(options.containsKey("build")){FumaFisherInputs.identity(options.get("build"));session.put("analysis_build_metadata",options.get("build"));}
                    long started=System.nanoTime(),timeout=integer(options,"timeout",300,1,86400);Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-started>timeout*1000000000L)throw new IllegalStateException("Fisher interrupted or --timeout exceeded;review complete populations/limits and rerun to a NEW output");};
                    var limits=new FumaAssociationInputs.Limits(integer(options,"max-bytes",268435456,1,1073741824),integer(options,"max-rows",1000000,1,10000000),integer(options,"max-line-bytes",1048576,64,16777216),whole(options,"max-cells",50000000,500000000),whole(options,"max-snapshot-bytes",1073741824,1099511627776L),whole(options,"max-output-bytes",1073741824,1099511627776L),whole(options,"max-output-rows",5000000,100000000));
                    long maximumOperations=whole(options,"max-operations",1000000000,10000000000L);double cutoff=FumaFisherInputs.decimal(options.get("cutoff"));
                    var modelOptions=new FumaFisherFamilies.Options(cutoff,new FumaFisherFamilies.Limits(limits.rows(),integer(options,"max-population",10000000,1,Integer.MAX_VALUE),integer(options,"max-support",200000,1,1000000),whole(options,"max-support-states",2000000,100000000),maximumOperations,integer(options,"max-root-iterations",1000,1,100000)));
                    FumaFisherGroups.Options groupOptions=null;
                    if(mode.equals("groups")) {
                        for(String key:List.of("gene-namespace","trait-namespace")){FumaFisherInputs.identity(options.get(key));session.put(key,options.get(key));}
                        var matching=switch(options.get("matching")){case "gene-trait"->FumaKnownAssociations.Matching.GENE_TRAIT;case "gene-across-traits"->FumaKnownAssociations.Matching.GENE_ACROSS_TRAITS;default->throw new IOException("--matching must equal original gene-trait or gene-across-traits mode");};
                        var unassessable=switch(options.get("unassessable")){case "error"->FumaFisherGroups.Unassessable.ERROR;case "exclude"->FumaFisherGroups.Unassessable.EXCLUDE;default->throw new IOException("--unassessable must be error or exclude");};
                        groupOptions=new FumaFisherGroups.Options(matching,unassessable,new FumaFisherGroups.Limits(limits.rows(),whole(options,"max-evidence",2000000,100000000),maximumOperations));session.put("matching",options.get("matching"));session.put("unassessable",options.get("unassessable"));
                    }
                    input=new FumaFisherInputs(directory,limits,check);
                    if(options.containsKey("fisher-options-file")){Path snapshot=input.transport.capture(Path.of(options.get("fisher-options-file")),"Fisher options carrier",1048576);if(!hash(snapshot).equals(options.get("options-sha256")))throw new IOException("Fisher options carrier changed after parsing");}
                    var data=input.prepare(options,groupOptions,integer(options,"max-bundle-files",4096,1,100000));input.verify();
                    phase="complete-family Fisher fitting and adjustment";
                    if(data.groups()!=null){long remaining=maximumOperations-data.groups().operations();if(remaining<1)throw new IOException("Combined grouping/Fisher --max-operations exceeded");var l=modelOptions.limits();modelOptions=new FumaFisherFamilies.Options(cutoff,new FumaFisherFamilies.Limits(l.maximumInputRows(),l.maximumPopulation(),l.maximumSupportPerTable(),l.maximumSupportStates(),remaining,l.maximumRootIterations()));}
                    var result=FumaFisherFamilies.fit(data.families(),data.tables(),modelOptions,check);warnings.addAll(data.warnings());if(data.groups()!=null)warnings.addAll(data.groups().warnings());warnings.addAll(result.warnings());
                    phase="complete Fisher evidence publication";publish(directory,input,data,result,check);
                    var primitiveWarnings=new LinkedHashSet<String>();for(var assessed:result.tables())if(assessed.fit()!=null){check.run();primitiveWarnings.addAll(assessed.fit().warnings());}warnings.addAll(primitiveWarnings);
                    phase="final source and snapshot integrity verification";input.verify();
                    session.put("inclusive_adjusted_cutoff",cutoff);session.put("table_rows",result.tables().size());session.put("family_rows",result.families().size());session.put("support_states",result.supportStates());session.put("fit_and_adjustment_operations",result.operations());session.put("group_operations",data.groups()==null?0:data.groups().operations());session.put("group_evidence",data.groups()==null?0:data.groups().evidence());session.put("group_rows",data.groups()==null?0:data.groups().groups().size());session.put("imported_known_associations",data.imported());session.put("input_cells",input.cells());session.put("snapshot_bytes",input.transport.snapshotBytes());session.put("scientific_files_and_snapshot_bytes",input.transport.output.bytes());session.put("scientific_result_rows",input.transport.output.rows());session.put("status","completed-with-review-warnings");manifest.put("analysis_status","completed-with-review-warnings");
                } catch(Exception failure) {
                    code[0]=2;session.put("status","failed");session.put("cause",failure.toString());session.put("failed_phase",phase);session.put("retained_files_location","partial/");if(input!=null)session.put("last_input_context",input.context());manifest.put("analysis_status","failed");manifest.put("error",failure.toString());RnaToolRuntime.quarantine(directory);Files.createDirectories(directory.resolve("partial"));warnings.add(0,"FISHER ENRICHMENT FAILED during "+phase+": "+failure.getMessage()+". Required action: inspect the named count/family/group/bundle/capacity cause and partial/ diagnostics;correct it and rerun to a NEW output directory. No complete Fisher result is admitted.");stderr.println("FISHER ENRICHMENT FAILED during "+phase+": "+failure.getMessage()+"\nRequired action: inspect WARNINGS.md and partial/ in the requested output;correct this cause and rerun to a NEW output directory.");
                }
                var hashes=new LinkedHashMap<String,String>();if(input!=null)input.transport.hashes.forEach((path,digest)->hashes.put(path.toString(),digest));session.put("source_sha256",hashes);manifest.put("source_sha256",hashes);session.put("warnings",warnings);
                Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",session.get("status"),"exit_code",code[0]))+"\n");
                Files.writeString(directory.resolve("WARNINGS.md"),"# "+(code[0]==0?"FISHER ENRICHMENT REVIEW REQUIRED":"FISHER ENRICHMENT FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect complete planned/fitted families,original and tested counts,all group contributors/exclusions/overlaps and every root audit. Original empty groups remain empty evidence even when pseudocount fits are retained. Correct failures and rerun to a NEW output. This Java-only operation never installs software. If required upstream software is unavailable or forbidden,SKIP its affected upstream analysis and provide valid permitted completed results;no missing association is invented.\n");
            });stderr.println((code[0]==0?"FISHER ENRICHMENT REVIEW REQUIRED: ":"FISHER ENRICHMENT FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)stdout.println("Declared Fisher enrichment completed: "+options.get("out"));return code[0];
        } catch(Exception failure){stderr.println("FISHER ENRICHMENT FAILED: "+failure.getMessage()+"\nRequired action: correct the named options/path/output cause and rerun to a NEW output directory. Existing outputs remain intact.\n"+SCOPE);return 2;}
    }
    private static void publish(Path dir,FumaFisherInputs input,FumaFisherInputs.Prepared data,FumaFisherFamilies.Result result,Runnable check)throws Exception {
        var out=input.transport.output;var planned=new ArrayList<Planned>();for(var family:data.families())for(String id:family.expectedTests()){check.run();planned.add(new Planned(family.id(),id));}
        out.table(dir.resolve("planned-tests.tsv"),List.of("family","test_id"),planned.size(),i->new String[]{planned.get(i).family(),planned.get(i).id()},true);
        var definitions=new LinkedHashMap<String,FumaFisherFamilies.Family>();for(var family:data.families())definitions.put(family.id(),family);
        out.table(dir.resolve("family-audit.tsv"),List.of("family","correction","encoding","missing_policy","empty_policy","pseudocount","confidence","expected_rows","actual_rows","available_rows","computed_adjustment_size","missing_rows","empty_original_groups","selected_rows","adjusted_probability_underflows"),result.families().size(),i->{var a=result.families().get(i);var f=definitions.get(a.family());return fields(a.family(),f.correction(),f.encoding(),f.missing(),f.empty(),f.pseudocount(),f.confidenceLevel(),a.expectedRows(),a.actualRows(),a.availableRows(),a.adjustmentSize(),a.missingRows(),a.emptyOriginalGroups(),a.selectedRows(),a.adjustedProbabilityUnderflows());},true);
        out.table(dir.resolve("fisher-tables.tsv"),List.of("input_row","family","test_id","trait","context","status","original_finer_novel","original_finer_known","original_comparator_novel","original_comparator_known","tested_finer_novel","tested_finer_known","tested_comparator_novel","tested_comparator_known","pseudocount","confidence","p","ln_p","conditional_odds_ratio","lower_confidence","upper_confidence","support_states","adjusted_p","adjusted_ln_p","selected"),result.tables().size(),i->{var a=result.tables().get(i);var t=a.table();var f=a.fit();var c=t.counts();var tested=f==null?null:f.tested();return fields(a.inputRow(),t.family(),t.id(),t.trait(),t.context(),a.status(),c==null?null:c.finerNovel(),c==null?null:c.finerKnown(),c==null?null:c.comparatorNovel(),c==null?null:c.comparatorKnown(),tested==null?null:tested.finerNovel(),tested==null?null:tested.finerKnown(),tested==null?null:tested.comparatorNovel(),tested==null?null:tested.comparatorKnown(),a.family().pseudocount(),a.family().confidenceLevel(),f==null?null:f.pValue(),f==null?null:f.logPValue(),f==null?null:f.oddsRatio(),f==null?null:f.lowerConfidence(),f==null?null:f.upperConfidence(),f==null?null:f.supportStates(),a.adjustedP(),a.adjustedLogP(),a.selected());},true);
        var roots=new ArrayList<RootRow>();var fitWarnings=new ArrayList<WarningRow>();for(var a:result.tables())if(a.fit()!=null){for(var root:a.fit().roots()){check.run();roots.add(new RootRow(a.inputRow(),root));}for(String warning:a.fit().warnings()){check.run();fitWarnings.add(new WarningRow(a.inputRow(),warning));}}
        out.table(dir.resolve("root-audit.tsv"),List.of("table_input_row","quantity","inverse_parameterization","root","remaining_bracket_width","iterations"),roots.size(),i->{var r=roots.get(i);return fields(r.table(),r.root().quantity(),r.root().inverse(),r.root().root(),r.root().bracketWidth(),r.root().iterations());},true);
        out.table(dir.resolve("fit-warnings.tsv"),List.of("table_input_row","warning"),fitWarnings.size(),i->fields(fitWarnings.get(i).table(),fitWarnings.get(i).warning()),true);
        out.table(dir.resolve("known-association-sources.tsv"),List.of("source","role","model","resource","gene_namespace","trait_namespace","gene_annotation","genome_build"),data.sources().size(),i->data.sources().get(i),true);
        var groups=data.groups();if(groups==null)return;
        out.table(dir.resolve("group-audit.tsv"),List.of("group","expected_rows","actual_rows","selected_rows","known_keys","novel_keys","unassessable_keys","missing_target_rows","unselected_rows"),groups.groups().size(),i->{var a=groups.groups().get(i);return fields(a.group(),a.expectedRows(),a.actualRows(),a.selectedRows(),a.knownKeys(),a.novelKeys(),a.unassessableKeys(),a.missingTargetRows(),a.unselectedRows());},true);
        out.table(dir.resolve("group-members.tsv"),List.of("group","source","trait","context","expected_rows","actual_rows"),groups.members().size(),i->{var a=groups.members().get(i);return fields(a.group(),a.member().source(),a.member().trait(),a.member().context(),a.member().expectedRows(),a.actualRows());},true);
        out.table(dir.resolve("group-keys.tsv"),List.of("group_key_row","group","gene","matched_trait","decision","counted_before_comparison_overlap","contributor_rows"),groups.keys().size(),i->{var k=groups.keys().get(i);return fields(i+1,k.group(),k.key().gene(),k.key().trait(),k.decision(),k.counted(),k.inputRows().size());},true);
        var contributors=new ArrayList<Contributor>();for(int i=0;i<groups.keys().size();i++)for(int row:groups.keys().get(i).inputRows()){check.run();contributors.add(new Contributor(i+1,row));}
        out.table(dir.resolve("group-contributors.tsv"),List.of("group_key_row","known_association_input_row"),contributors.size(),i->fields(contributors.get(i).key(),contributors.get(i).input()),true);
        out.table(dir.resolve("group-dispositions.tsv"),List.of("group","known_association_input_row","source","source_trait","source_context","gene","matched_trait","decision","disposition"),groups.dispositions().size(),i->{var d=groups.dispositions().get(i);var o=d.observation();return fields(d.group(),o.inputRow(),o.source(),o.trait(),o.context(),o.key().gene(),o.key().trait(),o.decision(),d.disposition());},true);
        out.table(dir.resolve("shared-keys.tsv"),List.of("shared_key_row","family","test_id","gene","matched_trait","overlap_policy","excluded_from_finer","finer_contributors","comparator_contributors"),groups.sharedKeys().size(),i->{var k=groups.sharedKeys().get(i);return fields(i+1,k.family(),k.comparison(),k.key().gene(),k.key().trait(),k.policy(),k.excludedFromFiner(),k.finerRows().size(),k.comparatorRows().size());},true);
        var shared=new ArrayList<SharedContributor>();for(int i=0;i<groups.sharedKeys().size();i++){var k=groups.sharedKeys().get(i);for(int row:k.finerRows()){check.run();shared.add(new SharedContributor(i+1,"finer",row));}for(int row:k.comparatorRows()){check.run();shared.add(new SharedContributor(i+1,"comparator",row));}}
        out.table(dir.resolve("shared-contributors.tsv"),List.of("shared_key_row","side","known_association_input_row"),shared.size(),i->fields(shared.get(i).key(),shared.get(i).side(),shared.get(i).input()),true);
        out.table(dir.resolve("unassigned-targets.tsv"),List.of("known_association_input_row","source","source_trait","source_context","gene","matched_trait","decision"),groups.unassigned().size(),i->{var o=groups.unassigned().get(i);return fields(o.inputRow(),o.source(),o.trait(),o.context(),o.key().gene(),o.key().trait(),o.decision());},true);
    }
    private static String[] fields(Object... values){String[] result=new String[values.length];for(int i=0;i<values.length;i++){Object value=values[i];result[i]=value==null?"NA":value instanceof Double d&&d==Double.NEGATIVE_INFINITY?"-Inf":value instanceof Double d&&d==Double.POSITIVE_INFINITY?"Inf":value.toString();}return result;}
    private static long whole(Map<String,String> options,String key,long fallback,long maximum)throws IOException{long value=FumaFisherInputs.integer(options.getOrDefault(key,""+fallback));if(value<1||value>maximum)throw new IOException("Unsupported positive --"+key+" capacity");return value;}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("fisher-options-file"))return direct;Path path=Path.of(direct.get("fisher-options-file")).toAbsolutePath().normalize();byte[] raw=FumaAssociationInputs.carrier(path);var result=new LinkedHashMap<String,String>();
        for(var entry:AdmixtureInputs.decode(raw).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("fisher-options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown,recursive or non-string Fisher option: "+key);result.put(key,PATHS.contains(key)?path.getParent().resolve(value).normalize().toString():value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    static String help(){return """
        fuma-tools --method fisher-enrichment [--fisher-options-file OPTIONS.json]
        Required: --mode counts|groups --analysis-id ID --families FAMILIES.tsv
          --plan PLAN.tsv --cutoff Q --out NEW_DIRECTORY
        Counts mode: --counts COUNTS.tsv
        Groups mode: --known-bundle COMPLETED_KNOWN_ASSOCIATIONS --groups GROUPS.tsv
          --comparisons COMPARISONS.tsv --gene-namespace ID --trait-namespace ID
          --matching gene-trait|gene-across-traits --unassessable error|exclude
        Families columns: family,correction,encoding,missing,empty,pseudocount,confidence.
          Correction NONE|BH|BY|HOLM|BONFERRONI;encoding P_VALUE|NATURAL_LOG;
          missing ERROR|EXCLUDE;empty ERROR|RETAIN_SOURCE;integer pseudocount >=0;
          confidence in (0,1). Plan: family,test_id declares every comparison ID.
          Complete declared available families are fitted/corrected before selection.
        Counts: family,test_id,trait,context,finer_novel,finer_known,comparator_novel,
          comparator_known. Four exact nonnegative integers,or four literal NA cells.
          Missing tables remain visible and require EXCLUDE;partial missing is invalid.
        Groups: group,source,trait,context,expected_rows includes missing/unselected rows.
          Selected matching keys count once per group;all contributor rows are retained.
        Comparisons: family,test_id,trait,context,finer_group,comparator_group,overlap.
          Overlap ERROR|RETAIN_BOTH|FINER_ONLY;FINER_ONLY removes every selected
          comparator key from finer. Trait/context are output labels,not filters.
        Known bundles require completed native markers,all output hashes,original matching
          keys and exact namespace metadata. Original adjusted values are not recomputed.
          Separate source annotation/build labels remain coordinate-free metadata.
        All inputs are strict plain UTF-8 TSV. No quoting,ID normalization or aliases.
          Additional columns remain in exact snapshots;options-carrier paths are relative
          to that file. Empty original groups remain flagged even with pseudocount fits.
        Output retains every count/fit/family/planned ID,root bracket and fit warning,
          plus all group domains/keys/contributors/dispositions/overlaps/unassigned rows.
        Limits: --max-bytes --max-rows --max-line-bytes --max-cells --max-snapshot-bytes
          --max-output-bytes --max-output-rows --max-bundle-files --max-population
          --max-support --max-support-states --max-operations --max-root-iterations
          --max-evidence --timeout. Complete capacities refuse;never truncate tests.
        Exit0 completed with review warnings;2 invalid input/model/budget or failed import,
          with partial diagnostics and Required action. No runtime installation is needed.
          If upstream analysis software is unavailable/forbidden,skip that affected upstream
          analysis and supply valid permitted results. Existing outputs are never overwritten.
        """+SCOPE;
    }
}
