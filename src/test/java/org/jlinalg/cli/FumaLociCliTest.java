package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FumaLociCliTest {
    @TempDir Path temp;
    private List<String> args(String output) throws Exception {
        Files.writeString(temp.resolve("gwas.tsv"),"chr\tpos\tnon_effect_allele\teffect_allele\trsID\tp\tbeta\n1\t100\tA\tG\trs1\t1e-10\t.123456789012345\n1\t200\tA\tG\trs2\t0.049999999999999996\t-.2\n");Files.writeString(temp.resolve("ref.tsv"),"chr\tpos\trsID\tA1\tA2\tMAF\n1\t100\trs1\tG\tA\t.25\n1\t200\trs2\tG\tA\t.25\n");Files.writeString(temp.resolve("ld.tsv"),"chr\tpos1\tpos2\tr2\n1\t100\t200\t.8\n");return new ArrayList<>(List.of("--method","loci","--gwas",temp.resolve("gwas.tsv").toString(),"--reference",temp.resolve("ref.tsv").toString(),"--ld",temp.resolve("ld.tsv").toString(),"--reference-id","synthetic-GRCh37-EUR-v1","--ld-floor",".01","--lead-p","5e-8","--candidate-p",".05","--maf","0","--independent-r2",".6","--lead-r2",".1","--merge-distance-bp","1000","--merge-boundary","strict","--include-reference","true","--mhc","none","--add-independent","true","--ties","position","--unmatched-reference","error","--empty","error","--out",temp.resolve(output).toString()));
    }
    private int run(List<String> args) {return FumaToolsCli.run(args.toArray(String[]::new),new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    private static void set(List<String> args,String name,String value) {args.set(args.indexOf("--"+name)+1,value);}
    @Test void successfulBundlePreservesOriginalPExtrasHashesAndWarnings() throws Exception {
        assertEquals(0,run(args("done")));Path d=temp.resolve("done");assertTrue(Files.readString(d.resolve("candidates.tsv")).contains("0.049999999999999996"));assertTrue(Files.readString(d.resolve("input-gwas.tsv")).contains(".123456789012345"));assertTrue(Files.readString(d.resolve("WARNINGS.md")).contains("STRICT P BOUNDARY"));assertTrue(Files.readString(d.resolve("WARNINGS.md")).contains("CONSISTENT LOCUS COMPONENTS"));assertTrue(Files.readString(d.resolve("manifest.yaml")).contains("input_sha256"));assertTrue(Files.readString(d.resolve("session.json")).contains("synthetic-GRCh37-EUR-v1"));
    }
    @Test void missingReferenceRowsFailOrAreExplicitlyExcluded() throws Exception {
        var a=args("bad-ref");Files.writeString(temp.resolve("gwas.tsv"),Files.readString(temp.resolve("gwas.tsv"))+"1\t999\tA\tG\trs3\t2e-10\t.3\n");assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("bad-ref/WARNINGS.md")).contains("Unmatched GWAS/reference"));assertTrue(Files.exists(temp.resolve("bad-ref/partial/gwas-disposition.tsv")));set(a,"unmatched-reference","exclude");set(a,"out",temp.resolve("excluded").toString());assertEquals(0,run(a));assertTrue(Files.readString(temp.resolve("excluded/WARNINGS.md")).contains("GWAS/REFERENCE MISMATCH"));
    }
    @Test void inadequateLdFloorAndMissingHeadersAreProminentFailures() throws Exception {
        var a=args("floor");set(a,"ld-floor",".2");assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("floor/WARNINGS.md")).contains("Stored LD floor"));a=args("headers");Files.writeString(temp.resolve("ld.tsv"),"wrong\n");assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("headers/WARNINGS.md")).contains("Missing TSV column"));
    }
    @Test void emptyAnalysisRequiresExplicitRetention() throws Exception {
        var a=args("empty-error");set(a,"lead-p","1e-20");assertEquals(2,run(a));set(a,"empty","retain");set(a,"out",temp.resolve("empty-kept").toString());assertEquals(0,run(a));assertTrue(Files.readString(temp.resolve("empty-kept/analysis-status.json")).contains("completed-empty"));assertEquals(1,Files.readAllLines(temp.resolve("empty-kept/loci.tsv")).size());
    }
    @Test void optionsCarrierIsHashedAndAllowsExplicitOverride() throws Exception {
        var a=args("carrier-original");var map=new LinkedHashMap<String,String>();for (int i=0;i<a.size();i+=2) map.put(a.get(i).substring(2),a.get(i+1));Path p=temp.resolve("options.json");Files.writeString(p,RegenieRuntime.json(map));assertEquals(0,run(List.of("--locus-options-file",p.toString(),"--out",temp.resolve("carrier-override").toString())));assertTrue(Files.readString(temp.resolve("carrier-override/session.json")).contains(FollowupSupport.hash(p)));Files.writeString(p,"{\"method\":\"loci\",\"ld-floor\":0.1}");assertEquals(2,run(List.of("--locus-options-file",p.toString())));
    }
    @Test void existingDestinationAndInputBudgetsPreserveData() throws Exception {
        var a=args("existing");Files.createDirectory(temp.resolve("existing"));Files.writeString(temp.resolve("existing/marker"),"keep");assertEquals(2,run(a));assertEquals("keep",Files.readString(temp.resolve("existing/marker")));a=args("budget");a.addAll(List.of("--max-pairs","0"));assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("budget/WARNINGS.md")).contains("TSV row budget"));
    }
    @Test void unsafePredefinedAndReferenceAmbiguityDoNotProduceResults() throws Exception {
        var a=args("seed");Files.writeString(temp.resolve("seeds.tsv"),"uid\n1:999:A:G\n");a.addAll(List.of("--predefined",temp.resolve("seeds.tsv").toString()));assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("seed/WARNINGS.md")).contains("Predefined UID is absent"));a=args("ambiguous");Files.writeString(temp.resolve("ref.tsv"),Files.readString(temp.resolve("ref.tsv"))+"1\t100\trs3\tT\tA\t.2\n");assertEquals(2,run(a));assertTrue(Files.readString(temp.resolve("ambiguous/WARNINGS.md")).contains("Ambiguous reference coordinate"));
    }
    @Test void helpRoutingAndUnknownOptionsAreExplicit() {
        assertEquals(0,run(List.of("--method","loci","--help")));assertEquals(2,run(List.of("--method","loci","--unknown","x")));assertEquals(0,run(List.of("--help")));
    }
}
