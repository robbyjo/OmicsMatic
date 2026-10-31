/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FavorRuntimeFingerprintTest {
    private static final String R="/opt/private-favor",JAVA="/opt/java21",HASH="a".repeat(64),KERNEL="6.test-kernel";
    private static final FavorRuntimeFingerprint.Limits LIMITS=new FavorRuntimeFingerprint.Limits(1048576,1000,100,100,1048576,16777216);
    private static Map<String,Object> row(Object... values) {
        var m=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;
    }
    private static Map<String,Object> object(Object value) {
        if(!(value instanceof Map<?,?> map))throw new AssertionError("Expected fixture object");var result=new LinkedHashMap<String,Object>();for(var e:map.entrySet())result.put((String)e.getKey(),e.getValue());return result;
    }
    private static Map<String,Object> child(Map<String,Object> parent,String key) {
        var result=object(parent.get(key));parent.put(key,result);return result;
    }
    private static Map<String,Object> file(String hash) {return row("kind","file","mode",493,"bytes",1,"sha256",hash);}
    private static void add(Map<String,Object> tree,String name,Map<String,Object> value) {
        int slash=name.lastIndexOf('/');if(slash>=0){String parent=name.substring(0,slash);if(!tree.containsKey(parent))add(tree,parent,row("kind","directory","mode",493));}tree.put(name,value);
    }
    private static Map<String,Object> binding(String path,String real,String resolution,List<String> roles) {
        return row("library","libsupport.so","path",path,"real_path",real,"bytes",1,"sha256",HASH,"resolution",resolution,"standalone_ldd_path",resolution.equals("ldd")?path:"not found","observed_process_roles",roles);
    }
    private static Map<String,Object> loader(String context,String target,boolean fixed,List<?> bindings) {
        return row("context",context,"object",target,"static",fixed,"bindings",bindings,"trace",AdmixtureInputs.digest((context+"\n"+target).getBytes(StandardCharsets.UTF_8)));
    }
    private static Map<String,Object> fixture()throws Exception {
        Map<String,Object> policy;
        try(var in=FavorRuntimeFingerprintTest.class.getResourceAsStream("/org/jlinalg/omics/favor-runtime-policy.json")){assertNotNull(in);policy=object(AdmixtureInputs.decode(in.readAllBytes()));}
        var rt=new LinkedHashMap<String,Object>();for(String name:List.of("bin/Rscript","bin/xsv","lib/R/bin/exec/R","lib/libR.so","lib/libsupport.so"))add(rt,name,file(name.equals("bin/xsv")?FavorXsvSupervisor.XSV_SHA256:HASH));
        var jt=new LinkedHashMap<String,Object>();add(jt,"bin/java",file(HASH));
        var host=new LinkedHashMap<String,Object>();for(String name:List.of("/bin/sh","/bin/bash","/usr/bin/mkdir","/usr/bin/ldd","/etc/ld.so.cache","/etc/ld.so.conf"))host.put(name,row("real_path",name,"bytes",1,"sha256",HASH));
        var packages=new ArrayList<Map<String,Object>>();for(var e:object(policy.get("R_packages")).entrySet())packages.add(row("name",e.getKey(),"version",e.getValue(),"path",R+"/lib/R/library/"+e.getKey()));
        var probe=row("status","loaded-and-small-IO-smoke-only","R","R version 4.4.3 (2025-02-28)","R_home",R+"/lib/R","loader_path","","tools",row("mkdir","/usr/bin/mkdir","sh","/bin/sh"),"library_paths",List.of(R+"/lib/R/library","/owned/empty-library"),"packages",packages,"dll_paths",List.of("base",R+"/lib/libR.so"),"mapped_paths",List.of(R+"/lib/libR.so",R+"/lib/libsupport.so"),"host",row("sysname","Linux","release",KERNEL,"machine","x86_64"));
        var jp=row("fields",row("version","21.0.4","home",JAVA,"kernel",KERNEL),"mapped_paths",List.of(JAVA+"/bin/java","/tmp/hsperfdata_owned/123"));
        var loaders=row("R-child\n"+R+"/lib/libR.so",loader("R-child",R+"/lib/libR.so",false,List.of(binding(R+"/lib/libsupport.so",R+"/lib/libsupport.so","observed-already-loaded",List.of("R")))),"clean\n/bin/bash",loader("clean","/bin/bash",false,List.of(binding(R+"/lib/../lib/libsupport.so",R+"/lib/libsupport.so","ldd",List.of()))));
        return row("schema_version",1,"status","inventory-only-not-admitted","profile",FavorRuntimeFingerprint.PROFILE,"roots",row("r",R,"java",JAVA),"trees",row("r",rt,"java",jt),"host_files",host,"loader_bindings",loaders,"required_absent_paths",List.of("/etc/ld.so.preload"),"unexpanded_external_directories",List.of(),"declared_broken_symlinks",List.of(),"R_probe",probe,"java_probes",row("clean",jp,"R-child",object(jp)),"conda_packages",policy.get("conda_packages"),"conda_packages_count",154,"conda_artifacts_match_pinned_build_lock",true,"build_lock_sha256",policy.get("build_lock_sha256"),"GenomeInfoDbData_archive_sha256",policy.get("GenomeInfoDbData_archive_sha256"));
    }
    private static byte[] bytes(Map<String,Object> m) {return RegenieRuntime.json(m).getBytes(StandardCharsets.UTF_8);}
    private static FavorRuntimeFingerprint.Spec parse(Map<String,Object> m)throws IOException {return FavorRuntimeFingerprint.parse(bytes(m),LIMITS);}
    private static Map<String,Object> rTree(Map<String,Object> m) {return child(child(m,"trees"),"r");}
    private static Map<String,Object> rLoader(Map<String,Object> m) {return child(child(m,"loader_bindings"),"R-child\n"+R+"/lib/libR.so");}
    private static void rBindings(Map<String,Object> m,List<?> bindings) {rLoader(m).put("bindings",bindings);}

    @Test void portableParseRetainsExactAliasesAndDoesNotAssertScientificAdmission()throws Exception {
        var s=parse(fixture());assertEquals(2,s.roots().size());assertEquals(2,s.loaders().size());assertEquals(KERNEL,s.kernel());assertTrue(s.loaders().stream().flatMap(l->l.bindings().stream()).anyMatch(b->b.file().path().contains("/../")));assertEquals(List.of("/etc/ld.so.preload"),s.absent());
    }
    @Test void aNewExecCannotInheritTheRProcessLoadedLibraryBindings()throws Exception {
        var m=fixture();child(child(m,"loader_bindings"),"clean\n/bin/bash").put("bindings",List.of(binding(R+"/lib/libsupport.so",R+"/lib/libsupport.so","observed-already-loaded",List.of("R"))));assertTrue(assertThrows(IOException.class,()->parse(m)).getMessage().contains("fresh execs"));
    }
    @Test void inferredBindingMustBeMappedByTheSameObservedProcess()throws Exception {
        var m=fixture();child(m,"R_probe").put("mapped_paths",List.of(R+"/lib/libR.so"));assertTrue(assertThrows(IOException.class,()->parse(m)).getMessage().contains("same observed process"));
    }
    @Test void inferredBindingsNeedAnObservedProcessAndTheCorrectJavaContext()throws Exception {
        var m=fixture();rBindings(m,List.of(binding(R+"/lib/libsupport.so",R+"/lib/libsupport.so","observed-already-loaded",List.of())));assertThrows(IOException.class,()->parse(m));var other=fixture();rBindings(other,List.of(binding(R+"/lib/libsupport.so",R+"/lib/libsupport.so","observed-already-loaded",List.of("java-clean"))));assertThrows(IOException.class,()->parse(other));
    }
    @Test void repeatedConsistentNeededEntriesAreRetained()throws Exception {
        var m=fixture();var b=binding(R+"/lib/libsupport.so",R+"/lib/libsupport.so","observed-already-loaded",List.of("R"));rBindings(m,List.of(b,object(b)));assertEquals(2,parse(m).loaders().get(0).bindings().size());
    }
    @Test void conflictingRepeatedLibrariesCannotHideBehindKnownFileIdentities()throws Exception {
        var m=fixture();rBindings(m,List.of(binding(R+"/lib/libsupport.so",R+"/lib/libsupport.so","observed-already-loaded",List.of("R")),binding(R+"/lib/libR.so",R+"/lib/libR.so","observed-already-loaded",List.of("R"))));assertTrue(assertThrows(IOException.class,()->parse(m)).getMessage().contains("Conflicting repeated"));
    }
    @Test void standaloneLddResolutionCannotClaimDifferentOrMissingAliases()throws Exception {
        for(String path:List.of("not found","/different/libsupport.so")){var m=fixture();var b=binding(R+"/lib/libsupport.so",R+"/lib/libsupport.so","ldd",List.of());b.put("standalone_ldd_path",path);rBindings(m,List.of(b));assertThrows(IOException.class,()->parse(m));}
    }
    @Test void unknownLoaderFilesAndWrongObjectContextOrTraceAreRejected()throws Exception {
        var m=fixture();rBindings(m,List.of(binding("/unrecorded/lib.so","/unrecorded/lib.so","ldd",List.of())));assertThrows(IOException.class,()->parse(m));for(String field:List.of("context","object","trace")){var other=fixture();rLoader(other).put(field,field.equals("trace")?"0".repeat(64):field.equals("object")?"/unrecorded/file":"clean");assertThrows(IOException.class,()->parse(other));}
    }
    @Test void staticObjectsMustNotHaveBindingsAndDynamicObjectsMustHaveBindings()throws Exception {
        var m=fixture();rLoader(m).put("static",true);assertThrows(IOException.class,()->parse(m));var empty=fixture();rBindings(empty,List.of());assertThrows(IOException.class,()->parse(empty));rLoader(empty).put("static",true);assertTrue(parse(empty).loaders().get(0).staticallyLinked());
    }
    @Test void criticalProgramsMustHaveRecordedIdentitiesIncludingPinnedXsv()throws Exception {
        for(String path:List.of("bin/Rscript","bin/xsv","lib/R/bin/exec/R")){var m=fixture();rTree(m).remove(path);assertTrue(assertThrows(IOException.class,()->parse(m)).getMessage().contains("program identity"));}var wrong=fixture();child(rTree(wrong),"bin/xsv").put("sha256",HASH);assertTrue(assertThrows(IOException.class,()->parse(wrong)).getMessage().contains("xsv"));var j=fixture();child(child(j,"trees"),"java").remove("bin/java");assertThrows(IOException.class,()->parse(j));
    }
    @Test void pinnedCondaArtifactsCannotBeChangedOmittedOrRepeated()throws Exception {
        var original=fixture();var packages=new ArrayList<Object>((List<?>)original.get("conda_packages"));var changed=object(packages.get(0));changed.put("sha256","0".repeat(64));packages.set(0,changed);original.put("conda_packages",packages);assertThrows(IOException.class,()->parse(original));var missing=fixture();missing.put("conda_packages",packages.subList(1,packages.size()));assertThrows(IOException.class,()->parse(missing));var repeated=fixture();repeated.put("conda_packages",List.of(packages.get(1),packages.get(1)));assertThrows(IOException.class,()->parse(repeated));
    }
    @Test void lockDataArchiveProfileAndInventoryStatusAreExact()throws Exception {
        for(String field:List.of("build_lock_sha256","GenomeInfoDbData_archive_sha256","profile","status","schema_version","conda_artifacts_match_pinned_build_lock","conda_packages_count")){var m=fixture();m.put(field,field.equals("schema_version")?2:field.equals("conda_packages_count")?153:field.equals("conda_artifacts_match_pinned_build_lock")?false:field.endsWith("sha256")?"0".repeat(64):"different");assertThrows(IOException.class,()->parse(m));}
    }
    @Test void privateRVersionHomePackageVersionsAndLocationsAreEnforced()throws Exception {
        for(String field:List.of("R","R_home","status")){var m=fixture();child(m,"R_probe").put(field,"wrong");assertThrows(IOException.class,()->parse(m));}for(String field:List.of("name","version","path")){var m=fixture();var p=child(m,"R_probe");var rows=new ArrayList<Object>((List<?>)p.get("packages"));var first=object(rows.get(0));first.put(field,"wrong");rows.set(0,first);p.put("packages",rows);assertThrows(IOException.class,()->parse(m));}
    }
    @Test void externalRPackageAndLoaderPathsCannotBeAdmitted()throws Exception {
        for(List<String> paths:List.of(List.<String>of(),List.of(R+"/lib/R/library","/external/library"),List.of("/owned/empty-library"))){var m=fixture();child(m,"R_probe").put("library_paths",paths);assertThrows(IOException.class,()->parse(m));}for(String path:List.of("/usr/lib",R+"/lib:",R+"/../lib")){var m=fixture();child(m,"R_probe").put("loader_path",path);assertThrows(IOException.class,()->parse(m));}
    }
    @Test void loadedRBasePseudoEntryIsAllowedButUnknownDllFilesAreNot()throws Exception {
        parse(fixture());var m=fixture();child(m,"R_probe").put("dll_paths",List.of("base","/unrecorded/dll.so"));assertThrows(IOException.class,()->parse(m));var other=fixture();child(child(other,"R_probe"),"tools").put("sh","/unrecorded/sh");assertThrows(IOException.class,()->parse(other));
    }
    @Test void javaVersionHomeAndKernelAreBoundToTheHost()throws Exception {
        for(String field:List.of("version","home","kernel")){var m=fixture();child(child(child(m,"java_probes"),"clean"),"fields").put(field,"different");assertThrows(IOException.class,()->parse(m));}for(String field:List.of("sysname","machine")){var m=fixture();child(child(m,"R_probe"),"host").put(field,"different");assertThrows(IOException.class,()->parse(m));}
    }
    @Test void processMapsCannotBeEmptyRelativeOrDeleted()throws Exception {
        for(List<String> paths:List.of(List.<String>of(),List.of("relative"),List.of("/a/lib.so (deleted)"))){var m=fixture();child(m,"R_probe").put("mapped_paths",paths);assertThrows(IOException.class,()->parse(m));}
    }
    @Test void completeHostAndDirectoryClosureCannotBeOmitted()throws Exception {
        var m=fixture();child(m,"host_files").remove("/bin/sh");assertThrows(IOException.class,()->parse(m));var external=fixture();external.put("unexpanded_external_directories",List.of("/other"));assertThrows(IOException.class,()->parse(external));var preload=fixture();preload.put("required_absent_paths",List.of());assertThrows(IOException.class,()->parse(preload));
    }
    @Test void treePopulationParentsPathsKindsAndCountsAreStrict()throws Exception {
        for(String path:List.of("/absolute","../escape","lib//bad","lib/./bad","lib/back\\slash")){var m=fixture();rTree(m).put(path,file(HASH));assertThrows(IOException.class,()->parse(m));}var parent=fixture();rTree(parent).remove("lib/R/bin");assertThrows(IOException.class,()->parse(parent));for(Object mode:List.of(-1,4096,1.5,"493")){var m=fixture();child(rTree(m),"bin/Rscript").put("mode",mode);assertThrows(IOException.class,()->parse(m));}var kind=fixture();child(rTree(kind),"bin/Rscript").put("kind","socket");assertThrows(IOException.class,()->parse(kind));
    }
    @Test void rootBindingsCannotBeRelativeTraversedOrMismatchedWithTrees()throws Exception {
        for(String path:List.of("relative","/opt/../favor","/opt//favor","/opt/favor/")){var m=fixture();child(m,"roots").put("r",path);assertThrows(IOException.class,()->parse(m));}var m=fixture();child(m,"trees").put("extra",row());assertThrows(IOException.class,()->parse(m));
    }
    @Test void documentationLinkExceptionRequiresItsExactDeclaration()throws Exception {
        var m=fixture();var tree=child(child(m,"trees"),"java");add(tree,"lib/src.zip",row("kind","symlink","mode",511,"target","../../openjdk-21/src.zip","resolved","/opt/openjdk-21/lib/src.zip","target_kind","missing"));assertThrows(IOException.class,()->parse(m));m.put("declared_broken_symlinks",List.of(row("root","java","path","lib/src.zip","target","../../openjdk-21/src.zip")));parse(m);var extra=fixture();add(rTree(extra),"lib/missing.so",row("kind","symlink","mode",511,"target","absent","resolved",R+"/lib/absent","target_kind","missing"));assertThrows(IOException.class,()->parse(extra));
    }
    @Test void conflictingAliasOrRealFileRecordsCannotOverrideAnIdentity()throws Exception {
        var m=fixture();child(m,"host_files").put(R+"/lib/libR.so",row("real_path",R+"/lib/libR.so","bytes",2,"sha256",HASH));assertThrows(IOException.class,()->parse(m));
    }
    @Test void unknownAndMissingFieldsAndMalformedInputAreRejected()throws Exception {
        var m=fixture();m.put("unexpected",true);assertThrows(IOException.class,()->parse(m));var missing=fixture();missing.remove("java_probes");assertThrows(IOException.class,()->parse(missing));var nested=fixture();child(nested,"R_probe").put("unexpected",true);assertThrows(IOException.class,()->parse(nested));assertThrows(IOException.class,()->FavorRuntimeFingerprint.parse(new byte[]{(byte)0xff},LIMITS));assertThrows(IOException.class,()->FavorRuntimeFingerprint.parse("{\"schema_version\":1,\"schema_version\":2}".getBytes(StandardCharsets.UTF_8),LIMITS));
    }
    @Test void byteEntryHostAndLoaderCapacitiesAreEnforcedBeforeAnalysis()throws Exception {
        byte[] b=bytes(fixture());for(var limits:List.of(new FavorRuntimeFingerprint.Limits(10,1000,100,100,1048576,16777216),new FavorRuntimeFingerprint.Limits(1048576,1,100,100,1048576,16777216),new FavorRuntimeFingerprint.Limits(1048576,1000,1,100,1048576,16777216),new FavorRuntimeFingerprint.Limits(1048576,1000,100,1,1048576,16777216),new FavorRuntimeFingerprint.Limits(1048576,1000,100,100,1048576,1)))assertThrows(IOException.class,()->FavorRuntimeFingerprint.parse(b,limits));var m=fixture();child(rTree(m),"bin/Rscript").put("bytes",1048577);assertThrows(IOException.class,()->parse(m));
    }
    @Test void invalidPinsAndUnavailableRuntimesNeverReturnAnAdmittedObject() {
        assertThrows(IOException.class,()->FavorRuntimeFingerprint.inspect(Path.of("missing-inventory.json"),"bad",LIMITS,()->{}));var error=assertThrows(FavorRuntimeFingerprint.Unavailable.class,()->FavorRuntimeFingerprint.inspect(Path.of("missing-inventory.json"),HASH,LIMITS,()->{}));assertTrue(error.getMessage().contains("Required action"));assertTrue(error.getMessage().contains("skip")||error.getMessage().contains("Skip"));
    }
    @Test void collectionsAreImmutableAndInvalidCapacitiesFailEarly()throws Exception {
        var s=parse(fixture());assertThrows(UnsupportedOperationException.class,()->s.roots().clear());assertThrows(UnsupportedOperationException.class,()->s.roots().get("r").members().clear());assertThrows(UnsupportedOperationException.class,()->s.loaders().get(0).bindings().clear());assertThrows(UnsupportedOperationException.class,()->s.maps().get("R").clear());assertThrows(IllegalArgumentException.class,()->new FavorRuntimeFingerprint.Limits(0,1,1,1,1,1));assertThrows(IllegalArgumentException.class,()->new FavorRuntimeFingerprint.Limits(1,1000001,1,1,1,1));assertThrows(IllegalArgumentException.class,()->new FavorRuntimeFingerprint.Limits(1,1,10001,1,1,1));assertThrows(IllegalArgumentException.class,()->new FavorRuntimeFingerprint.Limits(1,1,1,10001,1,1));
    }
}
