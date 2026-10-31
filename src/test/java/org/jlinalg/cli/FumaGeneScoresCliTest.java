package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FumaGeneScoresCliTest {
    @TempDir Path dir;
    private Map<String,String> options()throws Exception{var recipe=FumaWorkflowTestData.recipe(dir,false);var options=FumaWorkflowTestData.stage(recipe,"mapping");for(String key:new String[]{"genes","candidates","annotations","gene-scores"})options.put(key,dir.resolve(options.get(key)).toString());options.put("method","map-genes");options.put("out",dir.resolve("output").toString());return options;}
    private int run(Map<String,String> options)throws Exception{Path file=dir.resolve("options.json");Files.writeString(file,RegenieRuntime.json(options));return FumaToolsCli.run(new String[]{"--mapping-options-file",file.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    @Test void joinsRawScoresAndRecordsResourceIdentityAndLedgers()throws Exception{var o=options();assertEquals(0,run(o));var data=FumaWorkflowInputs.read(Path.of(o.get("out"),"genes.tsv"),new java.util.LinkedHashMap<>(),new FumaWorkflowInputs.Limits(1000000,100));assertTrue(data.header().contains("score.pLI"));assertEquals("0.123456789012345678901",data.value(data.rows().get(0),"score.pLI"));assertTrue(Files.exists(Path.of(o.get("out"),"gene-score-resource.tsv")));assertTrue(Files.readString(Path.of(o.get("out"),"session.json")).contains("synthetic-scores-v1"));}
    @Test void missingScoreIdRetainDoesNotTurnMissingIntoZero()throws Exception{var o=options();Files.writeString(dir.resolve("scores.tsv"),"ensg\tsymbol\tchr\tpos\tpLI\nG1\tONE\t1\t1\t.1\n");o.put("score-missing","retain");assertEquals(0,run(o));var data=FumaWorkflowInputs.read(Path.of(o.get("out"),"genes.tsv"),new java.util.LinkedHashMap<>(),new FumaWorkflowInputs.Limits(1000000,100));assertEquals("NA",data.value(data.rows().get(1),"score.pLI"));assertTrue(Files.readString(Path.of(o.get("out"),"WARNINGS.md")).contains("MISSING GENE-SCORE ANNOTATIONS"));}
    @Test void missingScoreErrorWithholdsCompletedGeneTable()throws Exception{var o=options();Files.writeString(dir.resolve("scores.tsv"),"ensg\tsymbol\tchr\tpos\tpLI\nG1\tONE\t1\t1\t.1\n");assertEquals(2,run(o));assertFalse(Files.exists(Path.of(o.get("out"),"genes.tsv")));assertTrue(Files.readString(Path.of(o.get("out"),"WARNINGS.md")).contains("Required action"));}
    @Test void firstRowPolicyIsExplicitAndResourceWideDuplicatesAreAudited()throws Exception{var o=options();Files.writeString(dir.resolve("scores.tsv"),Files.readString(dir.resolve("scores.tsv"))+"G1\tDUP\t1\t1\t.9\t9\n");o.put("score-duplicates","first");assertEquals(0,run(o));assertTrue(Files.readString(Path.of(o.get("out"),"gene-score-resource.tsv")).contains("duplicate-ignored-first"));}
    @Test void payloadBudgetFailureHasNoAdmittedGeneResults()throws Exception{var o=options();o.put("max-score-cells","1");assertEquals(2,run(o));assertFalse(Files.exists(Path.of(o.get("out"),"genes.tsv")));assertTrue(Files.readString(Path.of(o.get("out"),"session.json")).contains("budget exceeded"));}
    @Test void disabledScoresPreserveOriginalSchemaAndRejectUnusedPolicies()throws Exception{var o=options();o.remove("gene-scores");assertEquals(2,run(o));o.put("out",dir.resolve("without-scores").toString());o.remove("gene-score-id");o.remove("score-duplicates");o.remove("score-missing");assertEquals(0,run(o));assertFalse(Files.readString(Path.of(o.get("out"),"genes.tsv")).contains("score.pLI"));}
}
