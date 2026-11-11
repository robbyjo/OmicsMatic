/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemSamAlignments;

/** Typed original alignment-mode fitting controls; complete execution/result admission is separate. */
final class RsemFitRequest {
    private RsemFitRequest() { }
    enum Strand { NONE, FORWARD, REVERSE }
    enum Posterior { NONE, MEAN, INTERVAL }
    record Fragment(double mean, double standardDeviation) {
        Fragment {
            if (!Double.isFinite(mean) || mean < 1 || mean > Integer.MAX_VALUE || !Double.isFinite(standardDeviation) || standardDeviation < 0 || standardDeviation > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Single-end fragment mean must be finite and at least1; SD must be finite and nonnegative. SD0 retains the original fixed-length model.");
        }
    }
    record Settings(long seed, int workerThreads, Strand strand, Posterior posterior, boolean estimateReadStart,
                    boolean appendNames, Fragment singleEndFragment) {
        Settings {
            Objects.requireNonNull(strand); Objects.requireNonNull(posterior);
            if (seed < 0 || seed > 4294967295L || workerThreads < 1 || workerThreads > 4096)
                throw new IllegalArgumentException("Require an explicit unsigned32-bit seed and positive bounded worker count");
        }
    }
    static List<String> flags(RsemSamAlignments.Options input, Settings settings, int aggregateThreadCapacity) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(settings);
        if ((long)settings.workerThreads() + 3 > aggregateThreadCapacity) throw new IOException("Original worker request leaves insufficient aggregate thread capacity for its launcher and parent processes; reserve3 additional threads");
        if (input.seedLength() != 25) throw new IOException("These pinned original parser/read models use the fixed25-base low-quality seed test; a different native diagnostic threshold is not an admitted fitting request");
        if (input.paired() && settings.singleEndFragment() != null) throw new IOException("Single-end fragment parameters cannot be silently ignored for paired-end fitting");
        var flags = new ArrayList<String>(List.of("--alignments", "--no-bam-output", "--keep-intermediate-files", "--seed", Long.toString(settings.seed()),
                "--num-threads", Integer.toString(settings.workerThreads()), "--tag", input.readTypeTag()));
        // The original scripts initialize this tag to XM even though their POD says empty.
        // Always carry the assessed tag explicitly, including an intentional empty argv value.
        if (input.paired()) flags.add("--paired-end");
        if (!input.qualities()) flags.add("--no-qualities");
        if (input.release() == RsemAbundance.Release.V1_2_28) flags.addAll(List.of("--forward-prob", switch (settings.strand()) { case NONE -> "0.5"; case FORWARD -> "1"; case REVERSE -> "0"; }));
        else flags.addAll(List.of("--strandedness", settings.strand().name().toLowerCase(java.util.Locale.ROOT)));
        if (settings.posterior() != Posterior.NONE) flags.add("--calc-pme");
        if (settings.posterior() == Posterior.INTERVAL) flags.add("--calc-ci");
        if (settings.estimateReadStart()) flags.add("--estimate-rspd");
        if (settings.appendNames()) flags.add("--append-names");
        if (settings.singleEndFragment() != null) flags.addAll(List.of("--fragment-length-mean", Double.toString(settings.singleEndFragment().mean()),
                "--fragment-length-sd", Double.toString(settings.singleEndFragment().standardDeviation())));
        return List.copyOf(flags);
    }
    static List<String> command(RsemInputAudit.Context audit, Settings settings, Path newWork, int aggregateThreadCapacity, Runnable check) throws IOException {
        Objects.requireNonNull(audit); audit.verify(check);
        RsemPayload.originalPath(newWork);
        var input = audit.inputs();
        var command = new ArrayList<String>(List.of("/usr/bin/perl", audit.runtime().payload().home().resolve("bin/rsem-calculate-expression").toString()));
        command.addAll(flags(input.spec().options(), settings, aggregateThreadCapacity));
        if (audit.cram() != null) command.addAll(List.of("--fai", input.cramFasta() + ".fai"));
        for (Path path : List.of(input.alignment(), input.referencePrefix(), newWork.resolve("sample"))) { RsemPayload.originalPath(path); command.add(path.toString()); }
        return List.copyOf(command);
    }
}
