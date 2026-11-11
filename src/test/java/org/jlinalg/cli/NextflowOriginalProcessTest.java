/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.workflow.NextflowTraceAudit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NextflowOriginalProcessTest {
    @TempDir Path temp;
    static Stream<Arguments> cases(){return Stream.of("21.04.1","22.10.6","24.04.4").flatMap(version->Stream.of(Arguments.of(version,false),Arguments.of(version,true)));}
    @ParameterizedTest(name="{0} ignoredFailure={1}") @MethodSource("cases")
    void exactOriginalRunsThroughNativeGuard(String version,boolean ignoredFailure)throws Exception{
        var limits=new NextflowProcess.Limits(120000,8589934592L,120,536870912,4096,536870912,10000,64,1024,2147483648L,50);
        if(!System.getProperty("os.name").equals("Linux")){
            assertThrows(NextflowProcess.Unavailable.class,()->NextflowProcess.executeOwned(List.of("/usr/bin/false"),Map.of(),temp.resolve("process"),temp,limits,()->{},()->{}));return;
        }
        String supplied=System.getProperty("jlinalg.nextflow.home."+version);assertNotNull(supplied,"Explicit exact managed runtime must be supplied; do not abort/skip the original control");
        Path home=Path.of(supplied);assertEquals(home.toRealPath(),home);
        Path inputs=Files.createDirectory(temp.resolve("inputs"));
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/workflow/nextflow/original-engine-controls.zip")){
            assertNotNull(raw);try(var archive=new ZipInputStream(raw)){
                for(var entry=archive.getNextEntry();entry!=null;entry=archive.getNextEntry()){
                    String name=entry.getName();if(name.equals("control.nf")||name.equals("control.config")||name.startsWith("inputs/")){
                        Path destination=temp.resolve(name);assertTrue(destination.normalize().startsWith(temp));Files.write(destination,archive.readNBytes(1048577));
                    }
                }
            }
        }
        assertTrue(Files.isRegularFile(inputs.resolve("a.fa")));
        Path work=temp.resolve("process"),trace=work.resolve("trace.tsv"),output=temp.resolve("published");
        var command=new ArrayList<>(List.of("/usr/bin/bash",home.resolve("nextflow").toString(),"-C",temp.resolve("control.config").toString(),"-log",work.resolve("nextflow.log").toString(),"run",temp.resolve("control.nf").toString(),"-ansi-log","false","-with-trace",trace.toString(),"-work-dir",work.resolve("tasks").toString(),"--inputs",inputs.toString(),"--outdir",output.toString()));
        if(ignoredFailure)command.addAll(List.of("--fail","true","--ignore","true"));
        var env=new HashMap<String,String>();env.putAll(Map.of("PATH","/usr/bin:/bin","LANG","C.UTF-8","LC_ALL","C.UTF-8","NXF_HOME",work.resolve("nxf-home").toString(),"CAPSULE_CACHE_DIR",home.resolve("capsule").toString(),"NXF_JAVA_HOME",home.resolve("java").toString(),"NXF_OFFLINE","true","NXF_DISABLE_CHECK_LATEST","true","NXF_ANSI_LOG","false","NXF_PLUGINS_DEFAULT","false"));
        env.put("NXF_ENABLE_SECRETS","false");env.put("NXF_OPTS","-Xms32m -Xmx512m -XX:ReservedCodeCacheSize=128m -XX:ActiveProcessorCount=2");
        String engine=FollowupSupport.hash(home.resolve("nextflow")),marker=FollowupSupport.hash(home.resolve(".omicsmatic-nextflow.json"));
        NextflowProcess.Check integrity=()->{if(!engine.equals(FollowupSupport.hash(home.resolve("nextflow")))||!marker.equals(FollowupSupport.hash(home.resolve(".omicsmatic-nextflow.json"))))throw new IOException("Exact original runtime changed during controlled execution");};
        var result=NextflowProcess.executeOwned(command,env,work,temp,limits,integrity,()->{});
        assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());
        assertTrue(Files.readString(result.stdout()).contains("version "+version));
        var observed=NextflowTraceAudit.read(trace,4,0,NextflowTraceAudit.Limits.defaults(),()->{});
        long links;try(var paths=Files.walk(work.resolve("tasks"))){links=paths.filter(Files::isSymbolicLink).count();}assertTrue(links>=6,"Actual default Nextflow staging must exercise symlinks");
        if(ignoredFailure){assertFalse(observed.allObservedTasksSuccessful());assertEquals(1,observed.other());assertFalse(Files.exists(output.resolve("counts.tsv")));}
        else{assertTrue(observed.allObservedTasksSuccessful());assertEquals("a\t8\t4\nb\t6\t0\nc\t10\t8\n",Files.readString(output.resolve("counts.tsv"),StandardCharsets.UTF_8));}
    }
}
