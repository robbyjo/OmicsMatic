/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.workflow.NfcoreChipseq121Consensus;
import org.jlinalg.workflow.NfcoreChipseq200Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Original v2 peak/antibody branch plans over complete immutable source outputs. */
final class NfcoreChipseq200Peaks {
    record Group(String antibody, List<String> libraries, boolean replicates, boolean multipleGroups,
                 NfcoreChipseq121Consensus.Plan consensus, String prefix) { Group { libraries = List.copyOf(libraries); } }
    final String base;
    final Map<String, List<NfcoreChipseq121Consensus.Peak>> raw;
    final List<Group> groups;
    NfcoreChipseq200Peaks(NfcoreChipseq200Files data, NfcoreChipseq200Mappings.Plan mappings) throws IOException {
        var parameters = data.files.parameters(); String aligner = parameters.containsKey("aligner") ? text(parameters.get("aligner")) : "bwa"; boolean narrow = Boolean.TRUE.equals(parameters.get("narrow_peak")); String type = narrow ? "narrowPeak" : "broadPeak"; base = aligner + "/mergedLibrary/macs2/" + type + "/";
        for (String flag : List.of("skip_consensus_peaks", "skip_peak_annotation", "skip_peak_qc")) if (parameters.containsKey(flag) && !Boolean.FALSE.equals(parameters.get(flag))) throw new IOException("Original source does not select this complete peak/QC contract: " + flag);
        int minimum = parameters.containsKey("min_reps_consensus") ? Math.toIntExact(integer(parameters.get("min_reps_consensus"), 1, 1000000)) : 1; var kind = narrow ? NfcoreChipseq121Consensus.Kind.NARROW : NfcoreChipseq121Consensus.Kind.BROAD; var limits = NfcoreChipseq121Consensus.Limits.defaults(); var originals = new LinkedHashMap<String, NfcoreChipseq200Mappings.Read>();
        for (var row : mappings.reads()) if (!row.antibody().isEmpty()) originals.putIfAbsent(row.libraryId(), row);
        var peaks = new LinkedHashMap<String, List<NfcoreChipseq121Consensus.Peak>>(); var antibodyLibraries = new LinkedHashMap<String, List<String>>();
        for (var row : originals.values()) {
            var values = NfcoreChipseq121Consensus.read(data.files.output(base + row.libraryId() + "_peaks." + type), kind, limits); if (values.isEmpty()) throw new IOException("Original source produced no nonempty peaks for " + row.libraryId() + "; downstream source eligibility is unassessable by this complete-selected-branch component. Raw zero/empty outputs remain retained; no positive or negative finding is substituted.");
            peaks.put(row.libraryId(), values); antibodyLibraries.computeIfAbsent(row.antibody(), ignored -> new ArrayList<>()).add(row.libraryId());
        }
        var plans = new ArrayList<Group>();
        for (var antibody : antibodyLibraries.entrySet()) {
            var conditionCounts = new LinkedHashMap<String, Integer>(); var inputs = new ArrayList<NfcoreChipseq121Consensus.PeakSet>();
            for (String id : antibody.getValue()) {
                int separator = id.lastIndexOf('_'); if (separator < 1 || separator == id.length() - 1) throw new IOException("Original v2 group/replicate convention requires an assessable final underscore-delimited replicate token; ID was not repaired: " + id);
                conditionCounts.merge(id.substring(0, separator), 1, Math::addExact); inputs.add(new NfcoreChipseq121Consensus.PeakSet(id, peaks.get(id)));
            }
            boolean multiple = conditionCounts.size() > 1, replicates = conditionCounts.values().stream().anyMatch(n -> n > 1); boolean requested = multiple || replicates; String prefix = base + "consensus/" + antibody.getKey() + "/" + antibody.getKey() + ".consensus_peaks";
            if (requested) {
                data.task("MACS2_CONSENSUS", antibody.getKey()); var plan = NfcoreChipseq121Consensus.compute(inputs, kind, minimum, limits);
                try (var bool = Files.newBufferedReader(data.files.output(prefix + ".boolean.txt"), StandardCharsets.UTF_8); var bed = Files.newBufferedReader(data.files.output(prefix + ".bed"), StandardCharsets.UTF_8); var saf = Files.newBufferedReader(data.files.output(prefix + ".saf"), StandardCharsets.UTF_8); var intersections = Files.newBufferedReader(data.files.output(prefix + ".boolean.intersect.txt"), StandardCharsets.UTF_8)) { NfcoreChipseq121Consensus.auditBoolean(bool, plan, limits); NfcoreChipseq121Consensus.auditIntervals(bed, saf, plan, limits); NfcoreChipseq121Consensus.auditIntersections(intersections, plan, limits); }
                plans.add(new Group(antibody.getKey(), antibody.getValue(), replicates, multiple, plan, prefix));
            } else {
                if (data.tasks().stream().anyMatch(t -> t.process().endsWith(":MACS2_CONSENSUS") && t.tag().equals(antibody.getKey()))) throw new IOException("Observed consensus task contradicts original complete sample/replicate eligibility"); plans.add(new Group(antibody.getKey(), antibody.getValue(), false, false, null, prefix));
            }
        }
        raw = Map.copyOf(peaks); groups = List.copyOf(plans);
    }
    static NfcoreChipseq200Mappings.Plan mappings(NfcoreChipseq200Files data) throws IOException {
        NfcoreChipseq200Mappings.Plan plan; try (var input = Files.newBufferedReader(data.files.inputPath(text(data.files.parameters().get("input"))), StandardCharsets.UTF_8)) { plan = NfcoreChipseq200Mappings.inspect(input, NfcoreChipseq200Mappings.Limits.defaults()); }
        try (var checked = Files.newBufferedReader(data.files.output("pipeline_info/samplesheet.valid.csv"), StandardCharsets.UTF_8)) { NfcoreChipseq200Mappings.auditChecked(checked, plan, NfcoreChipseq200Mappings.Limits.defaults()); } plan.requireLiteralIdentifiers(); plan.requireConsistentLibraryMetadata(); return plan;
    }
}
