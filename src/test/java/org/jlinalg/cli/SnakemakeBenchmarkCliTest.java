/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeBenchmarkCliTest {
    @TempDir Path temp;
    private static final String HEADER=String.join("\t",org.jlinalg.workflow.SnakemakeBenchmarks.STANDARD)+"\n";
    private record Call(int exit,String stdout,String stderr) { }
    private static String row(String wall,String rss,String cpu) { return wall+"\t0:00:01\t"+rss+"\t100\t10\t8\t0\t0\t0.5\t"+cpu+"\n"; }
    private Call call(String...args) {
        var out=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
        int exit=JLinAlgCli.run(args,new PrintStream(out),new PrintStream(error));return new Call(exit,out.toString(java.nio.charset.StandardCharsets.UTF_8),error.toString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private Path inputs() throws Exception {
        Files.createDirectories(temp.resolve("benchmarks/nested"));
        Files.writeString(temp.resolve("benchmarks/a.tsv"),HEADER+row("1.5","32.25","0.10")+row("4.25","17.5","0.20"));
        Files.writeString(temp.resolve("benchmarks/nested/b.tsv"),HEADER+row("5","70","2.5")+row("6","80","3.75"));
        Files.writeString(temp.resolve("jobs.tsv"),"pipeline\treplicate\tjob\tbenchmark\trow\nα\tfirst\talign\ta.tsv\t1\nα\tfirst\tcount\tnested/b.tsv\t1\nα\tsecond\talign\ta.tsv\t2\nα\tsecond\tcount\tnested/b.tsv\t2\n");
        return Files.writeString(temp.resolve("request.properties"),"profile=snakemake-benchmark-9.27.1-v1\nmanifest=jobs.tsv\nbenchmark_directory=benchmarks\nmax_input_bytes=1000000\nmax_manifest_bytes=10000\nmax_work_bytes=10000000\nmax_files=100\nmax_rows=1000\nmax_line_characters=10000\nwall_ms=20000\n");
    }
    private Path run() throws Exception { Path request=inputs(),out=temp.resolve("run");Call result=call("snakemake-tools","benchmark","--request",request.toString(),"--out",out.toString());assertEquals(0,result.exit(),result.stderr());return out; }
    private Call verify(Path bundle,String out) { return call("snakemake-tools","benchmark","--verify",bundle.toString(),"--out",temp.resolve(out).toString()); }
    private void reseal(Path root) throws Exception {
        var pins=SnakemakeBenchmarkFiles.tree(root,10000000,100,new SnakemakeBenchmarkFiles.Deadline(20000));pins.remove("FILES.tsv");pins.remove("COMPLETED.properties");
        Files.writeString(root.resolve("FILES.tsv"),SnakemakeBenchmarkFiles.render(pins));String sha=SnakemakeBenchmarkFiles.pin(root.resolve("FILES.tsv"),new SnakemakeBenchmarkFiles.Deadline(20000)).split("\t")[2];
        String marker=Files.readString(root.resolve("COMPLETED.properties"));Files.writeString(root.resolve("COMPLETED.properties"),marker.replaceFirst("(?m)^manifest_sha256=[0-9a-f]+$","manifest_sha256="+sha));
    }
    @Test void dispatchProducesSeparateReplicateSummariesWithAllCapturedInputs() throws Exception {
        Path run=run();assertEquals("pipeline\treplicate\tjobs\tcpu_seconds\tmax_job_rss_mib\tsummed_job_wall_seconds\nα\tfirst\t2\t2.6\t7E+1\t6.5\nα\tsecond\t2\t3.95\t8E+1\t10.25\n",Files.readString(run.resolve("SUMMARY.tsv")));
        assertTrue(Files.isRegularFile(run.resolve("inputs/benchmarks/nested/b.tsv")));assertEquals(Files.readString(temp.resolve("jobs.tsv")),Files.readString(run.resolve("inputs/jobs.tsv")));
        assertTrue(Files.readString(run.resolve("WARNINGS.md")).contains("FAIL2"));assertTrue(Files.isRegularFile(run.resolve("COMPLETED.properties")));
    }
    @Test void genuineReplayWorksAfterOriginalCallerInputsAreRenamedAndAgainFromReplay() throws Exception {
        Path run=run();Files.move(temp.resolve("benchmarks"),temp.resolve("preserved-original-benchmarks"));Files.move(temp.resolve("jobs.tsv"),temp.resolve("preserved-original-jobs.tsv"));
        Call replay=verify(run,"replay");assertEquals(0,replay.exit(),replay.stderr());assertEquals(Files.readString(run.resolve("SUMMARY.tsv")),Files.readString(temp.resolve("replay/SUMMARY.tsv")));
        Call again=verify(temp.resolve("replay"),"replay-again");assertEquals(0,again.exit(),again.stderr());
    }
    @Test void selfConsistentFileManifestCannotAdmitAnIncorrectSummary() throws Exception {
        Path run=run();Files.writeString(run.resolve("SUMMARY.tsv"),Files.readString(run.resolve("SUMMARY.tsv")).replace("\t2.6\t","\t999\t"));reseal(run);
        Call result=verify(run,"rejected");assertEquals(2,result.exit());assertTrue(result.stderr().contains("independent native audit"));assertFalse(Files.exists(temp.resolve("rejected/COMPLETED.properties")));
    }
    @Test void completionCountsAndUnknownFieldsAreNotTrusted() throws Exception {
        Path run=run();String original=Files.readString(run.resolve("COMPLETED.properties"));
        Files.writeString(run.resolve("COMPLETED.properties"),original.replace("jobs=4","jobs=3"));assertEquals(2,verify(run,"bad-count").exit());
        Files.writeString(run.resolve("COMPLETED.properties"),original+"unrecognized=claim\n");assertEquals(2,verify(run,"bad-field").exit());
    }
    @Test void unlistedAndChangedCapturedFilesPreventReplay() throws Exception {
        Path run=run();Files.writeString(run.resolve("unexpected.txt"),"not in saved complete manifest\n");assertEquals(2,verify(run,"extra").exit());
        Files.writeString(run.resolve("inputs/benchmarks/a.tsv"),"changed\n");assertEquals(2,verify(run,"changed").exit());
    }
    @Test void missingMeasurementsPreserveRawSourceAndProminentFailureWithoutMarker() throws Exception {
        Path request=inputs();Files.writeString(temp.resolve("benchmarks/a.tsv"),HEADER+row("1","NA","NA"));Path out=temp.resolve("missing");
        Call result=call("snakemake-tools","benchmark","--request",request.toString(),"--out",out.toString());assertEquals(2,result.exit());assertTrue(result.stderr().contains("No zero imputation"));
        assertFalse(Files.exists(out.resolve("COMPLETED.properties")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("NO RESULT ADMITTED"));assertTrue(Files.readString(out.resolve("inputs/benchmarks/a.tsv")).contains("NA"));
    }
    @Test void completeManifestRejectsOmittedRepeatsDuplicateJobsAndUnlistedTables() throws Exception {
        Path request=inputs();String original=Files.readString(temp.resolve("jobs.tsv"));
        Files.writeString(temp.resolve("jobs.tsv"),original.replace("α\tsecond\tcount\tnested/b.tsv\t2\n",""));assertEquals(2,call("snakemake-tools","benchmark","--request",request.toString(),"--out",temp.resolve("omitted").toString()).exit());
        Files.writeString(temp.resolve("jobs.tsv"),original.replace("α\tfirst\tcount","α\tfirst\talign"));assertEquals(2,call("snakemake-tools","benchmark","--request",request.toString(),"--out",temp.resolve("duplicate").toString()).exit());
        Files.writeString(temp.resolve("jobs.tsv"),original);Files.writeString(temp.resolve("benchmarks/unlisted.tsv"),HEADER+row("1","2","3"));assertEquals(2,call("snakemake-tools","benchmark","--request",request.toString(),"--out",temp.resolve("unlisted").toString()).exit());
    }
    @Test void existingOutputAndInputOverlapCannotOverwriteInputs() throws Exception {
        Path request=inputs();Path out=Files.createDirectory(temp.resolve("existing"));Files.writeString(out.resolve("preserved.txt"),"preserve me\n");
        assertEquals(2,call("snakemake-tools","benchmark","--request",request.toString(),"--out",out.toString()).exit());assertEquals("preserve me\n",Files.readString(out.resolve("preserved.txt")));
        assertEquals(2,call("snakemake-tools","benchmark","--request",request.toString(),"--out",temp.resolve("benchmarks/new-output").toString()).exit());assertFalse(Files.exists(temp.resolve("benchmarks/new-output")));
    }
    @Test void duplicateRequestFieldsAndPathTraversalCannotChangeMembership() throws Exception {
        Path request=inputs();String original=Files.readString(request);Files.writeString(request,original+"wall_ms=30000\n");assertEquals(2,call("snakemake-tools","benchmark","--request",request.toString(),"--out",temp.resolve("duplicate-request").toString()).exit());
        Files.writeString(request,original);Files.writeString(temp.resolve("jobs.tsv"),Files.readString(temp.resolve("jobs.tsv")).replace("nested/b.tsv","../b.tsv"));assertEquals(2,call("snakemake-tools","benchmark","--request",request.toString(),"--out",temp.resolve("traversal").toString()).exit());
    }
    @Test void workCapacitiesRejectWithoutPartialAcceptedResult() throws Exception {
        Path request=inputs();Files.writeString(request,Files.readString(request).replace("max_work_bytes=10000000","max_work_bytes=1"));Path out=temp.resolve("capacity");
        Call result=call("snakemake-tools","benchmark","--request",request.toString(),"--out",out.toString());assertEquals(2,result.exit());assertFalse(Files.exists(out.resolve("COMPLETED.properties")));assertTrue(Files.isRegularFile(out.resolve("FAILURE.txt")));
    }
    @Test void actualHardLinksAreRejectedEvenWhenJavaBasicFileKeysAreUnavailable() throws Exception {
        Path request=inputs();Path link=temp.resolve("benchmarks/nested/b.tsv");Files.move(link,temp.resolve("preserved-b.tsv"));Files.createLink(link,temp.resolve("benchmarks/a.tsv"));
        assertTrue(Files.isSameFile(link,temp.resolve("benchmarks/a.tsv")));
        Call result=call("snakemake-tools","benchmark","--request",request.toString(),"--out",temp.resolve("hard-link").toString());assertEquals(2,result.exit());assertTrue(result.stderr().contains("Hard-linked duplicate"));assertFalse(Files.exists(temp.resolve("hard-link/COMPLETED.properties")));
    }
}
