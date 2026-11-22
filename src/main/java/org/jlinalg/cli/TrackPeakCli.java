/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
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
import org.jlinalg.genetics.ThresholdPeaks;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native threshold calling of an explicitly supplied score track, with no inferred significance. */
final class TrackPeakCli {
    private TrackPeakCli() { }
    private static final Set<String> OPTIONS=Set.of("input","sizes","sizes-format","genome-build","missing","cutoff","min-length","max-gap","precision","out");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try{
            Map<String,String> o=options(args,OPTIONS);for(String name:List.of("input","sizes","genome-build","missing","cutoff","min-length","max-gap","precision"))required(o,name);
            if(!Set.of("zero","error").contains(o.get("missing")))throw new IllegalArgumentException("missing must explicitly be zero or error");
            if(!Set.of("float32","float64").contains(o.get("precision")))throw new IllegalArgumentException("precision must explicitly be float32 or float64");
            String format=o.getOrDefault("sizes-format","sizes");if(!Set.of("sizes","fai").contains(format))throw new IllegalArgumentException("sizes-format must be sizes or fai");
            double cutoff=number(o,"cutoff",0);long minLength=Long.parseLong(o.get("min-length")),maxGap=Long.parseLong(o.get("max-gap"));
            if(minLength<1||maxGap<0)throw new IllegalArgumentException("min-length must be positive and max-gap nonnegative");
            var precision=o.get("precision").equals("float32")?ThresholdPeaks.Precision.FLOAT32:ThresholdPeaks.Precision.FLOAT64;
            var missing=o.get("missing").equals("zero")?ThresholdPeaks.Missing.ZERO:ThresholdPeaks.Missing.ERROR;
            int[] exit={0};transaction(o,"native-track-peaks",(dir,manifest)->{
                var warnings=new ArrayList<String>();var session=new LinkedHashMap<String,Object>();
                session.put("engine","native-java");session.put("genome_build_declared",o.get("genome-build"));session.put("missing_policy",o.get("missing"));session.put("precision",o.get("precision"));
                session.put("requested_cutoff",cutoff);session.put("min_length",minLength);session.put("max_gap",maxGap);
                session.put("scope","Threshold supplied signal; inclusive cutoff/span/gap and coalesced plateau-center summit conventions. No statistical background, MACS callpeak/HMMRATAC, normalization, P-values, q-values, IDR or assembly inference.");
                try{
                    var hashes=new LinkedHashMap<Path,String>();
                    for(String name:List.of("input","sizes")){Path p=Path.of(o.get(name)).toAbsolutePath().normalize();if(!Files.isRegularFile(p))throw new IOException("Local input does not exist: "+p);hashes.put(p,hash(p));manifest.put(name+"_sha256",hash(p));}
                    var reference=GenomicSignalCli.reference(Path.of(o.get("sizes")),format);var segments=GenomicSignalCli.signals(Path.of(o.get("input")));
                    var result=ThresholdPeaks.call(reference,segments,cutoff,minLength,maxGap,precision,missing);
                    session.put("effective_cutoff",result.effectiveCutoff());session.put("rounded_segments",result.roundedSegments());session.put("zero_filled_bases",result.zeroFilledBases());session.put("peak_count",result.peaks().size());
                    if(result.zeroFilledBases()>0)warnings.add("ZERO-FILLED SIGNAL: "+result.zeroFilledBases()+" declared reference bases were unrepresented and explicitly set to zero, including absent contigs/trailing sequence. Verify track generation and the exact reference universe.");
                    if(result.roundedSegments()>0||result.effectiveCutoff()!=cutoff)warnings.add("FLOAT32 ROUNDING: "+result.roundedSegments()+" segment values were rounded; effective cutoff="+result.effectiveCutoff()+". Both values and cutoff use float32 before comparison/coalescing, matching the tested MACS track profile. Boundary inclusion can differ from float64.");
                    if(result.effectiveCutoff()<=0)warnings.add("NONPOSITIVE CUTOFF: explicit zero-filled background can qualify as peaks. Native summits use the actual maximum, including after a zero plateau; MACS zero/signed-cutoff summit quirks are not reproduced. Positive-cutoff reference validation does not establish this legacy equivalence.");
                    if(result.peaks().isEmpty())warnings.add("NO THRESHOLD PEAKS: no intervals met the chosen cutoff/span/gap rules. This does not establish biological absence; inspect units, coverage and explicit settings.");
                    var rows=new ArrayList<String[]>();var bed=new ArrayList<String[]>();int id=0;
                    for(var peak:result.peaks()){
                        String name="peak_"+(++id);rows.add(new String[]{name,peak.chromosome(),Long.toString(peak.start()),Long.toString(peak.end()),Long.toString(peak.end()-peak.start()),Long.toString(peak.summit()),Long.toString(peak.summit()-peak.start()),Double.toString(peak.height()),Long.toString(peak.qualifyingBases())});
                        bed.add(new String[]{peak.chromosome(),Long.toString(peak.start()),Long.toString(peak.end()),name});
                    }
                    for(var entry:hashes.entrySet())if(!entry.getValue().equals(hash(entry.getKey())))throw new IOException("Input changed during threshold peak analysis: "+entry.getKey());
                    table(dir.resolve("results.tsv"),List.of("peak_id","chromosome","start","end","length","summit","summit_offset","maximum_signal","qualifying_bases"),rows);
                    var contents=new StringBuilder();for(var line:bed)contents.append(String.join("\t",line)).append('\n');Files.writeString(dir.resolve("peaks.bed"),contents);
                    manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# TRACK PEAK WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect the supplied signal units, reference, missing policy, precision, cutoff, minimum span and allowed gap before interpretation. Threshold peaks do not estimate statistical significance.\n");
                }catch(Exception failure){
                    exit[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));
                    for(String name:List.of("results.tsv","peaks.bed"))if(Files.exists(dir.resolve(name))){Files.createDirectories(dir.resolve("partial"));Files.move(dir.resolve(name),dir.resolve("partial/"+name));}
                    Files.writeString(dir.resolve("WARNINGS.md"),"# TRACK PEAK ANALYSIS FAILED\n\n"+failure.getMessage()+"\n\nRequired action: correct the named score/reference/numeric contract and rerun to a new output directory. Supply exact contigs, nonoverlapping half-open segments and explicit missing/precision/threshold choices. No successful results.tsv or peaks.bed is published.\n");
                }
                session.put("warnings",warnings);Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),"{\"status\":\""+manifest.get("analysis_status")+"\"}\n");
            });
            if(exit[0]!=0)err.println("TRACK PEAK ANALYSIS FAILED: see "+Path.of(o.get("out"),"WARNINGS.md")+" for the cause and required action.");
            else{out.println("Native threshold peaks completed: "+o.get("out"));if(Files.exists(Path.of(o.get("out"),"WARNINGS.md")))err.println("TRACK PEAK WARNINGS: see "+Path.of(o.get("out"),"WARNINGS.md"));}return exit[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage());return 2;}
    }
    static String help(){return"""
        track-peaks --input SIGNAL.bedGraph --sizes REFERENCE.chrom.sizes --genome-build DECLARED_BUILD
          --missing zero|error --precision float32|float64 --cutoff NUMBER --min-length BASES
          --max-gap BASES --out NEW_DIR [--sizes-format sizes|fai]
        Native Java thresholds a supplied score track. Inclusive score >= cutoff, gaps <= max-gap,
          span >= min-length. Adjacent equal values coalesce; summit is the lower median of maximum
          plateau centers, with each center rounded down to a base. Gaps count in span but not in
          qualifying_bases. Outputs ordered results.tsv and headerless BED4 peaks.bed.
        Exact declared reference bounds and nonoverlapping zero-based half-open segments required.
          Unordered rows accepted. Missing error requires EVERY reference base represented; zero
          explicitly fills gaps, absent contigs and trailing sequence and warns. FAI supplies lengths
          only, without verifying original FASTA geometry. No chromosome alias or assembly inference.
        float32 rounds BOTH scores and cutoff before comparison/plateau merging, as in MACS3 tracks;
          rounded values/cutoff are reported. float64 retains supplied doubles. Overflow/nonfinite
          values fail. Native coordinates are 64-bit; upstream reference cases use 32-bit bounds.
        Positive-cutoff geometry/summits are checked against actual MACS3 bdgpeakcall. For zero/signed
          thresholds native summits retain the mathematical maximum rather than MACS zero-reset quirks;
          nonpositive cutoffs can include explicit zero-filled background and are warned.
        No estimated background, normalization, MACS callpeak/HMMRATAC, P/q-values, IDR, multiple
          summits or cutoff-analysis sweep. A raw score cutoff 2 is not automatically q=0.01.
        No external runtime/download is needed. Empty peaks, zero filling, precision and nonpositive
          cutoffs retain prominent console/status/manifest warnings. Failure 2 retains cause/recovery
          in WARNINGS.md and publishes no successful results.tsv/peaks.bed. Use a new output directory.
        """;}
}
