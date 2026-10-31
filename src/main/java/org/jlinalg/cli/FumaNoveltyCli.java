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
import org.jlinalg.genetics.FumaLocusNovelty;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native supplied-reference lead novelty review with explicit scientific choices. */
final class FumaNoveltyCli {
    private FumaNoveltyCli() { }
    private static final Set<String> ALLOWED=Set.of("method","novelty-options-file","build","known-build","catalog-id","analysis-id","leads","known","coverage","radius","boundary","trait-policy","max-bytes","max-rows","max-line-bytes","max-cells","max-snapshot-bytes","max-output-bytes","max-output-rows","max-matches","max-operations","timeout","out");
    private static final Set<String> PATHS=Set.of("leads","known","coverage","out");
    private static final List<String> REQUIRED=List.of("build","known-build","catalog-id","analysis-id","leads","known","coverage","radius","boundary","trait-policy","out");
    private static final String SCOPE="SUPPLIED-REFERENCE LOCUS NOVELTY REVIEW. Java compares lead coordinates with declared known association rows; no catalog acquisition, trait synonym matching, allele harmonization, LD locus discovery or historical FUMA defaults are inferred. A no-match result is reference-relative and requires declared coverage of every queried coordinate. Required action: review exact build/chromosome/trait identities, catalog scope/release, coverage assertions, radius and endpoint choices, and every source row and known match. Coverage is an investigator assertion, not independently verified literature completeness. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        if(Arrays.asList(args).contains("--help")){stdout.println(help());return 0;}
        try {
            var options=parse(args);if(options.containsKey("method")&&!options.get("method").equals("locus-novelty"))throw new IOException("--method must be locus-novelty");for(String key:REQUIRED)required(options,key);int[] code={0};
            externalTransaction(options,"fuma-locus-novelty",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();warnings.add(SCOPE);session.put("external_runtime_required",false);session.put("paper_rule_context","P123 doi:10.1038/s41588-026-02741-5;reported radius250kb;catalog/trait filter/exact endpoint inequality unstated");
                FumaNoveltyInputs input=null;String phase="options and input preparation";
                try {
                    for(String key:List.of("build","known-build","catalog-id","analysis-id")){String value=options.get(key);if(value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require bounded printable novelty identity: "+key);session.put(key,value);}
                    for(String key:List.of("build","known-build"))if(!options.get(key).matches("[A-Za-z0-9_.-]{1,80}"))throw new IOException("Declare explicit simple novelty build identities; no inference or liftover");
                    int radius=integer(options,"radius",-1,1,Integer.MAX_VALUE);String boundary=AdmixtureInputs.choice(options,"boundary",Set.of("inclusive","strict")),trait=AdmixtureInputs.choice(options,"trait-policy",Set.of("exact","all-known"));
                    session.put("radius_bp",radius);session.put("boundary",boundary);session.put("trait_policy",trait);session.put("reference_coverage_supplied",!options.get("coverage").equals("none"));
                    long started=System.nanoTime(),timeout=integer(options,"timeout",300,1,86400);Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-started>timeout*1000000000L)throw new IllegalStateException("Novelty interrupted or --timeout exceeded; review full populations/limits and rerun to a NEW output");};
                    var limits=new FumaNoveltyInputs.Limits(integer(options,"max-bytes",268435456,1,1073741824),integer(options,"max-rows",1000000,1,10000000),integer(options,"max-line-bytes",1048576,64,16777216),whole(options,"max-cells",50000000,500000000),whole(options,"max-snapshot-bytes",1073741824,1099511627776L),whole(options,"max-output-bytes",1073741824,1099511627776L),whole(options,"max-output-rows",5000000,100000000));input=new FumaNoveltyInputs(directory,limits,check);
                    if(options.containsKey("novelty-options-file")){Path snapshot=input.capture(Path.of(options.get("novelty-options-file")),"options-carrier",1048576);if(!hash(snapshot).equals(options.get("options-sha256")))throw new IOException("Novelty options carrier changed after parsing");}
                    var data=input.prepare(options);input.verify();phase="complete lead-to-known comparisons and reference coverage";
                    var result=FumaLocusNovelty.compare(data.leads(),new FumaLocusNovelty.Reference(options.get("catalog-id"),options.get("known-build"),data.known(),data.coverage()),new FumaLocusNovelty.Options(options.get("build"),radius,boundary.equals("inclusive")?FumaLocusNovelty.Boundary.INCLUSIVE:FumaLocusNovelty.Boundary.STRICT,trait.equals("exact")?FumaLocusNovelty.TraitPolicy.EXACT:FumaLocusNovelty.TraitPolicy.ALL_KNOWN,new FumaLocusNovelty.Limits(limits.rows(),integer(options,"max-matches",2000000,0,10000000),whole(options,"max-operations",100000000,10000000000L))),check);
                    phase="complete novelty evidence publication";var output=input.output;
                    output.table(directory.resolve("locus-novelty.tsv"),List.of("lead_row","locus","trait","chromosome","position","window_start","window_end","reference_window_covered","known_matches","nearest_distance_bp","status"),result.summaries().size(),i->{var s=result.summaries().get(i);return new String[]{""+s.leadRow(),s.lead().locus(),s.lead().trait(),s.lead().chromosome(),""+s.lead().position(),""+s.windowStart(),""+s.windowEnd(),""+s.referenceWindowCovered(),""+s.knownMatches(),s.nearestDistanceBp()==null?"NA":s.nearestDistanceBp().toString(),s.status().name()};},true);
                    output.table(directory.resolve("known-matches.tsv"),List.of("lead_row","known_row","locus","lead_trait","known_variant","known_trait","chromosome","lead_position","known_position","distance_bp"),result.matches().size(),i->{var m=result.matches().get(i);return new String[]{""+m.leadRow(),""+m.knownRow(),m.lead().locus(),m.lead().trait(),m.known().variant(),m.known().trait(),m.lead().chromosome(),""+m.lead().position(),""+m.known().position(),""+m.distanceBp()};},true);
                    int[] matched=new int[data.known().size()];for(var match:result.matches()){check.run();matched[match.knownRow()-1]++;}
                    output.table(directory.resolve("known-rows.tsv"),List.of("known_row","source_line","variant","trait","chromosome","position","matching_leads","disposition"),data.known().size(),i->{var k=data.known().get(i);return new String[]{""+(i+1),""+(i+2),k.variant(),k.trait(),k.chromosome(),""+k.position(),""+matched[i],matched[i]>0?"matched":"no-selected-nearby-lead"};},true);
                    output.table(directory.resolve("coverage-rows.tsv"),List.of("coverage_row","source_line","trait","chromosome","start","end","meaning"),data.coverage().size(),i->{var c=data.coverage().get(i);return new String[]{""+(i+1),""+(i+2),c.trait(),c.chromosome(),""+c.start(),""+c.end(),"investigator-declared-selected-reference-completeness"};},true);
                    input.verify();check.run();warnings.addAll(result.warnings());warnings.add("PAPER RULE CHOICES: the reviewed P123 paragraph reports +/-250kb but does not specify the catalog snapshot, trait filter or exact endpoint inequality. Both boundary and trait choices are explicit here; no setting is certified as that paper's historical implementation. Required action: reconcile the chosen reference and protocol before paper-specific interpretation. Repeated known association rows are retained and counted separately, not deduplicated into independent evidence. Raw additional TSV columns remain in exact source snapshots.");
                    if(trait.equals("all-known"))warnings.add("ALL SUPPLIED KNOWN TRAITS SELECTED: no trait filtering occurs. Required action: verify this intended comparison population; coverage rows must use literal * to assert completeness for all selected traits.");
                    session.put("lead_rows",data.leads().size());session.put("known_rows",data.known().size());session.put("coverage_rows",data.coverage().size());session.put("known_match_rows",result.matches().size());for(var status:FumaLocusNovelty.Status.values())session.put(status.name().toLowerCase(java.util.Locale.ROOT),result.summaries().stream().filter(s->s.status()==status).count());session.put("operations",result.operations());session.put("input_cells",input.cells());session.put("snapshot_bytes",input.snapshotBytes());session.put("scientific_files_and_snapshot_bytes",output.bytes());session.put("scientific_result_rows",output.rows());session.put("status","completed-with-review-warnings");manifest.put("analysis_status","completed-with-review-warnings");
                }catch(Exception failure){code[0]=2;session.put("status","failed");session.put("cause",failure.toString());session.put("failed_phase",phase);session.put("retained_files_location","partial/");if(input!=null)session.put("last_input_context",input.context());manifest.put("analysis_status","failed");manifest.put("error",failure.toString());RnaToolRuntime.quarantine(directory);Files.createDirectories(directory.resolve("partial"));warnings.add(0,"LOCUS NOVELTY FAILED during "+phase+": "+failure.getMessage()+". Required action: inspect the named input/identity/capacity cause and partial/ diagnostics; correct it and rerun to a NEW output directory. No completed novelty table is admitted.");}
                var hashes=new LinkedHashMap<String,String>();if(input!=null)input.hashes.forEach((path,digest)->hashes.put(path.toString(),digest));session.put("source_sha256",hashes);manifest.put("source_sha256",hashes);session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",session.get("status"),"exit_code",code[0]))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# "+(code[0]==0?"LOCUS NOVELTY REVIEW REQUIRED":"LOCUS NOVELTY FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: review source snapshots, every known match, unassessable row and reference coverage assertion even after exit0. Correct failures and rerun to a NEW output; existing outputs remain intact. This command needs Java only and never installs software. If required upstream locus-discovery or catalog-generation software is unavailable or forbidden, SKIP that affected upstream analysis and supply permitted valid inputs; no missing loci, associations or literature coverage are invented.\n");
            });stderr.println((code[0]==0?"LOCUS NOVELTY REVIEW REQUIRED: ":"LOCUS NOVELTY FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)stdout.println("Supplied-reference locus novelty review completed: "+options.get("out"));return code[0];
        }catch(Exception failure){stderr.println("LOCUS NOVELTY FAILED: "+failure.getMessage()+"\nRequired action: correct the named options/path/output cause and rerun to a NEW output directory. Existing outputs remain intact.\n"+SCOPE);return 2;}
    }
    private static long whole(Map<String,String> options,String key,long fallback,long maximum){String value=options.getOrDefault(key,""+fallback);if(!value.matches("[0-9]+"))throw new IllegalArgumentException("Require positive integer --"+key);long number=Long.parseLong(value);if(number<1||number>maximum)throw new IllegalArgumentException("Unsupported --"+key+" capacity");return number;}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("novelty-options-file"))return direct;Path path=Path.of(direct.get("novelty-options-file")).toAbsolutePath().normalize();byte[] raw=FumaNoveltyInputs.carrier(path);var result=new LinkedHashMap<String,String>();
        for(var entry:AdmixtureInputs.decode(raw).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("novelty-options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string novelty option: "+key);result.put(key,PATHS.contains(key)&&!(key.equals("coverage")&&value.equals("none"))?path.getParent().resolve(value).normalize().toString():value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    static String help(){return """
        fuma-tools --method locus-novelty [--novelty-options-file OPTIONS.json]
        Required: --build BUILD --known-build BUILD --catalog-id SNAPSHOT --analysis-id ID
          --leads LEADS.tsv --known KNOWN.tsv --coverage COVERAGE.tsv|none
          --radius BASES --boundary inclusive|strict --trait-policy exact|all-known --out NEW
        Leads require locus,trait,chromosome,position. Known associations require
          variant,trait,chromosome,position. Header-only known tables are allowed.
          Coverage requires trait,chromosome,start,end: one-based closed assertions of
          selected-reference completeness, not fetched or independently certified here.
          all-known coverage must use literal * for trait. coverage=none means unknown
          coverage. Additional columns remain in exact raw snapshots; no quoted TSV.
        Radius is positive; inclusive uses distance<=radius, strict uses distance<radius.
          Builds must match exactly. Traits/chromosomes use literal control-free text:
          no synonyms, chr-prefix conversion, X/23 conversion or allele/LD matching.
          Keep all known associations, including repeated positions/IDs and source rows.
        Outputs: locus-novelty.tsv,known-matches.tsv,known-rows.tsv,coverage-rows.tsv,
          resource-ledger.tsv,exact source-inputs/,session/status and WARNINGS.md.
          Empty-match windows without complete declared coverage remain UNASSESSABLE;
          covered absence means NOVEL_RELATIVE_TO_REFERENCE, not universal novelty.
        Bounds: --max-bytes 268435456 per file --max-rows 1000000 combined input rows
          --max-line-bytes 1048576 (raw excludes LF; emitted includes LF),256 input columns
          --max-cells 50000000 --max-snapshot-bytes 1073741824
          --max-output-bytes 1073741824 across scientific files and raw snapshots
          --max-output-rows 5000000 across four result ledgers --max-matches 2000000
          --max-operations 100000000 --timeout 300 seconds; cooperative checks,not OS quotas.
        Carrier values are strings; paths resolve relative to the carrier. Direct options
          override carrier fields. Use an ASCII carrier path for Unicode Windows paths.
          Exit0 requires review; input/model/capacity failures2 retain partial/ diagnostics.
          Java-only: unavailable/forbidden upstream software means skip its affected analysis;
          no installation or invented catalog/association/locus is performed here.
        """+SCOPE;}
}
