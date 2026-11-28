/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaddCalibrationTest {
    private CaddCalibration table()throws IOException{return CaddCalibration.read(new StringReader("99\t20\n10\t1\n0.001\t-1\n0\t-10\n"),100,100);}
    @Test void exactCeilingAndClampedTailsAreDistinctFromInterpolation()throws Exception{var t=table();assertEquals("10",t.phred(1));assertEquals("99",t.phred(Math.nextUp(1)));assertEquals("10",t.phred(Math.nextDown(1)));assertEquals("0",t.phred(-100));assertEquals("99",t.phred(100));}
    @Test void sourceNonfiniteValuesRemainVisibleAndNanHasNoScore()throws Exception{var t=table();assertEquals("NA",t.phred(Double.NaN));assertEquals("99",t.phred(Double.POSITIVE_INFINITY));assertEquals("0",t.phred(Double.NEGATIVE_INFINITY));assertEquals("nan",CaddCalibration.sourceRaw(Double.NaN));assertEquals("inf",CaddCalibration.sourceRaw(Double.POSITIVE_INFINITY));}
    @Test void binaryRoundingAndNegativeZeroAreRetained(){assertEquals("0.000000",CaddCalibration.sourceRaw(0));assertEquals("-0.000000",CaddCalibration.sourceRaw(-0.0));assertEquals("0.007812",CaddCalibration.sourceRaw(1.0/128));assertEquals("0.023438",CaddCalibration.sourceRaw(3.0/128));assertEquals("-0.000000",CaddCalibration.sourceRaw(-1e-12));}
    @Test void duplicateBoundaryUsesLastLiteralValueAsOriginalDictionary()throws Exception{var t=CaddCalibration.read(new StringReader("1.000\t0\n2.00\t-0.0\n3\t1\n"),10,100);assertEquals(2,t.boundaries());assertEquals("2.00",t.phred(-0.0));}
    @Test void invalidOrOversizedTableCannotYieldPartialCalibration(){assertThrows(IOException.class,()->CaddCalibration.read(new StringReader("1 NaN\n"),10,100));assertThrows(IOException.class,()->CaddCalibration.read(new StringReader("# ignored\n"),10,100));assertThrows(IOException.class,()->CaddCalibration.read(new StringReader("1 0\n2 1\n"),1,100));assertThrows(IOException.class,()->CaddCalibration.parse("NA"));}
    @Test void explicitPreparationRetainsAnchoringAndDoesNotLeftAlign()throws Exception{var v=CaddVariantPreparation.prepare("chr1","12","aacg","aatg",true);assertEquals("chr1",v.chromosome());assertEquals("14",v.position());assertEquals("C",v.reference());assertEquals("T",v.alternate());assertEquals(2,v.prefixTrim());assertEquals(1,v.suffixTrim());var deletion=CaddVariantPreparation.prepare("1","20","AAA","AA",true);assertEquals("21",deletion.position());assertEquals("AA",deletion.reference());assertEquals("A",deletion.alternate());}
    @Test void literalModeNeverChangesAllelesOrAliasesChromosomes()throws Exception{var v=CaddVariantPreparation.prepare("chr1","12","aacg","aatg",false);assertEquals("12",v.position());assertEquals("aacg",v.reference());assertEquals("aatg",v.alternate());assertEquals("chr1",v.chromosome());assertEquals(0,v.prefixTrim());}
    @Test void sourceOmissionIsExplicitRatherThanBenignScore()throws Exception{assertEquals("SOURCE_OMITS_NON_ACGT",CaddVariantPreparation.prepare("1","1","A","<DEL>",true).state());assertEquals("SOURCE_OMITS_REF_EQUALS_ALT",CaddVariantPreparation.prepare("1","1","a","A",true).state());assertThrows(IOException.class,()->CaddVariantPreparation.prepare("1","2147483647","AA","AC",true));}
    @Test void pythonFloatSyntaxIsNotExpandedToJavaHexOrTypeSuffixes()throws Exception{assertThrows(IOException.class,()->CaddCalibration.parse("0x1.0p0"));assertThrows(IOException.class,()->CaddCalibration.parse("1d"));assertEquals(.1,CaddCalibration.parse(" +.1 "));assertTrue(Double.isNaN(CaddCalibration.parse(" -NaN ")));}
}
