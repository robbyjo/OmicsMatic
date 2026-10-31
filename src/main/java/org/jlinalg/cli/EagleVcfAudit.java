/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jlinalg.genetics.EagleGenotypes;

/** Full expected-output population audit against an explicit original-row plan. */
final class EagleVcfAudit {
    private EagleVcfAudit() { }
    enum Action { PHASED, UNPHASED_TARGET_ONLY, EXCLUDED }
    record Selection(Action action, String reason) {
        Selection {
            Objects.requireNonNull(action, "action");
            if (reason == null || reason.isBlank() || reason.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Require an explicit single-line Eagle row-selection reason");
        }
    }
    @FunctionalInterface interface Selector { Selection select(EagleVcf.Row input) throws IOException; }
    @FunctionalInterface interface Ledger { void row(EagleVcf.Row input, EagleVcf.Row output, Selection selection) throws IOException; }
    record Result(EagleVcf.Report input, EagleVcf.Report output, EagleGenotypes.Report genotypes,
            long excludedRows, String phasedContig) { }

    static Result compare(Path source, Path output, EagleGenotypes.Options options, EagleVcf.Limits limits,
            Selector selector, Ledger ledger, Runnable check) throws IOException {
        Objects.requireNonNull(selector, "selector"); Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(options, "options"); Objects.requireNonNull(check, "check");
        try (var input = EagleVcf.open(source, limits, check); var actual = EagleVcf.open(output, limits, check)) {
            var audit = new EagleGenotypes.Audit(input.samples(), actual.samples(), options,
                    limits.text().variants(), limits.text().genotypeCells());
            long expected = 0, excluded = 0; String phasedContig = null; EagleVcf.Row before;
            while ((before = input.next()) != null) {
                check.run(); Selection selection = Objects.requireNonNull(selector.select(before), "selection");
                if (selection.action() == Action.EXCLUDED) { excluded++; ledger.row(before, null, selection); continue; }
                if (selection.action() == Action.PHASED) {
                    if (phasedContig == null) phasedContig = before.contig();
                    else if (!phasedContig.equals(before.contig())) throw new IOException("Expected phased rows span different literal contigs; one Eagle run phases one selected chromosome");
                    if (before.alternateCount() != 1) throw new IOException("Selected phased target row must declare exactly one alternate allele");
                }
                EagleVcf.Row after = actual.next();
                if (after == null) throw new IOException("Eagle output ended before complete expected source row " + before.row());
                try {
                    audit.accept(before.identity(), before.calls(), after.identity(), after.calls(),
                            selection.action() == Action.PHASED ? EagleGenotypes.Disposition.PHASED : EagleGenotypes.Disposition.UNPHASED_TARGET_ONLY);
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    throw new IOException("Eagle output contract failed at source row " + before.row() + ": " + invalid.getMessage(), invalid);
                }
                expected++; ledger.row(before, after, selection);
            }
            if (actual.next() != null) throw new IOException("Eagle output has unexpected extra rows after the complete selection plan");
            if (!input.report().complete() || !actual.report().complete()) throw new IOException("Incomplete Eagle input/output EOF admission");
            check.run(); return new Result(input.report(), actual.report(), audit.finish(expected), excluded, phasedContig);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new IOException("Eagle complete genotype audit failed: " + invalid.getMessage(), invalid);
        }
    }
}
