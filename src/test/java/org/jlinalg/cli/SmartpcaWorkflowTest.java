/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;
import static org.junit.jupiter.api.Assertions.*;

final class SmartpcaWorkflowTest {
    @TempDir Path temporary;
    private Map<String,Object> request(){return map("schema_version",1,"mode","RUN","engine","native","profile",SmartpcaWorkflowContracts.PROFILE,"operation","PCA","out","capture",
        "inputs",map("genotypes","input.geno","markers","input.snp","samples","input.ind","reference_populations",List.of("R1","R2")),"options",map("numoutevec",2,"numoutlieriter",0,"numoutlierevec",0));}
    private Map<String,Object> canonical(Map<String,Object> raw)throws IOException{return SmartpcaWorkflowContracts.validate(raw,SmartpcaWorkflowContracts.address(temporary),SmartpcaWorkflowContracts.style());}
    private Path input()throws IOException {
        Path root=Files.createDirectory(temporary.resolve("input-"+java.util.UUID.randomUUID()));
        for(String name:List.of("inputs","passes","coordinates"))Files.createDirectory(root.resolve(name));
        Files.writeString(root.resolve("inputs/samples.ind"),"NA U R1\nNaN U R1\nInf U R2\nx U R2\ny U TARGET\nz U TARGET\n");
        var markers=new StringBuilder();var genotypes=new StringBuilder();var random=new Random(719328);
        for(int row=0;row<32;row++){markers.append("marker").append(row).append(" 1 0 ").append(row+1).append(" A G\n");for(int col=0;col<6;col++)genotypes.append(random.nextInt(3));genotypes.append('\n');}
        Files.writeString(root.resolve("inputs/markers.snp"),markers);Files.writeString(root.resolve("inputs/genotypes.geno"),genotypes);return root;
    }
    @Test void canonicalDefaultsPreserveTheSelectedSourceAndRequireAnExplicitBasis()throws Exception {
        var raw=request();raw.remove("options");var value=canonical(raw);var options=object(value.get("options"));
        assertEquals(10,number(options,"numoutevec"));assertEquals(5,number(options,"numoutlieriter"));assertEquals(10,number(options,"numoutlierevec"));
        assertEquals(6,decimal(options.get("outliersigmathresh")).doubleValue());assertEquals(true,options.get("usenorm"));assertEquals(false,options.get("lsqproject"));assertEquals(false,options.get("shrinkmode"));
        var inputs=object(raw.get("inputs"));inputs.remove("reference_populations");raw.put("inputs",inputs);assertThrows(IOException.class,()->canonical(raw));
        inputs.put("reference_populations",null);raw.put("inputs",inputs);assertNull(object(canonical(raw).get("inputs")).get("reference_populations"));
    }
    @Test void unknownSourceParametersAndImplicitBooleanConversionsAreRefused()throws Exception {
        for(var change:List.of(map("killr2",true),map("hiprec",true),map("fstonly",true),map("lsqproject","YES"),map("outliermode",3),map("numoutevec",0),map("outliersigmathresh",0))) {
            var raw=request();var options=object(raw.get("options"));options.putAll(change);raw.put("options",options);assertThrows(IOException.class,()->canonical(raw),change.toString());
        }
    }
    @Test void recordedPathSemanticsDoNotDependOnTheVerifierOperatingSystem()throws Exception {
        var win=SmartpcaWorkflowContracts.validate(request(),"D:/cohort/project","WINDOWS");assertEquals("D:/cohort/project/input.geno",object(win.get("inputs")).get("genotypes"));
        var posix=SmartpcaWorkflowContracts.validate(request(),"/cohort/project","POSIX");assertEquals("/cohort/project/capture",posix.get("out"));
        assertThrows(IOException.class,()->SmartpcaWorkflowContracts.resolve("D:/cohort","D:relative","WINDOWS"));
        assertThrows(IOException.class,()->SmartpcaWorkflowContracts.resolve("/cohort","../../escape","POSIX"));
    }
    @Test void literalMissingLookingIdsAreNeitherParsedAsNumbersNorOmitted()throws Exception {
        var data=SmartpcaWorkflowData.read(input(),canonical(request()));assertEquals(List.of("NA","NaN","Inf","x","y","z"),data.samples().stream().map(SmartpcaWorkflowData.Sample::id).toList());
        assertArrayEquals(new int[]{0,1,2,3},data.basisIndices());assertEquals(32,data.markers().size());assertEquals(false,data.summary().get("genotypes_recoded"));
    }
    @Test void duplicateIdsUnknownGroupsAndReservedIgnoreRowsCannotSilentlyChangeThePopulation()throws Exception {
        Path root=input();String original=Files.readString(root.resolve("inputs/samples.ind"));
        Files.writeString(root.resolve("inputs/samples.ind"),original.replace("NaN U R1","NA U R1"));assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(request())));
        Files.writeString(root.resolve("inputs/samples.ind"),original.replace("z U TARGET","z U Ignore"));assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(request())));
        Files.writeString(root.resolve("inputs/samples.ind"),original);var raw=request();var inputs=object(raw.get("inputs"));inputs.put("reference_populations",List.of("ABSENT"));raw.put("inputs",inputs);assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(raw)));
    }
    @Test void completeGenotypeWidthsCountsEncodingAndHardCallsAreChecked()throws Exception {
        Path root=input(),file=root.resolve("inputs/genotypes.geno");String original=Files.readString(file);
        for(String changed:List.of(original.substring(1),original+"000000\n",original.substring(original.indexOf('\n')+1),"3"+original.substring(1),"0.1"+original.substring(3),"\n"+original)) {
            Files.writeString(file,changed);assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(request())));
        }
        Files.write(file,new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(request())));
    }
    @Test void markerIdentityOrderAndNumericChromosomeConventionAreExplicit()throws Exception {
        Path root=input(),file=root.resolve("inputs/markers.snp");String original=Files.readString(file);
        for(String changed:List.of(original.replace("marker1 1 0 2","marker0 1 0 2"),original.replace("marker1 1 0 2","marker1 1 0 0"),original.replace("marker0 1 0 1","marker0 chr1 0 1"),original.replace("marker0 1 0 1","marker0 1 NaN 1"))) {
            Files.writeString(file,changed);assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(request())));
        }
    }
    @Test void nonAutosomalCallsAreRetainedAndValidatedWithExplicitExclusionMetadata()throws Exception {
        Path root=input();Files.writeString(root.resolve("inputs/markers.snp"),"extra 23 0 99 A G\n",java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(root.resolve("inputs/genotypes.geno"),"999999\n",java.nio.file.StandardOpenOption.APPEND);
        var data=SmartpcaWorkflowData.read(root,canonical(request()));assertEquals(33,data.markers().size());assertEquals(32,data.calls().length);assertEquals(1,number(data.summary(),"excluded_non_autosomal_markers"));
        String genotypes=Files.readString(root.resolve("inputs/genotypes.geno"));Files.writeString(root.resolve("inputs/genotypes.geno"),genotypes.substring(0,genotypes.length()-7)+"999993\n");assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(request())));
    }
    @Test void fullInputAndNumericalWorkCapsRefuseInsteadOfReducingTheDataset()throws Exception {
        Path root=input();
        for(var limit:List.of(map("maximum_samples",5),map("maximum_markers",31),map("maximum_matrix_cells",191),map("maximum_input_bytes",1),map("maximum_total_input_bytes",3),map("maximum_line_bytes",3),map("maximum_retained_bytes",1))) {
            var raw=request();raw.put("limits",limit);assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(raw)),limit.toString());
        }
        var raw=request();raw.put("limits",map("maximum_estimated_work",1));assertThrows(IOException.class,()->SmartpcaWorkflowEvaluation.evaluate(root,canonical(raw),null));
    }
    @Test void aSinglePopulationShrinkageBranchIsRefusedRatherThanInventingGroups()throws Exception {
        Path root=input();var raw=request();var inputs=object(raw.get("inputs"));inputs.put("reference_populations",List.of("R1"));raw.put("inputs",inputs);
        var options=object(raw.get("options"));options.put("shrinkmode",true);raw.put("options",options);assertThrows(IOException.class,()->SmartpcaWorkflowData.read(root,canonical(raw)));
    }
    @Test void savedTablesChunksAndPassesMustAgreeWithTheFreshNumericalCalculation()throws Exception {
        Path root=input();var raw=canonical(request());var budget=new SmartpcaWorkflowTables.Budget(10_000_000);var value=SmartpcaWorkflowEvaluation.evaluate(root,raw,budget);
        SmartpcaWorkflowTables.writeOutputs(root,value,budget);SmartpcaWorkflowAudit.checkOutputs(root,value);
        String table=Files.readString(root.resolve("coordinates.tsv"));assertTrue(table.contains("\tNaN\tR1\t"));
        Files.writeString(root.resolve("coordinates.tsv"),table.replace("\tNaN\tR1\t","\tNAN\tR1\t"));assertThrows(IOException.class,()->SmartpcaWorkflowAudit.checkOutputs(root,value));
        Files.writeString(root.resolve("coordinates.tsv"),table+"unexpected\n");assertThrows(IOException.class,()->SmartpcaWorkflowAudit.checkOutputs(root,value));
        Files.writeString(root.resolve("coordinates.tsv"),table);var chunk=read(root.resolve("coordinates/000000.json"));var coordinates=new ArrayList<Object>(list(chunk.get("coordinates")));var first=new ArrayList<Object>(list(coordinates.get(0)));first.set(0,decimal(first.get(0)).doubleValue()+.001);coordinates.set(0,first);chunk.put("coordinates",coordinates);
        Files.write(root.resolve("coordinates/000000.json"),json(chunk));assertThrows(IOException.class,()->SmartpcaWorkflowAudit.checkOutputs(root,value));
    }
    @Test void comparisonKeepsTinyValuesZeroIntegersAndLiteralSpecialIdsDistinct()throws Exception {
        SmartpcaWorkflowEvaluation.compare(new GprofilerJson.Numeric("1.0000000000001e-200"),1e-200,"small");
        assertThrows(IOException.class,()->SmartpcaWorkflowEvaluation.compare(new GprofilerJson.Numeric("2e-200"),1e-200,"small"));
        assertThrows(IOException.class,()->SmartpcaWorkflowEvaluation.compare(new GprofilerJson.Numeric("1e-300"),0.,"zero"));
        assertThrows(IOException.class,()->SmartpcaWorkflowEvaluation.compare(new GprofilerJson.Numeric("1.0000000000001"),1,"integer"));
        assertThrows(IOException.class,()->SmartpcaWorkflowEvaluation.compare("Inf","INF","id"));
    }
    @Test void missingCapturesArePrerequisitesButExistingUnsealedEvidenceIsAnIntegrityFailure()throws Exception {
        assertThrows(SmartpcaWorkflowContracts.Unavailable.class,()->SmartpcaWorkflowAudit.verify(temporary.resolve("missing")));
        Path root=Files.createDirectory(temporary.resolve("incomplete"));var failure=assertThrows(IOException.class,()->SmartpcaWorkflowAudit.verify(root));assertFalse(failure instanceof SmartpcaWorkflowContracts.Unavailable);
        try(var nodes=Files.list(root)){assertEquals(0,nodes.count());}
    }
    @Test void numericalOutputBudgetRefusesPartialWritesAndReportsNoCompletion()throws Exception {
        Path root=input();var budget=new SmartpcaWorkflowTables.Budget(2);assertThrows(IOException.class,()->SmartpcaWorkflowEvaluation.evaluate(root,canonical(request()),budget));assertFalse(Files.exists(root.resolve("COMPLETED.json")));
        var receipt=read(root.resolve("compute-process.json"));assertEquals(true,receipt.get("owned_thread_stopped"));assertEquals(false,receipt.get("calculation_returned"));
    }
    @Test void allEighteenOriginalFileSetsBindExactlyToTheirSavedMatrixAndBasis()throws Exception {
        var entries=new LinkedHashMap<String,byte[]>();
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/genetics/smartpca-original-analysis-reference.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(!entry.isDirectory())entries.put(entry.getName(),zip.readAllBytes());}
        }
        int checked=0;
        for(var entry:entries.entrySet())if(entry.getKey().endsWith("/reference.json")) {
            String folder=entry.getKey().substring(0,entry.getKey().lastIndexOf('/'));
            Path root=Files.createDirectory(temporary.resolve(folder));Files.createDirectory(root.resolve("inputs"));
            for(var pair:Map.of("input.geno","genotypes.geno","input.snp","markers.snp","input.ind","samples.ind").entrySet())Files.write(root.resolve("inputs").resolve(pair.getValue()),entries.get(folder+"/"+pair.getKey()));
            var reference=object(GprofilerJson.read(entry.getValue()));var raw=request();var inputs=object(raw.get("inputs"));
            byte[] poplist=entries.get(folder+"/basis.poplist");inputs.put("reference_populations",poplist==null?null:List.of(new String(poplist,StandardCharsets.UTF_8).strip().split("\\s+")));raw.put("inputs",inputs);
            var data=SmartpcaWorkflowData.read(root,canonical(raw));var original=object(reference.get("input"));var rows=list(original.get("genotypes"));assertEquals(rows.size(),data.calls().length,folder);
            for(int s=0;s<rows.size();s++){var cells=list(rows.get(s));assertEquals(cells.size(),data.calls()[s].length);for(int i=0;i<cells.size();i++)assertEquals(integer(cells.get(i),0,9),data.calls()[s][i],folder);}
            assertArrayEquals(list(original.get("basis_indices")).stream().mapToInt(x->{try{return Math.toIntExact(integer(x,0,100_000));}catch(IOException bad){throw new java.io.UncheckedIOException(bad);}}).toArray(),data.basisIndices(),folder);checked++;
        }
        assertEquals(18,checked);
    }
}
