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
import org.jlinalg.sequence.HomerSamTags;

/** Explicit original RefSeq exon quantification with complete independent Java audits. */
final class HomerRnaCli {
    private HomerRnaCli() { }
    private static final Set<String> ALLOWED=Set.of("home","profile","out","sam-list","genome","reference-provenance",
            "mapping","minimum-mapq","mates","flip-strands","strand-specific-paired","relative-strand","condensation","normalization",
            "reference-irregularities","short-exon-parts","duplicate-alignments","supplementary-alignments","unpaired-alignments","improper-pairs","identical-replicates",
            "maximum-input-bytes","maximum-decoded-characters","workspace-bytes","maximum-upstream-memory-bytes","maximum-records","maximum-line-characters",
            "maximum-chromosome-tags","maximum-ledger-keys","maximum-transcripts","maximum-exon-parts","maximum-features-per-transcript",
            "maximum-exons-per-chromosome","maximum-output-rows","maximum-contributors","timeout","options-file");

    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            var o=parse(args);FollowupSupport.required(o,"home");FollowupSupport.required(o,"profile");Path output=Path.of(FollowupSupport.required(o,"out")).toAbsolutePath().normalize();
            if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists; choose a NEW directory");Path home=Path.of(o.get("home")).toAbsolutePath().normalize();
            if(output.startsWith(home)||home.startsWith(output))throw new IOException("RNA output and admitted home must be separate and nonnested");
            for(Path p=output.getParent();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("RNA output parent cannot have linked ancestors");
            int[] code={0};String[] cause={""};
            FollowupSupport.externalTransaction(o,"homer-original-rna",(work,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method","original-HOMER5.1-RefSeq-exons-with-complete-native-audits");session.put("requested_options",o);
                try {
                    policies(o);var limits=HomerRnaInputs.Limits.read(o);var runtime=HomerRnaRuntime.open(home,o.get("profile"));HomerRnaRuntime.work(work);HomerRnaRuntime.memory(limits.rss());
                    Files.copy(runtime.home().resolve(HomerRnaRuntime.MARKER),work.resolve("runtime-admission.json"));
                    if(!runtime.markerHash().equals(FollowupSupport.hash(work.resolve("runtime-admission.json"))))throw new IOException("RNA admission changed during provenance copy");
                    var data=HomerRnaInputs.stage(o,work,limits,runtime);var directories=new LinkedHashMap<String,Path>();
                    for(var replicate:data.replicates()) {
                        Path directory=work.resolve("tags-"+replicate.alias());directories.put(replicate.alias(),directory);
                        step(data,"makeTagDirectory",tagArguments(o,directory,replicate.staged()),processLabel("tags",replicate.alias()),session);
                    }
                    var tags=HomerRnaTags.audit(data,directories);HomerRnaQuantification.run(o,data,tags,session);data.verify();tags.verify();runtime.verify();
                    warnings.add("REFERENCE AND PAPER REVIEW: selected HOMER5.1, original hg38/mm10 RefSeq v7.0 package, genome="+o.get("genome")+", declared provenance="+o.get("reference-provenance")+". P040 (10.1038/s41586-026-10954-1) reports exon raw/RPKM quantification before DESeq2; it does not state assembly, RefSeq edition, strand or complete counting command. Required action: verify the selected reference and every explicit option for your study; these development fixtures do not establish exact paper reproduction. HTTP archive hashes identify content and are not authenticated signatures.");
                    warnings.add("COUNTING AND LENGTH REVIEW: every original exon part and final raw/RPKM row is audited. Source counting uses inclusive 5-prime anchors with tagAdjust0 and no tag cap, plus original boundary ownership; it does not integrate alignment blocks or fragments across exons. RPKM uses the whole original tagInfo mass and source sum(end-start), including retained empty/one-base parts. Copy-family counts sum and lengths average. Required action: inspect transcripts.tsv,exons.tsv,part-counts.tsv,boundary-families.tsv,contributors.tsv and native matrices. Source metadata representatives are checked against all contributors; they are not inferred canonical genes. Original RPKM headers can say FPKM; the recorded arithmetic is explicit.");
                    warnings.add("ALIGNMENT AND SAMPLE REVIEW: plain SAM, original matching-block stored lengths, mapping="+o.get("mapping")+", MAPQ="+o.get("minimum-mapq")+", mates="+o.get("mates")+", relative strand="+o.get("relative-strand")+", flip="+o.get("flip-strands")+", strand-specific paired="+o.get("strand-specific-paired")+". Paired-flag rows carry half weight, including when one mate is selected. Duplicate/supplementary flags are retained only under the chosen policies. The fixed150bp library estimate is a diagnostic setting and is ignored by the fixed tagAdjust0 counter. Required action: review alignments.tsv,replicates.tsv,all native tag keys and upstream preprocessing. Fractional raw counts remain fractional; review downstream DESeq2 input requirements explicitly. No rounding, alignment, deduplication, gene conversion, differential expression or Cell Ranger analysis is performed.");
                    warnings.add("CAPACITY AND RECOVERY: original stdout/stderr, immutable input bytes, all reference/runtime/host hashes and full contributor identities are retained. Java heap and per-chromosome native bounds are separate from upstream RSS. Time,RSS and workspace checks are periodic monitors, not OS quotas. Required action: read every original stderr and session.json; size capacities for the full population. Missing or forbidden dependencies SKIP3; other failures2 quarantine partials. Correct the named cause and rerun to NEW paths. Partial tables are not completed results.");
                    manifest.put("analysis_status","completed-original-RNA-with-review-warnings");
                }catch(HomerRuntime.Unavailable unavailable){code[0]=3;cause[0]=unavailable.getMessage();manifest.put("analysis_status","skipped-unavailable-dependency-or-capability");session.put("error",unavailable.toString());quarantine(work);warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage()+". "+HomerRnaRuntime.recovery());}
                catch(Exception failure){code[0]=2;cause[0]=failure.getMessage();manifest.put("analysis_status","failed");session.put("error",failure.toString());quarantine(work);warnings.add("ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect retained partial/ inputs,ledgers and original stderr; correct the named cause and rerun to a NEW output. Partial matrices are not completed results.");}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);if(code[0]!=0)session.put("retained_files_location","partial/");
                Files.writeString(work.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(work.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status"),"exit_code",code[0]))+"\n",StandardCharsets.UTF_8);
                Files.writeString(work.resolve("WARNINGS.md"),"# HOMER RNA "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+HomerRnaRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+(code[0]==0?"":cause[0]+"\n")+output.resolve("WARNINGS.md"));
            if(code[0]==0)out.println("Original HOMER RNA completed: "+output);else err.println("Required action: inspect WARNINGS.md/session.json and retained partial/; correct the stated cause and use a NEW output directory.");return code[0];
        }catch(Exception failure){err.println("ANALYSIS FAILED: "+failure.getMessage()+"\n"+HomerRnaRuntime.recovery());return 2;}
    }
    private static void quarantine(Path work)throws IOException{Files.createDirectories(work.resolve("partial"));RnaToolRuntime.quarantine(work);}
    static void step(HomerRnaInputs.Data data,String program,List<String> args,String label,Map<String,Object> session)throws Exception {
        data.check();long seconds=Math.max(1,(data.deadline()-System.nanoTime()+999999999L)/1000000000L);
        int code=HomerRnaRuntime.execute(data.runtime(),program,args,data.work(),label,(int)Math.min(data.limits().timeout(),seconds),data.limits().workspace(),data.limits().rss(),session);
        if(code!=0)throw new IOException("Original "+program+" exited"+code+"; inspect "+label+".stderr");data.check();
    }
    static String processLabel(String prefix,String alias)throws IOException {
        if(!prefix.matches("[a-z0-9-]+")||!alias.matches("R[0-9]{4}"))throw new IOException("Invalid RNA process label identity");
        return prefix+"-"+alias.toLowerCase(java.util.Locale.ROOT);
    }
    static void policies(Map<String,String> o)throws IOException {
        choice(o,"genome",Set.of("hg38","mm10"));String provenance=FollowupSupport.required(o,"reference-provenance");if(provenance.isBlank()||provenance.length()>4096||provenance.chars().anyMatch(Character::isISOControl))throw new IOException("Require bounded explicit reference provenance without controls");
        choice(o,"mapping",Set.of("unique","keep-one","keep-all"));FollowupSupport.required(o,"minimum-mapq");int mapq=FollowupSupport.integer(o,"minimum-mapq",0,0,255);if(!o.get("mapping").equals("unique")&&mapq!=0)throw new IOException("MAPQ is unused by keep-one/keep-all; require explicit minimum-mapq0");
        choice(o,"mates",Set.of("both","first","second"));choice(o,"flip-strands",Set.of("true","false"));choice(o,"strand-specific-paired",Set.of("true","false"));choice(o,"relative-strand",Set.of("both","same","opposite"));
        choice(o,"condensation",Set.of("copy-families","distinct-transcripts","exon-parts"));choice(o,"normalization",Set.of("raw","rpkm","both"));
        for(String key:List.of("reference-irregularities","short-exon-parts"))choice(o,key,Set.of("error","retain-original"));
        for(String key:List.of("duplicate-alignments","supplementary-alignments","unpaired-alignments","improper-pairs"))choice(o,key,Set.of("error","retain-upstream"));choice(o,"identical-replicates",Set.of("error","retain-explicit"));FollowupSupport.required(o,"sam-list");
    }
    private static void choice(Map<String,String> o,String key,Set<String> values)throws IOException{if(!values.contains(FollowupSupport.required(o,key)))throw new IOException("Choose explicit --"+key+" from "+values);}
    static HomerSamTags.Options samOptions(Map<String,String> o)throws IOException {
        return new HomerSamTags.Options(switch(o.get("mapping")){case "unique"->HomerSamTags.Mapping.UNIQUE;case "keep-one"->HomerSamTags.Mapping.KEEP_ONE;case "keep-all"->HomerSamTags.Mapping.KEEP_ALL;default->throw new IOException("Invalid RNA mapping");},
                switch(o.get("mates")){case "both"->HomerSamTags.Mates.BOTH;case "first"->HomerSamTags.Mates.FIRST;case "second"->HomerSamTags.Mates.SECOND;default->throw new IOException("Invalid RNA mates");},
                HomerSamTags.Length.MATCHING_BLOCK,HomerSamTags.SoftClips.ORIGINAL_DEFAULT,FollowupSupport.integer(o,"minimum-mapq",0,0,255),0,1000000000,Boolean.parseBoolean(o.get("flip-strands")),Boolean.parseBoolean(o.get("strand-specific-paired")));
    }
    static List<String> tagArguments(Map<String,String> o,Path directory,Path sam) {
        var args=new ArrayList<String>(List.of(directory.toString(),sam.toString(),"-format","sam",switch(o.get("mapping")){case "unique"->"-unique";case "keep-one"->"-keepOne";case "keep-all"->"-keepAll";default->throw new IllegalArgumentException("Invalid mapping");},"-mapq",o.get("minimum-mapq"),"-fragLength","150"));
        if(!o.get("mates").equals("both"))args.add(o.get("mates").equals("first")?"-read1":"-read2");if(o.get("flip-strands").equals("true"))args.add("-flip");if(o.get("strand-specific-paired").equals("true"))args.add("-sspe");return List.copyOf(args);
    }
    static Map<String,String> parse(String[] args)throws Exception {
        var direct=FollowupSupport.options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;byte[] raw=AdmixtureInputs.raw(Path.of(direct.get("options-file")),1048576);var result=new LinkedHashMap<String,String>();
        for(var entry:AdmixtureInputs.decode(raw).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown,recursive or non-string RNA option");result.put(key,value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    static String help(){return """
        homer-tools rna --home PRIVATE_RNA_HOME --profile linux-x86_64-rna-5.1-v7.0
          --out NEW_DIRECTORY --sam-list REPLICATES.tsv --genome hg38|mm10
          --reference-provenance EDITION_AND_SOURCE --mapping unique|keep-one|keep-all
          --minimum-mapq 0..255 --mates both|first|second --flip-strands true|false
          --strand-specific-paired true|false --relative-strand both|same|opposite
          --condensation copy-families|distinct-transcripts|exon-parts --normalization raw|rpkm|both
          --reference-irregularities error|retain-original --short-exon-parts error|retain-original
          --duplicate-alignments error|retain-upstream --supplementary-alignments error|retain-upstream
          --unpaired-alignments error|retain-upstream --improper-pairs error|retain-upstream
          --identical-replicates error|retain-explicit
        Original HOMER5.1 RefSeq v7.0 RNA resources, Linux x86_64 Java (WSL on Windows).
        Prepare resources using prepare_homer_rna_resources.py; install separately with install_homer_rna.py.
        Plain UTF8 SAM list header replicate_id<TAB>sam_path;1..32 IDs,paths relative to list.
        Explicit SAM filtering/strand choices are required; MAPQ must be0 for keep-one/keep-all.
        Original matching-block CIGAR semantics and paired halfweights are retained; no fragment overlap.
        Fixed tagAdjust0/no tag cap, inclusive anchors, complete source boundary ownership audit.
        RPKM denominator is whole tagInfo mass and source end-start lengths; no silent length correction.
        RefSeq empty/one-base parts and missing/overrun contigs require explicit policies.
        Invalid effective RPKM lengths fail; choose a scientifically justified aggregation/normalization.
        Source copy suffixes,all source parts/contributors and original metadata stay visible.
        Fractions are preserved in raw counts; downstream DESeq2 compatibility requires review.
        Defaults: timeout3600,input64GiB,decoded100Bcharacters,workspace128GiB,upstreamRSS8GiB,
        records10M,line65536,chromosome-tags2M,ledger-keys100k,transcripts200k,exon-parts2M,
        features-per-transcript10k,exons-per-chromosome1M,output-rows2M,contributors10k.
        Raise maximum-* flags explicitly with adequate Java heap (3GiB for small two-sample full references).
        At least4GiB allowed upstream RSS and6GiB available memory required; monitors are not OS quotas.
        Use safe ASCII Linux output parents; Unicode source paths/IDs are retained through staged aliases.
        String-options JSON via --options-file; direct flags override. No arbitrary upstream arguments.
        Read WARNINGS.md,session.json,all original stderr,alignment/tag/feature/count/contributor ledgers.
        Missing/forbidden/changed dependencies SKIP3; other failures2 retain partial/. Use NEW output paths.
        No alignment,deduplication,differential-expression,GO,registration or Cell Ranger analysis.
        """;}
}
