/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.HomerMotifs;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native fixed-matrix HOMER DNA scoring with complete bounded input and result ledgers. */
final class HomerScanCli {
    private HomerScanCli() { }
    private static final Set<String> ALLOWED=Set.of("motifs","fasta","out","strands","offset","alphabet","duplicate-ids",
            "maximum-input-bytes","maximum-decoded-characters","maximum-line-characters","maximum-residues",
            "maximum-sequence-residues","maximum-sequences","maximum-motifs","maximum-width","maximum-motif-rows",
            "maximum-operations","maximum-hits","workspace-bytes","options-file");
    private static final String RECOVERY="Required action: correct the named input, identity, alphabet, threshold, offset or resource cause and rerun to a NEW output directory. This native supplied-matrix scan needs only Java and no HOMER installation. Failed files under partial/ are provisional and must not be used as completed results. Original HOMER enrichment/discovery, genome annotation, peak calling, tag coverage and RNA counting are separate workflows; a motif site score is not a P, q or E value. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try{
            var o=parse(args);Path requested=Path.of(required(o,"out")).toAbsolutePath().normalize();
            if(Files.exists(requested,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output already exists; select a NEW directory");
            int[] code={0};
            externalTransaction(o,"homer-fixed-motif-native-scan",(work,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                session.put("engine","native-java");session.put("method","HOMER Motif2 fixed-matrix natural-log scanning subset");session.put("requested_options",o);
                try{
                    long inputBytes=positive(o,"maximum-input-bytes",268435456),workspace=positive(o,"workspace-bytes",536870912);
                    var budget=new Budget(workspace);
                    var originals=new LinkedHashMap<Path,String>();var snapshots=new LinkedHashMap<Path,String>();
                    long combinedInput=0;
                    for(String name:List.of("motifs","fasta","options-file")){
                        if(!o.containsKey(name)){if(!name.equals("options-file"))required(o,name);continue;}
                        Path p=Path.of(required(o,name)).toAbsolutePath().normalize();HomerFasta.regular(p,inputBytes);
                        if(name.equals("options-file")&&!hash(p).equals(o.get("options-sha256")))throw new IOException("Options changed before snapshot");
                        combinedInput=Math.addExact(combinedInput,Files.size(p));if(combinedInput>inputBytes)throw new IOException("Combined original input bytes exceed maximum-input-bytes");
                        originals.put(p,hash(p));budget.add(Files.size(p));
                        Path target=work.resolve("input-"+name);Files.copy(p,target);
                        if(!hash(target).equals(originals.get(p)))throw new IOException("Original changed during immutable snapshot");
                        snapshots.put(target,hash(target));
                    }
                    verify(originals);verify(snapshots);
                    String strand=required(o,"strands"),alphabet=required(o,"alphabet"),duplicate=required(o,"duplicate-ids");
                    if(!Set.of("forward","reverse","both").contains(strand)||!Set.of("strict-dna","mask-n").contains(alphabet)||!Set.of("error","retain-ordinal").contains(duplicate))
                        throw new IOException("Select explicit strands forward/reverse/both, alphabet strict-dna/mask-n and duplicate-ids error/retain-ordinal");
                    String offsetText=required(o,"offset");Integer offset=offsetText.equals("midpoint")?null:Integer.valueOf(offsetText);
                    long decoded=positive(o,"maximum-decoded-characters",1073741824),residues=positive(o,"maximum-residues",100000000);
                    int maxSequence=bounded(o,"maximum-sequence-residues",1000000,1,100000000),maxSequences=bounded(o,"maximum-sequences",10000,1,1000000);
                    int line=bounded(o,"maximum-line-characters",1048576,16,1048576),motifCount=bounded(o,"maximum-motifs",10000,1,100000);
                    int width=bounded(o,"maximum-width",4096,1,4096);long maxRows=positive(o,"maximum-motif-rows",1000000);
                    long maximumOperations=positive(o,"maximum-operations",1000000000),maximumHits=positive(o,"maximum-hits",1000000);
                    // Bound major retained arrays/model rows/identity keys before loading them.
                    long estimated=Math.addExact(Math.multiplyExact(12L,maxSequence),Math.addExact(Math.max(Math.multiplyExact(128L,maxRows),workspace/4),Math.multiplyExact(4096L,maxSequences)));
                    if(estimated>workspace||estimated>Runtime.getRuntime().maxMemory()/2)throw new IOException("Estimated native sequence/model/identity memory exceeds workspace-bytes or half the declared Java maximum heap; review explicit capacities/heap before increasing them");
                    session.put("estimated_native_retained_bytes",estimated);session.put("workspace_limit_bytes",workspace);
                    var models=HomerMotifs.read(work.resolve("input-motifs"),new HomerMotifs.Limits(motifCount,width,maxRows,inputBytes,decoded,line,workspace/4)).stream().map(HomerMotifs::compile).toList();
                    var scanStrand=switch(strand){case "forward"->HomerMotifs.Strands.FORWARD;case "reverse"->HomerMotifs.Strands.REVERSE;default->HomerMotifs.Strands.BOTH;};
                    var fastaLimits=new HomerFasta.Limits(inputBytes,decoded,residues,maxSequences,maxSequence,line);
                    long[] totals={0,0,0,0,0}; // work,hits,scored-sites,lowercase,masked-residues
                    try(var results=budget.writer(work.resolve("results.tsv"));var modelRows=budget.writer(work.resolve("models.tsv"));
                            var identities=budget.writer(work.resolve("sequences.tsv"));var counts=budget.writer(work.resolve("scan-counts.tsv"))){
                        results.write("motif_ordinal\tmotif_name\tsequence_ordinal\tsequence_id\tleft0\tend_exclusive\tstrand\thomer_position\tforward_word\tnatural_log_odds\n");
                        modelRows.write("motif_ordinal\tmotif_name\tconsensus_label\twidth\tthreshold_inclusive\tsource_log_p_metadata\tfloored_probability_cells\textra_header_json\n");
                        identities.write("sequence_ordinal\tsequence_id\theader_json\tresidues\n");
                        counts.write("motif_ordinal\tsequence_ordinal\twindows\tambiguous_windows\tscored_sites\thits\tmaximum_possible_operations\n");
                        for(var model:models){
                            var m=model.motif();long floors=0;for(double[] row:m.probabilities())for(double p:row)if(p<.001)floors++;
                            modelRows.write(m.ordinal()+"\t"+m.name()+"\t"+m.consensus()+"\t"+m.width()+"\t"+m.threshold()+"\t"+(m.sourceLogP()==null?"":m.sourceLogP())+"\t"+floors+"\t"+RegenieRuntime.json(m.extraHeader())+"\n");
                        }
                        var fasta=HomerFasta.read(work.resolve("input-fasta"),fastaLimits,duplicate.equals("retain-ordinal"),seq->{
                            identities.write(seq.ordinal()+"\t"+seq.id()+"\t"+RegenieRuntime.json(seq.header())+"\t"+seq.residues().length()+"\n");
                            boolean first=true;
                            for(var model:models){
                                long operations=Math.multiplyExact(Math.multiplyExact(Math.max(0L,(long)seq.residues().length()-model.motif().width()+1),model.motif().width()),scanStrand==HomerMotifs.Strands.BOTH?2:1);
                                if(operations>maximumOperations-totals[0])throw new IOException("Complete scan exceeds the declared global operation limit");
                                var policy=new HomerMotifs.Options(scanStrand,offset,alphabet.equals("mask-n"),maxSequence,Math.max(1,maximumOperations-totals[0]),maximumHits);
                                var s=HomerMotifs.scan(seq.residues(),model,policy,hit->{
                                    if(totals[1]>=maximumHits)throw new IOException("Global motif-hit capacity exceeded; no truncated result is admitted");
                                    totals[1]++;var m=model.motif();
                                    results.write(m.ordinal()+"\t"+m.name()+"\t"+seq.ordinal()+"\t"+seq.id()+"\t"+hit.left0()+"\t"+hit.endExclusive()+"\t"+hit.strand()+"\t"+hit.reportedPosition()+"\t"+hit.forwardWord()+"\t"+hit.logOdds()+"\n");
                                });
                                totals[0]=Math.addExact(totals[0],s.maximumPossibleOperations());totals[2]=Math.addExact(totals[2],s.scoredSites());
                                if(first){totals[3]+=s.lowercasePromoted();totals[4]+=s.maskedResidues();first=false;}
                                counts.write(model.motif().ordinal()+"\t"+seq.ordinal()+"\t"+s.windows()+"\t"+s.ambiguousWindows()+"\t"+s.scoredSites()+"\t"+s.hits()+"\t"+s.maximumPossibleOperations()+"\n");
                            }
                        });
                        session.put("sequences",fasta.sequences());session.put("residues",fasta.residues());session.put("duplicate_ids_retained",fasta.duplicatedIds());
                        if(fasta.duplicatedIds()>0)warnings.add("DUPLICATE IDENTIFIERS RETAINED: "+fasta.duplicatedIds()+" source IDs repeat under explicit retain-ordinal policy. Required action: join with sequence_ordinal and inspect sequences.tsv; equal names are not merged.");
                    }
                    verify(originals);verify(snapshots);
                    if(o.containsKey("options-file")&&!hash(Path.of(o.get("options-file"))).equals(o.get("options-sha256")))throw new IOException("Options file changed during scanning");
                    session.put("motifs",models.size());session.put("hits",totals[1]);session.put("scored_sites",totals[2]);session.put("worst_case_operations",totals[0]);
                    session.put("lowercase_promoted",totals[3]);session.put("noncanonical_masked",totals[4]);
                    var hashes=new java.util.TreeMap<String,String>();originals.forEach((p,h)->hashes.put(p.toString(),h));session.put("immutable_original_sha256",hashes);
                    if(totals[1]==0)warnings.add("NO MOTIF HITS: all supplied sequences/models were scanned within the declared capacities. Required action: inspect fixed thresholds, sequence lengths, masked windows and eligibility; a zero-hit table is not evidence of absent biological binding.");
                    if(totals[3]+totals[4]>0)warnings.add("SOURCE ALPHABET PROCESSING: lowercase A/C/G/T promoted="+totals[3]+", explicitly masked noncanonical residues="+totals[4]+". Required action: inspect input-fasta and scan-counts.tsv. HOMER masking maps U to N, never T; ambiguous windows are excluded.");
                    warnings.add("SCIENTIFIC REVIEW REQUIRED: inspect immutable input-motifs/input-fasta, models.tsv, sequences.tsv and scan-counts.tsv. The native score is sum(log(max(p,0.001)/0.25)) with fixed inclusive motif thresholds; rows are never renormalized. Intervals are zero-based half-open sequence coordinates. homer_position is the plus left or minus rightmost base plus the explicit offset, or truncation(-sequence_length/2) for midpoint; minus forward_word remains in forward orientation. No genome/build/header coordinate is inferred. Required action: verify matrix provenance, input cohorts, thresholds, strands and coordinate interpretation before downstream analysis. Scores are not motif P/q/E values, enrichment, discovery, footprinting or a full HOMER/paper workflow.");
                    manifest.put("analysis_status","completed-native-fixed-motif-scan-with-review-warnings");
                }catch(Exception failure){
                    code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(work);session.put("retained_files_location","partial/");
                    warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". "+RECOVERY);
                }
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);
                Files.writeString(work.resolve("session.json"),RegenieRuntime.json(session)+"\n");
                Files.writeString(work.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
                Files.writeString(work.resolve("WARNINGS.md"),"# NATIVE HOMER "+(code[0]==0?"WARNINGS REQUIRE REVIEW":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+requested.resolve("WARNINGS.md"));if(code[0]==0)out.println("Native fixed-matrix HOMER scan completed: "+requested);return code[0];
        }catch(Exception failure){err.println("omicsmatic: "+failure.getMessage()+"\n"+RECOVERY);return 2;}
    }
    private static void verify(Map<Path,String> values)throws IOException{for(var e:values.entrySet()){HomerFasta.regular(e.getKey(),Long.MAX_VALUE);if(!hash(e.getKey()).equals(e.getValue()))throw new IOException("Immutable input/snapshot changed: "+e.getKey());}}
    private static long positive(Map<String,String> o,String key,long fallback){String text=o.get(key);long n=text==null?fallback:Long.parseLong(text);if(n<1)throw new IllegalArgumentException("Require positive --"+key);return n;}
    private static int bounded(Map<String,String> o,String key,int fallback,int min,int max){long n=positive(o,key,fallback);if(n<min||n>max)throw new IllegalArgumentException("Out-of-range --"+key);return (int)n;}
    private static Map<String,String> parse(String[] args)throws Exception{
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;
        Path p=Path.of(direct.get("options-file"));byte[] raw=AdmixtureInputs.raw(p,1048576);var result=new LinkedHashMap<String,String>();
        for(var e:AdmixtureInputs.decode(raw).entrySet()){
            String key=e.getKey().toString();if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String v))throw new IOException("Unknown, recursive or non-string HOMER native option");
            result.put(key,v);
        }
        result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    private static final class Budget{
        final long maximum;long bytes;Budget(long maximum){this.maximum=maximum;}
        void add(long n)throws IOException{bytes=Math.addExact(bytes,n);if(bytes>maximum)throw new IOException("HOMER native input/audit workspace byte limit exceeded");}
        BufferedWriter writer(Path path)throws IOException{
            OutputStream raw=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW);
            return new BufferedWriter(new OutputStreamWriter(new java.io.FilterOutputStream(raw){
                @Override public void write(int b)throws IOException{add(1);out.write(b);}
                @Override public void write(byte[] data,int start,int length)throws IOException{add(length);out.write(data,start,length);}
            },StandardCharsets.UTF_8));
        }
    }
    static String help(){return """
        homer-scan --motifs HOMER_MOTIFS --fasta DNA_FASTA --out NEW_DIRECTORY
          --strands forward|reverse|both --offset midpoint|INTEGER
          --alphabet strict-dna|mask-n --duplicate-ids error|retain-ordinal
          [--options-file ASCII_UTF8_STRING_OPTIONS.json]
        Native Java fixed supplied HOMER probability-matrix scanning. Natural-log
        scores use the original0.001 probability floor and uniform0.25 denominator,
        without row normalization. Inclusive header thresholds; no P/q/E values.
        Results retain sequence ordinals,zero-based half-open intervals,plus-left/
        minus-right reported anchors and forward-oriented matched text. Midpoint
        subtracts truncation(sequence_length/2). U is masked to N only with mask-n.
        Limits: maximum-input-bytes268435456,maximum-decoded-characters1073741824,
        maximum-line-characters1048576,maximum-residues100000000,
        maximum-sequence-residues1000000,maximum-sequences10000,
        maximum-motifs10000,maximum-width4096,maximum-motif-rows1000000,
        maximum-operations1000000000,maximum-hits1000000,workspace-bytes536870912.
        Set these positive flags explicitly when changing resource capacities.
        Work/hits are global; input/audit workspace and estimated native retained
        memory are bounded separately. Final status/manifest diagnostics add
        metadata outside the input/audit workspace check. No automatic truncation,
        threshold fitting,background or genome inference. No installation needed.
        Exit0 complete with review warnings;exit2 failure with quarantined partials.
        Read WARNINGS.md and Required action;rerun to NEW paths after correcting
        the cause. Original enrichment/discovery/annotation/peak/RNA workflows
        remain separate. Cell Ranger excluded. Direct flags override UTF8 options.
        """;}
}

