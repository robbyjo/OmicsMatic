/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.RelatedSampleSelection;
import org.jlinalg.genetics.RelatedSampleSelection.Pair;
import org.jlinalg.genetics.RelatedSampleSelection.TiePolicy;

/** Native phenotype-eligible maximal unrelated-set selection;no statistical runtime required. */
final class UnrelatedSelectCli {
    private UnrelatedSelectCli() { }
    private static final Set<String> ALLOWED=Set.of("samples","pairs","cutoff","pair-generation-cutoff","pair-coverage","scale","ties","max-samples","max-pairs","max-bytes","out","options-file");
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            var options=parse(args);required(options,"out");int[] exit={0};transaction(options,"native-phenotype-eligible-unrelated-selection",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("options",options);
                try {
                    int maximumSamples=integer(options,"max-samples",200000,1,1_000_000),maximumPairs=integer(options,"max-pairs",2000000,1,10_000_000),maximumBytes=integer(options,"max-bytes",536870912,1,Integer.MAX_VALUE);double cutoff=Double.parseDouble(required(options,"cutoff")),generated=Double.parseDouble(required(options,"pair-generation-cutoff"));if(!Double.isFinite(cutoff)||cutoff<0||!Double.isFinite(generated)||generated<0||generated>cutoff)throw new IllegalArgumentException("Require finite nonnegative pair-generation-cutoff <= selection cutoff;an already sparser graph cannot establish unrelatedness at a lower threshold");if(!required(options,"scale").equals("additive-grm")||!required(options,"pair-coverage").equals("complete-above-cutoff"))throw new IllegalArgumentException("Require explicit scale=additive-grm and pair-coverage=complete-above-cutoff;no kinship conversion or completeness inference");TiePolicy ties=switch(required(options,"ties")){case "input-order"->TiePolicy.INPUT_ORDER;case "lexical-id"->TiePolicy.LEXICAL_ID;default->throw new IllegalArgumentException("Require explicit ties=input-order or lexical-id");};
                    var immutable=new LinkedHashMap<Path,String>();Path samples=stage(required(options,"samples"),directory.resolve("samples.tsv"),maximumBytes,immutable),pairsFile=stage(required(options,"pairs"),directory.resolve("pairs.tsv"),maximumBytes,immutable);var ids=new ArrayList<String>();var eligible=new HashSet<String>();
                    try(var reader=Files.newBufferedReader(samples,StandardCharsets.UTF_8)){if(!"sample_id\teligible".equals(reader.readLine()))throw new IOException("Samples require exact TSV header sample_id,eligible");String line;while((line=reader.readLine())!=null){if(line.isBlank())continue;if(line.length()>65536||ids.size()>=maximumSamples)throw new IOException("Sample row/participant bound exceeded");String[] fields=line.split("\t",-1);if(fields.length!=2||!Set.of("true","false").contains(fields[1]))throw new IOException("Require sample_id plus explicit eligible true/false");ids.add(fields[0]);if(fields[1].equals("true"))eligible.add(fields[0]);}}
                    var pairs=new ArrayList<Pair>();try(var reader=Files.newBufferedReader(pairsFile,StandardCharsets.UTF_8)){if(!"sample_a\tsample_b\trelatedness".equals(reader.readLine()))throw new IOException("Pairs require exact TSV header sample_a,sample_b,relatedness");String line;while((line=reader.readLine())!=null){if(line.isBlank())continue;if(line.length()>65536||pairs.size()>=maximumPairs)throw new IOException("Pair row/record bound exceeded");String[] fields=line.split("\t",-1);if(fields.length!=3)throw new IOException("Require two participant IDs and a numeric relatedness value");pairs.add(new Pair(fields[0],fields[1],Double.parseDouble(fields[2])));}}
                    var result=RelatedSampleSelection.select(ids,eligible,pairs,cutoff,ties);try(var writer=Files.newBufferedWriter(directory.resolve("decisions.tsv"),StandardCharsets.UTF_8)){writer.write("sample_id\teligible\tinitial_degree\tstatus\texcluded_by\n");for(var row:result.decisions())writer.write(row.sampleId()+"\t"+row.eligible()+"\t"+row.initialDegree()+"\t"+row.status()+"\t"+row.excludedBy()+"\n");}
                    var retained=new HashSet<>(result.retained());try(var writer=Files.newBufferedWriter(directory.resolve("retained-samples.tsv"),StandardCharsets.UTF_8)){writer.write("sample_id\n");for(String id:ids)if(retained.contains(id))writer.write(id+"\n");}Files.write(directory.resolve("retained-visit-order.txt"),result.retained(),StandardCharsets.UTF_8);
                    for(var entry:immutable.entrySet())if(!hash(entry.getKey()).equals(entry.getValue()))throw new IOException("Source/staged unrelated-selection input changed:"+entry.getKey());if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("recipe-sha256")))throw new IOException("Options file changed during selection");
                    var counts=Map.of("participants",ids.size(),"eligible",eligible.size(),"retained",result.retained().size(),"excluded_related",eligible.size()-result.retained().size(),"ineligible",ids.size()-eligible.size(),"supplied_pairs",result.suppliedPairs(),"above_cutoff_pairs_all_samples",result.thresholdPairs(),"above_cutoff_pairs_eligible_samples",result.eligibleThresholdPairs());var proof=new LinkedHashMap<String,Object>();proof.put("status","completed-with-review-warnings");proof.put("counts",counts);proof.put("selection_cutoff",cutoff);proof.put("pair_generation_cutoff",generated);proof.put("threshold_rule","relatedness > cutoff;equal values do not exclude");proof.put("degree_rule","Apply eligibility before counting initial degrees;visit ascending initial degree and explicit ties;skip previously excluded participants;retain and exclude its neighbors");proof.put("ties",ties.toString());proof.put("invariants","No supplied above-cutoff edge joins retained participants;every excluded eligible participant has a named retained neighbor. Maximal,not maximum-cardinality.");proof.put("input_sha256",immutable.entrySet().stream().map(e->Map.of("path",e.getKey().toString(),"sha256",e.getValue())).toList());Files.writeString(directory.resolve("results.json"),RegenieRuntime.json(proof)+"\n",StandardCharsets.UTF_8);session.put("results",proof);session.put("native_only",true);manifest.put("analysis_status","completed-with-review-warnings");
                    warnings.add("PAIR COVERAGE AND COHORT REVIEW REQUIRED: selection establishes a maximal independent set only for the supplied above-cutoff graph. Complete pair coverage above the declared generation cutoff is a caller assertion,not independently recoverable from omitted rows. Required action: verify the complete source universe,additive-GRM scale,source pair-generation threshold and every phenotype/covariate eligibility flag before using the cohort. Do not substitute KING kinship values or a sparse graph generated at a higher cutoff. Repeat selection separately after complete-case filtering for each phenotype.");warnings.add("TIES AND INTERPRETATION: equal-degree ties follow the explicit "+options.get("ties")+" rule. The paper does not state its tie rule;identical retained IDs are not claimed. The greedy output is maximal,not guaranteed largest. Initial degrees are counted once after eligibility filtering. This Java command does not create a SAIGE GRM,fit a mixed model or perform association tests.");if(eligible.isEmpty())warnings.add("EMPTY ELIGIBLE COHORT: no participants were eligible and none were retained. Required action: inspect the phenotype/covariate eligibility source before downstream analysis.");
                }catch(Exception failure){exit[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage());}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("WARNINGS.md"),"# UNRELATED SELECTION "+(exit[0]==0?"WARNINGS REQUIRE REVIEW":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect decisions.tsv and the source graph/eligibility declarations;correct invalid inputs or coverage/scale/tie choices and rerun to a new writable output directory. Existing output is preserved. No external runtime is required.\n",StandardCharsets.UTF_8);
            });err.println((exit[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(exit[0]==0)out.println("Native maximal unrelated-set decisions completed:"+options.get("out"));return exit[0];
        }catch(Exception failure){err.println("jlinalg:"+failure.getMessage()+"\nRequired action: use --help,correct the input/option/output cause and rerun to a new writable output directory. No external runtime is required.");return 2;}
    }
    private static Path stage(String value,Path target,int maximumBytes,Map<Path,String> immutable)throws IOException {Path source=Path.of(value).toAbsolutePath().normalize();if(!Files.isRegularFile(source)||Files.size(source)>maximumBytes)throw new IOException("Missing/oversized selection source:"+source);String before=hash(source);Files.copy(source,target);if(!hash(target).equals(before))throw new IOException("Selection source changed while staging");immutable.put(source,before);immutable.put(target,before);return target;}
    private static Map<String,String> parse(String[] args)throws Exception {var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require UTF8 JSON string-options at most1MiB");byte[] raw=Files.readAllBytes(path);Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(value instanceof Map<?,?> map))throw new IOException("Options must be an object");var result=new LinkedHashMap<String,String>();for(var entry:map.entrySet()){String key=String.valueOf(entry.getKey());if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string options field");result.put(key,text);}result.putAll(direct);result.put("recipe-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));return result;}
    static String help(){return """
        unrelated-select --samples SAMPLES.tsv --pairs PAIRS.tsv --cutoff NUMBER
          --pair-generation-cutoff NUMBER --pair-coverage complete-above-cutoff
          --scale additive-grm --ties input-order|lexical-id --out NEW_DIRECTORY
          [--max-samples 200000] [--max-pairs 2000000] [--max-bytes 536870912]
          [--options-file ASCII_UTF8_STRING_OPTIONS.json]
        Native Java only;no SAIGE runtime or download. Samples have exact TSV
        header sample_id,eligible with explicit true/false per participant.
        Pairs have exact TSV header sample_a,sample_b,relatedness. All IDs must
        belong to the complete source universe;duplicate unordered/self pairs fail.
        Declare complete pair coverage above generation cutoff <= selection cutoff.
        Completeness and additive-GRM scale are caller assertions,not inferred.
        Apply phenotype/covariate eligibility before counting initial degrees.
        Visit ascending initial degree with explicit ties;retain a participant
        and exclude its neighbors;skip participants already excluded. Edges use
        strict relatedness>cutoff. Source order and named blockers are retained.
        This is a maximal independent set,not a largest/maximum-cardinality set.
        It does not generate a GRM,fit SAIGE or perform association tests.
        Read decisions.tsv,retained-samples.tsv,retained-visit-order.txt,results.json
        and prominent WARNINGS.md with Required action. Exit0 completes with review
        warnings;exit2 retains failed partials. Existing outputs are preserved.
        An ASCII-named UTF8 options carrier supports Unicode Windows data paths.
        """;}
}
