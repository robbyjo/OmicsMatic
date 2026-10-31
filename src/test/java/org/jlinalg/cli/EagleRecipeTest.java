/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class EagleRecipeTest {
    @TempDir Path temporary;
    @Test void cohortAndReferenceRecipesRetainExplicitModesAndVersionedCapacities()throws Exception {
        for(boolean reference:new boolean[]{false,true}){var spec=EagleRecipe.read(EagleTestRecipes.write(temporary,EagleTestRecipes.source(temporary,reference)));assertEquals(reference,spec.referenceSpec()!=null);assertEquals(100000,spec.cohortSpec()==null?spec.referenceSpec().vcfLimits().text().samples():spec.cohortSpec().vcfLimits().text().samples());assertEquals(1073741824L,spec.processLimits().addressSpaceBytes());assertEquals(4294967296L,spec.processLimits().sampledRssBytes());spec.verify();}
    }
    @Test void wholeUnsigned32SeedIsRetainedAndFractionsAreRefused()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"parameters").put("seed",4294967295L);assertEquals(4294967295L,EagleRecipe.read(EagleTestRecipes.write(temporary,source)).parameters().seed());
        EagleTestRecipes.section(source,"parameters").put("seed",1.5);assertThrows(IOException.class,()->EagleRecipe.read(EagleTestRecipes.write(temporary,source)));
    }
    @Test void unknownEngineFlagsAndTyposCannotReachOriginalProcess()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"parameters").put("extra_args","--outPrefix=/elsewhere");assertThrows(IOException.class,()->EagleRecipe.read(EagleTestRecipes.write(temporary,source)));
    }
    @Test void historicalEditionIsUnavailableAndDeclaredBcfFormatIsRetained()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);source.put("profile","linux-x86_64-eagle-2.3.5");assertThrows(EagleRecipe.UnavailableCapability.class,()->EagleRecipe.read(EagleTestRecipes.write(temporary,source)));
        source.put("profile",EagleRecipe.PROFILE);EagleTestRecipes.section(source,"target").put("format","bcf");assertEquals("bcf",EagleRecipe.read(EagleTestRecipes.write(temporary,source)).inputSources().get("target").format());
    }
    @Test void referenceAndCohortAlgorithmsCannotBeSilentlyReinterpreted()throws Exception {
        var source=EagleTestRecipes.source(temporary,true);EagleTestRecipes.section(source,"parameters").put("algorithm","COHORT_PBWT_ONLY");assertThrows(IOException.class,()->EagleRecipe.read(EagleTestRecipes.write(temporary,source)));
    }
    @Test void scientificPoliciesAndPosSelectionMustBeExplicit()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"policies").remove("missing");Path missing=EagleTestRecipes.write(temporary,source);assertThrows(IOException.class,()->EagleRecipe.read(missing));
        source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"region").put("selection","indexed-overlap");Path recipe=EagleTestRecipes.write(temporary,source);assertThrows(IOException.class,()->EagleRecipe.read(recipe));
    }
    @Test void resourceRootEscapeAndUnknownPresetAreRefused()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"target").put("path","../elsewhere.vcf");Path path=EagleTestRecipes.write(temporary,source);assertThrows(IOException.class,()->EagleRecipe.read(path));
        source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"limits").put("preset","future-defaults");Path invalid=EagleTestRecipes.write(temporary,source);assertThrows(IOException.class,()->EagleRecipe.read(invalid));
    }
    @Test void changedRecipeAndDuplicateJsonFieldsAreIntegrityFailures()throws Exception {
        var spec=EagleRecipe.read(EagleTestRecipes.write(temporary,EagleTestRecipes.source(temporary,false)));Files.writeString(spec.recipe(),"{}\n");assertThrows(IOException.class,spec::verify);
        Files.writeString(spec.recipe(),"{\"schema_version\":1,\"schema_version\":1}");assertThrows(IOException.class,()->EagleRecipe.read(spec.recipe()));
    }
    @Test void impossibleRetainedAndThreadBudgetsAreRefusedBeforePreparation()throws Exception {
        var source=EagleTestRecipes.source(temporary,true);EagleTestRecipes.section(source,"limits").put("retained_bytes",1);Path path=EagleTestRecipes.write(temporary,source);assertThrows(IOException.class,()->EagleRecipe.read(path));
        source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"parameters").put("threads",8);EagleTestRecipes.section(source,"process_limits").put("threads",4);Path invalid=EagleTestRecipes.write(temporary,source);assertThrows(IOException.class,()->EagleRecipe.read(invalid));
    }
}
