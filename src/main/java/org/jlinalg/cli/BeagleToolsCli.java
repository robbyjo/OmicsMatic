/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Original Beagle GT-only phasing and reference imputation with native validation. */
final class BeagleToolsCli {
    private BeagleToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("method","profile","input","reference","map","assembly","out","home","java","phase-policy","target-only-markers","sample-overlap","minimum-observed-markers","seed","iterations","burnin","ne","err","em","window-unit","window-size","overlap-size","phase-states","imp-states","threads","memory-mib","stack-mib","timeout-seconds","maximum-samples","maximum-variants","maximum-cells","maximum-input-mib","maximum-retained-mib","maximum-line-bytes","maximum-report-mib","options-file");
    private static final Set<String> PROFILES=Set.of("5.4-29Oct24","5.1-18May20","4.1-08Jun17","4.1-21Jan17");
    private static final String RECOVERY="Required action: inspect WARNINGS.md, analysis-status.json, session.yaml, the sample/variant ledgers and original logs. Correct the named inputs, exact allele/map/assembly choices, phase/exclusion policy or explicit resource limit; rerun to a new output directory. No reference panel, allele flip, ploidy conversion, QC threshold or alternate engine is inferred. GT is not reconstructed from GP."+BeagleRuntime.setup();
    private record Parsed(Map<String,String> options,String recipeHash) { }

    static int run(String[] args,PrintStream output,PrintStream error) {
        if(args.length==1&&args[0].equals("--help")){output.println(help());return 0;}
        try {
            var parsed=parse(args);var o=parsed.options();for(String key:List.of("input","map","assembly","out"))required(o,key);
            String method=choice(o,"method",Set.of("phase","impute")),profile=choice(o,"profile",PROFILES),family=profile.startsWith("4.1")?"4.1":profile.startsWith("5.4")?"5.4":"5.1";
            boolean reference=o.containsKey("reference");if(method.equals("impute")&&!reference)throw new IllegalArgumentException("Reference imputation requires --reference;sporadic missing-call completion without a panel is part of --method phase");
            String phase=choice(o,"phase-policy",Set.of("preserve-provided","allow-rephase"));
            String excluded=reference?choice(o,"target-only-markers",Set.of("error","drop-with-warning")):"error",overlap=reference?choice(o,"sample-overlap",Set.of("error","allow-with-warning")):"error";
            if(!reference&&(o.containsKey("target-only-markers")||o.containsKey("sample-overlap")))throw new IllegalArgumentException("Reference-only marker/sample policies require a reference panel");
            int iterations=integer(o,"iterations",null),minimum=integer(o,"minimum-observed-markers",null),seed=Integer.parseInt(required(o,"seed")),threads=integer(o,"threads",1),memory=integer(o,"memory-mib",2048),stack=integer(o,"stack-mib",5),timeout=integer(o,"timeout-seconds",3600);
            if(threads>256||memory<128||stack>256||timeout>86400||iterations>100000)throw new IllegalArgumentException("Require threads<=256,memory>=128MiB,stack<=256MiB,timeout<=86400 and iterations<=100000");
            double ne=number(required(o,"ne"),"ne"),err=number(required(o,"err"),"err");if(ne<=0||err<0||err>=1)throw new IllegalArgumentException("Require finite ne>0 and err in[0,1)");
            String unit=choice(o,"window-unit",Set.of("markers","cm"));if(!unit.equals(family.equals("4.1")?"markers":"cm"))throw new IllegalArgumentException("Window units must match the selected original family:4.1markers;5.xcM");
            double window=number(required(o,"window-size"),"window-size"),windowOverlap=number(required(o,"overlap-size"),"overlap-size");
            if(window<=0||windowOverlap<0||window<=windowOverlap||!family.equals("4.1")&&window<1.1*windowOverlap)throw new IllegalArgumentException("Invalid window/overlap sizes;modern window must be at least1.1*overlap");
            if(family.equals("4.1")) {
                Integer.parseInt(required(o,"window-size"));Integer.parseInt(required(o,"overlap-size"));
                for(String key:List.of("burnin","phase-states","imp-states","em"))if(o.containsKey(key))throw new IllegalArgumentException("--"+key+" is not admitted by the selected legacy profile");
            } else {
                integer(o,"burnin",null);integer(o,"phase-states",null);
                if(method.equals("impute"))integer(o,"imp-states",null);else if(o.containsKey("imp-states"))throw new IllegalArgumentException("--imp-states applies only to reference imputation");
                if(family.equals("5.4"))choice(o,"em",Set.of("true","false"));else if(o.containsKey("em"))throw new IllegalArgumentException("Beagle5.1does not expose the selected5.4EM flag;do not drop or reinterpret it");
            }
            var limits=new BeagleInputs.Limits(integer(o,"maximum-samples",100000),integer(o,"maximum-variants",2000000),positive(o,"maximum-cells",100000000L),mib(o,"maximum-input-mib",512),mib(o,"maximum-retained-mib",512),integer(o,"maximum-line-bytes",1048576));long reportLimit=mib(o,"maximum-report-mib",4096);
            String home=o.getOrDefault("home",System.getenv("JLINALG_BEAGLE_HOME"));Path javaExecutable=o.containsKey("java")?Path.of(o.get("java")):Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows")?"java.exe":"java");
            if(home!=null&&!home.isBlank()){Path h=PlinkToolsCli.resolved(Path.of(home)),d=PlinkToolsCli.resolved(Path.of(o.get("out")));if(h.startsWith(d)||d.startsWith(h))throw new IllegalArgumentException("Beagle output must be outside the sealed runtime home and its ancestors");}
            int[] code={0};
            externalTransaction(o,"beagle-original-"+method,(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("scope","Original pinned GT-only diploid phasing/reference imputation,one chromosome,explicit map and version-specific options.No GL/PL genotype likelihood inference,IBD,polyploid/pedigree model,reference download,harmonization or historical-cohort replay.");session.put("requested_options",o);session.put("assembly_declared_not_reference_validated",o.get("assembly"));
                try {
                    if(home==null||home.isBlank())throw new BeagleRuntime.Unavailable("No --home or JLINALG_BEAGLE_HOME supplied",null);Path upstream=directory.resolve("upstream");Files.createDirectory(upstream);var runtime=new BeagleRuntime(Path.of(home),profile,javaExecutable,upstream,session);
                    if(!runtime.family().equals(family))throw new BeagleRuntime.Unavailable("Selected profile family differs from bundled identity",null);
                    var prepared=BeagleInputs.prepare(Path.of(o.get("input")).toAbsolutePath().normalize(),reference?Path.of(o.get("reference")).toAbsolutePath().normalize():null,Path.of(o.get("map")).toAbsolutePath().normalize(),upstream,family,method,phase,excluded,overlap,minimum,limits);
                    manifest.put("input_sha256",prepared.hashes());session.put("staged_input_sha256",prepared.stagedHashes());session.put("staging","GT-only diploid calls retain allele indices and provided phase separators;unused INFO/FORMAT annotations are not analysis inputs.Exact samples and variant positions/REF/ALT map to deterministic ASCII names and a single staging chromosome1.No biological chromosome/build/ploidy identity is inferred.Canonical IDs prefer the target ID at shared loci,otherwise the reference ID.Original identifiers and exclusions are retained in ledgers.");
                    long outputCells=Math.multiplyExact((long)prepared.expected().size(),prepared.target().samples().size());if(outputCells>limits.cells())throw new IOException("Expected output genotype cells exceed --maximum-cells");
                    long estimated=Math.addExact(Math.multiplyExact(outputCells,42L),8L*limits.line());for(var m:prepared.expected().values())estimated=Math.addExact(estimated,128L+4L*(prepared.target().chromosome().length()+m.id().length()+m.ref().length()+m.alt().length()));
                    session.put("conservative_canonical_VCF_byte_bound",estimated);if(estimated>reportLimit)throw new IOException("Expected canonical output byte bound exceeds --maximum-report-mib;review declared limits");
                    for(String name:List.of("variant-ledger.tsv","sample-ledger.tsv"))Files.copy(upstream.resolve(name),directory.resolve(name));snapshot(parsed,directory);
                    var command=new ArrayList<>(runtime.base(memory,stack));command.addAll(List.of("gt=target.vcf","out=result","map=map.txt","nthreads="+threads,"seed="+seed,"ne="+o.get("ne"),"err="+o.get("err"),"impute="+method.equals("impute")));if(reference)command.add("ref=reference.vcf");
                    command.addAll(List.of("window="+o.get("window-size"),"overlap="+o.get("overlap-size")));
                    if(family.equals("4.1")){command.add("niterations="+iterations);command.add("ibd=false");if(method.equals("impute"))command.add("gprobs=true");}
                    else{command.addAll(List.of("burnin="+o.get("burnin"),"iterations="+iterations,"phase-states="+o.get("phase-states")));if(method.equals("impute"))command.addAll(List.of("imp-states="+o.get("imp-states"),"ap=true","gp=true"));if(family.equals("5.4"))command.add("em="+o.get("em"));}
                    runtime.execute(command,upstream,"analysis",timeout);BeagleInputs.verify(prepared,upstream);
                    var counts=BeagleOutputs.validate(upstream.resolve("result.vcf.gz"),prepared,family,method,phase,directory,reportLimit);BeagleInputs.verify(prepared,upstream);runtime.verify();verifyRecipe(parsed);
                    session.put("validation_counts",Map.of("variants",counts.variants(),"genotype_cells",counts.genotypeCells(),"observed_calls_preserved",counts.observedPreserved(),"completed_calls",counts.completed(),"provided_phase_order_changes",counts.providedPhaseChanges(),"posterior_fields",counts.posteriorFields(),"naive_GP_argmax_differences",counts.naiveArgmaxDifferences(),"strict_GP_nonmax_genotypes",counts.strictNonmaxGenotypes()));
                    session.put("excluded_target_only_markers",prepared.excludedTargetMarkers());session.put("original_output_sha256",hash(upstream.resolve("result.vcf.gz")));session.put("canonical_output_sha256",hash(directory.resolve("genotypes.vcf")));
                    warnings.add("No biological accuracy, posterior calibration, reference harmonization or relatedness certification is inferred. Review the declared assembly, exact REF/ALT/map, reference provenance, and sample/marker ledgers. FILTER=PASS in an original Beagle output is not a new variant-QC certification; no imputation-quality threshold is applied or borrowed from another paper.");
                    warnings.add("Posterior DS/AP/GP values retain original two-decimal strings, including 0 and 1. Native interval checks include half a hundredth of rounding uncertainty and do not renormalize probabilities. Phased GT is preserved as reported; it need not equal the largest rounded unphased GP. Required action: choose the intended dosage/call statistic explicitly and review variant-results.tsv before downstream analysis.");
                    if(phase.equals("allow-rephase"))warnings.add("The explicit allow-rephase policy permits changes to provided phase; "+counts.providedPhaseChanges()+" observed phased allele orders changed. Unordered observed diploid genotypes were checked separately. Required action: review phase changes before IBD/haplotype analyses; use preserve-provided with compatible inputs when existing phase must be retained.");
                    if(prepared.excludedTargetMarkers()>0)warnings.add(prepared.excludedTargetMarkers()+" target-only markers were excluded under explicit drop-with-warning policy. Required action: review every EXPLICIT_TARGET_ONLY_EXCLUSION in variant-ledger.tsv; this is not a complete original target-marker result.");
                    if(reference&&prepared.target().samples().stream().anyMatch(new java.util.HashSet<>(prepared.reference().samples())::contains))warnings.add("Target/reference sample names overlap under explicit allow-with-warning policy. Required action: review panel provenance and possible self-inclusion; distinct names alone would not establish distinct people either.");
                    Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));manifest.put("analysis_status","passed-with-review-warnings");status(directory,"passed-with-review-warnings");Files.writeString(directory.resolve("WARNINGS.md"),"# BEAGLE — REVIEW BEFORE USE\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                } catch(Exception failure) {
                    code[0]=failure instanceof BeagleRuntime.Unavailable?3:2;String state=code[0]==3?"skipped-dependency-unavailable":"failed";manifest.put("analysis_status",state);manifest.put("error",failure.toString());session.put("error",failure.toString());
                    for(String name:List.of("genotypes.vcf","variant-results.tsv"))if(Files.exists(directory.resolve(name))){Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve(name),directory.resolve("partial").resolve(name));}
                    status(directory,state);Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(directory.resolve("WARNINGS.md"),"# BEAGLE "+(code[0]==3?"SKIPPED":"FAILED — DO NOT USE")+"\n\n"+failure+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                }
            });
            if(code[0]!=0)error.println("BEAGLE "+(code[0]==3?"SKIPPED":"FAILED")+": see "+Path.of(o.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");else{output.println("Original Beagle completed: "+o.get("out"));error.println("BEAGLE WARNINGS: read "+Path.of(o.get("out")).resolve("WARNINGS.md")+" before use.");}return code[0];
        }catch(Exception failure){error.println("BEAGLE NOT COMPLETED: "+failure.getMessage()+". "+RECOVERY+" Review beagle-tools --help.");return 2;}
    }
    private static Parsed parse(String[] args)throws Exception {
        Map<String,String> direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return new Parsed(Map.copyOf(direct),null);
        Path path=Path.of(direct.get("options-file"));if(!Files.isRegularFile(path)||Files.size(path)>65536)throw new IOException("Beagle recipe is missing or exceeds64KiB");byte[] raw=Files.readAllBytes(path);
        Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(value instanceof Map<?,?> recipe))throw new IOException("Beagle recipe must be an object of option:string values");
        var combined=new LinkedHashMap<String,String>();for(var e:recipe.entrySet()){String key=String.valueOf(e.getKey());if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string Beagle recipe option: "+key);combined.put(key,text);}combined.putAll(direct);
        String recipeHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
        if(!recipeHash.equals(hash(path)))throw new IOException("Beagle recipe changed while being read");
        return new Parsed(Map.copyOf(combined),recipeHash);
    }
    private static void verifyRecipe(Parsed p)throws IOException {if(p.recipeHash()!=null&&!p.recipeHash().equals(hash(Path.of(p.options().get("options-file")))))throw new IOException("Beagle recipe changed during analysis");}
    private static void snapshot(Parsed p,Path directory)throws IOException {verifyRecipe(p);if(p.recipeHash()!=null){Files.copy(Path.of(p.options().get("options-file")),directory.resolve("options-input.json"));if(!p.recipeHash().equals(hash(directory.resolve("options-input.json"))))throw new IOException("Beagle recipe snapshot differs");}}
    private static void status(Path out,String text)throws IOException {Files.writeString(out.resolve("analysis-status.json"),"{\"status\":\""+text+"\"}\n");}
    private static String choice(Map<String,String> o,String key,Set<String> allowed){String v=required(o,key);if(!allowed.contains(v))throw new IllegalArgumentException("--"+key+" must be one of "+allowed);return v;}
    private static double number(String v,String label){double d=Double.parseDouble(v);if(!Double.isFinite(d))throw new IllegalArgumentException("--"+label+" must be finite");return d;}
    private static long positive(Map<String,String> o,String key,Long fallback){long n=Long.parseLong(o.containsKey(key)?o.get(key):fallback==null?required(o,key):fallback.toString());if(n<1)throw new IllegalArgumentException("--"+key+" must be positive");return n;}
    private static int integer(Map<String,String> o,String key,Integer fallback){return Math.toIntExact(positive(o,key,fallback==null?null:fallback.longValue()));}
    private static long mib(Map<String,String> o,String key,long fallback){return Math.multiplyExact(positive(o,key,fallback),1048576);}
    static String help(){return """
        beagle-tools --method phase|impute --profile EXACT_PROFILE --home SEALED_HOME
          --input TARGET.vcf[.gz] --map EXPLICIT_CM_MAP --assembly DECLARED_LABEL
          --out NEW_DIRECTORY --phase-policy preserve-provided|allow-rephase
          --minimum-observed-markers INTEGER --seed SIGNED_INTEGER --iterations INTEGER
          --ne EFFECTIVE_POPULATION_SIZE --err MISMATCH_PROBABILITY
          --window-unit markers|cm --window-size SIZE --overlap-size SIZE
        Profiles: 5.4-29Oct24, 5.1-18May20, 4.1-08Jun17, 4.1-21Jan17.
        Impute requires --reference; phase optionally accepts one. Reference workflows
        require --target-only-markers error|drop-with-warning and --sample-overlap
        error|allow-with-warning. No map, allele flip, QC/ploidy/model choice is inferred.
        4.1 windows/overlap are marker integers; 5.x uses cM and requires --burnin and
        --phase-states. Impute 5.x also requires --imp-states. 5.4 requires --em true|false;
        other profiles reject em. No GL/PL/IBD or automatic reference download.
        Single chromosome, resolved biallelic diploid GT; reference fully phased/nonmissing.
        Target genotype calls are streamed to a bounded on-disk store;native checks
        preserve observed dosages and explicit phase policy. Modern partially missing
        provided phase cannot be guaranteed preserved; choose compatible inputs or
        explicitly allow rephasing. GT is never reconstructed from rounded GP.
        --java defaults to current JVM; --threads defaults 1, --memory-mib 2048 (heap),
        --stack-mib 5, --timeout-seconds 3600. Resource ceilings apply independently;
        Java application heap/native/OS overhead is additional to upstream heap.
        UTF-8 JSON --options-file accepts string values; direct flags override it.
        Read analysis-status.json, WARNINGS.md, session.yaml and ledgers before use.
        Missing/changed/forbidden dependency => skip exit 3; input/process/native-check
        failure => exit 2. Failed canonical files go to partial/. Analysis never installs.
        """;}
}
