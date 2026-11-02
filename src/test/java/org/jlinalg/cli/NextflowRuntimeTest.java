/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NextflowRuntimeTest {
    @TempDir Path temp;
    private boolean linux()throws IOException{
        if(System.getProperty("os.name").equals("Linux"))return true;
        assertThrows(NextflowRuntime.Unavailable.class,()->NextflowRuntime.open(temp,"linux-x86_64-nextflow-24.04.4",temp.resolve("missing"),()->{}));return false;
    }
    private static Path home(String version){String value=System.getProperty("jlinalg.nextflow.home."+version);assertNotNull(value,"Supply an actual exact managed home for Linux admission checks");return Path.of(value);}
    private static Path report(String version){String suffix=version.equals("21.04.1")?"21-v2":version.equals("22.10.6")?"22":"24";return home(version).getParent().resolve("report-"+suffix);}
    private static Map<String,Object> mutable(Object value)throws IOException{var result=new LinkedHashMap<String,Object>();for(var row:NextflowFiles.object(value).entrySet())result.put(NextflowFiles.text(row.getKey()),row.getValue());return result;}
    private Path cloneMetadata(String name)throws IOException{
        Path target=Files.createDirectory(temp.resolve(name));Files.copy(report("24.04.4").resolve("manifest.json"),target.resolve("manifest.json"));Files.copy(report("24.04.4").resolve("COMPLETED.json"),target.resolve("COMPLETED.json"));return target;
    }
    private static void updateManifest(Path report,Map<String,Object> value)throws IOException{
        Files.writeString(report.resolve("manifest.json"),NextflowJson.json(value)+"\n");var completion=mutable(NextflowFiles.json(report.resolve("COMPLETED.json"),65536));completion.put("manifest",NextflowFiles.pin(report.resolve("manifest.json"),2097152,()->{}));Files.writeString(report.resolve("COMPLETED.json"),NextflowJson.json(completion)+"\n");
    }
    @Test void bundledProfilesAndProtocolPinsAreExact()throws Exception{
        assertEquals(3,NextflowFiles.object(NextflowRuntime.lock().get("profiles")).size());assertEquals(21170,NextflowRuntime.resource("install_nextflow.py",NextflowRuntime.INSTALLER).length);assertEquals(445,NextflowRuntime.resource("health.nf",NextflowRuntime.HEALTH).length);assertEquals(121,NextflowRuntime.resource("health.config",NextflowRuntime.HEALTH_CONFIG).length);
    }
    @Test void unsupportedEditionIsNeverSilentlyUpgraded(){assertThrows(NextflowRuntime.Unavailable.class,()->NextflowRuntime.open(temp,"linux-x86_64-nextflow-21.04.99",temp,()->{}));}
    @Test void absentExactRuntimeOrSetupIsUnavailable(){assertThrows(NextflowRuntime.Unavailable.class,()->NextflowRuntime.open(temp.resolve("missing"),"linux-x86_64-nextflow-24.04.4",temp.resolve("report"),()->{}));}
    @ParameterizedTest @ValueSource(strings={"21.04.1","22.10.6","24.04.4"})
    void nativeAdmissionVerifiesEachActualCompletedSetup(String version)throws Exception{
        if(!linux())return;var context=NextflowRuntime.open(home(version),"linux-x86_64-nextflow-"+version,report(version),()->{});assertEquals(version,context.version());assertEquals(30,context.helpers().size());assertTrue(context.payload().size()>=400);assertEquals(NextflowFiles.object(NextflowFiles.object(NextflowFiles.json(home(version).resolve(NextflowRuntime.MARKER),2097152)).get("permission_mode_departures")).size(),context.permissionDepartures());context.verify(()->{});
    }
    @Test void changedManifestPinIsAnIntegrityFailure()throws Exception{
        if(!linux())return;Path report=cloneMetadata("changed-pin");var completion=mutable(NextflowFiles.json(report.resolve("COMPLETED.json"),65536));completion.put("manifest",Map.of("bytes",Files.size(report.resolve("manifest.json")),"sha256","0".repeat(64)));Files.writeString(report.resolve("COMPLETED.json"),NextflowJson.json(completion));IOException failure=assertThrows(IOException.class,()->NextflowRuntime.open(home("24.04.4"),"linux-x86_64-nextflow-24.04.4",report,()->{}));assertFalse(failure instanceof NextflowRuntime.Unavailable);assertTrue(failure.getMessage().contains("completion/manifest"));
    }
    @Test void selfConsistentWrongEditionBindingStillFails()throws Exception{
        if(!linux())return;Path report=cloneMetadata("wrong-edition");var manifest=mutable(NextflowFiles.json(report.resolve("manifest.json"),2097152));manifest.put("version","21.04.1");updateManifest(report,manifest);IOException failure=assertThrows(IOException.class,()->NextflowRuntime.open(home("24.04.4"),"linux-x86_64-nextflow-24.04.4",report,()->{}));assertFalse(failure instanceof NextflowRuntime.Unavailable);assertTrue(failure.getMessage().contains("home/profile/version/lock"));
    }
    @Test void differentKernelRequiresAPermittedHealthRecheck()throws Exception{
        if(!linux())return;Path report=cloneMetadata("different-host");var manifest=mutable(NextflowFiles.json(report.resolve("manifest.json"),2097152));var host=mutable(manifest.get("host"));host.put("kernel","not-the-current-kernel");manifest.put("host",host);updateManifest(report,manifest);assertThrows(NextflowRuntime.Unavailable.class,()->NextflowRuntime.open(home("24.04.4"),"linux-x86_64-nextflow-24.04.4",report,()->{}));
    }
    @Test void failedSetupCannotBeAdmittedAsACompletedRuntime()throws Exception{
        if(!linux())return;Path report=Files.createDirectory(temp.resolve("failed-report"));Files.writeString(report.resolve("FAILURE.json"),"{}");IOException failure=assertThrows(IOException.class,()->NextflowRuntime.open(home("24.04.4"),"linux-x86_64-nextflow-24.04.4",report,()->{}));assertFalse(failure instanceof NextflowRuntime.Unavailable);assertTrue(failure.getMessage().contains("Failed Nextflow setup"));
    }
    @Test void changedAdmittedPayloadExpectationFailsBeforeAfterVerification()throws Exception{
        if(!linux())return;var context=NextflowRuntime.open(home("24.04.4"),"linux-x86_64-nextflow-24.04.4",report("24.04.4"),()->{});var payload=new LinkedHashMap<>(context.payload());var original=payload.get("nextflow");payload.put("nextflow",new NextflowFiles.Entry(original.kind(),new NextflowFiles.Pin(original.pin().bytes(),"0".repeat(64)),original.mode(),original.target()));
        var changed=new NextflowRuntime.Context(context.home(),context.report(),context.profile(),context.version(),context.kernel(),payload,context.setup(),context.helpers(),context.permissionDepartures());assertThrows(IOException.class,()->changed.verify(()->{}));
    }
    private NextflowRuntime.Context inertContext(){return new NextflowRuntime.Context(temp,temp,"profile","version","kernel",Map.of(),Map.of(),Map.of(),0);}
    @Test void explicitEnvironmentDoesNotInheritJavaOrProviderOptions()throws Exception{
        var values=inertContext().environment(temp.resolve("nxf-home"),512,2);assertEquals("true",values.get("NXF_OFFLINE"));assertEquals("false",values.get("NXF_PLUGINS_DEFAULT"));assertEquals(temp.resolve("java").toString(),values.get("NXF_JAVA_HOME"));assertFalse(values.containsKey("NXF_VER"));assertFalse(values.containsKey("JAVA_TOOL_OPTIONS"));assertTrue(values.get("NXF_OPTS").contains("-Xmx512m"));
    }
    @Test void invalidRuntimeCapacitiesDoNotBuildAnInvocation(){assertThrows(IOException.class,()->inertContext().environment(temp,0,2));assertThrows(IOException.class,()->inertContext().environment(temp,512,0));assertThrows(IOException.class,()->inertContext().environment(Path.of("relative"),512,2));}
}
