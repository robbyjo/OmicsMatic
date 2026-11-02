/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Complete captured inputs followed by original execution only; scientific output admission remains separate. */
final class FastpOriginalStage {
    private FastpOriginalStage() { }
    record Limits(FastpFastq.Limits fastq,FastpProcess.Limits process,long bundleBytes,int bundleFiles) {
        Limits {java.util.Objects.requireNonNull(fastq);java.util.Objects.requireNonNull(process);if(bundleBytes<1_048_576||bundleBytes>1_000_000_000_000L||bundleFiles<32||bundleFiles>1_000_000)throw new IllegalArgumentException("Invalid complete fastp retained-bundle capacities");}
    }
    record Result(Path root,FastpRuntime.Context runtime,List<FastpFiles.Capture> captures,
                  FastpFastq.Inspection inputs,FastpProcess.Result process,FastpParameters.Options options,
                  FastpFastq.Encoding encoding,FastpFastq.Alphabet alphabet,FastpFastq.Plus plus,FastpFastq.Pairing pairing) {
        Result {captures=List.copyOf(captures);}
    }
    static Result run(FastpRuntime.Context runtime,Path original1,Path original2,FastpFastq.Encoding encoding,
                      FastpFastq.Alphabet alphabet,FastpFastq.Plus plus,FastpFastq.Pairing pairing,
                      FastpParameters.Options options,Path newRoot,Limits limits,Runnable check)throws IOException {
        Path root=newRoot.toAbsolutePath().normalize();
        if(root.getParent()==null||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("fastp requires a canonical parent and NEW retained analysis directory");
        if(original1.startsWith(root)||original2!=null&&original2.startsWith(root)||runtime.home().startsWith(root)||root.startsWith(runtime.home())||runtime.setupReport().startsWith(root)||root.startsWith(runtime.setupReport()))throw new IOException("fastp retained analysis,input and runtime/setup trees must be disjoint");
        check.run();runtime.verify(check);Files.createDirectory(root);
        var captures=new ArrayList<FastpFiles.Capture>();
        try {
            FastpFiles.writeJson(root.resolve("request.json"),Map.of("status","REQUESTED_ORIGINAL_PROCESS_STAGE_ONLY","options",options,"encoding",encoding,"alphabet",alphabet,"plus_policy",plus,"pairing_policy",pairing,"limits",limits,"profile",runtime.profile(),"scientific_output_admitted",false));
            Files.writeString(root.resolve("WARNINGS.md"),WARNINGS,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            FastpParameters.requireCapacity(runtime.version(),options,limits.process());
            Files.createDirectory(root.resolve("inputs"));
            captures.add(FastpFiles.capture(root,original1.toAbsolutePath().normalize(),"read1","inputs/read1.original",limits.fastq().encodedBytes(),check));
            if(original2!=null)captures.add(FastpFiles.capture(root,original2.toAbsolutePath().normalize(),"read2","inputs/read2.original",limits.fastq().encodedBytes(),check));
            budget(root,limits,check);
            Path first=root.resolve(captures.get(0).snapshot()),second=captures.size()==2?root.resolve(captures.get(1).snapshot()):null;
            var inspected=FastpFastq.inspect(first,second,limits.fastq(),encoding,alphabet,plus,pairing,false,check);
            for(int i=0;i<captures.size();i++){
                var capture=captures.get(i);boolean gzip=i==0?inspected.read1().gzip():inspected.read2().gzip();String snapshot="inputs/read"+(i+1)+".fastq"+(gzip?".gz":"");
                Files.move(root.resolve(capture.snapshot()),root.resolve(snapshot));captures.set(i,new FastpFiles.Capture(capture.role(),capture.originalPath(),capture.identity(),snapshot));
            }
            var snapshotIdentities=new TreeMap<String,FastpFiles.Identity>();
            for(var capture:captures){var actual=FastpFiles.identity(root.resolve(capture.snapshot()),limits.fastq().encodedBytes(),check);if(!actual.pin().equals(capture.identity().pin()))throw new IOException("Captured fastp input bytes changed before original execution");snapshotIdentities.put(capture.snapshot(),actual);}
            FastpFiles.writeJson(root.resolve("input-captures.json"),captures);FastpFiles.writeJson(root.resolve("input-statistics.json"),inspected);FastpFiles.writeJson(root.resolve("runtime-binding.json"),runtime);
            FastpFiles.writeJson(root.resolve("duplication-memory.json"),FastpParameters.memory(runtime.version(),options.duplication()));
            var arguments=FastpParameters.arguments(runtime.version(),second!=null,encoding,options,root.resolve(captures.get(0).snapshot()).toString(),second==null?null:root.resolve(captures.get(1).snapshot()).toString());
            var command=new ArrayList<>(runtime.command());command.addAll(arguments);
            FastpProcess.Check integrity=()->{
                check.run();runtime.verify(check);
                for(var row:snapshotIdentities.entrySet())if(!FastpFiles.identity(root.resolve(row.getKey()),limits.fastq().encodedBytes(),check).equals(row.getValue()))throw new IOException("fastp captured original input changed during processing");
                FastpFiles.verifyOriginals(captures,limits.fastq().encodedBytes(),check);budget(root,limits,check);
            };
            var process=FastpProcess.executeOwned(command,runtime.environment(),root.resolve("original"),limits.process(),integrity,check);
            integrity.verify();
            FastpFiles.writeJson(root.resolve("stage.json"),Map.of("status","ORIGINAL_PROCESSING_FINISHED_OUTPUT_AUDIT_PENDING","scientific_output_admitted",false,"exit_code",process.exitCode(),"process_ledger","original/process.json","owned_live_after_cleanup",process.ownedLiveAfterCleanup(),"scope","All raw inputs were captured and fully inspected;original output files still require complete scientific audit before any result is admitted."));
            budget(root,limits,check);return new Result(root,runtime,captures,inspected,process,options,encoding,alphabet,plus,pairing);
        }catch(IOException|RuntimeException failed){
            String status=failed instanceof FastpRuntime.Unavailable?"SKIP3_PREREQUISITE_OR_CAPACITY_UNAVAILABLE":"FAIL2_NO_ANALYSIS_RESULT_ADMITTED";
            try{FastpFiles.writeJson(root.resolve("FAILURE.json"),Map.of("status",status,"error",failed.toString(),"captures",captures,"required_action","Preserve all raw files and logs;correct the named prerequisite/input/capacity/integrity cause and use a NEW output directory. If install/execution is not permitted,keep dependent analysis skipped."));}
            catch(IOException diagnostic){failed.addSuppressed(diagnostic);}
            try{Files.writeString(root.resolve("WARNINGS.md"),"\n\n**"+status+"**: "+failed+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
            catch(IOException diagnostic){failed.addSuppressed(diagnostic);}
            if(failed instanceof IOException io)throw io;throw new IOException("fastp original stage cancelled or invalid;raw evidence retained without an admitted result",failed);
        }
    }
    static void budget(Path root,Limits limits,Runnable check)throws IOException {
        long[] count={0,0};Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException {
                check.run();if(++count[0]>limits.bundleFiles()||Files.isSymbolicLink(dir)||!dir.toRealPath().equals(dir))throw new IOException("fastp retained bundle directory/link/member capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException {
                check.run();if(++count[0]>limits.bundleFiles()||!attrs.isRegularFile()||Files.isSymbolicLink(file))throw new IOException("fastp retained bundle contains extra/linked/nonregular or excessive members");count[1]=Math.addExact(count[1],attrs.size());if(count[1]>limits.bundleBytes())throw new IOException("fastp retained bundle byte capacity exceeded");return FileVisitResult.CONTINUE;
            }
        });
    }
    private static final String WARNINGS="""
        # fastp original processing: output audit still required

        **No completed scientific result is admitted by this process stage.** Require complete FASTQ,
        paired-population, original report and transformation audits before publishing any result.
        Raw inputs, original headers, compression bytes, qualities, options and outputs remain retained.
        Quality encoding, plus-header, alphabet and mate-identity policies are explicit. Ordinal-only
        pairing is unverified. Literal noncanonical bases and ignored plus-name mismatches need review.

        Source defaults depend on edition and input headers. No omitted paper options,barcode position,
        patch version or biological correctness is inferred. The source Bloom deduplicator is probabilistic;
        accuracy6 needs24GiB for its buffer plus overhead. Never silently lower requested accuracy.
        Hard process limits and sampled RSS checks are not an OS sandbox or a whole-host-memory guarantee.

        Required action: inspect all input/statistics/runtime/process records and raw outputs. Preserve
        failures and partials,correct the named cause and use NEW paths. If a required install/execution
        prerequisite is unavailable or forbidden,skip dependent analysis. Cell Ranger remains excluded.
        """;
}
