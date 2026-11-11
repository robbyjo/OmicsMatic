/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Recheck an explicitly pinned, already observed local build before fresh original controls.
 * This development driver performs no new compilation and is not a public admission command. */
public final class ResumeBuiltRuntimeProtocol {
    private ResumeBuiltRuntimeProtocol() { }
    /**
     * @param args release, prior local build home, explicit previously observed receipt SHA256, exact library directory, NEW runtime home
     * @throws Exception changed or unreviewed receipt, source graph, evidence or original runtime controls
     */
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("RELEASE BUILD_HOME EXPECTED_RECEIPT_SHA256 LIBRARY_DIRECTORY NEW_RUNTIME_HOME");
        String version=args[0],expected=args[2];Path home=Path.of(args[1]),library=Path.of(args[3]),runtime=Path.of(args[4]);
        RsemPayload.originalPath(home);RsemPayload.originalPath(runtime);
        if(!home.toRealPath().equals(home)||!expected.matches("[0-9a-f]{64}"))throw new IOException("Require a canonical prior build and explicit observed receipt hash");
        byte[] raw=AdmixtureInputs.raw(home.resolve("build.json"),67108864);
        if(!AdmixtureInputs.digest(raw).equals(expected))throw new IOException("Prior local build receipt differs from the explicitly observed pin");
        Map<?,?> record=RepeatBundle.object(raw);var profile=RsemPayload.profiles().get(version);
        if(profile==null||!"COMPLETED_EXACT_ORIGINAL_SOURCE_BUILD".equals(record.get("status"))
                ||!RsemPayload.LOCK.equals(record.get("source_profile_lock_sha256"))||!Boolean.TRUE.equals(record.get("source_to_build_provenance_verified")))
            throw new IOException("Prior receipt does not identify a completed exact source build");
        Map<?,?> sourceProfile=(Map<?,?>)record.get("profile"),tools=(Map<?,?>)record.get("tools");
        if(!version.equals(sourceProfile.get("version"))||!profile.commit().equals(sourceProfile.get("commit"))
                ||!profile.archiveSha256().equals(sourceProfile.get("archiveSha256"))||!List.of(library.toString()).equals(tools.get("libraries")))
            throw new IOException("Prior build profile/library declaration differs");
        Path source=home.resolve("RSEM-"+profile.commit()),decoder=home.resolve("outputs/omicsmatic-rsem-decode");
        if(!RepeatInputs.hash(home.resolve("inputs/original-source.tar.gz"),67108864,()->{}).equals(profile.archiveSha256()))throw new IOException("Retained exact source archive changed");
        Path manifest=runtime.resolveSibling(runtime.getFileName()+"-source-reverification.jsonl");
        var graph=RsemFileGraph.capture(List.of(source),List.of(decoder,home.resolve("inputs/decoder.cpp"),home.resolve("inputs/original-source.tar.gz")),
                RsemBuild.Limits.defaults().compiler(),manifest,()->{});
        Map<?,?> recordedGraph=(Map<?,?>)record.get("source_graph");
        if(!graph.sha256().equals(recordedGraph.get("sha256")))throw new IOException("Complete current compiled source/decoder graph differs from its observed build receipt");
        var evidence=new TreeMap<String,String>();
        for(var entry:((Map<?,?>)record.get("evidence")).entrySet()){
            if(!(entry.getKey() instanceof String name)||!(entry.getValue() instanceof String hash)||!hash.matches("[0-9a-f]{64}")
                    ||name.startsWith("/")||name.contains("\\")||java.util.Arrays.stream(name.split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals(".."))
                    ||!home.resolve(name).toRealPath().equals(home.resolve(name)))throw new IOException("Unreviewed retained build evidence path");
            evidence.put(name,hash);
        }
        var context=new RsemBuild.Context(home,profile,source,decoder,List.of(library),expected,graph,evidence);context.verify(()->{});
        System.out.println(version+" reverified explicit prior local build; no new compilation performed");
        BoundRuntimeProtocol.runBuilt(context,runtime);context.verify(()->{});
        Files.write(runtime.resolve("reloaded-source-build.json"),RsemJson.bytes(Map.of("status","verified-explicit-prior-local-build-receipt-and-complete-current-source-graph",
                "source_build_home",home,"source_build_receipt_sha256",expected,"source_graph_sha256",graph.sha256(),
                "source_reverification_manifest",manifest,"fresh_compilation_performed",false,"scientific_runtime_admitted",false,"public_fitting_workflow_available",false)),StandardOpenOption.CREATE_NEW);
    }
}
