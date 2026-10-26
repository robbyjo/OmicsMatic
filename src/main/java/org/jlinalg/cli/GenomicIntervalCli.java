/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.genetics.GenomicIntervals.Interval;
import static org.jlinalg.cli.FollowupSupport.*;

/** Assembly-declared interval intersections, merging, subtraction and coverage. */
final class GenomicIntervalCli {
    private GenomicIntervalCli() { }
    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (Arrays.asList(arguments).contains("--help")) { output.println(help()); return 0; }
        try {
            Map<String, String> o = options(arguments, Set.of("method", "input", "mask", "genome-build", "out", "same-strand", "max-gap", "min-fraction-a", "min-fraction-b"));
            String method = required(o, "method");
            if (!Set.of("intersect", "merge", "subtract", "coverage").contains(method)) throw new IllegalArgumentException("Unknown interval method");
            if (required(o, "genome-build").isBlank()) throw new IllegalArgumentException("Require declared genome assembly");
            boolean stranded = bool(o, "same-strand", false);
            if (!method.equals("intersect") && (o.containsKey("min-fraction-a") || o.containsKey("min-fraction-b"))
                    || !method.equals("merge") && o.containsKey("max-gap") || method.equals("merge") && o.containsKey("mask"))
                throw new IllegalArgumentException("Option is not applicable to the requested interval method");
            double fractionA = number(o, "min-fraction-a", 0), fractionB = number(o, "min-fraction-b", 0);
            if (fractionA < 0 || fractionA > 1 || fractionB < 0 || fractionB > 1) throw new IllegalArgumentException("Overlap fractions must be in [0,1]");
            long gap = Long.parseLong(o.getOrDefault("max-gap", "0"));
            if (gap < 0) throw new IllegalArgumentException("max-gap must be nonnegative");
            List<Interval> input = read(Path.of(required(o, "input")), stranded);
            List<Interval> masks = method.equals("merge") ? List.of() : read(Path.of(required(o, "mask")), stranded);
            transaction(o, "intervals-" + method, (directory, manifest) -> {
                // mask is a new option absent from the shared runner's older input inventory.
                if (o.containsKey("mask")) manifest.put("mask_sha256", hash(Path.of(o.get("mask"))));
                manifest.put("coordinate_system", "zero-based-half-open"); manifest.put("genome_build", required(o, "genome-build"));
                manifest.put("chromosome_matching", "exact labels, no aliases or liftover");
                manifest.put("scope", "Interval operations on one declared assembly; BEDTools-like subset with canonical headered TSV input");
                var index = new GenomicIntervals.Index(masks);
                try (var writer = Files.newBufferedWriter(directory.resolve("intervals.tsv"))) {
                    if (method.equals("merge")) {
                        row(writer, new String[]{"chrom", "start", "end", "id", "strand", "members"});
                        for (var merged : GenomicIntervals.merge(input, gap, stranded)) {
                            var interval = merged.interval();
                            row(writer, new String[]{interval.chromosome(), "" + interval.start(), "" + interval.end(), interval.id(), interval.strand(), String.join(",", merged.members())});
                        }
                    } else if (method.equals("intersect")) {
                        row(writer, new String[]{"id_a", "id_b", "chrom", "start", "end", "overlap_bases", "fraction_a", "fraction_b"});
                        for (Interval first : input) for (Interval second : index.overlaps(first, stranded)) {
                            long overlap = GenomicIntervals.overlap(first, second);
                            double fa = overlap / (double) first.length(), fb = overlap / (double) second.length();
                            if (fa >= fractionA && fb >= fractionB) row(writer, new String[]{first.id(), second.id(), first.chromosome(), "" + Math.max(first.start(), second.start()), "" + Math.min(first.end(), second.end()), "" + overlap, "" + fa, "" + fb});
                        }
                    } else if (method.equals("coverage")) {
                        row(writer, new String[]{"id", "chrom", "start", "end", "covered_bases", "length", "covered_fraction"});
                        for (Interval query : input) {
                            long covered = GenomicIntervals.coveredBases(query, index, stranded);
                            row(writer, new String[]{query.id(), query.chromosome(), "" + query.start(), "" + query.end(), "" + covered, "" + query.length(), "" + (covered / (double) query.length())});
                        }
                    } else {
                        row(writer, new String[]{"chrom", "start", "end", "id", "strand"});
                        for (Interval query : input) for (Interval piece : GenomicIntervals.subtract(query, index, stranded))
                            row(writer, new String[]{piece.chromosome(), "" + piece.start(), "" + piece.end(), piece.id(), piece.strand()});
                    }
                }
            });
            output.println("Intervals written to " + o.get("out")); return 0;
        } catch (Exception failure) { error.println("jlinalg: " + failure.getMessage()); return 2; }
    }

    private static List<Interval> read(Path path, boolean stranded) throws Exception {
        DelimitedData data = DelimitedData.read(path);
        int chrom = data.column("chrom"), start = data.column("start"), end = data.column("end"), id = data.column("id");
        int strand = data.header().indexOf("strand");
        List<Interval> intervals = new ArrayList<>(); Set<String> seen = new HashSet<>();
        if (data.rows().size() > 1_000_000) throw new IllegalArgumentException("At most one million intervals per input");
        for (String[] fields : data.rows()) {
            Interval interval = new Interval(fields[chrom], Long.parseLong(fields[start]), Long.parseLong(fields[end]), fields[id], strand < 0 ? "." : fields[strand]);
            if (!seen.add(interval.id())) throw new IllegalArgumentException("Duplicate interval ID: " + interval.id());
            if (stranded && interval.strand().equals(".")) throw new IllegalArgumentException("Stranded operations require known strand");
            intervals.add(interval);
        }
        return List.copyOf(intervals);
    }

    static String help() { return """
        intervals --method intersect|merge|subtract|coverage --input intervals.tsv --genome-build ASSEMBLY --out NEW_DIR
          [--mask intervals.tsv] [--same-strand true|false]
        Headered TSV: chrom,start,end,id [strand]; zero-based half-open, nonempty intervals, unique IDs.
        All inputs must use the declared assembly and exact chromosome labels.
        intersect/subtract/coverage require --mask; merge does not accept a mask.
        intersect: [--min-fraction-a 0 --min-fraction-b 0]; both thresholds apply.
        merge: [--max-gap 0]; merges overlapping and bookended intervals by default.
        coverage counts union bases once. Known + or - strands required with --same-strand true.
        Output: intervals.tsv and provenance manifest; no assembly conversion or chromosome renaming.
        """; }
}
