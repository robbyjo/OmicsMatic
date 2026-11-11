/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Original processing followed by complete bounded audit and portable semantic replay. */
final class FastpWorkflow {
    private FastpWorkflow() { }
    static FastpOutputAudit.Data data(Path root,FastpRecipe.Spec replay){return new FastpOutputAudit.Data(root.resolve("processing"),replay.profile().substring("linux-x86_64-fastp-".length()),replay.first(),replay.second(),replay.encoding(),replay.alphabet(),replay.plus(),replay.pairing(),replay.options(),replay.limits().stage().fastq());}
    static FastpOutputAudit.Result run(Path root,Path request,Path home,Path setup,Runnable check)throws IOException {
        var captured=FastpFiles.capture(root,request,"recipe","recipe-request.json",FastpRecipe.MAXIMUM_BYTES,check);FastpFiles.writeJson(root.resolve("recipe-capture.json"),captured);
        byte[] raw=FastpFiles.bytes(root.resolve("recipe-request.json"),FastpRecipe.MAXIMUM_BYTES);var spec=FastpRecipe.parse(raw,request);
        for(Path source:spec.second()==null?List.of(spec.first()):List.of(spec.first(),spec.second()))if(source.startsWith(root))throw new IOException("fastp original inputs must be outside the new result tree");
        var runtime=FastpRuntime.open(home,spec.profile(),setup,check);FastpParameters.requireCapacity(runtime.version(),spec.options(),spec.limits().stage().process());FastpRecordedRuntime.capture(root,runtime,check);budget(root,spec.limits(),check);
        var stage=FastpOriginalStage.run(runtime,spec.first(),spec.second(),spec.encoding(),spec.alphabet(),spec.plus(),spec.pairing(),spec.options(),root.resolve("processing"),spec.limits().stage(),check);
        byte[] replay=FastpRecipe.replay(raw,spec,stage.captures());Files.write(root.resolve("recipe-replay.json"),replay,java.nio.file.StandardOpenOption.CREATE_NEW);var owned=FastpRecipe.parse(replay,root.resolve("recipe-replay.json"));
        var audit=FastpOutputAudit.verify(data(root,owned),root.resolve("audit"),owned.limits().audit(),()->budget(root,spec.limits(),check),check);long passing=mainReads(audit);
        FastpFiles.writeJson(root.resolve("session.json"),Map.of("schema_version",1,"tool",FastpBundle.TOOL,"scope",FastpBundle.SCOPE,"status","audited-provisional","scoped_result_admitted",false,"producer_java_heap_bytes",Runtime.getRuntime().maxMemory(),"audit_result",audit,"main_passing_reads",passing,"downstream_usable",passing>0));
        Files.writeString(root.resolve("WARNINGS.md"),warnings(owned,stage.inputs(),audit),StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
        FastpFiles.verifyOriginals(List.of(captured),FastpRecipe.MAXIMUM_BYTES,check);FastpFiles.verifyOriginals(stage.captures(),spec.limits().stage().fastq().encodedBytes(),check);runtime.verify(check);budget(root,spec.limits(),check);FastpBundle.complete(root,owned,check);return audit;
    }
    static void budget(Path root,FastpRecipe.Limits limits,Runnable check)throws IOException {FastpOriginalStage.budget(root,limits.stage(),check);}
    static long mainReads(FastpOutputAudit.Result result){return result.statistics().get("read1_after").reads()+(result.statistics().containsKey("read2_after")?result.statistics().get("read2_after").reads():0);}
    static String warnings(FastpRecipe.Spec spec,FastpFastq.Inspection input,FastpOutputAudit.Result result){
        var warnings=new ArrayList<String>(result.warnings());warnings.add("PORTABLE_SCOPE: verification recomputes every declared captured population,report statistic and conservation envelope. It does not rerun fastp,readmit the recorded host,prove source-to-binary reproduction,authenticate an execution record,or certify biological accuracy. Original HTML/stdout/stderr are retained raw supplements.");
        warnings.add("RAW_STAGE_HISTORY: processing/stage.json and processing/WARNINGS.md are preserved provisional original-stage records. Only the top-level COMPLETED.json with successful fastp-tools --verify admits this scoped result.");
        if(mainReads(result)==0)warnings.add("NO_MAIN_PASSING_READS: this QC result has no passing main reads. Skip dependent main-read analyses;review failed/orphan populations and explicit filters.");
        if(!input.mateIdentityVerified()&&spec.second()!=null)warnings.add("PAIR_IDENTITY_UNVERIFIED: ordinal pairing was explicitly selected;biological pairing and arbitrary header metadata are not established.");
        if(input.read1().noncanonicalBases()>0||input.read2()!=null&&input.read2().noncanonicalBases()>0)warnings.add("LITERAL_NONCANONICAL_BASES: source literal symbols are retained. Review the exact inputs and source-mask statistics;no biological normalization is inferred.");
        if(input.read1().mismatchedPlus()>0||input.read2()!=null&&input.read2().mismatchedPlus()>0)warnings.add("PLUS_NAMES_IGNORED: source-ignored mismatched plus-line identifiers are retained and counted. Review the declared plus policy.");
        return "# fastp scoped result: warnings require review\n\n"+String.join("\n\n",warnings)+"\n\n"+FastpToolsCli.ACTION+"\n";
    }
    static void verify(Path root,FastpRecipe.Limits limits,Set<String> files,Set<String> dirs,Runnable check)throws IOException {
        byte[] raw=FastpFiles.bytes(root.resolve("recipe-request.json"),FastpRecipe.MAXIMUM_BYTES),replayRaw=FastpFiles.bytes(root.resolve("recipe-replay.json"),FastpRecipe.MAXIMUM_BYTES);var spec=FastpRecipe.parse(replayRaw,root.resolve("recipe-replay.json"));
        if(!spec.limits().equals(limits))throw new IOException("fastp recipe/manifest limits differ");Map<?,?> recipeCapture=FastpChoices.map(FastpFiles.json(root.resolve("recipe-capture.json"),65536),Set.of("role","originalPath","identity","snapshot"));
        Map<?,?> identity=FastpChoices.map(recipeCapture.get("identity"),Set.of("pin","key","modified"));if(!"recipe".equals(recipeCapture.get("role"))||!"recipe-request.json".equals(recipeCapture.get("snapshot"))||!FastpOutputAudit.capturedPin(FastpRuntime.object(identity.get("pin")),FastpRecipe.MAXIMUM_BYTES).equals(FastpFiles.pin(root.resolve("recipe-request.json"),FastpRecipe.MAXIMUM_BYTES,check)))throw new IOException("fastp original recipe capture differs");
        String originalRecipe=FastpChoices.text(recipeCapture,"originalPath");var original=FastpRecipe.parse(raw,root.resolve("recipe-request.json"));var captures=FastpMetrics.array(FastpFiles.json(root.resolve("processing/input-captures.json"),limits.audit().jsonBytes()));if(captures.size()!=(spec.second()==null?1:2))throw new IOException("fastp original recipe/input population differs");
        var decodedCaptures=new ArrayList<FastpFiles.Capture>();var originalInputs=FastpRuntime.object(FastpRuntime.object(SimpleJson.parse(FastpFiles.utf8(raw))).get("inputs"));
        var expected=new HashSet<String>(Set.of("recipe-request.json","recipe-replay.json","recipe-capture.json","runtime-captures.json","session.json","WARNINGS.md","processing/request.json","processing/input-captures.json","processing/input-statistics.json","processing/runtime-binding.json","processing/duplication-memory.json","processing/stage.json","processing/WARNINGS.md"));
        for(int i=0;i<captures.size();i++){
            Map<?,?> c=FastpChoices.map(captures.get(i),Set.of("role","originalPath","identity","snapshot")),id=FastpChoices.map(c.get("identity"),Set.of("pin","key","modified"));String member=FastpFiles.member(FastpChoices.text(c,"snapshot")),role="read"+(i+1);String source=FastpChoices.text(c,"originalPath");
            if(!role.equals(c.get("role"))||!source.equals(FastpRecipe.originalInput(originalRecipe,FastpRecipe.pathText(originalInputs,role)))||!Set.of("inputs/"+role+".fastq","inputs/"+role+".fastq.gz").contains(member))throw new IOException("fastp original recipe/captured input binding differs");
            decodedCaptures.add(new FastpFiles.Capture(role,source,new FastpFiles.Identity(FastpOutputAudit.capturedPin(FastpRuntime.object(id.get("pin")),limits.stage().fastq().encodedBytes()),FastpChoices.text(id,"key"),FastpChoices.text(id,"modified")),member));expected.add("processing/"+member);
        }
        if(!java.util.Arrays.equals(replayRaw,FastpRecipe.replay(raw,original,decodedCaptures)))throw new IOException("fastp portable recipe is not the exact captured-input replay");
        Map<?,?> rawRequest=FastpRuntime.object(FastpFiles.json(root.resolve("processing/request.json"),limits.audit().jsonBytes()));if(!SimpleJson.parse(FastpJson.json(limits.stage())).equals(rawRequest.get("limits")))throw new IOException("fastp raw-stage capacities differ from the recipe");
        var inputs=FastpFastq.inspect(spec.first(),spec.second(),limits.stage().fastq(),spec.encoding(),spec.alphabet(),spec.plus(),spec.pairing(),false,check);sameJson(root.resolve("processing/input-statistics.json"),inputs,limits.audit().jsonBytes());
        sameJson(root.resolve("processing/duplication-memory.json"),FastpParameters.memory(spec.profile().substring("linux-x86_64-fastp-".length()),spec.options().duplication()),65536);
        Map<?,?> runtime=FastpRuntime.object(FastpFiles.json(root.resolve("processing/runtime-binding.json"),limits.audit().jsonBytes()));expected.addAll(FastpRecordedRuntime.verify(root,runtime,check));
        var outputs=new HashSet<String>(Set.of("process.stdout","process.stderr","launch.json","process.json","WARNINGS.md","fastp.json","fastp.html","read1.fastq","failed.fastq"));if(spec.second()!=null){outputs.add("read2.fastq");if(spec.options().storeUnpaired()){outputs.add("unpaired1.fastq");outputs.add("unpaired2.fastq");}}for(String name:outputs)expected.add("processing/original/"+name);
        Set<String> auditFiles=Set.of("complete-index.tsv","read-dispositions.tsv","output-audit.json","effective-output-fastq-limits.json","sort/sorted.tsv");for(String name:auditFiles)expected.add("audit/"+name);
        if(!expected.equals(files)||!FastpBundle.directoryCensus(expected).equals(dirs))throw new IOException("Unexpected/incomplete fastp semantic file/directory graph");
        Map<?,?> process=FastpRuntime.object(FastpFiles.json(root.resolve("processing/original/process.json"),limits.audit().jsonBytes())),launch=FastpRuntime.object(FastpFiles.json(root.resolve("processing/original/launch.json"),limits.audit().jsonBytes()));if(!"running".equals(launch.get("status"))||!process.get("command").equals(launch.get("command"))||!process.get("limits").equals(launch.get("limits"))||!process.get("owned_launcher_pid").equals(launch.get("owned_launcher_pid")))throw new IOException("fastp launch/process records differ");FastpRuntime.integer(launch.get("owned_launcher_pid"),1,Long.MAX_VALUE);
        var p=limits.stage().process();FastpRuntime.integer(process.get("peak_sampled_rss_bytes"),0,p.sampledRssBytes());FastpRuntime.integer(process.get("peak_processes"),0,p.processes());FastpRuntime.integer(process.get("peak_threads"),0,p.threads());FastpRuntime.integer(process.get("peak_workspace_bytes"),0,p.workspaceBytes());
        Path scratch=Files.createTempDirectory("omicsmatic-fastp-verify-").toRealPath();if(scratch.startsWith(root)||root.startsWith(scratch))throw new IOException("Verification scratch must be outside the source bundle");
        try{
            var audit=FastpOutputAudit.verify(data(root,spec),scratch.resolve("audit"),limits.audit(),()->budget(scratch,limits,check),check);
            for(String name:auditFiles)if(!FastpFiles.pin(root.resolve("audit/"+name),limits.stage().bundleBytes(),check).equals(FastpFiles.pin(scratch.resolve("audit/"+name),limits.stage().bundleBytes(),check)))throw new IOException("fastp retained audit differs from complete native recomputation: "+name);
            Map<?,?> session=FastpChoices.map(FastpFiles.json(root.resolve("session.json"),limits.audit().jsonBytes()),Set.of("schema_version","tool","scope","status","scoped_result_admitted","producer_java_heap_bytes","audit_result","main_passing_reads","downstream_usable"));
            if(FastpRuntime.integer(session.get("schema_version"),1,1)!=1||!FastpBundle.TOOL.equals(session.get("tool"))||!FastpBundle.SCOPE.equals(session.get("scope"))||!"audited-provisional".equals(session.get("status"))||!Boolean.FALSE.equals(session.get("scoped_result_admitted"))||!SimpleJson.parse(FastpJson.json(audit)).equals(session.get("audit_result"))||FastpRuntime.integer(session.get("main_passing_reads"),0,Long.MAX_VALUE)!=mainReads(audit)||!Boolean.valueOf(mainReads(audit)>0).equals(session.get("downstream_usable")))throw new IOException("fastp session/result claims differ from replay");FastpRuntime.integer(session.get("producer_java_heap_bytes"),33_554_432,Long.MAX_VALUE);
            if(!warnings(spec,inputs,audit).equals(FastpFiles.utf8(FastpFiles.bytes(root.resolve("WARNINGS.md"),1_048_576))))throw new IOException("Required fastp warnings/recovery actions changed");FastpBundle.removeSuccessfulScratch(scratch);
        }catch(IOException|RuntimeException failure){throw new IOException("Portable fastp replay failed;source bundle unchanged. Preserve verification scratch "+scratch+";correct the named cause and retry verification. "+failure,failure);}
    }
    private static void sameJson(Path path,Object expected,int maximum)throws IOException {if(!SimpleJson.parse(FastpJson.json(expected)).equals(FastpFiles.json(path,maximum)))throw new IOException("Retained fastp metadata differs from recomputation: "+path.getFileName());}
}
