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
import java.util.TreeMap;
import org.jlinalg.sequence.HomerEnrichment;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed original motif finding, fixed-known enrichment and de-novo discovery. */
final class HomerToolsCli {
    private HomerToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("home","profile","out","targets","background","motifs","strands","offset","alphabet","duplicate-ids","overlap-policy","statistic","normalization","threads","timeout","options-file",
            "maximum-input-bytes","maximum-decoded-characters","maximum-residues","maximum-sequences","maximum-sequence-residues","maximum-motifs","maximum-motif-rows","maximum-operations","maximum-hits","workspace-bytes","maximum-line-characters",
            "motif-length","mismatches","motifs-to-find","trials","normalization-iterations","discovery-mode","redundancy-buffer","expected-sites-per-base","maximum-background-fraction","minimum-seed-logp","stat-cache-mb","maximum-neighborhoods","maximum-upstream-memory-bytes","maximum-unique-oligos");
    private static final Set<String> DISCOVERY=Set.of("motif-length","mismatches","motifs-to-find","trials","normalization-iterations","discovery-mode","redundancy-buffer","expected-sites-per-base","maximum-background-fraction","minimum-seed-logp","stat-cache-mb","maximum-neighborhoods","maximum-upstream-memory-bytes","maximum-unique-oligos");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length>0&&args[0].equals("rna"))return HomerRnaCli.run(java.util.Arrays.copyOfRange(args,1,args.length),out,err);
        if(args.length>0&&args[0].equals("atac"))return HomerAtacCli.run(java.util.Arrays.copyOfRange(args,1,args.length),out,err);
        if(args.length>0&&args[0].equals("named-annotate"))return HomerNamedCli.run(java.util.Arrays.copyOfRange(args,1,args.length),out,err);
        if(args.length>0&&args[0].equals("genome"))return HomerGenomeCli.run(java.util.Arrays.copyOfRange(args,1,args.length),out,err);
        if(args.length>0&&args[0].equals("annotate"))return HomerAnnotationCli.run(java.util.Arrays.copyOfRange(args,1,args.length),out,err);
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try{
            if(args.length==0||!Set.of("check","find","known","denovo").contains(args[0]))throw new IOException("Choose homer-tools check,find,known,denovo,annotate,named-annotate,genome or atac");
            String mode=args[0];Map<String,String> options=parse(java.util.Arrays.copyOfRange(args,1,args.length));
            required(options,"home");required(options,"profile");Path requested=Path.of(required(options,"out")).toAbsolutePath().normalize();
            if(Files.exists(requested,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists; choose a NEW directory");
            int[] code={0};
            externalTransaction(options,"homer-original-"+mode,(work,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method","original-homer2-"+mode);session.put("requested_options",options);
                try{
                    policies(options,mode);var runtime=HomerRuntime.open(Path.of(options.get("home")),options.get("profile"));HomerRuntime.provenance(runtime,session);
                    if(mode.equals("check")){
                        runtime.verify();manifest.put("analysis_status","completed-original-runtime-check-with-review-warnings");
                    }else if(mode.equals("denovo")){
                        discovery(runtime,options,work,session,warnings);manifest.put("analysis_status","completed-original-denovo-with-review-warnings");
                    }else{
                        var limits=HomerCoreData.Limits.from(options);var data=HomerCoreData.stage(options,mode,work,limits);
                        int threads=HomerCoreData.integer(options,"threads",1,1,256),timeout=HomerCoreData.integer(options,"timeout",600,1,86400);
                        var command=new ArrayList<String>(List.of(mode,"-i","stage-targets.fa","-m","stage-motifs.motif","-strand",switch(options.get("strands")){case "forward"->"+";case "reverse"->"-";default->"both";},"-p",Integer.toString(threads)));
                        HomerEnrichment.Statistic statistic=null;
                        if(mode.equals("known")){
                            statistic=options.get("statistic").equals("hypergeometric")?HomerEnrichment.Statistic.HYPERGEOMETRIC:HomerEnrichment.Statistic.BINOMIAL;
                            command.addAll(List.of("-b","stage-background.fa","-stat",statistic==HomerEnrichment.Statistic.HYPERGEOMETRIC?"hypergeo":"binomial","-nlen","0","-cache","8"));
                        }else if(data.offset()!=null)command.addAll(List.of("-offset",data.offset().toString()));
                        session.put("targets",data.targetCount());session.put("background",data.backgroundCount());session.put("motifs",data.models().size());session.put("complete_native_sites",data.hits());session.put("native_score_operations_including_output_verification",data.operations());
                        int exit=HomerRuntime.execute(runtime,command,work,"original",timeout,limits.workspace(),session);
                        if(exit!=0)throw new IOException("Original HOMER exited "+exit+". Original stderr: "+excerpt(work.resolve("original.stderr")));
                        data.verify();data.budget().add(Files.size(work.resolve("original.stdout")));data.budget().add(Files.size(work.resolve("original.stderr")));
                        long rows=mode.equals("known")?HomerCoreAudit.known(work,data,statistic):HomerCoreAudit.find(work,data);
                        runtime.verify();data.verify();HomerRuntime.bound(work,limits.workspace());
                        session.put("audited_original_rows",rows);session.put("complete_output_audit","passed");
                        var hashes=new TreeMap<String,String>();data.hashes().forEach((p,h)->hashes.put(p.toString(),h));session.put("immutable_original_snapshot_staged_sha256",hashes);
                        session.put("source_duplicate_ids_retained",data.duplicates());session.put("target_background_overlap_ids",data.overlappingIds());session.put("target_background_overlap_normalized_dna",data.overlappingDna());session.put("canonical_lowercase_promoted",data.lower());session.put("noncanonical_masked_to_N",data.masked());
                        if(rows==0)warnings.add("NO MOTIF HITS: the complete original output and native site set are both empty. Required action: review sequence lengths, masking and supplied fixed thresholds; zero sites do not establish absent biological binding.");
                        if(data.duplicates()>0)warnings.add("DUPLICATE SOURCE IDS RETAINED: "+data.duplicates()+" repeated names remain separate records. Required action: join by role and sequence_ordinal using sequences.tsv; no original parser suffixing or record merge is inferred.");
                        if(data.overlappingIds()+data.overlappingDna()>0)warnings.add("TARGET/BACKGROUND OVERLAP RETAINED: matching IDs="+data.overlappingIds()+", matching normalized DNA="+data.overlappingDna()+" under explicit retain policy. Required action: review the populations and the test interpretation; no deduplication or claim of independent sampling is made.");
                        if(data.lower()+data.masked()>0)warnings.add("SOURCE ALPHABET REVIEW: canonical lowercase promoted="+data.lower()+", explicit noncanonical-to-N masking="+data.masked()+". Required action: inspect immutable source inputs and sequences.tsv; U becomes N, never T, and ambiguous windows are excluded.");
                        warnings.add("IDENTITY AND MODEL ALIASES: stage-targets.fa/stage-background.fa and stage-motifs.motif use unique ASCII aliases to avoid upstream parser ambiguity. Source bytes, full headers, duplicate names, motif metadata and original ordinals remain in immutable inputs/models.tsv/sequences.tsv. Supplied probability values and fixed thresholds are preserved, with the original 0.001 probability floor and natural-log scoring. Required action: review these ledgers and join through aliases/ordinals; source header text is not an inferred genomic coordinate.");
                        if(mode.equals("known"))warnings.add("STATISTICAL REVIEW REQUIRED: fixed-threshold unweighted sequence-presence counts, explicit supplied background, no sequence/GC/oligo normalization, no threshold optimization and no multiple-testing adjustment. Original binomial rates use the one-background-sequence floor and ceiling; enrichment ratios use a one-sequence pseudocount in each role. original_coarse_p is HOMER's truncated 1e exponent from logP/2.3026, not a precise P value. Required action: retain original log P and native audited log P separately, review the tested family/background/overlap, and choose any multiple-testing adjustment explicitly downstream. These results are not q or E values or wrapper-generated background analyses.");
                        else warnings.add("COORDINATE AND SCORE REVIEW REQUIRED: results use zero-based half-open sequence intervals and original plus-left/minus-right anchors with the explicit offset or midpoint. Minus matched text is forward-oriented. Scores are fixed natural-log odds, not P/q/E values or binding probabilities. Every original row is valid and unique and the total equals the complete native site count. Required action: review thresholds, strands, offsets and sequence identity before downstream joins; no genome/assembly is inferred.");
                        manifest.put("analysis_status","completed-original-"+mode+"-with-review-warnings");
                    }
                        warnings.add("UPSTREAM SCOPE AND PROVENANCE: unmodified original HOMER "+runtime.version()+" core, requested profile "+runtime.profile()+". Original stdout/stderr and runtime hashes are retained. Official HTTP archive pins identify content and are not independently authenticated signatures. Required action: inspect original.stderr and the recorded profile/library closure before reporting results. This development command implements fixed known enrichment, find and de-novo motif discovery. Supplied-GTF annotation, named annotation, genomic motifs, ATAC and RNA workflows use separate typed subcommands and admissions. No full-paper or biological-accuracy certification is implied.");
                }catch(HomerRuntime.Unavailable unavailable){
                    code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency-or-capability");session.put("error",unavailable.toString());RnaToolRuntime.quarantine(work);session.put("retained_files_location","partial/");warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage()+". "+HomerRuntime.recovery());
                }catch(Exception failure){
                    code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(work);session.put("retained_files_location","partial/");warnings.add("ANALYSIS FAILED: "+failure.getMessage()+". Required action: correct the named input, identity, population, coordinate, statistic, resource or original-output cause; inspect retained partial/ diagnostics and rerun to a NEW output directory. Partial tables are not completed results. "+HomerRuntime.recovery());
                }
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(work.resolve("session.json"),RegenieRuntime.json(session)+"\n");
                Files.writeString(work.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status"),"exit_code",code[0]))+"\n");
                Files.writeString(work.resolve("WARNINGS.md"),"# HOMER "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+HomerRuntime.recovery()+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+requested.resolve("WARNINGS.md"));if(code[0]==0)out.println("Original HOMER "+mode+" completed: "+requested);return code[0];
        }catch(Exception failure){err.println("omicsmatic: "+failure.getMessage()+"\n"+HomerRuntime.recovery());return 2;}
    }
    private static void policies(Map<String,String> o,String mode)throws IOException{
        if(mode.equals("check")){
            if(!Set.of("home","profile","out","options-file","options-sha256").containsAll(o.keySet()))throw new IOException("homer-tools check accepts runtime/profile/out and optional options-file only");return;
        }
        required(o,"targets");if(!mode.equals("denovo"))required(o,"motifs");choice(o,"strands",Set.of("forward","reverse","both"));choice(o,"alphabet",Set.of("strict-dna","mask-n"));choice(o,"duplicate-ids",Set.of("error","retain-ordinal"));
        if(!mode.equals("denovo"))for(String key:DISCOVERY)if(o.containsKey(key))throw new IOException("--"+key+" is a discovery option and is not silently ignored by "+mode);
        if(mode.equals("denovo")){
            required(o,"background");choice(o,"overlap-policy",Set.of("error","retain"));choice(o,"statistic",Set.of("hypergeometric","binomial"));
            for(String key:List.of("normalization","motif-length","mismatches","motifs-to-find","discovery-mode"))required(o,key);
            for(String key:List.of("motifs","offset","maximum-hits"))if(o.containsKey(key))throw new IOException("--"+key+" is a fixed-model option; discovery learns its own matrices and audits count envelopes instead of fixed-site enumeration");
            return;
        }
        if(mode.equals("known")){
            required(o,"background");choice(o,"overlap-policy",Set.of("error","retain"));choice(o,"statistic",Set.of("hypergeometric","binomial"));
            if(!required(o,"normalization").equals("none"))throw new HomerRuntime.Unavailable("Requested HOMER normalization is not admitted by this fixed unweighted core workflow; SKIP rather than silently use none. Select the appropriate validated wrapper/normalization workflow when available");
            if(o.containsKey("offset"))throw new IOException("Known enrichment does not use output coordinates; --offset is a find option");
        }else{
            required(o,"offset");for(String key:List.of("background","overlap-policy","statistic","normalization"))if(o.containsKey(key))throw new IOException("--"+key+" is a known-enrichment option and is not silently ignored by find");
        }
    }
    private static void discovery(HomerRuntime.Context runtime,Map<String,String> options,Path work,Map<String,Object> session,List<String> warnings)throws Exception{
        var limits=HomerCoreData.Limits.from(options,true);var plan=HomerDiscovery.Plan.read(options,limits);
        var data=HomerCoreData.stage(options,"denovo",work,limits);var prepared=HomerDiscovery.prepare(data,plan,options,work);
        int timeout=HomerCoreData.integer(options,"timeout",600,1,86400);
        session.put("discovery_plan",Map.of("width",plan.width(),"mismatches",plan.mismatches(),"motifs_to_find",plan.motifs(),"trials",plan.trials(),"normalization_order",plan.normalization(),"mode",plan.mode(),"expected_sites_per_base",plan.expectedSites(),"redundancy_buffer",plan.buffer(),"seed_logp_request",plan.seedText(),"maximum_upstream_bytes",plan.maxUpstreamBytes()));
        session.put("targets",data.targetCount());session.put("background",data.backgroundCount());session.put("final_scorer_cache_note","Original final KnownMotifs uses its own default cache setting; the discovery -cache flag is not transferred. Resident memory is monitored periodically.");
        int exit=HomerRuntime.execute(runtime,plan.command(options),work,"original",timeout,limits.workspace(),plan.maxUpstreamBytes(),session);
        if(exit!=0)throw new IOException("Original HOMER discovery exited "+exit+". Original stderr: "+excerpt(work.resolve("original.stderr")));
        data.verify();for(String name:List.of("original.stdout","original.stderr","original-oligo-enrichment.tsv","original-normalization.tsv"))if(Files.isRegularFile(work.resolve(name)))data.budget().add(Files.size(work.resolve(name)));
        long motifs=HomerDiscovery.audit(work,data,prepared,options,session,warnings);runtime.verify();data.verify();HomerRuntime.bound(work,limits.workspace());
        session.put("audited_original_rows",motifs);session.put("complete_output_audit","passed");session.put("source_duplicate_ids_retained",data.duplicates());session.put("target_background_overlap_ids",data.overlappingIds());session.put("target_background_overlap_normalized_dna",data.overlappingDna());session.put("canonical_lowercase_promoted",data.lower());session.put("noncanonical_masked_to_N",data.masked());
        var hashes=new TreeMap<String,String>();data.hashes().forEach((p,h)->hashes.put(p.toString(),h));session.put("immutable_original_snapshot_staged_sha256",hashes);
        warnings.add("DISCOVERY INPUT AND IDENTITY REVIEW: original input bytes and full headers remain in input-targets/input-background; staged unique ASCII aliases and sequences.tsv retain every source record and original ordinal. Duplicate IDs retained="+data.duplicates()+", overlapping role IDs="+data.overlappingIds()+", overlapping normalized DNA="+data.overlappingDna()+", canonical lowercase promoted="+data.lower()+", explicitly masked residues="+data.masked()+". Required action: review aliases, supplied contrasts, duplicate/overlap policies and masking; no background is generated, U becomes N rather than T, and no genomic coordinate is inferred. Native models.tsv contains no supplied models in this operation; learned-models.tsv describes the actual original discoveries.");
        warnings.add("DISCOVERY RESOURCE AND PARAMETER REVIEW: requested length, mismatches, motif count, masking/final-scoring mode and normalization are explicit; remaining resolved parameters and actual argv are recorded in session.json. Trials must be divisible by threads so the original cannot silently round the requested trial count. Native oligo/count-envelope estimates and original workspace/resident-memory monitors are bounded; periodic monitoring is not an OS-enforced disk/RSS quota. Required action: inspect source parameters and diagnostics; correct a resource failure explicitly and rerun to a NEW directory. No automatic retuning, alternate engine or version substitution occurs.");
    }
    private static void choice(Map<String,String> o,String key,Set<String> values)throws IOException{if(!values.contains(required(o,key)))throw new IOException("Choose explicit --"+key+" from "+values);}
    private static Map<String,String> parse(String[] args)throws Exception{
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;byte[] raw=AdmixtureInputs.raw(Path.of(direct.get("options-file")),1048576);
        var result=new LinkedHashMap<String,String>();for(var entry:AdmixtureInputs.decode(raw).entrySet()){
            String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown,recursive or non-string original HOMER option");result.put(key,value);
        }
        result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    private static String excerpt(Path path)throws IOException{try(var input=Files.newInputStream(path)){return new String(input.readNBytes(65536),StandardCharsets.UTF_8);}}
    static String help(){return """
        homer-tools check --home PRIVATE_HOME --profile linux-x86_64-5.1 --out NEW_DIRECTORY
        homer-tools find --home PRIVATE_HOME --profile PROFILE --out NEW_DIRECTORY
          --targets FASTA --motifs HOMER_MATRICES --strands forward|reverse|both
          --offset midpoint|INTEGER --alphabet strict-dna|mask-n
          --duplicate-ids error|retain-ordinal
        homer-tools known --home PRIVATE_HOME --profile PROFILE --out NEW_DIRECTORY
          --targets FASTA --background FASTA --motifs HOMER_MATRICES
          --strands forward|reverse|both --alphabet strict-dna|mask-n
          --duplicate-ids error|retain-ordinal --overlap-policy error|retain
          --statistic hypergeometric|binomial --normalization none
        homer-tools denovo --home PRIVATE_HOME --profile PROFILE --out NEW_DIRECTORY
          --targets FASTA --background FASTA --motif-length INTEGER
          --mismatches INTEGER --motifs-to-find INTEGER --strands forward|reverse|both
          --alphabet strict-dna|mask-n --duplicate-ids error|retain-ordinal
          --overlap-policy error|retain --statistic hypergeometric|binomial
          --normalization none|oligo1..oligo15
          --discovery-mode full-mask|quick-mask-fixed|quick-mask-optimize
        Profiles: linux-x86_64-5.1,linux-x86_64-4.11,linux-x86_64-4.11.1.
        Original unmodified Linux core; Windows users run Linux Java inside WSL.
        Implements fixed known enrichment and fixed-matrix find with native audits.
        Installation is separate and explicit: tools/literature/install_homer.py.
        Missing/forbidden/unsupported/changed dependencies skip3 with cause/recovery.
        Other failures2 quarantine partials. Dense original find is supported:
        all three original versions double the longest-sequence site allocation;
        earlier capacity skips were incorrect and are retracted.
        Exit0 means complete original output passed its native audit,with review
        warnings. Read WARNINGS.md,original.stderr and identity/model/count ledgers.
        Original coarse P strings,log P and enrichment ratio stay separate from
        native audited statistics; no q/E values,GC/oligo normalization,threshold
        optimization,background generation or multiple-testing adjustment inferred.
        Source bytes/headers/ordinals preserved; staged aliases prevent upstream
        identity parsing ambiguity. Explicit overlap retains complete populations.
        Find uses original strand anchors and forward text; dense cases retain
        every record within explicit work/hit/output resource capacities.
        Options: threads1,timeout600,maximum-input-bytes268435456,
        maximum-decoded-characters1073741824,maximum-residues10000000,
        maximum-sequences10000,maximum-sequence-residues500000 (up to999999),
        maximum-motifs10000,maximum-motif-rows100000,maximum-operations1000000000,
        maximum-hits100000,workspace-bytes536870912,maximum-line-characters1048576.
        Set positive capacity flags explicitly; review Java heap/workspace estimates.
        Raw snapshots/native audit streams have byte budgets; original workspace is
        monitored periodically,not an OS-enforced disk/RSS quota. Final status and
        manifest diagnostics add metadata outside that budget. No silent truncation.
        An ASCII-named UTF8 string-options JSON via --options-file supports Unicode
        paths; direct flags override it. No unknown/recursive/non-string options.
        Discovery learns original models and preserves source statistics beside
        conservative presence envelopes for print-time normalization/rounding.
        Original positive1.000 seed sentinels are unscored,not computed log P.
        Native oligo counts check all words and seed eligibility/coverage weights;
        final motif statistics use fresh complete unweighted populations.
        Discovery options: trials10 (must be divisible by threads),normalization-
        iterations160,redundancy-buffer auto,expected-sites-per-base auto,
        maximum-background-fraction0.5,minimum-seed-logp auto,stat-cache-mb8,
        maximum-neighborhoods1000000000,maximum-upstream-memory-bytes1073741824,
        maximum-unique-oligos1000000. Fixed-model maximum-hits,offset andmotifs
        flags are rejected in discovery; all count/precision ledgers are complete.
        The original final scoring cache does not inherit the discovery cache flag.
        Supplied-GTF annotation and genomic motifs use separate typed subcommands/profiles.
        Named-genome,ATAC and RNA operations use their separate typed subcommands/profiles below.
        Cell Ranger remains excluded. Preserve all failed attempts.
        """+HomerAnnotationCli.help()+HomerNamedCli.help()+HomerGenomeCli.help()+HomerAtacCli.help()+HomerRnaCli.help();}
}
