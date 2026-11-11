/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MafftRecipeTest {
    @TempDir Path root;
    Map<String,Object> request(){var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("profile","linux-x86_64-mafft-7.526");r.put("operation","DE_NOVO");r.put("input","input.fasta");r.put("alphabet","NUCLEOTIDE");r.put("symbols","BIOLOGICAL");r.put("headers","WHOLE_HEADER");r.put("input_gaps","REMOVE");r.put("strategy","AUTO");r.put("scoring",Map.of("source_defaults",true));r.put("parameters",Map.of("threads",1,"output_order","INPUT","keep_length",false));return r;}
    Map<String,Object> addition(boolean keep){var r=request();r.put("operation","ADD");r.put("additions","added.fasta");r.put("input_gaps","PRESERVE_ALIGNMENT");r.put("addition_gaps","REMOVE");r.put("parameters",Map.of("threads",1,"output_order","INPUT","keep_length",keep));return r;}
    MafftRecipe.Spec read(Map<String,Object> r)throws IOException {Path source=root.resolve("recipe-"+UUID.randomUUID()+".json");Files.writeString(source,MafftJson.json(r),StandardCharsets.UTF_8);return MafftRecipe.read(source);}
    @Test void completeChoicesAreExplicitAndPathsResolveRelativeToRecipe()throws Exception {var s=read(request());assertEquals(root.resolve("input.fasta"),s.input());assertEquals(MafftRecipe.Operation.DE_NOVO,s.operation());assertEquals(1,s.parameters().threads());assertTrue(s.scoring().sourceDefaults());assertTrue(s.limits().fasta().cells()>0);s.verify();}
    @Test void majorOnlyAndUnlistedEditionsDoNotChooseAnInstalledFallback(){for(String version:List.of("7","7.525","8")){var r=request();r.put("profile","linux-x86_64-mafft-"+version);assertThrows(MafftRuntime.Unavailable.class,()->read(r));}}
    @Test void unknownAlphabetIsNotGuessedFromSequenceLetters(){var r=request();r.put("alphabet","AUTO");assertThrows(IOException.class,()->read(r));}
    @Test void unknownSettingsAreNotDiscarded(){var r=request();r.put("unreviewed_option",true);assertThrows(IOException.class,()->read(r));}
    @Test void explicitUnsupportedCapabilitiesRequireSkippedAnalysis(){for(String key:List.of("batch","rna_structure","dash","custom_matrix","codon_translation","adjust_direction","add_fragments","add_profile")){var r=request();r.put(key,Map.of("requested",true));assertThrows(MafftRuntime.Unavailable.class,()->read(r));}}
    @Test void sourceDefaultScoringCannotHideExplicitGapChanges(){var r=request();r.put("scoring",Map.of("source_defaults",true,"gap_open",1.53));assertThrows(IOException.class,()->read(r));}
    @Test void nucleotideScoringCannotSilentlyIgnoreAProteinMatrix(){var r=request();r.put("scoring",Map.of("source_defaults",false,"blosum",62,"gap_open",1.53,"offset",0));assertThrows(IOException.class,()->read(r));}
    @Test void proteinScoringRequiresAnExplicitSupportedBlosumMatrix()throws Exception {
        for(int matrix:List.of(30,45,62,80)){var r=request();r.put("alphabet","PROTEIN");r.put("scoring",Map.of("source_defaults",false,"blosum",matrix,"gap_open",1.53,"offset",0));assertEquals(matrix,read(r).scoring().blosum());}
        var r=request();r.put("alphabet","PROTEIN");r.put("scoring",Map.of("source_defaults",false,"gap_open",1.53,"offset",0));assertThrows(IOException.class,()->read(r));
    }
    @Test void iterativeStrategiesRequireAnExplicitMaximum()throws Exception {for(String strategy:List.of("LOCAL_PAIR","GLOBAL_PAIR","GENAFPAIR","FFT_NS_I")){var r=request();r.put("strategy",strategy);assertThrows(IOException.class,()->read(r));r.put("maximum_iterations",100);assertEquals(100,read(r).maximumIterations());}}
    @Test void automaticStrategyCannotDiscardAnExplicitIterationMaximum(){var r=request();r.put("maximum_iterations",100);assertThrows(IOException.class,()->read(r));}
    @Test void refinementThreadZeroDoesNotDisableRefinementIterations()throws Exception {var r=request();r.put("strategy","GLOBAL_PAIR");r.put("maximum_iterations",100);r.put("parameters",Map.of("threads",2,"iteration_threads",0,"output_order","SOURCE","keep_length",false));var s=read(r);assertEquals(100,s.maximumIterations());assertEquals(0,s.parameters().iterationThreads());assertEquals(2,s.parameters().threads());}
    @Test void threadCapacityAndStageSettingsCannotBeExceeded(){var first=request();first.put("parameters",Map.of("threads",1,"iteration_threads",2,"output_order","INPUT","keep_length",false));assertThrows(IOException.class,()->read(first));var second=request();second.put("parameters",Map.of("threads",0,"output_order","INPUT","keep_length",false));assertThrows(IOException.class,()->read(second));}
    @Test void deNovoCannotPretendToPreserveAnExistingAlignment(){var r=request();r.put("input_gaps","PRESERVE_ALIGNMENT");assertThrows(IOException.class,()->read(r));}
    @Test void addRequiresBothACompleteBackboneAndAnExplicitAddedGapPolicy(){var first=addition(false);first.remove("addition_gaps");assertThrows(IOException.class,()->read(first));var second=addition(false);second.put("input_gaps","REMOVE");assertThrows(IOException.class,()->read(second));}
    @Test void deNovoCannotDiscardARequestedKeepLengthPolicy(){var r=request();r.put("parameters",Map.of("threads",1,"output_order","INPUT","keep_length",true));assertThrows(IOException.class,()->read(r));}
    @Test void iterativeKeepLengthIsRefusedInsteadOfReducingTheRequestedMaximum(){var r=addition(true);r.put("strategy","LOCAL_PAIR");r.put("maximum_iterations",1000);assertThrows(MafftRuntime.Unavailable.class,()->read(r));}
    @Test void oldEditionCannotSilentlyDowngradeIterativeAddition(){var r=addition(false);r.put("profile","linux-x86_64-mafft-7.313");r.put("strategy","GLOBAL_PAIR");r.put("maximum_iterations",100);assertThrows(MafftRuntime.Unavailable.class,()->read(r));}
    @Test void deepCapacityOverridesRemainExactAndValidated()throws Exception {var r=request();r.put("limits",Map.of("inputBytes",4096,"fasta",Map.of("cells",32),"process",Map.of("cpuSeconds",2),"diagnosticWarnings",20));var s=read(r);assertEquals(4096,s.limits().inputBytes());assertEquals(32,s.limits().fasta().cells());assertEquals(2,s.limits().process().cpuSeconds());assertEquals(20,s.limits().diagnosticWarnings());}
    @Test void invalidOrUnknownCapacitiesCannotFallBackToDefaults(){var first=request();first.put("limits",Map.of("process",Map.of("cpuSeconds",0)));assertThrows(IOException.class,()->read(first));var second=request();second.put("limits",Map.of("fasta",Map.of("sample_first_rows",5)));assertThrows(IOException.class,()->read(second));}
    @Test void duplicateJsonKeysAreNotSilentlyOverwritten()throws Exception {Path p=root.resolve("duplicate.json");Files.writeString(p,"{\"schema_version\":1,\"profile\":\"7.313\",\"profile\":\"7.526\"}");assertThrows(IOException.class,()->MafftRecipe.read(p));}
    @Test void originalRecipeMutationInvalidatesTheSpec()throws Exception {var s=read(request());Files.writeString(s.source(),"{}");assertThrows(IOException.class,s::verify);}
}
