package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepeatRuntimeTest {
    @TempDir Path root;
    private Map<String,Object> mutable(Object source)throws Exception{
        var result=new LinkedHashMap<String,Object>();for(var entry:RepeatBundle.object(RepeatBundle.json(source)).entrySet())result.put(entry.getKey().toString(),entry.getValue());return result;
    }
    @Test void ownedTreeIncludesEveryFileAndItsCompleteByteHash()throws Exception{
        Files.writeString(root.resolve("data"),"abc");var tree=RepeatRuntime.managedTree(root,()->{});
        assertEquals(java.util.Set.of(".","data"),tree.keySet());var file=(Map<?,?>)((Map<?,?>)tree.get("data")).get("file");
        assertEquals(3L,file.get("bytes"));assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",file.get("sha256"));
    }
    @Test void equalCountEmptyDirectoryRenamesChangeTheOwnedTree()throws Exception{
        Files.createDirectory(root.resolve("empty"));var before=RepeatRuntime.managedTree(root,()->{});
        Files.move(root.resolve("empty"),root.resolve("other-empty"));assertNotEquals(before,RepeatRuntime.managedTree(root,()->{}));
    }
    @Test void sameSizeAndRestoredMtimeCannotHideChangedBytes()throws Exception{
        Path file=root.resolve("data");Files.writeString(file,"abc");FileTime time=Files.getLastModifiedTime(file);var before=RepeatRuntime.managedTree(root,()->{});
        Files.writeString(file,"xyz");Files.setLastModifiedTime(file,time);assertNotEquals(before,RepeatRuntime.managedTree(root,()->{}));
    }
    @Test void onlyTheTwoBoundSealFilesAreExcludedFromTheTree()throws Exception{
        Files.writeString(root.resolve("data"),"abc");var before=RepeatRuntime.managedTree(root,()->{});
        Files.writeString(root.resolve("manifest.json"),"bound separately");Files.writeString(root.resolve("ADMITTED.json"),"bound separately");
        assertEquals(before,RepeatRuntime.managedTree(root,()->{}));Files.writeString(root.resolve("unexpected"),"extra");assertNotEquals(before,RepeatRuntime.managedTree(root,()->{}));
    }
    @Test void directoriesCannotMasqueradeAsExcludedSealMetadata()throws Exception{
        Files.createDirectory(root.resolve("manifest.json"));assertThrows(IOException.class,()->RepeatRuntime.managedTree(root,()->{}));
    }
    @Test void storedHostIdentityCannotBeMutatedThroughAnArrayAlias(){
        byte[] source={1,2,3};var context=new RepeatRuntime.Context(root,null,"manifest","marker",source);source[0]=9;
        assertArrayEquals(new byte[]{1,2,3},context.hostIdentity());byte[] copy=context.hostIdentity();copy[1]=9;assertArrayEquals(new byte[]{1,2,3},context.hostIdentity());
    }
    private Map<String,Object> record()throws Exception{
        var value=new LinkedHashMap<String,Object>();value.put("schema_version",1L);value.put("tool",RepeatRuntime.TOOL);value.put("status","ADMITTED_FIXED_ORIGINAL_HEALTH_AND_HOST");
        value.put("profile",RepeatRecipe.CURRENT);value.put("version","4.2.6");value.put("runtime_home",root.toString());value.put("payload_home",root.resolve("payload").toString());value.put("python",null);
        value.put("payload_lock_sha256","b".repeat(64));value.put("health_lock_sha256",RepeatHealth.LOCK_SHA256);value.put("settings",RepeatRuntime.settings());value.put("host_identity_sha256","c".repeat(64));
        value.put("controls",List.of());value.put("scientific_runtime_admitted",true);value.put("study_search_completed",false);value.put("biological_accuracy_certified",false);value.put("scope","header is structural; actual payload,host and complete health still require verification");return value;
    }
    private Map<String,Object> manifest(){return new LinkedHashMap<>(Map.of("schema_version",1L,"tool",RepeatRuntime.TOOL,"profile",RepeatRecipe.CURRENT,"managed_tree",Map.of()));}
    private Map<String,Object> marker(Map<String,Object> manifest)throws Exception{return new LinkedHashMap<>(Map.of("schema_version",1L,"tool",RepeatRuntime.TOOL,"profile",RepeatRecipe.CURRENT,"manifest_sha256",AdmixtureInputs.digest(RepeatBundle.json(manifest))));}
    private void header(Map<String,Object> record,Map<String,Object> manifest,Map<String,Object> marker)throws Exception{RepeatRuntime.header(root,RepeatRecipe.CURRENT,record,manifest,marker,RepeatBundle.json(manifest));}
    @Test void runtimeHeaderDoesNotClaimACompletedStudySearch()throws Exception{
        var manifest=manifest();header(record(),manifest,marker(manifest));assertEquals(false,record().get("study_search_completed"));
    }
    @Test void nativeCompletionMetadataCannotMasqueradeAsOriginalRuntimeAdmission()throws Exception{
        var m=manifest();m.put("tool",RepeatBundle.TOOL);assertThrows(IOException.class,()->header(record(),m,marker(m)));
    }
    @Test void studyCompletionOrBiologicalCertificationFlagsAreRejected()throws Exception{
        for(String key:List.of("study_search_completed","biological_accuracy_certified")){var r=record();r.put(key,true);var m=manifest();assertThrows(IOException.class,()->header(r,m,marker(m)));}
    }
    @Test void undeclaredCertificationFieldsCannotBeAddedToTheAdmissionHeader()throws Exception{
        var r=record();r.put("whole_paper_certified",true);var m=manifest();assertThrows(IOException.class,()->header(r,m,marker(m)));
    }
    @Test void changedRuntimeSettingsCannotBeBlessedByARehashedMarker()throws Exception{
        var r=record();r.put("settings",Map.of("preset",RepeatRuntime.PRESET,"unbounded",true));var m=manifest();assertThrows(IOException.class,()->header(r,m,marker(m)));
    }
    @Test void markerHashMustBindTheExactManifestBytes()throws Exception{
        var m=manifest();var marker=marker(m);m.put("managed_tree",Map.of("unexpected",true));assertThrows(IOException.class,()->header(record(),m,marker));
    }
    @Test void missingDeclaredEvidenceFieldsAreNotDefaultedToSuccessfulValues()throws Exception{
        for(String key:List.of("controls","health_lock_sha256","runtime_home")){var r=record();r.remove(key);var m=manifest();assertThrows(IOException.class,()->header(r,m,marker(m)));}
    }
    private Map<String,Object> host(){return new LinkedHashMap<>(Map.of("files",new LinkedHashMap<>(Map.of("physicalHashes",1L,"logicalBytes",3L,"files",Map.of("module","abc"))),"perlRoots",List.of("/first","/second"),"pythonPaths",List.of("/zip","/stdlib")));}
    @Test void physicalHashCacheStatisticsDoNotDefineHostIdentity()throws Exception{
        var a=host();var b=host();var files=mutable(b.get("files"));files.put("physicalHashes",100L);b.put("files",files);assertEquals(RepeatHost.identity(a),RepeatHost.identity(b));
    }
    @Test void actualPerlAndPythonLookupPrecedenceRemainsPartOfHostIdentity()throws Exception{
        for(String key:List.of("perlRoots","pythonPaths")){var a=host();var b=host();var paths=new ArrayList<>((List<?>)b.get(key));java.util.Collections.reverse(paths);b.put(key,paths);assertNotEquals(RepeatHost.identity(a),RepeatHost.identity(b));}
    }
    @Test void fileIdentityAndModeChangesAreNotRemovedAlongWithCacheStatistics()throws Exception{
        var a=host();var b=host();var files=mutable(b.get("files"));files.put("files",Map.of("module","changed"));b.put("files",files);assertNotEquals(RepeatHost.identity(a),RepeatHost.identity(b));
    }
    private RepeatPayload.Context fakePayload(){return new RepeatPayload.Context(root.resolve("payload"),RepeatRecipe.CURRENT,"4.2.6",null,Map.of("repeatmasker/RepeatMasker",new RepeatPayload.Fingerprint(3,"a".repeat(64))),Map.of(),RepeatRuntime.PAYLOAD,"b".repeat(64));}
    private Map<String,Object> resourceLedger()throws Exception{
        var p=fakePayload();return mutable(Map.of("schema_version",1L,"scope","owned-original-source-and-library-resources-only","profile",p.profile(),"lock_sha256",p.lockSha256(),"payload_home",p.home(),
                "members",Map.of("repeatmasker/RepeatMasker",new RepeatResources.Member("repeatmasker/RepeatMasker","COPY",p.files().get("repeatmasker/RepeatMasker"))),
                "working_configuration_delta",Map.of(),"generated_cache_policy","Fresh analysis-owned Libraries only;sealed payload/source members stay unchanged. Record complete generated cache contents and resource provenance after original execution;directory names alone never establish library identity.","upstream_search_completed",false));
    }
    private RepeatResources.Prepared resources(Map<String,Object> ledger)throws Exception{return RepeatResources.fromLedger(fakePayload(),root.resolve("resources"),RepeatRuntime.PAYLOAD,RepeatBundle.json(ledger));}
    @Test void resourceLedgerMembersMustComeFromThePinnedPayload()throws Exception{
        var result=resources(resourceLedger());assertEquals(fakePayload().files().get("repeatmasker/RepeatMasker"),result.members().get("repeatmasker/RepeatMasker").fingerprint());
    }
    @Test void resourceProfileHomeAndCompletionScopeCannotBeRelabeled()throws Exception{
        for(String key:List.of("profile","payload_home","upstream_search_completed")){var ledger=resourceLedger();ledger.put(key,key.equals("upstream_search_completed")?true:"different");assertThrows(IOException.class,()->resources(ledger));}
    }
    @Test void extraMissingOrSelfSignedSourceMembersAreRejected()throws Exception{
        for(String mutation:List.of("extra","missing","changed-hash","changed-role")){
            var ledger=resourceLedger();var members=mutable(ledger.get("members"));var member=mutable(members.get("repeatmasker/RepeatMasker"));
            switch(mutation){case "extra"->members.put("repeatmasker/extra",member);case "missing"->members.clear();case "changed-hash"->{member.put("fingerprint",Map.of("bytes",3L,"sha256","c".repeat(64)));members.put("repeatmasker/RepeatMasker",member);}case "changed-role"->{member.put("payloadMember","different");members.put("repeatmasker/RepeatMasker",member);}default->throw new AssertionError();}
            ledger.put("members",members);assertThrows(IOException.class,()->resources(ledger),mutation);
        }
    }
    @Test void currentResourcesCannotAcquireAnUnreviewedConfigurationDelta()throws Exception{
        var ledger=resourceLedger();ledger.put("working_configuration_delta",Map.of("value","unreviewed"));assertThrows(IOException.class,()->resources(ledger));
    }
    private Map<String,Object> process()throws Exception{
        var l=RepeatProcess.Limits.health();var value=new LinkedHashMap<String,Object>();value.put("status","completed-process-only");value.put("exit_code",0L);value.put("accepted_process_exit_codes",List.of(0L));value.put("process_purpose","strict-zero-exit-process");
        value.put("owned_live_after_cleanup",List.of());value.put("command",List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+l.addressSpaceBytes(),"--cpu="+l.cpuSeconds(),"--fsize="+l.fileBytes(),"--nofile="+l.openFiles(),"--","/usr/bin/perl","original","-nopost"));
        value.put("accounted_roots",List.of(root.resolve("prepared").toString()));value.put("limits",RepeatBundle.object(RepeatBundle.json(l)));value.put("owned_launcher_pid",2L);value.put("elapsed_millis",100L);
        value.put("owned_processes",List.of(Map.of("pid",2L,"startTicks",10L,"parent",1L,"group",2L,"session",2L,"state","S")));
        for(String key:List.of("peak_workspace_bytes","peak_sampled_rss_bytes","peak_processes","peak_threads"))value.put(key,1L);return value;
    }
    private void verifyProcess(Map<String,Object> value)throws Exception{RepeatStageEvidence.verifyProcess(value,List.of("/usr/bin/perl","original","-nopost"),RepeatProcess.Limits.health(),List.of(root.resolve("prepared")));}
    @Test void retainedStrictZeroProcessHasTheExpectedCommandAndCleanup()throws Exception{verifyProcess(process());}
    @Test void libraryInspectionExitOneIsNeverAcceptedAsAnOriginalScientificExit()throws Exception{
        var value=process();value.put("exit_code",1L);value.put("accepted_process_exit_codes",List.of(0L,1L));value.put("process_purpose","library-binding-inspection-only");assertThrows(IOException.class,()->verifyProcess(value));
    }
    @Test void changedScientificArgumentsOrUnaccountedRootsAreRejected()throws Exception{
        var arguments=process();arguments.put("command",List.of("/usr/bin/perl","different"));assertThrows(IOException.class,()->verifyProcess(arguments));
        var roots=process();roots.put("accounted_roots",List.of());assertThrows(IOException.class,()->verifyProcess(roots));
    }
    @Test void liveWorkersUnknownCleanupOrMissingIdentitiesCannotBeAdmitted()throws Exception{
        for(String key:List.of("owned_live_after_cleanup","cleanup_state","owned_processes")){
            var value=process();value.put(key,key.equals("owned_live_after_cleanup")?List.of(99L):key.equals("owned_processes")?List.of():"unknown");assertThrows(IOException.class,()->verifyProcess(value));
        }
    }
    @Test void recordedPeaksMustRespectAllDeclaredResourceCapacities()throws Exception{
        for(String key:List.of("peak_workspace_bytes","peak_sampled_rss_bytes","peak_processes","peak_threads")){var value=process();value.put(key,Long.MAX_VALUE);assertThrows(IOException.class,()->verifyProcess(value));}
    }
    @Test void verificationDoesNotConfuseIntegrityHashTimeWithAWholeProcessWallDeadline()throws Exception{
        var value=process();value.put("elapsed_millis",RepeatProcess.Limits.health().wallMillis()*10);verifyProcess(value);
    }
    @Test void missingPayloadAndUnsupportedProfilesDoNotCreateAnAdmissionHome(){
        Path output=root.resolve("new-runtime");assertThrows(RepeatRuntime.Unavailable.class,()->RepeatRuntime.admit(root.resolve("missing"),RepeatRecipe.CURRENT,null,output,()->{}));assertFalse(Files.exists(output));
        assertThrows(RepeatRuntime.Unavailable.class,()->RepeatRuntime.admit(root.resolve("missing"),"unreported-edition",null,output,()->{}));assertFalse(Files.exists(output));
    }
}
