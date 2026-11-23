/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jlinalg.mrpresso.MrPressoRandom;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.junit.jupiter.api.Assertions.*;

final class MrPressoWorkflowContractsTest {
    @TempDir Path temporary;
    private Map<String,Object> request(){return map("schema_version",1,"mode","RUN","engine","native","profile",MrPressoWorkflowContracts.PROFILE,"operation","ANALYZE","out","capture",
        "inputs",map("associations","input.tsv","id_column","SNP","beta_outcome","Y","se_outcome","SY","beta_exposure",List.of("X1"),"se_exposure",List.of("SX1")),
        "rng",map("kind","Mersenne-Twister/Inversion","sample_kind","Rejection","seed",19));}
    private Map<String,Object> canonical(Map<String,Object> request)throws IOException{return MrPressoWorkflowContracts.validate(request,temporary);}

    @Test void actualOriginalDefaultOptionsAreExplicitInCanonicalRequest()throws Exception {
        var result=canonical(request());var options=object(result.get("options"));assertEquals(false,options.get("OUTLIERtest"));assertEquals(false,options.get("DISTORTIONtest"));
        assertEquals(.05,decimal(options.get("SignifThreshold")).doubleValue());assertEquals(1000,number(options,"NbDistribution"));
        assertEquals(false,object(result.get("policies")).get("allow_unreportable_fields"));
    }
    @Test void savedWindowsPathsBindWithoutUsingTheVerifierOperatingSystem()throws Exception {
        var result=MrPressoWorkflowContracts.validate(request(),"D:/cohort/project","WINDOWS");
        assertEquals("D:/cohort/project/capture",result.get("out"));assertEquals("D:/cohort/project/input.tsv",object(result.get("inputs")).get("associations"));
        assertEquals(result,MrPressoWorkflowContracts.validate(result,"D:/","WINDOWS"));
    }
    @Test void savedPosixPathsRemainPortableAndDotSegmentsAreResolvedExplicitly()throws Exception {
        var raw=request();raw.put("out","../new/capture");var result=MrPressoWorkflowContracts.validate(raw,"/data/project","POSIX");
        assertEquals("/data/new/capture",result.get("out"));assertEquals("/data/project/input.tsv",object(result.get("inputs")).get("associations"));
    }
    @Test void ambiguousWindowsAndAboveRootPathsAreRefused() {
        for(String value:List.of("D:relative","/drive-relative","\\\\server\\share\\capture")){
            var raw=request();raw.put("out",value);assertThrows(IOException.class,()->MrPressoWorkflowContracts.validate(raw,"D:/project","WINDOWS"));
        }
        assertThrows(IOException.class,()->MrPressoWorkflowContracts.resolveAddress("/data","../../escape","POSIX"));
    }
    @Test void seedAndStateAreExclusiveAndSampleKindIsNotInferred()throws Exception {
        var raw=request();var rng=object(raw.get("rng"));rng.put("state",Arrays.stream(new MrPressoRandom(19,MrPressoRandom.Sampling.REJECTION).state()).boxed().toList());raw.put("rng",rng);
        assertThrows(IOException.class,()->canonical(raw));rng.remove("seed");raw.put("rng",rng);canonical(raw);
        rng.put("sample_kind","Rounding");raw.put("rng",rng);assertThrows(IOException.class,()->canonical(raw));
    }
    @Test void invalidRSeedAndFractionalSimulationCountAreRefused() {
        var raw=request();var rng=objectUnchecked(raw.get("rng"));rng.put("seed",Integer.MIN_VALUE);raw.put("rng",rng);assertThrows(IOException.class,()->canonical(raw));
        var fraction=request();fraction.put("options",map("NbDistribution",63.5));assertThrows(IOException.class,()->canonical(fraction));
    }
    @Test void unknownFieldsAndSilentlyChangedEnginesAreRefused() {
        var raw=request();raw.put("automatic_repair",true);assertThrows(IOException.class,()->canonical(raw));
        var wrong=request();wrong.put("engine","analytic-presso");assertThrows(IOException.class,()->canonical(wrong));
    }
    @Test void selectedAxesMustMatchAndRemainDistinctLiteralNames() {
        var raw=request();var input=objectUnchecked(raw.get("inputs"));input.put("se_exposure",List.of("SX1","SX2"));raw.put("inputs",input);assertThrows(IOException.class,()->canonical(raw));
        var same=request();input=objectUnchecked(same.get("inputs"));input.put("id_column","Y");same.put("inputs",input);assertThrows(IOException.class,()->canonical(same));
    }
    @Test void literalNaNanAndLeadingZeroIdsAreNotParsedAsMissing()throws Exception {
        Path table=temporary.resolve("input.tsv");Files.writeString(table,"SNP\tY\tSY\tX1\tSX1\nNA\t.1\t.04\t.3\t.02\nNaN\t.2\t.04\t.4\t.02\n0001\t.3\t.04\t.5\t.02\nrs4\t.2\t.04\t.6\t.02\nrs5\tNA\t.04\t.7\t.02\n",StandardCharsets.UTF_8);
        var prepared=MrPressoWorkflowData.read(table,canonical(request()));assertEquals(List.of("NA","NaN","0001","rs4","rs5"),prepared.data().rowNames());
        assertEquals(4,number(prepared.metadata(),"complete_cases"));assertTrue(Double.isNaN(prepared.data().outcome()[4]));
    }
    @Test void malformedNumericAndIncompleteWidthsDoNotGetTrimmedOrRepaired()throws Exception {
        Path path=temporary.resolve("input.tsv");Files.writeString(path,"SNP\tY\tSY\tX1\tSX1\nrs1\t 0.1\t.04\t.3\t.02\n");
        assertThrows(IOException.class,()->MrPressoWorkflowData.read(path,canonical(request())));
        Files.writeString(path,"SNP\tY\tSY\tX1\tSX1\nrs1\t.1\t.04\t.3\n");assertThrows(IOException.class,()->MrPressoWorkflowData.read(path,canonical(request())));
    }
    @Test void sourceDefaultDistributionCountIsIncludedInWorkRefusalBeforeCalculation()throws Exception {
        Path path=table();var raw=request();raw.put("limits",map("maximum_estimated_work",1));
        var error=assertThrows(IOException.class,()->MrPressoWorkflowEvaluation.evaluate(path,canonical(raw)));assertTrue(error.getMessage().contains("Estimated native simulation work"));
    }
    @Test void nativeDeadlineInterruptsAndClosesOnlyItsOwnedComputeThread()throws Exception {
        Path path=table();var raw=request();raw.put("options",map("NbDistribution",1_000_000));raw.put("limits",map("timeout_seconds",1,"maximum_estimated_work",1_000_000_000_000L));
        var before=threads();var error=assertThrows(IOException.class,()->MrPressoWorkflowEvaluation.evaluate(path,canonical(raw)));assertTrue(error.getMessage().contains("deadline"),error.toString());
        for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.getName().startsWith("omicsmatic-mrpresso-native-")&&!before.contains(thread.getId()))assertFalse(thread.isAlive(),"Owned computation leaked after timeout");
    }
    @Test void coherentScientificNumericAndExactRngContradictionsAreRefused()throws Exception {
        var expected=map("beta",map("kind","FINITE","value",.31),"state",List.of(10403,12),"id","0001");
        var wrong=map("beta",map("kind","FINITE","value",.32),"state",List.of(10403,12),"id","0001");assertThrows(IOException.class,()->MrPressoWorkflowEvaluation.compare(expected,wrong,"test"));
        var changed=map("beta",map("kind","FINITE","value",.31),"state",List.of(10403,13),"id","0001");assertThrows(IOException.class,()->MrPressoWorkflowEvaluation.compare(expected,changed,"test"));
    }
    @Test void sourceProvenanceIsCapturedExactlyAndMutationIsRejected()throws Exception {
        MrPressoWorkflowSource.sources(temporary,false);MrPressoWorkflowSource.sources(temporary,true);
        Files.writeString(temporary.resolve("source/R/mr_presso.R"),"changed");assertThrows(IOException.class,()->MrPressoWorkflowSource.sources(temporary,true));
    }
    @Test void tinyUnitCoefficientInflationAndZeroSubstitutionAreNotHiddenByAnAbsoluteTolerance() {
        var expected=map("kind","FINITE","value",3.1e-21);var inflated=map("kind","FINITE","value",3.1e-15);
        assertThrows(IOException.class,()->MrPressoWorkflowEvaluation.compare(expected,inflated,"tiny-beta"));
        assertThrows(IOException.class,()->MrPressoWorkflowEvaluation.compare(expected,map("kind","FINITE","value",0),"tiny-beta"));
        assertThrows(IOException.class,()->MrPressoWorkflowEvaluation.compare(map("kind","FINITE","value",0),map("kind","FINITE","value",Double.MIN_VALUE),"zero"));
    }
    @Test void humanReadableTablesKeepRawThenConditionalCorrectedRows()throws Exception {
        var raw=request();raw.put("options",map("NbDistribution",63));
        var result=MrPressoWorkflowEvaluation.evaluate(table(),canonical(raw));
        var tables=MrPressoWorkflowTables.tables(result);
        assertEquals(List.of("mr-results.tsv","global-test.tsv"),new ArrayList<>(tables.keySet()));
        var rows=tables.get("mr-results.tsv").split("\n");
        assertEquals(3,rows.length);assertTrue(rows[1].startsWith("X1\tRaw\t"));
        assertEquals("X1\tOutlier-corrected\tNA\tNA\tNA\tNA\tfalse\tSOURCE_NOT_ESTIMATED",rows[2]);
    }
    private Path table()throws Exception {
        var text=new StringBuilder("SNP\tY\tSY\tX1\tSX1\n");
        for(int i=0;i<12;i++)text.append("rs").append(i).append('\t').append(.1+Math.sin(i*.7)*.02).append("\t.04\t").append(.3+i*.02).append("\t.02\n");
        Path path=temporary.resolve("input.tsv");Files.writeString(path,text,StandardCharsets.UTF_8);return path;
    }
    private static Set<Long> threads(){var set=new HashSet<Long>();for(Thread thread:Thread.getAllStackTraces().keySet())set.add(thread.getId());return set;}
    private static Map<String,Object> objectUnchecked(Object value){try{return object(value);}catch(IOException error){throw new AssertionError(error);}}
}
