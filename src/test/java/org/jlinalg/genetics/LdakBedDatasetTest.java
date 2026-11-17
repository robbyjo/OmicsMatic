/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LdakBedDatasetTest {
    @TempDir Path root;
    private Path fixture() throws IOException {
        Path prefix = root.resolve("input");
        Files.writeString(Path.of(prefix+".fam"), "F1 I1 NA NA NA -9\nF2 I1 0 0 0 NA\nF2 I2 0 0 2 case\n");
        Files.writeString(Path.of(prefix+".bim"), "chr1 first 0 100 A G\nchr1 second NA 200 0 A\n");
        Files.write(Path.of(prefix+".bed"), new byte[]{0x6c,0x1b,1,0x38,0x1b});
        return prefix;
    }

    @Test void preservesLiteralUnusedFamFieldsAndExactCompositeIdsWithoutChangingInputs() throws Exception {
        Path prefix = fixture();
        byte[] original = Files.readAllBytes(Path.of(prefix+".fam"));
        try (var data = LdakBedDataset.open(prefix, null)) {
            assertEquals(3, data.samples().size());
            assertEquals("NA", data.samples().get(0).sex());
            assertEquals("NA", data.samples().get(0).fatherId());
            assertEquals("case", data.samples().get(2).phenotype());
            assertEquals("I1", data.samples().get(1).individualId());
            assertEquals("F2", data.samples().get(1).familyId());
            assertArrayEquals(new double[]{2,1,0}, data.readAllele1Dosages(data.selectedVariants().get(0)));
            assertArrayEquals(new double[]{0,1,Double.NaN}, data.readAllele1Dosages(data.selectedVariants().get(1)));
            assertEquals("0", data.selectedVariants().get(1).allele1());
            assertEquals("NA", data.selectedVariants().get(1).geneticDistance());
            double[] copy = data.readAllele1Dosages(data.selectedVariants().get(0));
            copy[0] = -999;
            assertEquals(2, data.readAllele1Dosages(data.selectedVariants().get(0))[0]);
            assertThrows(UnsupportedOperationException.class, () -> data.samples().clear());
        }
        assertArrayEquals(original, Files.readAllBytes(Path.of(prefix+".fam")));
    }

    @Test void retainsOriginalOrdinalForSelectedRowsAndRejectsUnknownOrDuplicateIds() throws Exception {
        Path prefix = fixture();
        try (var data = LdakBedDataset.open(prefix, Set.of("second"))) {
            assertEquals(1, data.selectedVariants().size());
            assertEquals(1, data.selectedVariants().get(0).ordinal());
            assertArrayEquals(new double[]{0,1,Double.NaN}, data.readAllele1Dosages(data.selectedVariants().get(0)));
            var forged = new LdakBedDataset.Variant("chr1","first","0","100","A","G",0);
            assertThrows(IOException.class, () -> data.readAllele1Dosages(forged));
        }
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, Set.of("unknown")));
        Files.writeString(Path.of(prefix+".bim"), "1 first 0 100 A G\n1 first 0 200 C T\n");
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null));
    }

    @Test void rejectsDuplicateSamplesTruncationWrongModeAndNonzeroPadding() throws Exception {
        Path prefix = fixture();
        Files.writeString(Path.of(prefix+".fam"), "F1 I1 0 0 0 -9\nF1 I1 0 0 0 -9\nF2 I2 0 0 0 -9\n");
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null));
        Files.writeString(Path.of(prefix+".fam"), "F1 I1 0 0 0 -9\nF2 I1 0 0 0 -9\nF2 I2 0 0 0 -9\n");
        Files.write(Path.of(prefix+".bed"), new byte[]{0x6c,0x1b,1,0x38});
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null));
        Files.write(Path.of(prefix+".bed"), new byte[]{0x6c,0x1b,0,0x38,0x1b});
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null));
        Files.write(Path.of(prefix+".bed"), new byte[]{0x6c,0x1b,1,(byte)0xf8,0x1b});
        try (var data = LdakBedDataset.open(prefix, null)) {
            assertThrows(IOException.class, () -> data.readAllele1Dosages(data.selectedVariants().get(0)));
        }
    }

    @Test void appliesExplicitMetadataAndLineLimitsAndRejectsMalformedUtf8() throws Exception {
        Path prefix = fixture();
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null, new LdakBedDataset.Limits(2,10,1000,100)));
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null, new LdakBedDataset.Limits(10,1,1000,100)));
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null, new LdakBedDataset.Limits(10,10,1,100)));
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null, new LdakBedDataset.Limits(10,10,1000,16)));
        Files.write(Path.of(prefix+".fam"), new byte[]{(byte)0xc3,(byte)0x28,10});
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null));
        Files.write(Path.of(prefix+".fam"), "F1 I1 0 0 0\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> LdakBedDataset.open(prefix, null));
    }
}
