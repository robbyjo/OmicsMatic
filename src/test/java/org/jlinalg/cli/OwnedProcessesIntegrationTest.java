/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Only fixtures launched here are signaled;no PID-only cleanup is used. */
class OwnedProcessesIntegrationTest {
    @TempDir Path temporary;
    @Test void closesVerifiedOwnedChildWhileAnOlderIndependentFixtureSurvives()throws Exception {
        Path foreignReady=temporary.resolve("foreign.ready"),rootReady=temporary.resolve("root.ready"),childReady=temporary.resolve("child.ready"),stop=temporary.resolve("root.stop");
        Process foreign=null,root=null;List<ProcessHandle> owned=List.of();
        try {
            foreign=start("leaf",foreignReady.toString());await(foreignReady,foreign);root=start("root",rootReady.toString(),childReady.toString(),stop.toString());await(rootReady,root);await(childReady,root);
            long childPid=Long.parseLong(Files.readString(childReady).strip());owned=OwnedProcesses.descendants(root).toList();assertTrue(owned.stream().anyMatch(h->h.pid()==childPid));long foreignPid=foreign.pid();assertTrue(owned.stream().noneMatch(h->h.pid()==foreignPid));
            for(ProcessHandle h:owned)h.destroyForcibly();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(owned.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)Thread.sleep(20);
            assertTrue(owned.stream().noneMatch(ProcessHandle::isAlive));assertTrue(foreign.isAlive(),"The independent older fixture must survive owned-child cleanup");
        }finally{
            // Cooperatively ask our root fixture to close its own original
            // Process object,even if descendant discovery/assertions failed.
            Files.writeString(stop,"stop\n");if(root!=null&&!root.waitFor(10,TimeUnit.SECONDS))root.destroyForcibly();
            for(ProcessHandle h:owned)if(h.isAlive())h.destroyForcibly();if(foreign!=null){foreign.destroyForcibly();assertTrue(foreign.waitFor(10,TimeUnit.SECONDS));}
        }
    }
    private Process start(String...args)throws Exception {
        Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");var command=new java.util.ArrayList<>(List.of(java.toString(),"-cp",System.getProperty("java.class.path"),Fixture.class.getName()));command.addAll(List.of(args));
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(temporary.resolve("fixture-"+System.nanoTime()+".log").toFile()).start();
    }
    private static void await(Path ready,Process fixture)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!Files.exists(ready)&&fixture.isAlive()&&System.nanoTime()<deadline)Thread.sleep(20);assertTrue(Files.exists(ready),"Our fixture did not become ready");
    }
    public static final class Fixture {
        private Fixture(){ }
        public static void main(String[]args)throws Exception {
            if(args[0].equals("leaf")){Files.writeString(Path.of(args[1]),Long.toString(ProcessHandle.current().pid()));Thread.sleep(60000);return;}
            Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");Process child=new ProcessBuilder(java.toString(),"-cp",System.getProperty("java.class.path"),Fixture.class.getName(),"leaf",args[2]).redirectErrorStream(true).inheritIO().start();
            try{Files.writeString(Path.of(args[1]),Long.toString(ProcessHandle.current().pid()));long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);while(!Files.exists(Path.of(args[3]))&&System.nanoTime()<deadline)Thread.sleep(20);}
            finally{child.destroyForcibly();child.waitFor(10,TimeUnit.SECONDS);}
        }
    }
}
