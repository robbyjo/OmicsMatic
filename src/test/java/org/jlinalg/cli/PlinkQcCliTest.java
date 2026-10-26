package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkQcCliTest {
    @TempDir(cleanup=org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path root;
    private record Run(int code,String output,String error) { }
    private Path home(){String value=System.getenv("JLINALG_PLINK_HOME");Assumptions.assumeTrue(value!=null&&Files.isRegularFile(Path.of(value,".jlinalg-plink.json")),"Set JLINALG_PLINK_HOME to the permitted sealed original profile");return Path.of(value);}
    private Run run(String... args){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));}
    private Map<String,String> options(PlinkQcFixture.Input input,String engine,Path out){var result=new LinkedHashMap<String,String>();result.put("task","qc");result.put("engine",engine);result.put("home",home().toString());result.put("bfile",input.prefix().toString());result.put("samples",input.samples().toString());result.put("out",out.toString());result.put("genome-build","unspecified");result.put("chromosome-scope","autosomes");result.put("qc-order","samples-then-variants");result.put("unresolved-alleles","exclude");result.put("maximum-sample-missing",".1");result.put("maximum-variant-missing",".2");result.put("minimum-maf",".01");result.put("minimum-mac","2");result.put("frequency-population","all");result.put("hwe-mode","exact");result.put("hwe-p","1e-6");result.put("hwe-population","controls");result.put("hwe-founders","all");result.put("hwe-unreportable","exclude");result.put("final-check","fail");if(engine.equals("plink2")){result.put("hwe-k","0");result.put("hwe-keep-fewhet","no");}return result;}
    private Run qc(Map<String,String> options){var args=new ArrayList<String>();args.add("plink-tools");for(var entry:options.entrySet()){args.add("--"+entry.getKey());args.add(entry.getValue());}return run(args.toArray(String[]::new));}
    private List<Map<String,String>> table(Path out,String file)throws Exception{var lines=Files.readAllLines(out.resolve(file));String[] names=lines.get(0).split("\t",-1);var result=new ArrayList<Map<String,String>>();for(String line:lines.subList(1,lines.size())){String[] values=line.split("\t",-1);assertEquals(names.length,values.length,file+": "+line);var row=new LinkedHashMap<String,String>();for(int i=0;i<names.length;i++)row.put(names[i],values[i]);result.add(row);}return result;}
    private Map<String,String> variant(Path out,String id)throws Exception{return table(out,"variant-decisions.tsv").stream().filter(r->r.get("variant").equals(id)).findFirst().orElseThrow();}
    private void success(Run result,Path out)throws Exception{assertEquals(0,result.code(),result.error()+"\n"+(Files.exists(out.resolve("WARNINGS.md"))?Files.readString(out.resolve("WARNINGS.md")):""));assertTrue(result.error().contains("WARNINGS"));assertTrue(Files.isRegularFile(out.resolve("genotypes.bed")));assertTrue(Files.readString(out.resolve("session.yaml")).contains("verified_retained_genotype_cells"));assertFalse(Files.exists(out.resolve("partial")));}

    @Test void helpAndUnusedCrossTaskOptionsAreExplicit() {
        assertEquals(0,run("plink-tools","--task","qc","--help").code());assertTrue(run("plink-tools","--help").output().contains("--hwe-unreportable"));
        assertEquals(2,run("plink-tools","--task","glm","--hwe-mode","off").code());assertEquals(2,run("plink-tools","--task","qc","--model","linear").code());
    }
    @Test void missingDependencySkipsBeforeScientificInputRead() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);Path out=root.resolve("skip");var options=options(fixture,"plink2",out);options.put("home",root.resolve("missing-home").toString());options.put("bfile","not-read");Run result=qc(options);assertEquals(3,result.code());assertTrue(result.error().contains("SKIPPED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("--allow-install"));assertFalse(Files.exists(out.resolve("upstream/input.bed")));assertFalse(Files.exists(out.resolve("genotypes.bed")));
    }
    @Test void explicitPoliciesCannotBeOmittedOrSilentlyIgnored() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);
        for(String missing:List.of("qc-order","frequency-population","hwe-unreportable","hwe-k")){Path out=root.resolve("missing-"+missing);var options=options(fixture,"plink2",out);options.remove(missing);assertEquals(2,qc(options).code());assertFalse(Files.exists(out));}
        var unused=options(fixture,"plink2",root.resolve("unused"));unused.put("hwe-mode","off");assertEquals(2,qc(unused).code());
        var wrongEngine=options(fixture,"plink1",root.resolve("wrong-engine"));wrongEngine.put("hwe-k","0");assertEquals(2,qc(wrongEngine).code());
        for(String threshold:List.of("0","1")){Path out=root.resolve("hwe-boundary-"+threshold);var invalid=options(fixture,"plink2",out);invalid.put("hwe-p",threshold);assertEquals(2,qc(invalid).code());assertFalse(Files.exists(out));}
    }
    @Test void bothOriginalEnginesPreserveCasesIdentitiesCallsAndOriginalFamMetadata() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("输入"),false,false,false);
        for(String engine:List.of("plink1","plink2")){Path out=root.resolve("输出-"+engine);Run result=qc(options(fixture,engine,out));success(result,out);var samples=table(out,"sample-decisions.tsv");assertEquals(128,samples.size());assertEquals(125,samples.stream().filter(r->r.get("retained").equals("true")).count());var retained=table(out,"qc-samples-retained.tsv");assertEquals(62,retained.stream().filter(r->r.get("group").equals("case")).count());assertEquals(63,retained.stream().filter(r->r.get("group").equals("control")).count());assertEquals("false",variant(out,"rs10000").get("retained"));assertEquals("true",variant(out,"rs10001").get("retained"));assertEquals("PREEXCLUDED_UNRESOLVED_ALLELES",variant(out,"rs10003").get("decision"));assertEquals("PREEXCLUDED_NONAUTOSOMAL",variant(out,"rs10095").get("decision"));assertEquals(96,table(out,"variant-decisions.tsv").size());assertTrue(Files.readString(out.resolve("genotypes.fam")).contains("家族 成员 0 0 0 2"));assertTrue(Files.readString(out.resolve("genotypes.fam")).contains("UNSAMPLED_FATHER"));assertEquals(0,table(out,"final-threshold-violations.tsv").size());assertEquals(2,qc(options(fixture,engine,out)).code());}
    }
    @Test void hweAllVersusControlsChangesVariantDecisionsWithoutRemovingReferenceOnlyCases() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);Path out=root.resolve("all");var options=options(fixture,"plink2",out);options.put("hwe-population","all");success(qc(options),out);assertEquals("true",variant(out,"rs10000").get("retained"));assertEquals("false",variant(out,"rs10001").get("retained"));assertEquals(125,table(out,"qc-samples-retained.tsv").size());assertEquals(62,table(out,"qc-samples-retained.tsv").stream().filter(r->r.get("group").equals("case")).count());
    }
    @Test void frequencyAndHweFounderReferencesAreIndependentOfFinalSampleRetention() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);Path out=root.resolve("founders");var options=options(fixture,"plink2",out);options.put("frequency-population","founders");options.put("hwe-founders","founders");success(qc(options),out);assertEquals(125,table(out,"qc-samples-retained.tsv").size());var row=variant(out,"rs10001");assertEquals("246",row.get("frequency_ref_allele_observations"));assertEquals("61",row.get("hwe_ref_called"));
    }
    @Test void allMissingHweReferencePoliciesNeverTreatPrintedOneAsTestedEquilibrium() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,true,false);
        for(String policy:List.of("error","exclude","retain-with-warning")){Path out=root.resolve(policy);var options=options(fixture,"plink2",out);options.put("maximum-variant-missing",".6");options.put("hwe-unreportable",policy);Run result=qc(options);
            if(policy.equals("error")){assertEquals(2,result.code());assertFalse(Files.exists(out.resolve("genotypes.bed")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("zero calls"));}
            else{success(result,out);var row=variant(out,"rs10012");assertEquals(policy.equals("exclude")?"false":"true",row.get("retained"));assertEquals("NO_CALLS",row.get("hwe_reportability"));assertEquals("1",row.get("hwe_raw_p"));if(policy.equals("retain-with-warning"))assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("zero-call original P=1"));}
        }
    }
    @Test void declaredOnePassOrderCanChangeSampleRetention() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),true,false,false);
        for(String order:List.of("samples-then-variants","variants-then-samples")){Path out=root.resolve(order);var options=options(fixture,"plink2",out);options.put("qc-order",order);success(qc(options),out);var row=table(out,"sample-decisions.tsv").stream().filter(r->r.get("source_ordinal").equals("5")).findFirst().orElseThrow();assertEquals(order.equals("variants-then-samples")?"true":"false",row.get("retained"));}
    }
    @Test void finalCriteriaViolationsFailOrRemainProminentlyFlaggedOnlyWhenExplicitlyAllowed() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,true);
        for(String mode:List.of("fail","warn")){Path out=root.resolve(mode);var options=options(fixture,"plink2",out);options.put("hwe-mode","off");for(String key:List.of("hwe-p","hwe-population","hwe-founders","hwe-unreportable","hwe-k","hwe-keep-fewhet"))options.remove(key);options.put("final-check",mode);Run result=qc(options);assertFalse(table(out,"final-threshold-violations.tsv").isEmpty());
            if(mode.equals("fail")){assertEquals(2,result.code());assertFalse(Files.exists(out.resolve("genotypes.bed")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("final-cohort criteria"));}
            else{success(result,out);assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("--final-check warn"));assertTrue(table(out,"sample-decisions.tsv").stream().anyMatch(r->r.get("retained").equals("true")&&r.get("final_threshold_failure").equals("true")));}
        }
    }
    @Test void explicitZeroMinimumBoundsPreserveUnresolvedMonomorphicRawCallsOnlyWithWarnings() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);
        for(String engine:List.of("plink1","plink2")){Path out=root.resolve(engine+"-zero-bounds");var options=options(fixture,engine,out);options.put("minimum-maf","0");options.put("minimum-mac","0");options.put("unresolved-alleles","retain-with-warning");options.put("hwe-unreportable","retain-with-warning");success(qc(options),out);var row=variant(out,"rs10003");assertEquals("true",row.get("retained"));assertEquals("UNRESOLVED_ALLELES",row.get("hwe_reportability"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("unresolved allele labels"));String flags=Files.readString(out.resolve("upstream/frequency-filter.stdout"));assertFalse(flags.contains("--maf"));assertTrue(flags.contains("--mac 0"));}
    }
    @Test void originalMidpReportsUseExplicitEngineColumnAndPreserveRawValues() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);
        for(String engine:List.of("plink1","plink2")){Path out=root.resolve(engine+"-midp");var options=options(fixture,engine,out);options.put("hwe-mode","midp");success(qc(options),out);assertEquals(engine.equals("plink1")?"0.7821":"0.782139",variant(out,"rs10026").get("hwe_raw_p"));String header=Files.readAllLines(out.resolve("upstream/final-hwe"+(engine.equals("plink1")?".hwe":".hardy"))).get(0);assertTrue(header.contains(engine.equals("plink1")?"P":"MIDP"));}
    }
}
