/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;
import static org.junit.jupiter.api.Assertions.*;

final class SmartpcaFstWorkflowTest {
    @TempDir Path temporary;
    private Map<String,Object> request(){return map("schema_version",1,"mode","RUN","engine","native","profile",SmartpcaFstContracts.PROFILE,"operation","FST","out","capture","inputs",map("genotypes","input.geno","markers","input.snp","samples","input.ind","populations",null,"genome_build","synthetic"));}
    private Map<String,Object> canonical(Map<String,Object> raw)throws IOException{return SmartpcaFstContracts.validate(raw,SmartpcaWorkflowContracts.address(temporary),SmartpcaWorkflowContracts.style());}
    private static Map<String,byte[]> fixture()throws Exception {
        var rows=new LinkedHashMap<String,byte[]>();
        try(var stream=SmartpcaFstWorkflowTest.class.getResourceAsStream("/org/jlinalg/genetics/smartpca-original-fst-reference.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(!entry.isDirectory())rows.put(entry.getName(),zip.readAllBytes());}
        }return rows;
    }
    private Path input(String name)throws Exception {
        var files=fixture();Path root=Files.createDirectory(temporary.resolve(name+"-"+java.util.UUID.randomUUID()));Files.createDirectory(root.resolve("inputs"));
        for(var pair:Map.of("input.geno","genotypes.geno","input.snp","markers.snp","input.ind","samples.ind").entrySet())Files.write(root.resolve("inputs").resolve(pair.getValue()),files.get("program/"+name+"/"+pair.getKey()));return root;
    }
    @Test void sourceFstDefaultsAreExplicitAndPcaSettingsCannotBeMistakenForFstFilters()throws Exception {
        var checked=canonical(request());assertEquals(false,object(checked.get("options")).get("inbreed"));assertEquals(.05,decimal(object(checked.get("options")).get("blgsize")).doubleValue());
        for(var option:List.of(map("maxmissing",0),map("minallelecnt",20),map("usenorm",false),map("killr2",true),map("inbreed","YES"),map("blgsize",0))) {
            var raw=request();raw.put("options",option);assertThrows(IOException.class,()->canonical(raw),option.toString());
        }
        var raw=request();var inputs=object(raw.get("inputs"));inputs.remove("populations");raw.put("inputs",inputs);assertThrows(IOException.class,()->canonical(raw));
    }
    @Test void allTwentyFourOriginalFileCasesRetainTheirPointEstimatesOrRefusals()throws Exception {
        int finite=0,refused=0;
        for(var entry:fixture().entrySet())if(entry.getKey().startsWith("program/")&&entry.getKey().endsWith("/reference.json")) {
            var reference=object(GprofilerJson.read(entry.getValue()));String name=reference.get("case").toString();Path root=input(name);var raw=request();raw.put("options",map("inbreed",reference.get("inbreed"),"blgsize",reference.get("block_length")));
            var inputs=object(raw.get("inputs"));inputs.put("populations",object(reference.get("input")).get("populations"));raw.put("inputs",inputs);var canonical=canonical(raw);
            if(reference.get("kind").equals("ORIGINAL_FSTONLY_REFUSAL")){assertThrows(IOException.class,()->SmartpcaFstEvaluation.evaluate(root,canonical,new SmartpcaWorkflowTables.Budget(10_000_000)));refused++;continue;}
            var budget=new SmartpcaWorkflowTables.Budget(10_000_000);var result=SmartpcaFstEvaluation.evaluate(root,canonical,budget);SmartpcaFstTables.writeOutputs(root,result,budget);SmartpcaFstTables.verifyOutputs(root,result);
            var recomputed=SmartpcaFstEvaluation.evaluate(root,canonical,null);SmartpcaFstTables.verifyOutputs(root,recomputed);var expected=list(reference.get("pairs"));assertEquals(expected.size(),result.analysis().pairs().size());
            for(int i=0;i<expected.size();i++){var pair=object(expected.get(i));assertEquals(Double.parseDouble(pair.get("estimate").toString()),result.analysis().pairs().get(i).estimate(),.50001e-6,name);assertEquals(Double.parseDouble(pair.get("standard_error").toString()),result.analysis().pairs().get(i).standardError(),.50001e-6,name);}
            if(name.equals("nonautosomal-influences-unit-detection"))assertEquals("SOURCE_DIVIDE_ALL_BY_100",result.result().get("position_convention"));finite++;
        }
        assertEquals(23,finite);assertEquals(1,refused);
    }
    @Test void zeroDenominatorAndOneBlockFieldsStayIneligibleUnderExplicitRetention()throws Exception {
        for(String name:List.of("unidentified-fixed-same","single-block")) {
            Path root=input(name);var raw=canonical(request());var first=SmartpcaFstEvaluation.evaluate(root,raw,new SmartpcaWorkflowTables.Budget(10_000_000));assertEquals(false,first.result().get("results_admitted"));
            assertTrue(list(first.result().get("pairs")).stream().allMatch(x->{try{return Boolean.FALSE.equals(object(x).get("fields_admitted"));}catch(IOException bad){throw new java.io.UncheckedIOException(bad);}}));
            Path second=input(name);var allowed=request();allowed.put("policies",map("allow_unreportable_fields",true));var kept=SmartpcaFstEvaluation.evaluate(second,canonical(allowed),new SmartpcaWorkflowTables.Budget(10_000_000));assertEquals(true,kept.result().get("results_admitted"));
            assertTrue(kept.analysis().pairs().stream().allMatch(p->p.admission()!=org.jlinalg.genetics.SmartpcaFst.Admission.COMPUTED));
        }
    }
    @Test void negativeFiniteSourceFstIsRetainedWithoutClampingOrInvalidProbabilityRules()throws Exception {
        Path root=input("negative-diploid");var result=SmartpcaFstEvaluation.evaluate(root,canonical(request()),new SmartpcaWorkflowTables.Budget(10_000_000));assertTrue(result.analysis().pairs().get(0).estimate()<0);assertEquals(true,result.result().get("results_admitted"));
    }
    @Test void literalMissingLookingPopulationIdsStayLiteralAndLocusTamperingIsDetected()throws Exception {
        Path root=input("missing-looking-identifiers");var raw=canonical(request());var budget=new SmartpcaWorkflowTables.Budget(10_000_000);var value=SmartpcaFstEvaluation.evaluate(root,raw,budget);SmartpcaFstTables.writeOutputs(root,value,budget);
        assertEquals(List.of("NA","NaN","Inf"),value.populations());SmartpcaFstTables.verifyOutputs(root,value);
        Path file=root.resolve("locus-contributions.tsv");String original=Files.readString(file);Files.writeString(file,original.replaceFirst("\tNaN\t","\tNAN\t"));assertThrows(IOException.class,()->SmartpcaFstEvaluation.evaluate(root,raw,null));
        Files.writeString(file,original+"extra\n");assertThrows(IOException.class,()->SmartpcaFstEvaluation.evaluate(root,raw,null));
    }
    @Test void physicalAtoiCoercionUnknownGroupsAndNoDataCannotSilentlyChangeInput()throws Exception {
        Path root=input("diploid-complete");Path markers=root.resolve("inputs/markers.snp");String original=Files.readString(markers);Files.writeString(markers,original.replaceFirst("1000000","1e6"));
        var position=assertThrows(IOException.class,()->SmartpcaFstEvaluation.evaluate(root,canonical(request()),new SmartpcaWorkflowTables.Budget(10_000_000)));assertTrue(position.getMessage().contains("physical position"));Files.writeString(markers,original);
        var raw=request();var inputs=object(raw.get("inputs"));inputs.put("populations",List.of("GROUP_0","ABSENT"));raw.put("inputs",inputs);var absent=assertThrows(IOException.class,()->SmartpcaFstEvaluation.evaluate(root,canonical(raw),null));assertTrue(absent.getMessage().contains("absent"));
    }
    @Test void numericOutputAndWorkCapsCloseTheOwnedWorkerWithoutCompletion()throws Exception {
        Path root=input("diploid-complete");assertThrows(IOException.class,()->SmartpcaFstEvaluation.evaluate(root,canonical(request()),new SmartpcaWorkflowTables.Budget(1)));assertEquals(true,read(root.resolve("compute-process.json")).get("owned_thread_stopped"));assertFalse(Files.exists(root.resolve("COMPLETED.json")));
        Path second=input("diploid-complete");var raw=request();raw.put("limits",map("maximum_estimated_work",1));assertThrows(IOException.class,()->SmartpcaFstEvaluation.evaluate(second,canonical(raw),new SmartpcaWorkflowTables.Budget(10_000_000)));
    }
    @Test void canonicalWindowsAndPosixRequestsRemainPortable()throws Exception {
        var win=SmartpcaFstContracts.validate(request(),"D:/cohort","WINDOWS");assertEquals("D:/cohort/input.geno",object(win.get("inputs")).get("genotypes"));
        var unix=SmartpcaFstContracts.validate(request(),"/cohort","POSIX");assertEquals("/cohort/capture",unix.get("out"));
    }
    @Test void publicRouterRejectsMissingOrIncompleteCapturesWithTheCorrectExitCode()throws Exception {
        var buffer=new java.io.ByteArrayOutputStream();try(var stream=new java.io.PrintStream(buffer,true,java.nio.charset.StandardCharsets.UTF_8)) {
            assertEquals(3,SmartpcaCommandCli.run(new String[]{"--verify",temporary.resolve("absent").toString()},stream,stream));
            Path root=Files.createDirectory(temporary.resolve("incomplete"));assertEquals(2,SmartpcaCommandCli.run(new String[]{"--verify",root.toString()},stream,stream));
            assertEquals(0,JLinAlgCli.run(new String[]{"smartpca-tools","--help"},stream,stream));assertTrue(buffer.toString(java.nio.charset.StandardCharsets.UTF_8).contains(SmartpcaFstContracts.PROFILE));
            var raw=request();raw.remove("mode");Path path=temporary.resolve("missing-mode.json");write(path,raw);buffer.reset();assertEquals(2,SmartpcaCommandCli.run(new String[]{"--request",path.toString()},stream,stream));assertTrue(buffer.toString(java.nio.charset.StandardCharsets.UTF_8).contains("mode"));assertFalse(buffer.toString(java.nio.charset.StandardCharsets.UTF_8).contains("NullPointerException"));
        }
    }
}
