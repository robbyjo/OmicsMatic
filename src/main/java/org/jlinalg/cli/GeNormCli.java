/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.jlinalg.expression.GeNorm;

/** Standalone, dependency-free native geNorm command and computational replay. */
public final class GeNormCli {
    private static final String PROFILE = "native-genorm-log2-v1";
    private static final long INPUT_LIMIT = 8L * 1024 * 1024;
    private static final long BUNDLE_FILE_LIMIT = 64L * 1024 * 1024;
    private static final String NOTICE = """
        # Warnings and required actions

        Native geNorm equations on complete quantities or log2 quantities only.
        Cq/Ct values are not implicitly converted. If starting with Cq, supply
        independently justified efficiencies and an explicit quantity conversion
        before using this command. P040's reported geometric mean of Cq values
        is unresolved; this command does not reproduce that paper or its software.

        All factors and normalized targets are exported on the log2 scale.
        The two best reference genes share rank one. Exact maximum-M ties remove
        the first remaining input column; near ties can depend on rounding.
        Inspect the complete stability trace before selecting reference genes.
        The requested reference count is explicit; no 0.15 threshold, target
        calibrator, efficiency, missing-value repair or biological validity is inferred.
        This log-space profile is not NormqPCR's product-based log=FALSE branch.

        FAIL2: no accepted result exists without a valid COMPLETED.properties and
        successful computational verification. Failed runs retain captured input
        and any partial files as provisional. Read FAILURE.txt, correct the named
        cause, and rerun to a NEW directory. Verification preserves the old bundle.
        Hashes and successful replay show internal consistency, not authenticity.
        Runtime analyses use Java only; R and NormqPCR are reference-test inputs.
        Limits: 8 MiB per input, 2..100000 samples, 2..512 candidate references,
        ten million matrix entries, samples*references^2 <= 200 million,
        at most 10000 target columns and 64 MiB per saved bundle file.
        """;

    private record Matrix(List<String> samples, List<String> genes, double[][] values) { }
    private static final class UniqueProperties extends Properties {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Object put(Object key,Object value) {
            if (containsKey(key)) throw new IllegalArgumentException("Duplicate recorded property: " + key);
            return super.put(key,value);
        }
    }
    private GeNormCli() { }

    /** Execute the standalone command. */
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }

    /** Execute with explicit output streams, returning zero on admitted completion. */
    public static int run(String[] args, PrintStream output, PrintStream error) {
        Path owned = null;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                output.println("""
                    Native geNorm: Java only, samples in rows, unquoted UTF-8 TSV.
                    Header: sample_id followed by unique candidate reference names.
                    Run: --references refs.tsv --scale quantity|log2-quantity
                         --reference-count N --out NEW_DIRECTORY [--targets targets.tsv]
                    Targets must have identical sample IDs and order, and the same scale.
                    Verify: --verify EXISTING_DIRECTORY
                    Outputs: full M/V trace, tied ranking, log2 factors and optional
                    log2 normalized targets. Cq/Ct conversion and paper defaults are absent.
                    """); return 0;
            }
            Map<String,String> options = options(args);
            if (options.containsKey("verify")) {
                if (options.size() != 1) throw new IllegalArgumentException("--verify takes only an existing bundle directory");
                verify(Path.of(options.get("verify")));
                output.println("PASS_NATIVE_GENORM_FULL_BUNDLE_REPLAY " + options.get("verify")); return 0;
            }
            if (!options.keySet().containsAll(Set.of("references","scale","reference-count","out"))
                    || !Set.of("references","scale","reference-count","out","targets").containsAll(options.keySet()))
                throw new IllegalArgumentException("Use --references, --scale, --reference-count, --out and optional --targets; see --help");
            Path destination = Path.of(options.get("out")).toAbsolutePath().normalize();
            if (destination.getParent() == null) throw new IllegalArgumentException("Choose a NEW output directory");
            Files.createDirectories(destination.getParent()); Files.createDirectory(destination); owned = destination;
            write(owned.resolve("WARNINGS.md"), NOTICE);
            capture(Path.of(options.get("references")), owned.resolve("references.tsv"));
            boolean targets = options.containsKey("targets");
            if (targets) capture(Path.of(options.get("targets")), owned.resolve("targets.tsv"));
            Properties command = new Properties();
            command.setProperty("profile", PROFILE); command.setProperty("scale", options.get("scale"));
            command.setProperty("reference_count", options.get("reference-count"));
            command.setProperty("requested_references", options.get("references"));
            command.setProperty("requested_targets", options.getOrDefault("targets", ""));
            store(owned.resolve("command.properties"), command);
            for (var entry : compute(owned, command).entrySet()) write(owned.resolve(entry.getKey()), entry.getValue());
            Properties session = new Properties();
            session.setProperty("java_runtime", System.getProperty("java.runtime.version"));
            session.setProperty("os", System.getProperty("os.name"));
            session.putAll(implementationIdentities());
            session.setProperty("profile", PROFILE); store(owned.resolve("session.properties"), session);
            Properties receipt = new Properties(); receipt.setProperty("profile", PROFILE); receipt.setProperty("status", "COMPLETED");
            for (Path path : files(owned)) receipt.setProperty("sha256." + path.getFileName(), hash(path));
            store(owned.resolve("COMPLETED.properties"), receipt);
            verify(owned);
            output.println("PASS_NATIVE_GENORM_COMPLETED_AND_REPLAYED " + owned); return 0;
        } catch (Exception failure) {
            if (owned != null) try {
                Path marker = owned.resolve("COMPLETED.properties");
                if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS))
                    Files.move(marker, owned.resolve("COMPLETION-INVALIDATED-" + UUID.randomUUID() + ".properties"));
                write(owned.resolve("FAILURE.txt"), failure.getClass().getName() + ": " + failure.getMessage() + "\nNo complete result is admitted.\n");
            } catch (Exception recording) { error.println("FAIL2_FAILURE_RECORDING_UNAVAILABLE " + recording); }
            error.println("FAIL2_NATIVE_GENORM " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            error.println("Read WARNINGS.md/FAILURE.txt when created. Correct the cause and rerun to a NEW directory; preserve failed and original bundles.");
            return 2;
        }
    }

    private static Map<String,String> options(String[] args) {
        Map<String,String> result = new LinkedHashMap<>();
        if (args.length == 0 || args.length % 2 != 0) throw new IllegalArgumentException("Expected --option value pairs; see --help");
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || args[i].length() < 3 || args[i+1].isEmpty()
                    || result.putIfAbsent(args[i].substring(2), args[i+1]) != null)
                throw new IllegalArgumentException("Invalid or duplicate option: " + args[i]);
        }
        return result;
    }

    private static Map<String,String> compute(Path root, Properties command) throws IOException {
        Set<String> keys = Set.of("profile","scale","reference_count","requested_references","requested_targets");
        if (!command.stringPropertyNames().equals(keys) || !PROFILE.equals(command.getProperty("profile")))
            throw new IllegalArgumentException("Complete scientific command fields or profile differ");
        GeNorm.Scale scale = switch (command.getProperty("scale")) {
            case "quantity" -> GeNorm.Scale.QUANTITY;
            case "log2-quantity" -> GeNorm.Scale.LOG2_QUANTITY;
            default -> throw new IllegalArgumentException("--scale must be quantity or log2-quantity; Cq/Ct is not inferred");
        };
        Matrix refs = read(root.resolve("references.tsv"));
        int count = Integer.parseInt(command.getProperty("reference_count"));
        if (count < 2 || count > refs.genes.size()) throw new IllegalArgumentException("Reference count must be 2..candidate column count");
        GeNorm.Result result = GeNorm.select(refs.values, scale); int[] order = result.order();
        int[] selected = Arrays.copyOf(order, count);
        StringBuilder ranking = new StringBuilder("gene\trank\tinput_column_1based\tselected_for_factor\n");
        for (int i = 0; i < order.length; i++) ranking.append(refs.genes.get(order[i])).append('\t')
            .append(i < 2 ? 1 : i+1).append('\t').append(order[i]+1).append('\t').append(i < count).append('\n');
        StringBuilder stability = new StringBuilder("remaining_genes\tgene\tM\n");
        StringBuilder steps = new StringBuilder("remaining_genes\tmean_M\tremoved_gene\tV_next_over_current\n");
        for (GeNorm.Step step : result.steps()) {
            int[] columns = step.columns(); double[] m = step.stabilityM();
            for (int i = 0; i < columns.length; i++) stability.append(columns.length).append('\t')
                .append(refs.genes.get(columns[i])).append('\t').append(m[i]).append('\n');
            steps.append(columns.length).append('\t').append(step.meanM()).append('\t');
            if (step.removedColumn() < 0) steps.append("NA_not_applicable\tNA_not_applicable\n");
            else steps.append(refs.genes.get(step.removedColumn())).append('\t').append(step.variationToNext()).append('\n');
        }
        double[] factors = GeNorm.log2Factors(refs.values, scale, selected);
        StringBuilder nf = new StringBuilder("sample_id\tlog2_factor\n");
        for (int s = 0; s < factors.length; s++) nf.append(refs.samples.get(s)).append('\t').append(factors[s]).append('\n');
        Map<String,String> output = new LinkedHashMap<>(); output.put("ranking.tsv",ranking.toString());
        output.put("stability.tsv",stability.toString());output.put("steps.tsv",steps.toString());output.put("normalization.tsv",nf.toString());
        if (!command.getProperty("requested_targets").isEmpty()) {
            Matrix target = read(root.resolve("targets.tsv"));
            if (!target.samples.equals(refs.samples)) throw new IllegalArgumentException("Target sample IDs and order must exactly match the references");
            double[][] normalized = GeNorm.normalizeLog2(target.values, scale, factors);
            StringBuilder table = new StringBuilder("sample_id\t" + String.join("\t",target.genes) + "\n");
            for (int s = 0; s < normalized.length; s++) {
                table.append(target.samples.get(s));
                for (double value : normalized[s]) table.append('\t').append(value);
                table.append('\n');
            }
            output.put("normalized-targets-log2.tsv",table.toString());
        }
        return output;
    }

    private static Matrix read(Path path) throws IOException {
        if (Files.size(path) > INPUT_LIMIT) throw new IOException("Input exceeds 8 MiB: " + path.getFileName());
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() < 3 || lines.size()-1 > GeNorm.MAX_SAMPLES)
            throw new IllegalArgumentException("Require a header and 2..100000 samples");
        String[] header = lines.get(0).split("\t",-1);
        if (header.length < 2 || !header[0].equals("sample_id")) throw new IllegalArgumentException("TSV header must start with sample_id and have at least one gene");
        if (header.length-1 > 10000 || (long)(header.length-1)*(lines.size()-1) > 10_000_000L)
            throw new IllegalArgumentException("Require <=10000 columns and <=10 million matrix entries");
        Set<String> distinct = new LinkedHashSet<>();
        for (String field : header) if (field.isBlank() || field.indexOf('"') >= 0 || !distinct.add(field))
            throw new IllegalArgumentException("Use unique nonblank unquoted TSV column names");
        List<String> samples = new ArrayList<>();Set<String> sampleIds = new LinkedHashSet<>();
        double[][] values = new double[lines.size()-1][header.length-1];
        for (int s = 0; s < values.length; s++) {
            String[] row = lines.get(s+1).split("\t",-1);
            if (row.length != header.length || row[0].isBlank() || row[0].indexOf('"') >= 0 || !sampleIds.add(row[0]))
                throw new IllegalArgumentException("Invalid row, duplicate/blank sample_id or quoted field at row " + (s+2));
            samples.add(row[0]);
            for (int g = 0; g < values[s].length; g++) {
                if (!row[g+1].equals(row[g+1].strip())) throw new IllegalArgumentException("Numeric whitespace at row " + (s+2));
                values[s][g] = Double.parseDouble(row[g+1]);
            }
        }
        return new Matrix(List.copyOf(samples),List.copyOf(Arrays.asList(header).subList(1,header.length)),values);
    }

    private static void capture(Path from, Path to) throws IOException {
        if (Files.size(from) > INPUT_LIMIT) throw new IOException("Input exceeds 8 MiB");
        Files.copy(from,to);
        if (Files.size(to) > INPUT_LIMIT) throw new IOException("Captured input exceeds 8 MiB");
    }
    private static void write(Path path, String text) throws IOException {
        Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private static void store(Path path, Properties value) throws IOException {
        StringWriter writer = new StringWriter();value.store(writer,null);write(path,writer.toString());
    }
    private static Properties load(Path path) throws IOException {
        Properties result = new UniqueProperties();
        result.load(new StringReader(Files.readString(path,StandardCharsets.UTF_8)));return result;
    }
    private static List<Path> files(Path path) throws IOException {
        try (var stream = Files.list(path)) {
            List<Path> result = stream.limit(12).toList();
            if (result.size() > 11) throw new IOException("Bundle has more than eleven files");
            for (Path file : result) if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Bundle must contain only regular files: " + file.getFileName());
            for (Path file : result) if (Files.size(file) > BUNDLE_FILE_LIMIT)
                throw new IOException("Bundle file exceeds 64 MiB: " + file.getFileName());
            return result;
        }
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static Properties implementationIdentities() throws Exception {
        Properties result = new Properties();
        for (Class<?> type : List.of(GeNorm.class,GeNorm.Scale.class,GeNorm.Step.class,GeNorm.Result.class,
                GeNormCli.class,Matrix.class,UniqueProperties.class)) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.','/') + ".class")) {
                if (stream == null) throw new IOException("Implementation class resource unavailable: " + type.getName());
                result.setProperty("implementation_sha256."+type.getName(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }
        }
        return result;
    }
    private static void verify(Path root) throws Exception {
        List<Path> actual = files(root);Properties receipt = load(root.resolve("COMPLETED.properties"));
        if (!"COMPLETED".equals(receipt.getProperty("status")) || !PROFILE.equals(receipt.getProperty("profile")))
            throw new IOException("Completion status/profile differs");
        Set<String> expectedKeys = new LinkedHashSet<>(Set.of("profile","status"));
        for (Path file : actual) if (!file.getFileName().toString().equals("COMPLETED.properties")) {
            String key = "sha256." + file.getFileName();expectedKeys.add(key);
            if (!hash(file).equals(receipt.getProperty(key))) throw new IOException("Captured file hash differs: " + file.getFileName());
        }
        if (!receipt.stringPropertyNames().equals(expectedKeys)) throw new IOException("Completion inventory differs");
        Properties command = load(root.resolve("command.properties"));Map<String,String> recomputed = compute(root,command);
        Set<String> members = new LinkedHashSet<>(Set.of("COMPLETED.properties","WARNINGS.md","references.tsv","command.properties","session.properties"));
        members.addAll(recomputed.keySet());
        if (!command.getProperty("requested_targets").isEmpty()) members.add("targets.tsv");
        if (!members.equals(actual.stream().map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet())))
            throw new IOException("Unexpected or missing bundle member");
        if (!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(NOTICE))
            throw new IOException("Complete profile warnings differ");
        Properties session = load(root.resolve("session.properties"));
        Properties implementation = implementationIdentities();
        Set<String> sessionKeys = new LinkedHashSet<>(Set.of("profile","java_runtime","os"));
        sessionKeys.addAll(implementation.stringPropertyNames());
        if (!session.stringPropertyNames().equals(sessionKeys) || !PROFILE.equals(session.getProperty("profile"))
                || session.getProperty("java_runtime").isBlank() || session.getProperty("os").isBlank())
            throw new IOException("Recorded runtime fields differ");
        for (String key : implementation.stringPropertyNames()) if (!implementation.getProperty(key).equals(session.getProperty(key)))
            throw new IOException("Recorded implementation bytes differ; use the original pinned build: " + key);
        for (var entry : recomputed.entrySet()) if (!Arrays.equals(Files.readAllBytes(root.resolve(entry.getKey())),entry.getValue().getBytes(StandardCharsets.UTF_8)))
            throw new IOException("Scientific recomputation differs: " + entry.getKey());
    }
}
