/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.phylogeny.PhylogeneticAlignment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IqtreeRecipeTest {
    @TempDir Path temporary;
    private Map<String,Object> base() {
        return new HashMap<>(Map.of("schema_version",1,"profile","linux-x86_64-iqtree-2.3.6","operation","INFER",
                "alignment",new HashMap<>(Map.of("path","aligned.fasta","format","FASTA","kind","DNA","header_policy","WHOLE_HEADER")),
                "model",new HashMap<>(Map.of("selection","FIXED","name","GTR+R10")),
                "support",new HashMap<>(Map.of("bootstrap","NONE","replicates",0,"sh_alrt_replicates",0,"bnni",false,"write_branch_lengths",false)),
                "parameters",new HashMap<>(Map.of("seed",61129,"threads",1,"identical_sequences","KEEP","safe_mode",false,"outgroup",List.of()))));
    }
    @SuppressWarnings("unchecked") private Map<String,Object> part(Map<String,Object> root,String key){return (Map<String,Object>)root.get(key);}
    private IqtreeRecipe.Spec read(Map<String,Object> root)throws IOException {
        Path path=temporary.resolve("recipe.json");Files.writeString(path,IqtreeJson.json(root));return IqtreeRecipe.read(path);
    }
    private List<String> arguments(IqtreeRecipe.Spec spec)throws IOException {
        return spec.arguments(temporary.resolve("prepared.fasta"),spec.tree()==null?null:temporary.resolve("prepared.nwk"),temporary.resolve("result"),
                Map.of("one, original α","T000000001","two","T000000002","three","T000000003","four","T000000004"));
    }
    @Test void fullRecipeRetainsLiteralSettingsAndBindsItsOriginalFile() throws Exception {
        var spec=read(base());assertEquals(temporary.resolve("aligned.fasta"),spec.alignment().path());
        assertEquals("GTR+R10",spec.model().name());assertEquals(IqtreeRecipe.IdenticalSequences.KEEP,spec.parameters().identicalSequences());
        var args=arguments(spec);assertTrue(args.contains("-keep-ident"));assertTrue(args.contains("-wsl"));assertFalse(args.contains("-redo"));
        spec.verify();Files.writeString(spec.source(),"{}");assertThrows(IOException.class,spec::verify);
    }
    @Test void exactProfileAndSchemaAreNeverSilentlySubstituted() throws Exception {
        for(String version:List.of("1.6.12","2.1.2","2.1.4-beta","2.2.0.3","2.3.1","2.3.5","2.3.6","2.4.0")) {
            var root=base();root.put("profile","linux-x86_64-iqtree-"+version);assertEquals(root.get("profile"),read(root).profile());
        }
        var root=base();root.put("profile","linux-x86_64-iqtree-1.6.619");assertThrows(IqtreeRuntime.Unavailable.class,()->read(root));
        root.put("profile","linux-x86_64-iqtree-2.3.6");root.put("schema_version",2);assertThrows(IqtreeRuntime.Unavailable.class,()->read(root));
    }
    @Test void unknownFieldsArbitraryProgramsDuplicateKeysAndNonUtf8AreRefused() throws Exception {
        for(String key:List.of("program","extra_flags","class","partitions")) {var root=base();root.put(key,"/bin/sh");assertThrows(IOException.class,()->read(root));}
        var root=base();part(root,"parameters").put("-AUTO",true);assertThrows(IOException.class,()->read(root));
        Path file=temporary.resolve("duplicate.json");Files.writeString(file,"{\"schema_version\":1,\"schema_version\":1}");assertThrows(IOException.class,()->IqtreeRecipe.read(file));
        Files.write(file,new byte[]{(byte)0xc0,(byte)0xaf});assertThrows(IOException.class,()->IqtreeRecipe.read(file));
    }
    @Test void formatsAndHeaderLayoutsAreExplicitAndNeverGuessed() throws Exception {
        var missing=base();part(missing,"alignment").remove("header_policy");assertThrows(IOException.class,()->read(missing));
        var wrong=base();part(wrong,"alignment").put("layout","SEQUENTIAL");assertThrows(IOException.class,()->read(wrong));
        var root=base();var a=part(root,"alignment");a.remove("header_policy");a.putAll(Map.of("format","PHYLIP","layout","INTERLEAVED","name_field","FIXED_TEN_COLUMNS","named_continuations",true));
        assertEquals(PhylogeneticAlignment.NameField.FIXED_TEN_COLUMNS,read(root).alignment().nameField());
        a.put("layout","SEQUENTIAL");var sequential=root;assertThrows(IOException.class,()->read(sequential));
    }
    @Test void builtinModelValidationPreservesReportedModelUncertaintyAndBlocksFileModels() throws Exception {
        for(String model:List.of("JC","GTR+R10","GTR+F+I+R10","HKY+FO+G4","012345+G"))IqtreeRecipe.validateModel(model,PhylogeneticAlignment.Kind.DNA);
        IqtreeRecipe.validateModel("LG+F+R10",PhylogeneticAlignment.Kind.AMINO_ACID);IqtreeRecipe.validateModel("MG+F1X4",PhylogeneticAlignment.Kind.CODON_STANDARD);
        for(String bad:List.of("GTR+F3x4+R6","/tmp/custom","../LG","GTR+G4+R10","GTR+F+FQ","GTR+R1","GTR+","GTR{1,2}"))
            assertThrows(IOException.class,()->IqtreeRecipe.validateModel(bad,PhylogeneticAlignment.Kind.DNA),bad);
        assertThrows(IOException.class,()->IqtreeRecipe.validateModel("GTR+F3x4+R6",PhylogeneticAlignment.Kind.CODON_STANDARD));
        assertThrows(IOException.class,()->IqtreeRecipe.validateModel("VT+F+R5",PhylogeneticAlignment.Kind.DNA));
    }
    @Test void modelFinderKeepsUpstreamDefaultCandidatesUnlessExplicitlyRestricted() throws Exception {
        var root=base();root.put("model",new HashMap<>(Map.of("selection","MODEL_FINDER","criterion","BIC")));
        var args=arguments(read(root));assertTrue(args.contains("MFP"));assertFalse(args.contains("-mset"));
        part(root,"model").put("candidates",List.of("JC","GTR"));args=arguments(read(root));assertEquals("JC,GTR",args.get(args.indexOf("-mset")+1));
        part(root,"model").put("candidates",List.of("JC","JC"));assertThrows(IOException.class,()->read(root));
    }
    @Test void standardAndUltrafastBootstrapUseDistinctPinnedVersionOptions() throws Exception {
        var root=base();var support=part(root,"support");support.putAll(Map.of("bootstrap","ULTRAFAST","replicates",1000,"sh_alrt_replicates",1000,"bnni",true,"write_branch_lengths",true));
        var args=arguments(read(root));assertTrue(args.containsAll(List.of("-B","-alrt","-bnni","-wbtl")));assertFalse(args.contains("-b"));
        root.put("profile","linux-x86_64-iqtree-1.6.12");args=arguments(read(root));assertTrue(args.contains("-bb"));assertFalse(args.contains("-B"));
        support.putAll(Map.of("bootstrap","STANDARD","replicates",100,"bnni",false));args=arguments(read(root));assertTrue(args.contains("-b"));assertFalse(args.contains("-bb"));
        support.put("bnni",true);assertThrows(IOException.class,()->read(root));
    }
    @Test void invalidSupportCountsAndFloatingPointIntegersAreNotCoerced() throws Exception {
        for(Map<String,Object> change:List.<Map<String,Object>>of(Map.of("bootstrap","ULTRAFAST","replicates",999),Map.of("replicates",1000),Map.of("sh_alrt_replicates",1),Map.of("replicates",1000.0))) {
            var root=base();part(root,"support").putAll(change);assertThrows(IOException.class,()->read(root));
        }
        var root=base();part(root,"parameters").put("threads",1.0);assertThrows(IOException.class,()->read(root));
    }
    @Test void exactOutgroupNamesAreMappedBeforeConstructingAnUpstreamArgument() throws Exception {
        var root=base();part(root,"parameters").put("outgroup",List.of("one, original α"));var args=arguments(read(root));
        assertEquals("T000000001",args.get(args.indexOf("-o")+1));
        part(root,"parameters").put("outgroup",List.of("absent"));var spec=read(root);assertThrows(IOException.class,()->arguments(spec));
        part(root,"parameters").put("outgroup",List.of("two","two"));assertThrows(IOException.class,()->read(root));
    }
    @Test void fixedTreeEvaluationAndStartingTreeInferenceKeepDifferentRoles() throws Exception {
        var root=base();root.put("operation","EVALUATE");root.put("tree",new HashMap<>(Map.of("path","supplied.nwk","mode","FIX_TOPOLOGY_AND_LENGTHS")));
        var args=arguments(read(root));assertTrue(args.containsAll(List.of("-te","-blfix")));assertFalse(args.contains("-t"));
        root.put("operation","INFER");assertThrows(IOException.class,()->read(root));
        part(root,"tree").put("mode","STARTING");args=arguments(read(root));assertTrue(args.contains("-t"));assertFalse(args.contains("-blfix"));
    }
    @Test void completeCapacitiesAreClosedAndEnforcedBeforeExecution() throws Exception {
        var root=base();root.put("limits",Map.of("inputBytes",1000,"alignment",Map.of("taxa",10),"process",Map.of("threads",1)));
        assertEquals(1000,read(root).limits().inputBytes());assertEquals(10,read(root).limits().alignment().taxa());
        part(root,"parameters").put("threads",2);assertThrows(IOException.class,()->read(root));
        root.put("limits",Map.of("process",Map.of("executable","/bin/sh")));assertThrows(IOException.class,()->read(root));
    }
    @Test void completeFileIdentitiesDetectByteMetadataAndCapacityChanges() throws Exception {
        Path path=temporary.toRealPath().resolve("input");Files.writeString(path,"original");var old=IqtreeFiles.identity(path,8,()->{});
        assertEquals(8,old.bytes());assertThrows(IOException.class,()->IqtreeFiles.identity(path,7,()->{}));
        Files.setLastModifiedTime(path,FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis()+2000));
        assertNotEquals(old,IqtreeFiles.identity(path,8,()->{}));Files.writeString(path,"changed!");assertNotEquals(old.sha256(),IqtreeFiles.identity(path,8,()->{}).sha256());
        assertThrows(IllegalStateException.class,()->IqtreeFiles.identity(path,8,()->{throw new IllegalStateException("cancelled");}));
    }
    @Test void linkedLinuxInputsCannotBecomeAdmittedFileIdentities() throws Exception {
        assumeTrue(System.getProperty("os.name").equals("Linux"),"Linux hard-link/parent-link control");
        Path root=temporary.toRealPath();Path input=root.resolve("input");Files.writeString(input,"original");
        Files.createLink(root.resolve("hard"),input);assertThrows(IOException.class,()->IqtreeFiles.identity(input,8,()->{}));
        Files.delete(root.resolve("hard"));Files.createSymbolicLink(root.resolve("soft"),input);assertThrows(IOException.class,()->IqtreeFiles.identity(root.resolve("soft"),8,()->{}));
    }
}
