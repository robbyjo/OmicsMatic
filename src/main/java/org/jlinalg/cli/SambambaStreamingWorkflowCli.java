/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.jlinalg.alignment.SambambaStreamingBamAudit;
import static org.jlinalg.cli.SambambaStreamingWorkflowFiles.*;

/** Original Sambamba marking/removal in a Linux JVM, with captured-input replay. */
public final class SambambaStreamingWorkflowCli {
    static final String NOTICE="""
        # Warnings and required actions

        Streaming original Sambamba Linux x86-64 profile v2. Supply the exact
        permitted0.6.6 or0.8.2 executable and all20 request fields. No software is
        installed during analysis. Missing runtime/helper/library: SKIP3, skip
        dependent analysis, supply the named permitted prerequisite and retry NEW
        output. A wrong build or edition is FAIL2; no substitution is admitted.

        Marking and optional removal execute original software on the same
        captured input. Native Java checks all BGZF/BAM framing, raw records,
        headers and retained removal records with bounded streaming memory.
        It records transitions and source-defined metrics, not an independent
        duplicate classification. VERIFY genuinely reruns the original classifier
        and compares complete records and all header values except each checked
        exact command string. A checksum or saved native audit alone is insufficient.

        All six audit capacities are explicit: compressed/decoded bytes, records,
        per-record/header/dictionary bytes. Counts are64-bit. Memory depends on
        record/header/dictionary bounds and JVM needs, not total record count.
        Decision rows stream to disk; partial ledgers are provisional until full
        success. Never truncate data or split duplicate families to fit a limit.
        Standard coordinate-sorted short-read BAM only; long-CIGAR CG and
        nonstandard auxiliary types remain unsupported and are refused.

        All files retain raw values/representations. Original0.8.2 writes VN:1.0;
        preserve that value with its actual binary identity. Missing quality255
        is not zero. Existing secondary/supplementary duplicate flags persist;
        primary flags are recomputed. This is not UMI-aware marking. Original
        tie/spill behavior remains original. P110/P198 author flags/builds remain
        incompletely reported. No paper-specific parameters are inferred.

        Generated BAI files are collateral without independent qualification.
        Use a separately qualified indexer before index-dependent analysis.
        Integrity/replay is not authenticity, a reproducible source build,
        biological calibration or whole-tool/paper qualification. This profile
        alone adds no catalog credit; the full250-paper objective remains open.

        wall_ms applies per original mark/remove, including startup/PID recording.
        Metadata probes have fixed10-second/512-MiB bounds. prlimit enforces
        child RLIMIT_AS; setpriv requests parent-death KILL. Work bytes/entries
        and RSS are sampled, not an OS disk quota or total JVM memory limit.
        No network/filesystem isolation is claimed. Cancel the recorded Linux
        JVM; Windows WSL-relay cancellation is not qualified by this notice.

        Require COMPLETED.properties and successful genuine-runtime VERIFY.
        FAIL2 admits no result: read FAILURE.txt, WARNINGS.md and phase consoles;
        retain raw outputs/errors and correct the named cause before retrying NEW
        output. Late completion markers are invalidated. Existing bundles are
        never overwritten or repaired. Use the original v1 CLI/build for v1
        bundles; profiles are distinct and their identities are not refreshed.
        """;
    private SambambaStreamingWorkflowCli(){ }
    /** Standalone entry point in the development executable JAR. */
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    /** Execute or verify, returning0, SKIP3 or FAIL2. */
    public static int run(String[] args,PrintStream output,PrintStream error){
        Path owned=null;
        try{
            if(args.length==1&&args[0].equals("--help")){
                output.println("Streaming original Sambamba Linux JVM profile v2\nRun: --request request.properties --out NEW_DIRECTORY\nVerify: --verify EXISTING_BUNDLE --out NEW_DIRECTORY\nEvery request requires: "+new java.util.TreeSet<>(KEYS)+"\nUse exact pinned downloads; see SAMBAMBA_STREAMING_WORKFLOW.md. Generated BAI remains unqualified collateral.");return 0;
            }
            Map<String,String> options=options(args);
            boolean verification=options.containsKey("verify");
            if(!options.keySet().equals(verification?Set.of("verify","out"):Set.of("request","out")))throw new IllegalArgumentException("Use --request FILE --out NEW or --verify BUNDLE --out NEW; see --help");
            Path requestedOut=Path.of(options.get("out")).toAbsolutePath().normalize();commandPath(requestedOut);
            Path verifyRoot=verification?Path.of(options.get("verify")).toRealPath():null;
            Path intended=intendedNew(requestedOut);
            if(verification&&(intended.startsWith(verifyRoot)||verifyRoot.startsWith(intended)))throw new IOException("Use a NEW verification output outside the original bundle; no original directory is modified");
            Files.createDirectories(requestedOut.getParent());Files.createDirectory(requestedOut);owned=requestedOut.toRealPath();
            write(owned.resolve("WARNINGS.md"),NOTICE);linux();
            Config c;Properties capture;SambambaStreamingWorkflowBundle.Bundle old=null;
            if(verification){
                Path original=verifyRoot;
                if(owned.startsWith(original)||original.startsWith(owned))throw new IOException("Use a NEW verification output outside the original bundle");
                old=SambambaStreamingWorkflowBundle.read(original);c=old.config();
                write(owned.resolve("request.properties"),Files.readAllBytes(original.resolve("request.properties")));
                capture=new Properties();capture.putAll(old.capture());capture.setProperty("kind","verification");
                capture.setProperty("parent_bundle",original.toString());capture.setProperty("parent_marker_sha256",old.markerSha());
                capture.setProperty("capture_source",original.resolve("input.bam").toString());
                stableCopy(original.resolve("input.bam"),owned.resolve("input.bam"),old.capture().getProperty("input_sha256"),c);
            }else{
                Path request=Path.of(options.get("request")).toAbsolutePath().normalize();
                if(Files.size(request)>1_048_576)throw new IOException("Request exceeds1 MiB");
                String requestHash=sha(request,1_048_576);byte[] raw=Files.readAllBytes(request);
                if(!sha(raw).equals(requestHash)||!sha(request,1_048_576).equals(requestHash))throw new IOException("Request changed during capture");
                write(owned.resolve("request.properties"),raw);
                Config original=config(load(owned.resolve("request.properties")));Path requestDir=request.getParent();
                Path requestedBam=SambambaStreamingWorkflowBundle.resolve(requestDir,original.get("bam")),requestedBinary=SambambaStreamingWorkflowBundle.resolve(requestDir,original.get("sambamba"));
                if(!Files.isRegularFile(requestedBam))throw new IOException("Requested BAM unavailable: "+requestedBam);
                Path source=requestedBam.toRealPath(),executable=binary(requestedBinary,original.get("edition"));
                if(Files.size(source)>original.auditLimits().compressedBytes())throw new IOException("Requested BAM exceeds audit_compressed_bytes; declare adequate permitted capacity and retry NEW output without splitting duplicate families");
                String inputHash=sha(source,original.auditLimits().compressedBytes());stableCopy(source,owned.resolve("input.bam"),inputHash,original);
                var effective=new LinkedHashMap<>(original.values());effective.put("bam","input.bam");effective.put("sambamba",executable.toString());c=config(properties(effective));
                capture=new Properties();capture.setProperty("profile",PROFILE);capture.setProperty("kind","analysis");
                capture.setProperty("request_path_requested",options.get("request"));capture.setProperty("request_directory",requestDir.toString());
                capture.setProperty("request_sha256",sha(raw));capture.setProperty("bam_requested",original.get("bam"));capture.setProperty("bam_request_absolute",requestedBam.toString());
                capture.setProperty("bam_resolved",source.toString());capture.setProperty("binary_requested",original.get("sambamba"));capture.setProperty("binary_request_absolute",requestedBinary.toString());
                capture.setProperty("binary_resolved",executable.toString());capture.setProperty("input_sha256",inputHash);capture.setProperty("capture_source",source.toString());
                capture.setProperty("parent_bundle","");capture.setProperty("parent_marker_sha256","");
            }
            capture.setProperty("execution_root",owned.toString());capture.setProperty("output_requested",options.get("out"));capture.setProperty("jvm_pid",Long.toString(ProcessHandle.current().pid()));
            store(owned.resolve("capture.properties"),capture);store(owned.resolve("effective.properties"),c.properties());store(owned.resolve("implementation.properties"),implementation());
            SambambaStreamingWorkflowBundle.coherent(owned,c,capture);SambambaStreamingBamAudit.validateInput(owned.resolve("input.bam"),c.auditLimits());
            Path executable=binary(Path.of(c.get("sambamba")),c.get("edition"));Properties runtime=SambambaStreamingWorkflowRuntime.inspect(executable,owned,c);
            if(old!=null)SambambaStreamingWorkflowRuntime.replayCoherence(old.runtime(),runtime);
            store(owned.resolve("runtime-before.properties"),runtime);
            for(boolean remove:c.remove()?List.of(false,true):List.of(false)){
                String phase=remove?"remove":"mark";Path scratch=owned.resolve(remove?"tmp-remove":"tmp-mark");
                if(!scratch.normalize().startsWith(owned))throw new IOException("Scratch path escapes owned workspace");
                Files.createDirectory(scratch);SambambaStreamingWorkflowRuntime.verify(runtime);binary(executable,c.get("edition"));
                var result=SambambaStreamingWorkflowProcess.execute(command(executable,c,owned,remove),owned,phase,c,c.number("wall_ms"),c.addressBytes());
                if(result.exitCode()!=0)throw new IOException("Original "+phase+" exited "+result.exitCode()+"; read "+phase+".stderr and process receipt. Check the explicit address/work/wall capacities and input, then retry NEW output.");
                SambambaStreamingWorkflowRuntime.verify(runtime);binary(executable,c.get("edition"));
            }
            SambambaStreamingWorkflowBundle.audit(owned,c,capture,false);
            SambambaStreamingWorkflowRuntime.verify(runtime);store(owned.resolve("runtime-after.properties"),runtime);
            if(old!=null){
                for(boolean remove:c.remove()?List.of(false,true):List.of(false)){
                    String phase=remove?"remove":"mark",name=remove?"deduplicated.bam":"marked.bam";
                    SambambaStreamingBamAudit.compareActualRuns(old.root().resolve(name),owned.resolve(name),
                        headerCommand(SambambaStreamingWorkflowBundle.sourceCommand(phase,c,old.capture())),headerCommand(SambambaStreamingWorkflowBundle.sourceCommand(phase,c,capture)),c.auditLimits());
                }
                var checked=SambambaStreamingWorkflowBundle.read(old.root());
                if(!old.markerSha().equals(checked.markerSha())||!old.members().equals(checked.members()))throw new IOException("Original bundle changed during verification");
                Properties compared=new Properties();compared.setProperty("parent_bundle",old.root().toString());compared.setProperty("parent_marker_sha256",old.markerSha());
                compared.setProperty("marked_records_and_headers_equal","true");compared.setProperty("removed_records_and_headers_equal",c.remove()?"true":"not_requested");store(owned.resolve("comparison.properties"),compared);
            }
            SambambaStreamingWorkflowBundle.complete(owned,c,capture);
            output.println((verification?"PASS_ORIGINAL_SAMBAMBA_CAPTURED_INPUT_REPLAY ":"PASS_ORIGINAL_SAMBAMBA_PROFILE ")+owned+"; selected="+SambambaStreamingWorkflowBundle.selected(c)+"; BAI index qualification absent");
            return 0;
        }catch(Throwable failure){
            int code=failure instanceof Prerequisite?3:2;String label=code==3?"SKIP3_SAMBAMBA_PREREQUISITE_UNAVAILABLE":"FAIL2_SAMBAMBA_ANALYSIS_UNADMITTED";
            if(owned!=null)try{
                Path marker=owned.resolve("COMPLETED.properties");
                if(Files.exists(marker))Files.move(marker,owned.resolve("COMPLETION-INVALIDATED-"+UUID.randomUUID()+".properties"));
                write(owned.resolve("FAILURE.txt"),label+"\n"+failure.getClass().getName()+": "+failure.getMessage()+"\nNo accepted result. Retain provisional files and original bundles. Supply the named permitted prerequisite or correct the cause; retry NEW output. Read WARNINGS.md and phase consoles.\n");
            }catch(Throwable recording){error.println("FAIL2_FAILURE_RECORDING_UNAVAILABLE "+recording);code=2;}
            error.println(label+": "+failure);error.println("No accepted result. Read WARNINGS.md/FAILURE.txt and phase consoles when present. Supply the named permitted prerequisite or correct the cause, preserve originals, and retry NEW output; skip dependent analysis when unavailable.");
            return code;
        }
    }
    private static Map<String,String> options(String[] args){
        if(args.length==0||args.length%2!=0)throw new IllegalArgumentException("Use --option value pairs; see --help");
        var values=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)
            if(!args[i].startsWith("--")||args[i].length()<3||args[i+1].isEmpty()||values.putIfAbsent(args[i].substring(2),args[i+1])!=null)throw new IllegalArgumentException("Invalid/duplicate option: "+args[i]);
        return values;
    }
    private static Properties properties(Map<String,String> values){Properties p=new Properties();p.putAll(values);return p;}
    static void stableCopy(Path source,Path target,String expected,Config config)throws Exception {
        long maximum=config.auditLimits().compressedBytes();
        if(Files.size(source)>maximum)throw new IOException("Captured BAM exceeds audit_compressed_bytes; declare adequate permitted capacity without truncating duplicate families");
        long available=config.workBytes()-inventory(target.getParent(),config,false).bytes();
        if(Files.size(source)>available)throw new IOException("Captured BAM exceeds remaining work_mb; supply adequate permitted workspace capacity and retry NEW output");
        if(!sha(source,maximum).equals(expected))throw new IOException("Input changed before capture");
        // Bound each actual write as well: the source can grow after the preflight.
        long bound=Math.min(maximum,available),copied=0;
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,java.nio.file.StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];
            for(int n;(n=in.read(buffer))>=0;)if(n>0){
                if(copied>bound-n)throw new IOException("Input grew beyond audit_compressed_bytes or remaining work_mb during capture");
                out.write(buffer,0,n);copied+=n;
            }
        }
        if(!sha(source,maximum).equals(expected)||!sha(target,maximum).equals(expected))throw new IOException("Input changed during capture; no bytes were repaired");
    }
}
