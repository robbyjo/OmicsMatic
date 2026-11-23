/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SquidpyWorkflowContractsTest {
    @TempDir Path temp;
    private Map<String,Object> request(String edition,String operation) {
        var packages=new LinkedHashMap<String,Object>();
        for(String name:List.of("anndata","numpy","scipy","pandas","scanpy","numba","scikit-learn","statsmodels","omnipath"))packages.put(name,"1.0");
        var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("mode","RUN");value.put("profile",SquidpyWorkflowContracts.PROFILE);
        value.put("operation",operation);value.put("out","new-analysis");value.put("python","private/python");value.put("expected_python","3.12.3");value.put("expected_squidpy",edition);value.put("expected_packages",packages);
        value.put("input",Map.of("format","prepared_json","file","input.json"));value.put("options",new LinkedHashMap<String,Object>());return value;
    }
    @Test void localRequestGetsExplicitLimitsAndSeparateScientificPolicies()throws IOException {
        var input=request("1.8.4","SPATIAL_NEIGHBORS");input.put("options",Map.of("coord_type","generic","n_neighs",30));
        var canonical=SquidpyWorkflowContracts.validate(input,temp);
        assertEquals(temp.resolve("new-analysis").toString(),canonical.get("out"));
        assertEquals(input.get("options"),canonical.get("options"));
        assertEquals(Map.of("allow_no_result_rows",false,"allow_identifier_changes",false,"allow_replay_differences",false),canonical.get("admission"));
        SquidpyWorkflowContracts.validateSaved(canonical);
    }
    @Test void unknownOrWrongEditionOptionsAreRefusedInsteadOfIgnored() {
        var old=request("1.1.2","SPATIAL_NEIGHBORS");old.put("options",Map.of("library_key","section"));
        assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(old,temp));
        var current=request("1.8.4","SPATIAL_AUTOCORR");current.put("options",Map.of("seed",17));
        assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(current,temp));
        var historical=request("1.2.2","SPATIAL_AUTOCORR");historical.put("options",Map.of("rng",17));
        assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(historical,temp));
        current.put("options",Map.of("n_neighbor",3));assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(current,temp));
    }
    @Test void originalSeedNamesAndLiteralGeneIdentifiersRemainUnchanged()throws IOException {
        for(String version:List.of("1.1.2","1.2.2","1.8.4")){
            var request=request(version,"SPATIAL_AUTOCORR");String seed=version.equals("1.8.4")?"rng":"seed";
            var options=Map.of(seed,17,"genes",List.of("NA","NaN","gene\twith\tseparators","gene\nsecond line"));request.put("options",options);
            assertEquals(options,SquidpyWorkflowContracts.validate(request,temp).get("options"));
        }
    }
    @Test void absentOriginalReaderIsASkippedPrerequisite() {
        var r=request("1.1.2","READ_NANOSTRING");
        assertThrows(SquidpyWorkflowContracts.Unavailable.class,()->SquidpyWorkflowContracts.validate(r,temp));
    }
    @Test void localInteractionPairsAndEveryCoreDependencyEditionAreRequired() {
        var r=request("1.8.4","LIGREC");assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(r,temp));
        r.put("interactions_file","captured-pairs.json");r.put("expected_packages",Map.of("numpy","1.0"));
        assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(r,temp));
    }
    @Test void nanostringFilesStayInsideCapturedDirectory()throws IOException {
        var r=request("1.8.4","READ_NANOSTRING");r.remove("input");r.put("nanostring_directory","cosmx");
        r.put("options",Map.of("counts_file","counts.csv","meta_file","meta.csv"));
        assertEquals(temp.resolve("cosmx").toString(),SquidpyWorkflowContracts.validate(r,temp).get("nanostring_directory"));
        r.put("options",Map.of("counts_file","../unrelated.csv","meta_file","meta.csv"));assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(r,temp));
    }
    @Test void threadAndByteCapacitiesAndBooleanPoliciesHaveStrictBounds() {
        var r=request("1.8.4","SPATIAL_NEIGHBORS");
        for(var bad:List.of(Map.of("threads",33),Map.of("maximum_output_bytes",0),Map.of("timeout_seconds",0),Map.of("maximum_files",63))){r.put("limits",bad);assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(r,temp));}
        r.remove("limits");r.put("options",Map.of("n_jobs",2));assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(r,temp));
        r.put("options",Map.of());r.put("admission",Map.of("allow_no_result_rows","true"));assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(r,temp));
    }
    @Test void overflowingScientificNumbersDoNotBecomeImplicitInfinity() {
        var r=request("1.8.4","SPATIAL_NEIGHBORS");r.put("options",Map.of("radius",new GprofilerJson.Numeric("1e10000")));
        assertThrows(IOException.class,()->SquidpyWorkflowContracts.validate(r,temp));
    }
    @Test void recordedWindowsUnixAndUncPathsCanBeReadWithoutThoseHosts()throws IOException {
        assertEquals("F:/studies/counts.mtx",SquidpyWorkflowContracts.recordedPath("F:\\studies\\analysis","..\\counts.mtx"));
        assertEquals("/work/runs/input.h5ad",SquidpyWorkflowContracts.recordedPath("/work/runs/current","../input.h5ad"));
        assertEquals("//server/share/counts.mtx",SquidpyWorkflowContracts.recordedPath("\\\\server\\share\\run","../counts.mtx"));
    }
    @Test void runtimeClosureIncludesPythonHtmlAndTestModulesAndExcludesOnlyCacheDirectories()throws IOException {
        Path root=temp.resolve("runtime");Files.createDirectory(root);
        for(String dir:List.of("html","doc","tests","__pycache__")){Files.createDirectory(root.resolve(dir));Files.writeString(root.resolve(dir+"/module.py"),"# "+dir,StandardCharsets.UTF_8);}
        var result=SquidpyWorkflowFiles.runtimeFiles(List.of(root));var files=(Map<?,?>)result.get("files");
        assertEquals(3,files.size());assertTrue(files.containsKey(root.resolve("html/module.py").toString()));assertTrue(files.containsKey(root.resolve("doc/module.py").toString()));assertTrue(files.containsKey(root.resolve("tests/module.py").toString()));assertFalse(files.containsKey(root.resolve("__pycache__/module.py").toString()));
    }
    @Test void runtimeFingerprintsReadBytesEvenWhenSizeAndModificationTimeAreRestored()throws IOException {
        Path file=temp.resolve("provider.py");Files.writeString(file,"AAAA");var modified=Files.getLastModifiedTime(file);
        var before=SquidpyWorkflowFiles.runtimeFiles(List.of(file));Files.writeString(file,"BBBB");Files.setLastModifiedTime(file,modified);
        var after=SquidpyWorkflowFiles.runtimeFiles(List.of(file));assertNotEquals(before.get("files"),after.get("files"));
    }
    @Test void typedMissingValuesRemainDistinctFromLiteralTextAndLargeIntegers()throws IOException {
        SquidpyWorkflowFrames.scalar(Map.of("kind","string","value","NaN"),0);
        SquidpyWorkflowFrames.scalar(Map.of("kind","integer","value","9007199254740993"),0);
        assertTrue(Double.isNaN(SquidpyWorkflowFrames.floating(Map.of("kind","float","value","NaN"))));
        assertThrows(IOException.class,()->SquidpyWorkflowFrames.floating(Map.of("kind","string","value","NaN")));
        assertThrows(IOException.class,()->SquidpyWorkflowFrames.scalar(Map.of("kind","float","value","nan"),0));
        assertThrows(IOException.class,()->SquidpyWorkflowFrames.scalar(Map.of("kind","None","value",0),0));
    }
    @Test void matrixAuditDoesNotRoundTripOrRepairExactIntegerSourceText()throws IOException {
        Path file=temp.resolve("X.mtx");byte[] raw="%%MatrixMarket matrix coordinate integer general\n%\n1 1 1\n1 1 9007199254740993\n".getBytes(StandardCharsets.US_ASCII);Files.write(file,raw);
        var canonical=SquidpyWorkflowContracts.validate(request("1.8.4","SPATIAL_NEIGHBORS"),temp);
        var facts=SquidpyWorkflowFrames.matrix(temp,Map.of("file","X.mtx","shape",List.of(1,1),"stored_entries",1,"dtype","int64"),canonical);
        assertEquals(1L,facts.get("stored_entries"));assertArrayEquals(raw,Files.readAllBytes(file));
    }
    @Test void matrixControlBytesAreRefusedBeforeWhitespaceTrimming()throws IOException {
        var canonical=SquidpyWorkflowContracts.validate(request("1.8.4","SPATIAL_NEIGHBORS"),temp);
        var definition=Map.<String,Object>of("file","float32.mtx","shape",List.of(1,1),"dtype","float32","sparse_format","csr","stored_entries",1);
        String header="%%MatrixMarket matrix coordinate real general\n%\n1 1 1\n";
        for(char invalid:new char[]{0,1,11,12,31}){
            Files.writeString(temp.resolve("float32.mtx"),header+"1 1 1.00000000000000"+invalid+"\n",StandardCharsets.US_ASCII);
            var failure=assertThrows(IOException.class,()->SquidpyWorkflowFrames.matrix(temp,definition,canonical));assertTrue(failure.getMessage().contains("control byte"));
        }
        Files.writeString(temp.resolve("float32.mtx"),header+"1\t1\t1.00000000e+00\n",StandardCharsets.US_ASCII);
        assertEquals(1L,SquidpyWorkflowFrames.matrix(temp,definition,canonical).get("stored_entries"));
    }
}
