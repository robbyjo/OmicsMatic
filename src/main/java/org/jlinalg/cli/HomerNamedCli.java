/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit original named-genome region annotation and complete native checks. */
final class HomerNamedCli {
    private HomerNamedCli() { }
    private static final Set<String> ALLOWED=Set.of("home","profile","out","genome","peaks","peak-format","reference-build","annotation-release","peak-unknown-strand","duplicate-ids","resource-bounds","tss-resource-anchors","feature-ties","tss-ties","missing-tss-contig","annotation-stats","timeout","maximum-input-bytes","workspace-bytes","maximum-upstream-memory-bytes","maximum-records","maximum-resource-records","maximum-candidate-rows","maximum-operations","maximum-line-characters","options-file");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try{
            var o=parse(args);FollowupSupport.required(o,"home");FollowupSupport.required(o,"profile");Path output=Path.of(FollowupSupport.required(o,"out")).toAbsolutePath().normalize();if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists; choose a NEW directory");int[] code={0};
            FollowupSupport.externalTransaction(o,"homer-original-named-annotation",(work,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method","original-HOMER5.1-v7.0-named-region-annotation");session.put("requested_options",o);
                try{
                    policies(o);var l=HomerNamedData.Limits.read(o);var runtime=HomerNamedRuntime.open(Path.of(o.get("home")),o.get("profile"));HomerNamedRuntime.memory(l.rss());var data=HomerNamedData.stage(o,work,l,runtime);boolean statistics=o.get("annotation-stats").equals("original-query-contigs");
                    int exit=HomerNamedRuntime.execute(runtime,o.get("genome"),statistics,work,l.timeout(),l.workspace(),l.rss(),session);if(exit!=0)throw new IOException("Original named annotatePeaks.pl exited "+exit+"; inspect retained original.stderr");
                    data.verify();long rows=HomerNamedData.audit(data,work,l,statistics);runtime.verify();data.verify();HomerRuntime.bound(work,l.workspace());
                    session.put("audited_original_rows",rows);session.put("complete_native_feature_TSS_and_category_population_audit","passed");session.put("basic_resource",resource(data.basic()));session.put("full_resource",resource(data.full()));
                    session.put("source_duplicate_peak_ids",data.duplicateIds());session.put("dot_peak_strands_assigned_plus",data.plusAssigned());session.put("feature_geometry_ties",data.featureTies());session.put("TSS_observable_assignment_ties",data.tssTies());session.put("peaks_on_missing_TSS_contigs",data.missingTss());
                    var t=data.tss();session.put("TSS_resource",Map.of("records",t.records().size(),"effective_records",data.index().effectiveRecords(),"inactive_duplicate_rows",data.index().inactiveDuplicateRows(),"original_ID_collapsed_groups",t.collapsedOriginalIdGroups(),"reserved_HOMER_name_rows",t.reservedNameRows(),"out_of_reference_or_missing_anchors",t.outOfReferenceAnchors(),"missing_reference_rows",t.missingReferenceRecords(),"nonpositive_promoter_window_starts",t.nonpositiveWindowStarts(),"promoter_window_end_overruns",t.windowEndOverruns()));
                    var hashes=new java.util.TreeMap<String,String>();data.hashes().forEach((p,h)->hashes.put(p.toString(),h));session.put("immutable_input_and_staging_sha256",hashes);
                    warnings.add("REFERENCE EDITION REVIEW: explicitly selected original HOMER5.1, named genome="+o.get("genome")+", resource edition=v7.0. P053 reports hg38/mm10 but does not state its HOMER/database edition. Required action: verify this chosen edition is suitable for your analysis; this is not proof of exact paper reproduction. Source archives came from official HTTP URLs with inspected content hashes, not independently authenticated signatures. All twelve resource files and the actual installation/Perl/library closure are checked before and after execution.");
                    warnings.add("ORIGINAL RESOURCE BOUNDARIES REQUIRE REVIEW: complete basic/full resource rows="+data.basic().resourceRecords()+"/"+data.full().resourceRecords()+". Missing-reference rows="+data.basic().missingReferenceRecords()+"/"+data.full().missingReferenceRecords()+"; negative starts="+data.basic().negativeStartRecords()+"/"+data.full().negativeStartRecords()+"; end overruns="+data.basic().endBeyondReferenceRecords()+"/"+data.full().endBeyondReferenceRecords()+". Source1.5-billion sentinel ends and other original irregularities remain unchanged under the explicit resource-bounds policy. TSS invalid/missing-reference anchors retained="+t.outOfReferenceAnchors()+"; promoter windows can overhang reference ends while anchors are valid. Required action: review session.json, reference-lengths.tsv, resource-tss.tsv and native candidate tables. No resource row is silently clipped, repaired or removed; input peaks must lie inside the exact pinned reference.");
                    warnings.add("FEATURE AND TSS IDENTITY REVIEW: BED6 is zero-based half-open; staged HOMER coordinates are one-based inclusive. Original feature lookup uses inclusive containment of floor((start+end)/2), ordered by source start/end/strand. Equal geometry retains all compatible names; source construction priority is recorded separately. Nearest TSS uses exact contigs and gene-oriented signed distance independent of peak strand. Raw TSS IDs remain separate from original displayed IDs, which remove everything from the first -HOMER marker. Collapsed original-ID groups="+t.collapsedOriginalIdGroups()+"; TSS assignment ties="+data.tssTies()+"; feature ties="+data.featureTies()+". Required action: inspect all native-feature-candidates.tsv and native-tss-candidates.tsv matches before assigning a unique gene or TE. Displayed feature names can also lose numeric/HOMER suffixes. A selected feature need not belong to the nearest TSS.");
                    warnings.add("INPUT POPULATION REVIEW: every input peak retains its own ASCII alias and source row in peaks.tsv; duplicate source IDs retained="+data.duplicateIds()+", dot strands assigned plus="+data.plusAssigned()+", missing-TSS contig peaks retained="+data.missingTss()+". Required action: review these explicit policies. NA means no compatible source assignment, not evidence of an absent biological feature. Input order and Unicode names remain in the ledger; no contig alias or strand is inferred without the stated policy.");
                    warnings.add("CATEGORY STATISTICS AND METADATA REVIEW: native category background bp sum every original inclusive interval on chromosomes represented by the queries, including overlaps; source rules exclude negative starts and ends beyond1.4billion. This is not an interval union or a whole-assembly universe. Requested original statistics policy="+o.get("annotation-stats")+"; exact category counts and bp are checked when requested. Original P values, ratios, gene symbols and namespace conversions remain original diagnostics and metadata. Required action: inspect native-category-populations.tsv, original-annotation-stats.tsv when requested, all19 original output columns and original.stderr; native population agreement does not certify P-value calibration or metadata namespaces. No multiple-testing adjustment is invented.");
                    warnings.add("CAPACITY AND SCOPE REVIEW: original named annotation can use several GB of RAM even for a few peaks; the adapter requires at least6GiB upstream allowance and available RAM, then monitors owned-process RSS, time and workspace. These are periodic monitors, not OS quotas; Java heap is separate. Required action: set capacities for your population, preserve warnings/partials, and rerun to a NEW directory after correcting failures. This operation uses size given and default19-column annotation; sequence/GC, motif, peakcalling, tag/coverage, RNA and GO analyses require separate capabilities.");
                    manifest.put("analysis_status","completed-original-named-annotation-with-review-warnings");
                }catch(HomerRuntime.Unavailable unavailable){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency-or-capability");session.put("error",unavailable.toString());RnaToolRuntime.quarantine(work);warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage()+". "+HomerNamedRuntime.recovery());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(work);warnings.add("ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect partial/, original.stderr and input/resource ledgers; correct the named input, resource, policy, capacity or audit cause and rerun to a NEW directory. Partial tables are not completed results.");}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);if(code[0]!=0)session.put("retained_files_location","partial/");Files.writeString(work.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(work.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status"),"exit_code",code[0]))+"\n",StandardCharsets.UTF_8);Files.writeString(work.resolve("WARNINGS.md"),"# HOMER NAMED "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+HomerNamedRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+output.resolve("WARNINGS.md"));if(code[0]==0)out.println("Original HOMER named annotation completed: "+output);return code[0];
        }catch(Exception failure){err.println("omicsmatic: "+failure.getMessage()+"\n"+HomerNamedRuntime.recovery());return 2;}
    }
    private static Map<String,Object> resource(org.jlinalg.sequence.HomerNamedFeatures.Summary s){return Map.of("records",s.resourceRecords(),"negative_start_records",s.negativeStartRecords(),"end_overrun_records",s.endBeyondReferenceRecords(),"missing_reference_records",s.missingReferenceRecords(),"unassigned_queries",s.unassignedQueries(),"geometry_tied_queries",s.geometryTiedQueries());}
    static void policies(Map<String,String> o)throws IOException {
        FollowupSupport.required(o,"peaks");choice(o,"genome",Set.of("hg38","mm10"));if(!FollowupSupport.required(o,"reference-build").equals(o.get("genome"))||!FollowupSupport.required(o,"annotation-release").equals("v7.0"))throw new HomerRuntime.Unavailable("Named reference-build must match genome hg38/mm10 and annotation-release v7.0; no version substitution");
        choice(o,"peak-format",Set.of("bed6","homer"));choice(o,"peak-unknown-strand",Set.of("error","original-plus"));choice(o,"duplicate-ids",Set.of("error","retain-ordinal"));choice(o,"resource-bounds",Set.of("error","retain-original"));choice(o,"tss-resource-anchors",Set.of("error","retain-original"));choice(o,"feature-ties",Set.of("error","retain-original"));choice(o,"tss-ties",Set.of("error","retain-original"));choice(o,"missing-tss-contig",Set.of("error","retain-NA"));choice(o,"annotation-stats",Set.of("omit","original-query-contigs"));
    }
    private static void choice(Map<String,String> o,String key,Set<String> values)throws IOException{if(!values.contains(FollowupSupport.required(o,key)))throw new IOException("Choose explicit --"+key+" from "+values);}
    private static Map<String,String> parse(String[] args)throws Exception{var direct=FollowupSupport.options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;byte[] raw=AdmixtureInputs.raw(Path.of(direct.get("options-file")),1048576);var result=new LinkedHashMap<String,String>();for(var entry:AdmixtureInputs.decode(raw).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string named annotation option");result.put(key,value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;}
    static String help(){return """
        homer-tools named-annotate --home PRIVATE_NAMED_HOME
          --profile linux-x86_64-named-annotation-5.1-v7.0 --out NEW_DIRECTORY
          --genome hg38|mm10 --reference-build MATCHING_GENOME --annotation-release v7.0
          --peaks PEAK_FILE --peak-format bed6|homer
          --peak-unknown-strand error|original-plus --duplicate-ids error|retain-ordinal
          --resource-bounds error|retain-original --tss-resource-anchors error|retain-original
          --feature-ties error|retain-original --tss-ties error|retain-original
          --missing-tss-contig error|retain-NA --annotation-stats omit|original-query-contigs
        Original5.1 annotatePeaks.pl with explicit v7.0 named resources, size given,19columns.
        Setup: tools/literature/install_homer_named.py; read homer-named-lock.json.
        Native Java streams complete basic/full annotation, nearest-TSS families and category populations.
        Official resource irregularities require explicit review/retention; input peaks stay in reference.
        Raw IDs, collapsed displayed IDs, all ties, original metadata/statistics and stdout remain separate.
        P053 does not specify the software/database edition; this choice does not prove exact reproduction.
        Missing/forbidden dependencies skip3; other failures2 retain partial/ and prominent Required action.
        Read WARNINGS.md,session.json,peaks.tsv,resource-tss.tsv,native-*-candidates.tsv,
        native-category-populations.tsv,results.tsv and original.stdout/stderr.
        Optional capacities: timeout600,maximum-input-bytes268435456,workspace-bytes1073741824,
        maximum-upstream-memory-bytes8589934592 (at least6GiB),maximum-records100000,
        maximum-resource-records20000000,maximum-candidate-rows1000000,
        maximum-operations200000000,maximum-line-characters1048576. Java heap is separate.
        UTF8 string options JSON via --options-file; direct flags override. No ignored options.
        No sequence/GC,motif,peakcalling,coverage,RNA or GO operation is admitted here.
        """;}
}
