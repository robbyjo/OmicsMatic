/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jlinalg.sequence.MemeDiscoverySites;
import org.jlinalg.sequence.MemeMotifInputs;
import org.jlinalg.sequence.MemeXml;

/** Complete original file family with independent XML science, text matrix parity and original HTML replay. */
final class MemeDiscoveryOutput {
    private MemeDiscoveryOutput() { }
    record Audit(Path root, MemeDiscoverySites.Audit scientific, Map<String, Object> inventory,
                 NextflowFiles.Pin text, NextflowFiles.Pin html, List<String> warnings) {
        Audit { inventory = Map.copyOf(inventory); warnings = List.copyOf(warnings); }
        void verify(MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
            require(inventory.equals(MemeRuntimeFiles.tree(root, MemeDiscoveryInputs.treeLimits(request), check)), "Original complete MEME output family changed");
        }
        Map<String, Object> document() throws IOException {
            return Map.of("motifs", scientific.motifs().size(), "contributing_sites", scientific.motifs().stream().mapToLong(m -> m.sites().size()).sum(),
                "sequence_diagrams", scientific.scans().size(), "selected_hits", scientific.scans().stream().mapToLong(s -> s.independent().hits().size()).sum(),
                "XML", Map.of("bytes", scientific.source().bytes(), "sha256", scientific.source().sha256()), "text", MemeRuntimeFiles.pin(text), "html", MemeRuntimeFiles.pin(html),
                "scientific_projection_sha256", NextflowFiles.sha(NextflowJson.json(projection(scientific)).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "warnings", warnings, "scope", scientific.motifs().isEmpty()
                    ? "Complete original zero-motif XML/input/settings/stop-reason and text bindings; exact original HTML replay. No motifs, site scores, diagrams or figures were produced; biological absence is not established."
                    : "Independent complete canonical-DNA XML geometry/counts/prior scores/position tails/diagrams; all text PWMs/centibit matrices; complete original HTML replay parity; complete decoded source figure carriers. Original EM/motif significance and figure pixels are supplied by the guarded upstream program, not independently reimplemented.");
        }
    }
    static Audit read(Path root, MemeDiscoveryRequest.Request request, MemeDiscoveryInputs.Captured inputs, List<String> command, Runnable check) throws IOException {
        var before = MemeRuntimeFiles.tree(root, MemeDiscoveryInputs.treeLimits(request), check);
        var scientific = MemeDiscoveryEmpty.read(root.resolve("meme.xml"), request, inputs, check);
        var xml = scientific.source().root(); var model = xml.one("model");
        var normalized = new ArrayList<>(command); require(!normalized.isEmpty(), "Missing original MEME command binding"); normalized.set(0, "meme");
        require(model.one("command_line").text().strip().equals(String.join(" ", normalized)), "Original MEME complete command differs from the typed request and captured carriers");
        require(xml.one("training_set").attribute(request.legacy() ? "datafile" : "primary_sequences").equals(command.get(1)), "Original MEME primary input carrier differs");
        require(model.one("background_frequencies").attribute("source").equals(command.get(command.indexOf("-bfile") + 1)), "Original MEME background carrier differs");
        require(integer(model.one("maxiter").text().strip()) == request.iterations(), "Original MEME iteration limit differs"); precision(model.one("distance").text().strip(), request.distance().doubleValue(), 6);
        String cutoff = model.one("evalue_threshold").text().strip();
        if (request.maximumEvalue() == null) require(cutoff.equals("inf"), "Original MEME unbounded E-value stopping choice differs");
        else precision(cutoff, request.maximumEvalue().doubleValue(), 6);
        if (request.minimumSites() != null) require(integer(model.one("minsites").text().strip()) == request.minimumSites(), "Original MEME effective minimum sites differs; retain source warnings instead of silently accepting a clamped request");
        if (request.maximumSites() != null) require(integer(model.one("maxsites").text().strip()) == request.maximumSites(), "Original MEME effective maximum sites differs; retain source warnings instead of silently accepting a clamped request");
        if (!request.legacy()) {
            require(integer(model.one("maxsize").text().strip()) == request.limit("bases"), "Original MEME dataset capacity differs");
            long expectedSearch = request.searchSize() == 0 ? inputs.population().bases() : Math.min(request.searchSize(), inputs.population().bases());
            require(integer(model.one("searchsize").text().strip()) == expectedSearch && integer(model.one("brief").text().strip()) == inputs.population().records().size(), "Original MEME sampling/output-population setting differs");
        }
        var names = new TreeSet<String>(Set.of(".", "meme.xml", "meme.txt", "meme.html"));
        if (request.legacy()) names.add("help.gif");
        for (int i = 1; i <= scientific.motifs().size(); i++) for (String direction : List.of("", "_rc")) {
            String stem = "logo" + direction + i; names.add(stem + ".png"); names.add(stem + ".eps");
            png(root.resolve(stem + ".png"), request, check); eps(root.resolve(stem + ".eps"), request, check);
        }
        require(before.keySet().equals(names), "Original MEME output family is incomplete or contains unexpected files/directories");
        if (request.legacy()) {
            byte[] gif = NextflowFiles.bytes(root.resolve("help.gif"), 1048576); require(gif.length > 10 && (new String(gif, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("GIF89a")
                || new String(gif, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("GIF87a")), "Original legacy MEME help image is incomplete");
        }
        Path text = root.resolve("meme.txt"); int maximum = Math.toIntExact(Math.min(request.limit("file_bytes"), 268435456));
        if (scientific.motifs().isEmpty()) MemeDiscoveryEmpty.text(NextflowFiles.utf8(NextflowFiles.bytes(text, maximum)), scientific);
        else {
        var document = MemeMotifInputs.read(text, new MemeMotifInputs.Limits(maximum, maximum, request.motifs(), request.maximumWidth(),
            Math.max(4L, 4L * request.motifs() * request.maximumWidth()), request.outputLimits().xml()), check);
        require(document.entries().size() == scientific.motifs().size(), "Original MEME text/XML complete motif populations differ");
        for (int i = 0; i < document.entries().size(); i++) {
            var actual = document.entries().get(i); var original = scientific.motifs().get(i).raw();
            require(actual.model().id().equals(original.attribute("name")) && actual.model().alternateId().equals(original.attributes().getOrDefault("alt", ""))
                && actual.model().sites() == integer(original.attribute("sites")), "Original MEME text/XML motif identifiers/site counts differ");
            require(actual.model().evalue().compareTo(decimal(original.attribute("e_value"))) == 0, "Original MEME text/XML motif E-value differs");
            var rows = original.one("probabilities").one("alphabet_matrix").children();
            require(rows.size() == actual.probabilityTokens().size(), "Original MEME text/XML PWM widths differ");
            for (int row = 0; row < rows.size(); row++) for (int b = 0; b < 4; b++)
                require(decimal(actual.probabilityTokens().get(row).get(b)).compareTo(decimal(rows.get(row).children().get(b).text().strip())) == 0, "Original MEME text/XML raw PWM scalar differs");
            BigDecimal e = decimal(original.attribute("e_value")); require(e.signum() >= 0, "Negative original MEME motif E-value");
            if (original.attributes().containsKey("p_value")) { var p = decimal(original.attribute("p_value")); require(p.signum() >= 0 && p.compareTo(BigDecimal.ONE) <= 0, "Original MEME motif P-value outside [0,1]"); }
        }
        scoreMatrices(text, scientific, inputs, request, check);
        }
        var html = NextflowFiles.pin(root.resolve("meme.html"), request.limit("file_bytes"), check);
        String carrier = NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("meme.html"), maximum));
        require(carrier.startsWith("<!DOCTYPE") && carrier.stripTrailing().endsWith("</html>") && carrier.contains("MEME"), "Original MEME HTML carrier is incomplete");
        var warnings = new ArrayList<String>(inputs.population().warnings());
        if (scientific.motifs().isEmpty()) warnings.addAll(scientific.warnings());
        warnings.add("Original MEME EM fitting and motif-significance estimates come from the exact source-built guarded upstream profile. The Java site/count/score/diagram checks are independent; they do not rederive motif significance or certify biology.");
        warnings.add("HTML is bound to audited XML by exact replay through the same guarded original report helper. This is source-renderer consistency, not an independent browser/script/security or every-rendered-field certificate. Source figure carriers are complete and decoded; their pixels are not an independent scientific reimplementation.");
        warnings.add(scientific.motifs().isEmpty() ? "The complete original empty text report and its stopping reason are checked; no text matrices were produced."
            : "The original detailed text report is retained unchanged. Complete text PWMs and centibit matrices are checked against XML; its other presentation tables remain original source output.");
        if (inputs.population().records().stream().anyMatch(input -> input.weight() != 1))
            warnings.add("For weighted inputs, the text score-matrix n follows original MEME wps: max(weighted total residues minus the unweighted record count times (width-1), 2), truncated to an integer. It is not the sum of weight times possible windows and is retained without mathematical repair.");
        require(before.equals(MemeRuntimeFiles.tree(root, MemeDiscoveryInputs.treeLimits(request), check)), "Complete original MEME result family changed during audit");
        return new Audit(root, scientific, before, NextflowFiles.pin(text, request.limit("file_bytes"), check), html, warnings);
    }
    static void replay(Audit audit, Path regenerated, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        require(audit.html().equals(NextflowFiles.pin(regenerated, request.limit("file_bytes"), check)), "Original MEME HTML differs from exact original-helper replay of its audited XML"); audit.verify(request, check);
    }
    static Map<String, Object> projection(MemeDiscoverySites.Audit audit) {
        if (audit.motifs().isEmpty()) {
            try { return Map.of("version", audit.source().root().attributes().get("version"), "motifs", List.of(),
                "scanned_sites_summary_present", false, "reason_for_stopping", audit.source().root().one("model").one("reason_for_stopping").text().strip()); }
            catch (IOException invalid) { throw new IllegalStateException(invalid); }
        }
        return Map.of("version", audit.source().root().attributes().get("version"), "motifs", audit.motifs().stream().map(m -> node(m.raw())).toList(),
            "scanned_sites_summary", node(uncheckedSummary(audit.source().root())));
    }
    private static MemeXml.Node uncheckedSummary(MemeXml.Node root) { try { return root.one("scanned_sites_summary"); } catch (IOException impossible) { throw new IllegalStateException(impossible); } }
    static Map<String, Object> node(MemeXml.Node n) {
        var attrs = new TreeMap<>(n.attributes()); attrs.remove("elapsed_time");
        return Map.of("name", n.name(), "attributes", attrs, "text", n.text().strip(), "children", n.children().stream().map(MemeDiscoveryOutput::node).toList());
    }
    private static void scoreMatrices(Path path, MemeDiscoverySites.Audit audit, MemeDiscoveryInputs.Captured inputs, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        String text = NextflowFiles.utf8(NextflowFiles.bytes(path, Math.toIntExact(Math.min(request.limit("file_bytes"), 268435456))));
        var lines = text.lines().iterator(); int motif = 0;
        while (lines.hasNext()) {
            check.run(); String line = lines.next().strip(); if (!line.startsWith("log-odds matrix:")) continue;
            require(motif < audit.motifs().size(), "Extra original MEME text score matrix"); var original = audit.motifs().get(motif++).raw(); int width = Math.toIntExact(integer(original.attribute("width")));
            var fields = new LinkedHashMap<String, String>(); var matcher = Pattern.compile("([A-Za-z][A-Za-z0-9_-]*)\\s*=\\s*([^\\s]+)").matcher(line.substring("log-odds matrix:".length()));
            int consumed = 0;
            while (matcher.find()) { require(line.substring("log-odds matrix:".length()).substring(consumed, matcher.start()).isBlank() && fields.putIfAbsent(matcher.group(1), matcher.group(2)) == null, "Invalid/repeated original MEME score metadata"); consumed = matcher.end(); }
            require(line.substring("log-odds matrix:".length()).substring(consumed).isBlank() && fields.keySet().equals(Set.of("alength", "w", "n", "bayes", "E")), "Incomplete original MEME text score metadata");
            require(integer(fields.get("alength")) == 4 && integer(fields.get("w")) == width, "Original MEME text score dimensions differ");
            require(integer(fields.get("n")) == reportedPossibleSites(inputs.population().records(), width) && decimal(fields.get("bayes")).compareTo(decimal(original.attribute("bayes_threshold"))) == 0
                && decimal(fields.get("E")).compareTo(decimal(original.attribute("e_value"))) == 0, "Original MEME text weighted-possible-sites/threshold/E metadata differs");
            var rows = original.one("scores").one("alphabet_matrix").children();
            for (int row = 0; row < width; row++) {
                require(lines.hasNext(), "Truncated original MEME text centibit matrix"); String[] values = lines.next().strip().split("\\s+"); require(values.length == 4, "Incomplete original MEME text centibit row");
                for (int b = 0; b < 4; b++) require(decimal(values[b]).compareTo(decimal(rows.get(row).children().get(b).text().strip())) == 0, "Original MEME text/XML centibit scalar differs");
            }
        }
        require(motif == audit.motifs().size(), "Original MEME text score matrix population is missing");
    }
    static long reportedPossibleSites(List<MemeDiscoverySites.Input> records, int width) {
        // Original src/display.c casts wps(dataset,w[,0]) to int. src/meme.h
        // subtracts the UNWEIGHTED sequence-count adjustment, with a floor of2.
        double weightedResidues = 0;
        for (var input : records) weightedResidues += input.weight() * input.bases().length();
        return (long) Math.max(weightedResidues - (double) records.size() * (width - 1), 2);
    }
    private static void png(Path path, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        byte[] raw = NextflowFiles.bytes(path, Math.toIntExact(Math.min(request.limit("file_bytes"), 268435456))); check.run();
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10}; require(raw.length >= 45 && Arrays.equals(signature, Arrays.copyOf(raw, 8)), "Missing/truncated original MEME PNG signature");
        int at = 8, width = 0, height = 0; boolean header = false, data = false, terminal = false;
        while (at < raw.length) {
            require(at <= raw.length - 12, "Truncated original MEME PNG chunk"); int length = signed32(raw, at); require(length >= 0 && length <= raw.length - at - 12, "Original MEME PNG chunk exceeds complete file");
            String type = new String(raw, at + 4, 4, java.nio.charset.StandardCharsets.US_ASCII); var crc = new java.util.zip.CRC32(); crc.update(raw, at + 4, length + 4);
            require(crc.getValue() == Integer.toUnsignedLong(signed32(raw, at + 8 + length)), "Original MEME PNG chunk CRC differs");
            if (!header) { require(type.equals("IHDR") && length == 13, "Original MEME PNG lacks its initial header"); width = signed32(raw, at + 8); height = signed32(raw, at + 12); require(width > 0 && height > 0 && width <= 4096 && height <= 4096, "Original MEME PNG dimensions exceed the scoped decoder"); header = true; }
            else require(!type.equals("IHDR"), "Duplicate original MEME PNG header");
            if (type.equals("IDAT")) data = true;
            if (type.equals("IEND")) { require(length == 0 && data && at + 12 == raw.length, "Incomplete/late original MEME PNG terminator"); terminal = true; }
            at += length + 12;
        }
        require(terminal, "Original MEME PNG has no complete terminal chunk");
        var image = ImageIO.read(new ByteArrayInputStream(raw)); require(image != null && image.getWidth() == width && image.getHeight() == height, "Original MEME PNG raster cannot be decoded"); image.flush(); check.run();
    }
    private static int signed32(byte[] raw, int at) { return (raw[at] & 255) << 24 | (raw[at + 1] & 255) << 16 | (raw[at + 2] & 255) << 8 | raw[at + 3] & 255; }
    private static void eps(Path path, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        String text = NextflowFiles.utf8(NextflowFiles.bytes(path, Math.toIntExact(Math.min(request.limit("file_bytes"), 268435456)))); check.run();
        require(text.startsWith("%!PS-Adobe-3.0 EPSF-3.0") && text.stripTrailing().endsWith("%%EOF") && text.contains("%%BoundingBox:") && text.contains("EndLogo"), "Original MEME EPS carrier is incomplete");
    }
    private static long integer(String token) throws IOException { try { require(token != null && token.matches("0|[1-9][0-9]{0,17}"), "Invalid original MEME output integer"); return Long.parseLong(token); } catch (NumberFormatException failure) { throw new IOException("Invalid original MEME output integer", failure); } }
    private static BigDecimal decimal(String token) throws IOException {
        try { require(token != null && token.length() <= 128, "Excessive original MEME decimal"); BigDecimal value = new BigDecimal(token); require(Math.abs((long) value.scale()) <= 4096, "Excessive original MEME decimal scale"); return value; }
        catch (NumberFormatException failure) { throw new IOException("Invalid original MEME output decimal", failure); }
    }
    private static void precision(String token, double expected, int digits) throws IOException {
        BigDecimal value = BigDecimal.valueOf(expected), source = decimal(token); BigDecimal quantum = BigDecimal.ONE.scaleByPowerOfTen(value.precision() - value.scale() - digits);
        require(source.subtract(value).abs().compareTo(quantum.divide(BigDecimal.valueOf(2)).add(value.abs().multiply(new BigDecimal("1e-10")))) <= 0, "Original MEME reported setting differs at source precision");
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
