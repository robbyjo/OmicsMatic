/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IqtreeFixedLikelihoodReplayTest {
    private static final String FIXTURE="/org/jlinalg/phylogeny/iqtree-fixed-jc69/original-controls.zip";

    private static Map<String,byte[]> fixture() throws Exception {
        byte[] raw;
        try (var source=IqtreeFixedLikelihoodReplayTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(source);raw=source.readNBytes(1048577);assertTrue(raw.length<=1048576);
        }
        assertEquals("c7769535336a04bf1899dcc449f494e136a1518e189d3157d196e0de2d7dd54c",hex(raw));
        Map<String,byte[]> entries=new HashMap<>();long total=0;
        try (var source=new ZipInputStream(new ByteArrayInputStream(raw))) {
            for (var entry=source.getNextEntry();entry!=null;entry=source.getNextEntry()) {
                assertFalse(entry.isDirectory());assertFalse(entry.getName().contains(".."));assertFalse(entry.getName().startsWith("/"));
                byte[] data=source.readNBytes(1048577);assertTrue(data.length<=1048576);total+=data.length;assertTrue(total<=2097152);
                assertNull(entries.put(entry.getName(),data));
            }
        }
        int checked=0;
        for (String line:text(entries,"SHA256SUMS").split("\n")) if (!line.isEmpty()) {
            assertEquals(line.substring(0,64),hex(entries.get(line.substring(66))));checked++;
        }
        assertEquals(entries.size()-1,checked);return entries;
    }
    private static String hex(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
    private static String text(Map<String,byte[]> files,String name) { return new String(files.get(name),StandardCharsets.UTF_8); }
    private static void rounded(double actual,String displayed) {
        double quantum=new BigDecimal(displayed).ulp().doubleValue();
        assertEquals(Double.parseDouble(displayed),actual,.5*quantum+2e-12,"Difference must fit the actual printed decimal precision");
    }

    @ParameterizedTest
    @ValueSource(strings={"1.6.12","2.1.2","2.1.4-beta","2.2.0.3","2.3.1","2.3.5","2.3.6","2.4.0"})
    void completeOriginalFixedTreeAndEverySiteMatchIndependentNativePruning(String version) throws Exception {
        Map<String,byte[]> files=fixture();Properties properties=new Properties();properties.load(new StringReader(text(files,"source.properties")));
        assertEquals("JC",properties.getProperty("model"));assertEquals("true",properties.getProperty("fixed_branch_lengths"));
        assertTrue(properties.getProperty("program.sha256."+version).matches("[a-f0-9]{64}"));
        Map<String,String> alignment=new LinkedHashMap<>();String name=null;
        for (String line:text(files,"input/alignment.fasta").split("\\R")) {
            if (line.startsWith(">")) { name=line.substring(1);assertNull(alignment.put(name,"")); }
            else if (!line.isEmpty()) { assertNotNull(name);alignment.put(name,alignment.get(name)+line); }
        }
        var requested=NewickTree.parse(text(files,"input/fixed.nwk"));
        String prefix="profiles/"+version+"/";
        var original=NewickTree.parse(text(files,prefix+"result.treefile"));
        assertEquals(requested.taxa(),original.taxa());assertEquals(4,original.taxa().size());
        assertEquals(requested.bipartitions(1000),original.bipartitions(1000));
        for (String a:requested.taxa()) for (String b:requested.taxa()) assertEquals(requested.distance(a,b),original.distance(a,b),1e-15);
        var nativeResult=JukesCantorLikelihood.evaluate(original,alignment);
        assertEquals(Double.parseDouble(properties.getProperty("oracle.log_likelihood")),nativeResult.logLikelihood(),2e-12);
        String[] rows=text(files,prefix+"result.sitelh").strip().split("\\R");assertEquals("1 12",rows[0]);assertEquals(2,rows.length);
        String[] values=rows[1].strip().split("\\s+");assertEquals("Site_Lh",values[0]);assertEquals(13,values.length);
        double[] siteValues=nativeResult.siteLogLikelihoods();assertEquals(12,siteValues.length);
        for (int i=0;i<12;i++) {
            assertEquals(Double.parseDouble(properties.getProperty("oracle.site."+i)),siteValues[i],2e-13);
            rounded(siteValues[i],values[i+1]);
        }
        String report=text(files,prefix+"result.iqtree");
        var matcher=Pattern.compile("Log-likelihood of the tree: ([^ ]+)").matcher(report);assertTrue(matcher.find());rounded(nativeResult.logLikelihood(),matcher.group(1));
        assertTrue(report.contains("Number of free parameters (#branches + #model parameters): 0"));
        assertEquals(0,nativeResult.zeroProbabilitySites());
        assertTrue(text(files,"UPSTREAM-GPL-2.0.txt").contains("GNU GENERAL PUBLIC LICENSE"));
    }
}
