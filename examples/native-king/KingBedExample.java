/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import org.jlinalg.genetics.KingRobustKinship;
import org.jlinalg.genetics.PlinkBedDataset;

/** Public Java17 consumer of selected autosomal BED hard calls; no external runtime. */
public final class KingBedExample {
    private KingBedExample() { }

    /** Estimate the explicitly chosen first two FAM participants on a supplied variant list. */
    public static void main(String[] args) throws Exception {
        if (args.length != 3)
            throw new IllegalArgumentException("Usage: KingBedExample BED_PREFIX VARIANT_IDS NEW_OUTPUT.tsv");
        var selected = new LinkedHashSet<String>();
        for (String id : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            if (id.isEmpty() || !selected.add(id))
                throw new IllegalArgumentException("Require nonempty, unique literal variant IDs; no trimming or repair");
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("The declared variant population is empty");
        try (var bed = PlinkBedDataset.open(Path.of(args[0]), selected)) {
            if (bed.samples().size() < 2) throw new IllegalArgumentException("Require at least two participants");
            var pair = new KingRobustKinship.Pair(0, 1);
            var counter = new KingRobustKinship.Accumulator(bed.samples().size(), List.of(pair), selected.size(), 128L*1024*1024);
            for (var variant : bed.selectedVariants()) {
                if (!variant.chromosome().matches("(?:[1-9]|1[0-9]|2[0-2])"))
                    throw new IllegalArgumentException("This example requires explicitly selected numeric human autosomes1..22: "+variant.id());
                counter.acceptPackedBed(bed.readPackedRow(variant));
            }
            counter.finish();
            var estimate = counter.estimate(0); var counts = estimate.counts();
            var first = bed.samples().get(pair.first()); var second = bed.samples().get(pair.second());
            try (var writer = Files.newBufferedWriter(Path.of(args[2]), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                writer.write("FID1\tIID1\tFID2\tIID2\tNSNP\tHETHET\tIBS0\tHET1_HOM2\tHET2_HOM1\tKINSHIP\tDISPOSITION\n");
                writer.write(String.join("\t", first.familyId(), first.individualId(), second.familyId(), second.individualId(),
                    Long.toString(counts.jointlyCalled()), Long.toString(counts.bothHeterozygous()),
                    Long.toString(counts.oppositeHomozygotes()), Long.toString(counts.firstHetSecondHom()),
                    Long.toString(counts.firstHomSecondHet()), Double.toString(estimate.coefficient()), estimate.disposition().name())+"\n");
            }
            System.err.println("Native between-family arithmetic only. Read every count and disposition. NaN/-Infinity are unreportable; a finite value alone does not establish a biological relationship. No sample removal, IBD/PI_HAT, within-family KING, SNPRelate or historical paper reproduction. Inputs must remain unchanged while reading.");
        }
    }
}
