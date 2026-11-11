/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** External-reference probe; not a production BED admission or analysis command. */
public final class KingRobustReferenceProbe {
    private KingRobustReferenceProbe() { }

    /** Count complete synthetic reference inputs through both native genotype transports. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Require synthetic BED prefix and NEW probe output");
        Path prefix = Path.of(args[0]), out = Path.of(args[1]);
        if (Files.exists(out)) throw new IllegalArgumentException("Preserve existing probe output");
        var samples = Files.readAllLines(Path.of(prefix+".fam"), StandardCharsets.UTF_8);
        var variants = Files.readAllLines(Path.of(prefix+".bim"), StandardCharsets.UTF_8);
        int n = samples.size(), m = variants.size();
        var ids = new ArrayList<String[]>();
        for (String row : samples) {
            String[] values = row.split("\\s+");
            if (values.length != 6) throw new IllegalArgumentException("Unexpected synthetic FAM row");
            ids.add(new String[] {values[0], values[1]});
        }
        var pairs = new ArrayList<KingRobustKinship.Pair>();
        for (int first = 1; first < n; first++)
            for (int second = 0; second < first; second++) pairs.add(new KingRobustKinship.Pair(first, second));
        var packed = new KingRobustKinship.Accumulator(n, pairs, m, 100000000);
        var decoded = new KingRobustKinship.Accumulator(n, pairs, m, 100000000);
        try (var input = Files.newInputStream(Path.of(prefix+".bed"))) {
            if (input.read() != 0x6c || input.read() != 0x1b || input.read() != 1)
                throw new IllegalArgumentException("Unexpected synthetic BED header");
            for (int variant = 0; variant < m; variant++) {
                byte[] row = input.readNBytes((n+3)/4), calls = new byte[n];
                if (row.length != (n+3)/4) throw new IllegalArgumentException("Truncated synthetic BED");
                for (int sample = 0; sample < n; sample++) {
                    int code = ((row[sample/4] & 0xff) >>> (2*(sample%4))) & 3;
                    calls[sample] = switch (code) { case 0 -> 2; case 2 -> 1; case 3 -> 0; default -> -1; };
                }
                packed.acceptPackedBed(row); decoded.accept(calls);
            }
            if (input.read() != -1) throw new IllegalArgumentException("Extra synthetic BED content");
        }
        packed.finish(); decoded.finish();
        try (var writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writer.write("FID1\tIID1\tFID2\tIID2\tNSNP\tHETHET\tIBS0\tHET1_HOM2\tHET2_HOM1\tCOEFFICIENT_HEX\tDISPOSITION\n");
            for (int i = 0; i < pairs.size(); i++) {
                var a = packed.estimate(i); var b = decoded.estimate(i); var counts = a.counts();
                if (!counts.equals(b.counts()) || a.disposition() != b.disposition()
                        || Double.doubleToLongBits(a.coefficient()) != Double.doubleToLongBits(b.coefficient()))
                    throw new AssertionError("Native packed and decoded complete populations differ");
                var pair = packed.pair(i);
                String[] first = ids.get(pair.first()), second = ids.get(pair.second());
                writer.write(String.join("\t", first[0], first[1], second[0], second[1],
                    Long.toString(counts.jointlyCalled()), Long.toString(counts.bothHeterozygous()),
                    Long.toString(counts.oppositeHomozygotes()), Long.toString(counts.firstHetSecondHom()),
                    Long.toString(counts.firstHomSecondHet()), Double.toHexString(a.coefficient()), a.disposition().name())+"\n");
            }
        }
        System.out.println("Native reference probe completed "+pairs.size()+" pairs and "+packed.variantCount()+" variants");
    }
}
