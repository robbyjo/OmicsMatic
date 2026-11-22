/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FumaKnownAssociations;
import org.jlinalg.genetics.FumaKnownAssociations.*;
import static org.jlinalg.cli.FollowupSupport.*;

/** Complete declared-family adjustment and provenance-preserving known-association review. */
final class FumaAssociationCli {
    private FumaAssociationCli() { }
    private static final Set<String> ALLOWED=Set.of("method","known-options-file","build","analysis-id","gene-namespace","trait-namespace","sources","families","members","tests","cutoff","matching","absence","max-bytes","max-rows","max-line-bytes","max-cells","max-snapshot-bytes","max-output-bytes","max-output-rows","max-evidence","max-operations","timeout","out");
    private static final Set<String> PATHS=Set.of("sources","families","members","tests","out");
    private static final List<String> REQUIRED=List.of("analysis-id","gene-namespace","trait-namespace","sources","families","members","tests","cutoff","matching","absence","out");
    private static final String SCOPE="DECLARED KNOWN-ASSOCIATION REVIEW. Java adjusts complete explicitly declared families, constructs the selected baseline union, and reviews every target. Family membership is an investigator assertion; matching mode never changes correction families. Required action: verify original test/adjustment definitions, source/model/resource releases, exact gene/trait namespaces, eligibility and every member count, including missing tests. No gene-ID conversion, trait aliasing, build conversion, allele/effect-direction replication, two-sided P conversion, Fisher enrichment or whole FUMA/paper reconstruction is inferred. Cell Ranger remains excluded.";
    private record MemberKey(String family,String trait,String context) { }
    private record MemberRow(String family,Member member,int observed) { }
    private record Contributor(int unionRow,int baselineRow) { }
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        if(Arrays.asList(args).contains("--help")){stdout.println(help());return 0;}
        try {
            var options=parse(args);if(options.containsKey("method")&&!options.get("method").equals("known-associations"))throw new IOException("--method must be known-associations");for(String key:REQUIRED)required(options,key);int[] code={0};
            externalTransaction(options,"fuma-known-associations",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();warnings.add(SCOPE);session.put("external_runtime_required",false);session.put("historical_paper_defaults_inferred",false);session.put("fisher_enrichment_performed",false);
                FumaAssociationInputs input=null;String phase="options and input preparation";
                try {
                    for(String key:List.of("analysis-id","gene-namespace","trait-namespace")){identity(options.get(key),key);session.put(key,options.get(key));}
                    if(options.containsKey("build")){identity(options.get("build"),"build");session.put("analysis_build_metadata",options.get("build"));}
                    double cutoff=number(options,"cutoff",Double.NaN);String matching=AdmixtureInputs.choice(options,"matching",Set.of("gene-trait","gene-across-traits")),absence=AdmixtureInputs.choice(options,"absence",Set.of("require-baseline-tests","selected-reference"));
                    var matchingMode=matching.equals("gene-trait")?Matching.GENE_TRAIT:Matching.GENE_ACROSS_TRAITS;
                    var absenceMode=absence.equals("require-baseline-tests")?Absence.REQUIRE_BASELINE_TESTS:Absence.SELECTED_REFERENCE;
                    session.put("inclusive_adjusted_cutoff",cutoff);session.put("matching",matching);session.put("absence",absence);
                    long started=System.nanoTime(),timeout=integer(options,"timeout",300,1,86400);Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-started>timeout*1000000000L)throw new IllegalStateException("Association interrupted or --timeout exceeded; review complete populations/limits and rerun to a NEW output");};
                    var limits=new FumaAssociationInputs.Limits(integer(options,"max-bytes",268435456,1,1073741824),integer(options,"max-rows",1000000,1,10000000),integer(options,"max-line-bytes",1048576,64,16777216),whole(options,"max-cells",50000000,500000000),whole(options,"max-snapshot-bytes",1073741824,1099511627776L),whole(options,"max-output-bytes",1073741824,1099511627776L),whole(options,"max-output-rows",5000000,100000000));
                    var modelOptions=new Options(cutoff,matchingMode,absenceMode,new Limits(limits.rows(),integer(options,"max-evidence",2000000,1,10000000),whole(options,"max-operations",100000000,10000000000L)));
                    input=new FumaAssociationInputs(directory,limits,check);
                    if(options.containsKey("known-options-file")){Path snapshot=input.capture(Path.of(options.get("known-options-file")),"options-carrier",1048576);if(!hash(snapshot).equals(options.get("options-sha256")))throw new IOException("Association options carrier changed after parsing");}
                    var data=input.prepare(options);input.verify();phase="complete family adjustment and baseline union";
                    var result=FumaKnownAssociations.review(data.sources(),data.families(),data.tests(),modelOptions,check);warnings.addAll(result.warnings());
                    if(data.analyses().stream().map(FumaAssociationInputs.Analysis::geneAnnotation).distinct().count()>1||data.analyses().stream().map(FumaAssociationInputs.Analysis::genomeBuild).distinct().count()>1)warnings.add("SOURCE ANNOTATION/BUILD DIFFERENCES: source labels are preserved separately; this operation matches declared gene IDs without coordinates. Required action: establish gene-ID comparability across source annotations/builds before interpretation. No coordinate or annotation conversion is performed.");
                    if(matchingMode==Matching.GENE_ACROSS_TRAITS)warnings.add("GENE UNION ACROSS TRAITS: baseline links may come from any supplied trait; source trait namespaces remain separate metadata. Required action: interpret this explicitly pooled matching projection separately from per-trait comparisons; adjustment families are unchanged.");
                    phase="complete association evidence publication";var output=input.output;
                    output.table(directory.resolve("analysis-sources.tsv"),List.of("source","role","model","resource","gene_namespace","trait_namespace","gene_annotation","genome_build"),data.analyses().size(),i->{var a=data.analyses().get(i);return new String[]{a.source().id(),a.source().role().name(),a.model(),a.resource(),a.geneNamespace(),a.traitNamespace(),a.geneAnnotation(),a.genomeBuild()};},true);
                    var definitions=new LinkedHashMap<String,Family>();for(var f:data.families()){check.run();definitions.put(f.id(),f);}
                    output.table(directory.resolve("family-audit.tsv"),List.of("family","source","encoding","adjustment","missing_policy","reported_zero_policy","duplicate_policy","probability_definition","expected_rows","actual_rows","available_rows","computed_adjustment_size","missing_rows","reported_zeros","duplicate_rows","selected_rows","adjusted_probability_underflows"),result.families().size(),i->{var a=result.families().get(i);var f=definitions.get(a.family());return new String[]{a.family(),a.source(),f.encoding().name(),f.adjustment().name(),f.missing().name(),f.reportedZero().name(),f.duplicates().name(),f.probabilityDefinition(),""+a.expectedRows(),""+a.actualRows(),""+a.availableRows(),value(a.adjustmentSize()),""+a.missingRows(),""+a.reportedZeros(),""+a.duplicateRows(),""+a.selectedRows(),""+a.adjustedProbabilityUnderflows()};},true);
                    var counts=new LinkedHashMap<MemberKey,Integer>();for(var t:data.tests()){check.run();counts.merge(new MemberKey(t.family(),t.trait(),t.context()),1,Integer::sum);}var members=new ArrayList<MemberRow>();for(var f:data.families())for(var m:f.members()){check.run();members.add(new MemberRow(f.id(),m,counts.getOrDefault(new MemberKey(f.id(),m.trait(),m.context()),0)));}
                    output.table(directory.resolve("family-members.tsv"),List.of("family","trait","context","expected_rows","actual_rows"),members.size(),i->{var m=members.get(i);return new String[]{m.family(),m.member().trait(),m.member().context(),""+m.member().expectedRows(),""+m.observed()};},true);
                    output.table(directory.resolve("all-tests.tsv"),List.of("input_row","tests_source_line","family","source","role","test_id","gene","trait","context","input_encoding","input_value","adjusted_p","adjusted_ln_p","selected","duplicate_of_input_row","disposition"),result.tests().size(),i->{var a=result.tests().get(i);var t=a.test();return new String[]{""+a.inputRow(),""+(a.inputRow()+1),t.family(),a.source().id(),a.source().role().name(),t.id(),t.gene(),t.trait(),t.context(),a.family().encoding().name(),value(t.value()),value(a.adjustedP()),value(a.adjustedLogP()),""+a.selected(),a.duplicateOfInputRow()==0?"NA":""+a.duplicateOfInputRow(),a.disposition()};},true);
                    output.table(directory.resolve("known-union.tsv"),List.of("union_row","gene","matched_trait","matching","baseline_tests"),result.known().size(),i->{var k=result.known().get(i);return new String[]{""+(i+1),k.key().gene(),value(k.key().trait()),matching,""+k.baselineRows().size()};},true);
                    var contributors=new ArrayList<Contributor>();for(int i=0;i<result.known().size();i++)for(int row:result.known().get(i).baselineRows()){check.run();contributors.add(new Contributor(i+1,row));}
                    output.table(directory.resolve("known-contributors.tsv"),List.of("union_row","baseline_input_row","source","family","test_id","gene","source_trait","source_context"),contributors.size(),i->{var c=contributors.get(i);var a=result.tests().get(c.baselineRow()-1);var t=a.test();return new String[]{""+c.unionRow(),""+c.baselineRow(),a.source().id(),t.family(),t.id(),t.gene(),t.trait(),t.context()};},true);
                    output.table(directory.resolve("target-decisions.tsv"),List.of("target_input_row","source","family","test_id","gene","source_trait","source_context","matched_trait","matching","status","matched_known_tests"),result.targets().size(),i->{var d=result.targets().get(i);var a=result.tests().get(d.inputRow()-1);var t=a.test();return new String[]{""+d.inputRow(),a.source().id(),t.family(),t.id(),t.gene(),t.trait(),t.context(),value(d.key().trait()),matching,d.decision().name(),value(d.matchedKnownTests())};},true);
                    output.table(directory.resolve("baseline-coverage.tsv"),List.of("target_input_row","baseline_source","matching_rows","available_rows","missing_rows","selected_rows","source_available_rows"),result.coverage().size(),i->{var c=result.coverage().get(i);return new String[]{""+c.targetRow(),c.source(),""+c.matchingRows(),""+c.availableRows(),""+c.missingRows(),""+c.selectedRows(),""+c.sourceAvailableRows()};},true);
                    output.table(directory.resolve("known-links.tsv"),List.of("target_input_row","baseline_input_row"),result.links().size(),i->{var l=result.links().get(i);return new String[]{""+l.targetRow(),""+l.baselineRow()};},true);
                    phase="final source and snapshot integrity verification";input.verify();session.put("source_rows",data.sources().size());session.put("family_rows",data.families().size());session.put("member_rows",members.size());session.put("test_rows",data.tests().size());session.put("known_union_rows",result.known().size());session.put("known_contributor_rows",contributors.size());session.put("target_rows",result.targets().size());session.put("baseline_coverage_rows",result.coverage().size());session.put("known_link_rows",result.links().size());for(var status:Decision.values())session.put(status.name().toLowerCase(java.util.Locale.ROOT),result.targets().stream().filter(t->t.decision()==status).count());session.put("operations",result.operations());session.put("input_cells",input.cells());session.put("snapshot_bytes",input.snapshotBytes());session.put("scientific_files_and_snapshot_bytes",output.bytes());session.put("scientific_result_rows",output.rows());session.put("status","completed-with-review-warnings");manifest.put("analysis_status","completed-with-review-warnings");
                }catch(Exception failure){code[0]=2;session.put("status","failed");session.put("cause",failure.toString());session.put("failed_phase",phase);session.put("retained_files_location","partial/");if(input!=null)session.put("last_input_context",input.context());manifest.put("analysis_status","failed");manifest.put("error",failure.toString());RnaToolRuntime.quarantine(directory);Files.createDirectories(directory.resolve("partial"));warnings.add(0,"KNOWN ASSOCIATIONS FAILED during "+phase+": "+failure.getMessage()+". Required action: inspect the named source/family/value/capacity cause and partial/ diagnostics; correct it and rerun to a NEW output directory. No completed association table is admitted.");}
                var hashes=new LinkedHashMap<String,String>();if(input!=null)input.hashes.forEach((path,digest)->hashes.put(path.toString(),digest));session.put("source_sha256",hashes);manifest.put("source_sha256",hashes);session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",session.get("status"),"exit_code",code[0]))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# "+(code[0]==0?"KNOWN ASSOCIATIONS REVIEW REQUIRED":"KNOWN ASSOCIATIONS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect all family/member/test ledgers, original precision, every union contributor, unassessable target and source identity even after exit0. Correct failures and rerun to a NEW output. This Java-only operation never installs software. If required upstream association software is unavailable or forbidden, SKIP that affected upstream analysis and supply permitted valid results; no missing association or source coverage is invented.\n");
            });stderr.println((code[0]==0?"KNOWN ASSOCIATIONS REVIEW REQUIRED: ":"KNOWN ASSOCIATIONS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)stdout.println("Declared known-association review completed: "+options.get("out"));return code[0];
        }catch(Exception failure){stderr.println("KNOWN ASSOCIATIONS FAILED: "+failure.getMessage()+"\nRequired action: correct the named options/path/output cause and rerun to a NEW output directory. Existing outputs remain intact.\n"+SCOPE);return 2;}
    }
    private static String value(Object value){return value==null?"NA":value instanceof Double d&&d==Double.NEGATIVE_INFINITY?"-Inf":value.toString();}
    private static void identity(String value,String key)throws IOException{if(value.isBlank()||value.length()>1024||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require bounded printable association identity: "+key);}
    private static long whole(Map<String,String> options,String key,long fallback,long maximum){String value=options.getOrDefault(key,""+fallback);if(!value.matches("[0-9]+"))throw new IllegalArgumentException("Require positive integer --"+key);long number=Long.parseLong(value);if(number<1||number>maximum)throw new IllegalArgumentException("Unsupported --"+key+" capacity");return number;}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("known-options-file"))return direct;Path path=Path.of(direct.get("known-options-file")).toAbsolutePath().normalize();byte[] raw=FumaAssociationInputs.carrier(path);var result=new LinkedHashMap<String,String>();
        for(var entry:AdmixtureInputs.decode(raw).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("known-options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string association option: "+key);result.put(key,PATHS.contains(key)?path.getParent().resolve(value).normalize().toString():value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    static String help(){return """
        fuma-tools --method known-associations [--known-options-file OPTIONS.json]
        Required: --analysis-id ID --gene-namespace ID --trait-namespace ID
          --sources SOURCES.tsv --families FAMILIES.tsv --members MEMBERS.tsv --tests TESTS.tsv
          --cutoff Q --matching gene-trait|gene-across-traits
          --absence require-baseline-tests|selected-reference --out NEW
        Sources: source,role,model,resource,gene_namespace,trait_namespace,gene_annotation,genome_build.
          Role BASELINE or TARGET; all gene namespaces must equal the declared namespace.
          gene-trait also requires all trait namespaces to match. Identical labels are
          investigator assertions of comparability, not automatic harmonization.
          Source builds/annotations remain independent labels in coordinate-free matching.
        Families: family,source,encoding,adjustment,missing,reported_zero,duplicates,probability_definition.
          Encoding P_VALUE or NATURAL_LOG. Adjustment BH,BY,HOLM,BONFERRONI,SUPPLIED_ADJUSTED.
          Missing ERROR or EXCLUDE; reported_zero ERROR or ALLOW_REPORTED;
          duplicates ERROR or RETAIN. No policy or original test direction is inferred.
        Members: family,trait,context,expected_rows (nonnegative integer, including missing).
          Every family needs explicit members, including declared zero-row members.
        Tests: family,test_id,gene,trait,context,value. All tables are strict plain UTF-8 TSV;
          additional columns remain in exact snapshots; no quoting or ID normalization.
          Values are decimal probabilities/natural logs, literal NA, or natural-log -Inf.
          Nonzero decimal underflow is refused: supply finite natural logs instead.
          SUPPLIED_ADJUSTED imports original q values unchanged; their original correction
          denominator remains unknown (computed_adjustment_size=NA).
        Full correction precedes inclusive selection and union. Exact gene-trait matching
          and gene unions across traits never change correction-family membership.
          Missing baseline evidence remains UNASSESSABLE_BASELINE even in selected-reference
          mode; one selected known baseline match is sufficient evidence of KNOWN.
          Novelty is NOVEL_RELATIVE_TO_BASELINES, never universal novelty or replication.
        Outputs: analysis-sources.tsv,family-audit.tsv,family-members.tsv,all-tests.tsv,
          known-union.tsv,known-contributors.tsv,target-decisions.tsv,baseline-coverage.tsv,
          known-links.tsv,resource-ledger.tsv,exact source-inputs/,session/status,WARNINGS.md.
        Bounds: --max-bytes 268435456 per file --max-rows 1000000 combined data rows
          --max-line-bytes 1048576 (raw excludes LF; emitted includes LF),256 input columns
          --max-cells 50000000 --max-snapshot-bytes 1073741824
          --max-output-bytes 1073741824 across scientific files and exact snapshots
          --max-output-rows 5000000 across nine result ledgers --max-evidence 2000000
          --max-operations 100000000 --timeout 300 seconds; cooperative checks,not OS quotas.
        Optional --build labels the analysis only; no source build equality or conversion
          is imposed on coordinate-free gene-ID joins. Per-source build labels are required.
        Carrier values are strings; paths resolve relative to the carrier. Direct options
          override carrier fields. Use an ASCII carrier path for Unicode Windows paths.
          Exit0 requires review; invalid input/model/capacity failures2 retain partial/ diagnostics.
          Java-only; upstream analysis with unavailable/forbidden required software must be skipped.
        """+SCOPE;}
}
