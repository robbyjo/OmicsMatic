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

/** Original genomic known/de-novo motif workflow with explicit supplied resources. */
final class HomerGenomeCli {
    private HomerGenomeCli() { }
    private static final Set<String> CORE_ALLOWED=Set.of("home","profile","out","analysis","genome","reference-build","peaks","peak-format","background","background-format","background-mode","size","unknown-strand","duplicate-ids","alphabet","mask-lowercase","background-overlap","weighting","strands","known-strand-policy","statistic","known-motifs","comparison-motifs","matrix-values","motif-length","mismatches","motifs-to-find","normalization-order","normalization-iterations","maximum-n","threads","stat-cache-mb","timeout","maximum-input-bytes","maximum-reference-bases","maximum-extracted-bases","maximum-records","maximum-motifs","maximum-motif-rows","maximum-line-characters","maximum-operations","maximum-neighborhoods","maximum-upstream-memory-bytes","workspace-bytes","options-file");
    private static final Set<String> ALLOWED=allowed();
    private static Set<String> allowed(){var values=new java.util.HashSet<>(CORE_ALLOWED);values.addAll(HomerGeneratedBackground.OPTIONS);return Set.copyOf(values);}
    enum Analysis {
        BOTH("both",true,true), KNOWN("known",true,false), DENOVO("denovo",false,true);
        final String label;final boolean known,discovery;
        Analysis(String label,boolean known,boolean discovery){this.label=label;this.known=known;this.discovery=discovery;}
        static Analysis read(Map<String,String> o)throws IOException{
            String value=o.getOrDefault("analysis","both");
            for(var analysis:values())if(analysis.label.equals(value))return analysis;
            throw new IOException("Choose analysis both,known or denovo");
        }
    }
    record Plan(String size,int width,int move,int motifWidth,int mismatches,int motifs,int norm,int iterations,
                int threads,int cache,double maximumN,Analysis analysis) {
        static Plan read(Map<String,String> o)throws IOException {
            var analysis=Analysis.read(o);String mode=HomerGeneratedBackground.mode(o);
            for(String key:List.of("genome","reference-build","peaks"))FollowupSupport.required(o,key);if(mode.equals("supplied")){FollowupSupport.required(o,"background");HomerGeneratedBackground.rejectSuppliedExtras(o);}
            if(analysis.known)FollowupSupport.required(o,"known-motifs");
            else if(o.containsKey("known-motifs")||o.containsKey("known-strand-policy"))throw new IOException("Known enrichment is not requested;remove known-motifs and known-strand-policy for analysis denovo");
            if(analysis.discovery)FollowupSupport.required(o,"comparison-motifs");
            else for(String key:List.of("comparison-motifs","motif-length","mismatches","motifs-to-find"))if(o.containsKey(key))throw new IOException("De novo discovery is not requested;remove --"+key+" for analysis known");
            for(String key:List.of("reference-build"))if(o.get(key).isBlank()||o.get(key).codePoints().anyMatch(Character::isISOControl))throw new IOException("Require explicit nonempty control-free reference-build label");
            choice(o,"peak-format",Set.of("bed6","homer"));if(mode.equals("supplied"))choice(o,"background-format",Set.of("bed6","homer"));
            choice(o,"unknown-strand",Set.of("error","original-plus"));choice(o,"duplicate-ids",Set.of("error","retain-ordinal"));choice(o,"alphabet",Set.of("strict-dna","mask-n"));choice(o,"mask-lowercase",Set.of("yes","no"));choice(o,"background-overlap",Set.of("retain","remove-original"));choice(o,"weighting",Set.of("gc","cpg","none"));choice(o,"strands",Set.of("both","forward"));choice(o,"statistic",Set.of("binomial","hypergeometric"));

            if(!Set.of("require-match","original-wrapper").contains(o.getOrDefault("known-strand-policy","require-match")))throw new IOException("Choose known-strand-policy require-match or original-wrapper");
            if(!Set.of("probabilities","original-weights").contains(o.getOrDefault("matrix-values","probabilities")))throw new IOException("Choose matrix-values probabilities or original-weights");
            String size=FollowupSupport.required(o,"size");int width=0,move=0;
            if(!size.equals("given")){
                if(size.matches("-?[0-9]+,-?[0-9]+")){String[] f=size.split(",");long a=signed(f[0]),b=signed(f[1]);if(b<=a||b-a>100000)throw new IOException("Asymmetric size requires ordered endpoints and span<=100000");width=(int)(b-a);move=(int)Math.floorDiv(a+b,2);}
                else width=integerText(size,1,100000);
            }
            int threads=whole(o,"threads",1,1,10);if(analysis.discovery&&10%threads!=0)throw new IOException("Original wrapper uses ten discovery trials;choose threads1,2,5 or10 to avoid changing that count");
            int m=analysis.discovery?whole(o,"motif-length",8,4,15):0,mis=analysis.discovery?whole(o,"mismatches",2,0,Math.min(5,m)):0;
            var result=new Plan(size,width,move,m,mis,analysis.discovery?whole(o,"motifs-to-find",25,1,1000):0,whole(o,"normalization-order",3,0,analysis.discovery?Math.min(8,m):8),whole(o,"normalization-iterations",160,1,10000),threads,whole(o,"stat-cache-mb",8,1,4096),number(o.getOrDefault("maximum-n",mode.equals("generated-new")?"0.5":"0.7"),0,1),analysis);if(!mode.equals("supplied"))HomerGeneratedBackground.Settings.read(o,result);return result;
        }
        List<String> command(Map<String,String> o,String version){
            var a=new ArrayList<>(List.of("stage-targets.pos","stage-genome.fa","original-out","-size",size));HomerGeneratedBackground.arguments(a,o);
            if(analysis.known)a.addAll(List.of("-mknown","stage-known.motif"));else a.add("-noknown");
            if(analysis.discovery)a.addAll(List.of("-mcheck","stage-comparison.motif","-len",Integer.toString(motifWidth),"-mis",Integer.toString(mismatches),"-S",Integer.toString(motifs)));else a.add("-nomotif");
            a.addAll(List.of("-nlen",Integer.toString(norm),"-nmax",Integer.toString(iterations),"-cache",Integer.toString(cache),"-p",Integer.toString(threads),"-maxN",Double.toString(maximumN),"-nofacts","-keepFiles","-dumpFasta","-preparsedDir","private-preparsed"));
            if(version.equals("5.1")&&!o.get("background-mode").equals("generated-new"))a.add("-useOldBg");
            if(o.get("weighting").equals("gc"))a.add("-gc");else if(o.get("weighting").equals("cpg"))a.add("-cpg");else a.add("-noweight");
            if(o.get("mask-lowercase").equals("yes"))a.add("-mask");if(o.get("background-overlap").equals("retain"))a.add("-keepOverlappingBg");if(o.get("strands").equals("forward"))a.add("-norevopp");if(o.get("statistic").equals("hypergeometric"))a.add("-h");return a;
        }
    }
    static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try{
            Map<String,String> o=parse(args);FollowupSupport.required(o,"home");FollowupSupport.required(o,"profile");Path target=Path.of(FollowupSupport.required(o,"out")).toAbsolutePath().normalize();if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists;choose a NEW directory");int[] code={0};
            FollowupSupport.externalTransaction(o,"homer-original-genomic-motifs",(work,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method","original-HOMER-genomic-motifs");session.put("requested_options",o);
                try{
                    var plan=Plan.read(o);session.put("requested_analysis",plan.analysis().label);session.put("known_analysis",plan.analysis().known?"requested":"not-requested");session.put("discovery_analysis",plan.analysis().discovery?"requested":"not-requested");
                    var limits=HomerGenomeData.Limits.read(o);var runtime=HomerGenomeRuntime.open(Path.of(o.get("home")),o.get("profile"));var knownStrands=knownStrands(o,runtime.version());
                    session.put("known_strands_effective",knownStrands==null?"not-requested":knownStrands.toString());session.put("discovery_strands_requested",plan.analysis().discovery?o.get("strands"):"not-requested");session.put("normalization_strands_requested",o.get("strands"));
                    if(knownStrands!=null&&o.get("strands").equals("forward")&&knownStrands==org.jlinalg.sequence.HomerMotifs.Strands.BOTH)warnings.add("LEGACY KNOWN-STRAND POLICY: original HOMER "+runtime.version()+" genomic wrapper does not forward -norevopp to known enrichment. Under explicit original-wrapper policy,known motifs use BOTH strands despite the requested FORWARD selection. Normalization and de novo discovery use FORWARD when requested. Required action: retain the effective phase distinction in interpretation;choose require-match to SKIP this legacy configuration,or select a scientifically appropriate separately admitted operation/version explicitly. No upstream scientific script is patched.");
                    var generated=o.get("background-mode").equals("supplied")?null:HomerGeneratedBackground.prepare(o,plan,limits,runtime,work,session,warnings);
                    var data=generated==null?HomerGenomeData.stage(o,plan,limits,runtime,work):generated.data();
                    int exit=generated==null?HomerGenomeRuntime.execute(runtime,plan.command(o,runtime.version()),work,limits.timeout(),limits.workspace(),limits.rss(),session):HomerGenomeRuntime.execute(runtime,plan.command(o,runtime.version()),work,limits.timeout(),limits.workspace(),limits.rss(),session,generated.runtime()::verify,generated.runtime()::marker);if(exit!=0)throw new IOException("Original findMotifsGenome.pl exited "+exit+";inspect original.stderr");
                    data.verify();if(generated!=null)data=HomerGeneratedBackground.complete(generated,plan,limits,o,work,session,warnings);HomerGenomeAudit.audit(data,plan,limits,o,runtime,work,session,warnings);runtime.verify();if(generated!=null)generated.runtime().verify();data.verify();HomerRuntime.bound(work,limits.workspace());
                    var hashes=new TreeMap<String,String>();data.hashes().forEach((p,h)->hashes.put(p.toString(),h));session.put("immutable_input_and_staging_sha256",hashes);session.put("reference_build",o.get("reference-build"));session.put("input_regions",generated==null?data.regions().size():generated.data().regions().size());session.put("source_duplicate_region_ids",data.duplicates());session.put("native_pre_normalization_population_audit","passed");
                    warnings.add("REFERENCE AND COORDINATE REVIEW: reference-build="+o.get("reference-build")+" is a supplied label,not inferred assembly verification. Exact source contigs map bijectively to staged ASCII aliases in reference.tsv;original bytes/full headers and region rows remain retained. Source case and bases are preserved in staged LF FASTA;native extraction applies the explicit alphabet/masking/strand choices. Numeric size N has original inclusive width N+1;asymmetric size also uses the original floor-rounded displacement. Source input intervals must lie within their contigs. Resized extraction and explicitly retained old-generated boundaries keep their recorded source semantics. Required action: inspect reference.tsv,regions.tsv and population.tsv before interpreting genomic sites.");
                    long nonunit=data.known().stream().mapToLong(m->m.motif().nonunitRows()).sum()+data.comparison().stream().mapToLong(m->m.motif().nonunitRows()).sum();session.put("source_nonunit_matrix_rows",nonunit);warnings.add("MATRIX SCALE REVIEW: profile libraries retain their exact original values;custom files use explicit matrix-values="+o.getOrDefault("matrix-values","probabilities")+". Nonunit rows="+nonunit+". Original scoring uses log(max(value,0.001)/0.25),without row normalization before fixed known scoring. Original comparison/print code may normalize for presentation. Required action: inspect the model mapping tables for requested phases and immutable input matrices;original weights are not relabelled as calibrated probabilities or silently rescaled. Staged names and contigs are deterministic aliases;computational reruns should use the retained exact staging because original iteration order may depend on identities.");
                    warnings.add("ORIGINAL WORKFLOW REVIEW: background mode="+o.get("background-mode")+",requested analysis="+plan.analysis().label+". Known enrichment="+(plan.analysis().known?"completed":"not-requested")+",de novo discovery and comparison="+(plan.analysis().discovery?"completed":"not-requested")+". Unrequested phases have null counts and no result tables;they are not zero-discovery or zero-enrichment findings. Supplied/background overlap policy="+o.get("background-overlap")+",weighting="+o.get("weighting")+",normalization order="+plan.norm()+". The original sequence/GC-bin exclusions and every analytic weight remain explicit;sequence records outside final group are not tested population members. Required action: review population.tsv,original-out/group.adj and original.stderr. Generated modes require their additional operation admissions and audits;named genome packages,repeat/TE resources,peak/coverage and RNA require their own admitted operations;no full-paper or biological certification is implied.");
                    warnings.add("RUNTIME REVIEW: original scorer/merger binaries and scientific Perl bodies are pinned;homerTools is built from original source with its installation-home configuration only. Archive HTTP pins identify content without independently authenticated signatures. Required action: retain runtime/build/source hashes and all original diagnostics. Timeout,workspace and process RSS monitors are periodic bounds,not OS-enforced quotas. Reruns require NEW output paths. Cell Ranger excluded.");
                    manifest.put("analysis_status",plan.analysis()==Analysis.BOTH?"completed-original-genomic-known-and-denovo-with-review-warnings":"completed-original-genomic-"+plan.analysis().label+"-with-review-warnings");
                }catch(HomerRuntime.Unavailable unavailable){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency-or-capability");session.put("error",unavailable.toString());RnaToolRuntime.quarantine(work);warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage()+". "+HomerGenomeRuntime.recovery());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(work);warnings.add("ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect retained partial/,input/population ledgers and original.stderr;correct the named input,resource,capacity or audit cause and rerun to a NEW directory. Partial tables are not completed results.");}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);if(code[0]!=0)session.put("retained_files_location","partial/");Files.writeString(work.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(work.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status"),"exit_code",code[0]))+"\n",StandardCharsets.UTF_8);Files.writeString(work.resolve("WARNINGS.md"),"# HOMER GENOMIC "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+HomerGenomeRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+target.resolve("WARNINGS.md"));if(code[0]==0)out.println("Original HOMER requested genomic workflow completed: "+target);return code[0];
        }catch(Exception failure){err.println("omicsmatic: "+failure.getMessage()+"\n"+HomerGenomeRuntime.recovery());return 2;}
    }
    static org.jlinalg.sequence.HomerMotifs.Strands knownStrands(Map<String,String> o,String version)throws IOException{
        if(!Analysis.read(o).known)return null;
        if(o.get("strands").equals("both"))return org.jlinalg.sequence.HomerMotifs.Strands.BOTH;
        if(version.equals("5.1"))return org.jlinalg.sequence.HomerMotifs.Strands.FORWARD;
        if(!o.getOrDefault("known-strand-policy","require-match").equals("original-wrapper"))throw new HomerRuntime.Unavailable("Original HOMER "+version+" genomic wrapper ignores forward-only selection for known enrichment. Choose explicit --known-strand-policy original-wrapper to retain known-BOTH/discovery-FORWARD behavior,or retain require-match and SKIP. No version or strand is silently substituted");
        return org.jlinalg.sequence.HomerMotifs.Strands.BOTH;
    }
    static void choice(Map<String,String> o,String key,Set<String> choices)throws IOException{if(!choices.contains(FollowupSupport.required(o,key)))throw new IOException("Choose explicit --"+key+" from "+choices);}
    static int whole(Map<String,String> o,String key,int fallback,int min,int max)throws IOException{return integerText(o.getOrDefault(key,Integer.toString(fallback)),min,max);}
    static double number(String text,double min,double max)throws IOException{try{double n=Double.parseDouble(text);if(!Double.isFinite(n)||n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException failure){throw new IOException("Require finite numeric value in["+min+","+max+"]: "+text,failure);}}
    static long signed(String text)throws IOException{try{if(!text.matches("-?[0-9]+"))throw new NumberFormatException();long n=Long.parseLong(text);if(n< -1000000000L||n>1000000000L)throw new NumberFormatException();return n;}catch(NumberFormatException failure){throw new IOException("Invalid bounded integer: "+text,failure);}}
    static int integerText(String text,int min,int max)throws IOException{long n=signed(text);if(n<min||n>max)throw new IOException("Integer outside capacity["+min+","+max+"]");return(int)n;}
    private static Map<String,String> parse(String[] args)throws Exception{
        var direct=FollowupSupport.options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;byte[] raw=AdmixtureInputs.raw(Path.of(direct.get("options-file")),1048576);var result=new LinkedHashMap<String,String>();for(var e:AdmixtureInputs.decode(raw).entrySet()){String key=e.getKey().toString();if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String value))throw new IOException("Unknown,recursive or non-string genomic option");result.put(key,value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    static String help(){return """
        homer-tools genome --home PRIVATE_GENOMIC_HOME --profile linux-x86_64-genome-5.1
          --out NEW_DIRECTORY --genome FASTA --reference-build LABEL
          --peaks REGIONS --peak-format bed6|homer --background REGIONS
          --background-format bed6|homer (supplied only)
          --background-mode supplied|generated-old|generated-new --size given|N|START,END
          --unknown-strand error|original-plus --duplicate-ids error|retain-ordinal
          --alphabet strict-dna|mask-n --mask-lowercase yes|no
          --background-overlap retain|remove-original --weighting gc|cpg|none
          --strands both|forward --statistic binomial|hypergeometric
          --analysis both(default)|known|denovo
          --known-motifs FILE|profile (known/both only)
          --comparison-motifs FILE|profile (denovo/both only)
        Generated modes omit background/background-format and require generated-admission,
        background-randomness original-unseeded and background-count-policy exact|retain-original.
        Old:background-totalN,background-boundary-policy error|retain-original;5.1 requires
        overlap remove-original(literal inclusive intervals);4.11/4.11.1 requires retain.
        Self-overlaps remain original.
        New5.1:background-countN,background-candidatesNN(0=all),background-binsN,
        background-sub-binsN,background-kmer0..4,background-kmer-mode overall|positional,
        background-self-overlap forbid|allow;weighting none,mask-lowercase no,maximum-n0.5.
        Original requested-phase genomic workflow;both includes known+de novo+matching+SVG.
        Known-only retains normalization,does not require discovery seed windows and rejects
        comparison-motifs,motif-length,mismatches,motifs-to-find. Denovo-only retains known
        matching/SVG,does not run enrichment and rejects known-motifs/known-strand-policy.
        Unrequested phases have null counts,no result tables and explicit not-requested status.
        Setup: tools/literature/install_homer_genome.py;separate profiles5.1,4.11,4.11.1.
        Optional matrix-values probabilities(default)|original-weights for custom files.
        Profile libraries retain original raw values,including nonunit/count-weight rows.
        Optional known-strand-policy require-match(default)|original-wrapper:
        legacy4.11/4.11.1 known enrichment ignores forward-only,requiring explicit
        original-wrapper permission to retain known-BOTH/discovery-FORWARD behavior.
        Options:motif-length8,mismatches2,motifs-to-find25,normalization-order3,
        normalization-iterations160,maximum-n0.7,threads1(divisor of10 for discovery),stat-cache-mb8,
        timeout3600,maximum-input-bytes8589934592,maximum-reference-bases8000000000,
        maximum-extracted-bases100000000,maximum-records100000,maximum-motifs20000,
        maximum-motif-rows500000,maximum-line-characters1048576,
        maximum-operations1000000000,maximum-neighborhoods1000000000,
        maximum-upstream-memory-bytes2147483648,workspace-bytes34359738368.
        Native sequence/GC population and weighted fixed-model audits retain complete ledgers.
        Original oligo normalization is independently replayed;learned-model presence uses
        conservative print-precision bounds. Original q adjustment is not standard BH.
        Read WARNINGS.md,population.tsv,results.tsv and original-out reports.
        Missing dependencies skip3;other failures2 retain quarantined partial diagnostics.
        String-options JSON via --options-file supports Unicode paths;direct flags override.
        No implicit downloads,installation,version substitution or Cell Ranger.
        """;}
}
