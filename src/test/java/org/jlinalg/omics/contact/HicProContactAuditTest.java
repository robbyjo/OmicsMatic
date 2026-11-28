/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics.contact;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HicProContactAuditTest {
    private static final HicProContactAudit.Limits LIMITS=new HicProContactAudit.Limits(100,100,100,1024,100000);
    private static final String RAW="read:α\tchr1\t1\t+\tchr1\t10\t-\textra\nread2 chr1 11 - chr2 5 +\nread3\tchr2\t5\t+\tchr1\t11\t-\t\nread4\tchr1\t20\t+\tchr1\t21\t+\n";
    private static final String PROJECTED="read:α\tchr1\t1\tchr1\t10\t+\t-\nread2\tchr1\t11\tchr2\t5\t-\t+\nread3\tchr2\t5\tchr1\t11\t+\t-\nread4\tchr1\t20\tchr1\t21\t+\t+\n";
    private static Map<String,Long> refs(){var r=new LinkedHashMap<String,Long>();r.put("chr1",21L);r.put("chr2",5L);return r;}
    private static HicProContactAudit.Result inspect(String raw,String projected,List<Long> bins)throws IOException{return HicProContactAudit.inspect(new StringReader(raw),new StringReader(projected),refs(),bins,Set.of("read:α","read2","read3","read4"),LIMITS);}
    private static String bins(){return "bin_id\tchrom\tstart\tend\tweight\n0\tchr1\t0\t10\tNaN\n1\tchr1\t10\t20\t1.0\n2\tchr1\t20\t21\t0.5\n3\tchr2\t0\t5\t0.0\n";}
    private static String pixels(){return "bin1_id\tbin2_id\tcount\n0\t0\t1\n1\t2\t1\n1\t3\t2\n";}
    @Test void exactSourceFieldProjectionPreservesLiteralReadChromosomeAndStrands()throws Exception{assertEquals("read:α\tChr01\t10\tchr1\t11\t+\t-",HicProContactAudit.project("read:α  Chr01 10 + chr1 11 - 42 unused\t").projected());}
    @Test void sourceOneBasedEndpointsAndUpperTriangleReconstructEveryCount()throws Exception{var r=inspect(RAW,PROJECTED,List.of(10L));assertEquals(4,r.pairs());assertEquals(2,r.cisPairs());assertEquals(2,r.transPairs());var m=r.matrices().get(0);assertEquals(4,m.bins());assertEquals(Map.of(HicProContactAudit.key(0,0),1L,HicProContactAudit.key(1,2),1L,HicProContactAudit.key(1,3),2L),m.pixels());}
    @Test void eachDeclaredResolutionUsesTheEntirePairPopulation()throws Exception{var r=inspect(RAW,PROJECTED,List.of(10L,20L));var coarse=r.matrices().get(1);assertEquals(3,coarse.bins());assertEquals(4,coarse.pairs());assertEquals(Map.of(HicProContactAudit.key(0,0),1L,HicProContactAudit.key(0,1),1L,HicProContactAudit.key(0,2),2L),coarse.pixels());}
    @Test void verifiesAllBinsPixelsCountMassAndPreservesMissingWeights()throws Exception{var m=inspect(RAW,PROJECTED,List.of(10L)).matrices().get(0);var v=HicProContactAudit.verify(m,new StringReader(bins()),new StringReader(pixels()),LIMITS);assertEquals(new HicProContactAudit.Verified(4,1,3,4),v);}
    @Test void duplicateInputReadIdsAreNotSilentlyDeduplicated()throws Exception{var r=inspect(RAW+RAW,PROJECTED+PROJECTED,List.of(10L));assertEquals(8,r.pairs());assertEquals(4,r.matrices().get(0).pixels().get(HicProContactAudit.key(1,3)));}
    @Test void readIdentityCoordinateOrRowOrderChangesRefuseTheCompleteProjection(){assertThrows(IOException.class,()->inspect(RAW,PROJECTED.replace("read2","other"),List.of(10L)));assertThrows(IOException.class,()->inspect(RAW,PROJECTED.replace("chr1\t11\tchr2","chr1\t12\tchr2"),List.of(10L)));assertThrows(IOException.class,()->inspect(RAW,PROJECTED+PROJECTED,List.of(10L)));}
    @Test void unmatchedLiteralChromosomeOrOutOfReferencePositionIsNeverAliasedOrClamped(){assertThrows(IOException.class,()->inspect(RAW.replace("chr1","1"),PROJECTED.replace("chr1","1"),List.of(10L)));assertThrows(IOException.class,()->inspect(RAW.replace("21","22"),PROJECTED.replace("21","22"),List.of(10L)));}
    @Test void missingCapturedReadMembershipRefusesEveryDependentMatrix(){assertThrows(IOException.class,()->HicProContactAudit.inspect(new StringReader(RAW),new StringReader(PROJECTED),refs(),List.of(10L),Set.of("read:α"),LIMITS));}
    @Test void changedDroppedDuplicateAndReorderedPixelsNeverPass(){var m=assertDoesNotThrow(()->inspect(RAW,PROJECTED,List.of(10L))).matrices().get(0);for(String p:List.of(pixels().replace("1\t3\t2","1\t3\t3"),pixels().replace("1\t3\t2\n",""),pixels()+"1\t3\t2\n",pixels().replace("1\t2\t1\n1\t3\t2","1\t3\t2\n1\t2\t1")))assertThrows(IOException.class,()->HicProContactAudit.verify(m,new StringReader(bins()),new StringReader(p),LIMITS));}
    @Test void changedContigOrderCoordinatesAndNonfiniteWeightsRefuseResults(){var m=assertDoesNotThrow(()->inspect(RAW,PROJECTED,List.of(10L))).matrices().get(0);for(String b:List.of(bins().replace("chr1","1"),bins().replace("20\t21","20\t30"),bins().replace("0.5","Infinity"),bins().replace("0.5","-0.5")))assertThrows(IOException.class,()->HicProContactAudit.verify(m,new StringReader(b),new StringReader(pixels()),LIMITS));}
    @Test void rowPixelBinAndDecodedCapacitiesFailWithoutTruncating(){for(var limit:List.of(new HicProContactAudit.Limits(3,100,100,1024,100000),new HicProContactAudit.Limits(100,100,2,1024,100000),new HicProContactAudit.Limits(100,100,100,1024,10)))assertThrows(IOException.class,()->HicProContactAudit.inspect(new StringReader(RAW),new StringReader(PROJECTED),refs(),List.of(10L),null,limit));assertThrows(IllegalArgumentException.class,()->HicProContactAudit.inspect(new StringReader(RAW),new StringReader(PROJECTED),refs(),List.of(10L),null,new HicProContactAudit.Limits(100,3,100,1024,100000)));}
    @Test void invalidSourcePositionsStrandsAndIncompleteRecordsAreNotRepaired(){for(String s:List.of("x c 0 + c 1 -","x c 01 + c 1 -","x c 1 ? c 1 -","x c 1 + c 2","x c 9223372036854775808 + c 1 -"))assertThrows(IOException.class,()->HicProContactAudit.project(s));}
    @Test void retainedMatrixPopulationIsImmutable()throws Exception{var m=inspect(RAW,PROJECTED,List.of(10L)).matrices().get(0);assertThrows(UnsupportedOperationException.class,()->m.pixels().clear());assertThrows(UnsupportedOperationException.class,()->m.chromosomes().clear());}
}
