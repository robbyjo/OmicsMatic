/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Artificial controls for request admission, complete saved inputs and failure reporting. */
class DiannWorkflowTest {
    @TempDir Path tmp;
    @SuppressWarnings("unchecked") static Map<String,Object> document()throws IOException {
        return (Map<String,Object>)SimpleJson.parse(NextflowJson.json(DiannToolsCli.example()));
    }
    @SuppressWarnings("unchecked") static Map<String,Object> field(Map<String,Object> doc,String name){return(Map<String,Object>)doc.get(name);}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> runs(Map<String,Object> doc){return(List<Map<String,Object>>)doc.get("raw_runs");}
    @Test void portableRequestRetainsOriginalPathsAndAllExplicitScientificSettings()throws Exception {
        var doc=document();var spec=DiannRequest.parse(doc,false);
        assertEquals("/absolute/original-reference.fasta",spec.reference());assertEquals(2,spec.runs().size());
        assertEquals("run-A.d",spec.runs().get(0).basename());assertEquals("run-A",spec.runs().get(0).name());
        assertTrue(spec.quantification().mbr());assertEquals(0,spec.quantification().window());
        assertEquals(2,spec.generation().missed());assertEquals(1,spec.generation().variableMods());
        assertEquals(NextflowJson.json(doc),NextflowJson.json(spec.document()));
    }
    @Test void unsupportedPrerequisitesSkipInsteadOfSelectingSubstitutes()throws Exception {
        for(String edition:List.of("1.8.2","2.0","latest")){var doc=document();doc.put("edition",edition);assertThrows(DiannRuntime.Unavailable.class,()->DiannRequest.parse(doc,false));}
        var use=document();use.put("private_academic_local",false);assertThrows(DiannRuntime.Unavailable.class,()->DiannRequest.parse(use,false));
        var modification=document();field(modification,"library_generation").put("variable_oxidation_M",false);assertThrows(DiannRuntime.Unavailable.class,()->DiannRequest.parse(modification,false));
        var format=document();runs(format).get(0).put("format","vendor-raw");assertThrows(DiannRuntime.Unavailable.class,()->DiannRequest.parse(format,false));
    }
    @Test void wholePopulationAndStrictScientificSchemaAreRequired()throws Exception {
        var extra=document();extra.put("automatic_repair",true);assertThrows(IOException.class,()->DiannRequest.parse(extra,false));
        var duplicate=document();runs(duplicate).get(1).put("path",runs(duplicate).get(0).get("path"));assertThrows(IOException.class,()->DiannRequest.parse(duplicate,false));
        var sameName=document();runs(sameName).get(1).put("path","/different/run-A.d");assertThrows(IOException.class,()->DiannRequest.parse(sameName,false));
        var single=document();single.put("raw_runs",List.of(runs(single).get(0)));assertThrows(IOException.class,()->DiannRequest.parse(single,false));
        var noRun=document();noRun.put("raw_runs",List.of());assertThrows(IOException.class,()->DiannRequest.parse(noRun,false));
        var enzyme=document();field(enzyme,"library_generation").put("enzyme","unspecific");assertThrows(IOException.class,()->DiannRequest.parse(enzyme,false));
        var mzml=document();runs(mzml).get(0).put("path","/absolute/run-A.mzML");runs(mzml).get(0).put("format","centroided-mzml");assertThrows(IOException.class,()->DiannRequest.parse(mzml,false));
        runs(mzml).get(0).put("centroided_spectra",true);assertEquals("centroided-mzml",DiannRequest.parse(mzml,false).runs().get(0).format());
    }
    @Test void finiteExplicitBoundsAndAggregateCapacitiesCannotBeBypassed()throws Exception {
        for(Object value:List.of(Double.NaN,Double.POSITIVE_INFINITY,.009,.051)){var doc=document();field(doc,"quantification").put("report_qvalue",value);assertThrows(IOException.class,()->DiannRequest.parse(doc,false));}
        var reversed=document();field(reversed,"library_generation").put("minimum_peptide_length",31);assertThrows(IOException.class,()->DiannRequest.parse(reversed,false));
        var threads=document();field(threads,"process").put("threads",1);assertThrows(IOException.class,()->DiannRequest.parse(threads,false));
        var space=document();field(space,"process").put("workspace_bytes",1024);assertThrows(IOException.class,()->DiannRequest.parse(space,false));
        var fractional=document();field(fractional,"quantification").put("threads",1.5);assertThrows(IOException.class,()->DiannRequest.parse(fractional,false));
    }
    @Test void versionSpecificCommandsUseFullCohortFreshQuantificationAndExplicitOptions()throws Exception {
        for(String edition:List.of("1.8","1.8.1","2.0.2")){
            var doc=document();doc.put("edition",edition);field(doc,"quantification").put("strategy","robust-lc");field(doc,"quantification").put("normalization","global");field(doc,"quantification").put("scan_window_radius",7);
            var spec=DiannRequest.parse(doc,false);var prediction=DiannWorkflow.predictionCommand("/new capture","/private original",spec);var quant=DiannWorkflow.quantificationCommand("/new capture","/private original",spec);
            assertTrue(prediction.containsAll(List.of("--predictor","--fasta-search","--gen-spec-lib","K*,R*,!*P","UniMod:4,57.021464,C","UniMod:35,15.994915,M","--met-excision")));
            assertEquals(2,Collections.frequency(quant,"--f"));assertTrue(quant.contains("/new capture/inputs/raw/0001/run-A.d"));assertTrue(quant.contains("/new capture/inputs/raw/0002/run-B.d"));
            assertTrue(quant.containsAll(List.of("--reanalyse","--gen-spec-lib","--global-norm","--peak-center","--window","7")));assertFalse(quant.contains("--use-quant"));
            assertEquals(edition.equals("2.0.2"),quant.contains("--direct-quant"));assertEquals(!edition.equals("2.0.2"),quant.contains("--smart-profiling"));
            assertTrue(quant.contains("/new capture/engine/generated-library.predicted.speclib"));
        }
    }
    Path savedInputs()throws Exception {
        Path root=Files.createDirectory(tmp.resolve(UUID.randomUUID().toString()));Files.createDirectories(root.resolve("inputs/raw"));
        Files.writeString(root.resolve("inputs/reference.fasta"),">sp|P1|ARTIFICIAL GN=GENE1\nMACDEFGHIK\n",StandardCharsets.UTF_8);
        var spec=DiannRequest.parse(document(),false);var originals=new LinkedHashMap<String,Object>();long bytes=0,nodes=0;
        for(var entry:DiannInputs.originals(spec).entrySet()){
            Path path=root.resolve(entry.getKey());if(!entry.getKey().endsWith(".fasta")){Files.createDirectories(path.resolve("kept-empty"));Files.write(path.resolve("analysis.tdf"),new byte[]{1,2});Files.write(path.resolve("analysis.tdf_bin"),new byte[]{3,4,5});}
            var tree=new TreeMap<String,Object>();try(var paths=Files.walk(path)){for(Path member:paths.toList()){
                String name=member.equals(path)?".":path.relativize(member).toString().replace('\\','/');nodes++;
                if(Files.isDirectory(member))tree.put(name,Map.of("kind","directory"));else{var pin=NextflowFiles.pin(member,10000,()->{});bytes+=pin.bytes();tree.put(name,Map.of("kind","file","pin",pin));}
            }}originals.put(entry.getKey(),Map.of("original",entry.getValue(),"tree",tree));
        }
        NextflowFiles.writeJson(root.resolve("source-inputs.json"),Map.of("schema_version",1,"originals",originals,"complete_original_bytes",bytes,"complete_original_nodes",nodes,"original_bytes_unchanged",true));return root;
    }
    @Test void savedInputsArePortableAndPreserveBytesAndEmptyDirectoryPopulation()throws Exception {
        var spec=DiannRequest.parse(document(),false);Path root=savedInputs();DiannInputs.verify(root,spec,false,()->{});
        Path extra=Files.createDirectory(root.resolve("inputs/raw/unrecorded-empty"));assertThrows(IOException.class,()->DiannInputs.verify(root,spec,false,()->{}));Files.delete(extra);
        Path empty=root.resolve("inputs/raw/0001/run-A.d/kept-empty");Files.delete(empty);assertThrows(IOException.class,()->DiannInputs.verify(root,spec,false,()->{}));Files.createDirectory(empty);
        Path binary=root.resolve("inputs/raw/0002/run-B.d/analysis.tdf_bin");Files.write(binary,new byte[]{3,4,6});assertThrows(IOException.class,()->DiannInputs.verify(root,spec,false,()->{}));
    }
    void logs(Path root,String content)throws IOException {Path directory=root.resolve("processes/quantification");Files.createDirectories(directory);Files.writeString(directory.resolve("process.stdout"),content);Files.writeString(directory.resolve("process.stderr"),"");}
    @Test void originalWarningsAreRetainedAndUnknownOrNumericalDiagnosticsFail()throws Exception {
        Path root=tmp.resolve("logs");var spec=DiannRequest.parse(document(),false);String banner=quantificationLog("1.8.1");
        String recommendation="WARNING: for the vast majority of timsTOF datasets it is better to manually fix both the MS1 and MS2 mass accuracies to 15 ppm";
        logs(root,banner+recommendation+"\n");assertEquals(List.of(recommendation),DiannWorkflow.diagnostics(root,spec,"quantification"));
        for(String line:List.of("WARNING: previously unseen condition","ERROR: process stopped","nonfinite output","model failed","training not converged")){Files.writeString(root.resolve("processes/quantification/process.stdout"),banner+line+"\n");assertThrows(IOException.class,()->DiannWorkflow.diagnostics(root,spec,"quantification"),line);}
        Files.writeString(root.resolve("processes/quantification/process.stdout"),"DIA-NN 2.0.2\n2 files will be processed\n");assertThrows(IOException.class,()->DiannWorkflow.diagnostics(root,spec,"quantification"));
    }
    static String quantificationLog(String edition){return "DIA-NN "+edition+" (artificial diagnostic control)\n2 files will be processed\nFirst pass: generating a spectral library from DIA data\n[0:01] File #1/2\n[0:02] File #2/2\nSecond pass: using the newly created spectral library to reanalyse the data\n[0:03] File #1/2\n[0:04] File #2/2\n";}
    @Test void completeMbrPassesAndKnownOriginalDispositionsRemainExplicit()throws Exception {
        var doc=document();doc.put("edition","2.0.2");var spec=DiannRequest.parse(doc,false);Path root=tmp.resolve("mbr-diagnostics");
        String warning="WARNING: 712 precursors without any fragments annotated were skipped\nWARNING: QuantUMS requires 6 or more runs for the optimisation of its hyperparameters to perform best.\n";
        logs(root,quantificationLog("2.0.2")+warning);var retained=DiannWorkflow.diagnostics(root,spec,"quantification");assertEquals(2,retained.size());String summary=DiannWorkflow.warningSummary(Map.of("original_warnings",retained));assertTrue(summary.contains("ORIGINAL LIBRARY OMISSIONS"));assertTrue(summary.contains("LIMITED QUANTUMS OPTIMIZATION"));
        for(String damaged:List.of(quantificationLog("2.0.2").replace("[0:04] File #2/2\n",""),quantificationLog("2.0.2").replace("[0:02] File #2/2","[0:02] File #1/2"))){Files.writeString(root.resolve("processes/quantification/process.stdout"),damaged);assertThrows(IOException.class,()->DiannWorkflow.diagnostics(root,spec,"quantification"));}
    }
    @Test void skipAndFailureRemoveCompletionAndExposeRequiredActions()throws Exception {
        for(boolean missing:List.of(false,true)){Path root=Files.createDirectory(tmp.resolve("failure-"+missing));Files.writeString(root.resolve("COMPLETED.json"),"{}");
            Throwable error=missing?new DiannRuntime.Unavailable("missing original prerequisite"):new IOException("changed original input");DiannWorkflow.failure(root,"ARTIFICIAL_CONTROL",error);
            assertFalse(Files.exists(root.resolve("COMPLETED.json")));assertTrue(Files.exists(root.resolve("INCOMPLETE.json")));String warnings=Files.readString(root.resolve("WARNINGS.md"));assertTrue(warnings.contains(missing?"SKIP3":"FAIL2"));assertTrue(warnings.contains("Required action:"));assertTrue(warnings.contains(error.getMessage()));
            assertThrows(IOException.class,()->DiannBundle.verify(root,false,tmp.resolve("unused-scratch-"+missing),()->{}));
        }
    }
    @Test void cliReportsPrerequisiteSkipAndRejectsAmbiguousOptions()throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(2,DiannToolsCli.run(new String[]{"--verify","one","--verify","two"},new PrintStream(out),new PrintStream(err)));assertTrue(err.toString(StandardCharsets.UTF_8).contains("FAIL2"));
        out.reset();err.reset();Path request=tmp.resolve("request.json");Files.writeString(request,NextflowJson.json(document()));
        assertEquals(3,DiannToolsCli.run(new String[]{"--request",request.toString(),"--home",tmp.resolve("absent-runtime").toString(),"--out",tmp.resolve("must-not-exist").toString()},new PrintStream(out),new PrintStream(err)));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertTrue(err.toString(StandardCharsets.UTF_8).contains("Required action:"));assertFalse(Files.exists(tmp.resolve("must-not-exist")));
    }
}
