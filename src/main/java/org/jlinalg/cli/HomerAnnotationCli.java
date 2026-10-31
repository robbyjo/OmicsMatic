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

/** Typed original supplied-GTF annotation, with complete native coordinate and TSS checks. */
final class HomerAnnotationCli {
    private HomerAnnotationCli() { }
    private static final Set<String> ALLOWED=Set.of("home","profile","out","peaks","peak-format","gtf","reference-lengths","reference-build","annotation-release","gtf-id","gtf-unknown-strand","peak-unknown-strand","duplicate-ids","tss-ties","missing-gtf-contig","annotation-stats","timeout","maximum-input-bytes","workspace-bytes","maximum-upstream-memory-bytes","maximum-records","maximum-transcripts","maximum-line-characters","options-file");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try{
            Map<String,String> o=parse(args);FollowupSupport.required(o,"home");FollowupSupport.required(o,"profile");Path output=Path.of(FollowupSupport.required(o,"out")).toAbsolutePath().normalize();
            if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists;choose a NEW directory");int[] code={0};
            FollowupSupport.externalTransaction(o,"homer-original-annotation",(work,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method","original-HOMER-supplied-GTF-annotation");session.put("requested_options",o);
                try{
                    policies(o);var limits=HomerAnnotationData.Limits.read(o);var runtime=HomerAnnotationRuntime.open(Path.of(o.get("home")),o.get("profile"));var data=HomerAnnotationData.stage(o,work,limits);
                    var argv=new ArrayList<>(List.of("stage-peaks.pos","none","-gtf","stage-genes.gtf","-size","given"));
                    if(o.get("annotation-stats").equals("original-gtf-extent"))argv.addAll(List.of("-annStats","original-annotation-stats.tsv"));
                    int exit=HomerAnnotationRuntime.execute(runtime,argv,work,limits.timeout(),limits.workspace(),limits.rss(),session);if(exit!=0)throw new IOException("Original annotatePeaks.pl exited "+exit+";inspect original.stderr");
                    data.verify();long rows=HomerAnnotationData.audit(data,work,limits);runtime.verify();data.verify();HomerRuntime.bound(work,limits.workspace());
                    if(o.get("annotation-stats").equals("original-gtf-extent")&&(!Files.isRegularFile(work.resolve("original-annotation-stats.tsv"))||Files.size(work.resolve("original-annotation-stats.tsv"))==0))throw new IOException("Requested original annotation statistics file is missing/empty");
                    session.put("audited_original_rows",rows);session.put("gtf_records",data.gtf().records());session.put("gtf_exons",data.gtf().exons());session.put("gtf_transcripts",data.gtf().transcripts().size());session.put("noncontiguous_source_transcript_blocks",data.gtf().noncontiguousSourceBlocks());session.put("source_duplicate_peak_ids",data.duplicateIds());session.put("peak_dot_strands_assigned_plus",data.plusAssignedPeaks());session.put("gtf_dot_strands_assigned_plus",data.gtf().plusAssignedExons());session.put("peaks_on_missing_GTF_contigs",data.missingContigs());session.put("peaks_with_native_TSS_assignment_ties",data.ties());session.put("complete_native_coordinate_TSS_audit","passed");
                    var hashes=new TreeMap<String,String>();data.hashes().forEach((p,h)->hashes.put(p.toString(),h));session.put("immutable_input_and_staging_sha256",hashes);
                    warnings.add("REFERENCE AND POPULATION REVIEW: supplied GTF,exact contig lengths,declared build="+o.get("reference-build")+",annotation release="+o.get("annotation-release")+";genome argument is explicitly none. No FASTA,named genome,organism database or contig alias is inferred. Every supplied peak remains a separate staged ASCII alias with source bytes,ordinals and names in peaks.tsv. Duplicate source IDs retained="+data.duplicateIds()+". Required action: verify the actual reference assembly/release and review immutable input copies;declarations alone do not establish compatibility. No sequence,motif,tag/coverage,RNA or GO analysis is performed.");
                    warnings.add("COORDINATE AND NEAREST-TSS REVIEW: BED6 is zero-based half-open;HOMER inputs/outputs are one-based inclusive. Native auditing uses floor((start1+end1)/2),exact contigs,all equally nearest transcript candidates and transcript-oriented signed distance,independent of peak strand. Tied peak assignments="+data.ties()+";the original selection remains beside every compatible native candidate. Required action: review native-tss-candidates.tsv;do not turn the selected tied transcript into uniquely supported gene evidence. Feature labels may refer to a different overlapping transcript than the nearest TSS.");
                    warnings.add("GTF PARSING REVIEW: original default lowercase exon/transcript_id mode is explicit. Original parseGTF sorts exon rows by contig,gene,transcript,start,end before grouping;native aggregation independently retains the same compatible transcript spans. Noncontiguous source blocks beyond the first="+data.gtf().noncontiguousSourceBlocks()+" and all transcripts/source-block counts remain in gtf-transcripts.tsv. Dot exon strands assigned plus="+data.gtf().plusAssignedExons()+",dot peak strands assigned plus="+data.plusAssignedPeaks()+" under explicit permissions. Required action: review source row order,exon features,segment identities and strand choices. Inconsistent transcript gene/contig/strand mappings and reserved -HOMER identifiers fail before execution;raw source row order is preserved in immutable inputs;the original internal sort is explicit and no input records are silently dropped.");
                    warnings.add("MISSING GTF CONTIGS: "+data.missingContigs()+" peaks remain under the explicitly selected policy,with original NA annotation/TSS rather than fabricated genes. Required action: inspect every retained missing-contig row and the reference dictionary;NA is not evidence of an intergenic locus or absent biology.");
                    warnings.add("ORIGINAL FEATURE AND METADATA REVIEW: complete original feature labels,gene metadata,numeric strings and stdout/stderr remain retained. Native Java independently checks identity,coordinates,population and exact nearest-TSS ID/distance;it does not independently reconstruct annotation priority,feature-class selection or gene-symbol conversion. Original metadata removes terminal dot-numeric versions from gene/transcript IDs,while Nearest PromoterID retains the full selected transcript ID. The source column labelled Entrez ID can contain a version-trimmed GTF gene_id;it does not certify an Entrez namespace. Full GTF IDs and derived metadata IDs remain separate in gtf-transcripts.tsv. Version-stripped transcript IDs mapping to different version-stripped genes fail before execution rather than conflating genes. Required action: review original annotations,full IDs and actual namespaces before downstream joins;do not silently strip versions from user data.");
                    warnings.add("ANNOTATION STATISTICS REVIEW: requested file policy="+o.get("annotation-stats")+". Original prioritize/annotation diagnostics can still occur in stderr. With supplied GTF and genome none,the original extent/background comes from annotation processing,not a validated complete assembly-wide universe;the statistics file is preserved as an original diagnostic. Native Java does not certify these enrichment denominators,P values or empirical calibration. Required action: review the source annotation universe and tested family before any biological/statistical inference;no q/E value or multiple-testing adjustment is inferred.");
                    warnings.add("RUNTIME AND RESOURCE REVIEW: only configureHomer-style installation-path lines3/4 in two Perl files are relocated;all scientific script bodies and the original compiled annotation binary retain their pinned bytes. Perl/modules,shell/core utilities,shared libraries,host and runtime inventory are checked. Required action: inspect runtime/source hashes and original.stderr. Time,workspace and owned-process RSS bounds are periodic monitors,not OS-enforced quotas;set -Xmx and capacities explicitly when needed. Existing files are preserved;use a NEW output directory for reruns.");
                    manifest.put("analysis_status","completed-original-supplied-GTF-annotation-with-review-warnings");
                }catch(HomerRuntime.Unavailable unavailable){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency-or-capability");session.put("error",unavailable.toString());RnaToolRuntime.quarantine(work);warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage()+". "+HomerAnnotationRuntime.recovery());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(work);warnings.add("ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect retained partial/,original.stderr and input ledgers;correct the named input,reference,parameter,resource or audit cause and rerun to a NEW directory. Partial tables are not completed results.");}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);if(code[0]!=0)session.put("retained_files_location","partial/");
                Files.writeString(work.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(work.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status"),"exit_code",code[0]))+"\n",StandardCharsets.UTF_8);
                Files.writeString(work.resolve("WARNINGS.md"),"# HOMER ANNOTATION "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+HomerAnnotationRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+output.resolve("WARNINGS.md"));if(code[0]==0)out.println("Original HOMER supplied-GTF annotation completed: "+output);return code[0];
        }catch(Exception failure){err.println("omicsmatic: "+failure.getMessage()+"\n"+HomerAnnotationRuntime.recovery());return 2;}
    }
    private static void policies(Map<String,String> o)throws IOException{
        for(String key:List.of("peaks","gtf","reference-lengths","reference-build","annotation-release"))FollowupSupport.required(o,key);
        choice(o,"peak-format",Set.of("bed6","homer"));choice(o,"gtf-unknown-strand",Set.of("error","original-plus"));choice(o,"peak-unknown-strand",Set.of("error","original-plus"));choice(o,"duplicate-ids",Set.of("error","retain-ordinal"));choice(o,"tss-ties",Set.of("error","retain-original"));choice(o,"missing-gtf-contig",Set.of("error","retain"));choice(o,"annotation-stats",Set.of("omit","original-gtf-extent"));
        if(!FollowupSupport.required(o,"gtf-id").equals("transcript"))throw new HomerRuntime.Unavailable("Only original default transcript_id annotation is admitted;gene-ID mode can collapse distinct TSS rows and is not silently substituted");
    }
    private static void choice(Map<String,String> o,String key,Set<String> values)throws IOException{if(!values.contains(FollowupSupport.required(o,key)))throw new IOException("Choose explicit --"+key+" from "+values);}
    private static Map<String,String> parse(String[] args)throws Exception{
        var direct=FollowupSupport.options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;byte[] raw=AdmixtureInputs.raw(Path.of(direct.get("options-file")),1048576);var result=new LinkedHashMap<String,String>();
        for(var entry:AdmixtureInputs.decode(raw).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown,recursive or non-string annotation option");result.put(key,value);}
        result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    static String help(){return """
        homer-tools annotate --home PRIVATE_ANNOTATION_HOME
          --profile linux-x86_64-annotation-5.1 --out NEW_DIRECTORY
          --peaks PEAK_FILE --peak-format bed6|homer --gtf GTF9
          --reference-lengths CONTIG_TAB_LENGTH --reference-build BUILD
          --annotation-release RELEASE --gtf-id transcript
          --gtf-unknown-strand error|original-plus
          --peak-unknown-strand error|original-plus --duplicate-ids error|retain-ordinal
          --tss-ties error|retain-original --missing-gtf-contig error|retain
          --annotation-stats omit|original-gtf-extent
        Original annotatePeaks.pl,genome none,size given,default transcript_id mode.
        Separate annotation profiles:5.1,4.11,4.11.1;core-only profiles are insufficient.
        Setup: tools/literature/install_homer_annotation.py with explicit install/download permission.
        No genome/sequence download. BED6 is zero-based half-open,HOMER6/7 is one-based inclusive.
        Native complete peak/coordinate/exact-contig nearest-TSS audit retains all tied candidates.
        Original feature labels/metadata/diagnostic statistics remain original-engine results.
        Supplied-GTF background is not a certified whole-genome enrichment universe.
        Missing dependencies skip3;other failures2 quarantine partials with prominent Required action.
        Read WARNINGS.md,session.json,peaks.tsv,gtf-transcripts.tsv,native-tss-candidates.tsv,
        results.tsv and original.stdout/stderr. Reruns require NEW output paths.
        Optional capacities: timeout600,maximum-input-bytes268435456,workspace-bytes536870912,
        maximum-upstream-memory-bytes1073741824,maximum-records1000000,
        maximum-transcripts1000000,maximum-line-characters1048576.
        UTF8 string options JSON via --options-file supports Unicode paths;direct flags override it.
        No ignored/recursive/non-string options. Cell Ranger remains excluded.
        """;}
}
