/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Closed settings, checked relocation and real complete reference/resource contracts. */
final class LofteeWorkflowTest {
    @TempDir static Path corpus;@TempDir Path temporary;
    @BeforeAll static void load()throws Exception {
        String selected="original-probe-4-original-default/inputs/";
        try(var zip=new ZipInputStream(Objects.requireNonNull(LofteeWorkflowTest.class.getResourceAsStream("/org/jlinalg/genetics/loftee-original/controls-v1.zip")))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().startsWith(selected)){String name=entry.getName().substring(selected.length());NextflowFiles.member(name);Files.write(corpus.resolve(name),zip.readNBytes(1<<20),StandardOpenOption.CREATE_NEW);}
        }
    }
    static Map<String,Object> base(Path root)throws IOException {
        String assembly="SYNTHETIC_LOFTEE_CONTROLS";var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("mode","ANALYZE");request.put("profile",LofteeWorkflowRequest.PROFILE);request.put("edition","grch38-1.0.4");request.put("input_vcf",root.resolve("input.vcf").toString());request.put("input_compression","none");request.put("reference_origin","synthetic-controls");request.put("transcripts",new LinkedHashMap<>(Map.of("kind","gtf","file",root.resolve("genes.gtf.gz").toString(),"index",root.resolve("genes.gtf.gz.tbi").toString(),"assembly_label",assembly,"release_label","synthetic-five-transcripts")));request.put("fasta",new LinkedHashMap<>(Map.of("file",root.resolve("reference.fa").toString(),"index",root.resolve("reference.fa.fai").toString(),"compression","none","assembly_label",assembly,"release_label","synthetic-four-thousand-bases")));request.put("gerp",Map.of("kind","bigwig","file",root.resolve("gerp.bw").toString(),"assembly_label",assembly,"release_label","synthetic-GERP"));request.put("ancestor",Map.of("kind","source-disabled"));request.put("conservation",Map.of("kind","source-disabled"));request.put("runtime",Map.of("environment",root.resolve("runtime-env").toString(),"bioperl",root.resolve("runtime-bio").toString(),"source",root.resolve("runtime-source").toString()));request.put("analyses",new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id","case1","settings",new LinkedHashMap<>(),"ancestral_check",false,"conservation_check",false,"complete_cds_check","source-disabled")))));request.put("out",root.resolve("NEW-result").toString());return request;
    }
    static LofteeWorkflowRequest.Request parse(Map<String,Object> request)throws IOException{return LofteeWorkflowRequest.parse(request,corpus);}
    static Map<String,Object> first(Map<String,Object> request)throws IOException{return object(list(request.get("analyses")).get(0));}
    static void updateFirst(Map<String,Object> request,Consumer<Map<String,Object>> change)throws IOException{var value=first(request);change.accept(value);request.put("analyses",List.of(value));}
    @Test void sourceDefaultsStayExplicitAndUnusedResourcesAreNotInvented()throws Exception {
        var r=parse(base(corpus));var settings=object(r.analyses().get(0).get("settings"));assertEquals(15L,settings.get("min_intron_size"));assertEquals(-58.0,settings.get("gerp_end_trunc_cutoff"));assertEquals(false,settings.get("run_splice_predictions"));assertEquals(true,settings.get("use_gerp_end_trunc"));assertEquals(1,r.threads());assertEquals("source-disabled",r.ancestor().get("kind"));assertEquals("source-disabled",r.conservation().get("kind"));assertEquals(corpus.resolve("input.vcf"),r.files().get("input.vcf"));assertFalse(r.files().containsKey("ancestor.fa"));
    }
    @Test void nativeInputAuditChecksAllOriginalVariantsAndReferenceBases()throws Exception {
        var r=parse(base(corpus));var warnings=new ArrayList<String>();var prepared=LofteeWorkflowInputs.prepare(temporary,r,warnings);assertEquals(15L,prepared.inspection().get("variants"));assertEquals(16L,prepared.inspection().get("ALT_alleles"));assertEquals(0,prepared.inspection().get("samples"));prepared.verify();assertTrue(warnings.stream().anyMatch(s->s.contains("CALLER ASSERTIONS")));assertEquals(Files.readString(corpus.resolve("input.vcf")),Files.readString(prepared.directory().resolve("input.vcf")));
    }
    @Test void changedCapturedReferenceCannotBeAdmittedLater()throws Exception {
        var prepared=LofteeWorkflowInputs.prepare(temporary,parse(base(corpus)),new ArrayList<>());Path file=prepared.directory().resolve("reference.fa");byte[] bytes=Files.readAllBytes(file);bytes[100]='N';Files.write(file,bytes);assertThrows(IOException.class,prepared::verify);
    }
    static Stream<Consumer<Map<String,Object>>> invalidRequests(){return Stream.of(
        m->m.put("unknown",true),m->m.put("profile","compatible-LOFTEE"),m->m.put("edition","master"),m->m.put("input_compression","auto"),m->m.put("maximum_variants",0),m->m.put("threads",65),m->m.put("mode","INSTALL"),
        m->{try{var x=object(m.get("fasta"));x.put("assembly_label","GRCh37");m.put("fasta",x);}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("ancestral_check","false"));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("ancestral_check",true));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("conservation_check",true));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("complete_cds_check","enabled"));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("settings",Map.of("run_splice_predictions","false")));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("settings",Map.of("weak_donor_cutoff",-3)));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("settings",Map.of("filter_position",.1)));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{updateFirst(m,a->a.put("settings",Map.of("use_gerp_end_trunc",false,"gerp_end_trunc_cutoff",-58)));}catch(IOException e){throw new UncheckedIOException(e);}},
        m->{try{var a=first(m);m.put("analyses",List.of(a,a));}catch(IOException e){throw new UncheckedIOException(e);}},m->m.put("analyses",List.of()),m->m.put("gerp",Map.of("kind","not-applied")),
        m->{try{var a=first(m);a.put("settings",Map.of("use_gerp_end_trunc",false));m.put("analyses",List.of(a));}catch(IOException e){throw new UncheckedIOException(e);}}
    );}
    @ParameterizedTest @MethodSource("invalidRequests") void refusesAmbiguousUnappliedOrUnsafeConfiguration(Consumer<Map<String,Object>> change)throws Exception {var input=base(corpus);change.accept(input);assertThrows(IOException.class,()->parse(input));}
    @Test void explicitPercentileSettingRequiresNoUnappliedGerpResource()throws Exception {
        var input=base(corpus);updateFirst(input,a->a.put("settings",Map.of("use_gerp_end_trunc",false,"filter_position",.1)));input.put("gerp",Map.of("kind","not-applied"));var r=parse(input);assertFalse(r.files().containsKey("gerp.bw"));assertEquals(false,object(r.analyses().get(0).get("settings")).get("use_gerp_end_trunc"));
    }
    @Test void originalSqliteConservationStateIsNotSharedAcrossForkedWorkers()throws Exception {
        var input=base(corpus);input.put("conservation",Map.of("kind","sqlite","file",corpus.resolve("synthetic.sqlite").toString(),"assembly_label","SYNTHETIC_LOFTEE_CONTROLS","release_label","test-schema"));updateFirst(input,a->a.put("conservation_check",true));input.put("threads",2);assertThrows(IOException.class,()->parse(input));input.put("threads",1);assertEquals(1,parse(input).threads());
    }
    @Test void originalCacheExecutionIsSkippedBeforeDeserialization()throws Exception {
        var q=base(corpus);q.put("transcripts",Map.of("kind","vep-cache","directory",corpus.resolve("must-not-read-cache").toString(),"assembly_label","GRCh38","release_label","synthetic-control-never-deserialize","cache_version",105,"species","homo_sapiens","cache_trust","trusted-original-ensembl-cache"));
        for(String name:List.of("fasta","gerp")){var value=object(q.get(name));value.put("assembly_label","GRCh38");q.put(name,value);}
        var r=parse(q);var error=assertThrows(LofteeWorkflowRuntime.Unavailable.class,()->LofteeWorkflowRuntime.quick(r));assertTrue(error.getMessage().contains(System.getProperty("os.name").equals("Linux")?"has not been validated":"Linux x86_64"));
    }
    static Map<String,Object> textPolicy(){return Map.of("kind","exact-text","old_prefix","/captured/long-environment");}
    @Test void exactTextReversalPreservesEveryNonprefixByte()throws Exception {
        byte[] original="prefix=/captured/long-environment/lib;cutoff=-58\n".getBytes(StandardCharsets.US_ASCII);byte[] installed="prefix=/new/runtime/lib;cutoff=-58\n".getBytes(StandardCharsets.US_ASCII);assertArrayEquals(original,LofteeRuntimePrefix.baseline(installed,textPolicy(),"/new/runtime"));byte[] changed="prefix=/new/runtime/lib;cutoff=-57\n".getBytes(StandardCharsets.US_ASCII);assertNotEquals(NextflowFiles.sha(original),NextflowFiles.sha(LofteeRuntimePrefix.baseline(changed,textPolicy(),"/new/runtime")));
    }
    @Test void partialOrMissingPrefixRelocationIsRefused()throws Exception {
        assertThrows(IOException.class,()->LofteeRuntimePrefix.baseline("/captured/long-environment/lib:/new/runtime/lib".getBytes(StandardCharsets.US_ASCII),textPolicy(),"/new/runtime"));assertThrows(IOException.class,()->LofteeRuntimePrefix.baseline("no declared runtime prefix".getBytes(StandardCharsets.US_ASCII),textPolicy(),"/new/runtime"));assertThrows(IOException.class,()->LofteeRuntimePrefix.baseline(new byte[]{1,2,3},textPolicy(),"/a/much/longer/than/the/captured/environment/prefix"));
    }
    @Test void exactBinaryFieldsPermitOnlyCheckedNulPaddingAndPrefixReplacement()throws Exception {
        byte[] text="/captured/long-environment/lib:/captured/long-environment/core".getBytes(StandardCharsets.US_ASCII);byte[] baseline=new byte[text.length+16];Arrays.fill(baseline,(byte)7);System.arraycopy(text,0,baseline,4,text.length);baseline[4+text.length]=0;byte[] installed=baseline.clone();byte[] shorter="/new/runtime/lib:/new/runtime/core".getBytes(StandardCharsets.US_ASCII);Arrays.fill(installed,4,5+text.length,(byte)0);System.arraycopy(shorter,0,installed,4,shorter.length);var description=Map.<String,Object>of("kind","exact-NUL-terminated-binary-fields-shorter-or-equal-prefix","old_prefix","/captured/long-environment","regions",List.of(Map.of("offset",4,"length",text.length)));assertArrayEquals(baseline,LofteeRuntimePrefix.baseline(installed,description,"/new/runtime"));installed[4+shorter.length+1]=9;assertThrows(IOException.class,()->LofteeRuntimePrefix.baseline(installed,description,"/new/runtime"));
    }
    @Test void changedOriginalRefFailsBeforeAnyModelIsRun()throws Exception {
        Path inputs=Files.createDirectory(temporary.resolve("supplied"));try(var paths=Files.list(corpus)){for(Path path:paths.toList())Files.copy(path,inputs.resolve(path.getFileName()));}Path vcf=inputs.resolve("input.vcf");String text=Files.readString(vcf);assertTrue(text.contains("\t104\t"));text=text.replace("\tframeshift-insertion\tC\tCA","\tframeshift-insertion\tG\tGA");Files.writeString(vcf,text);assertThrows(IOException.class,()->LofteeWorkflowInputs.prepare(Files.createDirectory(temporary.resolve("attempt")),parse(base(inputs)),new ArrayList<>()));
    }
}
