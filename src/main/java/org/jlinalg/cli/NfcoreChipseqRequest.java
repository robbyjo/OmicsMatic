/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit source/runtime selection over the existing fully typed captured-engine recipe. */
final class NfcoreChipseqRequest {
    private NfcoreChipseqRequest() { }
    static final String PROFILE = "nfcore-chipseq-1.2.1";
    static final String ENGINE = "linux-x86_64-nextflow-21.04.1";
    static final String EXTERNAL = "// No institutional overrides selected; original scientific defaults remain in force.\n";
    record Request(Path recipe, Path containerHome, Path image, String peakType, String readMode,
                   int minimumReplicates, int executorCpus, long executorMemoryBytes, Map<?, ?> raw) { }
    static Request read(Path file) throws IOException {
        Map<?, ?> raw = object(NextflowParameters.parse(utf8(bytes(file, 4194304)), 4194304, 100000));
        keys(raw, "schema_version", "pipeline_profile", "engine_recipe", "container_home", "image", "peak_type", "read_mode", "minimum_replicates", "executor_cpus", "executor_memory_bytes"); integer(raw.get("schema_version"), 1, 1);
        if (!PROFILE.equals(text(raw.get("pipeline_profile")))) throw new NextflowProcess.Unavailable("Requested nf-core family/version is not admitted by this adapter profile. No pipeline/engine version is inferred or substituted; dependent analysis skipped.");
        String peak = text(raw.get("peak_type")), mode = text(raw.get("read_mode")); if (!Set.of("broadPeak", "narrowPeak").contains(peak) || !Set.of("paired", "single").contains(mode)) throw new IOException("Declare exact source peak_type and read_mode");
        return new Request(absolute(raw.get("engine_recipe")), absolute(raw.get("container_home")), absolute(raw.get("image")), peak, mode, (int) integer(raw.get("minimum_replicates"), 1, 1000000), (int) integer(raw.get("executor_cpus"), 1, 1024), integer(raw.get("executor_memory_bytes"), 16777216, 1099511627776L), raw);
    }
    private static Path absolute(Object value) throws IOException { Path path = Path.of(text(value)); if (!path.isAbsolute() || !path.normalize().equals(path)) throw new IOException("Require literal normalized absolute source/runtime paths"); return path; }
    static String overlay(Request request, Path engine) throws IOException {
        for (Path path : List.of(engine, request.image())) if (path.toString().indexOf(',') >= 0 || path.toString().chars().anyMatch(c -> c < 32 || c == 127)) throw new NextflowProcess.Unavailable("Original config/bind syntax cannot represent the requested path. Use NEW supported run/runtime paths; analysis skipped.");
        return "process.executor = 'local'\nprocess.container = " + quote(request.image().toString()) + "\nexecutor.cpus = " + request.executorCpus() + "\nexecutor.memory = '" + request.executorMemoryBytes() + " B'\nsingularity.cacheDir = " + quote(engine.resolve("workspace/execution/container-cache").toString()) + "\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\n";
    }
    private static String quote(String value) { return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"; }
    static NextflowRecipe.Request validate(Request request, Path engine) throws IOException {
        NextflowRecipe.Request recipe = NextflowRecipe.read(request.recipe());
        if (!Files.isDirectory(recipe.workflow().source())) throw new NextflowProcess.Unavailable("Pinned original workflow source is missing. Complete permitted source setup before dependent analysis; analysis skipped.");
        if (!recipe.profile().equals(ENGINE)) throw new NextflowProcess.Unavailable("ChIP-seq1.2.1 admission selects exact Nextflow21.04.1. The author engine can remain unreported; requested unsupported engine is not silently replaced. Analysis skipped.");
        if (!recipe.workflow().entrypoint().equals("main.nf") || !recipe.workflow().configs().equals(List.of("nextflow.config", "runtime/host.config")) || !recipe.workflow().profiles().equals(List.of("singularity")) || !recipe.requireTasks()) throw new IOException("Require the original entrypoint, complete primary-config order, singularity profile and observed tasks");
        Path bundle = recipe.sources().get("bundle"); if (bundle == null || !recipe.workflow().source().startsWith(bundle)) throw new IOException("Declare one complete common bundle containing workflow, bound design, reads and references");
        for (Path source : recipe.sources().values()) if (!source.startsWith(bundle)) throw new IOException("Every declared input alias must remain in the complete captured common bundle");
        if (recipe.tools().size() != 1 || !recipe.tools().get(0).name().equals("singularity") || !recipe.tools().get(0).executable().equals(request.containerHome().resolve("bin/singularity"))) throw new IOException("Declare only the exact admitted private singularity launcher; no host scientific executable substitute is accepted");
        originalSource(recipe.workflow().source());
        Map<?, ?> parameters = object(recipe.parameters());
        parameterContracts(recipe, parameters);
        for (var pair : Map.of("input", "design", "fasta", "fasta", "gtf", "gtf", "custom_config_base", "external_configs").entrySet()) {
            if (!Map.of("$input", pair.getValue()).equals(parameters.get(pair.getKey())) || !recipe.sources().containsKey(pair.getValue())) throw new IOException("Require complete declared captured input binding: " + pair.getKey());
        }
        if (!Map.of("$output", "").equals(parameters.get("outdir")) || !Map.of("$output", "pipeline_info").equals(parameters.get("tracedir"))) throw new IOException("Original output/trace paths must bind to the owned published tree");
        if (!parameters.containsKey("macs_gsize")) throw new IOException("Declare the actual source MACS genome-size setting explicitly");
        for (var entry : parameters.entrySet()) if (text(entry.getKey()).startsWith("skip_") && Boolean.TRUE.equals(entry.getValue())) throw new NextflowProcess.Unavailable("This profile requires the complete selected original workflow branches; requested skip flag is outside this admission: " + entry.getKey() + ". Use the separately scoped source tool where appropriate; analysis skipped.");
        requireFlag(parameters, "single_end", request.readMode().equals("single")); requireFlag(parameters, "narrow_peak", request.peakType().equals("narrowPeak"));
        if (parameters.containsKey("min_reps_consensus") && integer(parameters.get("min_reps_consensus"), 1, 1000000) != request.minimumReplicates()) throw new IOException("Declared consensus minimum differs from exact source parameter");
        if (!parameters.containsKey("min_reps_consensus") && request.minimumReplicates() != 1) throw new IOException("Non-default source replicate minimum must be explicit in parameters");
        if (parameters.containsKey("max_cpus") && integer(parameters.get("max_cpus"), 1, 1024) > request.executorCpus()) throw new IOException("Requested source task CPU maximum exceeds declared executor capacity");
        Path external = recipe.sources().get("external_configs"); if (!Files.isDirectory(external) || !utf8(bytes(external.resolve("nfcore_custom.config"), 1048576)).equals(EXTERNAL)) throw new IOException("Require the separately declared no-institutional-override runtime binding; no unreported institutional configuration is inferred");
        try (var files = Files.list(external)) { if (!files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("nfcore_custom.config"))) throw new IOException("Unexpected external configuration members"); }
        if (!utf8(bytes(recipe.workflow().source().resolve("runtime/host.config"), 1048576)).equals(overlay(request, engine))) throw new IOException("Runtime-only overlay differs from the typed local executor, image and namespace binding");
        Set<String> required = Set.of("pipeline_info", "fastqc", "trim_galore", "bwa", "genome", "multiqc", "igv"); var directories = new HashSet<String>();
        for (var output : recipe.outputs()) if (output.kind().equals("directory") && !output.allowEmpty()) directories.add(output.path());
        if (!directories.containsAll(required)) throw new IOException("Declare every required nonempty source output family; complete publication capture is mandatory");
        return recipe;
    }
    private static void parameterContracts(NextflowRecipe.Request recipe, Map<?, ?> parameters) throws IOException {
        Map<?, ?> schema = object(json(recipe.workflow().source().resolve("nextflow_schema.json"), 4194304));
        var properties = new java.util.LinkedHashMap<String, Map<?, ?>>();
        for (Object definition : object(schema.get("definitions")).values())
            for (var property : object(object(definition).get("properties")).entrySet()) properties.put(text(property.getKey()), object(property.getValue()));
        Set<String> inputPaths = Set.of("input", "fasta", "gtf", "custom_config_base", "bwa_index", "gene_bed", "blacklist", "bamtools_filter_pe_config", "bamtools_filter_se_config", "multiqc_config");
        Set<String> unsupported = Set.of("genome", "igenomes_base", "custom_config_version", "hostnames", "config_profile_description", "config_profile_contact", "config_profile_url", "clusterOptions", "email", "email_on_fail");
        for (var entry : parameters.entrySet()) {
            String name = text(entry.getKey()); Object value = entry.getValue(); Map<?, ?> property = properties.get(name);
            if (property == null) throw new IOException("Unknown original ChIP-seq1.2.1 parameter: " + name + "; no ignored setting is admitted");
            if (unsupported.contains(name)) throw new NextflowProcess.Unavailable("Requested parameter needs an additional reference/configuration/notification contract outside this explicit local profile: " + name + ". Use explicitly captured local FASTA/GTF and resources; analysis skipped.");
            if (inputPaths.contains(name)) {
                if (!(value instanceof Map<?, ?>)) throw new IOException("Input parameter must use a complete declared $input binding: " + name);
                Map<?, ?> binding = object(value);
                if (!binding.keySet().equals(Set.of("$input")) && !binding.keySet().equals(Set.of("$input", "path"))) throw new IOException("Input parameter must use a complete declared $input binding: " + name);
                String id = text(binding.get("$input")); Path base = recipe.sources().get(id);
                if (base == null) throw new IOException("Undeclared input parameter resource: " + name);
                if (binding.containsKey("path")) member(text(binding.get("path")));
                // The existing typed binder independently rejects traversal and binds this alias into the complete capture.
                NextflowParameters.bind(value, recipe.sources(), recipe.workflow().source());
                continue;
            }
            if (name.equals("outdir") || name.equals("tracedir")) continue;
            String type = text(property.get("type"));
            boolean valid = switch (type) {
                case "boolean" -> value instanceof Boolean;
                case "integer" -> value instanceof NextflowParameters.NumberToken token && token.text().matches("-?(?:0|[1-9][0-9]*)");
                case "number" -> value instanceof NextflowParameters.NumberToken;
                case "string" -> value instanceof String || name.equals("macs_gsize") && value instanceof NextflowParameters.NumberToken;
                default -> false;
            };
            if (!valid) throw new IOException("Require the original typed scalar for parameter " + name + " (" + type + ")");
            if (property.containsKey("enum") && !list(property.get("enum")).contains(value)) throw new IOException("Unknown original parameter choice: " + name);
            if (name.equals("publish_dir_mode") && !value.equals("copy")) throw new NextflowProcess.Unavailable("Complete immutable publication requires original publish_dir_mode=copy; requested alternative is outside this adapter profile. Analysis skipped.");
            if (name.equals("help") && Boolean.TRUE.equals(value)) throw new NextflowProcess.Unavailable("Original help-only request is not a scientific workflow execution. Analysis skipped.");
        }
    }
    private static void requireFlag(Map<?, ?> parameters, String name, boolean expected) throws IOException { Object value = parameters.get(name); if (parameters.containsKey(name) ? !Boolean.valueOf(expected).equals(value) : expected) throw new IOException("Exact original " + name + " selection must agree with declared mode"); }
    static void originalSource(Path workflow) throws IOException {
        byte[] raw;
        try (var input = NfcoreChipseqRequest.class.getResourceAsStream("/org/jlinalg/omics/nfcore/nfcore-source-lock.json")) { if (input == null) throw new IOException("Missing original source lock"); raw = input.readNBytes(134642); }
        if (raw.length != 134641 || !sha(raw).equals("f5ef123c1d212061c202a1b5ef2d9fef62aa3f7cc764834d40c0c94799bf8646")) throw new IOException("Original source-lock bytes differ");
        Map<?, ?> profile = object(object(object(SimpleJson.parse(utf8(raw))).get("pipelines")).get("chipseq-1.2.1")); Map<?, ?> files = object(profile.get("files")); var expected = new HashSet<String>();
        for (var entry : files.entrySet()) { String name = member(text(entry.getKey())); if (!pin(workflow.resolve(name), 1000000000L, () -> { }).equals(fingerprint(entry.getValue(), 1000000000L))) throw new IOException("Original ChIP-seq1.2.1 source differs: " + name); expected.add(name); }
        for (String name : expected) if (name.startsWith("bin/") && !Files.isExecutable(workflow.resolve(name))) throw new NextflowProcess.Unavailable("Original workflow helper execution is forbidden or source executable modes are missing: " + name + ". Install the exact source with executable permissions where permitted; dependent analysis skipped.");
        expected.add("runtime/host.config"); var actual = tree(workflow, 10000, 1000000000L, 1000000000L, () -> { });
        if (!actual.keySet().equals(expected) || actual.values().stream().anyMatch(value -> !value.kind().equals("file"))) throw new IOException("Original source directory has missing/extra code or links beyond the explicit runtime overlay");
    }
}
