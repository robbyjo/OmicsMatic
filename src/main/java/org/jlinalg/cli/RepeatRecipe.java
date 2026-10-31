/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.RepeatAnnotations;

/** Closed, edition-specific RepeatMasker/native annotation requests; no command templates. */
final class RepeatRecipe {
    private RepeatRecipe() { }
    static final String NATIVE = "native-repeat-annotations-v1";
    static final String CURRENT = "linux-x86_64-repeatmasker-4.2.6-custom-rmblast";
    static final String HISTORICAL = "linux-x86_64-repeatmasker-4.1.5-dfam3.7-rmblast";
    enum Operation { CHECK, SEARCH, MASK, AUDIT }
    enum Mode { ANNOTATION_REUSE, CUSTOM_LIBRARY, SPECIES, SIMPLE_ONLY }
    enum Staging { EXACT_SOURCE, ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA }
    enum Speed { DEFAULT, SENSITIVE, QUICK, RUSH }
    enum Inclusion { INCLUDE, EXCLUDE }
    static final class UnavailableCapability extends IOException {
        private static final long serialVersionUID = 1L;
        UnavailableCapability(String message) { super(message); }
    }
    record Source(Path path, RepeatFasta.Encoding encoding) { }
    record Annotation(Source source, RepeatAnnotations.Format format) { }
    record Parameters(Speed speed, int parallelBatches, Inclusion rna, Inclusion lowComplexity,
            BigDecimal maximumDivergence) {
        List<String> flags(Mode mode, RepeatAnnotations.Mask mask) {
            var args = new ArrayList<String>(List.of("-engine", "rmblast", "-pa", Integer.toString(parallelBatches)));
            switch (speed) { case SENSITIVE -> args.add("-s"); case QUICK -> args.add("-q"); case RUSH -> args.add("-qq"); default -> { } }
            if (rna == Inclusion.EXCLUDE) args.add("-norna");
            if (lowComplexity == Inclusion.EXCLUDE) args.add("-nolow");
            if (mode == Mode.SIMPLE_ONLY) args.add("-noint");
            if (maximumDivergence != null) { args.add("-div"); args.add(maximumDivergence.toPlainString()); }
            if (mask == RepeatAnnotations.Mask.SOFT) args.add("-xsmall");
            if (mask == RepeatAnnotations.Mask.X) args.add("-x");
            return List.copyOf(args);
        }
    }
    record Limits(RepeatFasta.Limits fasta, RepeatAnnotations.Limits annotations, long inputBytes,
            long indexBytes, long retainedBytes, long workspaceBytes, int fileCount) {
        Limits {
            if (inputBytes < 1 || indexBytes < 1 || workspaceBytes < 65536 || fileCount < 8
                    || Math.addExact(Math.multiplyExact(7L, fasta.retainedBytes()),
                            Math.addExact(annotations.retainedBytes(), indexBytes)) > retainedBytes)
                throw new IllegalArgumentException("Retained capacity must cover seven bounded FASTA censuses, annotations and interval index;require positive input/workspace capacities");
        }
    }
    record ProcessLimits(long wallMillis, long addressSpaceBytes, long cpuSeconds, long fileBytes,
            int openFiles, long workspaceBytes, int fileCount, int processes, int threads,
            long sampledRssBytes, int monitorMillis) { }
    record Spec(Path recipe, String recipeSha256, Path resourceRoot, Operation operation, Mode mode,
            String profile, String assembly, Map<String,String> review, Source query, Source library,
            Annotation annotations, Source masked, String species, Staging staging, RepeatAnnotations.Mask mask,
            RepeatMaskAudit.Selection selection, Parameters parameters, Limits limits, ProcessLimits process) {
        Spec { review = Map.copyOf(review); }
        boolean search() { return mode != Mode.ANNOTATION_REUSE; }
        void verify() throws IOException {
            if (!AdmixtureInputs.digest(raw(recipe)).equals(recipeSha256)) throw new IOException("RepeatMasker recipe changed during preparation/analysis");
        }
    }
    static Spec read(Path supplied) throws IOException {
        try { return decode(supplied); }
        catch (IllegalArgumentException | ArithmeticException invalid) { throw new IOException("Invalid typed RepeatMasker recipe: " + invalid.getMessage(), invalid); }
    }
    private static Spec decode(Path supplied) throws IOException {
        Path recipe = supplied.toAbsolutePath().normalize(); byte[] bytes = raw(recipe);
        Map<?,?> m = object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()));
        keys(m, Set.of("schema_version", "operation", "mode", "profile", "resource_root", "assembly", "input_review", "query", "library", "annotations", "masked", "species", "staging_policy", "mask", "selection", "parameters", "limits", "process_limits"));
        whole(m, "schema_version", null, 1, 1);
        Operation operation = enumeration(m, "operation", Operation.class); Mode mode = enumeration(m, "mode", Mode.class);
        String profile = text(m, "profile"); boolean search = mode != Mode.ANNOTATION_REUSE;
        if (!(search ? Set.of(CURRENT, HISTORICAL) : Set.of(NATIVE)).contains(profile))
            throw new UnavailableCapability("Requested RepeatMasker edition/profile is unavailable;no historical edition is substituted: " + profile);
        if (profile.equals(CURRENT) && mode != Mode.CUSTOM_LIBRARY)
            throw new UnavailableCapability("The4.2.6 profile has a custom-library foundation;species/simple resources require a separately admitted profile");
        if (search && operation != Operation.CHECK && operation != Operation.SEARCH
                || !search && operation == Operation.SEARCH) throw new IOException("SEARCH/CHECK require explicit search mode;MASK/AUDIT require ANNOTATION_REUSE");
        Path root = recipe.getParent().resolve(text(m, "resource_root")).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) throw new IOException("RepeatMasker resource_root must be a canonical existing directory");
        String assembly = text(m, "assembly"); Map<?,?> reviewSource = object(m.get("input_review"));
        Set<String> reviewKeys = Set.of("organism", "genome_provenance", "repeat_library_provenance", "annotation_provenance", "coordinate_policy", "paper_settings_status");
        keys(reviewSource, reviewKeys); var review = new LinkedHashMap<String,String>();
        for (String key : reviewKeys) review.put(key, text(reviewSource, key));
        Source query = source(root, m.get("query")), library = null, masked = null;
        Staging staging = enumeration(m, "staging_policy", Staging.class);
        if (!search && staging != Staging.EXACT_SOURCE) throw new IOException("Native annotation reuse preserves original query identifiers and sequence case");
        RepeatAnnotations.Mask mask = enumeration(m, "mask", RepeatAnnotations.Mask.class);
        Annotation annotation = null; String species = null; Parameters parameters = null;
        RepeatMaskAudit.Selection selection = RepeatMaskAudit.Selection.all();
        if (search) {
            absent(m, "annotations", "masked", "selection");
            if (mode == Mode.CUSTOM_LIBRARY) { library = source(root, m.get("library")); absent(m, "species"); }
            else {
                absent(m, "library"); species = text(m, "species");
                if (!species.matches("[A-Za-z0-9][A-Za-z0-9 ._-]{0,255}")) throw new IOException("Species must be an explicit ASCII taxon name/identifier without shell metacharacters");
            }
            Map<?,?> p = object(m.get("parameters")); keys(p, Set.of("speed", "parallel_batches", "rna", "low_complexity", "maximum_divergence_percent"));
            BigDecimal divergence = null;
            if (p.containsKey("maximum_divergence_percent")) {
                String value = text(p, "maximum_divergence_percent");
                if (!value.matches("[0-9]{1,3}(?:\\.[0-9]{1,6})?")) throw new IOException("Divergence requires an exact non-exponent decimal string");
                divergence = new BigDecimal(value);
                if (divergence.signum() <= 0 || divergence.compareTo(BigDecimal.valueOf(100)) > 0)
                    throw new IOException("Original -div treats zero as unset;require an explicit value in (0,100] or omit the filter");
            }
            parameters = new Parameters(enumeration(p, "speed", Speed.class), (int)whole(p, "parallel_batches", null, 1, 1024),
                    enumeration(p, "rna", Inclusion.class), enumeration(p, "low_complexity", Inclusion.class), divergence);
            if (mode == Mode.SIMPLE_ONLY && parameters.lowComplexity() != Inclusion.INCLUDE)
                throw new IOException("SIMPLE_ONLY requires low-complexity/simple-repeat searches to remain enabled");
        } else {
            absent(m, "library", "species", "parameters"); Map<?,?> a = object(m.get("annotations"));
            keys(a, Set.of("path", "encoding", "format", "completion_evidence"));
            if (!text(a, "completion_evidence").equals("EXTERNAL_NOT_CERTIFIED")) throw new IOException("Imported annotation structure does not certify upstream completion or biological absence");
            annotation = new Annotation(sourceFields(root, a), enumeration(a, "format", RepeatAnnotations.Format.class));
            Map<?,?> s = object(m.get("selection")); keys(s, Set.of("classes", "class_names", "names", "repeat_names", "marked_overlap"));
            selection = new RepeatMaskAudit.Selection(enumeration(s, "classes", RepeatMaskAudit.Choice.class), labels(s, "class_names"),
                    enumeration(s, "names", RepeatMaskAudit.Choice.class), labels(s, "repeat_names"), enumeration(s, "marked_overlap", RepeatMaskAudit.MarkedOverlap.class));
            if (operation == Operation.AUDIT) masked = source(root, m.get("masked")); else absent(m, "masked");
        }
        Map<?,?> l = object(m.get("limits")); keys(l, Set.of("preset", "input_bytes", "fasta_characters", "fasta_residues", "fasta_per_sequence", "fasta_sequences", "fasta_line_characters", "fasta_header_characters", "fasta_retained_bytes", "annotation_characters", "annotation_rows", "annotation_line_characters", "annotation_retained_bytes", "index_retained_bytes", "retained_bytes", "workspace_bytes", "file_count"));
        if (!text(l, "preset").equals("bounded-repeat-v1")) throw new IOException("Unsupported RepeatMasker capacity preset");
        long input = whole(l, "input_bytes", 17179869184L, 1, 1000000000000L);
        var fasta = new RepeatFasta.Limits(input, whole(l,"fasta_characters",34359738368L,1,1000000000000L),whole(l,"fasta_residues",17179869184L,1,1000000000000L),
                whole(l,"fasta_per_sequence",1000000000L,1,1000000000000L),(int)whole(l,"fasta_sequences",1000000L,1,10000000),(int)whole(l,"fasta_line_characters",1048576L,64,16777216),
                (int)whole(l,"fasta_header_characters",8192L,1,1048576),whole(l,"fasta_retained_bytes",33554432L,65536,1099511627776L));
        var annotations = new RepeatAnnotations.Limits(whole(l,"annotation_characters",8589934592L,1,1000000000000L),whole(l,"annotation_rows",1000000L,1,1000000000L),
                (int)whole(l,"annotation_line_characters",65536L,64,16777216),whole(l,"annotation_retained_bytes",536870912L,1,1099511627776L));
        var limits = new Limits(fasta, annotations, input, whole(l,"index_retained_bytes",134217728L,1,1099511627776L),whole(l,"retained_bytes",1073741824L,1,1099511627776L),
                whole(l,"workspace_bytes",68719476736L,65536,1000000000000L),(int)whole(l,"file_count",4096L,8,1000000));
        ProcessLimits process = null;
        if (search) {
            Map<?,?> q = object(m.get("process_limits")); keys(q, Set.of("preset", "wall_millis", "address_space_bytes", "cpu_seconds", "file_bytes", "open_files", "workspace_bytes", "file_count", "processes", "threads", "sampled_rss_bytes", "monitor_millis"));
            if (!text(q,"preset").equals("bounded-process-v1")) throw new IOException("Unsupported process capacity preset");
            process = new ProcessLimits(whole(q,"wall_millis",3600000L,1,604800000),whole(q,"address_space_bytes",17179869184L,16777216,1099511627776L),whole(q,"cpu_seconds",3600L,1,604800),
                    whole(q,"file_bytes",17179869184L,1,1000000000000L),(int)whole(q,"open_files",256L,8,1048576),whole(q,"workspace_bytes",limits.workspaceBytes(),1,limits.workspaceBytes()),
                    (int)whole(q,"file_count",4096L,8,1000000),(int)whole(q,"processes",256L,1,4096),(int)whole(q,"threads",256L,1,1048576),whole(q,"sampled_rss_bytes",8589934592L,1,1099511627776L),(int)whole(q,"monitor_millis",100L,1,1000));
            if (16L + 4L * parameters.parallelBatches() > process.threads()) throw new IOException("Original RMBlast uses four engine threads per parallel batch;include orchestration overhead in the thread capacity");
        } else absent(m, "process_limits");
        return new Spec(recipe, AdmixtureInputs.digest(bytes), root, operation, mode, profile, assembly, review, query, library, annotation, masked, species, staging, mask, selection, parameters, limits, process);
    }
    private static Source source(Path root, Object supplied) throws IOException { Map<?,?> m = object(supplied); keys(m,Set.of("path","encoding")); return sourceFields(root,m); }
    private static Source sourceFields(Path root, Map<?,?> m) throws IOException {
        Path path = root.resolve(text(m,"path")).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IOException("RepeatMasker source escapes its declared resource_root");
        return new Source(path, enumeration(m,"encoding",RepeatFasta.Encoding.class));
    }
    private static byte[] raw(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path) || Files.size(path)>2097152) throw new IOException("Missing,linked,noncanonical or oversized RepeatMasker recipe");
        try (var input=Files.newInputStream(path)) { byte[] bytes=input.readNBytes(2097153); if(bytes.length>2097152)throw new IOException("Recipe byte capacity exceeded"); return bytes; }
    }
    private static Map<?,?> object(Object value) throws IOException { if(!(value instanceof Map<?,?> map))throw new IOException("Expected a typed RepeatMasker JSON object");return map; }
    private static void keys(Map<?,?> map,Set<String> allowed)throws IOException { for(Object key:map.keySet())if(!allowed.contains(key))throw new IOException("Unknown RepeatMasker recipe field: "+key); }
    private static void absent(Map<?,?> map,String... names)throws IOException { for(String name:names)if(map.containsKey(name))throw new IOException("Field is inapplicable to this RepeatMasker operation/mode: "+name); }
    private static String text(Map<?,?> map,String key)throws IOException { if(!(map.get(key) instanceof String value)||value.isBlank()||value.length()>8192||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require explicit single-line RepeatMasker text: "+key);return value; }
    private static long whole(Map<?,?> map,String key,Long fallback,long minimum,long maximum)throws IOException {
        Object value=map.containsKey(key)?map.get(key):fallback;
        if(!(value instanceof Long number)||number<minimum||number>maximum)throw new IOException("Require an exact whole-number JSON literal within bounds: "+key);return number;
    }
    private static Set<String> labels(Map<?,?> map,String key)throws IOException {
        if(!(map.get(key) instanceof List<?> values))throw new IOException("Require an explicit repeat label array: "+key);
        var result=new java.util.LinkedHashSet<String>();for(Object value:values)if(!(value instanceof String text)||text.isBlank()||text.codePoints().anyMatch(Character::isISOControl)||!result.add(text))throw new IOException("Require unique nonempty exact repeat labels: "+key);return Set.copyOf(result);
    }
    private static <T extends Enum<T>> T enumeration(Map<?,?> map,String key,Class<T> type)throws IOException { try{return Enum.valueOf(type,text(map,key));}catch(IllegalArgumentException invalid){throw new IOException("Unsupported RepeatMasker choice: "+key,invalid);} }
}
