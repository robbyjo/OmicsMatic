/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/** A bound original execution context; source-build and complete fitting admission remain separate. */
final class RsemRuntime {
    private RsemRuntime() { }
    enum Program { VERSION, PREPARE, QUANTIFY }
    record Context(Path home,RsemPayload.Context payload,RsemHost.Result host,RsemHost.Limits limits,String bindingSha256) {
        void verify(Runnable check)throws IOException {
            RsemHost.verifyFiles(host.snapshot(),payload,limits,check);
            if(!bindingSha256.isEmpty()&&!bindingSha256.equals(RepeatInputs.hash(home.resolve("runtime-binding.json"),4194304,check)))
                throw new IOException("RSEM bound-context metadata changed");
        }
        /** Zero exit and cleanup admit process execution only. The caller must audit every scientific input/output. */
        RsemProcess.Result executeOnly(Program program,List<String> arguments,Path newWork,RsemProcess.Limits process,
                long wholeOperationMillis,RsemProcess.Check inputs,Runnable cancellation,List<Path> accountedRoots)throws IOException {
            validateArguments(program,arguments,process.threads());
            Runnable check=deadline(wholeOperationMillis,cancellation);
            var command=new ArrayList<String>(List.of("/usr/bin/perl",payload.home().resolve("bin/"+
                    (program==Program.PREPARE?"rsem-prepare-reference":"rsem-calculate-expression")).toString()));
            if(program==Program.VERSION)command.add("--version");else command.addAll(arguments);
            RsemProcess.Check integrity=()->{inputs.verify();verify(check);inputs.verify();};
            return RsemProcess.executeOwned(command,payload.environment(newWork.toAbsolutePath().normalize()),newWork,process,integrity,check,accountedRoots);
        }
    }
    static Context bind(RsemPayload.Context payload,Path suppliedHome,RsemHost.Limits limits,long wholeOperationMillis,
            RsemProcess.Check inputs,Runnable cancellation)throws IOException {
        Path home=suppliedHome.toAbsolutePath().normalize();RsemPayload.originalPath(home);
        if(home.getParent()==null||!home.getParent().toRealPath().equals(home.getParent())||home.startsWith(payload.home())||payload.home().startsWith(home))
            throw new IOException("RSEM binding requires a canonical NEW directory disjoint from its staged payload");
        Runnable check=deadline(wholeOperationMillis,cancellation);inputs.verify();payload.verify(check);Files.createDirectory(home);
        try{
            var host=RsemHost.capture(payload,home.resolve("host"),limits,check);
            var preliminary=new Context(home,payload,host,limits,"");
            var version=preliminary.executeOnly(Program.VERSION,List.of(),home.resolve("version"),limits.process(),wholeOperationMillis,inputs,check,List.of(host.work()));
            String observed=new String(AdmixtureInputs.raw(version.stdout(),65536),StandardCharsets.UTF_8);
            if(!observed.equals("Current version: "+payload.source().reportedVersion()+"\n")||Files.size(version.stderr())!=0)
                throw new IOException("RSEM version diagnostic differs from its exact pinned source profile");
            var record=new LinkedHashMap<String,Object>();record.put("schema_version",1);record.put("status","BOUND_ORIGINAL_SCRIPTS_AND_DECLARED_HOST_ONLY");
            record.put("profile",payload.profile());record.put("payload_home",payload.home());record.put("payload_manifest_sha256",payload.manifestSha256());
            record.put("source",payload.source());record.put("host_snapshot_sha256",host.snapshotSha256());record.put("host_limits",limits);
            record.put("whole_binding_wall_millis",wholeOperationMillis);record.put("observed_version_text",observed);
            record.put("version_text_uniquely_identifies_source",false);record.put("source_to_build_provenance_verified",payload.sourceBuild()!=null);
            if(payload.sourceBuild()!=null)record.put("source_build_receipt_sha256",payload.sourceBuild().recordSha256());
            record.put("scientific_runtime_admitted",false);record.put("public_fitting_workflow_available",false);
            record.put("scope","Exact original script profile,staged executable bytes,declared host/module/ELF lookup closure and actual original version diagnostic. Process checks do not certify a fitted result,source-to-binary compilation,an OS sandbox or whole-tool/paper equivalence. Typed scientific input/output admission and portable setup remain required.");
            byte[] raw=RsemJson.bytes(record);if(raw.length>4194304)throw new IOException("RSEM binding metadata exceeds capacity");
            var used=RepeatBundle.inventory(home,new RepeatBundle.Limits(limits.process().workspaceBytes(),limits.process().fileCount()),false,check);
            if(used.bytes()+raw.length+65536>limits.process().workspaceBytes()||used.members()+8>limits.process().fileCount())throw new IOException("Complete RSEM binding with final diagnostic reserve exceeds capacity");
            Files.write(home.resolve("runtime-binding.json"),raw,StandardOpenOption.CREATE_NEW);inputs.verify();check.run();
            var context=new Context(home,payload,host,limits,AdmixtureInputs.digest(raw));context.verify(check);return context;
        }catch(IOException|RuntimeException failure){
            try{String message=failure.toString();if(message.length()>8192)message=message.substring(0,8192);
                Files.writeString(home.resolve("RUNTIME_FAILED.txt"),"RSEM BINDING FAILED: no scientific runtime or fit admitted.\n"+message+
                    "\nRequired action: preserve all probes and logs. Correct the named source,provider,permission or budget problem and rerun to NEW paths. If required installation is forbidden,skip dependent fitting.\n",StandardOpenOption.CREATE_NEW);
            }catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
    private static final Map<String,Integer> PREPARE=Map.of("--transcript-to-gene-map",1,"--gtf",1,"--trusted-sources",1,
            "--polyA",0,"--polyA-length",1,"--no-polyA-subset",1,"--quiet",0);
    private static final Map<String,Integer> QUANTIFY=Map.ofEntries(
            Map.entry("--alignments",0),Map.entry("--no-bam-output",0),Map.entry("--keep-intermediate-files",0),Map.entry("--paired-end",0),
            Map.entry("--no-qualities",0),Map.entry("--strandedness",1),Map.entry("--forward-prob",1),Map.entry("--seed",1),Map.entry("--num-threads",1),
            Map.entry("--calc-pme",0),Map.entry("--calc-ci",0),Map.entry("--estimate-rspd",0),Map.entry("--append-names",0),
            Map.entry("--fragment-length-mean",1),Map.entry("--fragment-length-sd",1),Map.entry("--quiet",0));
    static void validateArguments(Program program,List<String> arguments,int threadCapacity)throws IOException {
        if(program==null||arguments==null||arguments.size()>128)throw new IOException("Invalid or over-capacity RSEM stage argument population");
        if(program==Program.VERSION){if(!arguments.isEmpty())throw new IOException("The fixed RSEM version probe accepts no supplied arguments");return;}
        Map<String,Integer> allowed=program==Program.PREPARE?PREPARE:QUANTIFY;var seen=new HashSet<String>();int positional=0,total=0;
        for(int i=0;i<arguments.size();i++){
            String token=arguments.get(i);argument(token);total+=token.length();
            if(token.startsWith("-")){
                Integer arity=allowed.get(token);if(arity==null||!seen.add(token))throw new IOException("Unknown,repeated or undeclared-provider RSEM option: "+token);
                if(arity==1){
                    if(++i>=arguments.size())throw new IOException("RSEM option is missing its complete value: "+token);
                    String value=arguments.get(i);argument(value);total+=value.length();
                    if(token.equals("--num-threads"))try{int threads=Integer.parseInt(value);if(threads<1||threads>threadCapacity)throw new NumberFormatException();}
                    catch(NumberFormatException invalid){throw new IOException("RSEM thread request exceeds the declared owned-process capacity",invalid);}
                }
            }else{RsemPayload.originalPath(Path.of(token));positional++;}
        }
        if(total>32768||positional!=(program==Program.PREPARE?2:3))throw new IOException("RSEM stage requires its exact positional population and bounded arguments");
        if(program==Program.QUANTIFY&&!seen.containsAll(Set.of("--alignments","--no-bam-output","--keep-intermediate-files","--seed","--num-threads")))
            throw new IOException("Bound RSEM quantification requires explicit alignment mode,retained intermediates,seed and thread count with BAM generation disabled");
    }
    private static void argument(String token)throws IOException {
        if(token==null||token.length()>4096||!token.matches("[A-Za-z0-9_./+,:=\\-]+"))throw new IOException("RSEM shell dispatch refuses whitespace,control characters or shell metacharacters; use permitted NEW input/output paths");
    }
    private static Runnable deadline(long millis,Runnable cancellation){
        if(millis<1||millis>86400000)throw new IllegalArgumentException("Require an explicit bounded whole RSEM operation wall budget");
        java.util.Objects.requireNonNull(cancellation);long started=System.nanoTime();
        return ()->{cancellation.run();if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)>millis)throw new CancellationException("Whole RSEM file/provider/process operation exceeded its declared wall capacity");};
    }
}
