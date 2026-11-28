/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.workflow.NfcoreInputBindings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class NfcoreRnasplice104RequestTest {
    static Map<String,Object> request(){var result=baseRequest();result.put("engine_profile",NfcoreValidationPlugin.ENGINE);result.put("rmats_read_length",40);result.put("rmats_paired_statistics",false);result.put("rmats_pairings",Map.of());result.put("rmats_maximum_rows",1000000);result.put("quantification_maximum_rows",1000000);return result;}
    private static Map<String,Object> baseRequest(){
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("pipeline_profile",NfcoreRnasplice104Profile.ID);for(String key:List.of("source_directory","plugins_directory","engine_directory","container_home","image_set","fasta","gtf"))result.put(key,"/private/"+key);var samples=new ArrayList<Map<String,Object>>();for(int i=1;i<=4;i++)samples.add(new LinkedHashMap<>(Map.of("id","s"+i,"condition",i<=2?"A":"B","strandedness","unstranded","libraries",new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("fastq_1","/data/s"+i+"_1.fastq.gz","fastq_2","/data/s"+i+"_2.fastq.gz")))))));result.put("samples",samples);result.put("contrasts",new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id","A-B","treatment","A","control","B")))));result.put("executor_cpus",4);result.put("executor_memory_bytes",17179869184L);result.put("task_cpus",2);result.put("task_memory_bytes",6442450944L);result.put("task_seconds",21600);result.put("miso_read_length",75);result.put("miso_plot_genes",List.of("ENSG1","ENSG2"));result.put("limits",Map.of());result.put("rmats_maximum_p","0.05");result.put("rmats_minimum_absolute_psi","0.05");return result;
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> samples(Map<String,Object> request){return(List<Map<String,Object>>)request.get("samples");}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> libraries(Map<String,Object> sample){return(List<Map<String,Object>>)sample.get("libraries");}
    @Test void admitsExplicitCompleteBiologicalDesignAndPredictsTechnicalIds()throws Exception{
        var raw=request();libraries(samples(raw).get(0)).add(new LinkedHashMap<>(Map.of("fastq_1","/data/second_1.fastq.gz","fastq_2","/data/second_2.fastq.gz")));var value=NfcoreRnasplice104Request.parse(raw);var reads=NfcoreRnasplice104Prepare.reads(value);assertEquals(4,value.samples().size());assertEquals(5,reads.size());assertEquals("s1_T1",reads.get(0).sourceId());assertEquals("s1_T2",reads.get(1).sourceId());assertEquals("s2_T1",reads.get(2).sourceId());
    }
    @Test void preservesCaseSensitiveProducerPathsDuringPortableParsing()throws Exception{
        var raw=request();libraries(samples(raw).get(0)).add(new LinkedHashMap<>(Map.of("fastq_1","/data/S1_1.fastq.gz","fastq_2","/data/S1_2.fastq.gz")));var parsed=NfcoreRnasplice104Request.parse(raw);assertEquals(2,parsed.samples().get(0).libraries().size());assertEquals("/data/s1_1.fastq.gz",parsed.samples().get(0).libraries().get(0).first());assertEquals("/data/S1_1.fastq.gz",parsed.samples().get(0).libraries().get(1).first());
    }
    @Test void doesNotCountTechnicalLibrariesAsIndependentBiologicalReplicates(){
        var raw=request();samples(raw).remove(3);samples(raw).remove(1);for(var sample:samples(raw))libraries(sample).add(new LinkedHashMap<>(Map.of("fastq_1","/data/extra_1.fastq.gz","fastq_2","/data/extra_2.fastq.gz")));IOException failure=assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(raw));assertTrue(failure.getMessage().contains("biological"));
    }
    @Test void refusesUncompressedAndMismatchedOriginalSuffixesWithoutRenaming(){
        var raw=request();libraries(samples(raw).get(0)).get(0).put("fastq_1","/data/read.fastq");assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(raw));var mixed=request();libraries(samples(mixed).get(0)).get(0).put("fastq_2","/data/read.fq.gz");assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(mixed));
    }
    @Test void refusesSilentIdentifierRepairAndUnknownStrandedness(){
        var raw=request();samples(raw).get(0).put("id","sample one");assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(raw));var strand=request();samples(strand).get(0).put("strandedness","auto");assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(strand));
    }
    @Test void retainsPermissiveOriginalConditionSemanticsWithoutMakeNamesRepair()throws Exception{
        var raw=request();samples(raw).get(0).put("condition","A-B");samples(raw).get(1).put("condition","A-B");raw.put("contrasts",List.of(Map.of("id","actual-label","treatment","A-B","control","B")));var parsed=NfcoreRnasplice104Request.parse(raw);assertEquals("A-B",parsed.samples().get(0).condition());assertEquals("A-B",parsed.contrasts().get(0).treatment());
        var spaced=request();samples(spaced).get(0).put("condition","A B");samples(spaced).get(1).put("condition","A B");spaced.put("contrasts",List.of(Map.of("id","actual-label","treatment","A B","control","B")));assertEquals("A B",NfcoreRnasplice104Request.parse(spaced).samples().get(0).condition());
    }
    @Test void requiresLiteralContrastMembershipAndIndependentGroups(){
        var absent=request();absent.put("contrasts",List.of(Map.of("id","x","treatment","absent","control","B")));assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(absent));var self=request();self.put("contrasts",List.of(Map.of("id","x","treatment","A","control","A")));assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(self));var overlap=request();overlap.put("contrasts",List.of(Map.of("id","first","treatment","A","control","B"),Map.of("id","second","treatment","A","control","B")));assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(overlap));
    }
    @Test void refusesCapacityContradictionsAndUnsupportedEdition(){
        var raw=request();raw.put("task_cpus",5);assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(raw));var edition=request();edition.put("pipeline_profile","nf-core/rnasplice-latest");assertThrows(NextflowProcess.Unavailable.class,()->NfcoreRnasplice104Request.parse(edition));
    }
    @Test void bindsEveryReadReferenceAndOriginalScientificDefaultExplicitly()throws Exception{
        var value=NfcoreRnasplice104Request.parse(request());var layout=NfcoreInputBindings.plan("/opt/run/engine","/opt/run/materialized");String sheet=NfcoreRnasplice104Prepare.samplesheet(value,layout);assertTrue(sheet.contains("s1,/opt/run/engine/workspace/resources/r0/materialized/data/reads/000001/first/s1_1.fastq.gz"));assertTrue(sheet.contains("/second/s1_2.fastq.gz,unstranded,A\n"));var recipe=NfcoreRnasplice104Prepare.recipe("/opt/run",value);assertEquals(List.of("singularity"),object(recipe.get("workflow")).get("profiles"));Map<?,?> parameters=object(recipe.get("parameters"));assertEquals(75,parameters.get("miso_read_len"));assertEquals("ENSG1,ENSG2",parameters.get("miso_genes"));assertFalse(parameters.containsKey("skip_bigwig"));assertFalse(parameters.containsKey("skip_miso"));assertEquals(28,list(recipe.get("outputs")).size());
    }
    @Test void pinsWholeSourceGitModesAndCompleteImageAliasClosure()throws Exception{
        var source=NfcoreRnasplice104Profile.sourceFiles();assertEquals(206,source.size());assertEquals(29,source.values().stream().filter(row->Boolean.TRUE.equals(row.get("executable"))).count());assertEquals(31,NfcoreRnasplice104Profile.images().size());String overlay=NfcoreRnasplice104Profile.overlay("/opt/run/engine","/opt/run/engine/workspace/resources/r0/materialized",4,17179869184L);assertEquals(72,overlay.split("withName:",-1).length-1);assertTrue(overlay.contains("container = '/opt/run/engine/workspace/resources/r0/materialized/images/"));assertFalse(overlay.contains("/private/"));
    }
    @Test void requiresDeclaredBiologicalPairsInsteadOfInferringThemFromPairedFastqs()throws Exception{
        var raw=request();raw.put("rmats_paired_statistics",true);assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(raw));raw.put("rmats_pairings",Map.of("A-B",List.of(Map.of("treatment","s1","control","s3"),Map.of("treatment","s2","control","s4"))));assertTrue(NfcoreRnasplice104Request.parse(raw).rmatsPairedStatistics());raw.put("rmats_pairings",Map.of("A-B",List.of(Map.of("treatment","s1","control","s3"))));assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(raw));
    }
    @Test void refusesSourceSuffixGroupingThatWouldChangeBiologicalIdentities(){
        var raw=request();samples(raw).get(0).put("id","s_T4");assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(raw));var lanes=request();samples(lanes).get(0).put("id","s_T1");libraries(samples(lanes).get(0)).add(new LinkedHashMap<>(Map.of("fastq_1","/data/next_1.fastq.gz","fastq_2","/data/next_2.fastq.gz")));assertThrows(IOException.class,()->NfcoreRnasplice104Request.parse(lanes));
    }
    @Test void skipsOriginalGlobalReadProtocolThatCannotRepresentMixedLibraries(){
        var raw=request();samples(raw).get(0).put("strandedness","reverse");assertThrows(NextflowProcess.Unavailable.class,()->NfcoreRnasplice104Request.parse(raw));
    }
    @Test void skipsShellMetacharactersWithoutRepairingLiteralScientificIdentifiers(){
        var raw=request();samples(raw).get(0).put("id","sample;pwd");assertThrows(NextflowProcess.Unavailable.class,()->NfcoreRnasplice104Request.parse(raw));var filename=request();libraries(samples(filename).get(0)).get(0).put("fastq_1","/data/$(pwd).fastq.gz");assertThrows(NextflowProcess.Unavailable.class,()->NfcoreRnasplice104Request.parse(filename));var genes=request();genes.put("miso_plot_genes",List.of("x`pwd`"));assertThrows(NextflowProcess.Unavailable.class,()->NfcoreRnasplice104Request.parse(genes));
    }
}
