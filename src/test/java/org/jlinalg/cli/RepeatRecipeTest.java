/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
final class RepeatRecipeTest {
    @TempDir Path root;
    private RepeatRecipe.Spec read(Map<String,Object> m)throws IOException{return RepeatRecipe.read(RepeatTestRecipes.write(root,m));}
    @Test void nativeRequestIsClosedExplicitAndDoesNotCertifyUpstreamCompletion()throws Exception{
        var s=read(RepeatTestRecipes.nativeRequest());assertFalse(s.search());assertEquals(RepeatRecipe.Operation.MASK,s.operation());assertEquals("synthetic-build",s.assembly());assertEquals(root.resolve("query.fa"),s.query().path());assertEquals("OUT_WITH_ID",s.annotations().format().name());assertNull(s.process());s.verify();
        assertThrows(UnsupportedOperationException.class,()->s.review().put("organism","altered"));
    }
    @Test void unknownKeysAndCommandTemplatesFailAtEveryApplicableLevel()throws Exception{
        for(String key:List.of("command","args","environment")){var m=RepeatTestRecipes.nativeRequest();m.put(key,"arbitrary");assertThrows(IOException.class,()->read(m));}
        for(String key:List.of("query","annotations","selection","limits","input_review")){var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,key).put("unknown",true);assertThrows(IOException.class,()->read(m));}
    }
    @Test void duplicateKeysInvalidUtf8AndOversizedMetadataFail()throws Exception{
        Path p=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest());String raw=Files.readString(p);Files.writeString(p,raw.replaceFirst("\\{","{\"schema_version\":1,"));assertThrows(IOException.class,()->RepeatRecipe.read(p));
        Files.write(p,new byte[]{'{',(byte)0xc3,0x28,'}'});assertThrows(IOException.class,()->RepeatRecipe.read(p));Files.write(p,new byte[2097153]);assertThrows(IOException.class,()->RepeatRecipe.read(p));
    }
    @Test void requestedHistoricalEditionIsNeverSilentlySubstituted()throws Exception{
        var m=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");m.put("profile","linux-repeatmasker-4.1.2-p1");assertThrows(RepeatRecipe.UnavailableCapability.class,()->read(m));
        var unsupported=RepeatTestRecipes.searchRequest("SPECIES");unsupported.put("profile",RepeatRecipe.CURRENT);assertThrows(RepeatRecipe.UnavailableCapability.class,()->read(unsupported));
        var nativeRequest=RepeatTestRecipes.nativeRequest();nativeRequest.put("profile",RepeatRecipe.HISTORICAL);assertThrows(RepeatRecipe.UnavailableCapability.class,()->read(nativeRequest));
    }
    @Test void searchAndNativeOperationsHaveDistinctRequiredRoles()throws Exception{
        var m=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");m.remove("library");assertThrows(IOException.class,()->read(m));
        var species=RepeatTestRecipes.searchRequest("SPECIES");species.put("library",RepeatTestRecipes.source("library.fa","PLAIN_UTF8"));assertThrows(IOException.class,()->read(species));
        var nativeRequest=RepeatTestRecipes.nativeRequest();nativeRequest.put("operation","SEARCH");assertThrows(IOException.class,()->read(nativeRequest));
        var search=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");search.put("operation","MASK");assertThrows(IOException.class,()->read(search));
        var audit=RepeatTestRecipes.nativeRequest();audit.put("operation","AUDIT");assertThrows(IOException.class,()->read(audit));audit.put("masked",RepeatTestRecipes.source("masked.fa","PLAIN_UTF8"));assertNotNull(read(audit).masked());
    }
    @Test void pathsCannotEscapeTheDeclaredResourceRoot()throws Exception{
        for(String path:List.of("../escape.fa",root.getParent().resolve("outside.fa").toString())){var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"query").put("path",path);assertThrows(IOException.class,()->read(m));}
        var missing=RepeatTestRecipes.nativeRequest();missing.put("resource_root","absent");assertThrows(IOException.class,()->read(missing));
    }
    @Test void reviewFieldsAndStagingPoliciesAreMandatoryMeaningfulChoices()throws Exception{
        for(String key:List.of("organism","genome_provenance","repeat_library_provenance","annotation_provenance","coordinate_policy","paper_settings_status")){var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"input_review").put(key,"");assertThrows(IOException.class,()->read(m));}
        var m=RepeatTestRecipes.nativeRequest();m.put("staging_policy","ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA");assertThrows(IOException.class,()->read(m));
        var search=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");search.put("staging_policy","ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA");assertEquals(RepeatRecipe.Staging.ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA,read(search).staging());
    }
    @Test void divergenceIsExactAndZeroDoesNotMeanZeroDivergenceInTheOriginal()throws Exception{
        var m=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");RepeatTestRecipes.map(m,"parameters").put("maximum_divergence_percent","40.0500");var s=read(m);assertTrue(s.parameters().flags(s.mode(),s.mask()).contains("40.0500"));
        for(Object invalid:List.of("0","0.0","100.1","1e1","NaN",40)){var bad=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");RepeatTestRecipes.map(bad,"parameters").put("maximum_divergence_percent",invalid);assertThrows(IOException.class,()->read(bad));}
    }
    @Test void parallelBatchesAreNotMisreportedAsSingleEngineThreads()throws Exception{
        var m=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");RepeatTestRecipes.map(m,"parameters").put("parallel_batches",64);assertThrows(IOException.class,()->read(m));RepeatTestRecipes.map(m,"process_limits").put("threads",272);assertEquals(64,read(m).parameters().parallelBatches());
        for(String speed:List.of("DEFAULT","SENSITIVE","QUICK","RUSH")){RepeatTestRecipes.map(m,"parameters").put("speed",speed);var s=read(m);var flags=s.parameters().flags(s.mode(),s.mask());assertTrue(flags.contains("-pa"));assertTrue(flags.contains("64"));if(speed.equals("RUSH"))assertTrue(flags.contains("-qq"));}
    }
    @Test void simpleOnlyAndTaxonNamesCannotSmuggleUnrelatedFlags()throws Exception{
        var m=RepeatTestRecipes.searchRequest("SIMPLE_ONLY");var valid=read(m);assertTrue(valid.parameters().flags(valid.mode(),valid.mask()).contains("-noint"));RepeatTestRecipes.map(m,"parameters").put("low_complexity","EXCLUDE");assertThrows(IOException.class,()->read(m));
        for(String species:List.of("Mus musculus'; touch x","Mus\nmusculus","$(command)","-lib")){var bad=RepeatTestRecipes.searchRequest("SPECIES");bad.put("species",species);assertThrows(IOException.class,()->read(bad));}
    }
    @Test void annotationFormatSelectionAndExternalCompletionCannotBeInferred()throws Exception{
        for(String format:List.of("OUT_WITH_ID","OUT_WITHOUT_ID","UCSC_RMSK_WITH_BIN","UCSC_RMSK_NO_BIN")){var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"annotations").put("format",format);assertEquals(format,read(m).annotations().format().name());}
        var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"annotations").put("completion_evidence","CERTIFIED");assertThrows(IOException.class,()->read(m));
        var labels=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(labels,"selection").put("class_names",List.of("LINE"));assertThrows(IOException.class,()->read(labels));
        var duplicates=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(duplicates,"selection").put("classes","EXACT");RepeatTestRecipes.map(duplicates,"selection").put("class_names",List.of("LINE","LINE"));assertThrows(IOException.class,()->read(duplicates));
    }
    @Test void integralCapacitiesCombinedMemoryAndRecipeMutationAreEnforced()throws Exception{
        for(Object bad:List.of(1.0,"1000",-1)){var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"limits").put("input_bytes",bad);assertThrows(IOException.class,()->read(m));}
        var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"limits").put("retained_bytes",1024);assertThrows(IOException.class,()->read(m));
        var workspace=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(workspace,"limits").put("workspace_bytes",65535);assertThrows(IOException.class,()->read(workspace));
        var s=read(RepeatTestRecipes.nativeRequest());Files.writeString(s.recipe(),"{}");assertThrows(IOException.class,s::verify);
    }
}
