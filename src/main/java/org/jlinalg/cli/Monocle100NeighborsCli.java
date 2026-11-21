/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.Monocle100NeighborsFiles.*;
import static org.jlinalg.cli.Monocle100NeighborsContracts.*;

/** Complete prepared-CDS original RANN execution, explicit Java/original weights and R-free verification. */
public final class Monocle100NeighborsCli {
    private Monocle100NeighborsCli() { }
    static final String SCOPE="Original RANN2.6.2 nearest neighbors from a complete caller-prepared in-memory Monocle1.0.0 CDS. engine=original additionally executes unchanged calculateLW and compares every returned neighbor and binary64 weight with Java; engine=java explicitly constructs weights in Java without original calculateLW's dense feasible-space allocation. No fallback is automatic. Literal slot, cell/point order and first-column/tie behavior remain explicit. No graph learning, response/covariate transformation or statistical test is performed. VERIFY recomputes captured projections/Java weights and selected distances without R, RDS parsing or nearest-neighbor refitting; integrity is not producer authentication. REPLAY reruns original nearest neighbors in NEW output and compares complete original returns/RNG. No whole-tool or paper reproduction is claimed.";
    static final String ACTION="On SKIP3, provide the named exact permitted Linux R4.3.3/Monocle1.0.0/RANN2.6.2/Python runtime or accessible input; if installation/execution is disallowed, retain the dependent skip and state what must be supplied. Downloads/private installation are allowed where permitted; analysis installs nothing. On FAIL2, no complete weight graph is admitted: preserve raw/original/partial/null/failed evidence, worker original-error/error JSON, stdout/stderr and process receipts, correct the named input, source, capacity, numerical or process cause and retry NEW output. Do not substitute editions, choose roots, rename UMAT/UMAP, drop cells/genes, change ties or remove self-links to force success. A weight graph is not an admitted statistical test; current MORAN-LM refuses self-links. Require exit0, COMPLETED.json and analysis.json with weights_admitted=true.";
    private record Verified(Path root,Map<String,Object> parameters,Caps caps,Monocle100NeighborsAudit.Result audit) { }

    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path owned=null;boolean missingDependencyIsSkip=true;
        try {
            if(args.length==1&&args[0].equals("--help")) {
                output.println("Monocle100NeighborsCli RUN --request ASCII_PATH.json\nMonocle100NeighborsCli VERIFY --capture PATH --out NEW\nMonocle100NeighborsCli REPLAY --capture PATH --out NEW --runtime RUNTIME.json\nRUN requires schema_version1, source_cds RDS, runtime JSON, explicit engine original|java, neighbor_graph knn|principal_graph, k, literal reduction_slot, seed, dataset_provenance and NEW out. Runtime schema_version1/profile="+PROFILE+" requires python, rscript, r_home, native_library_path and ordered r_libraries. RUN/REPLAY uses Linux Java/Python; VERIFY is R-free and portable.\n"+SCOPE+"\n"+ACTION);output.flush();return output.checkError()?2:0;
            }
            require(args.length>0,"Choose RUN, VERIFY or REPLAY; read --help");
            if(args[0].equals("RUN")) {
                require(args.length==3&&args[1].equals("--request"),"RUN requires only --request JSON");Request request=request(Path.of(args[2]));RuntimeConfig runtime=runtime(request.runtime());protectRuntimeOutput(request.out(),runtime);
                missingDependencyIsSkip=false;Files.createDirectories(request.out().getParent());owned=Files.createDirectory(request.out());execute(owned,request.source(),request.cds(),request.runtime(),request.parameters(),request.caps(),runtime,null,output);return 0;
            }
            require(args.length==5||args.length==7,"Use the exact VERIFY/REPLAY argument contract");require(args[1].equals("--capture")&&args[3].equals("--out"),"Require --capture PATH --out NEW");
            missingDependencyIsSkip=false;Verified parent=verifyCapture(Path.of(args[2]));Path destination=destination(Path.of(args[4]).toAbsolutePath().normalize(),List.of(parent.root()));
            if(args[0].equals("VERIFY")) {
                require(args.length==5,"VERIFY launches no runtime and accepts no extra fields");Files.createDirectories(destination.getParent());owned=Files.createDirectory(destination);
                write(owned.resolve("WARNINGS.md"),"# Saved-capture verification scope\n\n"+SCOPE+"\n\n"+ACTION+"\n");
                json(owned.resolve("RESULT.json"),Map.of("status","PASS_R_FREE_ORIGINAL_NEIGHBOR_CAPTURE","audit",parent.audit().report(),"R_Java_RDS_or_nearest_neighbor_refit",false,"original_runtime_executed",false));
                output.println("PASS R-free original-neighbor capture verification; no RDS or nearest-neighbor refit. Read WARNINGS.md");output.flush();require(!output.checkError(),"Verification console recording failed");return 0;
            }
            require(args[0].equals("REPLAY")&&args.length==7&&args[5].equals("--runtime"),"REPLAY requires --capture PATH --out NEW --runtime JSON");missingDependencyIsSkip=true;Path runtimePath=regular(Path.of(args[6]));RuntimeConfig runtime=runtime(runtimePath);protectRuntimeOutput(destination,runtime);missingDependencyIsSkip=false;
            Files.createDirectories(destination.getParent());owned=Files.createDirectory(destination);
            execute(owned,parent.root().resolve("source-request.json"),parent.root().resolve("inputs/source-cds.rds"),runtimePath,parent.parameters(),parent.caps(),runtime,parent,output);return 0;
        }catch(Exception|OutOfMemoryError failure) {
            int code=failure instanceof Monocle100NeighborsRuntime.Unavailable||(missingDependencyIsSkip&&(failure instanceof NoSuchFileException||failure instanceof AccessDeniedException))?3:2;
            String status=code==3?"SKIP3_DEPENDENT_NEIGHBOR_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_WEIGHT_GRAPH_ADMITTED";
            if(owned!=null)try {
                if(Files.exists(owned.resolve("analysis.json")))Files.move(owned.resolve("analysis.json"),owned.resolve("unadmitted-analysis.json"));
                json(owned.resolve("analysis.json"),Map.of("status",status,"weights_admitted",false,"scientific_test_admitted",false,"cause",failure.toString()));
                write(owned.resolve("FAILURE.txt"),failure+"\n"+ACTION+"\n");
            }catch(IOException|OutOfMemoryError recordingFailure){error.println("FAIL2 failure recording incomplete: "+recordingFailure);}
            error.println(status+": "+failure+"\n"+ACTION);error.flush();return code;
        }
    }
    private static void protectRuntimeOutput(Path out,RuntimeConfig runtime) throws IOException {
        var paths=new ArrayList<Path>(runtime.libraries());paths.add(runtime.rHome());paths.add(runtime.nativeLibraries());paths.add(runtime.python());paths.add(runtime.rscript());paths.add(runtime.source());
        for(Path path:paths){Path actual=Files.exists(path)?path.toRealPath():path;require(!out.startsWith(actual)&&!actual.startsWith(out),"Output overlaps a protected runtime path");}
    }
    private static void execute(Path root,Path sourceRequest,Path cds,Path runtimePath,Map<String,Object> parameters,Caps caps,RuntimeConfig configured,Verified parent,PrintStream output) throws Exception {
        write(root.resolve("WARNINGS.md"),"# Model scope and required actions\n\n"+SCOPE+"\n\n"+ACTION+"\n");json(root.resolve("analysis.json"),Map.of("status","RUNNING_NO_WEIGHT_GRAPH_ADMITTED","weights_admitted",false,"scientific_test_admitted",false));
        var captured=new ArrayList<Captured>();Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));
        captured.add(capture(regular(sourceRequest),root.resolve("source-request.json"),1_048_576));captured.add(capture(regular(runtimePath),root.resolve("runtime-request.json"),1_048_576));captured.add(capture(regular(cds),root.resolve("inputs/source-cds.rds"),caps.cdsBytes()));
        json(root.resolve("effective-request.json"),parameters);json(root.resolve("worker/parameters.json"),parameters);sources(root,false);
        if(parent!=null) {
            captured.add(capture(parent.root().resolve("worker/original-nearest.rds"),root.resolve("inputs/replay-nearest.rds"),caps.maximumFile()));
            captured.add(capture(parent.root().resolve("worker/RNG.rds"),root.resolve("inputs/replay-RNG.rds"),caps.maximumFile()));
            if(parameters.get("engine").equals("original"))captured.add(capture(parent.root().resolve("worker/original-listw.rds"),root.resolve("inputs/replay-listw.rds"),caps.maximumFile()));
        }
        RuntimeConfig runtime=Monocle100NeighborsRuntime.prepare(configured);protectRuntimeOutput(root,runtime);Monocle100NeighborsRuntime.run(root,runtime,parameters,caps);
        for(Captured file:captured)file.verify();sources(root,true);var audited=Monocle100NeighborsAudit.audit(root,parameters,caps);Monocle100NeighborsAudit.export(root,audited);outputLimits(root,caps,0,0);
        var original=read(root.resolve("worker/report.json"),caps.jsonBytes());StringBuilder warnings=new StringBuilder();
        for(String key:List.of("warnings","messages"))for(Object value:list(original.get(key)))warnings.append(key).append(": ").append(text(value)).append('\n');
        if(audited.weights().selfLinks()>0)warnings.append("SOURCE-SELECTED SELF-LINKS: ").append(audited.weights().selfLinks()).append(". Preserve them; current MORAN-LM refuses this graph. No statistical result is admitted here.\n");
        write(root.resolve("ORIGINAL-WARNINGS.txt"),warnings.toString());
        if(parent!=null) {
            require(Boolean.TRUE.equals(original.get("replayed_complete_original_returns")),"Replay did not compare complete original returns/RNG");
            compareSummary(audited.report(),parent.audit().report());verifyCapture(parent.root());
            json(root.resolve("replay.json"),Map.of("parent_manifest",pin(NextflowFiles.pin(parent.root().resolve("manifest.json"),64*1024*1024,()->{})),"parent_preserved",true,"complete_original_returns_RNG_identical",true));
        }
        for(Captured file:captured)file.verify();sources(root,true);
        Files.move(root.resolve("analysis.json"),root.resolve("initial-status.json"));
        json(root.resolve("analysis.json"),Map.of("status","PASS_WEIGHTS_ADMITTED","engine",parameters.get("engine"),"weights_admitted",true,"scientific_test_admitted",false,"audit",audited.report(),"scope",SCOPE));
        manifest(root,caps.maximumFile(),caps.files());
        byte[] completion=(NextflowJson.json(Map.of("schema_version",1,"engine",ENGINE,"profile",PROFILE,"status","PASS_ORIGINAL_NEIGHBOR_CAPTURE","weights_admitted",true,"scientific_test_admitted",false))+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        outputLimits(root,caps,completion.length,1);
        output.println("PASS "+parameters.get("engine")+" Monocle1.0.0 neighbors/weights: "+audited.cells().size()+" cells, "+audited.weights().arcs().size()+" directed links. Read WARNINGS.md and ORIGINAL-WARNINGS.txt; no statistical test is admitted.");output.flush();require(!output.checkError(),"Console recording failed; no completed weight graph admitted");
        for(Captured file:captured)file.verify();verifyManifest(root,caps.maximumFile(),caps.files());outputLimits(root,caps,completion.length,1);
        Files.write(root.resolve("COMPLETED.json"),completion,java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    private static Verified verifyCapture(Path path) throws IOException {
        require(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS),"Capture directory is missing or linked");Path root=path.toRealPath();var parameters=savedParameters(root);Caps caps=caps(parameters);
        var marker=read(root.resolve("COMPLETED.json"),1_048_576);var names=Set.of("schema_version","engine","profile","status","weights_admitted","scientific_test_admitted");keys(marker,names,names);integer(marker.get("schema_version"),1,1);
        require(ENGINE.equals(marker.get("engine"))&&PROFILE.equals(marker.get("profile"))&&"PASS_ORIGINAL_NEIGHBOR_CAPTURE".equals(marker.get("status"))&&Boolean.TRUE.equals(marker.get("weights_admitted"))&&Boolean.FALSE.equals(marker.get("scientific_test_admitted")),"Incomplete, foreign or conflicting capture marker");
        outputLimits(root,caps,0,0);verifyManifest(root,caps.maximumFile(),caps.files());sources(root,true);var source=read(root.resolve("source-request.json"),1_048_576);require(sourceParameters(source).equals(parameters),"Saved source/effective scientific request differs");
        require(parameters(read(root.resolve("worker/parameters.json"),1_048_576)).equals(parameters),"Worker/effective scientific request differs");
        var before=read(root.resolve("runtime-code-before.json"),16*1024*1024);var after=read(root.resolve("runtime-code-after.json"),16*1024*1024);require(before.equals(after)&&!before.isEmpty(),"Changed saved runtime code identities");
        var process=read(root.resolve("worker/PROCESS.json"),caps.jsonBytes());require("PASS_PROCESS_ONLY".equals(process.get("status"))&&integer(process.get("exit_code"),0,0)==0&&list(process.get("owned_live_after_cleanup")).isEmpty(),"Incomplete original process/cleanup receipt");
        var end=read(root.resolve("worker/SUPERVISOR-END.json"),1_048_576);require(Boolean.FALSE.equals(end.get("alive_after_cleanup"))&&list(end.get("owned_live_after_cleanup")).isEmpty(),"Owned supervisor or descendants survived cleanup");
        var audited=Monocle100NeighborsAudit.audit(root,parameters,caps);var analysis=read(root.resolve("analysis.json"),caps.jsonBytes());require("PASS_WEIGHTS_ADMITTED".equals(analysis.get("status"))&&Boolean.TRUE.equals(analysis.get("weights_admitted"))&&Boolean.FALSE.equals(analysis.get("scientific_test_admitted")),"Capture has no complete admitted weight graph");compareSummary(audited.report(),object(analysis.get("audit")));compareSummary(audited.report(),read(root.resolve("results/audit.json"),caps.jsonBytes()));
        var rows=list(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("results/weights.json"),caps.jsonBytes())),caps.jsonBytes(),40_000_000));require(rows.size()==audited.weights().arcs().size(),"Changed exported complete arc population");StringBuilder table=new StringBuilder("from_cell\tto_cell\tweight\n");
        for(int index=0;index<rows.size();index++) {
            var row=object(rows.get(index));keys(row,Set.of("from","to","weight"),Set.of("from","to","weight"));var arc=audited.weights().arcs().get(index);
            require(integer(row.get("from"),0,audited.cells().size()-1)==arc.from()&&integer(row.get("to"),0,audited.cells().size()-1)==arc.to()&&Double.doubleToRawLongBits(finite(row.get("weight")))==Double.doubleToRawLongBits(arc.weight()),"Changed exported native/original arc field");
            table.append(audited.cells().get(arc.from())).append('\t').append(audited.cells().get(arc.to())).append('\t').append(arc.weight()).append('\n');
        }
        require(NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("results/weights.tsv"),caps.jsonBytes())).equals(table.toString()),"Changed complete literal weight-table projection");
        verifyManifest(root,caps.maximumFile(),caps.files());return new Verified(root,parameters,caps,audited);
    }
    private static void compareSummary(Map<String,Object> wanted,Map<String,Object> actual) throws IOException {
        require(wanted.keySet().equals(actual.keySet()),"Saved audit field population differs");
        for(var row:wanted.entrySet()) {
            Object expected=row.getValue(),got=actual.get(row.getKey());
            if(expected instanceof Number value)require(Double.doubleToLongBits(value.doubleValue())==Double.doubleToLongBits(finite(got)),"Saved numerical audit differs: "+row.getKey());
            else require(expected.equals(got),"Saved audit differs: "+row.getKey());
        }
    }
}
