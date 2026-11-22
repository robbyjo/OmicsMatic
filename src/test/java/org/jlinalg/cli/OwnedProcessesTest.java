/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Entirely simulated process graphs:these tests never create or signal OS processes. */
class OwnedProcessesTest {
    private static final Instant ZERO=Instant.parse("2026-11-22T00:00:00Z");
    private static Fake process(long pid,long seconds){return new Fake(pid,ZERO.plusSeconds(seconds));}
    private static List<Long> pids(ProcessHandle root){return OwnedProcesses.descendants(root).map(ProcessHandle::pid).toList();}

    @Test void validatesEveryImmediateEdgeAndNeverCallsGlobalDescendants() {
        Fake root=process(1,10),child=process(2,11),grandchild=process(3,12);root.add(child);child.add(grandchild);
        assertEquals(List.of(2L,3L),pids(root));assertEquals(0,root.globalCalls+child.globalCalls+grandchild.globalCalls);
    }
    @Test void rejectsTheObservedOlderOrphanEvenWhenItsRecordedParentPIDMatches() {
        Fake root=process(21480,200000);Fake foreign=process(17380,0);root.add(foreign);
        OwnedProcesses.descendants(root).forEach(ProcessHandle::destroyForcibly);assertEquals(0,foreign.signals);assertTrue(foreign.alive);
    }
    @Test void rejectsGrandchildOlderThanItsImmediateParentEvenIfNewerThanRoot() {
        Fake root=process(1,10),child=process(2,30),foreign=process(3,20);root.add(child);child.add(foreign);
        assertEquals(List.of(2L),pids(root));assertEquals(0,foreign.signals);
    }
    @Test void rejectsMatchingParentPIDWithDifferentParentBirth() {
        Fake root=process(1,10),child=process(2,20),replacement=process(1,19);root.children.add(child);child.parent=replacement;
        assertTrue(pids(root).isEmpty());
    }
    @Test void rejectsUnrelatedParentPID() {
        Fake root=process(1,10),child=process(2,20),foreign=process(9,9);root.children.add(child);child.parent=foreign;
        assertTrue(pids(root).isEmpty());
    }
    @Test void cannotDiscoverFromAnExitedRoot() {
        Fake root=process(1,10),child=process(2,20);root.add(child);root.alive=false;assertTrue(pids(root).isEmpty());
    }
    @Test void rejectsUnknownRootAndChildCreationTimes() {
        Fake root=process(1,10),child=process(2,20);root.add(child);root.started=null;assertTrue(pids(root).isEmpty());
        root.started=ZERO.plusSeconds(10);child.started=null;assertTrue(pids(root).isEmpty());
    }
    @Test void retainedHandleDoesNotSignalAReusedPID() {
        Fake root=process(1,10),child=process(2,20);root.add(child);ProcessHandle held=OwnedProcesses.descendants(root).findFirst().orElseThrow();child.started=ZERO.plusSeconds(40);
        assertFalse(held.isAlive());assertFalse(held.destroy());assertFalse(held.destroyForcibly());assertEquals(0,child.signals);
    }
    @Test void rejectsParentReuseDuringEnumeration() {
        Fake root=process(1,10),child=process(2,20);root.add(child);root.whenEnumerated=()->root.started=ZERO.plusSeconds(15);assertTrue(pids(root).isEmpty());
    }
    @Test void retainedOwnedChildCanBeClosedAfterItsRootExits() {
        Fake root=process(1,10),child=process(2,20);root.add(child);ProcessHandle held=OwnedProcesses.descendants(root).findFirst().orElseThrow();root.alive=false;
        assertTrue(held.destroyForcibly());assertEquals(1,child.signals);
    }
    @Test void discoversNewGrandchildrenFromARetainedOwnedChildAfterRootExit() {
        Fake root=process(1,10),child=process(2,20),grandchild=process(3,30);root.add(child);ProcessHandle held=OwnedProcesses.descendants(root).findFirst().orElseThrow();root.alive=false;child.add(grandchild);
        assertEquals(List.of(3L),pids(held));
    }
    @Test void matchingRequiresExactRecordedBirth() {
        Fake candidate=process(7,20);assertTrue(OwnedProcesses.matching(candidate,ZERO.plusSeconds(19)).isEmpty());
        ProcessHandle held=OwnedProcesses.matching(candidate,ZERO.plusSeconds(20)).orElseThrow();candidate.started=ZERO.plusSeconds(21);assertFalse(held.destroyForcibly());assertEquals(0,candidate.signals);
    }
    @Test void duplicateAndCyclicRecordsCannotLoopOrIncludeTheRoot() {
        Fake root=process(1,10),child=process(2,10);root.add(child);root.children.add(child);child.children.add(root);root.parent=child;assertEquals(List.of(2L),pids(root));
    }

    private static final class Fake implements ProcessHandle {
        final long id;Instant started;boolean alive=true;int signals,globalCalls;Fake parent;final List<ProcessHandle> children=new ArrayList<>();Runnable whenEnumerated=()->{};
        Fake(long id,Instant started){this.id=id;this.started=started;}
        void add(Fake child){children.add(child);child.parent=this;}
        @Override public long pid(){return id;}
        @Override public boolean isAlive(){return alive;}
        @Override public Optional<ProcessHandle> parent(){return Optional.ofNullable(parent);}
        @Override public Stream<ProcessHandle> children(){whenEnumerated.run();return List.copyOf(children).stream();}
        @Override public Stream<ProcessHandle> descendants(){globalCalls++;throw new AssertionError("Global descendants query must not be used");}
        @Override public Info info(){return new Info(){
            @Override public Optional<String> command(){return Optional.of("simulated-process");}
            @Override public Optional<String> commandLine(){return Optional.empty();}
            @Override public Optional<String[]> arguments(){return Optional.empty();}
            @Override public Optional<Instant> startInstant(){return Optional.ofNullable(started);}
            @Override public Optional<Duration> totalCpuDuration(){return Optional.empty();}
            @Override public Optional<String> user(){return Optional.empty();}
        };}
        @Override public CompletableFuture<ProcessHandle> onExit(){return CompletableFuture.completedFuture(this);}
        @Override public boolean supportsNormalTermination(){return true;}
        @Override public boolean destroy(){signals++;alive=false;return true;}
        @Override public boolean destroyForcibly(){return destroy();}
        @Override public int compareTo(ProcessHandle other){return Long.compare(id,other.pid());}
    }
}
