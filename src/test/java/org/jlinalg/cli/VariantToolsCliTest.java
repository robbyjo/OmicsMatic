/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyVariantFollowupPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VariantToolsCliTest {
    @TempDir Path temporary;
    private Path runtime(String tool) throws Exception {
        Path home=temporary.resolve(tool);Files.createDirectory(home);Path program=home.resolve(tool.equals("alphagenome")?"python.exe":"table_annovar.pl");Files.writeString(program,"Synthetic runtime-admission fixture, not scientific software.\n");
        var metadata=new LinkedHashMap<String,Object>();metadata.put("schema_version",1);metadata.put("tool",tool);metadata.put("profile","variant-tools-v1");metadata.put("lock_sha256",VariantToolRuntime.lockHash());metadata.put("version",tool.equals("alphagenome")?"0.10.0":"synthetic");metadata.put("executable",program.getFileName().toString());metadata.put("files_sha256",Map.of(program.getFileName().toString(),FollowupSupport.hash(program)));metadata.put("symlinks",Map.of());metadata.put("software_scope","Synthetic admission fixture only");Files.writeString(home.resolve(VariantToolRuntime.MARKER),StudyAssistance.json(metadata));return home;
    }
    private int cli(String... args) { return VariantToolsCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())); }
    @Test void dispatchExposesThePackagedInstallerWithoutRequiringAnAnalysisInput() {
        var output=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"variant-tools","--help"},new PrintStream(output),new PrintStream(new ByteArrayOutputStream())));assertTrue(output.toString().contains("variant-tools install alphagenome"));
    }
    @Test void offlineMissingToolFailsBeforeCreatingAHome() {
        Path home=temporary.resolve("offline");assertEquals(2,cli("install","alphagenome","--home",home.toString(),"--downloads","false"));assertFalse(Files.exists(home));
    }
    @Test void annovarRegistrationIsDiscoverableWithoutDownloadingOrPretendingItIsInstalled() throws Exception {
        var output=new ByteArrayOutputStream();assertEquals(0,VariantToolsCli.run(new String[]{"status","annovar","--home",temporary.resolve("missing").toString()},new PrintStream(output),System.err));var result=StudyPublicEvidence.map(SimpleJson.parse(output.toString()));assertEquals("registration_or_registered_archive_required",result.get("status"));assertEquals(VariantToolRuntime.REGISTRATION,result.get("registration_url"));
    }
    @Test void annovarRequiresUserLicenceAcknowledgementBeforeCreatingTheHome() {
        Path home=temporary.resolve("not-accepted");assertEquals(2,cli("install","annovar","--home",home.toString(),"--source-file",temporary.resolve("archive.tar.gz").toString()));assertFalse(Files.exists(home));
    }
    @Test void validManagedRuntimeCanBeReusedWhenDownloadsAreForbidden() throws Exception {
        Path home=runtime("alphagenome");String before=FollowupSupport.hash(home.resolve(VariantToolRuntime.MARKER));assertEquals(0,cli("install","alphagenome","--home",home.toString(),"--downloads","false"));assertEquals(before,FollowupSupport.hash(home.resolve(VariantToolRuntime.MARKER)));
    }
    @Test void changedPackageCannotBeResealedAsAnInstalledTool() throws Exception {
        Path home=runtime("alphagenome");Files.writeString(home.resolve("python.exe"),"Changed dependency");assertThrows(java.io.IOException.class,()->VariantToolRuntime.open("alphagenome",home));assertEquals(2,cli("install","alphagenome","--home",home.toString()));assertEquals("Changed dependency",Files.readString(home.resolve("python.exe")));
    }
    @Test void missingOrAdditionalFilesInvalidateTheRuntime() throws Exception {
        Path home=runtime("alphagenome");Files.writeString(home.resolve("extra.py"),"unexpected");assertThrows(java.io.IOException.class,()->VariantToolRuntime.open("alphagenome",home));Files.delete(home.resolve("extra.py"));Files.delete(home.resolve("python.exe"));assertThrows(java.io.IOException.class,()->VariantToolRuntime.open("alphagenome",home));
    }
    @Test void incompleteExistingHomesAreRetainedInsteadOfBeingOverwritten() throws Exception {
        Path home=temporary.resolve("incomplete");Files.createDirectory(home);Files.writeString(home.resolve("diagnostic.txt"),"retain me");assertEquals(2,cli("install","alphagenome","--home",home.toString()));assertEquals("retain me",Files.readString(home.resolve("diagnostic.txt")));try(var files=Files.list(home)){assertEquals(1,files.count());}
    }
    @Test void installationDefaultsPreserveExplicitExternalPythonAndAreIdempotent() {
        var defaults=StudyVariantFollowupPolicy.resolve(Map.of());var genome=StudyPublicEvidence.map(defaults.get("alphagenome"));assertEquals("automatic",StudyPublicEvidence.map(genome.get("installation")).get("mode"));assertEquals(defaults,StudyVariantFollowupPolicy.resolve(defaults));
        assertEquals("external",StudyPublicEvidence.map(StudyPublicEvidence.map(defaults.get("consequences")).get("installation")).get("mode"));
        var external=StudyVariantFollowupPolicy.resolve(Map.of("alphagenome",Map.of("python","prepared-python")));assertEquals("external",StudyPublicEvidence.map(StudyPublicEvidence.map(external.get("alphagenome")).get("installation")).get("mode"));
    }
    @Test void invalidInstallerSettingsCannotEnterAResolvedStudy() {
        for(var installation:List.of(Map.of("mode","unknown"),Map.of("mode","automatic","source_url_env","not valid"),Map.of("mode","automatic","sha256","wrong"),Map.of("mode","automatic","accept_license","yes"),Map.of("archive","one","source_url_env","TWO")))
            assertThrows(IllegalArgumentException.class,()->StudyVariantFollowupPolicy.resolve(Map.of("consequences",Map.of("installation",installation))));
        assertThrows(IllegalArgumentException.class,()->StudyVariantFollowupPolicy.resolve(Map.of("alphagenome",Map.of("installation",Map.of("archive","not-an-sdk")))));
        assertThrows(IllegalArgumentException.class,()->StudyVariantFollowupPolicy.resolve(Map.of("consequences",Map.of("installation",Map.of("mode","automatic")))));
    }
    @Test void studyCanBindAnInstalledRuntimeOfflineWithoutModifyingItsEvidence() throws Exception {
        Path home=runtime("alphagenome"),output=temporary.resolve("operation");Files.createDirectory(output);var configuration=Map.<String,Object>of("installation",Map.of("mode","automatic","home",home.toString()),"python","unused-bootstrap");
        var context=new Context(temporary,output,Map.of(),Map.of("resources",Map.of("network","offline","downloads","forbid")),Map.of(),Map.of());var result=StudyVariantTools.prepare(context,"alphagenome",configuration);
        assertEquals(home.resolve("python.exe").toString(),result.get("python"));assertFalse(configuration.containsKey("managed_runtime_marker_sha256"));assertTrue(Files.isRegularFile(output.resolve("alphagenome-software-setup.yaml")));
    }
    @Test void changedManagedDependencyInvalidatesScientificCacheInputs() throws Exception {
        Path home=runtime("alphagenome");var study=Map.<String,Object>of("hypotheses",List.of(Map.of("id","H","analysis",Map.of("variant_followup",Map.of("alphagenome",Map.of("enabled",true,"engine","atlas","installation",Map.of("mode","automatic","home",home.toString())))))));var operation=Map.<String,Object>of("type","variant_scoring","hypothesis","H");
        var before=StudyVariantTools.inputHashes(temporary,study,operation);assertTrue(before.get("variant_software:alphagenome").startsWith("verified:"));Files.writeString(home.resolve("python.exe"),"changed");assertEquals("invalid_managed_runtime",StudyVariantTools.inputHashes(temporary,study,operation).get("variant_software:alphagenome"));
    }
    @Test void nativeSdkRouteRetainsASetupFailureBeforeAnyModelProcessIsLaunched() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("PATH")!=null);
        Path home=temporary.resolve("unfinished"),output=temporary.resolve("native-preflight");Files.createDirectory(home);Files.createDirectory(output);
        var configuration=Map.<String,Object>of("enabled",true,"engine","atlas","api_key_env","PATH","python","NOT_A_REAL_INTERPRETER","installation",Map.of("mode","automatic","home",home.toString()));
        var context=new Context(temporary,output,Map.of(),Map.of("resources",Map.of("network","online","downloads","automatic")),Map.of(),Map.of());var variant=StudyVariantUniverse.key("GRCh38","1",100,"A","C");
        var result=new StudyAlphaGenome().execute(context,Map.of(variant.id(),variant),configuration);assertEquals("software_setup_required",result.statuses().get(variant.id()));assertTrue(Files.isRegularFile(output.resolve("alphagenome-software-status.yaml")));assertFalse(Files.exists(output.resolve("alphagenome")));
    }
}
