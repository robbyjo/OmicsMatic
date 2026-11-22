package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FumaToolsCliTest {
    @TempDir Path temp;
    private List<String> args(String output) throws Exception {
        Files.writeString(temp.resolve("genes.tsv"),"ensembl_gene_id\tentrezID\texternal_gene_name\tgene_biotype\tchromosome_name\tstart_position\tend_position\nE1\t1\tA\tpc\t1\t1\t2\nE2\t2\tB\tpc\t1\t3\t4\nE3\t3\tC\tpc\t1\t5\t6\nE4\t4\tD\tpc\t1\t7\t8\n");Files.writeString(temp.resolve("query.txt"),"a\nb\n");Files.writeString(temp.resolve("terms.gmt"),"pair\thttps://example.org/pair\t1\t2\nnull\tNA\t3\t4\n");Files.writeString(temp.resolve("collections.tsv"),"path\nterms.gmt\n");
        return new ArrayList<>(List.of("--method","gene-set","--annotations",temp.resolve("genes.tsv").toString(),"--query",temp.resolve("query.txt").toString(),"--biotypes","all","--gmt-manifest",temp.resolve("collections.tsv").toString(),"--annotation-id","fixture-v1","--exclude-mhc","false","--correction","fdr_tsbky","--adjusted-cutoff","1","--min-overlap","2","--duplicates","error","--out",temp.resolve(output).toString()));
    }
    private int run(List<String> args) {return FumaToolsCli.run(args.toArray(String[]::new),new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    @Test void successfulBundleRetainsAuditWarningsAndHashes() throws Exception {
        assertEquals(0,run(args("done")));Path d=temp.resolve("done");assertTrue(Files.readString(d.resolve("GS.txt")).contains("pair"));assertTrue(Files.readString(d.resolve("WARNINGS.md")).contains("Required action"));assertTrue(Files.readString(d.resolve("session.json")).contains("fixture-v1"));assertTrue(Files.readString(d.resolve("manifest.yaml")).contains("input_sha256"));assertTrue(Files.readString(d.resolve("all-tests.tsv")).contains("log_adjusted_p"));
    }
    @Test void invalidAnnotationIsProminentFailureWithNoAdmittedResults() throws Exception {
        var a=args("bad");Path p=temp.resolve("genes.tsv");Files.writeString(p,Files.readString(p).replace("E2\t2\t","E2\tNA\t"));assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("bad/WARNINGS.md")).contains("Invalid annotation row 3"));assertFalse(Files.exists(temp.resolve("bad/GS.txt")));assertTrue(Files.readString(temp.resolve("bad/analysis-status.json")).contains("failed"));
    }
    @Test void duplicateMembersRequireAnExplicitCountPolicy() throws Exception {
        var a=args("duplicate");Files.writeString(temp.resolve("terms.gmt"),"pair\tNA\t1\t1\t2\n");assertEquals(2,run(a));a.set(a.indexOf("error"),"original-count");a.set(a.size()-1,temp.resolve("counted").toString());assertEquals(0,run(a));assertTrue(Files.readString(temp.resolve("counted/WARNINGS.md")).contains("DUPLICATE GMT MEMBERS COUNTED"));
    }
    @Test void existingDestinationIsPreserved() throws Exception {
        var a=args("existing");Files.createDirectory(temp.resolve("existing"));Files.writeString(temp.resolve("existing/marker"),"keep");assertEquals(2,run(a));assertEquals("keep",Files.readString(temp.resolve("existing/marker")));
    }
    @Test void resourceLimitsAndMissingMhcAnchorsFailInRetainedBundles() throws Exception {
        var a=args("limited");a.addAll(List.of("--max-terms","1"));assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("limited/WARNINGS.md")).contains("GMT term limit"));a=args("mhc");a.set(a.indexOf("false"),"true");assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("mhc/WARNINGS.md")).contains("anchor MOG"));
    }
    @Test void unknownOptionsAndUnsupportedWorkflowFail() throws Exception {
        assertEquals(2,run(List.of("--unknown","x")));assertEquals(2,run(List.of("--method","snp2gene")));assertEquals(0,run(List.of("--help")));
    }
}
