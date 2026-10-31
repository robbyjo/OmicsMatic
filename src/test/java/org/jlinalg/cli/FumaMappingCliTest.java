package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FumaMappingCliTest {
    @TempDir Path temp;
    private Map<String,String> prepare()throws Exception {
        Files.writeString(temp.resolve("genes.tsv"),"ensembl_gene_id\texternal_gene_name\tchromosome_name\tstart_position\tend_position\tstrand\tgene_biotype\nG1\tONE\t1\t100\t200\t+\tprotein_coding\nG2\tTWO\t1\t150\t250\t-\tprotein_coding\n");
        Files.writeString(temp.resolve("candidates.tsv"),"uid\trsID\tchr\tpos\tgwasP\tCADD\tRDB\tlocus\tindependent_rsIDs\nu1\trs1\t1\t150\t1e-10\t10\t2\t1\trs1\n");
        Files.writeString(temp.resolve("annotations.tsv"),"uid\tCADD\tRDB\tstate.brain\tbinary.enhancer\nu1\t20\t1\t7\t1\n");
        Files.writeString(temp.resolve("eqtl.tsv"),"uid\tgene\tdb\ttissue\tp\tFDR\talignedDirection\nu1\tG2\tGTEx\tBrain.txt.gz\t0.9\t0.99\t+\n");
        var o=new LinkedHashMap<String,String>();o.put("method","map-genes");for(String name:new String[]{"genes","candidates","annotations"})o.put(name,temp.resolve(name+".tsv").toString());o.put("annotation-id","synthetic-GRCh37");o.put("biotypes","all");o.put("mhc","keep");o.put("mhc-extension","none");o.put("positional","window");o.put("window-bp","0");o.put("eqtl","supplied");o.put("eqtls",temp.resolve("eqtl.tsv").toString());o.put("eqtl-p","none");o.put("eqtl-fdr","none");o.put("eqtl-tissues","all");for(String prefix:new String[]{"pos","eqtl"}){o.put(prefix+"-cadd","0");o.put(prefix+"-rdb","none");o.put(prefix+"-states","none");o.put(prefix+"-binary","none");}o.put("empty","error");o.put("out",temp.resolve("output").toString());return o;
    }
    private int run(Map<String,String> options,String... overrides)throws Exception {Path p=temp.resolve("options.json");Files.writeString(p,RegenieRuntime.json(options));var args=new java.util.ArrayList<String>();args.add("--mapping-options-file");args.add(p.toString());args.addAll(java.util.List.of(overrides));return FumaToolsCli.run(args.toArray(String[]::new),new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    @Test void carrierRoutesNativeWorkflowWithWarningsAndHashes()throws Exception{var o=prepare();assertEquals(0,run(o));Path out=Path.of(o.get("out"));assertTrue(Files.readString(out.resolve("genes.tsv")).contains("G2\tTWO"));assertTrue(Files.readString(out.resolve("eqtl-evidence.tsv")).contains("0.9\t0.99"));String session=Files.readString(out.resolve("session.json"));assertTrue(session.contains("completed-with-review-warnings"));assertTrue(session.contains("options.json"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));}
    @Test void selectedNumericStateAndBinaryColumnHaveExplicitInclusiveRules()throws Exception{var o=prepare();o.put("pos-states","brain");o.put("pos-state-method","mode");o.put("pos-state-max","7");o.put("pos-binary","enhancer");o.put("pos-binary-method","all");assertEquals(0,run(o));assertEquals(3,Files.readAllLines(Path.of(o.get("out"),"positional-evidence.tsv")).size());}
    @Test void directOverrideAndCarrierAreBothRecorded()throws Exception{var o=prepare();assertEquals(0,run(o,"--eqtl-p","0.05"));assertEquals(1,Files.readAllLines(Path.of(o.get("out"),"eqtl-evidence.tsv")).size());assertTrue(Files.readString(Path.of(o.get("out"),"session.json")).contains("0.05"));}
    @Test void failedBudgetQuarantinesAuditsAndNeverPublishesGenes()throws Exception{var o=prepare();assertEquals(2,run(o,"--max-evidence","1"));Path out=Path.of(o.get("out"));assertFalse(Files.exists(out.resolve("genes.tsv")));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("failed"));assertTrue(Files.isDirectory(out.resolve("partial")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));}
    @Test void emptyRequiresExplicitPolicyAndRetainsDispositionAudit()throws Exception{var o=prepare();o.put("pos-cadd","100");o.put("eqtl-cadd","100");assertEquals(2,run(o));assertTrue(Files.exists(Path.of(o.get("out"),"partial/candidate-disposition.tsv")));o.put("out",temp.resolve("empty-output").toString());assertEquals(0,run(o,"--empty","retain"));assertEquals(1,Files.readAllLines(Path.of(o.get("out"),"genes.tsv")).size());assertTrue(Files.readString(Path.of(o.get("out"),"analysis-status.json")).contains("completed-empty"));}
    @Test void existingOutputIsPreserved()throws Exception{var o=prepare();Path out=Path.of(o.get("out"));Files.createDirectory(out);Files.writeString(out.resolve("sentinel"),"keep");assertEquals(2,run(o));assertEquals("keep",Files.readString(out.resolve("sentinel")));try(var files=Files.list(out)){assertEquals(1,files.count());}}
    @Test void malformedMissingResourceAndCarrierTypesFailProminently()throws Exception{var o=prepare();o.put("annotations",temp.resolve("missing.tsv").toString());assertEquals(2,run(o));assertTrue(Files.readString(Path.of(o.get("out"),"WARNINGS.md")).contains("ANALYSIS FAILED"));Files.writeString(temp.resolve("wrong.json"),"{\"method\":\"map-genes\",\"max-rows\":10}");assertEquals(2,FumaMappingCli.run(new String[]{"--mapping-options-file",temp.resolve("wrong.json").toString()},System.out,new PrintStream(new ByteArrayOutputStream())));}
    @Test void disabledModuleRejectsUnusedInputsAndFilters()throws Exception{var o=prepare();o.put("eqtl","none");assertEquals(2,run(o));}
    @Test void duplicateHeadersAndUnknownTrackFailRatherThanIgnore()throws Exception{var o=prepare();o.put("pos-states","unknown");o.put("pos-state-method","any");o.put("pos-state-max","7");assertEquals(2,run(o));o.put("out",temp.resolve("bad-header-output").toString());o.put("pos-states","none");o.remove("pos-state-method");o.remove("pos-state-max");Files.writeString(temp.resolve("annotations.tsv"),"uid\tuid\tCADD\tRDB\nu1\tu1\t1\t1\n");assertEquals(2,run(o));}
    @Test void helpExplainsBoundsAndPartialModuleScope(){var bytes=new ByteArrayOutputStream();assertEquals(0,FumaToolsCli.run(new String[]{"--method","map-genes","--help"},new PrintStream(bytes),System.err));assertTrue(bytes.toString().contains("--max-evidence"));assertTrue(bytes.toString().contains("chromatin"));}
}
