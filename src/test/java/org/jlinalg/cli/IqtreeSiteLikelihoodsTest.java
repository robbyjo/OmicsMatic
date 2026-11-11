/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IqtreeSiteLikelihoodsTest {
    @TempDir Path temporary;
    private Path write(String content)throws IOException {Path path=temporary.toRealPath().resolve("sites");Files.writeString(path,content);return path;}
    @Test void wholeRowsUseTheSumOfPrintedDecimalUncertainties()throws Exception {
        var result=IqtreeSiteLikelihoods.audit(write("1 3\nSite_Lh -1.234 -2.3 -3.40\n"),1000,3,"-6.934",null,0,()->{});
        assertTrue(result.totalAgrees());assertEquals("-6.934",result.sumOfPrintedSites());assertEquals("0.0560",result.combinedHalfRoundingInterval());
        var wrong=IqtreeSiteLikelihoods.audit(write("1 3\nSite_Lh -1.234 -2.3 -3.40\n"),1000,3,"-7.00",null,0,()->{});assertFalse(wrong.totalAgrees());
    }
    @Test void signedTinySourceValuesArePreservedAndNativeDisagreementsRemainExplicit()throws Exception {
        var result=IqtreeSiteLikelihoods.audit(write("1 2\nSite_Lh 1e-15 -2e-15\n"),1000,2,"-1e-15",new double[]{0,-1},1e-7,()->{});
        assertEquals(1,result.positiveSourceCells());assertEquals("-0.000000000000001",result.sumOfPrintedSites());assertTrue(result.totalAgrees());
        assertEquals(2,result.nativeComparisons());assertEquals(1,result.nativeDisagreements());
        var original=IqtreeSiteLikelihoods.audit(write("1 2\nSite_Lh -5.9952e-15 4.21885e-15\n"),1000,2,"-0.0000",new double[]{0,0},1e-7,()->{});
        assertTrue(original.totalAgrees());assertEquals(1,original.positiveSourceCells());assertEquals(0,original.nativeDisagreements());
        assertEquals("-0.00000000000000177635",original.sumOfPrintedSites());assertEquals("-0.0000",original.reportedTotal());
    }
    @Test void truncatedExtraDuplicateAndNonfiniteCellsCannotCertifyAPrefix()throws Exception {
        for(String bad:new String[]{"1 2 Site_Lh -1","1 2 Site_Lh -1 -2 -3","1 2 Site_Lh -1 -2 Other -3", "2 2 Site_Lh -1 -2", "1 3 Site_Lh -1 -2 -3", "1 2 Tree1 -1 -2", "1 2 Site_Lh NaN -2", "1 2 Site_Lh -Infinity -2", "1 2 Site_Lh 1e999 -2"}) {
            Path path=write(bad);assertThrows(IOException.class,()->IqtreeSiteLikelihoods.audit(path,1000,2,"-3",null,0,()->{}),bad);
        }
    }
    @Test void malformedUtf8CapacityAndCancellationFailWithoutReturningAnAudit()throws Exception {
        Path path=write("1 2 Site_Lh -1 -2");assertThrows(IOException.class,()->IqtreeSiteLikelihoods.audit(path,3,2,"-3",null,0,()->{}));
        assertThrows(IllegalStateException.class,()->IqtreeSiteLikelihoods.audit(path,1000,2,"-3",null,0,()->{throw new IllegalStateException("cancelled");}));
        Files.write(path,new byte[]{'1',' ', '2',' ',(byte)0xc0,(byte)0xaf});assertThrows(IOException.class,()->IqtreeSiteLikelihoods.audit(path,1000,2,"-3",null,0,()->{}));
    }
    @Test void completeNativeDimensionsAndAllowanceAreRequired()throws Exception {
        Path path=write("1 2 Site_Lh -1 -2");
        assertThrows(IOException.class,()->IqtreeSiteLikelihoods.audit(path,1000,2,"-3",new double[]{-1},0,()->{}));
        assertThrows(IOException.class,()->IqtreeSiteLikelihoods.audit(path,1000,2,"-3",null,1e-7,()->{}));
        assertThrows(IOException.class,()->IqtreeSiteLikelihoods.audit(path,1000,2,"-3",null,Double.NaN,()->{}));
    }
    @Test void all68EarlierOriginalFitsIncludeCodonRoundoffBeyondEighteenDecimalPlaces()throws Exception {
        byte[] raw;
        try(var input=getClass().getResourceAsStream("/org/jlinalg/phylogeny/iqtree-alignment/original-controls.zip")) {
            assertNotNull(input);raw=input.readNBytes(2097153);assertTrue(raw.length<=2097152);
        }
        assertEquals("bbe0eac5c0d19b7b8cff13b92984c0103ed8b9fdef82d0df43f0c432f855c260",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var files=new HashMap<String,byte[]>();long total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                byte[] bytes=zip.readNBytes(1048577);total+=bytes.length;
                assertTrue(bytes.length<=1048576&&total<=4194304&&files.size()<2000);assertNull(files.put(entry.getName(),bytes));
            }
        }
        var properties=new Properties();properties.load(new StringReader(new String(files.get("source.properties"),StandardCharsets.UTF_8)));
        int protocols=0;long positive=0;
        for(String id:properties.getProperty("cases").split("\\|")) {
            String key="case."+id+".";if(!properties.getProperty(key+"source_exit_code").equals("0"))continue;
            String prefix=properties.getProperty(key+"path");
            var report=Pattern.compile("(?m)^Log-likelihood of the tree: ([^ ]+) ").matcher(new String(files.get(prefix+"result.iqtree"),StandardCharsets.UTF_8));assertTrue(report.find());
            Path source=temporary.toRealPath().resolve("retained-"+id);Files.write(source,files.get(prefix+"result.sitelh"));
            var audit=IqtreeSiteLikelihoods.audit(source,1048576,Integer.parseInt(properties.getProperty(key+"model_sites")),report.group(1),null,0,()->{});
            assertTrue(audit.totalAgrees(),id);positive+=audit.positiveSourceCells();protocols++;
        }
        assertEquals(68,protocols);assertTrue(positive>0,"Actual signed source roundoff must remain represented");
    }
}
