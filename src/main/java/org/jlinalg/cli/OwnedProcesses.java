/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Birth-checked traversal of an owned process's immediate child edges.
 * OS parent PIDs alone do not establish ownership after PID reuse. In particular,
 * a global descendants snapshot can include a process older than the new parent.
 * Unverifiable edges are excluded. Returned handles retain their observed birth
 * identity for later liveness/termination checks. This is periodic observation,
 * not an operating-system job object or a guarantee of observing escaped children.
 */
final class OwnedProcesses {
    private OwnedProcesses() { }

    private record Identity(long pid,Instant started) { }

    static Stream<ProcessHandle> descendants(Process ownedRoot) {
        Objects.requireNonNull(ownedRoot,"ownedRoot");
        if(!ownedRoot.isAlive())return Stream.empty();
        return descendants(ownedRoot.toHandle());
    }

    static Stream<ProcessHandle> descendants(ProcessHandle ownedRoot) {
        Objects.requireNonNull(ownedRoot,"ownedRoot");
        ScopedHandle root=ownedRoot instanceof ScopedHandle scoped?scoped:snapshot(ownedRoot,null).orElse(null);
        if(root==null||!root.isAlive())return Stream.empty();
        var result=new ArrayList<ProcessHandle>();var queue=new ArrayDeque<ScopedHandle>();Set<Identity> seen=new HashSet<>();seen.add(root.identity);queue.add(root);
        while(!queue.isEmpty()) {
            ScopedHandle parent=queue.removeFirst();
            for(ScopedHandle child:children(parent))if(seen.add(child.identity)){result.add(child);queue.addLast(child);}
        }
        return List.copyOf(result).stream();
    }

    private static Optional<ScopedHandle> snapshot(ProcessHandle handle,ScopedHandle parent) {
        try {
            if(!handle.isAlive())return Optional.empty();
            Optional<Instant> start=handle.info().startInstant();
            if(start.isEmpty())return Optional.empty();
            ScopedHandle result=new ScopedHandle(handle,new Identity(handle.pid(),start.get()),parent);
            return result.isAlive()?Optional.of(result):Optional.empty();
        }catch(SecurityException exception){return Optional.empty();}
    }

    private static List<ScopedHandle> children(ScopedHandle parent) {
        if(!parent.isAlive())return List.of();var result=new ArrayList<ScopedHandle>();
        // Use immediate children. Never use ProcessHandle.descendants(): its
        // global parent-PID traversal may include pre-existing orphan processes.
        try(var stream=parent.delegate.children()) {
            for(ProcessHandle candidate:stream.toList()) {
                Optional<ScopedHandle> observed=snapshot(candidate,parent);
                if(observed.isEmpty())continue;ScopedHandle child=observed.get();
                if(child.identity.started().isBefore(parent.identity.started()))continue;
                Optional<ProcessHandle> reportedParent=candidate.parent();
                if(reportedParent.isEmpty()||reportedParent.get().pid()!=parent.pid())continue;
                if(!same(reportedParent.get(),parent.identity.started())||!parent.isAlive()||!child.isAlive())continue;
                result.add(child);
            }
        }catch(SecurityException exception){return List.copyOf(result);}
        return List.copyOf(result);
    }

    private static boolean same(ProcessHandle handle,Instant expected) {
        try{return handle.isAlive()&&handle.info().startInstant().filter(expected::equals).isPresent();}
        catch(SecurityException exception){return false;}
    }

    /** Re-resolve a recorded PID only when its exact observed birth still matches. */
    static Optional<ProcessHandle> matching(long pid,Instant started) {
        return ProcessHandle.of(pid).flatMap(handle->matching(handle,started));
    }
    static Optional<ProcessHandle> matching(ProcessHandle candidate,Instant started) {
        Objects.requireNonNull(started,"started");return snapshot(candidate,null).filter(handle->handle.identity.started().equals(started)).map(handle->handle);
    }

    private static final class ScopedHandle implements ProcessHandle {
        private final ProcessHandle delegate;private final Identity identity;private final ScopedHandle parent;
        private ScopedHandle(ProcessHandle delegate,Identity identity,ScopedHandle parent){this.delegate=delegate;this.identity=identity;this.parent=parent;}
        @Override public long pid(){return identity.pid();}
        @Override public boolean isAlive(){return same(delegate,identity.started());}
        @Override public Info info(){return delegate.info();}
        @Override public Optional<ProcessHandle> parent(){return parent!=null&&parent.isAlive()?Optional.of(parent):Optional.empty();}
        @Override public Stream<ProcessHandle> children(){return OwnedProcesses.children(this).stream().map(handle->handle);}
        @Override public Stream<ProcessHandle> descendants(){return OwnedProcesses.descendants(this);}
        @Override public CompletableFuture<ProcessHandle> onExit(){return delegate.onExit().thenApply(handle->this);}
        // The JDK native handle also binds termination to its original start
        // time. Keep that handle;do not resolve this PID again before signaling.
        @Override public boolean destroy(){return isAlive()&&delegate.destroy();}
        @Override public boolean destroyForcibly(){return isAlive()&&delegate.destroyForcibly();}
        @Override public boolean supportsNormalTermination(){return delegate.supportsNormalTermination();}
        @Override public int compareTo(ProcessHandle other){int order=Long.compare(pid(),other.pid());if(order!=0)return order;return other instanceof ScopedHandle scoped?identity.started().compareTo(scoped.identity.started()):0;}
        @Override public boolean equals(Object other){return other instanceof ScopedHandle scoped&&identity.equals(scoped.identity);}
        @Override public int hashCode(){return identity.hashCode();}
        @Override public String toString(){return Long.toString(pid());}
    }
}
