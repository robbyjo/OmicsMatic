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
import org.jlinalg.genetics.PrsCsMath;

/** Original, explicitly selected PRS-CS posterior inference with native validation. */
final class PrsCsToolsCli {
    private PrsCsToolsCli() { }
    private static final Set<String> PROFILES=Set.of("source-fbc9ec0","1.1.0","1.0.0");
    private static final Set<String> ALLOWED=Set.of("profile","home","legacy-use","summary","target-bim","reference","reference-kind","chromosomes","format","trait-kind","sample-size","sample-size-kind","gwas-population","ld-population","assembly","a","b","phi","iterations","burnin","thin","seed","effect-units","posterior","write-psi","score-export","unmatched","palindromic","strand","positions","p-floor","minimum-model-variants","ld-tolerance","threads","timeout-seconds","out","maximum-model-variants","maximum-input-rows","maximum-input-mib","maximum-retained-mib","maximum-line-bytes","maximum-ld-cells","maximum-block-snps","maximum-blocks","maximum-report-mib","maximum-posterior-cells","options-file");
    private static final List<String> PRODUCTS=List.of("posterior-means.tsv","posterior-draws.tsv","shrinkage.tsv","score-weights.tsv");
    private static final String RECOVERY="Required action: inspect WARNINGS.md, analysis-status.json, session.yaml, variant/input ledgers, native-marginals.tsv, LD validation and retained original logs. Correct the named data/model/policy/resource choice and rerun to a new output directory. Supply your own documented matching-build LD panel; no panel, ancestry, effective sample size, tuning set, overlap removal, allele repair or clinical calibration is inferred."+PrsCsRuntime.setup();
    private record Parsed(Map<String,String> options,String recipeHash) { }

    static int run(String[] args,PrintStream output,PrintStream error) {
        if(args.length==1&&args[0].equals("--help")){output.println(help());return 0;}
        try {
            Parsed parsed=parse(args);Map<String,String> o=parsed.options();
            for(String key:List.of("summary","target-bim","reference","assembly","gwas-population","ld-population","out"))label(required(o,key),key);
            String profile=choice(o,"profile",PROFILES),kind=choice(o,"reference-kind",Set.of("1kg","ukbb")),format=choice(o,"format",Set.of("beta-se","or-se","beta-p","or-p"));
            String trait=choice(o,"trait-kind",Set.of("quantitative","binary")),sizeKind=choice(o,"sample-size-kind",Set.of("total","effective"));
            if(trait.equals("quantitative")&&sizeKind.equals("effective")||format.startsWith("or")&&!trait.equals("binary"))throw new IllegalArgumentException("Quantitative traits require total N;OR formats require a binary trait and SE refers to log(OR)");
            if(!profile.equals("source-fbc9ec0"))choice(o,"legacy-use",Set.of("offline-reproduction"));else if(o.containsKey("legacy-use"))throw new IllegalArgumentException("--legacy-use applies only to explicitly selected historical profiles");
            if(profile.equals("1.0.0")&&format.endsWith("-se"))throw new IllegalArgumentException("Original1.0.0 has no SE format;select the intended compatible source or supply the intended P-value model");
            String units=choice(o,"effect-units",Set.of("per-allele","standardized")),posterior=choice(o,"posterior",Set.of("mean","draws","both"));boolean psi=bool(o,"write-psi");
            if(!profile.equals("source-fbc9ec0")&&!posterior.equals("mean"))throw new IllegalArgumentException("Only source-fbc9ec0 supports retained posterior draws");
            if(profile.equals("1.0.0")&&psi)throw new IllegalArgumentException("Original1.0.0 does not support psi output");
            String score=o.getOrDefault("score-export","none");if(!Set.of("none","per-allele").contains(score)||score.equals("per-allele")&&posterior.equals("draws"))throw new IllegalArgumentException("--score-export is none or per-allele;per-allele export requires posterior mean or both");
            String unmatched=choice(o,"unmatched",Set.of("error","exclude-with-warning")),pal=choice(o,"palindromic",Set.of("error","exclude-with-warning","allow-upstream-with-warning")),strand=choice(o,"strand",Set.of("exact","allow-complement-with-warning")),positions=choice(o,"positions",Set.of("require-match","reference-with-warning"));
            String floor=choice(o,"p-floor",Set.of("error","original-floor-with-warning"));if(format.endsWith("-se")&&!floor.equals("error"))throw new IllegalArgumentException("P-value flooring is unused for SE formats;choose --p-floor error");
            PrsCsMath.PValuePolicy pPolicy=floor.equals("error")?PrsCsMath.PValuePolicy.ERROR:PrsCsMath.PValuePolicy.ORIGINAL_FLOOR_WITH_WARNING;
            List<Integer> chromosomes=chromosomes(required(o,"chromosomes"));long n=positive(o,"sample-size",null),seed=Long.parseLong(required(o,"seed"));
            if(n>9_007_199_254_740_991L||seed<0||seed>4294967295L)throw new IllegalArgumentException("Require exact integer N<=2^53-1 and seed in [0,2^32-1]");
            int iterations=integer(o,"iterations",null),burnin=Integer.parseInt(required(o,"burnin")),thin=integer(o,"thin",null),retained=PrsCsMath.retainedSamples(iterations,burnin,thin);
            int minimum=integer(o,"minimum-model-variants",null),threads=integer(o,"threads",1),timeout=integer(o,"timeout-seconds",3600);
            if(iterations>1000000||threads>256||timeout>86400)throw new IllegalArgumentException("Require iterations<=1000000,threads<=256,timeout<=86400");
            double a=positiveNumber(required(o,"a"),"a"),b=positiveNumber(required(o,"b"),"b");String phi=required(o,"phi");if(!phi.equals("auto"))positiveNumber(phi,"phi");
            double tolerance=number(o.getOrDefault("ld-tolerance","1e-6"),"ld-tolerance");if(tolerance<0||tolerance>.01)throw new IllegalArgumentException("Require --ld-tolerance in [0,0.01]");
            var limits=new PrsCsInputs.Limits(integer(o,"maximum-model-variants",500000),positive(o,"maximum-input-rows",20000000L),mib(o,"maximum-input-mib",4096),mib(o,"maximum-retained-mib",2048),integer(o,"maximum-line-bytes",1048576),positive(o,"maximum-ld-cells",100000000L),integer(o,"maximum-block-snps",10000),integer(o,"maximum-blocks",10000),mib(o,"maximum-report-mib",4096),positive(o,"maximum-posterior-cells",100000000L));
            if(!posterior.equals("mean")&&Math.addExact(128L,24L*retained)>limits.lineBytes())throw new IllegalArgumentException("Retained draw width exceeds declared line-byte audit bound;review schedule or --maximum-line-bytes");
            Path summary=Path.of(o.get("summary")).toAbsolutePath().normalize(),bim=Path.of(o.get("target-bim")).toAbsolutePath().normalize(),reference=Path.of(o.get("reference")).toAbsolutePath().normalize();
            String home=o.getOrDefault("home",System.getenv("JLINALG_PRSCS_HOME"));disjoint(o,home);int[] code={0};
            externalTransaction(o,"prs-cs-original",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("requested_options",o);session.put("exact_model_parameters",Map.of("a",a,"b",b,"phi",phi,"sample_size",n,"iterations",iterations,"burnin",burnin,"thin",thin,"retained_per_chromosome",retained,"seed",seed));
                session.put("scope","Original single-population PRS-CS posterior inference from explicit summary statistics,target BIM and supplied autosomal LD. No PRS-CSx,joint cross-population model,LD construction,association testing,hyperparameter selection,relatedness/leakage control,convergence/accuracy certification or absolute-risk calibration. The original seed restarts for each chromosome. Assembly and population labels are declarations.");
                if(!profile.equals("source-fbc9ec0"))warnings.add("UNSUPPORTED LEGACY PYTHON: this explicitly selected historical source uses Python3.10.11,which reached end-of-life on 2026-10-01. Required action: keep this runtime for offline historical reproduction;select current source/CPython3.12 for new analyses. Scientific source is unmodified.");
                try {
                    if(home==null||home.isBlank())throw new PrsCsRuntime.Unavailable("No --home or JLINALG_PRSCS_HOME supplied",null);
                    Path runtimeWork=directory.resolve("runtime"),stage=directory.resolve("inputs"),audit=directory.resolve("audit");Files.createDirectory(runtimeWork);Files.createDirectory(stage);Files.createDirectory(audit);
                    var runtime=new PrsCsRuntime(Path.of(home),profile,runtimeWork,session);
                    if(format.endsWith("-se")&&!runtime.supportsSe()||psi&&!runtime.supportsPsi()||!posterior.equals("mean")&&!runtime.supportsDraws())throw new PrsCsRuntime.Unavailable("Selected source capabilities differ from validated option contract",null);
                    var input=PrsCsInputs.prepare(new PrsCsInputs.Request(summary,bim,reference,kind,chromosomes,format,n,unmatched,pal,strand,positions,pPolicy),limits,stage);warnings.addAll(input.warnings());
                    manifest.put("input_sha256",input.sourceHashes());session.put("input_paths",input.sources().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().toString())));session.put("staged_sha256",input.stagedHashes());session.put("input_counts",input.counts());session.put("reference_family_estimated_retained_bytes",input.estimatedRetained());
                    for(var entry:input.model().entrySet())if(entry.getValue().size()<minimum)throw new IOException("Chromosome "+entry.getKey()+" has fewer than the explicitly required minimum model variants");
                    long posteriorCells=Math.multiplyExact((long)input.byId().size(),posterior.equals("mean")?1:posterior.equals("draws")?retained:retained+1L);
                    if(posteriorCells>limits.posteriorCells())throw new IOException("Expected posterior scalar count exceeds --maximum-posterior-cells");session.put("expected_posterior_scalars",posteriorCells);
                    if(!posterior.equals("mean"))for(var v:input.byId().values())if(v.id().getBytes(StandardCharsets.UTF_8).length+128L+24L*retained>limits.lineBytes())throw new IOException("Retained draw row plus model SNP ID exceeds declared line-byte audit bound");
                    for(String name:List.of("variant-ledger.tsv","reference-input-ledger.tsv","target-input-ledger.tsv","summary-input-ledger.tsv","native-marginals.tsv"))Files.copy(stage.resolve(name),directory.resolve(name));snapshot(parsed,directory);
                    var ld=PrsCsAudits.hdf5(runtime,input,audit,tolerance,timeout,threads);session.put("ld_validation",Map.of("source_blocks",ld.blocks(),"source_snps",ld.sourceSnps(),"source_cells",ld.sourceCells(),"selected_cells",ld.selectedCells(),"native_numeric_checks",ld.numericChecks(),"conservative_work_allowance_bytes",ld.estimatedWorkBytes()));
                    PrsCsAudits.originalParser(runtime,input,stage,audit,timeout,threads);session.put("original_marginal_audit_sha256",hash(audit.resolve("original-marginals.tsv")));Files.copy(audit.resolve("ld-validation.tsv"),directory.resolve("ld-validation.tsv"));
                    var hdfWarnings=PrsCsOutputs.warnings(audit.resolve("hdf5.stderr"),limits.lineBytes(),runtime.legacy());var parserWarnings=PrsCsOutputs.warnings(audit.resolve("original-parser.stderr"),limits.lineBytes(),runtime.legacy());
                    long existing=bytes(directory),rowBytes=0;for(var v:input.byId().values())rowBytes=Math.addExact(rowBytes,256L+8L*v.id().length());
                    long predicted=Math.addExact(existing,Math.addExact(1048576L,Math.addExact(3L*rowBytes,Math.multiplyExact(posteriorCells,64L))));
                    session.put("conservative_report_bytes_before_fit",predicted);if(predicted>limits.reportBytes())throw new IOException("Expected original/canonical posterior reports exceed --maximum-report-mib");
                    Path mean=null,draws=null;int numpyWarnings=hdfWarnings.numpyArrayToScalar()+parserWarnings.numpyArrayToScalar(),scipyWarnings=hdfWarnings.scipyAliases()+parserWarnings.scipyAliases();
                    for(String mode:posterior.equals("both")?List.of("mean","draws"):List.of(posterior)) {
                        Path work=directory.resolve("upstream-"+mode);Files.createDirectory(work);if(mode.equals("mean"))mean=work;else draws=work;
                        var command=runtime.original(work,"analysis");command.addAll(List.of("--ref_dir="+reference,"--bim_prefix="+stage.resolve("target"),"--sst_file="+stage.resolve("summary.txt"),"--n_gwas="+n,"--out_dir="+work.resolve("result"),"--a="+a,"--b="+b,"--n_iter="+iterations,"--n_burnin="+burnin,"--thin="+thin,"--chrom="+String.join(",",chromosomes.stream().map(Object::toString).toList()),"--seed="+seed,"--beta_std="+runtime.bool(units.equals("standardized"))));
                        if(!phi.equals("auto"))command.add("--phi="+phi);if(runtime.supportsPsi())command.add("--write_psi="+runtime.bool(psi));if(runtime.supportsDraws())command.add("--write_pst="+runtime.bool(mode.equals("draws")));
                        String processStage="mcmc-"+mode;runtime.execute(command,work,processStage,timeout,threads);var observed=PrsCsOutputs.warnings(work.resolve(processStage+".stderr"),limits.lineBytes(),runtime.legacy());numpyWarnings+=observed.numpyArrayToScalar();scipyWarnings+=observed.scipyAliases();PrsCsInputs.verify(input,stage);PrsCsAudits.verifyBridge(ld,audit);
                    }
                    long remaining=limits.reportBytes()-bytes(directory)-1048576;var result=PrsCsOutputs.validate(input,mean,draws,psi,retained,units,score.equals("per-allele"),directory,remaining);
                    session.put("posterior_validation",Map.of("variants",result.variants(),"posterior_scalars",result.posteriorScalars(),"psi_fields",result.psiFields(),"same_seed_mean_draw_checks",result.meanDrawChecks()));session.put("original_output_sha256",result.originalHashes());
                    if(result.posteriorScalars()!=posteriorCells)throw new IOException("Validated posterior scalar count differs from explicit preflight family");
                    session.put("reviewed_upstream_deprecation_counts",Map.of("numpy_array_to_scalar",numpyWarnings,"legacy_scipy_aliases",scipyWarnings));
                    if(numpyWarnings>0)warnings.add(numpyWarnings+" known NumPy array-to-scalar DeprecationWarnings were retained from the unmodified current source. Required action: preserve this pinned compatible environment and review original stderr;an in-place NumPy upgrade can turn this source behavior into an error. No warning was suppressed or source patched.");
                    if(scipyWarnings>0)warnings.add(scipyWarnings+" known SciPy1.8 NumPy-alias DeprecationWarnings were retained from the unmodified historical parser/sampler. Required action: preserve the pinned offline reproduction environment and review original stderr;newer SciPy removes these aliases. Unknown or numerical warnings still fail validation. No warning was suppressed or scientific source patched.");
                    if(trait.equals("binary")&&sizeKind.equals("total"))warnings.add("Binary-trait total N was explicitly selected. Required action: verify that this is the intended PRS-CS approximation for the study's case/control balance;effective N is not inferred or substituted.");
                    if(!o.get("gwas-population").equals(o.get("ld-population")))warnings.add("Declared GWAS and LD population labels differ. Required action: review panel/study compatibility and any mismatch sensitivity;labels do not establish actual ancestry or compatibility.");
                    warnings.add("MCMC completion and native file/arithmetic checks do not establish convergence,posterior calibration,predictive performance or clinical validity. Required action: choose a scientifically justified sampling schedule and assess chain stability/replication,training-target overlap and independent evaluation. Short fixture schedules are tests only. Multi-population combinations and hyperparameter tuning require separately designed validation.");
                    warnings.add("Selected raw LD passed finite correlation-range,unit-diagonal and symmetry checks at tolerance "+tolerance+". The original source applies allele signs and its SVD-based adjustment;no general PSD certification or LD repair is supplied by Java. Required action: review panel provenance,variant order and ld-validation.tsv. Resource ceilings are conservative work/storage guards and do not guarantee total process RAM.");
                    warnings.add("Original posterior values retain seven significant digits;exact a/b/phi choices are in session.yaml because upstream filenames round their parameter labels. "+(score.equals("per-allele")?"score-weights.tsv uses the posterior mean,with explicit reference-frequency conversion if standardized units were requested. Frequencies are from the supplied LD panel and are not target-cohort estimates. Required action: choose downstream missing-genotype handling and evaluation deliberately.":"No genotype score or absolute-risk estimate is produced."));
                    runtime.verify();PrsCsInputs.verify(input,stage);PrsCsAudits.verifyBridge(ld,audit);PrsCsOutputs.verify(result,directory);verifyRecipe(parsed);
                    if(bytes(directory)+1048576>limits.reportBytes())throw new IOException("Final PRS-CS report exceeds its byte ceiling");session.put("analysis_status","passed-with-review-warnings");manifest.put("analysis_status","passed-with-review-warnings");status(directory,"passed-with-review-warnings");Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(directory.resolve("WARNINGS.md"),"# PRS-CS — REVIEW BEFORE USE\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                }catch(Exception failure) {
                    code[0]=failure instanceof PrsCsRuntime.Unavailable?3:2;String state=code[0]==3?"skipped-dependency-unavailable":"failed";manifest.put("analysis_status",state);manifest.put("error",failure.toString());session.put("error",failure.toString());session.put("analysis_status",state);
                    for(String name:PRODUCTS)if(Files.exists(directory.resolve(name))){Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve(name),directory.resolve("partial").resolve(name));}
                    status(directory,state);Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(directory.resolve("WARNINGS.md"),"# PRS-CS "+(code[0]==3?"SKIPPED":"FAILED — DO NOT USE")+"\n\n"+failure.toString().replace(directory.toString(),"<bundle>")+"\n\nRetained log paths use <bundle> for this published output directory;session commands retain the original staging paths.\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                }
            });
            if(code[0]!=0)error.println("PRS-CS "+(code[0]==3?"SKIPPED":"FAILED")+": see "+Path.of(o.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");
            else {output.println("Original PRS-CS completed: "+o.get("out"));error.println("PRS-CS WARNINGS: read "+Path.of(o.get("out")).resolve("WARNINGS.md")+" before use.");}return code[0];
        }catch(Exception failure){error.println("PRS-CS NOT COMPLETED: "+failure.getMessage()+". "+RECOVERY+" Review prs-cs-tools --help.");return 2;}
    }
    private static Parsed parse(String[] args)throws Exception {
        Map<String,String> direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return new Parsed(Map.copyOf(direct),null);Path path=Path.of(direct.get("options-file"));
        byte[] raw;try(var stream=Files.newInputStream(path)){raw=stream.readNBytes(1048577);}if(raw.length>1048576)throw new IOException("PRS-CS options file exceeds1MiB");
        Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(value instanceof Map<?,?> values))throw new IOException("PRS-CS options file requires an object of option:string values");
        var merged=new LinkedHashMap<String,String>();for(var e:values.entrySet()){String key=String.valueOf(e.getKey());if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string PRS-CS recipe option: "+key);merged.put(key,text);}merged.putAll(direct);
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));if(!digest.equals(hash(path)))throw new IOException("PRS-CS recipe changed while reading");return new Parsed(Map.copyOf(merged),digest);
    }
    private static void verifyRecipe(Parsed p)throws IOException {if(p.recipeHash()!=null&&!p.recipeHash().equals(hash(Path.of(p.options().get("options-file")))))throw new IOException("PRS-CS options file changed during analysis");}
    private static void snapshot(Parsed p,Path out)throws IOException {verifyRecipe(p);if(p.recipeHash()!=null){Files.copy(Path.of(p.options().get("options-file")),out.resolve("options-input.json"));if(!p.recipeHash().equals(hash(out.resolve("options-input.json"))))throw new IOException("PRS-CS recipe snapshot differs");}}
    private static void disjoint(Map<String,String> o,String home)throws IOException {
        Path destination=PlinkToolsCli.resolved(Path.of(o.get("out")));
        for(String key:List.of("reference","summary","target-bim","options-file","home")) {
            String value=key.equals("home")?home:o.get(key);if(value==null||value.isBlank())continue;Path source=PlinkToolsCli.resolved(Path.of(value));
            if(source.startsWith(destination)||destination.startsWith(source))throw new IOException("PRS-CS output overlaps "+key+" or its ancestors");
        }
    }
    private static List<Integer> chromosomes(String value) {
        var result=new ArrayList<Integer>();for(String item:value.split(",",-1)){int c=Integer.parseInt(item);if(c<1||c>22||!item.equals(Integer.toString(c))||result.contains(c))throw new IllegalArgumentException("Declare unique autosomal chromosome integers1..22 without padding");result.add(c);}return List.copyOf(result);
    }
    private static void label(String value,String key){if(value.isBlank()||value.length()>8192||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid/oversized --"+key);}
    private static boolean bool(Map<String,String> o,String key){String value=choice(o,key,Set.of("true","false"));return Boolean.parseBoolean(value);}
    private static String choice(Map<String,String> o,String key,Set<String> allowed){String v=required(o,key);if(!allowed.contains(v))throw new IllegalArgumentException("--"+key+" must be one of "+allowed);return v;}
    private static double number(String value,String key){double n=Double.parseDouble(value);if(!Double.isFinite(n))throw new IllegalArgumentException("Require finite --"+key);return n;}
    private static double positiveNumber(String value,String key){double n=number(value,key);if(n<=0)throw new IllegalArgumentException("Require positive --"+key);return n;}
    private static long positive(Map<String,String> o,String key,Long fallback){long n=Long.parseLong(o.containsKey(key)?o.get(key):fallback==null?required(o,key):fallback.toString());if(n<1)throw new IllegalArgumentException("Require positive --"+key);return n;}
    private static int integer(Map<String,String> o,String key,Integer fallback){return Math.toIntExact(positive(o,key,fallback==null?null:fallback.longValue()));}
    private static long mib(Map<String,String> o,String key,long fallback){return Math.multiplyExact(1048576,positive(o,key,fallback));}
    private static long bytes(Path directory)throws IOException {long result=0;try(var paths=Files.walk(directory)){for(Path file:paths.filter(Files::isRegularFile).toList())result=Math.addExact(result,Files.size(file));}return result;}
    private static void status(Path out,String state)throws IOException {Files.writeString(out.resolve("analysis-status.json"),"{\"status\":\""+state+"\"}\n");}
    static String help(){return """
        prs-cs-tools --profile source-fbc9ec0|1.1.0|1.0.0 --home SEALED_HOME
          --summary SST[.gz] --target-bim TARGET.bim[.gz] --reference ORIGINAL_LD_DIR
          --reference-kind 1kg|ukbb --chromosomes COMMA_SEPARATED_AUTOSOMES
          --format beta-se|or-se|beta-p|or-p --trait-kind quantitative|binary
          --sample-size INTEGER --sample-size-kind total|effective
          --gwas-population DECLARED_LABEL --ld-population DECLARED_LABEL
          --assembly DECLARED_LABEL --a POSITIVE --b POSITIVE --phi auto|POSITIVE
          --iterations INTEGER --burnin INTEGER --thin INTEGER --seed UINT32
          --effect-units per-allele|standardized --posterior mean|draws|both
          --write-psi true|false --minimum-model-variants INTEGER --out NEW_DIRECTORY
          --unmatched error|exclude-with-warning
          --palindromic error|exclude-with-warning|allow-upstream-with-warning
          --strand exact|allow-complement-with-warning
          --positions require-match|reference-with-warning
          --p-floor error|original-floor-with-warning
        Historical profiles require --legacy-use offline-reproduction (EOL Python).
        1.0.0 accepts P formats only,without psi/draws;1.1.0 has no draws.
        SE is SE(beta) or SE(logOR). A single declared N applies to all variants.
        Integral (iterations-burnin)/thin must equal the actual retained count.
        Both posterior mode runs original mean/draw inference separately with the
        same seed;their printed intervals and psi are checked. No tuning is inferred.
        Reference basename must contain declared lowercase 1kg/ukbb;original
        snpinfo/HDF5 files remain read-only. Java checks model order and raw LD,
        then compares marginal arithmetic with unmodified upstream parsers.
        --score-export none(default)|per-allele explicitly exports mean weights
        for genotype-score;standardized weights use supplied LD-panel frequencies.
        --threads default1,--timeout-seconds3600,--ld-tolerance1e-6.
        Ceilings: --maximum-model-variants500000,--maximum-input-rows20000000,
        --maximum-input-mib4096,--maximum-retained-mib2048,
        --maximum-line-bytes1048576,--maximum-ld-cells100000000,
        --maximum-block-snps10000,--maximum-blocks10000,
        --maximum-report-mib4096,--maximum-posterior-cells100000000.
        Full requested source LD counts toward conservative memory/work guards;
        use explicit per-chromosome runs or reviewed larger ceilings when needed.
        These bounds do not guarantee total process RAM. No panel is downloaded.
        UTF-8 JSON --options-file accepts strings;direct flags override it.
        Analysis never installs. Missing/changed/forbidden dependency skips(exit3);
        invalid inputs/process/validation fail(exit2). Canonical partials are
        quarantined under partial/. Always read WARNINGS.md and session.yaml.
        """;}
}
