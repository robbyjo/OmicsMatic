/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomtomRuntimeTest {
    @TempDir Path temporary;
    @Test void nativeHostEitherBindsCpuProcessIdentityOrReturnsNamedUnsupportedPlatform()throws Exception {
        if(System.getProperty("os.name").equals("Linux")){var first=MemeNativeHost.read();assertEquals(first,MemeNativeHost.read());assertTrue(first.keySet().containsAll(java.util.Set.of("cpu_profile_sha256","Uid:","Gid:","Groups:","Cpus_allowed_list:","kernel")));}
        else{var error=assertThrows(TomtomRuntime.Unavailable.class,MemeNativeHost::read);assertTrue(error.getMessage().contains("skip dependent execution"));}
    }
    @Test void unsupportedProfileOrPlatformNeverCreatesAnAdmissionArtifact()throws Exception {
        Path output=temporary.resolve("not-created");assertThrows(IOException.class,()->TomtomRuntime.admit("unsupported",TomtomRecipe.Mode.TEXT,temporary.resolve("home"),temporary,output,TomtomRuntime.Limits.defaults(),()->{}));assertFalse(Files.exists(output));
    }
    @Test void completeAdmissionLimitsRejectInvalidImageCountsAndDeadline() {
        var base=TomtomRuntime.Limits.defaults();assertThrows(IllegalArgumentException.class,()->new TomtomRuntime.Limits(base.host(),base.evidence(),base.process(),0,base.admissionMillis()));assertThrows(IllegalArgumentException.class,()->new TomtomRuntime.Limits(base.host(),base.evidence(),base.process(),base.images(),0));
    }
    @Test void originalHealthResourcesRetainWholeIndependentInputAndTwoNinePairReferenceFamilies()throws Exception {
        var lock=MemeRuntimeFiles.lock();var pin=NextflowFiles.fingerprint(lock.get("health_reference"),2097152);var health=NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(MemeRuntimeFiles.resource(MemeRuntimeFiles.RESOURCE+"health-reference.json",pin))));var cases=NextflowFiles.list(health.get("cases"));assertEquals(2,cases.size());
        for(Object value:cases){var rows=NextflowFiles.list(NextflowFiles.object(value).get("rows"));assertEquals(9,rows.size());for(Object row:rows)assertEquals(10,NextflowFiles.strings(row).size());}
        assertEquals(684,MemeRuntimeFiles.resource(MemeRuntimeFiles.RESOURCE+"health-motifs.meme",NextflowFiles.fingerprint(lock.get("health_motifs"),2097152)).length);
    }
}
