/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.BedGraphSignals;
import org.jlinalg.genetics.GenomicIntervals;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native Java length-weighted summaries and genomic-state signal enrichment. */
final class GenomicSignalCli {
    private GenomicSignalCli() { }
    private static final Set<String> OPTIONS=Set.of("method","out","input","regions","sizes","sizes-format","genome-build","missing","pseudocount");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try{
            Map<String,String> o=options(args,OPTIONS);String method=required(o,"method");
            if(!Set.of("summarize","state-enrichment").contains(method))throw new IllegalArgumentException("Unknown genomic-signal method");
            boolean enrichment=method.equals("state-enrichment");for(String key:List.of("input","regions","sizes","genome-build","missing"))required(o,key);
            if(!Set.of("zero","error").contains(o.get("missing")))throw new IllegalArgumentException("missing must explicitly be zero or error");
            if(!Set.of("sizes","fai").contains(o.getOrDefault("sizes-format","sizes")))throw new IllegalArgumentException("sizes-format must be sizes or fai");
            if(enrichment)required(o,"pseudocount");else if(o.containsKey("pseudocount"))throw new IllegalArgumentException("pseudocount applies only to state-enrichment");
            double pseudocount=enrichment?number(o,"pseudocount",0):0;if(pseudocount<0)throw new IllegalArgumentException("pseudocount must be nonnegative");
            int[] exit={0};transaction(o,"genomic-signal-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method",method);session.put("engine","native-java");session.put("genome_build_declared",o.get("genome-build"));
                session.put("scope","Length-weighted interval summaries of a supplied bedGraph signal and explicit reference universe. No peak calling, track normalization, assembly inference or differential-accessibility model.");
                try{
                    var hashes=new LinkedHashMap<Path,String>();for(String key:List.of("input","regions","sizes")){Path p=Path.of(o.get(key)).toAbsolutePath().normalize();if(!Files.isRegularFile(p))throw new IOException("Local input does not exist: "+p);hashes.put(p,hash(p));manifest.put(key+"_sha256",hash(p));}
                    var reference=reference(Path.of(o.get("sizes")),o.getOrDefault("sizes-format","sizes"));var segments=signals(Path.of(o.get("input")));var regions=regions(Path.of(o.get("regions")));
                    var track=new BedGraphSignals.Track(reference,segments);var genome=track.genomeSummary();boolean zero=o.get("missing").equals("zero");
                    session.put("signal_segments",segments.size());session.put("reference_bases",genome.bases());session.put("represented_reference_bases",genome.coveredBases());session.put("missing_policy",o.get("missing"));
                    List<String> header;var rows=new ArrayList<String[]>();long missingRegions=0;
                    if(!enrichment){
                        header=List.of("region_id","chromosome","start","end","bases","covered_bases","coverage_fraction","integral","mean_zero_filled","mean_covered");var ids=new HashSet<String>();
                        for(var region:regions){
                            if(!ids.add(region.id()))throw new IOException("summarize requires unique BED4 region IDs; use state-enrichment to aggregate repeated state IDs");
                            var s=track.summarize(region.chromosome(),region.start(),region.end());if(s.coveredBases()<s.bases()){missingRegions++;if(!zero)throw new IOException("Unrepresented signal bases in region "+region.id()+"; provide complete signal or explicitly choose --missing zero");}
                            rows.add(new String[]{region.id(),region.chromosome(),Long.toString(region.start()),Long.toString(region.end()),Long.toString(s.bases()),Long.toString(s.coveredBases()),Double.toString((double)s.coveredBases()/s.bases()),Double.toString(s.integral()),Double.toString(s.meanIncludingZeros()),format(s.meanOverCovered())});
                        }
                    }else{
                        if(!track.isNonnegative())throw new IOException("State log2 enrichment requires nonnegative signal; signed/log-ratio tracks need a different analysis");
                        if(!zero&&genome.coveredBases()!=genome.bases())throw new IOException("Genome background contains unrepresented bases; complete every declared contig or explicitly choose --missing zero");
                        double baseline=genome.meanIncludingZeros();if(!(baseline>0))throw new IOException("Genome-wide mean signal is zero; enrichment is undefined even with a pseudocount. Check the supplied signal/reference before analysis");
                        nonoverlapping(regions);var groups=new LinkedHashMap<String,State>();
                        for(var region:regions){var s=track.summarize(region.chromosome(),region.start(),region.end());if(s.coveredBases()<s.bases())missingRegions++;groups.computeIfAbsent(region.id(),ignored->new State()).add(s);}
                        header=List.of("state_id","regions","bases","covered_bases","mean_signal","genome_mean","pseudocount","log2_enrichment");int zeroStates=0;
                        for(var entry:groups.entrySet()){
                            State state=entry.getValue();double mean=state.integral()/state.bases,score;
                            if(mean==0&&pseudocount==0){score=Double.NaN;zeroStates++;}
                            else {double numerator=mean+pseudocount,denominator=baseline+pseudocount;if(!Double.isFinite(numerator)||!Double.isFinite(denominator))throw new IOException("Pseudocount plus mean exceeds finite range");score=(Math.log(numerator)-Math.log(denominator))/Math.log(2);}
                            rows.add(new String[]{entry.getKey(),Long.toString(state.regions),Long.toString(state.bases),Long.toString(state.covered),Double.toString(mean),Double.toString(baseline),Double.toString(pseudocount),format(score)});
                        }
                        session.put("genome_mean",baseline);session.put("pseudocount",pseudocount);session.put("states",groups.size());
                        if(zeroStates>0)warnings.add("ZERO-SIGNAL STATES: "+zeroStates+" state(s) have mean zero with pseudocount zero; log2 enrichment is reported as NA. Inspect signal/reference coverage; no biological absence is inferred.");
                        if(pseudocount>0)warnings.add("PSEUDOCOUNT CHANGES ENRICHMENT: "+pseudocount+" in the supplied signal's units was added to both means. This differs from the unadjusted log2(mean_state/mean_genome) formula.");
                        if(genome.coveredBases()<genome.bases())warnings.add("SPARSE GENOME BACKGROUND: "+(genome.bases()-genome.coveredBases())+" declared reference bases are unrepresented and explicitly treated as zero. Verify the exact reference universe and signal preprocessing.");
                    }
                    if(missingRegions>0)warnings.add("UNREPRESENTED SIGNAL: "+missingRegions+" query intervals include bases absent from bedGraph; --missing zero treats these bases as zeros. Coverage columns distinguish unrepresented bases from explicit zero-valued segments.");
                    if(genome.coveredBases()==0)warnings.add("EMPTY SIGNAL TRACK: no reference bases are represented. Summary zeros follow the explicit missing policy and do not establish biological absence.");
                    for(var entry:hashes.entrySet())if(!entry.getValue().equals(hash(entry.getKey())))throw new IOException("Input changed during signal analysis: "+entry.getKey());
                    table(dir.resolve("results.tsv"),header,rows);manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# GENOMIC SIGNAL WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect coverage, declared reference/build, signal units and the explicit missing/pseudocount choices before interpreting enrichment. No missing signal, assay normalization, peak reproducibility or coordinate assembly is inferred.\n");
                }catch(Exception failure){
                    exit[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));
                    if(Files.exists(dir.resolve("results.tsv"))){Files.createDirectory(dir.resolve("partial"));Files.move(dir.resolve("results.tsv"),dir.resolve("partial/results.tsv"));}
                    Files.writeString(dir.resolve("WARNINGS.md"),"# GENOMIC SIGNAL ANALYSIS FAILED\n\n"+failure.getMessage()+"\n\nRequired action: correct the named bedGraph/BED/reference or numeric contract and rerun to a new output directory. Use exact contig labels and zero-based half-open bounds; choose the actual genome universe and missing-signal policy explicitly. No successful results.tsv is published.\n");
                }
                session.put("warnings",warnings);Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),"{\"status\":\""+manifest.get("analysis_status")+"\"}\n");
            });
            if(exit[0]!=0)err.println("GENOMIC SIGNAL ANALYSIS FAILED: see "+Path.of(o.get("out"),"WARNINGS.md")+" for the cause and required action.");
            else {out.println("Native genomic signal analysis completed: "+o.get("out"));if(Files.exists(Path.of(o.get("out"),"WARNINGS.md")))err.println("GENOMIC SIGNAL WARNINGS: see "+Path.of(o.get("out"),"WARNINGS.md"));}return exit[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage());return 2;}
    }
    static Map<String,Long> reference(Path file,String format)throws IOException {
        var result=new LinkedHashMap<String,Long>();try(var in=Files.newBufferedReader(file)){String line;long row=0;while((line=in.readLine())!=null){row++;if(line.isBlank()||line.startsWith("#"))continue;String[] f=line.split("\t",-1);if(f.length!=(format.equals("sizes")?2:5))throw new IOException("Reference requires exact "+(format.equals("sizes")?"two-column chrom.sizes":"five-column FAI")+" rows at line "+row);try{long n=Long.parseLong(f[1]);if(f[0].isBlank()||n<1||result.put(f[0],n)!=null)throw new IOException("Invalid/duplicate reference entry at line "+row);}catch(NumberFormatException failure){throw new IOException("Invalid reference length at line "+row,failure);}}}return result;
    }
    static List<BedGraphSignals.Segment> signals(Path file)throws IOException {
        var result=new ArrayList<BedGraphSignals.Segment>();try(var in=Files.newBufferedReader(file)){String line;long row=0;while((line=in.readLine())!=null){row++;if(line.isBlank()||line.startsWith("#")||line.startsWith("track ")||line.startsWith("browser "))continue;String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("bedGraph requires four tab-delimited fields at line "+row);try{result.add(new BedGraphSignals.Segment(f[0],Long.parseLong(f[1]),Long.parseLong(f[2]),Double.parseDouble(f[3])));}catch(IllegalArgumentException failure){throw new IOException("Invalid bedGraph row "+row+": "+failure.getMessage(),failure);}if(result.size()>10_000_000)throw new IOException("Signal exceeds ten million segments");}}return result;
    }
    private static List<GenomicIntervals.Interval> regions(Path file)throws IOException {
        var result=new ArrayList<GenomicIntervals.Interval>();try(var in=Files.newBufferedReader(file)){String line;long row=0;while((line=in.readLine())!=null){row++;if(line.isBlank()||line.startsWith("#")||line.startsWith("track ")||line.startsWith("browser "))continue;String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Regions require four tab-delimited BED fields at line "+row);try{result.add(new GenomicIntervals.Interval(f[0],Long.parseLong(f[1]),Long.parseLong(f[2]),f[3],"."));}catch(IllegalArgumentException failure){throw new IOException("Invalid BED4 region at line "+row,failure);}if(result.size()>10_000_000)throw new IOException("Regions exceed ten million rows");}}
        if(result.isEmpty())throw new IOException("Region file is empty");return result;
    }
    private static void nonoverlapping(List<GenomicIntervals.Interval> regions)throws IOException {
        var sorted=new ArrayList<>(regions);sorted.sort(Comparator.comparing(GenomicIntervals.Interval::chromosome).thenComparingLong(GenomicIntervals.Interval::start).thenComparingLong(GenomicIntervals.Interval::end));GenomicIntervals.Interval previous=null;
        for(var region:sorted){if(previous!=null&&previous.chromosome().equals(region.chromosome())&&region.start()<previous.end())throw new IOException("State intervals overlap; supply a nonoverlapping state assignment rather than double-counting bases");previous=region;}
    }
    private static final class State {
        private long regions,bases,covered;private double sum,correction;
        void add(BedGraphSignals.Summary s){regions++;bases=Math.addExact(bases,s.bases());covered=Math.addExact(covered,s.coveredBases());double value=s.integral(),next=sum+value;correction+=Math.abs(sum)>=Math.abs(value)?(sum-next)+value:(value-next)+sum;sum=next;if(!Double.isFinite(sum+correction))throw new IllegalArgumentException("State integral exceeds finite range");}
        double integral(){return sum+correction;}
    }
    private static String format(double value){return Double.isNaN(value)?"NA":Double.toString(value);}
    static String help(){return"""
        genomic-signal --method summarize|state-enrichment --input SIGNAL.bedGraph --regions REGIONS.bed
          --sizes REFERENCE.chrom.sizes --genome-build DECLARED_BUILD --missing zero|error --out NEW_DIR
        Native Java. bedGraph: four tab-delimited chromosome/start/end/finite-value fields, zero-based
          half-open intervals. Unordered nonoverlapping segments accepted; overlaps/aliases/bounds errors
          rejected. BED4: chromosome/start/end/region-or-state ID. Comment/track/browser metadata skipped.
          Reference: headerless exact two-column chrom.sizes; --sizes-format fai accepts a declared
          five-column FAI dictionary (lengths only, not a FASTA byte-geometry verification).
        summarize: unique region IDs; reports represented bases, integral, zero-filled length-weighted
          mean and mean over represented bases (NA if none). Missing behavior is explicitly required.
        state-enrichment: repeated state IDs aggregate by base length, not by number of regions.
          State intervals must not overlap, including within a state. Requires --pseudocount NUMBER >=0.
          Explicit zero selects unadjusted log2(mean_state/mean_genome); zero-mean states become NA with
          a warning. Positive pseudocount changes the formula and is warned. Nonnegative signal and a
          positive genome mean required. Genome background uses EVERY base in the reference dictionary,
          including absent contigs; --missing error requires complete genome signal in this mode.
        A sparse track and an explicit zero are distinguished in coverage columns. No inferred assembly,
          lifted coordinates, RPM/RPGC normalization, peak calling, IDR or differential-accessibility model.
          Log ratios use a difference of logs to avoid overflowing mean ratios. Range sums retain local
          precision without subtracting enormous global prefixes. Inputs are hashed before/after use.
        Failure 2 retains actionable WARNINGS.md; successful sparse/zero/pseudocount findings remain
          prominent in console/status/manifest. No external executable or installation is needed.
        """;}
}
