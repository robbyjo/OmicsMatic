/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.MinimacReferenceBlocks;

/** Closed, source-version-specific recipes and immutable snapshots for typed Minimac workflows. */
final class MinimacRecipe {
    private MinimacRecipe() { }
    static final String CURRENT = "linux-x86_64-minimac4-4.1.6";
    static final String LEGACY102 = "linux-x86_64-minimac4-1.0.2-qemu8.2.2";
    static final String LEGACY100 = "linux-x86_64-minimac4-1.0.0-private-gomp14";
    static final String MIN3 = "linux-x86_64-minimac3-2.0.1";
    static final class UnavailableCapability extends IOException {
        private static final long serialVersionUID = 1L;
        UnavailableCapability(String message) { super(message); }
    }
    record Input(Path data, Path identities, String kind, String staging, String columns) { }
    record Region(String contig, int start, int end, String scope, String review) { }
    record Limits(MinimacVcf.Limits vcf, long retainedBytes, long workspaceBytes, int outputLineCharacters) { }
    record Qc(String kind, String operator, String threshold, String missing) { }
    record Rule(String flag, double minimum, double maximum, boolean integer, boolean booleanFlag) { }
    record Engine(int threads, String parameterPolicy, Map<String,Object> overrides, List<String> arguments) {
        Engine { overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides)); arguments = List.copyOf(arguments); }
    }
    record Spec(Path recipe, String recipeSha256, Path root, String operation, String profile, String assembly,
            Input reference, Input target, Region region, MinimacReferenceBlocks.BoundaryPolicy boundary,
            MinimacVcf.PhasePolicy phase, MinimacVcf.MissingPolicy targetMissing, String targetOnly,
            String sampleOverlap, String idPolicy, String observedCallChanges, String providedPhaseChanges,
            Engine engine, Qc qc, Limits limits, Map<String,Object> source) {
        boolean modern() { return profile.equals(CURRENT); }
        boolean hasTarget() { return target != null; }
    }
    record Snapshot(Map<String,Path> files, Map<Path,String> originalSha256, Map<Path,String> stagedSha256) {
        Snapshot { files = Map.copyOf(files); originalSha256 = Map.copyOf(originalSha256); stagedSha256 = Map.copyOf(stagedSha256); }
        void verify() throws IOException {
            for (var values : List.of(originalSha256, stagedSha256))
                for (var entry : values.entrySet()) {
                    MinimacVcf.regular(entry.getKey(), 1000000000000L);
                    if (!FollowupSupport.hash(entry.getKey()).equals(entry.getValue()))
                        throw new IOException("Minimac input/snapshot changed: " + entry.getKey());
                }
        }
    }

    static Spec read(Path supplied) throws IOException {
        Path recipe = supplied.toAbsolutePath().normalize();
        byte[] bytes = raw(recipe, 2097152);
        String hash = AdmixtureInputs.digest(bytes);
        Map<?,?> m = object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()));
        keys(m, Set.of("schema_version","operation","profile","assembly","resource_root","reference","target",
                "region","input_review","boundary_policy","phase_policy","target_missing_policy",
                "target_only_markers","sample_overlap","id_policy","observed_call_changes","provided_phase_changes",
                "engine","post_imputation_qc","limits"));
        integer(m, "schema_version", 1, 1);
        String operation = choice(m,"operation",Set.of("prepare-reference","convert-reference","inspect-reference","impute","check"));
        String profile = choice(m,"profile",Set.of(CURRENT,LEGACY102,LEGACY100,MIN3));
        String assembly = text(m,"assembly");
        Path root = recipe.getParent().resolve(text(m,"resource_root")).toAbsolutePath().normalize();
        if (!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
            throw new IOException("Minimac resource_root must be an existing real directory without linked ancestors");
        Input reference = input(root, object(m.get("reference")));
        boolean targetOperation = operation.equals("impute") || operation.equals("check");
        Input target = targetOperation ? input(root, object(m.get("target"))) : null;
        if (target != null && !target.kind.equals("phased-vcf")) throw new IOException("Target requires explicitly phased VCF GT");
        if (!targetOperation && m.containsKey("target")) throw new IOException("Target data is only valid for impute/check");
        if (operation.equals("prepare-reference") && !reference.kind.equals("phased-vcf"))
            throw new IOException("prepare-reference requires phased-vcf input");
        if (operation.equals("convert-reference") && !reference.kind.equals("m3vcf"))
            throw new IOException("Original --update-m3vcf conversion requires an M3VCF input");
        if (targetOperation && !(profile.equals(CURRENT) ? reference.kind.equals("msav") : reference.kind.equals("m3vcf")))
            throw new IOException("Current imputation/check requires MSAV; historical profiles require M3VCF. Prepare/convert explicitly first");
        Map<?,?> r = object(m.get("region")); keys(r,Set.of("contig","start","end","scope","review"));
        String contig = text(r,"contig");
        if (contig.codePoints().anyMatch(Character::isWhitespace) || contig.matches(".*[,<>=\"\\\\].*"))
            throw new IOException("Invalid exact region contig");
        int start = (int)integer(r,"start",1,Integer.MAX_VALUE), end = (int)integer(r,"end",start,Integer.MAX_VALUE);
        Region region = new Region(contig,start,end,choice(r,"scope",Set.of("autosomal","PAR","nonPAR","other")),text(r,"review"));
        Map<?,?> review = object(m.get("input_review"));
        Set<String> reviewKeys = Set.of("organism","reference_panel","reference_sample_eligibility","target_sample_eligibility",
                "assembly_region_ploidy","phase","allele_harmonization","variant_normalization");
        keys(review,reviewKeys); for (String key : reviewKeys) text(review,key);
        MinimacReferenceBlocks.BoundaryPolicy boundary;
        try { boundary = MinimacReferenceBlocks.BoundaryPolicy.valueOf(text(m,"boundary_policy")); }
        catch (IllegalArgumentException failure) { throw new IOException("Require explicit supported compressed-reference boundary policy",failure); }
        if (targetOperation && !profile.equals(CURRENT) && boundary != MinimacReferenceBlocks.BoundaryPolicy.REQUIRE_SHARED_KEEP_LATER)
            throw new IOException("Historical imputation must require shared same-contig block boundaries; its reader can otherwise omit markers");
        MinimacVcf.PhasePolicy phase = choice(m,"phase_policy",Set.of("require-pipe","allow-unphased-homozygotes")).equals("require-pipe")
                ? MinimacVcf.PhasePolicy.REQUIRE_PIPE : MinimacVcf.PhasePolicy.ALLOW_UNPHASED_HOMOZYGOTES;
        MinimacVcf.MissingPolicy missing = null;
        String targetOnly = null, overlap = null, ids = text(m,"id_policy"), observed = null, provided = null;
        if (!Set.of("require-reference-id","retain-upstream-id-with-ledger").contains(ids)) throw new IOException("Unsupported explicit reference/output ID policy");
        if (targetOperation) {
            missing = choice(m,"target_missing_policy",Set.of("exact-phased","allow-full-missing-with-declared-ploidy")).equals("exact-phased")
                    ? MinimacVcf.MissingPolicy.EXACT_PHASED : MinimacVcf.MissingPolicy.ALLOW_FULL_MISSING_WITH_DECLARED_PLOIDY;
            targetOnly = choice(m,"target_only_markers",Set.of("error","exclude-with-ledger"));
            overlap = choice(m,"sample_overlap",Set.of("error","allow-with-warning"));
            observed = choice(m,"observed_call_changes",Set.of("error","retain-with-ledger"));
            provided = choice(m,"provided_phase_changes",Set.of("error","retain-with-ledger"));
        } else for (String key : List.of("target_missing_policy","target_only_markers","sample_overlap","observed_call_changes","provided_phase_changes"))
            if (m.containsKey(key)) throw new IOException("Target-only field for a reference operation: " + key);
        Map<?,?> l = object(m.get("limits"));
        keys(l,Set.of("samples","variants","genotype_cells","input_bytes","decoded_characters","line_characters",
                "identity_rows","retained_bytes","workspace_bytes","output_line_characters"));
        long workspace = integer(l,"workspace_bytes",1024,1000000000000L);
        var limits = new Limits(new MinimacVcf.Limits((int)integer(l,"samples",1,1000000),
                integer(l,"variants",1,1000000000L),integer(l,"genotype_cells",1,1000000000000L),
                integer(l,"input_bytes",1,1000000000000L),integer(l,"decoded_characters",1,1000000000000L),
                (int)integer(l,"line_characters",64,16777216),workspace,(int)integer(l,"identity_rows",1,Integer.MAX_VALUE)),
                integer(l,"retained_bytes",1024,1000000000000L),workspace,
                (int)(l.containsKey("output_line_characters")?integer(l,"output_line_characters",64,1073741824):integer(l,"line_characters",64,16777216)));
        Engine engine = engine(object(m.get("engine")), profile, operation);
        if (operation.equals("prepare-reference") && Set.of(LEGACY100,LEGACY102).contains(profile))
            throw new UnavailableCapability("Reference-only --estimate on the admitted historical Min4 profiles returned 255 with an empty GWAS-chromosome mismatch and no reference. SKIP this preparation route. Required action: supply a separately validated compatible reference or explicitly select an appropriate validated preparation profile; the requested imputation version is never substituted.");
        if (operation.equals("convert-reference") && !profile.equals(CURRENT))
            throw new UnavailableCapability("Original --update-m3vcf/MSAV conversion needs the explicit current Min4/Savvy profile. SKIP this unavailable historical conversion. Required action: explicitly choose a compatible validated conversion profile or supply a traceable supported reference; no version is substituted.");
        if (operation.equals("inspect-reference") && reference.kind.equals("msav") && !profile.equals(CURRENT))
            throw new UnavailableCapability("Historical Minimac profiles have no admitted MSAV exporter. SKIP MSAV inspection. Required action: supply a complete traceable text export or explicitly select the current Min4/Savvy inspection profile.");
        Qc qc = null;
        if (operation.equals("impute")) {
            Map<?,?> q = object(m.get("post_imputation_qc"));
            keys(q,Set.of("kind","operator","minimum","missing"));
            String kind = choice(q,"kind",Set.of("none","source-INFO-R2"));
            if (kind.equals("none")) {
                if (q.size()!=1) throw new IOException("QC none must not carry unused thresholds");
                qc = new Qc(kind,"none",null,"not-applicable");
            } else {
                String threshold = text(q,"minimum");
                try {
                    var decimal = new java.math.BigDecimal(threshold);
                    if (decimal.signum()<0 || decimal.compareTo(java.math.BigDecimal.ONE)>0)
                        throw new NumberFormatException("Outside [0,1]");
                } catch (NumberFormatException failure) { throw new IOException("Require exact decimal-text source-R2 minimum within [0,1]",failure); }
                qc = new Qc(kind,choice(q,"operator",Set.of("gt","ge")),threshold,
                        choice(q,"missing",Set.of("error","retain-with-warning","exclude-with-ledger")));
            }
        } else if (m.containsKey("post_imputation_qc")) throw new IOException("Post-imputation QC is only valid for impute");
        return new Spec(recipe,hash,root,operation,profile,assembly,reference,target,region,boundary,phase,missing,
                targetOnly,overlap,ids,observed,provided,engine,qc,limits,freezeObject(m));
    }

    private static Input input(Path root, Map<?,?> value) throws IOException {
        keys(value,Set.of("path","identity_ledger","kind","staging","column_semantics"));
        String kind = choice(value,"kind",Set.of("phased-vcf","m3vcf","mvcf","msav"));
        String staging = text(value,"staging");
        Set<String> allowed = kind.equals("phased-vcf") ? Set.of("gt-only-lf-bgzf")
                : kind.equals("m3vcf") ? Set.of("lossless-gzip","preserve-compressed-source","lf-gzip") : Set.of("copy-verbatim");
        if (!allowed.contains(staging)) throw new IOException("Staging policy does not match declared reference/target kind");
        return new Input(member(root,text(value,"path")),member(root,text(value,"identity_ledger")),kind,staging,
                choice(value,"column_semantics",Set.of("person-samples","haplotype-columns")));
    }

    static Path member(Path root, String member) throws IOException {
        if (member.indexOf('\\')>=0 || member.indexOf(':')>=0) throw new IOException("Resource member must use a relative portable path");
        Path name = Path.of(member);
        if (name.isAbsolute() || member.isEmpty()) throw new IOException("Resource member must be relative to explicit resource_root");
        for (Path part : name) if (part.toString().equals("..") || part.toString().equals("."))
            throw new IOException("Resource member traversal is forbidden");
        Path path = root.resolve(name).toAbsolutePath().normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IOException("Resource member escapes explicit resource_root");
        return path;
    }

    static Snapshot snapshot(Spec spec, Path ownedDirectory) throws IOException {
        Path directory = ownedDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS) || !directory.toRealPath().equals(directory))
            throw new IOException("Snapshots need an owned real output directory");
        var sources = new LinkedHashMap<String,Path>();
        sources.put("recipe",spec.recipe); sources.put("reference-source",spec.reference.data);
        sources.put("reference-identities",spec.reference.identities);
        if (spec.target != null) { sources.put("target-source",spec.target.data); sources.put("target-identities",spec.target.identities); }
        var original = new LinkedHashMap<Path,String>(); long bytes = 0;
        for (var entry : sources.entrySet()) {
            Path path = entry.getValue(); MinimacVcf.regular(path,spec.limits.vcf.inputBytes());
            bytes = Math.addExact(bytes,Files.size(path));
            if (bytes > spec.limits.vcf.inputBytes() || bytes > spec.limits.workspaceBytes)
                throw new IOException("Combined immutable input-byte/workspace bound exceeded");
            String hash = FollowupSupport.hash(path);
            if (path.equals(spec.recipe) && !hash.equals(spec.recipeSha256))
                throw new IOException("Minimac recipe changed before snapshot");
            String previous = original.putIfAbsent(path,hash);
            if (previous != null && !previous.equals(hash)) throw new IOException("Aliased Minimac input changed during admission");
        }
        Path target = directory.resolve("input-snapshots"); Files.createDirectory(target);
        var files = new LinkedHashMap<String,Path>(); var staged = new LinkedHashMap<Path,String>();
        for (var entry : sources.entrySet()) {
            Path path = target.resolve(entry.getKey()); Files.copy(entry.getValue(),path);
            String hash = FollowupSupport.hash(path);
            if (!hash.equals(original.get(entry.getValue()))) throw new IOException("Minimac input changed during immutable snapshot");
            staged.put(path,hash); files.put(entry.getKey(),path);
        }
        var result = new Snapshot(files,original,staged); result.verify(); return result;
    }

    static void grouping(MinimacVcf.Identity identity, String semantics) throws IOException {
        for (var contig : identity.contigs().values()) {
            var people = new java.util.HashSet<String>();
            for (var column : contig.values()) {
                if (semantics.equals("haplotype-columns")) {
                    if (column.size()!=1) throw new IOException("Declared haplotype columns require exactly one explicit engine copy");
                } else if (semantics.equals("person-samples")) {
                    String person = column.get(0).person();
                    if (!people.add(person) || column.stream().anyMatch(copy->!copy.person().equals(person)))
                        throw new IOException("A person-sample column must contain one person's copies, without splitting that person across columns");
                } else throw new IOException("Unsupported explicit column semantics");
            }
        }
    }

    private static Engine engine(Map<?,?> m, String profile, String operation) throws IOException {
        keys(m,Set.of("threads","parameter_policy","overrides"));
        int threads = (int)integer(m,"threads",1,256);
        String policy = choice(m,"parameter_policy",Set.of("upstream-defaults","overrides-with-pinned-defaults"));
        Map<?,?> values = object(m.get("overrides"));
        if (policy.equals("upstream-defaults") != values.isEmpty())
            throw new IOException("Upstream-defaults needs empty overrides; explicit overrides need overrides-with-pinned-defaults");
        Map<String,Rule> rules = rules(profile,operation);
        keys(values,rules.keySet());
        var overrides = new LinkedHashMap<String,Object>(); var arguments = new ArrayList<String>();
        for (var entry : values.entrySet()) {
            String key = entry.getKey().toString(); Rule rule = rules.get(key);
            Object value = entry.getValue();
            if (rule.booleanFlag) {
                if (!(value instanceof Boolean enabled)) throw new IOException("Require explicit boolean engine option " + key);
                overrides.put(key,enabled); if (enabled) arguments.add(rule.flag);
            } else {
                double n = rule.integer ? integer(values,key,(long)rule.minimum,(long)rule.maximum)
                        : number(values,key,rule.minimum,rule.maximum);
                Number typed;
                if (rule.integer) typed = Long.valueOf((long)n); else typed = Double.valueOf(n);
                overrides.put(key,typed); arguments.add(rule.flag);
                arguments.add(rule.integer ? Long.toString((long)n) : Double.toString(n));
            }
        }
        if (profile.equals(CURRENT) && operation.equals("prepare-reference") && !values.isEmpty())
            throw new UnavailableCapability("The admitted Min4 4.1.6 compression overrides cannot be honored: original --max-block-size 16 produced a block with 120 variants, and pinned source routes minimum/maximum/slope options to an unrelated ratio field. SKIP the requested custom preparation. Required action: explicitly choose upstream-defaults only if scientifically acceptable, or validate a corrected explicit profile. Defaults are not silently substituted.");
        return new Engine(threads,policy,overrides,arguments);
    }
    private static Map<String,Rule> rules(String profile, String operation) {
        boolean fit = operation.equals("impute") || operation.equals("check");
        var r = new LinkedHashMap<String,Rule>();
        if (profile.equals(CURRENT)) {
            if (fit) {
                r.put("chunk_bases",new Rule("--chunk",1,Integer.MAX_VALUE,true,false));
                r.put("overlap_bases",new Rule("--overlap",0,Integer.MAX_VALUE,true,false));
                r.put("temporary_samples",new Rule("--temp-buffer",1,1000000,true,false));
                r.put("flank_decay",new Rule("--decay",0,1000000,false,false));
                r.put("minimum_target_ratio",new Rule("--min-ratio",0,1,false,false));
                r.put("match_error",new Rule("--match-error",0,.5,false,false));
                r.put("minimum_recombination",new Rule("--min-recom",0,.5,false,false));
                r.put("template_probability",new Rule("--prob-threshold",0,1,false,false));
                r.put("original_state_probability",new Rule("--prob-threshold-s1",-1,1,false,false));
                r.put("template_difference",new Rule("--diff-threshold",0,1000000,false,false));
            }
            if (operation.equals("prepare-reference")) {
                r.put("minimum_block_markers",new Rule("--min-block-size",1,65535,true,false));
                r.put("maximum_block_markers",new Rule("--max-block-size",1,65535,true,false));
                r.put("slope_unit",new Rule("--slope-unit",1,Integer.MAX_VALUE,true,false));
            }
        } else if (profile.equals(MIN3) && (fit || operation.equals("prepare-reference"))) {
            r.put("rounds",new Rule("--rounds",fit?0:1,100000,true,false));
            r.put("states",new Rule("--states",fit?0:1,10000000,true,false));
            r.put("window_bases",new Rule("--window",0,Integer.MAX_VALUE,true,false));
            r.put("low_memory",new Rule("--lowMemory",0,0,false,true));
        } else if (fit) {
            r.put("chunk_length_mb",new Rule("--ChunkLengthMb",Double.MIN_VALUE,1000000000,false,false));
            r.put("chunk_overlap_mb",new Rule("--ChunkOverlapMb",0,1000000000,false,false));
            r.put("window_bases",new Rule("--window",0,Integer.MAX_VALUE,true,false));
            r.put("vcf_buffer_samples",new Rule("--vcfBuffer",1,1000000,true,false));
            r.put("minimum_target_ratio_percent",new Rule("--minRatioPercent",0,100,false,false));
            r.put("template_probability",new Rule("--probThreshold",0,1,false,false));
            r.put("template_difference",new Rule("--diffThreshold",0,1000000,false,false));
            r.put("top_probability",new Rule("--topThreshold",0,1,false,false));
            r.put("full_minimac3_mode",new Rule("--minimac3",0,0,false,true));
        }
        return r;
    }
    private static byte[] raw(Path path, int maximum) throws IOException {
        MinimacVcf.regular(path,maximum);
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(maximum+1);
            if (bytes.length > maximum) throw new IOException("Minimac metadata grew beyond its explicit bound");
            return bytes;
        }
    }
    private static Map<?,?> object(Object value) throws IOException {
        if (!(value instanceof Map<?,?> result)) throw new IOException("Require a Minimac JSON object");
        return result;
    }
    private static void keys(Map<?,?> value, Set<String> allowed) throws IOException {
        for (Object key : value.keySet()) if (!(key instanceof String text) || !allowed.contains(text))
            throw new IOException("Unknown Minimac recipe field: " + key);
    }
    private static String text(Map<?,?> value, String key) throws IOException {
        if (!(value.get(key) instanceof String text) || text.isBlank() || text.length()>8192
                || text.codePoints().anyMatch(Character::isISOControl)) throw new IOException("Require explicit single-line Minimac text: " + key);
        return text;
    }
    private static String choice(Map<?,?> value, String key, Set<String> choices) throws IOException {
        String text = text(value,key);
        if (!choices.contains(text)) throw new IOException("Unsupported " + key + ": " + text + "; expected " + choices);
        return text;
    }
    private static double number(Map<?,?> value, String key, double min, double max) throws IOException {
        if (!(value.get(key) instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue()<min || number.doubleValue()>max) throw new IOException("Require finite " + key + " within [" + min + "," + max + "]");
        return number.doubleValue();
    }
    private static long integer(Map<?,?> value, String key, long min, long max) throws IOException {
        if (!(value.get(key) instanceof Long number) || number<min || number>max)
            throw new IOException("Require an exact integer literal " + key + " within [" + min + "," + max + "]");
        return number;
    }
    private static Map<String,Object> freezeObject(Map<?,?> map) {
        var result = new LinkedHashMap<String,Object>();
        for (var entry : map.entrySet()) result.put(entry.getKey().toString(),freeze(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }
    private static Object freeze(Object value) {
        if (value instanceof Map<?,?> map) return freezeObject(map);
        if (value instanceof List<?> list) return list.stream().map(MinimacRecipe::freeze).toList();
        return value;
    }
}

