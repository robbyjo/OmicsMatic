/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IqtreeAlignmentReplayTest {
    private record Fixture(Map<String,byte[]> files,Properties properties) { }
    private static final Fixture FIXTURE=load();
    private static Fixture load() {
        try {
            byte[] raw;
            try(var source=IqtreeAlignmentReplayTest.class.getResourceAsStream("/org/jlinalg/phylogeny/iqtree-alignment/original-controls.zip")) {
                assertNotNull(source);raw=source.readNBytes(2097153);assertTrue(raw.length<=2097152);
            }
            assertEquals("bbe0eac5c0d19b7b8cff13b92984c0103ed8b9fdef82d0df43f0c432f855c260",hex(raw));
            Map<String,byte[]> files=new HashMap<>();long total=0;
            try(var source=new ZipInputStream(new ByteArrayInputStream(raw))) {
                for(var entry=source.getNextEntry();entry!=null;entry=source.getNextEntry()) {
                    assertFalse(entry.isDirectory());assertFalse(entry.getName().contains(".."));assertFalse(entry.getName().startsWith("/"));assertTrue(files.size()<2000);
                    byte[] value=source.readNBytes(1048577);assertTrue(value.length<=1048576);total+=value.length;assertTrue(total<=4194304);assertNull(files.put(entry.getName(),value));
                }
            }
            assertEquals(932,files.size());int checked=0;
            for(String line:text(files,"SHA256SUMS").split("\n"))if(!line.isEmpty()) { assertEquals(line.substring(0,64),hex(files.get(line.substring(66))));checked++; }
            assertEquals(files.size()-1,checked);Properties properties=new Properties();properties.load(new StringReader(text(files,"source.properties")));
            assertEquals(128,properties.getProperty("cases").split("\\|").length);return new Fixture(Map.copyOf(files),properties);
        } catch(Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private static String hex(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    private static String text(Map<String,byte[]> files,String name) { return new String(files.get(name),StandardCharsets.UTF_8); }
    private static String property(String id,String name) { return FIXTURE.properties().getProperty("case."+id+"."+name); }
    private static Stream<String> cases() { return Arrays.stream(FIXTURE.properties().getProperty("cases").split("\\|")); }
    private static void rounded(double actual,String displayed) { assertEquals(Double.parseDouble(displayed),actual,.5*new BigDecimal(displayed).ulp().doubleValue()+2e-12); }
    private static PhylogeneticAlignment alignment(String id,String content) throws Exception {
        var kind=switch(property(id,"kind")) {case "DNA"->PhylogeneticAlignment.Kind.DNA;case "AA"->PhylogeneticAlignment.Kind.AMINO_ACID;case "CODON"->PhylogeneticAlignment.Kind.CODON_STANDARD;default->throw new AssertionError("Unknown original datatype");};
        String format=property(id,"format");
        if(format.equals("FASTA"))return PhylogeneticAlignment.readFasta(new StringReader(content),kind,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,PhylogeneticAlignment.Limits.defaults());
        return PhylogeneticAlignment.readPhylip(new StringReader(content),kind,format.endsWith("sequential")?PhylogeneticAlignment.Layout.SEQUENTIAL:PhylogeneticAlignment.Layout.INTERLEAVED,format.contains("-fixed-")?PhylogeneticAlignment.NameField.FIXED_TEN_COLUMNS:PhylogeneticAlignment.NameField.RELAXED,false,PhylogeneticAlignment.Limits.defaults());
    }

    @ParameterizedTest(name="Retained original alignment protocol {0}") @MethodSource("cases")
    void everyOriginalAlignmentProtocolRetainsItsExactAcceptedOrRefusedScope(String id) throws Exception {
        Map<String,byte[]> files=FIXTURE.files();String prefix=property(id,"path"),nativeStatus=property(id,"native");
        String raw=text(files,prefix+"alignment.fasta"),version=property(id,"profile");int code=Integer.parseInt(property(id,"source_exit_code"));
        if(nativeStatus.equals("reject-invalid")) {
            assertEquals(2,code);assertThrows(IllegalArgumentException.class,()->alignment(id,raw));assertFalse(files.containsKey(prefix+"result.sitelh"));return;
        }
        var data=alignment(id,raw);assertEquals(4,data.rows().size());
        for(var row:data.rows())assertEquals(property(id,"sequence."+row.taxon()),row.sequence());
        if(nativeStatus.equals("reject-legacy-marker")) {
            assertEquals(2,code);assertThrows(IllegalArgumentException.class,()->data.requireIqtreeVersion(version));assertTrue(text(files,prefix+"stderr").contains("Unrecognized character !"));return;
        }
        data.requireIqtreeVersion(version);
        if(code!=0) {
            assertEquals(2,code);assertEquals("explicit-sequential-option-required",property(id,"reason"));assertTrue(text(files,prefix+"stderr").contains("wrong sequence length"));assertFalse(files.containsKey(prefix+"result.sitelh"));return;
        }
        int sites=Integer.parseInt(property(id,"model_sites"));assertEquals(sites,data.modelSites());
        NewickTree original;
        var reader=new NewickReader(new StringReader(text(files,prefix+"result.treefile")));
        try(reader) { original=reader.nextTree();assertNotNull(original);assertNull(reader.nextTree()); }
        assertTrue(reader.isComplete());assertEquals(1,reader.treesRead());assertEquals(data.taxa(),original.taxa());
        var fixed=NewickTree.parse(text(files,prefix+"fixed.nwk"));assertEquals(fixed.bipartitions(1000),original.bipartitions(1000));
        for(String a:data.taxa())for(String b:data.taxa())assertEquals(fixed.distance(a,b),original.distance(a,b),2e-15);
        String[] values=text(files,prefix+"result.sitelh").strip().split("\\s+");assertEquals(3+sites,values.length);assertEquals("1",values[0]);assertEquals(String.valueOf(sites),values[1]);assertEquals("Site_Lh",values[2]);
        for(int i=0;i<sites;i++)assertTrue(Double.isFinite(Double.parseDouble(values[i+3])));
        String report=text(files,prefix+"result.iqtree");var match=Pattern.compile("Log-likelihood of the tree: ([^ ]+)").matcher(report);assertTrue(match.find());String total=match.group(1);
        if(data.kind()==PhylogeneticAlignment.Kind.DNA || data.kind()==PhylogeneticAlignment.Kind.AMINO_ACID) {
            rounded(Double.parseDouble(property(id,"oracle.total")),total);
            for(int i=0;i<sites;i++)rounded(Double.parseDouble(property(id,"oracle.site."+i)),values[i+3]);
            if(data.kind()==PhylogeneticAlignment.Kind.DNA) {
                var nativeResult=JukesCantorLikelihood.evaluate(original,data.jc69Sequences());assertEquals(Double.parseDouble(property(id,"oracle.total")),nativeResult.logLikelihood(),2e-12);
                double[] probabilities=nativeResult.siteLogLikelihoods();for(int i=0;i<sites;i++)assertEquals(Double.parseDouble(property(id,"oracle.site."+i)),probabilities[i],2e-13);
            }
            // AA checks above use independent twenty-state enumeration; no native
            // protein tree-search or fitted-model likelihood is being claimed.
        } else {
            // A has four wholly missing triplets, the other rows have three
            // each; A's AAR and NTN contribute two further whole-codon unknowns.
            assertEquals(12,sites);assertEquals(15,data.statistics().missingCells());assertEquals(2,data.statistics().partialCodonCellsAsUnknown());
            String stdout=text(files,prefix+"stdout");assertTrue(stdout.contains("ambiguous character AAR at site 19"));assertTrue(stdout.contains("ambiguous character NTN at site 22"));
            for(int i=0;i<3;i++) {
                String source=property(id,"source_missing_codon_site."+i);assertEquals(source,values[3+sites-3+i]);double value=Double.parseDouble(source);
                assertNotEquals(0.0,value);assertTrue(Math.abs(value)<=32*Math.ulp(1.0));
            }
            // Signed source roundoff is retained. This is not independent MG parity.
        }
        double sum=0,budget=.5*new BigDecimal(total).ulp().doubleValue()+2e-11;
        for(int i=0;i<sites;i++) { sum+=Double.parseDouble(values[i+3]);budget+=.5*new BigDecimal(values[i+3]).ulp().doubleValue(); }
        assertEquals(Double.parseDouble(total),sum,budget,"Complete printed site values must reconcile with the printed total");
    }
}
