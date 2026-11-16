/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.jlinalg.workflow.SnakemakeBenchmarks;
import static org.jlinalg.cli.SnakemakeBenchmarkFiles.*;

/** Native, complete-input benchmark aggregation with independently recomputed replay. */
public final class SnakemakeBenchmarkCli {
    private static final String NOTICE="""
        # Snakemake benchmark scope, warnings and required actions

        Native Java aggregation of the selected Snakemake 9.27.1 standard/extended TSV
        format: SUM of recorded cpu_time seconds and MAXIMUM of recorded job max_rss
        in MiB, separately for each explicit pipeline/replicate. Summed job wall time
        is not elapsed pipeline time; maximum job RSS is not concurrent pipeline peak
        memory. Source sampling and two-decimal formatting limit recorded precision.
        A short job may have NA measurements; these are not zeros. Incomplete or
        nonfinite required metrics, duplicate jobs/rows, omitted repeats and unlisted
        benchmark files admit no aggregate. No repeat pooling or statistical test runs.

        This command reads supplied benchmarks; it does not execute Snakemake,
        measure processes, establish that a caller's job list covers an entire DAG,
        certify successful biological jobs, or reproduce unreported author settings.
        None of the six reviewed paper roles establishes engine version 9.27.1.
        The separate original-engine adapter is still under development. No additional
        catalog credit is claimed here. Build current source; released v0.4.0 lacks
        this command. Whole-paper reproduction and the 250-paper goal remain open.

        Require root COMPLETED.properties. --verify validates complete captured input,
        code and output membership and independently recomputes the native summary in
        NEW output. Hashes establish integrity, not authenticity. Missing/forbidden
        dependencies for upstream execution require SKIP3 and skipping that dependent
        analysis; this native operation needs no external Snakemake installation.
        FAIL2 admits no complete result: retain raw inputs and every failed attempt,
        inspect FAILURE.txt and diagnostics, correct the named cause and retry NEW
        output with adequate permitted capacities. Never impute missing measurements,
        substitute formats/units, drop jobs/repeats, or refresh saved code fingerprints.
        Input/work capacities and cooperative wall checks are not operating-system
        quotas; use a JVM/host with adequate permitted memory and filesystem capacity.
        """;
    private record Audit(int jobs,int tables,int groups,String summary,String measurements) { }
    private SnakemakeBenchmarkCli() { }
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    static int run(String[] supplied,PrintStream output,PrintStream error) {
        Path owned=null;
        try {
            String[] args=supplied.length>0&&supplied[0].equals("benchmark")?Arrays.copyOfRange(supplied,1,supplied.length):supplied;
            if(args.length==1&&args[0].equals("--help")) { output.println(help());return 0; }
            var options=new TreeMap<String,String>();
            for(int index=0;index<args.length;index+=2)if(index+1>=args.length||!Set.of("--request","--verify","--out").contains(args[index])||options.putIfAbsent(args[index],args[index+1])!=null)throw new IllegalArgumentException("Use unique option/value pairs; see --help");
            boolean replay=options.containsKey("--verify");
            if(!options.keySet().equals(replay?Set.of("--verify","--out"):Set.of("--request","--out")))throw new IllegalArgumentException("Use --request FILE --out NEW or --verify BUNDLE --out NEW");
            for(String value:options.values())if(value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Control characters in paths are not qualified");
            Path destination=future(Path.of(options.get("--out")).toAbsolutePath().normalize());
            if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists; preserve it and choose a NEW directory");
            Path old=replay?Path.of(options.get("--verify")).toRealPath():null;overlap(destination,old);
            Config c;Path request=null,manifest=null,benchmarks=null;Map<String,String> source=null;String manifestPin=null;
            if(replay)c=checkBundle(old);
            else {
                request=Path.of(options.get("--request")).toRealPath();c=config(load(request,1_048_576));
                manifest=resolve(request.getParent(),c.get("manifest")).toRealPath();benchmarks=resolve(request.getParent(),c.get("benchmark_directory")).toRealPath();
                overlap(destination,request);overlap(destination,manifest);overlap(destination,benchmarks);
            }
            Deadline deadline=new Deadline(c.n("wall_ms"));
            if(!replay) { source=tree(benchmarks,c.n("max_input_bytes"),c.i("max_files"),deadline);manifestPin=pin(manifest,deadline); }
            Files.createDirectories(destination.getParent());Files.createDirectory(destination);owned=destination.toRealPath();
            write(owned.resolve("WARNINGS.md"),NOTICE);error.println("WARNING: native Snakemake benchmark aggregation only. Read "+owned.resolve("WARNINGS.md"));error.flush();if(error.checkError())throw new IOException("Warning recording failed");
            Files.createDirectory(owned.resolve("inputs"));
            if(replay) {
                Map<String,String> captured=tree(old.resolve("inputs"),c.n("max_input_bytes"),c.i("max_files"),deadline);capture(old.resolve("inputs"),owned.resolve("inputs"),captured,c,deadline);
                copy(old.resolve("request.properties"),owned.resolve("request.properties"),1_048_576,deadline);
                write(owned.resolve("REPLAY_PARENT.tsv"),"manifest_sha256\n"+hash(old.resolve("FILES.tsv"))+"\n");
            }else {
                copy(request,owned.resolve("request.properties"),1_048_576,deadline);
                copy(manifest,owned.resolve("inputs/jobs.tsv"),c.n("max_manifest_bytes"),deadline);
                Files.createDirectory(owned.resolve("inputs/benchmarks"));capture(benchmarks,owned.resolve("inputs/benchmarks"),source,c,deadline);
                if(!source.equals(tree(benchmarks,c.n("max_input_bytes"),c.i("max_files"),deadline))||!manifestPin.equals(pin(manifest,deadline)))throw new IOException("Original benchmark input changed during capture; no accepted summary");
                if(!pin(request,deadline).equals(pin(owned.resolve("request.properties"),deadline))||!config(load(owned.resolve("request.properties"),1_048_576)).equals(c))throw new IOException("Request changed during capture");
            }
            properties(owned.resolve("effective.properties"),c.values());
            String inputPins=render(tree(owned.resolve("inputs"),c.n("max_input_bytes"),c.i("max_files"),deadline));write(owned.resolve("INPUTS.tsv"),inputPins);write(owned.resolve("CODE.tsv"),render(code()));
            Audit audit=audit(owned,c,deadline);write(owned.resolve("SUMMARY.tsv"),audit.summary());write(owned.resolve("MEASUREMENTS.tsv"),audit.measurements());
            if(replay&&(!audit.summary().equals(Files.readString(old.resolve("SUMMARY.tsv")))||!audit.measurements().equals(Files.readString(old.resolve("MEASUREMENTS.tsv")))))throw new IOException("Independent native recomputation differs; retain both bundles and investigate");
            if(!inputPins.equals(render(tree(owned.resolve("inputs"),c.n("max_input_bytes"),c.i("max_files"),deadline)))||!Files.readString(owned.resolve("CODE.tsv")).equals(render(code())))throw new IOException("Captured inputs or implementation changed during aggregation");
            write(owned.resolve("FILES.tsv"),render(tree(owned,c.n("max_work_bytes"),c.i("max_files"),deadline)));
            var marker=marker(audit);marker.put("kind",replay?"genuine-native-recomputation":"native-benchmark-aggregate");marker.put("manifest_sha256",hash(owned.resolve("FILES.tsv")));marker.put("parent_manifest_sha256",replay?hash(old.resolve("FILES.tsv")):"none");properties(owned.resolve("COMPLETED.properties"),marker);
            tree(owned,c.n("max_work_bytes"),c.i("max_files"),deadline);output.println("PASS "+PROFILE+" jobs="+audit.jobs()+" groups="+audit.groups()+" "+owned);output.flush();if(output.checkError())throw new IOException("Success recording failed");return 0;
        }catch(Throwable failure) {
            if(owned!=null)try {
                Path marker=owned.resolve("COMPLETED.properties");if(Files.exists(marker))Files.move(marker,owned.resolve("INVALIDATED-"+UUID.randomUUID()+"-COMPLETED.properties"));
                write(owned.resolve("FAILURE.txt"),failure+"\n\n"+NOTICE);Files.writeString(owned.resolve("WARNINGS.md"),"\n# FAIL2 — NO RESULT ADMITTED\n\n"+failure+"\nPreserve this attempt; correct the named cause and retry NEW output.\n",StandardOpenOption.APPEND);
            }catch(Throwable recording){error.println("FAIL2 diagnostic recording also failed: "+recording);}
            error.println("FAIL2_SNAKEMAKE_BENCHMARK_UNADMITTED "+failure+"\n"+NOTICE);error.flush();return 2;
        }
    }
    private static Audit audit(Path root,Config c,Deadline deadline) throws IOException {
        var assignments=assignments(root.resolve("inputs/jobs.tsv"),c);var tables=new TreeMap<String,List<SnakemakeBenchmarks.Measurement>>();var listed=new java.util.TreeSet<String>();
        for(var assignment:assignments)listed.add(assignment.table());
        Map<String,String> tree=tree(root.resolve("inputs/benchmarks"),c.n("max_input_bytes"),c.i("max_files"),deadline);var actual=new java.util.TreeSet<String>();for(var entry:tree.entrySet())if(entry.getValue().startsWith("F\t"))actual.add(entry.getKey());
        if(!listed.equals(actual))throw new IOException("Benchmark files differ from the complete job manifest; unlisted/missing files admit no aggregate");
        int rows=0;
        for(String name:listed) { deadline.check();var measurements=SnakemakeBenchmarks.read(root.resolve("inputs/benchmarks").resolve(name),new SnakemakeBenchmarks.Limits(c.n("max_input_bytes"),c.i("max_rows"),c.i("max_line_characters")));rows=Math.addExact(rows,measurements.size());if(rows>c.i("max_rows"))throw new IOException("Complete benchmark row count exceeds max_rows");tables.put(name,measurements); }
        var summaries=SnakemakeBenchmarks.aggregate(tables,assignments);deadline.check();
        var summary=new StringBuilder("pipeline\treplicate\tjobs\tcpu_seconds\tmax_job_rss_mib\tsummed_job_wall_seconds\n");
        for(var entry:summaries.entrySet()) { var value=entry.getValue();summary.append(entry.getKey().pipeline()).append('\t').append(entry.getKey().replicate()).append('\t').append(value.jobs()).append('\t').append(decimal(value.cpuSeconds())).append('\t').append(decimal(value.maxJobRssMiB())).append('\t').append(decimal(value.summedJobWallSeconds())).append('\n'); }
        var measurements=new StringBuilder("pipeline\treplicate\tjob\tbenchmark\trow\twall_seconds\tcpu_seconds\tmax_rss_mib\n");
        for(var assignment:assignments) { var value=tables.get(assignment.table()).get(assignment.row()-1);measurements.append(assignment.pipeline()).append('\t').append(assignment.replicate()).append('\t').append(assignment.job()).append('\t').append(assignment.table()).append('\t').append(assignment.row()).append('\t').append(decimal(value.wallSeconds())).append('\t').append(decimal(value.cpuSeconds())).append('\t').append(decimal(value.maxRssMiB())).append('\n'); }
        return new Audit(assignments.size(),tables.size(),summaries.size(),summary.toString(),measurements.toString());
    }
    private static Config checkBundle(Path root) throws IOException {
        Config c=config(load(root.resolve("effective.properties"),1_048_576));Deadline deadline=new Deadline(c.n("wall_ms"));
        if(Files.exists(root.resolve("FAILURE.txt")))throw new IOException("A failed attempt is not an admitted bundle");
        var marker=load(root.resolve("COMPLETED.properties"),65_536);Map<String,String> files=tree(root,c.n("max_work_bytes"),c.i("max_files"),deadline);files.remove("FILES.tsv");files.remove("COMPLETED.properties");
        if(!render(files).equals(Files.readString(root.resolve("FILES.tsv")))||!hash(root.resolve("FILES.tsv")).equals(marker.getProperty("manifest_sha256")))throw new IOException("Complete saved bundle bytes/membership differ");
        if(!Files.readString(root.resolve("INPUTS.tsv")).equals(render(tree(root.resolve("inputs"),c.n("max_input_bytes"),c.i("max_files"),deadline)))||!Files.readString(root.resolve("CODE.tsv")).equals(render(code())))throw new IOException("Saved captured inputs or implementation differ; never refresh fingerprints");
        if(!config(load(root.resolve("request.properties"),1_048_576)).equals(c))throw new IOException("Saved original/effective requests differ");
        Audit audited=audit(root,c,deadline);var expected=marker(audited);expected.put("kind",marker.getProperty("kind"));expected.put("manifest_sha256",marker.getProperty("manifest_sha256"));expected.put("parent_manifest_sha256",marker.getProperty("parent_manifest_sha256"));
        var actual=new TreeMap<String,String>();for(String key:marker.stringPropertyNames())actual.put(key,marker.getProperty(key));
        if(!expected.equals(actual)||!audited.summary().equals(Files.readString(root.resolve("SUMMARY.tsv")))||!audited.measurements().equals(Files.readString(root.resolve("MEASUREMENTS.tsv"))))throw new IOException("Saved completion/summary differs from complete independent native audit");
        String kind=marker.getProperty("kind"),parent=marker.getProperty("parent_manifest_sha256");
        if("native-benchmark-aggregate".equals(kind)) { if(!"none".equals(parent)||Files.exists(root.resolve("REPLAY_PARENT.tsv")))throw new IOException("Original aggregation cannot declare a replay parent"); }
        else if(!"genuine-native-recomputation".equals(kind)||parent==null||!parent.matches("[0-9a-f]{64}")||!Files.readString(root.resolve("REPLAY_PARENT.tsv")).equals("manifest_sha256\n"+parent+"\n"))throw new IOException("Saved replay kind/parent binding differs");
        return c;
    }
    private static Map<String,String> marker(Audit audit) { var result=new TreeMap<String,String>();result.put("profile",PROFILE);result.put("jobs",Integer.toString(audit.jobs()));result.put("tables",Integer.toString(audit.tables()));result.put("groups",Integer.toString(audit.groups()));return result; }
    private static String decimal(java.math.BigDecimal value) { return value.stripTrailingZeros().toString(); }
    private static String hash(Path path) throws IOException { return pin(path,new Deadline(Long.MAX_VALUE)).split("\t")[2]; }
    private static void capture(Path source,Path target,Map<String,String> members,Config c,Deadline deadline) throws IOException {
        for(var entry:members.entrySet()) { deadline.check();Path from=source.resolve(entry.getKey()),to=target.resolve(entry.getKey());if(entry.getValue().startsWith("D\t"))Files.createDirectories(to);else { copy(from,to,c.n("max_input_bytes"),deadline);if(!pin(to,deadline).equals(entry.getValue()))throw new IOException("Captured source changed: "+from); } }
    }
    private static Path resolve(Path parent,String value) { Path path=Path.of(value);return path.isAbsolute()?path:parent.resolve(path); }
    private static Path future(Path path) throws IOException { if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))return path.toRealPath();return future(path.getParent()).resolve(path.getFileName()); }
    private static void overlap(Path destination,Path protectedPath) throws IOException { if(protectedPath!=null&&(destination.startsWith(protectedPath)||protectedPath.startsWith(destination)))throw new IOException("New output overlaps a protected input/bundle: "+protectedPath); }
    private static String help() { return "snakemake-tools benchmark --request REQUEST.properties --out NEW\nsnakemake-tools benchmark --verify SAVED_BUNDLE --out NEW\n\n"+NOTICE; }
}
