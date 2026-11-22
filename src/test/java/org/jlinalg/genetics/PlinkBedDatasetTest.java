/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkBedDatasetTest {
    @TempDir Path root;
    private Path fixture(String fam, String bim, byte[] body) throws IOException {
        Path prefix = root.resolve("known");
        Files.writeString(Path.of(prefix + ".fam"),fam,StandardCharsets.UTF_8);
        Files.writeString(Path.of(prefix + ".bim"),bim,StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3]; bytes[0] = 0x6c; bytes[1] = 0x1b; bytes[2] = 1;
        System.arraycopy(body,0,bytes,3,body.length); Files.write(Path.of(prefix + ".bed"),bytes); return prefix;
    }
    private static final String FAM = "F1 I1 0 0 1 -9\nF1 I2 0 0 2 1\nF2 I1 I0 M0 0 2\nF2 I2 0 0 1 -9\nF3 I1 0 0 2 -9\n";
    private static final String BIM = "1 rs1 0 10 C A\n2 rs2 0 20 G T\n";

    @Test void qcMissingAllelesRequireExplicitAdmissionAndCannotYieldBiologicalDosages() throws Exception {
        Path prefix=fixture(FAM,"1 rs1 0 10 0 A\n2 rs2 0 20 0 .\n",new byte[]{(byte)0x78,0,(byte)0x55,1});
        assertThrows(IOException.class,()->PlinkBedDataset.open(prefix,Set.of("rs1","rs2")));
        try(var data=PlinkBedDataset.open(prefix,Set.of("rs1","rs2"),PlinkBedDataset.Limits.defaults(),PlinkBedDataset.AllelePolicy.QC_ALLOW_MISSING)) {
            assertFalse(data.selectedVariants().get(0).resolvedAlleles());assertArrayEquals(new byte[]{0x78,0},data.readPackedRow(data.selectedVariants().get(0)));
            assertThrows(IOException.class,()->data.readAllele1Dosages(data.selectedVariants().get(0)));assertThrows(IOException.class,()->data.readAllele1Dosages(data.selectedVariants().get(1)));
        }
    }
    @Test void qcPackedRowsAreOwnedCopiesWithTheSamePaddingAndSelectionChecks() throws Exception {
        Path prefix=fixture(FAM,BIM,new byte[]{0x78,0,(byte)0xe4,3});
        try(var data=PlinkBedDataset.open(prefix,Set.of("rs1"))) {
            var variant=data.selectedVariants().get(0);byte[] bytes=data.readPackedRow(variant);bytes[0]=0;assertEquals(0x78,data.readPackedRow(variant)[0]);assertTrue(variant.resolvedAlleles());
            assertThrows(IOException.class,()->data.readPackedRow(new PlinkBedDataset.Variant("2","rs2",20,"G","T",1)));
        }
        Files.write(Path.of(prefix+".bed"),new byte[]{0x6c,0x1b,1,0x78,4,(byte)0xe4,3});try(var data=PlinkBedDataset.open(prefix,Set.of("rs1"))){assertThrows(IOException.class,()->data.readPackedRow(data.selectedVariants().get(0)));}
    }
    @Test void qcAdmissionStillRejectsIdenticalNonmissingAlleles() throws Exception {
        Path prefix=fixture(FAM,"1 rs1 0 10 A a\n2 rs2 0 20 G T\n",new byte[]{0,0,0,0});
        assertThrows(IOException.class,()->PlinkBedDataset.open(prefix,Set.of("rs1"),PlinkBedDataset.Limits.defaults(),PlinkBedDataset.AllelePolicy.QC_ALLOW_MISSING));
    }

    @Test void actualBedBitCodesAndPaddingRespectAllele1Orientation() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {(byte)0x78,0, (byte)0xe4,3});
        try (var data = PlinkBedDataset.open(prefix,Set.of("rs1","rs2"))) {
            assertEquals(5,data.samples().size()); assertEquals(2,data.variantCount());
            assertArrayEquals(new double[] {2,1,0,Double.NaN,2},data.readAllele1Dosages(data.selectedVariants().get(0)));
            assertArrayEquals(new double[] {2,Double.NaN,1,0,0},data.readAllele1Dosages(data.selectedVariants().get(1)));
            assertTrue(data.samples().get(0).founder()); assertFalse(data.samples().get(2).founder());
            assertThrows(UnsupportedOperationException.class,() -> data.samples().clear());
        }
    }
    @Test void selectedRowsUseTheirOriginalOrdinalAndBimOrder() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {(byte)0x78,0, (byte)0xe4,3});
        try (var data = PlinkBedDataset.open(prefix,Set.of("rs2"))) {
            var variant = data.selectedVariants().get(0); assertEquals(1,variant.ordinal()); assertEquals("G",variant.allele1());
            assertArrayEquals(new double[] {2,Double.NaN,1,0,0},data.readAllele1Dosages(variant));
            assertThrows(IOException.class,() -> data.readAllele1Dosages(new PlinkBedDataset.Variant("1","rs1",10,"C","A",0)));
        }
    }
    @Test void exactCompositeSampleIdsPermitSameIidInDifferentFamilies() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {0,0,0,0});
        try (var data = PlinkBedDataset.open(prefix,Set.of("rs1"))) { assertEquals("I1",data.samples().get(0).individualId()); assertEquals("I1",data.samples().get(2).individualId()); }
        Files.writeString(Path.of(prefix + ".fam"),FAM+"F1 I1 0 0 1 -9\n");
        assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
    }
    @Test void absentAndDuplicatedSelectedIdsFail() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {0,0,0,0});
        assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("missing")));
        Files.writeString(Path.of(prefix + ".bim"),"1 rs1 0 10 C A\n1 rs1 0 20 C A\n");
        assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
    }
    @Test void exactFileDimensionsAndVariantMajorHeaderAreRequired() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {0,0,0});
        assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
        fixture(FAM,BIM,new byte[] {0,0,0,0,0}); assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
        fixture(FAM,BIM,new byte[] {0,0,0,0});byte[] bytes = Files.readAllBytes(Path.of(prefix + ".bed"));bytes[2]=0;Files.write(Path.of(prefix + ".bed"),bytes);
        assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
    }
    @Test void unusedPaddingBitsAreRejectedOnSelectedRead() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {0,4,0,0});
        try (var data = PlinkBedDataset.open(prefix,Set.of("rs1"))) { assertThrows(IOException.class,() -> data.readAllele1Dosages(data.selectedVariants().get(0))); }
    }
    @Test void unresolvedOrEqualSelectedAllelesAreRejected() throws Exception {
        for (String alleles : new String[] {"0 A","C .","A A","a A"}) {
            Path prefix = fixture(FAM,"1 rs1 0 10 "+alleles+"\n",new byte[] {0,0});
            assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
        }
    }
    @Test void metadataFieldCountsNumbersAndControlsAreStrict() throws Exception {
        for (String bim : new String[] {"1 rs1 0 10 C\n","1 rs1 0 10 C A extra\n","1 rs1 NaN 10 C A\n","1 rs1 0 no C A\n","1 rs\u00001 0 10 C A\n"}) {
            Path prefix = fixture(FAM,bim,new byte[] {0,0}); assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
        }
    }
    @Test void sampleVariantAndMetadataLimitsAreEnforced() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {0,0,0,0});
        for (var limit : new PlinkBedDataset.Limits[] {
                new PlinkBedDataset.Limits(4,10,2,10000,10000,1000),new PlinkBedDataset.Limits(10,1,2,10000,10000,1000),
                new PlinkBedDataset.Limits(10,10,1,10000,10000,1000),new PlinkBedDataset.Limits(10,10,2,1,10000,1000),
                new PlinkBedDataset.Limits(10,10,2,10000,1,1000)})
            assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1","rs2"),limit));
    }
    @Test void utf8AndCrLfMetadataArePreservedWithoutNormalizingIds() throws Exception {
        Path prefix = fixture("家\t样本\t0\t0\t1\t-9\r\n","chr1\t变异\t0\t10\tC\tA\r\n",new byte[] {2});
        try (var data = PlinkBedDataset.open(prefix,Set.of("变异"))) {
            assertEquals("家",data.samples().get(0).familyId());assertEquals("样本",data.samples().get(0).individualId());
            assertArrayEquals(new double[] {1},data.readAllele1Dosages(data.selectedVariants().get(0)));
        }
    }
    @Test void malformedUtf8AndOversizedMetadataLinesFail() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {0,0,0,0});Files.write(Path.of(prefix+".fam"),new byte[] {(byte)0xc3,0x28});
        assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
        fixture(FAM,"1 "+"x".repeat(100)+" 0 10 C A\n",new byte[] {0,0});
        var limits = new PlinkBedDataset.Limits(10,10,10,10000,10000,32);
        assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("x".repeat(100)),limits));
    }
    @Test void closedDatasetCannotReadAndEmptyInputsAreRejected() throws Exception {
        Path prefix = fixture(FAM,BIM,new byte[] {0,0,0,0});
        var data = PlinkBedDataset.open(prefix,Set.of("rs1"));var variant = data.selectedVariants().get(0);data.close();assertThrows(IOException.class,() -> data.readAllele1Dosages(variant));
        fixture("",BIM,new byte[] {0,0,0,0});assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
        fixture(FAM,"",new byte[] {});assertThrows(IOException.class,() -> PlinkBedDataset.open(prefix,Set.of("rs1")));
    }
}
