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
import org.jlinalg.genetics.FumaVariantAnnotations;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed native postprocessing of supplied SNP consequences, scores, states and counts. */
final class FumaAnnotationCli {
    private FumaAnnotationCli() { }
    private static final List<String> IDENTITIES=List.of("build","annotation-id","consequence-id","cadd-id","rdb-id","state-id","reference-id");
    private static final List<String> REQUIRED=List.of("build","annotation-id","consequence-id","cadd-id","rdb-id","state-id","reference-id","snps","genes","annotations","consequences","ld","independent","reference-counts","states","duplicates","single-state","bed-manifest","out");
    private static final Set<String> PATHS=Set.of("snps","genes","annotations","consequences","ld","independent","reference-counts","bed-manifest","out");
    private static final Set<String> ALLOWED=Set.of("method","annotation-options-file","build","annotation-id","consequence-id","cadd-id","rdb-id","state-id","reference-id","snps","genes","annotations","consequences","ld","independent","reference-counts","states","duplicates","single-state","bed-manifest","out","max-bytes","max-expanded-bytes","max-rows","max-states","max-tracks","max-line-bytes","max-cells","max-snapshot-bytes","max-output-bytes","max-evidence","max-operations","max-fisher-states","max-reference-population","timeout");
    private static final String SCOPE="SUPPLIED SNP ANNOTATION POSTPROCESSING ONLY. Independent Java uses exact supplied consequences, CADD/RDB/state resources, gene intervals, original LD rows and reference-category counts. No ANNOVAR prediction, score calculation, dbSNP/allele normalization, annotation acquisition, LD estimation or historical/full FUMA reconstruction is performed. Resource declarations do not prove assembly or population compatibility. Required action: verify exact identifiers, allele/candidate scope, genome/resource releases and reference subset membership; inspect WARNINGS.md, session.json and all full resource/consequence/nearest/LD/category/BED ledgers. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        if(Arrays.asList(args).contains("--help")){stdout.println(help());return 0;}
        try {
            var options=parse(args);if(options.containsKey("method")&&!options.get("method").equals("annotate-variants"))throw new IOException("--method must be annotate-variants");for(String key:REQUIRED)required(options,key);int[] code={0};
            externalTransaction(options,"fuma-variant-annotation",(directory,manifest)->{
                var warnings=new ArrayList<String>();warnings.add(SCOPE);var session=new LinkedHashMap<String,Object>();session.put("source_commit","bdfbf06b5dd24037ec3c7497d773fad3c2656150");session.put("source_SNPannot_R_sha256","da75a86a2b57afb3f718cb707e39be613037bf0910d080fbbd886e5e821abe56");session.put("external_runtime_required",false);session.put("bed_selection_requested",!options.get("bed-manifest").equals("none"));FumaAnnotationInputs input=null;String phase="options and input preparation";
                try {
                    for(String key:IDENTITIES){String value=options.get(key);if(value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require bounded printable resource identity: "+key);session.put(key,value);}if(!options.get("build").matches("[A-Za-z0-9_.-]{1,80}"))throw new IOException("Declare a simple explicit build identity; no build inference or liftover");
                    for(String key:List.of("states","duplicates","single-state"))session.put(key,options.get(key));long start=System.nanoTime(),seconds=integer(options,"timeout",300,1,86400);Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-start>seconds*1000000000L)throw new IllegalStateException("SNP annotation interrupted or --timeout exceeded; correct complete input populations/limits and rerun to a NEW output");};
                    var limits=new FumaAnnotationInputs.Limits(integer(options,"max-bytes",268435456,1,1073741824),integer(options,"max-expanded-bytes",268435456,1,1073741824),integer(options,"max-rows",1000000,1,10000000),integer(options,"max-states",256,1,2048),integer(options,"max-tracks",64,1,256),integer(options,"max-line-bytes",1048576,64,16777216),whole(options,"max-cells",50000000,500000000),whole(options,"max-snapshot-bytes",1073741824,1099511627776L),whole(options,"max-output-bytes",1073741824,1099511627776L));
                    input=new FumaAnnotationInputs(directory,limits,check);if(options.containsKey("annotation-options-file")){byte[] carrier=input.raw(Path.of(options.get("annotation-options-file")),"options-carrier",1048576);if(!AdmixtureInputs.digest(carrier).equals(options.get("options-sha256")))throw new IOException("Annotation options carrier changed after parsing");}
                    var prepared=input.prepare(options);input.verify();session.put("input_cells",input.inputCells());session.put("snapshot_bytes",input.snapshotBytes());session.put("candidate_rows",prepared.variants().size());session.put("gene_resource_rows",prepared.genes().size());session.put("annotation_resource_rows",prepared.annotations().size());session.put("consequence_rows",prepared.consequenceRows().size());session.put("original_LD_rows",prepared.ld().size());session.put("reference_categories",prepared.references().size());session.put("selected_BED_tracks",prepared.tracks().size());
                    phase="native supplied-resource annotation";var result=FumaVariantAnnotations.annotate(prepared.variants(),prepared.genes(),prepared.annotations(),prepared.stateColumns(),prepared.consequenceRows(),prepared.ld(),prepared.independentIds(),prepared.references(),prepared.tracks(),new FumaVariantAnnotations.Limits(limits.rows(),integer(options,"max-evidence",2000000,1,20000000),whole(options,"max-operations",100000000,10000000000L),whole(options,"max-fisher-states",5000000,100000000),whole(options,"max-reference-population",100000000,Integer.MAX_VALUE)),check);warnings.addAll(result.warnings());phase="complete result publication";
                    FumaAnnotationOutputs.write(directory,prepared,result,input.output);input.verify();check.run();session.put("nearest_gene_evidence_rows",result.nearest().size());session.put("BED_overlap_rows",result.bedHits().size());session.put("source_annot_bed_written",!result.bedHits().isEmpty());session.put("scientific_files_and_snapshot_bytes",input.output.bytes());session.put("status","completed-with-review-warnings");manifest.put("analysis_status","completed-with-review-warnings");
                    warnings.add("TYPED RESOURCE VIEW: annotations.tsv always contains first exact annotation matches in candidate order plus source-emitted BED flags. Original SNPannot.R leaves its input annot.txt untouched when no BED selection is requested. The original bytes, all extra/duplicate resource rows and all raw labels remain in source-inputs/ and resource-ledger.tsv; no input is overwritten. Required action: distinguish the prepared candidate view from the untouched resource snapshot.");
                    warnings.add("NUMERICAL SOURCE FINDING: full R4.2.3 reference protocols show a tiny complementary-category Fisher asymmetry at a 100-million reference population. Native results are symmetric and agree with a separate 100-digit exact product to below2e-15 relative in that case; the retained original complement differs by about1.83e-12 relative. Original tables are not corrected. Required action: inspect exact margins, source/numeric validation and rounded-zero/NA/Inf warnings; no whole-source bit parity or historical calibration is inferred.");
                    if(!options.get("bed-manifest").equals("none")&&prepared.tracks().isEmpty())warnings.add("EMPTY SELECTED TRACK MANIFEST: no BED resources were selected. Required action: review the manifest; absent selected columns are not evidence of no overlap in an unspecified resource.");
                }catch(Exception failure){code[0]=2;session.put("status","failed");session.put("cause",failure.toString());session.put("failed_phase",phase);if(input!=null)session.put("last_input_context",input.context());manifest.put("analysis_status","failed");RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS FAILED during "+phase+": "+failure.getMessage()+". "+(input==null?"":"Last input: "+input.context()+". ")+"Required action: inspect partial/ and the named resource, identity, population, numeric, format or capacity cause; correct it and rerun to a NEW output. Retained files are partial diagnostics, not an admitted analysis.");}
                var hashes=new LinkedHashMap<String,String>();if(input!=null)input.hashes.forEach((p,h)->hashes.put(p.toString(),h));session.put("input_sha256",hashes);manifest.put("input_sha256",hashes);session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",session.get("status"),"exit_code",code[0]))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),(code[0]==0?"# ANALYSIS WARNINGS\n\n":"# ANALYSIS FAILED\n\n")+String.join("\n\n",warnings)+"\n");
            });
            stderr.println((code[0]==0?"SNP ANNOTATION WARNINGS: ":"SNP ANNOTATION FAILED: ")+Path.of(options.get("out")).toAbsolutePath().resolve("WARNINGS.md"));if(code[0]==0)stdout.println("Supplied SNP annotation workflow completed: "+options.get("out"));return code[0];
        }catch(Exception failure){stderr.println("SNP ANNOTATION FAILED: "+failure.getMessage()+"\nRequired action: correct the named option, supplied resource or output path and rerun to a NEW directory. "+SCOPE);return 2;}
    }
    private static long whole(Map<String,String> options,String name,long fallback,long maximum)throws IOException {long value=Long.parseLong(options.getOrDefault(name,""+fallback));if(value<1||value>maximum)throw new IOException("--"+name+" outside1.."+maximum);return value;}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("annotation-options-file"))return direct;Path path=Path.of(direct.get("annotation-options-file")).toAbsolutePath().normalize();byte[] bytes=FumaAnnotationInputs.bounded(path,1048576,"options-carrier");var result=new LinkedHashMap<String,String>();
        for(var entry:AdmixtureInputs.decode(bytes).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("annotation-options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string SNP annotation option: "+key);result.put(key,PATHS.contains(key)&&!(key.equals("bed-manifest")&&value.equals("none"))?path.getParent().resolve(value).normalize().toString():value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(bytes));return result;
    }
    static String help(){return """
        fuma-tools --method annotate-variants --annotation-options-file OPTIONS.json
        Required explicit string options:
          --build BUILD --annotation-id GENE_RELEASE --consequence-id RELEASE
          --cadd-id RELEASE --rdb-id RELEASE --state-id RELEASE --reference-id RELEASE
          --snps SNP_TABLE --genes GENE_TABLE --annotations SCORE_STATE_TABLE
          --consequences SUPPLIED_ANNOVAR_ROWS --ld ORIGINAL_LD_ROWS
          --independent INDEPENDENT_TABLE --reference-counts REFERENCE_CATEGORY_COUNTS
          --states COMMA_SEPARATED_COLUMNS --duplicates error|source-first
          --single-state error|retain-matrix --bed-manifest none|TRACKS.tsv
          --out NEW_DIRECTORY
        Source TSV roles: snps needs uniqID,chr,pos,gwasP; genes needs ensembl_gene_id,
          external_gene_name,chromosome_name,start_position,end_position. Annotation header
          is exact uniqID,CADD,RDB then declared states. Consequences need uniqID,gene,annot;
          LD needs SNP1,SNP2; independent needs uniqID. Other source columns are preserved.
          Reference counts require exact annot/count/prop. Raw RDB labels are not coerced.
          Numeric NA must be exact NA; finite decimal/scientific values only. States1..15.
        BED manifest: resource_id,path,format (bed|bed-gzip); paths relative to manifest.
          BED3+ is unheadered/tab-separated, zero-based half-open; no contig conversion.
          All requested populations/duplicate interval hits and source zero-hit statuses
          are retained. Scientific tables use one-based closed gene/SNP coordinates.
        First duplicate score/gene matches require explicit source-first. Candidate IDs
          and reference categories are unique. Singleton state matrices require explicit
          retain-matrix; all-missing modes and nonpositive reference backgrounds fail.
        Default bounds: --max-bytes 268435456 --max-expanded-bytes 268435456 per file
          --max-rows 1000000 --max-states 256 --max-tracks 64 --max-line-bytes 1048576
          --max-cells 50000000 across all retained input data cells
          --max-snapshot-bytes 1073741824 --max-output-bytes 1073741824 scientific files
          --max-evidence 2000000 --max-operations 100000000 native interpretation work
          --max-fisher-states 5000000 total support --max-reference-population 100000000
          --timeout 300 seconds. Bounds are periodic checks, not heap or OS quotas.
        All options-file values are strings; paths resolve relative to that UTF-8 file.
          On Windows use an ASCII carrier path with Unicode resource/output paths inside.
        Exit0 completed with review warnings;2 failure with partial/ diagnostics. Native
          postprocessing needs Java only; upstream ANNOVAR/resource generation is separate.
          If required upstream software cannot be installed where permitted, SKIP that
          upstream analysis and supply its permitted outputs before this postprocessor.
        Inspect WARNINGS.md, analysis-status.json, session.json, resource-ledger.tsv and
          every complete consequence/nearest/LD/category/BED ledger. Original source bytes
          are retained under source-inputs/. No multiple-testing correction is inferred.
        """+SCOPE;
    }
}
