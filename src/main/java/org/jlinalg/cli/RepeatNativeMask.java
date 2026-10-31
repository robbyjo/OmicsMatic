/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Native MASK execution with explicit provenance, complete verification and retained failures. */
final class RepeatNativeMask {
    private RepeatNativeMask() { }
    private static final long DIAGNOSTIC_RESERVE = 65536;
    record Result(Path work, Path masked, Path coverage, Path ledger, RepeatMaskAudit.Report audit,
            Map<String,String> outputSha256) {
        Result { outputSha256 = Map.copyOf(outputSha256); }
    }
    static Result execute(RepeatInputs.Prepared input, Path suppliedWork, Runnable check) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(check); var spec = input.spec();
        if (spec.search() || spec.operation() != RepeatRecipe.Operation.MASK || input.annotations() == null)
            throw new IOException("Native mask execution requires a prepared native MASK request with supplied annotations");
        input.verify(check);
        Path work = suppliedWork.toAbsolutePath().normalize();
        if (work.getParent() == null || !work.getParent().toRealPath().equals(work.getParent())
                || work.startsWith(input.work()) || input.work().startsWith(work))
            throw new IOException("Native result requires a canonical NEW directory disjoint from its protected preparation");
        for (var snapshot : input.snapshots()) if (snapshot.original().startsWith(work))
            throw new IOException("A protected native mask source may not lie inside its result directory");
        Budget budget = new Budget(input, check); budget.member(); Files.createDirectory(work);
        try {
            Path mask = work.resolve("native-masked.fa"); budget.member();
            var audit = RepeatMaskAudit.write(input.query().path(), RepeatFasta.Encoding.PLAIN_UTF8, mask,
                    input.annotations().hits(), spec.selection(), spec.mask(), RepeatInputs.stageLimits(spec.limits()),
                    spec.limits().indexBytes(), budget.availableBytes(), check);
            budget.bytes(Files.size(mask));
            Path coverage = work.resolve("native-coverage.tsv");
            try (var stream = budget.output(coverage)) {
                text(stream, "query\tlength\tcovered_bases\tchanged_characters\n");
                for (var sequence : audit.sequences()) {
                    check.run(); text(stream, sequence.query()+"\t"+sequence.length()+"\t"+sequence.coveredBases()+"\t"+sequence.changedCharacters()+"\n");
                }
            }
            String warning = "# Native repeat MASK: scope and required review\n\n"
                    + "This is a Java transformation of supplied annotations. No original RepeatMasker search was run or certified.\n"
                    + "Annotation completeness and biological absence are not inferred, including when zero rows or zero selected rows are present.\n"
                    + "Masking uses the explicit union of selected intervals; source names, headers and case outside the mask remain exact.\n"
                    + "Required action: review the assembly, annotation source, selection, coordinate policy and all zero-coverage queries. Retain protected inputs, provenance and output hashes.\n";
            try (var stream = budget.output(work.resolve("WARNINGS.md"))) { text(stream, warning); }
            input.verify(check);
            var hashes = new LinkedHashMap<String,String>();
            for (Path path : List.of(mask, coverage, work.resolve("WARNINGS.md")))
                hashes.put(path.toString(), RepeatInputs.hash(path, spec.limits().workspaceBytes(), check));
            Path ledger = work.resolve("native-mask.json"); var data = new LinkedHashMap<String,Object>();
            data.put("schema_version", 1); data.put("scope", "complete-native-annotation-mask-only");
            data.put("upstream_search_completed", false); data.put("external_annotation_completeness_certified", false);
            data.put("request", Map.of("recipe_sha256", spec.recipeSha256(), "assembly", spec.assembly(), "input_review", spec.review(),
                    "mask", spec.mask(), "selection", spec.selection(), "preparation", input.work().toString()));
            data.put("audit", audit);
            data.put("outputs", hashes.entrySet().stream().map(e -> Map.of("path", e.getKey(), "sha256", e.getValue())).toList());
            data.put("required_action", "Retain original inputs and provenance;review zero-coverage queries. This native result never certifies an original search or biological absence.");
            try (var stream = budget.output(ledger)) { text(stream, RepeatJson.json(data)+"\n"); }
            hashes.put(ledger.toString(), RepeatInputs.hash(ledger, spec.limits().workspaceBytes(), check));
            input.verify(check);
            for (var entry : hashes.entrySet()) if (!RepeatInputs.hash(Path.of(entry.getKey()), spec.limits().workspaceBytes(), check).equals(entry.getValue()))
                throw new IOException("Native mask result changed before final verification");
            return new Result(work, mask, coverage, ledger, audit, hashes);
        } catch (IOException | RuntimeException failure) {
            String message = failure.toString(); if (message.length() > 8192) message = message.substring(0, 8192);
            try { Files.writeString(work.resolve("NATIVE_MASK_FAILED.txt"), "RepeatMasker native MASK FAIL2: no complete result admitted.\n"+message
                    +"\nRequired action: retain these partial outputs and protected inputs,correct the named cause/capacity,and retry to a NEW output directory. No failed native file certifies a search or biological absence.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
            catch (IOException diagnostics) { failure.addSuppressed(diagnostics); }
            throw failure;
        }
    }
    private static void text(OutputStream output, String text) throws IOException { output.write(text.getBytes(StandardCharsets.UTF_8)); }
    private static final class Budget {
        private final long maximum;
        private final int maximumMembers;
        private final Runnable check;
        private long bytes;
        private int members;
        Budget(RepeatInputs.Prepared input, Runnable check) throws IOException {
            maximum = input.spec().limits().workspaceBytes()-DIAGNOSTIC_RESERVE;
            maximumMembers = input.spec().limits().fileCount()-1; this.check = check;
            Files.walkFileTree(input.work(), new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                    member(); if (!path.toRealPath().equals(path)) throw new IOException("Linked native preparation directory");
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    member(); if (!attributes.isRegularFile() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Nonregular native preparation member");
                    bytes(attributes.size()); return FileVisitResult.CONTINUE;
                }
            });
        }
        long availableBytes() throws IOException {
            if (maximum <= bytes) throw new IOException("Combined preparation/native-mask byte capacity exhausted;diagnostic reserve retained");
            return maximum-bytes;
        }
        void member() throws IOException { check.run(); if (++members > maximumMembers) throw new IOException("Combined preparation/native-mask member capacity exceeded;diagnostic slot retained"); }
        void bytes(long count) throws IOException { check.run(); bytes = Math.addExact(bytes, count); if (bytes > maximum) throw new IOException("Combined preparation/native-mask byte capacity exceeded;64KiBdiagnostic reserve retained"); }
        OutputStream output(Path path) throws IOException {
            member(); return new FilterOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                @Override public void write(int value) throws IOException { bytes(1); out.write(value); }
                @Override public void write(byte[] values, int offset, int length) throws IOException { bytes(length); out.write(values, offset, length); }
            };
        }
    }
}
