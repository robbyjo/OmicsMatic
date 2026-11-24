/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

class GenomicsemPreparationTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path temporary;
    private Map<String,Object> request()throws Exception {
        return GenomicsemPrepContracts.validate(map("schema_version",1L,"mode","RUN","profile",GenomicsemPrepContracts.PROFILE,"operation","LDSC","runtime_home","runtime","runtime_addons",null,"out","new-output","bundle","bundle","paths",map("traits",List.of("T1.sumstats.gz","T2.sumstats.gz"),"ld","ld","wld","weights"),"options",map("trait_names",List.of("T1","T2"),"seed",769L,"upstream_behavior_reviewed",true,"warning_policy","RETAIN_AND_FLAG","unavailable_output_policy","FAIL","sample_prevalence",Arrays.asList(null,null),"population_prevalence",Arrays.asList(null,null),"separate_weights",true,"chromosomes",4L,"blocks",40L,"standardized",true,"select",false,"chisq_max",100L)));
    }
    @Test void sourceDescriptorAndOptionalPayloadHaveIndependentPins()throws Exception {
        assertEquals(GenomicsemPrepContracts.PROFILE,GenomicsemPrepRuntime.metadata().get("profile"));assertArrayEquals(Files.readAllBytes(Path.of("tools/literature/genomicsem_prep/ADDONS.json")),GenomicsemPrepRuntime.resource("ADDONS.json"));
    }
    @Test void refusesUnpairedLiabilityPrevalence()throws Exception {var q=request();var o=object(q.get("options"));o.put("sample_prevalence",Arrays.asList(.5,null));q.put("options",o);assertThrows(IOException.class,()->GenomicsemPrepContracts.validate(q));}
    @Test void requiresExplicitUnavailablePolicy()throws Exception {var q=request();var o=object(q.get("options"));o.remove("unavailable_output_policy");q.put("options",o);assertThrows(IOException.class,()->GenomicsemPrepContracts.validate(q));}
    @Test void refusesImplicitRepairOption()throws Exception {var q=request();var o=object(q.get("options"));o.put("clip_infinite_Z",true);q.put("options",o);assertThrows(IOException.class,()->GenomicsemPrepContracts.validate(q));}
    @Test void doesNotNormalizeHistoricalEdition()throws Exception {var q=request();q.put("profile","GenomicSEM0.5.0");assertThrows(GenomicsemRuntime.Unavailable.class,()->GenomicsemPrepContracts.validate(q));}
    @Test void chromosomeSelectionPreservesExplicitOrder()throws Exception {var q=request();var o=object(q.get("options"));o.put("select",List.of(4L,2L));q.put("options",o);assertEquals(List.of(4,2),GenomicsemPrepBundle.chromosomes(GenomicsemPrepContracts.validate(q)));}
    @Test void oddEvenSelectionsUseDeclaredChromosomeCount()throws Exception {var q=request();var o=object(q.get("options"));o.put("select","ODD");q.put("options",o);assertEquals(List.of(1,3),GenomicsemPrepBundle.chromosomes(GenomicsemPrepContracts.validate(q)));o.put("select","EVEN");q.put("options",o);assertEquals(List.of(2,4),GenomicsemPrepBundle.chromosomes(GenomicsemPrepContracts.validate(q)));}
    @Test void refusesTraversalAndGlobInputPaths(){for(String p:List.of("../outside","x/../y","/absolute","C:/data","ld/*.gz","x\\y"))assertThrows(IOException.class,()->GenomicsemPrepContracts.relative(p));}
    @Test void keepsTypedMissingNanAndInfinityDistinct()throws Exception {var na=GenomicsemPrepObject.Cell.parse("N","double");var nan=GenomicsemPrepObject.Cell.parse("Q","double");var inf=GenomicsemPrepObject.Cell.parse("P","double");assertTrue(na.missing());assertFalse(nan.missing());assertTrue(nan.nonfinite());assertEquals("NaN",GenomicsemPrepTables.display(nan));assertEquals("Inf",GenomicsemPrepTables.display(inf));assertThrows(IOException.class,inf::number);assertThrows(IOException.class,()->GenomicsemPrepObject.Cell.parse("P","integer"));}
    @Test void independentFp64SpectrumIdentifiesIndefiniteMatrixWithoutRepair()throws Exception {double[] values={1,2,2,1},original=values.clone();var m=new GenomicsemPrepMatrices.Matrix(2,2,values,List.of(),List.of(),0);var a=GenomicsemPrepMatrices.spectrum(m);assertEquals("NONPOSITIVE_DEFINITE_DIAGNOSTIC",a.get("status"));assertEquals(-1,Double.parseDouble(a.get("minimum_eigenvalue").toString()),1e-13);assertArrayEquals(original,values);}
    @Test void unavailableOrAsymmetricMatrixNeverReceivesPositiveDefiniteCertificate()throws Exception {var missing=new GenomicsemPrepMatrices.Matrix(2,2,new double[]{1,Double.NaN,0,1},List.of(),List.of(),1);assertEquals("UNAVAILABLE_SOURCE_CELLS",GenomicsemPrepMatrices.spectrum(missing).get("status"));var asymmetric=new GenomicsemPrepMatrices.Matrix(2,2,new double[]{1,.1,.2,1},List.of(),List.of(),0);assertEquals("ASYMMETRIC_SOURCE_MATRIX",GenomicsemPrepMatrices.spectrum(asymmetric).get("status"));}
    @Test void namedFieldReaderRetainsQuotedSpacesAndRefusesMultilineRecords()throws Exception {assertEquals(List.of("a b","c"),GenomicsemPrepTables.fields("\"a b\" c"));assertThrows(IOException.class,()->GenomicsemPrepTables.fields("\"unfinished"));}
    @Test void mainRouterReportsSixOperationsAndSkipRemedy()throws Exception {var bytes=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"genomicsem-prep","--help"},new PrintStream(bytes),new PrintStream(bytes)));assertTrue(bytes.toString().contains("S_LDSC"));assertTrue(bytes.toString().contains("SKIP3"));}
}
