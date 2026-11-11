/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import org.jlinalg.sequence.StremeEnrichment;
import org.jlinalg.sequence.StremeFisherAudit;

/** Independent statistical component example; this does not run motif discovery. */
public final class Example {
    private Example() { }
    /** @param args no arguments for prepared synthetic counts; --xml VERSION ORIGINAL.xml for an XML audit */
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                emit(System.err, "WARNING: prepared synthetic discovery-control counts. Training values are selection scores; this example does not discover motifs or validate a sequence partition.");
                table("training-selection-score", new StremeEnrichment.Table(60, 60, 45, 5));
                table("declared-heldout-table", new StremeEnrichment.Table(20, 20, 17, 1));
            } else if (args.length == 3 && args[0].equals("--xml")) {
                var audit = StremeFisherAudit.read(Path.of(args[2]), args[1], StremeFisherAudit.Limits.defaults(), () -> { });
                emit(System.out, "STATISTICAL COMPONENT CHECKED: version=" + args[1] + "; motifs=" + audit.motifs().size()
                        + "; holdout-declared=" + audit.hasHoldout() + "; source-sha256=" + audit.source().sha256());
                for (String warning : audit.warnings()) emit(System.err, "WARNING: " + warning);
                for (var motif : audit.motifs()) {
                    emit(System.out, motif.raw().attribute("id"));
                    result("training-selection-score", motif.training());
                    result(audit.hasHoldout() ? "declared-heldout-table" : "empty-test-sentinel", motif.heldOut());
                }
            } else {
                throw new IllegalArgumentException("Use no arguments, or --xml 5.5.4|5.5.9 ORIGINAL.xml");
            }
        } catch (StremeFisherAudit.UnsupportedInput failure) {
            emit(System.err, "SKIP3 — STATISTICAL AUDIT SKIPPED: " + failure.getMessage());
            emit(System.err, "Required action: retain the original result; use the matching edition/objective audit. Do not substitute Fisher for a binomial or central-distance test.");
            System.exit(3);
        } catch (Exception failure) {
            emit(System.err, "FAIL2 — NO STATISTICAL RECEIPT: " + failure);
            emit(System.err, "Required action: inspect the complete original input and declared limits, correct the cause, then rerun. No discovery analysis is admitted by this example.");
            System.exit(2);
        }
    }
    private static void table(String label, StremeEnrichment.Table table) {
        result(label, StremeEnrichment.fisher(table, StremeEnrichment.Limits.defaults(), () -> { }));
    }
    private static void result(String label, StremeEnrichment.FisherTail value) {
        emit(System.out, label + "\t" + value.table() + "\texact=" + value.numerator() + "/" + value.denominator()
                + "\tbinary64=" + value.probability() + "\tlog10=" + value.log10Probability());
    }
    private static void emit(PrintStream stream, String text) { stream.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
