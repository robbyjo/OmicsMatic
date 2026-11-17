/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.stats.TukeyKramer;

/** Native ordinary one-way Tukey-Kramer comparisons from complete prepared observations. */
public final class TukeyKramerCli {
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ROWS = 2_000_000, MAX_GROUPS = 256;
    private static final String SCOPE = "Native Java ordinary one-way Tukey-Kramer all-pairs comparisons. Declare independent Gaussian observations with a common population variance; the complete declared group family determines adjusted p-values and simultaneous confidence intervals. Input is UTF-8 TSV with group and value columns. Labels remain literal and first-appearance order is preserved. No missing observations are dropped or imputed. R and Prism are not required. Factorial marginal means, paired/repeated measurements and mixed models require their own justified model.";
    private static final String ACTION = "SKIP3: dependent analysis skipped when a permitted input or output prerequisite is unavailable; supply an accessible file/directory and retry NEW output. FAIL2: no complete result; preserve input and partials, read FAILURE.txt and WARNINGS.md, correct the named cause and retry NEW output. Input/capacity/numeric failures never admit a prefix of groups or comparisons. Require exit 0 and a valid COMPLETED.json. This method does not establish a complete GraphPad Prism replacement or whole-paper reproduction.";
    private TukeyKramerCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }

    public static int run(String[] args, PrintStream output, PrintStream error) {
        Path owned = null;
        try {
            if (args.length == 0 || args.length == 1 && args[0].equals("--help")) {
                output.println("posthoc-tools tukey --input GROUP_VALUE.tsv --out NEW_DIRECTORY [--confidence 0.95]\n" + SCOPE + "\n" + ACTION);
                output.flush(); return output.checkError() ? 2 : 0;
            }
            if (!args[0].equals("tukey") || (args.length != 5 && args.length != 7)) throw new IOException("Use posthoc-tools --help");
            var options = new LinkedHashMap<String, String>();
            for (int i = 1; i < args.length; i += 2) {
                if (!Set.of("--input", "--out", "--confidence").contains(args[i]) || options.putIfAbsent(args[i], args[i+1]) != null)
                    throw new IOException("Unknown or duplicate option: " + args[i]);
            }
            if (!options.keySet().containsAll(Set.of("--input", "--out"))) throw new IOException("Declare --input and --out");
            double confidence = Double.parseDouble(options.getOrDefault("--confidence", "0.95"));
            Path input = Path.of(options.get("--input")).toAbsolutePath().normalize();
            if (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) throw new MissingPrerequisite("Input file is unavailable: " + input);
            if (Files.isSymbolicLink(input) || !Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Require a regular unlinked input file");
            input = input.toRealPath();
            Path out = destination(Path.of(options.get("--out")), input);
            Files.createDirectories(out.getParent()); Files.createDirectory(out); owned = out;
            write(out.resolve("WARNINGS.md"), SCOPE + "\n\n" + ACTION + "\n");
            if (Files.size(input) > MAX_BYTES) throw new IOException("Complete input exceeds 64 MiB; no prefix admitted");
            byte[] raw = Files.readAllBytes(input);
            if (raw.length > MAX_BYTES) throw new IOException("Input grew beyond 64 MiB; no prefix admitted");
            Files.write(out.resolve("input.tsv"), raw, StandardOpenOption.CREATE_NEW);
            String inputSha = digest(raw);
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            } catch (java.nio.charset.CharacterCodingException malformed) {
                throw new IOException("Input is not valid UTF-8; original bytes are retained", malformed);
            }
            boolean bom = text.startsWith("\ufeff");
            if (bom) text = text.substring(1);
            var grouped = new LinkedHashMap<String, ArrayList<Double>>();
            long rows = 0;
            try (var lines = text.lines()) {
                var iterator = lines.iterator();
                if (!iterator.hasNext() || !iterator.next().equals("group\tvalue")) throw new IOException("Require exact group<TAB>value header");
                while (iterator.hasNext()) {
                    String line = iterator.next(); rows++;
                    if (rows > MAX_ROWS) throw new IOException("Complete input exceeds two million observations; no rows removed");
                    int tab = line.indexOf('\t');
                    if (tab < 1 || line.indexOf('\t', tab+1) >= 0) throw new IOException("Require two columns at data row " + rows);
                    String label = line.substring(0, tab), number = line.substring(tab+1);
                    if (label.isBlank() || label.chars().anyMatch(c -> c < 32 || c == 127)) throw new IOException("Invalid literal group label at data row " + rows);
                    if (!number.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new IOException("Require a finite decimal value at data row " + rows + "; missing values are not removed");
                    double value = Double.parseDouble(number);
                    if (!Double.isFinite(value)) throw new IOException("Nonfinite value at data row " + rows);
                    grouped.computeIfAbsent(label, ignored -> new ArrayList<>()).add(value);
                    if (grouped.size() > MAX_GROUPS) throw new IOException("Complete family exceeds 256 groups; groups are not truncated");
                }
            }
            List<String> labels = List.copyOf(grouped.keySet());
            double[][] values = new double[labels.size()][];
            for (int g = 0; g < labels.size(); g++) values[g] = grouped.get(labels.get(g)).stream().mapToDouble(Double::doubleValue).toArray();
            TukeyKramer.Result result = TukeyKramer.compare(values, confidence);
            StringBuilder groups = new StringBuilder("index\tgroup\tn\tmean\n");
            for (var group : result.groups()) groups.append(group.position()).append('\t').append(labels.get(group.position())).append('\t').append(group.size()).append('\t').append(group.mean()).append('\n');
            StringBuilder pairs = new StringBuilder("first_group\tsecond_group\tdifference_second_minus_first\tstandard_error\tstudentized_range_q\tadjusted_p\tlower\tupper\n");
            boolean zeroProbability = false;
            for (var comparison : result.comparisons()) {
                pairs.append(labels.get(comparison.firstGroup())).append('\t').append(labels.get(comparison.secondGroup())).append('\t')
                    .append(comparison.difference()).append('\t').append(comparison.standardError()).append('\t').append(comparison.studentizedRange()).append('\t')
                    .append(comparison.adjustedPValue()).append('\t').append(comparison.lowerConfidenceLimit()).append('\t').append(comparison.upperConfidenceLimit()).append('\n');
                zeroProbability |= comparison.adjustedPValue() == 0;
            }
            write(out.resolve("groups.tsv"), groups.toString()); write(out.resolve("comparisons.tsv"), pairs.toString());
            var report = new LinkedHashMap<String, Object>();
            report.put("profile", "native-tukey-kramer-oneway-v1"); report.put("observations", rows);
            report.put("groups", labels.size()); report.put("comparisons", result.comparisons().size());
            report.put("difference_direction", "second minus first"); report.put("confidence_level", confidence);
            report.put("residual_degrees_of_freedom", result.residualDegreesOfFreedom());
            report.put("pooled_residual_sd", result.residualStandardDeviation()); report.put("critical_q", result.criticalStudentizedRange());
            report.put("probability_method", result.probabilityMethod()); report.put("positive_tail_evaluations", result.positiveTailEvaluations());
            report.put("input_sha256", inputSha); report.put("utf8_bom_present", bom);
            report.put("java_version", System.getProperty("java.version")); report.put("runtime_dependencies", "Java only; JDistlib is bundled");
            report.put("input_limits", Map.of("bytes", MAX_BYTES, "observations", MAX_ROWS, "groups", MAX_GROUPS));
            report.put("numerical_zero_adjusted_p_observed", zeroProbability);
            write(out.resolve("analysis.json"), NextflowJson.json(report) + "\n");
            if (zeroProbability) write(out.resolve("NUMERIC-WARNING.txt"), "Some adjusted p-values are zero at floating-point resolution. Preserve q and degrees of freedom; a returned numeric zero does not establish an exactly zero mathematical probability.\n");
            if (!inputSha.equals(pin(input).get("sha256"))) throw new IOException("Source input changed during analysis; captured original input is retained");
            var manifest = new LinkedHashMap<String, Object>();
            for (String name : List.of("input.tsv", "groups.tsv", "comparisons.tsv", "analysis.json", "WARNINGS.md")) manifest.put(name, pin(out.resolve(name)));
            if (zeroProbability) manifest.put("NUMERIC-WARNING.txt", pin(out.resolve("NUMERIC-WARNING.txt")));
            write(out.resolve("manifest.json"), NextflowJson.json(manifest) + "\n");
            output.println("PASS native Tukey-Kramer: " + labels.size() + " groups, " + result.comparisons().size() + " comparisons. Warnings: " + out.resolve("WARNINGS.md") + (zeroProbability ? "; see NUMERIC-WARNING.txt" : ""));
            output.flush(); error.flush();
            if (output.checkError() || error.checkError()) throw new IOException("Console recording failed; no complete result");
            write(out.resolve("COMPLETED.json"), NextflowJson.json(Map.of("status", "PASS_NATIVE_TUKEY_KRAMER", "profile", "native-tukey-kramer-oneway-v1", "manifest", pin(out.resolve("manifest.json")), "comparisons", result.comparisons().size())) + "\n");
            return 0;
        } catch (Exception failure) {
            int code = failure instanceof MissingPrerequisite || failure instanceof java.nio.file.AccessDeniedException ? 3 : 2;
            String message = (code == 3 ? "SKIP3: dependent analysis skipped. " : "FAIL2: no complete result admitted. ") + failure.getMessage() + "\n" + ACTION + "\n";
            if (owned != null) try {
                Files.deleteIfExists(owned.resolve("COMPLETED.json")); write(owned.resolve("FAILURE.txt"), message);
            } catch (IOException recording) { message += "Failure recording also failed: " + recording + "\n"; }
            error.print(message); error.flush(); return code;
        }
    }

    private static Path destination(Path requested, Path input) throws IOException {
        Path absolute = requested.toAbsolutePath().normalize();
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Output exists; preserve it and choose NEW output");
        Path parent = absolute.getParent(); if (parent == null) throw new IOException("Output needs a parent directory");
        while (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) parent = parent.getParent();
        Path resolved = parent.toRealPath().resolve(parent.relativize(absolute)).normalize();
        if (resolved.startsWith(input) || input.startsWith(resolved)) throw new IOException("Output overlaps the protected input");
        return resolved;
    }
    private static void write(Path path, String text) throws IOException { Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
    private static String digest(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static Map<String, Object> pin(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) { byte[] buffer = new byte[1048576]; for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n); }
        return Map.of("bytes", Files.size(path), "sha256", HexFormat.of().formatHex(digest.digest()));
    }
    private static final class MissingPrerequisite extends IOException {
        private static final long serialVersionUID = 1L;
        MissingPrerequisite(String message) { super(message); }
    }
}
