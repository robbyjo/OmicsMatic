/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemModel;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemResults;
import org.jlinalg.sequence.RsemSamAlignments;

/** Strict fitting recipes with explicit scientific settings and complete capacity overrides. */
final class RsemRecipe {
    private RsemRecipe() { }
    record Capacities(RsemInputs.Limits inputs, RsemInputAudit.Limits audit, RsemFit.Limits fit) { }
    record Spec(Path recipe, String sha256, String version, Path build, String buildReceiptSha256,
                List<Path> libraries, RsemInputs.Spec input, RsemFitRequest.Settings settings, Capacities limits) {
        Spec { libraries = List.copyOf(libraries); }
        void verify() throws IOException {
            if (!sha256.equals(RepeatInputs.hash(recipe, 2097152, () -> { }))) throw new IOException("RSEM recipe changed; preserve evidence and rerun with a reviewed request to NEW output");
        }
    }
    static Spec read(Path supplied) throws IOException {
        Path recipe = supplied.toAbsolutePath().normalize(); byte[] raw = AdmixtureInputs.raw(recipe, 2097152); var m = RepeatBundle.object(raw);
        keys(m, Set.of("schema_version", "source_version", "build_home", "build_receipt_sha256", "library_directories", "reference_prefix", "alignment", "cram_reference", "read_type", "read_type_tag", "settings", "limits"));
        require(whole(m.get("schema_version")) == 1, "Require RSEM fitting recipe schema1");
        String version = text(m, "source_version", false); require(RsemPayload.profiles().containsKey(version), "Unsupported exact RSEM source release");
        String receipt = text(m, "build_receipt_sha256", false); require(receipt.matches("[0-9a-f]{64}"), "Require the explicit observed source-build receipt SHA256");
        var release = RsemAbundance.Release.valueOf("V" + version.replace('.', '_'));
        var options = new RsemSamAlignments.Options(release, Math.toIntExact(whole(m.get("read_type"))), text(m, "read_type_tag", true), 25);
        Map<?,?> s = object(m.get("settings")); keys(s, Set.of("seed", "worker_threads", "strand", "posterior", "estimate_read_start", "append_names", "single_end_fragment"));
        RsemFitRequest.Fragment fragment = null;
        if (s.containsKey("single_end_fragment")) { var f = object(s.get("single_end_fragment")); keys(f, Set.of("mean", "standard_deviation")); fragment = new RsemFitRequest.Fragment(number(f.get("mean")), number(f.get("standard_deviation"))); }
        var settings = new RsemFitRequest.Settings(whole(s.get("seed")), Math.toIntExact(whole(s.get("worker_threads"))),
                enumeration(s, "strand", RsemFitRequest.Strand.class), enumeration(s, "posterior", RsemFitRequest.Posterior.class), bool(s, "estimate_read_start"), bool(s, "append_names"), fragment);
        var capacities = override(defaults(), m.containsKey("limits") ? object(m.get("limits")) : Map.of());
        RsemFitRequest.flags(options, settings, capacities.fit().process().threads());
        return new Spec(recipe, AdmixtureInputs.digest(raw), version, resolve(recipe, text(m, "build_home", false)), receipt,
                paths(recipe, m.get("library_directories")), new RsemInputs.Spec(resolve(recipe, text(m, "reference_prefix", false)), resolve(recipe, text(m, "alignment", false)),
                    m.containsKey("cram_reference") ? resolve(recipe, text(m, "cram_reference", false)) : null, options), settings, capacities);
    }
    static Capacities defaults() {
        var process = new RsemProcess.Limits(180000, 3221225472L, 180, 67108864, 256, 268435456, 4096, 8, 64, 2147483648L, 25);
        return new Capacities(new RsemInputs.Limits(16777216, 67108864, 256, 1048576, new RsemReference.Limits(100, 100, 1000000, 1000, 16777216, 1000000)),
            new RsemInputAudit.Limits(new RsemSamAlignments.Limits(10000, 10000, 100000, 10000, 1000, 100000, 1000000, 10000000, 10000, 1000000, 10000, 16777216), new RsemCramReference.Limits(1000, 1000000, 1000000, 1000000, 1048576, 1048576), process, 1048576, 3600000),
            new RsemFit.Limits(process, new RsemFitOutputs.Limits(10000, 100000, 1000, 1000000, 100000, new RsemModel.Limits(100, 10000, 10000, 10000, 100000, 1000000, 1000000),
                new RsemResults.Limits(100, 100, 100, 10000, 1000000, 1000000, 1000000), new RepeatBundle.Limits(268435456, 4096)), 1048576, 3600000));
    }
    static Map<String,Object> limitValues() throws IOException { return numericRecord(defaults()); }
    private static Map<String,Object> numericRecord(Object fixed) throws IOException {
        var result = new java.util.LinkedHashMap<String,Object>();
        try { for (var part : fixed.getClass().getRecordComponents()) { Object value = part.getAccessor().invoke(fixed); result.put(part.getName(), value instanceof Number ? value : numericRecord(value)); } }
        catch (ReflectiveOperationException invalid) { throw new IOException("Cannot export the fixed RSEM capacity records", invalid); }
        return result;
    }
    /** Only fixed, locally declared capacity-record types are traversed; no JSON class names are accepted. */
    private static <T> T override(T defaults, Map<?,?> values) throws IOException {
        var type = defaults.getClass(); var components = type.getRecordComponents(); var allowed = new HashSet<String>();
        for (var c : components) allowed.add(c.getName()); keys(values, allowed);
        var args = new Object[components.length]; var types = new Class<?>[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                var c = components[i]; types[i] = c.getType(); Object previous = c.getAccessor().invoke(defaults);
                if (!values.containsKey(c.getName())) args[i] = previous;
                else if (c.getType().isRecord()) args[i] = override(previous, object(values.get(c.getName())));
                else {
                    long n = whole(values.get(c.getName())); require(n >= 0 && n <= 1000000000000L, "Capacity exceeds the supported complete-operation range: " + c.getName());
                    if (c.getType() == int.class) args[i] = Math.toIntExact(n); else if (c.getType() == long.class) args[i] = n; else throw new IOException("Unsupported fixed capacity type");
                }
            }
            @SuppressWarnings("unchecked") T result = (T)type.getDeclaredConstructor(types).newInstance(args); return result;
        } catch (InvocationTargetException invalid) { throw new IOException("Invalid complete RSEM capacity: " + invalid.getCause(), invalid.getCause()); }
        catch (ReflectiveOperationException | ArithmeticException invalid) { throw new IOException("Invalid complete RSEM capacity", invalid); }
    }
    static Map<?,?> object(Object value) throws IOException { if (value instanceof Map<?,?> result) return result; throw new IOException("Expected a complete RSEM JSON object"); }
    static void keys(Map<?,?> value, Set<String> allowed) throws IOException { for (Object key : value.keySet()) require(allowed.contains(key), "Unknown RSEM field: " + key); }
    static String text(Map<?,?> value, String key, boolean empty) throws IOException { if (!(value.get(key) instanceof String result) || (!empty && result.isBlank()) || result.length() > 4096 || result.codePoints().anyMatch(Character::isISOControl)) throw new IOException("Require explicit single-line RSEM text: " + key); return result; }
    static Path resolve(Path recipe, String value) { return recipe.getParent().resolve(value).toAbsolutePath().normalize(); }
    static List<Path> paths(Path recipe, Object value) throws IOException { if (!(value instanceof List<?> list)) throw new IOException("Require an explicit RSEM path array"); var result = new ArrayList<Path>(); for (Object item : list) { require(item instanceof String && !((String)item).isBlank() && !((String)item).codePoints().anyMatch(Character::isISOControl), "Invalid RSEM path array entry"); result.add(resolve(recipe, (String)item)); } require(result.size() <= 128 && Set.copyOf(result).size() == result.size(), "Duplicate or excessive RSEM roots"); return List.copyOf(result); }
    static long whole(Object value) throws IOException { if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != n.longValue() || n.longValue() < 0 || n.longValue() > 1000000000000L) throw new IOException("Require an exact nonnegative RSEM integer within1e12"); return n.longValue(); }
    private static double number(Object value) throws IOException { if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IOException("Require a finite RSEM numeric value"); return n.doubleValue(); }
    private static boolean bool(Map<?,?> value, String key) throws IOException { if (!(value.get(key) instanceof Boolean result)) throw new IOException("Require explicit RSEM boolean: " + key); return result; }
    private static <T extends Enum<T>> T enumeration(Map<?,?> value, String key, Class<T> type) throws IOException { try { return Enum.valueOf(type, text(value, key, false)); } catch (IllegalArgumentException invalid) { throw new IOException("Unsupported RSEM setting: " + key, invalid); } }
    private static void require(boolean valid, String message) throws IOException { if (!valid) throw new IOException(message); }
}
