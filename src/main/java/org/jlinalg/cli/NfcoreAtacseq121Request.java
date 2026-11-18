/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.workflow.NfcoreAtacseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete original ATAC1.2.1 source, design, typed-parameter and local runtime selection. */
final class NfcoreAtacseq121Request {
    private NfcoreAtacseq121Request() { }
    static final String RESOURCE = "/org/jlinalg/omics/nfcore-atacseq121/";
    static final String PROFILE = "nfcore-atacseq-1.2.1";
    static final String ENGINE = "linux-x86_64-nextflow-21.04.1";
    static final String EXTERNAL = "// No institutional overrides selected; original scientific defaults remain in force.\n";
    private static final Pin SOURCE = new Pin(15062, "3d37ce495dd8feebeba2d1797de3584d0708b7aced25aa26a074fa9c931218c7");
    record Request(Path recipe, Path containerHome, Path images, int cpus, long memoryBytes) { }
    record Selection(NextflowRecipe.Request recipe, NfcoreAtacseq121Mappings.Design design, String peakType) { }

    static Request read(Path file) throws IOException {
        Map<?, ?> raw = object(json(file, 4194304));
        keys(raw, "schema_version", "pipeline_profile", "engine_recipe", "container_home", "images", "executor_cpus", "executor_memory_bytes");
        integer(raw.get("schema_version"), 1, 1);
        if (!PROFILE.equals(text(raw.get("pipeline_profile")))) throw new NextflowProcess.Unavailable("Unsupported requested ATAC family/version. No edition is inferred or substituted; analysis skipped.");
        return new Request(absolute(raw.get("engine_recipe")), absolute(raw.get("container_home")), absolute(raw.get("images")),
            (int)integer(raw.get("executor_cpus"), 1, 1024), integer(raw.get("executor_memory_bytes"), 16777216, 1099511627776L));
    }
    private static Path absolute(Object value) throws IOException {
        Path path = Path.of(text(value));
        if (!path.isAbsolute() || !path.normalize().equals(path)) throw new IOException("Declare normalized literal absolute ATAC paths");
        return path;
    }
    static Map<?, ?> sourceLock() throws IOException {
        byte[] raw = resource("source-lock.json", 1048576);
        if (!new Pin(raw.length, sha(raw)).equals(SOURCE)) throw new IOException("Pinned original ATAC source-lock resource differs");
        return object(SimpleJson.parse(utf8(raw)));
    }
    static byte[] resource(String name, int maximum) throws IOException {
        try (var input = NfcoreAtacseq121Request.class.getResourceAsStream(RESOURCE+name)) {
            if (input == null) throw new IOException("Missing pinned ATAC resource: "+name);
            byte[] raw = input.readNBytes(maximum+1);
            if (raw.length > maximum) throw new IOException("ATAC resource capacity exceeded");
            return raw;
        }
    }
    static Selection validate(Request request, Path engineRoot, String overlay) throws IOException {
        var recipe = NextflowRecipe.read(request.recipe());
        if (!Files.isDirectory(recipe.workflow().source())) throw new NextflowProcess.Unavailable("Original pinned ATAC source is missing. Download the permitted exact source separately; dependent analysis skipped.");
        if (!recipe.profile().equals(ENGINE)) throw new NextflowProcess.Unavailable("This ATAC1.2.1 execution profile selects exact Nextflow21.04.1. The paper's engine remains unreported; no other edition is substituted. Analysis skipped.");
        if (!recipe.workflow().entrypoint().equals("main.nf") || !recipe.workflow().configs().equals(List.of("nextflow.config", "runtime/host.config")) || !recipe.workflow().profiles().equals(List.of("singularity")) || !recipe.requireTasks()) throw new IOException("Require complete primary-config order, original entrypoint, singularity profile and observed tasks");
        Path bundle = recipe.sources().get("bundle");
        if (bundle == null || !recipe.workflow().source().startsWith(bundle) || recipe.sources().values().stream().anyMatch(path -> !path.startsWith(bundle))) throw new IOException("All source, design, reads and references must remain in one completely captured bundle");
        for (String name : tree(bundle, recipe.capture().files(), recipe.capture().bytes(), recipe.capture().fileBytes(), () -> { }).keySet())
            if (name.chars().anyMatch(c -> !(Character.isLetterOrDigit(c) || "/_.-".indexOf(c) >= 0))) throw new NextflowProcess.Unavailable("Original unquoted helper syntax cannot represent a captured input filename. Use NEW explicit staging paths and retain the original path mapping; dependent analysis skipped.");
        if ((recipe.tools().size() != 1 && recipe.tools().size() != 2) || !recipe.tools().get(0).name().equals("singularity") || !recipe.tools().get(0).executable().equals(request.containerHome().resolve("bin/singularity")) || recipe.tools().size() == 2 && !recipe.tools().get(1).name().equals("dot")) throw new IOException("Require the admitted private singularity launcher and, optionally, an explicit dot visualization executable; no scientific host-PATH substitute");
        if (System.getProperty("os.name").equals("Linux") && Files.isExecutable(Path.of("/usr/bin/dot")) && recipe.tools().size() == 1) throw new NextflowProcess.Unavailable("Graphviz is present on the engine PATH but undeclared. Prepare a NEW request with --dot /usr/bin/dot so its exact executable identity is recorded; analysis skipped.");
        var expected = new HashSet<String>();
        for (var row : object(sourceLock().get("files")).entrySet()) {
            String name = member(text(row.getKey())); Path file = recipe.workflow().source().resolve(name);
            if (!pin(file, 1000000000L, () -> { }).equals(fingerprint(row.getValue(), 1000000000L))) throw new IOException("Original ATAC1.2.1 source differs: "+name);
            if (name.startsWith("bin/") && System.getProperty("os.name").equals("Linux") && !Files.isExecutable(file)) throw new NextflowProcess.Unavailable("Original ATAC helper executable mode is absent or forbidden: "+name+". Install the exact source with permitted executable modes; dependent analysis skipped.");
            expected.add(name);
        }
        expected.add("runtime/host.config"); var tree = tree(recipe.workflow().source(), 10000, 1000000000L, 1000000000L, () -> { });
        if (!tree.keySet().equals(expected) || tree.values().stream().anyMatch(row -> !row.kind().equals("file"))) throw new IOException("ATAC source has extra/missing code or links outside the explicit runtime overlay");
        if (!utf8(bytes(recipe.workflow().source().resolve("runtime/host.config"), 1048576)).equals(overlay)) throw new IOException("Runtime-only overlay differs from the typed local executor and source-image binding");
        Map<?, ?> parameters = object(recipe.parameters()); parameters(recipe, parameters);
        for (var pair : Map.of("input", "design", "fasta", "fasta", "gtf", "gtf", "custom_config_base", "external_configs").entrySet())
            if (!Map.of("$input", pair.getValue()).equals(parameters.get(pair.getKey())) || !recipe.sources().containsKey(pair.getValue())) throw new IOException("Require complete captured local input binding: "+pair.getKey());
        if (!Map.of("$output", "").equals(parameters.get("outdir")) || !Map.of("$output", "pipeline_info").equals(parameters.get("tracedir"))) throw new IOException("Require owned complete source publication and trace paths");
        if (!parameters.containsKey("macs_gsize") || !(parameters.get("macs_gsize") instanceof String || parameters.get("macs_gsize") instanceof NextflowParameters.NumberToken)) throw new IOException("Declare the exact nonempty positive original MACS genome-size setting");
        Object gsize = parameters.get("macs_gsize");
        if (gsize instanceof String text && !text.matches("(?:hs|mm|ce|dm|[0-9]+(?:\\.[0-9]*)?(?:[eE][+-]?[0-9]+)?)")) throw new IOException("Unsupported source MACS genome-size spelling");
        if (!(gsize instanceof String text && Set.of("hs", "mm", "ce", "dm").contains(text))) {
            try { if (!(new java.math.BigDecimal(gsize instanceof NextflowParameters.NumberToken token ? token.text() : (String)gsize).signum() > 0)) throw new IOException("MACS genome size must be positive"); }
            catch (NumberFormatException invalid) { throw new IOException("Invalid original MACS genome size", invalid); }
        }
        Path external = recipe.sources().get("external_configs");
        if (!utf8(bytes(external.resolve("nfcore_custom.config"), 1048576)).equals(EXTERNAL)) throw new IOException("Require the explicit no-institutional-override config binding");
        try (var files = Files.list(external)) { if (!files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("nfcore_custom.config"))) throw new IOException("Unexpected external config members"); }
        Set<String> required = Set.of("pipeline_info", "fastqc", "trim_galore", "bwa", "genome", "multiqc", "igv"); var declared = new HashSet<String>();
        for (var output : recipe.outputs()) if (output.kind().equals("directory") && !output.allowEmpty()) declared.add(output.path());
        if (!declared.containsAll(required)) throw new IOException("Require every nonempty original publication family; zero process exit alone admits no ATAC result");
        if (parameters.containsKey("max_cpus") && integer(parameters.get("max_cpus"), 1, 1024) > request.cpus()) throw new IOException("Source task CPU cap exceeds declared executor capacity");
        var design = NfcoreAtacseq121Mappings.read(new StringReader(utf8(bytes(recipe.sources().get("design"), 67108864))), NfcoreAtacseq121Mappings.Limits.defaults());
        if (design.paired() == Boolean.TRUE.equals(parameters.get("single_end"))) throw new IOException("Declared source single_end mode differs from complete design");
        for (var row : design.reads()) {
            if (!row.group().matches("[A-Za-z0-9_.-]+")) throw new NextflowProcess.Unavailable("This original unquoted shell profile cannot safely represent the supplied group ID. No identifier repair: use an explicitly reviewed source workflow for this ID; dependent analysis skipped.");
            for (String path : row.read2().isEmpty() ? List.of(row.read1()) : List.of(row.read1(), row.read2()))
                if (path.chars().anyMatch(c -> !(Character.isLetterOrDigit(c) || "/_.-".indexOf(c) >= 0))) throw new NextflowProcess.Unavailable("Original unquoted read path is outside this profile's representable shell paths. Use NEW local staging paths, retain originals and path mapping; dependent analysis skipped.");
            Path first = readPath(row.read1(), engineRoot, bundle);
            if (!row.read2().isEmpty() && Files.isSameFile(first, readPath(row.read2(), engineRoot, bundle))) throw new IOException("Paired FASTQ paths resolve to one physical input file: "+row.sampleId());
        }
        return new Selection(recipe, design, Boolean.TRUE.equals(parameters.get("narrow_peak")) ? "narrowPeak" : "broadPeak");
    }
    private static Path readPath(String supplied, Path engineRoot, Path bundle) throws IOException {
        Path path = Path.of(supplied), bound = engineRoot.resolve("workspace/resources/r0").resolve(bundle.getFileName());
        if (!path.isAbsolute() || !path.normalize().equals(path) || !path.startsWith(bound)) throw new IOException("Read is outside the complete captured common input bundle: "+supplied+". Prepare explicit local input bindings for the intended run root before analysis.");
        Path original = bundle.resolve(bound.relativize(path));
        if (!Files.isRegularFile(original) || !original.toRealPath().startsWith(bundle)) throw new IOException("A complete bound FASTQ is missing or escapes the declared input bundle: "+supplied);
        return original;
    }
    private static void parameters(NextflowRecipe.Request recipe, Map<?, ?> parameters) throws IOException {
        Map<?, ?> schema = object(json(recipe.workflow().source().resolve("nextflow_schema.json"), 4194304));
        var known = new java.util.LinkedHashMap<String, Map<?, ?>>();
        for (Object definition : object(schema.get("definitions")).values()) for (var row : object(object(definition).get("properties")).entrySet()) known.put(text(row.getKey()), object(row.getValue()));
        Set<String> paths = Set.of("input", "fasta", "gtf", "custom_config_base", "bwa_index", "gene_bed", "tss_bed", "blacklist", "bamtools_filter_pe_config", "bamtools_filter_se_config", "multiqc_config");
        Set<String> outside = Set.of("genome", "igenomes_base", "custom_config_version", "hostnames", "config_profile_description", "config_profile_contact", "config_profile_url", "clusterOptions", "email", "email_on_fail", "name");
        for (var row : parameters.entrySet()) {
            String name = text(row.getKey()); Object value = row.getValue(); Map<?, ?> property = known.get(name);
            if (property == null) throw new IOException("Unknown original ATAC1.2.1 parameter: "+name);
            if (outside.contains(name) || name.startsWith("skip_") && Boolean.TRUE.equals(value) || name.equals("help") && Boolean.TRUE.equals(value)) throw new NextflowProcess.Unavailable("Requested setting lies outside this complete local ATAC execution contract: "+name+". Supply permitted explicit local references and the full selected branches; analysis skipped.");
            if (paths.contains(name)) {
                Map<?, ?> binding = object(value);
                if (!binding.keySet().equals(Set.of("$input")) && !binding.keySet().equals(Set.of("$input", "path"))) throw new IOException("Require a complete declared input binding: "+name);
                NextflowParameters.bind(value, recipe.sources(), recipe.workflow().source()); continue;
            }
            if (Set.of("outdir", "tracedir").contains(name)) continue;
            String type = text(property.get("type"));
            boolean valid = switch (type) {
                case "boolean" -> value instanceof Boolean;
                case "integer" -> value instanceof NextflowParameters.NumberToken token && token.text().matches("-?(?:0|[1-9][0-9]*)");
                case "number" -> value instanceof NextflowParameters.NumberToken;
                case "string" -> value instanceof String || name.equals("macs_gsize") && value instanceof NextflowParameters.NumberToken;
                default -> false;
            };
            if (!valid) throw new IOException("Require original typed scalar for "+name+" ("+type+")");
            if (value instanceof String text && !text.matches("[A-Za-z0-9_. -]+")) throw new NextflowProcess.Unavailable("Original unquoted scalar syntax is outside this local profile for "+name+". Use an explicitly reviewed original workflow for the requested spelling; analysis skipped.");
            if (property.containsKey("enum") && !list(property.get("enum")).contains(value)) throw new IOException("Unknown original choice for "+name);
            if (name.equals("publish_dir_mode") && !value.equals("copy")) throw new NextflowProcess.Unavailable("Immutable complete publication requires source publish_dir_mode=copy; analysis skipped.");
        }
    }
}
