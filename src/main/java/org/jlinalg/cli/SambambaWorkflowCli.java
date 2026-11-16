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
import org.jlinalg.alignment.SambambaBamAudit;
import static org.jlinalg.cli.SambambaWorkflowFiles.*;

/** Original Sambamba marking/removal in a Linux JVM, with captured-input replay. */
public final class SambambaWorkflowCli {
    static final String NOTICE="""
        # Warnings and required actions

        This bounded original-runtime profile executes exact official Sambamba
        0.6.6 or0.8.2 Linux-amd64 binaries. Use Java inside a permitted Linux/WSL
        environment. Requests declare every supported scientific/resource option.
        Software is not installed during analysis. Supply exact permitted missing
        binaries/helpers/libraries; skip dependent analysis when unavailable.

        Marking retains records and changes duplicate flags. Removal additionally
        executes the original removal operation on the same captured input; the
        original marked companion remains retained. Native audits check all BAM
        record bytes except the permitted duplicate bit, complete retained records
        after removal, reference dictionaries, source header metadata and raw aux
        representations. They do not independently classify duplicate molecules.
        VERIFY reruns the exact original classifier and compares complete records
        and headers, allowing only each checked exact command string to differ.

        Original0.8.2 deliberately writes BAM header VN:1.0; preserve it alongside
        the observed0.8.2 executable identity. Missing quality byte255 is not zero.
        Existing secondary/supplementary duplicate flags persist; primary flags
        are recomputed. This is not UMI-aware marking. Equal-score/spill behavior
        remains original. No paper edition is substituted or author flags inferred.
        P110 removed duplicates with0.6.6; P198 marked them with0.8.2 after donor
        aggregation. Their complete parameters and binary builds remain unknown.

        Generated BAI files are retained collateral, without independent index
        qualification. For index-dependent downstream analysis, use a separately
        qualified indexer/verification procedure. This profile admits the selected
        BAM duplicate operation, not an independently qualified BAI or whole paper.

        Input/output BAM audit bounds:64 MiB compressed,256 MiB decoded, one million
        records,16 MiB per record,4 MiB header, fewer than16384 references. Standard
        coordinate-sorted short-read BAM only; long-CIGAR CG and nonstandard aux
        types are refused. Do not truncate or arbitrarily split duplicate families
        to fit a bound; use a separately qualified larger-capacity workflow.

        setpriv requests parent-death KILL and prlimit enforces child RLIMIT_AS.
        wall_ms is per original mark/remove stage, including startup/PID recording.
        Metadata probes have fixed10-second/512-MiB bounds. RSS and workspace bytes
        and entry counts are sampled; work_mb/max_files are not an OS disk quota.
        Java audit memory is separate; use sufficient JVM heap. No filesystem or
        network isolation is claimed. All original consoles and partials remain.

        SKIP3: named execution prerequisite unavailable; skip dependent analysis,
        supply the permitted runtime and retry NEW output. FAIL2: invalid input,
        source/runtime/request contradiction, original error, limit or recording
        failure; no accepted result. Read FAILURE.txt and raw phase consoles.
        Require COMPLETED.properties and successful original-runtime VERIFY; file
        presence, checksums or a native transition audit alone are insufficient.
        Failed runs retain provisional files, and late completion markers are
        invalidated. Existing bundles are never overwritten or repaired.

        Integrity/replay is not authenticity, a reproducible binary build, biological
        calibration or whole-tool/paper qualification. No catalog credit is added
        by this profile alone; the full250-paper goal remains open.
        """;
    private SambambaWorkflowCli(){ }
    /** Standalone entry point in the development executable JAR. */
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    /** Execute or verify, returning0, SKIP3 or FAIL2. */
    public static int run(String[] args,PrintStream output,PrintStream error){
        Path owned=null;
        try{
            if(args.length==1&&args[0].equals("--help")){
                output.println("Original Sambamba Linux JVM profile\nRun: --request request.properties --out NEW_DIRECTORY\nVerify: --verify EXISTING_BUNDLE --out NEW_DIRECTORY\nEvery request requires: "+new java.util.TreeSet<>(KEYS)+"\nUse exact pinned downloads; see SAMBAMBA_EXECUTION_PROFILE.md. Generated BAI remains unqualified collateral.");return 0;
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
            Config c;Properties capture;SambambaWorkflowBundle.Bundle old=null;
            if(verification){
                Path original=verifyRoot;
                if(owned.startsWith(original)||original.startsWith(owned))throw new IOException("Use a NEW verification output outside the original bundle");
                old=SambambaWorkflowBundle.read(original);c=old.config();
                write(owned.resolve("request.properties"),Files.readAllBytes(original.resolve("request.properties")));
                capture=new Properties();capture.putAll(old.capture());capture.setProperty("kind","verification");
                capture.setProperty("parent_bundle",original.toString());capture.setProperty("parent_marker_sha256",old.markerSha());
                capture.setProperty("capture_source",original.resolve("input.bam").toString());
                stableCopy(original.resolve("input.bam"),owned.resolve("input.bam"),old.capture().getProperty("input_sha256"));
            }else{
                Path request=Path.of(options.get("request")).toAbsolutePath().normalize();
                if(Files.size(request)>1_048_576)throw new IOException("Request exceeds1 MiB");
                String requestHash=sha(request,1_048_576);byte[] raw=Files.readAllBytes(request);
                if(!sha(raw).equals(requestHash)||!sha(request,1_048_576).equals(requestHash))throw new IOException("Request changed during capture");
                write(owned.resolve("request.properties"),raw);
                Config original=config(load(owned.resolve("request.properties")));Path requestDir=request.getParent();
                Path requestedBam=SambambaWorkflowBundle.resolve(requestDir,original.get("bam")),requestedBinary=SambambaWorkflowBundle.resolve(requestDir,original.get("sambamba"));
                if(!Files.isRegularFile(requestedBam))throw new IOException("Requested BAM unavailable: "+requestedBam);
                Path source=requestedBam.toRealPath(),executable=binary(requestedBinary,original.get("edition"));
                if(Files.size(source)>(64L<<20))throw new IOException("Requested BAM exceeds64 MiB; use a separately qualified larger-capacity workflow without splitting duplicate families");
                String inputHash=sha(source,64L<<20);stableCopy(source,owned.resolve("input.bam"),inputHash);
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
            SambambaWorkflowBundle.coherent(owned,c,capture);SambambaBamAudit.validateInput(owned.resolve("input.bam"),SambambaBamAudit.Limits.defaults());
            Path executable=binary(Path.of(c.get("sambamba")),c.get("edition"));Properties runtime=SambambaWorkflowRuntime.inspect(executable,owned,c);
            if(old!=null)SambambaWorkflowRuntime.replayCoherence(old.runtime(),runtime);
            store(owned.resolve("runtime-before.properties"),runtime);
            for(boolean remove:c.remove()?List.of(false,true):List.of(false)){
                String phase=remove?"remove":"mark";Path scratch=owned.resolve(remove?"tmp-remove":"tmp-mark");
                if(!scratch.normalize().startsWith(owned))throw new IOException("Scratch path escapes owned workspace");
                Files.createDirectory(scratch);SambambaWorkflowRuntime.verify(runtime);binary(executable,c.get("edition"));
                var result=SambambaWorkflowProcess.execute(command(executable,c,owned,remove),owned,phase,c,c.number("wall_ms"),c.addressBytes());
                if(result.exitCode()!=0)throw new IOException("Original "+phase+" exited "+result.exitCode()+"; read "+phase+".stderr and process receipt. Check the explicit address/work/wall capacities and input, then retry NEW output.");
                SambambaWorkflowRuntime.verify(runtime);binary(executable,c.get("edition"));
            }
            for(var entry:SambambaWorkflowBundle.auditFiles(owned,c,capture).entrySet())write(owned.resolve(entry.getKey()),entry.getValue());
            SambambaWorkflowRuntime.verify(runtime);store(owned.resolve("runtime-after.properties"),runtime);
            if(old!=null){
                for(boolean remove:c.remove()?List.of(false,true):List.of(false)){
                    String phase=remove?"remove":"mark",name=remove?"deduplicated.bam":"marked.bam";
                    SambambaBamAudit.compareActualRuns(old.root().resolve(name),owned.resolve(name),
                        headerCommand(SambambaWorkflowBundle.sourceCommand(phase,c,old.capture())),headerCommand(SambambaWorkflowBundle.sourceCommand(phase,c,capture)),SambambaBamAudit.Limits.defaults());
                }
                var checked=SambambaWorkflowBundle.read(old.root());
                if(!old.markerSha().equals(checked.markerSha())||!old.members().equals(checked.members()))throw new IOException("Original bundle changed during verification");
                Properties compared=new Properties();compared.setProperty("parent_bundle",old.root().toString());compared.setProperty("parent_marker_sha256",old.markerSha());
                compared.setProperty("marked_records_and_headers_equal","true");compared.setProperty("removed_records_and_headers_equal",c.remove()?"true":"not_requested");store(owned.resolve("comparison.properties"),compared);
            }
            SambambaWorkflowBundle.complete(owned,c,capture);
            output.println((verification?"PASS_ORIGINAL_SAMBAMBA_CAPTURED_INPUT_REPLAY ":"PASS_ORIGINAL_SAMBAMBA_PROFILE ")+owned+"; selected="+SambambaWorkflowBundle.selected(c)+"; BAI index qualification absent");
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
    private static void stableCopy(Path source,Path target,String expected)throws Exception {
        if(Files.size(source)>(64L<<20))throw new IOException("Captured BAM exceeds64 MiB; use a separately qualified larger-capacity workflow without truncating duplicate families");
        if(!sha(source,64L<<20).equals(expected))throw new IOException("Input changed before capture");Files.copy(source,target);
        if(!sha(source,64L<<20).equals(expected)||!sha(target,64L<<20).equals(expected))throw new IOException("Input changed during capture; no bytes were repaired");
    }
}
