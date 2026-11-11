/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.phylogeny.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IqtreeInferenceReplayTest {
    private record Fixture(Map<String,byte[]> files,List<Map<?,?>> protocols) { }
    private static final Fixture FIXTURE=load();
    @TempDir Path temporary;
    private static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private static Fixture load() {
        try {
            byte[] raw;
            try(var input=IqtreeInferenceReplayTest.class.getResourceAsStream("/org/jlinalg/phylogeny/iqtree-inference/original-controls.zip")) {
                assertNotNull(input);raw=input.readNBytes(8388609);assertTrue(raw.length<=8388608);
            }
            assertEquals("dbdb94013aaf99899ef1d1e980b367e86c951f01612618a2f0683f00b969a4d3",sha(raw));
            var files=new HashMap<String,byte[]>();long total=0;
            try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))) {
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    assertFalse(entry.isDirectory());assertFalse(entry.getName().startsWith("/"));assertFalse(entry.getName().contains(".."));assertFalse(entry.getName().contains("\\"));
                    byte[] bytes=zip.readNBytes(16777217);assertTrue(bytes.length<=16777216);total+=bytes.length;assertTrue(total<=52428800&&files.size()<2000);
                    assertNull(files.put(entry.getName(),bytes));
                }
            }
            assertEquals(972,files.size());assertEquals(40491868,total);int checks=0;
            for(String line:text(files,"SHA256SUMS").split("\n"))if(!line.isEmpty()) {assertEquals(line.substring(0,64),sha(files.get(line.substring(66))));checks++;}
            assertEquals(971,checks);
            List<?> original=(List<?>)SimpleJson.parse(text(files,"capture/original/observed.json"));assertEquals(82,original.size());
            var protocols=new ArrayList<Map<?,?>>();for(Object item:original)protocols.add((Map<?,?>)item);
            Map<?,?> typed=(Map<?,?>)SimpleJson.parse(text(files,"typed-standard/observed.json"));
            for(Object item:(List<?>)typed.get("protocols")) {
                Map<?,?> control=(Map<?,?>)item;String version=(String)control.get("version");
                protocols.add(Map.of("name","typed:"+version,"version",version,"exit_code",0L,"case","typed-standard-1000","model","JC","dataset","DNA"));
            }
            assertEquals(84,protocols.size());return new Fixture(Map.copyOf(files),List.copyOf(protocols));
        } catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    private static String text(Map<String,byte[]> files,String name) {byte[] raw=files.get(name);assertNotNull(raw,name);return new String(raw,StandardCharsets.UTF_8);}
    private static Stream<String> protocols(){return FIXTURE.protocols().stream().map(p->(String)p.get("name"));}

    @Test void scopeRetainsFailedExpectedSuccessAttemptsAndNeverClaimsAPublicAnalysis()throws Exception {
        var scope=(Map<?,?>)SimpleJson.parse(text(FIXTURE.files(),"scope.json"));
        assertEquals(82L,scope.get("original_protocols"));assertEquals(80L,scope.get("completed_original_fits"));
        assertEquals(2L,scope.get("failed_original_expected_success_attempts"));assertEquals(2L,scope.get("completed_typed_standard_bootstrap_retries"));
        assertEquals(false,scope.get("scientific_analysis_admitted"));assertEquals(false,scope.get("public_analysis_adapter_available"));
        assertEquals(false,scope.get("independent_non_JC_fitted_likelihood_validation"));
    }

    @ParameterizedTest(name="Original inference output replay {0}") @MethodSource("protocols")
    void completeSourceOutputsAndFailedPrefixesRetainTheirExactScope(String name)throws Exception {
        Map<?,?> protocol=FIXTURE.protocols().stream().filter(p->p.get("name").equals(name)).findFirst().orElseThrow();
        Map<String,byte[]> files=FIXTURE.files();boolean typed=name.startsWith("typed:");
        String base=typed?"typed-standard/"+protocol.get("version")+"/":"original/"+name+"/";
        String output=base+(typed?"process/":"");String role=(String)protocol.get("case");
        String dataset=(String)protocol.get("dataset");
        var kind=switch(dataset){case "AA"->PhylogeneticAlignment.Kind.AMINO_ACID;case "CODON"->PhylogeneticAlignment.Kind.CODON_STANDARD;default->PhylogeneticAlignment.Kind.DNA;};
        var alignment=PhylogeneticAlignment.readFasta(new StringReader(text(files,base+"alignment.fasta")),kind,
                PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,PhylogeneticAlignment.Limits.defaults());
        alignment.requireIqtreeVersion((String)protocol.get("version"));assertEquals(12,alignment.taxa().size());
        if(((Number)protocol.get("exit_code")).intValue()!=0) {
            assertEquals(-9L,protocol.get("exit_code"));assertEquals(0L,protocol.get("expected_exit_code"));
            if(files.containsKey(output+"result.boottrees"))
                assertThrows(java.io.IOException.class,()->BootstrapSupport.count(new StringReader(text(files,output+"result.boottrees")),alignment.taxa(),1000,true,BootstrapSupport.Limits.defaults()));
            assertFalse(files.containsKey(output+"COMPLETED.json"));return;
        }
        String report=text(files,output+"result.iqtree");
        var total=Pattern.compile("(?m)^Log-likelihood of the tree: ([^ ]+) ").matcher(report);assertTrue(total.find());String printed=total.group(1);assertFalse(total.find());
        var tree=NewickTree.parse(text(files,output+"result.treefile"));assertEquals(alignment.taxa(),tree.taxa());tree.requireMetricLengths();
        double[] independent=report.contains("\nModel of substitution: JC\n")?JukesCantorLikelihood.evaluate(tree,alignment.jc69Sequences()).siteLogLikelihoods():null;
        Path sites=temporary.toRealPath().resolve("source.sitelh");Files.write(sites,files.get(output+"result.sitelh"));
        var audit=IqtreeSiteLikelihoods.audit(sites,16777216,alignment.modelSites(),printed,independent,independent==null?0:1e-7,()->{});
        assertTrue(audit.totalAgrees(),name);assertEquals(0,audit.nativeDisagreements(),name);
        if(role.contains("UFB")||role.contains("standard-")) {
            long replicates=role.equals("DNA-standard-100")?100:1000;
            String extension=role.contains("UFB")?"ufboot":"boottrees";
            var counts=BootstrapSupport.count(new StringReader(text(files,output+"result."+extension)),alignment.taxa(),replicates,true,BootstrapSupport.Limits.defaults());
            assertEquals(replicates,counts.replicates());
            for(String treeRole:List.of("treefile","contree")) {
                var supported=NewickTree.parse(text(files,output+"result."+treeRole));supported.requireMetricLengths();
                int columns=treeRole.equals("treefile")&&role.equals("DNA-UFB-SHaLRT-BNNI")?2:1;
                var values=BootstrapSupport.compare(supported,counts,columns,columns-1,1000000);assertEquals(9,values.size());
                assertTrue(values.stream().allMatch(BootstrapSupport.Comparison::agreesAtPrintedPrecision),name+" "+treeRole);
            }
        }
        if(typed) {
            var process=(Map<?,?>)SimpleJson.parse(text(files,output+"process.json"));
            assertEquals("completed-process-only",process.get("status"));assertEquals(0L,process.get("exit_code"));assertEquals(List.of(),process.get("owned_live_after_cleanup"));
        }
        assertFalse(files.containsKey(output+"COMPLETED.json"));
    }
}
