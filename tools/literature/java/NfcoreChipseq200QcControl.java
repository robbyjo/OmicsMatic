/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.workflow.NfcoreChipseq200Qc;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete original saved-QC replay and adverse numerical controls, without source refitting. */
public final class NfcoreChipseq200QcControl {
    private NfcoreChipseq200QcControl() { }
    private static final List<String> NEGATIVE = List.of("factor-tail", "normalized-tail", "transform-tail", "distance-tail", "score-tail", "spectrum-tail", "fraction-tail", "nonfinite-normalized-tail", "nonfinite-transform-tail", "nonfinite-distance-tail", "negative-count-tail", "ragged-count-tail", "missing-PC-tail", "zero-transform", "capacity");
    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("--complete")) {
            var verified = NfcoreEngineBundle.verify(Path.of(args[1]), () -> { }); var data = new NfcoreChipseq200Files(verified); data.verifySource(); var peaks = new NfcoreChipseq200Peaks(data, NfcoreChipseq200Peaks.mappings(data)); var reports = new ArrayList<Map<String, Object>>();
            for (var group : peaks.groups) if (group.multipleGroups() && group.replicates()) reports.add(NfcoreChipseq200QcFiles.audit(data, group, Path.of(args[2]), "vst"));
            if (reports.size() != 1) throw new IllegalStateException("Require the complete original one-antibody fixture, not a partial group selection");
            writeJson(Path.of(args[3]), Map.of("status", "PASS_COMPLETE_ORIGINAL_SAVED_QC_NATIVE_COMPONENT_ONLY", "antibodies", reports, "new_tool_credit", 0)); return;
        }
        if (args.length != 3 || !args[0].equals("--controls")) throw new IllegalArgumentException("--complete ENGINE FACTS NEW_REPORT or --controls FACTS NEW_REPORT");
        Path worker = Path.of(args[1]); var checks = new ArrayList<Map<String, Object>>(); var original = NfcoreChipseq200QcFiles.facts(worker, "vst"); audit(original, NfcoreChipseq200Qc.Limits.defaults()); checks.add(Map.of("case", "complete-original", "status", "PASS"));
        var signed = NfcoreChipseq200QcFiles.facts(worker, "vst"); for (double[] row : signed.scores()) for (int pc = 0; pc < row.length; pc++) row[pc] *= -1; audit(signed, NfcoreChipseq200Qc.Limits.defaults()); checks.add(Map.of("case", "complete-original-all-PC-signs-reversed", "status", "PASS"));
        for (String mode : NEGATIVE) {
            var f = NfcoreChipseq200QcFiles.facts(worker, "vst"); int g = f.counts().length - 1, s = f.factors().length - 1, p = f.sdev().length - 1;
            switch (mode) {
                case "factor-tail" -> f.factors()[s] *= 1.01;
                case "normalized-tail" -> f.normalized()[g][s] += 1;
                case "transform-tail" -> f.transformed()[g][s] += 1;
                case "distance-tail" -> f.distances()[s][s] += 1;
                case "score-tail" -> f.scores()[s][p] += 1;
                case "spectrum-tail" -> f.sdev()[p] += 1;
                case "fraction-tail" -> f.fractions()[p] += .01;
                case "nonfinite-normalized-tail" -> f.normalized()[g][s] = Double.NaN;
                case "nonfinite-transform-tail" -> f.transformed()[g][s] = Double.POSITIVE_INFINITY;
                case "nonfinite-distance-tail" -> f.distances()[s][s] = Double.NaN;
                case "negative-count-tail" -> f.counts()[g][s] = -1;
                case "ragged-count-tail" -> f.counts()[g] = new int[s];
                case "missing-PC-tail" -> f.scores()[s] = new double[p];
                case "zero-transform" -> { for (double[] row : f.transformed()) java.util.Arrays.fill(row, 1); }
                case "capacity" -> { }
                default -> throw new IllegalArgumentException(mode);
            }
            String refusal = null; try { audit(f, mode.equals("capacity") ? new NfcoreChipseq200Qc.Limits(1, 1, 2) : NfcoreChipseq200Qc.Limits.defaults()); } catch (IllegalArgumentException expected) { refusal = expected.getMessage(); }
            if (refusal == null) throw new IllegalStateException("Adverse complete numerical control was accepted: " + mode); checks.add(Map.of("case", mode, "status", "EXPECTED_FAIL2_NO_RESULT", "reason", refusal));
        }
        degenerate(checks);
        writeJson(Path.of(args[2]), Map.of("status", "PASS_SAVED_QC_ORIGINAL_AND_ADVERSE_NATIVE_CONTROLS_ONLY", "checks", checks, "original_genes", original.counts().length, "original_samples", original.factors().length, "source_transform_refitted", false, "new_tool_credit", 0));
    }
    private static void audit(NfcoreChipseq200QcFiles.Facts f, NfcoreChipseq200Qc.Limits limits) { NfcoreChipseq200Qc.audit(f.counts(), f.factors(), f.normalized(), f.transformed(), f.scores(), f.sdev(), f.fractions(), f.distances(), limits); }
    private static void degenerate(List<Map<String, Object>> checks) {
        int[][] counts = {{10,10,10,10},{10,10,10,10},{10,10,10,10}}; double[][] normalized = {{10,10,10,10},{10,10,10,10},{10,10,10,10}}, transformed = {{-1,1,-1,1},{-1,-1,1,1},{5,5,5,5}}; double[] factors = {1,1,1,1}, sdev = {Math.sqrt(4.0/3),Math.sqrt(4.0/3),0}, fractions = {.5,.5,0}; double[][] scores = new double[4][3], distances = new double[4][4]; double angle = .376, c = Math.cos(angle), s = Math.sin(angle);
        for (int i = 0; i < 4; i++) { scores[i][0] = c * transformed[0][i] - s * transformed[1][i]; scores[i][1] = s * transformed[0][i] + c * transformed[1][i]; for (int j = 0; j < 4; j++) distances[i][j] = Math.hypot(transformed[0][i] - transformed[0][j], transformed[1][i] - transformed[1][j]); }
        NfcoreChipseq200Qc.audit(counts,factors,normalized,transformed,scores,sdev,fractions,distances,NfcoreChipseq200Qc.Limits.defaults()); checks.add(Map.of("case", "equal-eigenvalue-rotated-full-basis", "status", "PASS"));
        double[][] published = new double[4][2]; for (int i = 0; i < 4; i++) { published[i][0] = -transformed[0][i]; published[i][1] = transformed[1][i]; } NfcoreChipseq200Qc.auditPublishedAxes(transformed,published,sdev,NfcoreChipseq200Qc.Limits.defaults()); checks.add(Map.of("case", "equal-eigenvalue-independent-published-sign-rotation", "status", "PASS"));
        for (int i = 0; i < 4; i++) scores[i][1] = scores[i][0]; boolean refused = false; try { NfcoreChipseq200Qc.audit(counts,factors,normalized,transformed,scores,sdev,fractions,distances,NfcoreChipseq200Qc.Limits.defaults()); } catch (IllegalArgumentException expected) { refused = expected.getMessage().contains("orthogonal"); } if (!refused) throw new IllegalStateException("Duplicate equal-eigenvalue axes were not refused for orthogonality"); checks.add(Map.of("case", "equal-eigenvalue-duplicate-axis", "status", "EXPECTED_FAIL2_NO_RESULT"));
    }
}
