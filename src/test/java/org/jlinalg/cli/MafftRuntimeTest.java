/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MafftRuntimeTest {
    @TempDir Path temp;
    static Stream<String> versions(){return Stream.of("7.313","7.475","7.487","7.490","7.505","7.526");}
    private static Path suppliedRoot(){String root=System.getenv("JLINALG_MAFFT_SETUP_ROOT");assumeTrue(root!=null&&System.getProperty("os.name").equals("Linux"),"Exact Linux private setup matrix not supplied");return Path.of(root);}
    private static Path dedicated(){String value=System.getenv("JLINALG_MAFFT_TEST_HOME");assumeTrue(value!=null&&System.getProperty("os.name").equals("Linux"),"Dedicated mutable runtime fixture not supplied");Path path=Path.of(value);assertTrue(path.getFileName().toString().startsWith("native-test-"),"Never mutate a supplied production home");return path;}
    private static MafftRuntime.Context context()throws IOException{return MafftRuntime.open(dedicated(),"linux-x86_64-mafft-7.526");}
    private static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private static void write(Path path,byte[] raw)throws IOException{Files.write(path,raw,StandardOpenOption.TRUNCATE_EXISTING);}
    @Test void onlySixExactEditionsAreDeclaredWithoutMajorOnlySubstitution(){assertEquals(6,MafftRuntime.profiles().size());assertFalse(MafftRuntime.profiles().contains("linux-x86_64-mafft-7"));assertFalse(MafftRuntime.profiles().contains("linux-x86_64-mafft-7.525"));}
    @Test void freshPortableExportHasEveryPinnedResourceAndExplicitRecovery()throws Exception {
        Path kit=temp.resolve("kit");var exported=MafftRuntime.exportSetup(kit);assertEquals(8,exported.size());
        try(var paths=Files.walk(kit)){assertEquals(exported.keySet(),new HashSet<>(paths.filter(Files::isRegularFile).map(p->kit.relativize(p).toString().replace('\\','/')).toList()));}
        for(var e:exported.entrySet()){byte[] raw=Files.readAllBytes(kit.resolve(e.getKey()));assertEquals(raw.length,e.getValue().bytes());assertEquals(sha(raw),e.getValue().sha256());}
        String readme=Files.readString(kit.resolve("README.txt"));for(String text:List.of("unfinished","--allow-install","--allow-download","--archive-dir","SKIP3","FAIL2","COMPLETED.json","P052","7.475","Cell Ranger"))assertTrue(readme.contains(text),text);
        String installer=Files.readString(kit.resolve("install_mafft.py"));assertTrue(installer.contains("MAFFT_TMPDIR"));assertTrue(installer.contains("WNOWAIT"));
    }
    @Test void exportRefusesToReplaceAnyExistingDirectory()throws Exception {Path root=temp.resolve("existing");Files.createDirectory(root);Files.writeString(root.resolve("keep"),"original");assertThrows(IOException.class,()->MafftRuntime.exportSetup(root));assertEquals("original",Files.readString(root.resolve("keep")));}
    @Test void exportRequiresAnExistingCanonicalParent() {assertThrows(IOException.class,()->MafftRuntime.exportSetup(temp.resolve("absent/kit")));assertFalse(Files.exists(temp.resolve("absent")));}
    @Test void unsupportedNativeHostHasAnActionableUnavailableResult(){assumeFalse(System.getProperty("os.name").equals("Linux"));var failure=assertThrows(MafftRuntime.Unavailable.class,()->MafftRuntime.open(temp.resolve("absent"),"linux-x86_64-mafft-7.526"));assertTrue(failure.getMessage().contains("WSL"));}
    @Test void originalElfParserRefusesTruncatedAndForeignPrograms(){for(byte[] raw:List.of(new byte[0],"#!/bin/sh\n".getBytes(StandardCharsets.UTF_8),new byte[64]))assertThrows(IOException.class,()->MafftRuntime.staticElf(raw));}
    @ParameterizedTest @MethodSource("versions") void everyActualInstalledEditionAndCompleteSyntheticFamilyIsReadOnlyAudited(String version)throws Exception {
        Path root=suppliedRoot();var c=MafftRuntime.open(root.resolve("homes/"+version),"linux-x86_64-mafft-"+version);c.verify();
        assertEquals(version,c.version());assertEquals(28,c.helpers().size());assertTrue(c.files().size()>=43);assertEquals(23,c.setupFiles().size());
        assertEquals(List.of("/bin/bash",c.home().resolve("mafft.bat").toString()),c.command());
        if(version.equals("7.475")){assertNotNull(c.knownWarning());assertTrue(c.knownWarning().contains("7.463"));}else assertNull(c.knownWarning());
        assertThrows(UnsupportedOperationException.class,()->c.files().clear());assertThrows(UnsupportedOperationException.class,()->c.helpers().clear());
    }
    @Test void unavailableHomeAndUnresolvedEditionRemainUnavailable()throws Exception {assumeTrue(System.getProperty("os.name").equals("Linux"));assertThrows(MafftRuntime.Unavailable.class,()->MafftRuntime.open(temp.resolve("absent"),"linux-x86_64-mafft-7.526"));assertThrows(MafftRuntime.Unavailable.class,()->MafftRuntime.open(temp,"linux-x86_64-mafft-7"));}
    @Test void aRealFailedFinalizationCannotAdmitItsProvisionalRuntimeMarker()throws Exception {
        Path root=suppliedRoot(),home=root.resolve("homes/finalization-failed-7.526"),report=root.resolve("reports/finalization-failed-7.526");
        assertTrue(Files.isRegularFile(home.resolve(MafftRuntime.MARKER)));assertTrue(Files.isRegularFile(report.resolve("FAILURE.json")));assertFalse(Files.exists(report.resolve("COMPLETED.json")));
        assertThrows(IOException.class,()->MafftRuntime.open(home,"linux-x86_64-mafft-7.526"));
    }
    @Test void unmarkedDirectoryCannotBeAdmittedAsAnInstalledPayload()throws Exception {assumeTrue(System.getProperty("os.name").equals("Linux"));Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rwx------"));assertThrows(MafftRuntime.Unavailable.class,()->MafftRuntime.open(temp,"linux-x86_64-mafft-7.526"));}
    @Test void exportedEnvironmentHasOnlyDeclaredBindingsAndExplicitThreads()throws Exception {var c=context();var env=c.environment(c.setupReport(),2);assertEquals(9,env.size());assertEquals(c.setupReport().toString(),env.get("MAFFT_TMPDIR"));assertEquals("2",env.get("OMP_NUM_THREADS"));assertFalse(env.containsKey("MAFFT_BINARIES"));assertFalse(env.containsKey("BASH_ENV"));assertFalse(env.containsKey("PERL5OPT"));assertThrows(IllegalArgumentException.class,()->c.environment(c.setupReport(),0));}
    @Test void completeHomeCannotHideAnAdditionalEmptyDirectory()throws Exception {var c=context();Path extra=c.home().resolve("unexpected");Files.createDirectory(extra,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));try{assertThrows(IOException.class,c::verify);}finally{Files.delete(extra);}c.verify();}
    @Test void aChangedLateOriginalProgramIsDetectedBeforeAnyExecution()throws Exception {
        var c=context();Path file=c.home().resolve("mafftdir/libexec/tbfast");byte[] old=Files.readAllBytes(file),changed=old.clone();changed[changed.length-1]^=1;
        var mode=Files.getPosixFilePermissions(file);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rwx------"));try{write(file,changed);assertThrows(IOException.class,c::verify);}finally{write(file,old);Files.setPosixFilePermissions(file,mode);}c.verify();
    }
    @Test void changedPrivateProgramModesAreNotSilentlyRepaired()throws Exception {var c=context();Path file=c.home().resolve("mafft.bat");var mode=Files.getPosixFilePermissions(file);try{Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rwxr-xr-x"));assertThrows(IOException.class,c::verify);}finally{Files.setPosixFilePermissions(file,mode);}c.verify();}
    @Test void aPrivateProgramHardlinkAliasCannotBeAdmitted()throws Exception {var c=context();Path alias=temp.resolve("alias");Files.createLink(alias,c.home().resolve("mafftdir/libexec/tbfast"));try{assertThrows(IOException.class,c::verify);}finally{Files.delete(alias);}c.verify();}
    @Test void aCopiedMarkerDoesNotAdmitAnotherHome()throws Exception {var c=context();Path other=temp.resolve("other");Files.createDirectory(other,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));Files.copy(c.home().resolve(MafftRuntime.MARKER),other.resolve(MafftRuntime.MARKER));assertThrows(IOException.class,()->MafftRuntime.open(other,c.profile()));c.verify();}
    @Test void anExtraSetupDirectoryCannotBeHiddenByRehashingCompletionMetadata()throws Exception {
        var c=context();Path completion=c.setupReport().resolve("COMPLETED.json"),extra=c.setupReport().resolve("extra");byte[] old=Files.readAllBytes(completion);
        Files.createDirectory(extra,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try{String changed=new String(old,StandardCharsets.UTF_8).replace("\"directories\": [","\"directories\": [\n    \"extra\",");assertNotEquals(new String(old,StandardCharsets.UTF_8),changed);write(completion,changed.getBytes(StandardCharsets.UTF_8));assertThrows(IOException.class,()->MafftRuntime.open(c.home(),c.profile()));}finally{write(completion,old);Files.delete(extra);}c.verify();
    }
    private static void changedAndRehashedReport(String name,String from,String to)throws Exception {
        var c=context();Path file=c.setupReport().resolve(name),completion=c.setupReport().resolve("COMPLETED.json");byte[] original=Files.readAllBytes(file),oldCompletion=Files.readAllBytes(completion);
        String text=new String(original,StandardCharsets.UTF_8);assertTrue(text.contains(from));byte[] changed=text.replace(from,to).getBytes(StandardCharsets.UTF_8);assertEquals(original.length,changed.length,"Counterexample must preserve size so native semantic checks decide");
        String manifest=new String(oldCompletion,StandardCharsets.UTF_8);assertTrue(manifest.contains(sha(original)));manifest=manifest.replace(sha(original),sha(changed));
        try{write(file,changed);write(completion,manifest.getBytes(StandardCharsets.UTF_8));assertThrows(IOException.class,()->MafftRuntime.open(c.home(),c.profile()));}finally{write(file,original);write(completion,oldCompletion);}c.verify();
    }
    @Test void rehashedOriginalMapChangesDoNotBypassTheSourceControl()throws Exception {changedAndRehashedReport("AA-add-keep-map/added.fasta.map","P, 5, -","P, 5, 5");}
    @Test void rehashedNonzeroExitCannotBeCalledCompletedSetup()throws Exception {changedAndRehashedReport("DNA-auto/process.json","\"exit_code\": 0","\"exit_code\": 9");}
    @Test void rehashedCommandChangesAreNotAcceptedAsTheDeclaredAlgorithm()throws Exception {changedAndRehashedReport("DNA-auto/process.json","--nuc","--bad");}
    @Test void rehashedVersionBannerCannotAdmitAnotherEdition()throws Exception {changedAndRehashedReport("version/process.stderr","v7.526","v7.525");}
    @Test void rehashedInstallationCannotClaimUserScientificAdmission()throws Exception {changedAndRehashedReport("installation.json","\"scientific_analysis_admitted\": false","\"scientific_analysis_admitted\": true ");}
}
