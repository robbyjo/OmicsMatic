/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.ChainLiftover;
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.genetics.GenomicIntervals.Interval;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native in-JVM basic chain mapping with explicit direction, reference and unmapped contracts. */
final class CoordinateLiftCli {
    private CoordinateLiftCli() { }
    private static final Set<String> OPTIONS=Set.of("input","chain","source-sizes","target-sizes","source-sizes-format","target-sizes-format","source-build","target-build","min-match","unmapped","max-target-length","reverse-chain","reverse-min-match","roundtrip-policy","roundtrip-fraction","out");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try{
            Map<String,String> o=options(args,OPTIONS);
            for(String key:List.of("input","chain","source-sizes","target-sizes","source-build","target-build","min-match","unmapped"))required(o,key);
            for(String key:List.of("source-sizes-format","target-sizes-format"))choice(o,key,"sizes",Set.of("sizes","fai"));choice(o,"unmapped",null,Set.of("retain","error"));
            double min=fraction(o,"min-match"),reverseMin=0,roundtripFraction=0;int maximum=integer(o,"max-target-length",0,0,Integer.MAX_VALUE);
            boolean reciprocal=o.containsKey("reverse-chain");
            if(reciprocal){required(o,"reverse-min-match");required(o,"roundtrip-policy");reverseMin=fraction(o,"reverse-min-match");choice(o,"roundtrip-policy",null,Set.of("exact","overlap"));
                if(o.get("roundtrip-policy").equals("overlap")){required(o,"roundtrip-fraction");roundtripFraction=fraction(o,"roundtrip-fraction");}else if(o.containsKey("roundtrip-fraction"))throw new IllegalArgumentException("roundtrip-fraction applies only to overlap round trips");
            }else for(String key:List.of("reverse-min-match","roundtrip-policy","roundtrip-fraction"))if(o.containsKey(key))throw new IllegalArgumentException(key+" requires reverse-chain");
            final double backwardMinimum=reverseMin,overlapMinimum=roundtripFraction;int[] exit={0};transaction(o,"native-coordinate-lift",(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();var counts=new TreeMap<String,Long>();
                session.put("engine","HTSJDK basic LiftOver in Java; declared dependency 5.0.0");session.put("source_build_declared",o.get("source-build"));session.put("target_build_declared",o.get("target-build"));session.put("coordinate_system","zero-based-half-open");session.put("min_match_aligned_fraction",min);
                session.put("scope","Basic single qualifying chain result, no split/BED12 mapping, variant allele/reference harmonization, BAM/WIG/VCF conversion, orthology/pathogenicity inference or complete UCSC/CrossMap/rtracklayer/paper equivalence.");
                try{
                    var hashes=new LinkedHashMap<Path,String>();for(String key:List.of("input","chain","source-sizes","target-sizes","reverse-chain"))if(o.containsKey(key)){Path path=file(o.get(key));hashes.put(path,hash(path));manifest.put(key+"_sha256",hashes.get(path));}
                    Map<String,Long> from=GenomicSignalCli.reference(Path.of(o.get("source-sizes")),o.getOrDefault("source-sizes-format","sizes")),to=GenomicSignalCli.reference(Path.of(o.get("target-sizes")),o.getOrDefault("target-sizes-format","sizes"));
                    var mapper=new ChainLiftover(Path.of(o.get("chain")),from,to);var backward=reciprocal?new ChainLiftover(Path.of(o.get("reverse-chain")),to,from):null;
                    session.put("chains",mapper.summary().chains());session.put("alignment_blocks",mapper.summary().blocks());session.put("chain_source_contigs",mapper.summary().fromLengths().size());session.put("chain_target_contigs",mapper.summary().toLengths().size());
                    session.put("reference_policy","Every chain header contig must occur with the exact size in the corresponding full source/target dictionary; additional declared reference contigs may have no chain. Length agreement and build labels do not verify assembly identity.");
                    session.put("roundtrip_policy",reciprocal?o.get("roundtrip-policy"):"not-requested");if(reciprocal){session.put("reverse_min_match",backwardMinimum);if(o.get("roundtrip-policy").equals("overlap"))session.put("roundtrip_both_overlap_fractions",overlapMinimum);}
                    long accepted=0,rejected=0,changedLength=0,truncated=0;List<Interval> queries=read(Path.of(o.get("input")));
                    try(var results=Files.newBufferedWriter(dir.resolve("results.tsv"));var bed=Files.newBufferedWriter(dir.resolve("mapped.bed"));var diagnostics=Files.newBufferedWriter(dir.resolve("diagnostics.tsv"))){
                        row(results,new String[]{"id","source_chrom","source_start","source_end","source_strand","status","target_chrom","target_start","target_end","target_strand","target_length","span_ratio","roundtrip_status","roundtrip_chrom","roundtrip_start","roundtrip_end","roundtrip_strand","overlap_source","overlap_returned"});
                        row(diagnostics,new String[]{"id","direction","diagnostic"});
                        for(var query:queries){
                            var lifted=mapper.map(query,min);String status=lifted.status().toString();Interval target=lifted.target(),returned=null;String roundtrip=reciprocal?"not-evaluated-forward-rejected":"not-requested";double fa=Double.NaN,fb=Double.NaN;
                            truncated+=diagnostics(diagnostics,query.id(),"forward",lifted.diagnostics());
                            if(target!=null&&maximum>0&&target.length()>maximum)status="TARGET_SPAN_EXCEEDS_LIMIT";
                            if(target!=null&&status.equals("MAPPED")&&backward!=null){
                                var back=backward.map(target,backwardMinimum);returned=back.target();truncated+=diagnostics(diagnostics,query.id(),"reverse",back.diagnostics());
                                if(returned==null){roundtrip="failed-"+back.status();status="REJECTED_ROUNDTRIP";}
                                else{
                                    long overlap=GenomicIntervals.overlap(query,returned);fa=overlap/(double)query.length();fb=overlap/(double)returned.length();
                                    boolean strand=query.strand().equals(".")||query.strand().equals(returned.strand());
                                    boolean match=strand&&query.chromosome().equals(returned.chromosome())&&(o.get("roundtrip-policy").equals("exact")?query.start()==returned.start()&&query.end()==returned.end():fa>=overlapMinimum&&fb>=overlapMinimum);
                                    roundtrip=match?"passed":"failed-coordinates-or-strand";if(!match)status="REJECTED_ROUNDTRIP";
                                }
                            }
                            counts.merge(status,1L,Long::sum);
                            if(status.equals("MAPPED")){accepted++;if(target.length()!=query.length())changedLength++;bed.write(target.chromosome()+"\t"+target.start()+"\t"+target.end()+"\t"+target.id()+"\t0\t"+target.strand()+"\n");}else rejected++;
                            row(results,new String[]{query.id(),query.chromosome(),Long.toString(query.start()),Long.toString(query.end()),query.strand(),status,target==null?"NA":target.chromosome(),target==null?"NA":Long.toString(target.start()),target==null?"NA":Long.toString(target.end()),target==null?"NA":target.strand(),target==null?"NA":Long.toString(target.length()),target==null?"NA":Double.toString(target.length()/(double)query.length()),roundtrip,returned==null?"NA":returned.chromosome(),returned==null?"NA":Long.toString(returned.start()),returned==null?"NA":Long.toString(returned.end()),returned==null?"NA":returned.strand(),format(fa),format(fb)});
                        }
                    }
                    session.put("input_intervals",queries.size());session.put("accepted_intervals",accepted);session.put("rejected_intervals",rejected);session.put("status_counts",counts);session.put("changed_target_span",changedLength);session.put("truncated_diagnostic_queries",truncated);
                    for(var entry:hashes.entrySet())if(!entry.getValue().equals(hash(entry.getKey())))throw new IOException("Input changed during coordinate conversion: "+entry.getKey());
                    if(rejected>0)warnings.add("UNMAPPED/REJECTED INTERVALS: "+rejected+" of "+queries.size()+" inputs were not accepted. Inspect every status in results.tsv and upstream diagnostic text in diagnostics.tsv; no preferred alternative hit is selected. BELOW_MIN_MATCH uses the engine's exact counter. NOT_UNIQUELY_MAPPED includes ambiguous hits and all-gap chain overlaps.");
                    if(accepted==0)warnings.add("ZERO ACCEPTED INTERVALS: check exact contig names, chain direction, source/target dictionaries and minMatch/round-trip/size choices. No biological absence or cross-species lack of orthology is inferred.");
                    if(changedLength>0)warnings.add("CHANGED TARGET SPAN: "+changedLength+" accepted intervals changed span. A returned bounding interval may span target gaps; minMatch is the fraction of SOURCE bases aligned through chain blocks, not sequence identity or fraction of target span aligned.");
                    if(truncated>0)warnings.add("DIAGNOSTIC TEXT LIMIT: "+truncated+" query/direction records had more than100 candidate diagnostics; only the first100 strings were retained. Mapping decisions use the full chain engine and are not based on the diagnostic text or its rounded float fractions.");
                    if(rejected>0&&o.get("unmapped").equals("error"))throw new IOException("Explicit unmapped=error policy rejected "+rejected+" interval(s); inspect partial/results.tsv and diagnostics before rerunning");
                    manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                }catch(Exception failure){
                    exit[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));
                    for(String name:List.of("results.tsv","mapped.bed","diagnostics.tsv"))if(Files.exists(dir.resolve(name))){Files.createDirectories(dir.resolve("partial"));Files.move(dir.resolve(name),dir.resolve("partial/"+name));}
                    warnings.add("COORDINATE CONVERSION FAILED: "+failure.getMessage());
                }
                if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# COORDINATE CONVERSION "+(exit[0]==0?"WARNINGS":"FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect the named input/reference/chain contract and per-interval statuses/diagnostics. Supply the correct full source/target dictionaries, exact contigs, directional chain and explicit minMatch/unmapped/size/round-trip policies. Choose retain only after reviewing unacceptable mappings; never silently discard them. Native Java needs no external executable or automatic download. Missing local chain data must be supplied from a permitted source before rerunning to a new output directory. Failure publishes no successful results.tsv or mapped.bed; retained partial files are diagnostic.\n");
                session.put("warnings",warnings);Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),"{\"status\":\""+manifest.get("analysis_status")+"\"}\n");
            });
            if(exit[0]!=0)err.println("COORDINATE CONVERSION FAILED: see "+Path.of(o.get("out"),"WARNINGS.md")+" for cause and required action.");else{out.println("Native coordinate conversion completed: "+o.get("out"));if(Files.exists(Path.of(o.get("out"),"WARNINGS.md")))err.println("COORDINATE CONVERSION WARNINGS: see "+Path.of(o.get("out"),"WARNINGS.md"));}return exit[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage());return 2;}
    }
    private static long diagnostics(java.io.Writer writer,String id,String direction,List<String> values)throws IOException {for(String text:values.stream().limit(100).toList())row(writer,new String[]{id,direction,text});return values.size()>100?1:0;}
    private static List<Interval> read(Path path)throws IOException {
        var result=new ArrayList<Interval>();var ids=new HashSet<String>();try(var reader=Files.newBufferedReader(path)){
            String first=reader.readLine();if(first==null)throw new IOException("Empty interval table");String[] header=first.split("\t",-1);
            if(!List.of("chrom\tstart\tend\tid","chrom\tstart\tend\tid\tstrand").contains(first))throw new IOException("Require exact TSV header chrom start end id [strand], tab-delimited in that order");
            String line;long row=1;while((line=reader.readLine())!=null){row++;String[] f=line.split("\t",-1);if(f.length!=header.length)throw new IOException("Interval row width differs from header at line "+row);
                try{var interval=new Interval(f[0],Long.parseLong(f[1]),Long.parseLong(f[2]),f[3],f.length==5?f[4]:".");if(interval.id().chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||!ids.add(interval.id()))throw new IllegalArgumentException("IDs require unique nonempty tokens without whitespace/control characters");result.add(interval);}
                catch(IllegalArgumentException failure){throw new IOException("Invalid interval at line "+row+": "+failure.getMessage(),failure);}if(result.size()>1_000_000)throw new IOException("At most one million input intervals");
            }
        }if(result.isEmpty())throw new IOException("Interval table has no data rows");return result;
    }
    private static Path file(String value)throws IOException {Path p=Path.of(value).toAbsolutePath().normalize();if(!Files.isRegularFile(p))throw new IOException("Missing local input/reference/chain: "+p);return p;}
    private static String choice(Map<String,String> o,String key,String fallback,Set<String> values){String value=o.getOrDefault(key,fallback);if(value==null||!values.contains(value))throw new IllegalArgumentException(key+" must be one of "+values);return value;}
    private static double fraction(Map<String,String> o,String key){double value=number(o,key,0);if(value<=0||value>1)throw new IllegalArgumentException(key+" must be in (0,1]");return value;}
    private static String format(double value){return Double.isNaN(value)?"NA":Double.toString(value);}
    static String help(){return"""
        coordinate-lift --input INTERVALS.tsv --chain FORWARD.chain[.gz]
          --source-sizes FROM.chrom.sizes --target-sizes TO.chrom.sizes
          --source-build DECLARED_FROM --target-build DECLARED_TO --min-match (0,1]
          --unmapped retain|error --out NEW_DIR
          [--source-sizes-format sizes|fai --target-sizes-format sizes|fai --max-target-length 0]
        Native Java, existing HTSJDK basic LiftOver engine. Canonical TSV exact header:
          chrom start end id [strand], tab-delimited in that order. Zero-based half-open,
          nonempty intervals, unique token IDs, strand +|-|. (default .). At most1million rows.
          Signed-32-bit sequence lengths/coordinates/chain IDs; exact contigs, no aliases.
          Requires every chain header contig and exact length in full source/target dictionaries.
          Chain t is SOURCE and q is TARGET; filenames/build labels do not infer direction.
          Sizes/FAI supply lengths only; length agreement does not prove assembly identity.
        Optional reciprocal mapping: --reverse-chain REVERSE.chain --reverse-min-match (0,1]
          --roundtrip-policy exact|overlap; overlap additionally requires --roundtrip-fraction (0,1].
          Exact returns the original bounds/contig/known strand. Overlap requires the fraction
          threshold for BOTH original and returned spans plus matching contig/known strand.
          This is not matching the original peak identity among a collection of overlapping peaks.
        Only one qualifying chain is accepted; no best-score fallback or split/BED12 mappings.
          minMatch is source bases aligned through chain blocks, not direct sequence identity.
          Returned target span may include gaps; --max-target-length 0 disables the optional cap.
        results.tsv retains EVERY input/status and raw forward/round-trip coordinates; mapped.bed
          contains accepted BED6 intervals only (score0). diagnostics.tsv retains up to100 human
          upstream strings per failed query/direction; their rounded fractions never decide mapping.
          NOT_UNIQUELY_MAPPED includes ambiguous hits and all-gap overlaps. No silent row dropping.
          --unmapped error fails if any input is unmapped, size-rejected or round-trip-rejected,
          retaining tables under partial/. retain completes with prominent warnings/counts.
        No allele/indel/REF harmonization, VCF/BAM/WIG conversion, metadata/summit lifting,
          clinical pathogenicity/orthology inference, full-tool or published-pipeline equivalence.
          No external runtime or automatic downloads. Missing chain/reference/input fails2 with
          actionable WARNINGS.md; supply permitted exact local data and rerun to a new directory.
        """;}
}
