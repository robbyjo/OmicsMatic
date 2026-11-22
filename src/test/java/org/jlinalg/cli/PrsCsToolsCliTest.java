/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

class PrsCsToolsCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path root;
    private record Run(int code,String output,String error) { }
    private Run run(Map<String,String> options)throws Exception {
        var args=new ArrayList<String>();for(var entry:options.entrySet()){args.add("--"+entry.getKey());args.add(entry.getValue());}
        return run(args.toArray(String[]::new));
    }
    private static Run run(String[] args)throws Exception {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code;
        try(var stdout=new PrintStream(output,true,StandardCharsets.UTF_8);var stderr=new PrintStream(error,true,StandardCharsets.UTF_8)){code=PrsCsToolsCli.run(args,stdout,stderr);}
        return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    private Map<String,String> base(Path data,Path out) {
        var options=new LinkedHashMap<String,String>();options.put("profile","source-fbc9ec0");options.put("home",root.resolve("missing-home").toString());options.put("summary",data.resolve("beta-se.txt").toString());options.put("target-bim",data.resolve("target.bim").toString());options.put("reference",data.resolve("ldblk_1kg_eur").toString());options.put("reference-kind","1kg");options.put("chromosomes","1,22");options.put("format","beta-se");options.put("trait-kind","quantitative");options.put("sample-size","20000");options.put("sample-size-kind","total");options.put("gwas-population","synthetic");options.put("ld-population","synthetic");options.put("assembly","synthetic-coordinate-label");options.put("a","1");options.put("b","0.5");options.put("phi","0.01");options.put("iterations","40");options.put("burnin","20");options.put("thin","5");options.put("seed","17");options.put("effect-units","per-allele");options.put("posterior","mean");options.put("write-psi","true");options.put("score-export","none");options.put("unmatched","exclude-with-warning");options.put("palindromic","allow-upstream-with-warning");options.put("strand","allow-complement-with-warning");options.put("positions","require-match");options.put("p-floor","error");options.put("minimum-model-variants","1");options.put("out",out.toString());options.put("maximum-model-variants","100");options.put("maximum-input-rows","1000");options.put("maximum-input-mib","1");options.put("maximum-retained-mib","16");options.put("maximum-line-bytes","4096");options.put("maximum-ld-cells","10000");options.put("maximum-block-snps","100");options.put("maximum-blocks","100");options.put("maximum-report-mib","16");options.put("maximum-posterior-cells","1000");return options;
    }
    private static Path actualHome() {
        String value=System.getenv("JLINALG_PRSCS_HOME");Assumptions.assumeTrue(value!=null,"Supply exact pinned private PRS-CS home for actual original analysis tests");return Path.of(value);
    }
    private static void reference(Path output,String mode,String label)throws Exception {
        try(var resource=PrsCsToolsCliTest.class.getResourceAsStream("/org/jlinalg/genetics/prscs-reference/posterior-provenance.json")) {
            assertNotNull(resource);Map<?,?> proof=(Map<?,?>)SimpleJson.parse(new String(resource.readAllBytes(),StandardCharsets.UTF_8));Map<?,?> selected=null;
            for(Object v:(List<?>)proof.get("cases")){Map<?,?> row=(Map<?,?>)v;if(label.equals(row.get("label")))selected=row;}assertNotNull(selected);
            for(var entry:((Map<?,?>)selected.get("files_sha256")).entrySet()) {
                Path file=output.resolve("upstream-"+mode).resolve(entry.getKey().toString());assertEquals(entry.getValue(),FollowupSupport.hash(file));
            }
        }
    }
    private static void passed(Path output)throws Exception {
        assertTrue(Files.readString(output.resolve("analysis-status.json")).contains("passed-with-review-warnings"));String warning=Files.readString(output.resolve("WARNINGS.md"));assertTrue(warning.startsWith("# PRS-CS — REVIEW BEFORE USE"));assertTrue(warning.contains("Required action:"));assertTrue(warning.contains("do not establish convergence"));
        assertEquals(26,Files.readAllLines(output.resolve("variant-ledger.tsv")).size());assertEquals(21,Files.readAllLines(output.resolve("native-marginals.tsv")).size());assertTrue(Files.exists(output.resolve("manifest.yaml")));
    }
    private static void success(Run result,Path output)throws Exception {assertEquals(0,result.code(),result.error()+(Files.exists(output.resolve("WARNINGS.md"))?"\n"+Files.readString(output.resolve("WARNINGS.md")):""));}

    @Test void profileTraitSeedSamplingAndUnusedModelOptionsFailBeforeCreatingOutput()throws Exception {
        Path data=PrsCsInputsTest.fixture(root);var original=base(data,root.resolve("result"));
        List<Map<String,String>> changes=List.of(Map.of("profile","1.0.0","legacy-use","offline-reproduction"),Map.of("profile","1.1.0","legacy-use","offline-reproduction","posterior","draws"),Map.of("legacy-use","offline-reproduction"),Map.of("format","or-se"),Map.of("sample-size-kind","effective"),Map.of("sample-size","9007199254740992"),Map.of("seed","-1"),Map.of("seed","4294967296"),Map.of("iterations","12","burnin","0"),Map.of("iterations","12","burnin","3"),Map.of("p-floor","original-floor-with-warning"),Map.of("posterior","draws","score-export","per-allele"),Map.of("chromosomes","01,22"),Map.of("chromosomes","1,1"),Map.of("phi","0"),Map.of("a","NaN"),Map.of("ld-tolerance","-1"),Map.of("unknown-option","value"));
        for(var change:changes) {var options=new LinkedHashMap<>(original);options.putAll(change);Run result=run(options);assertEquals(2,result.code(),result.error());assertFalse(Files.exists(Path.of(options.get("out"))));assertTrue(result.error().contains("Required action:"));}
    }

    @Test void missingDependencySkipsWithProminentSpecificRecoveryAndNeverInstalls()throws Exception {
        Path data=PrsCsInputsTest.fixture(root),out=root.resolve("result");var options=base(data,out);Run result=run(options);
        assertEquals(3,result.code());assertTrue(result.error().contains("PRS-CS SKIPPED"));assertFalse(Files.exists(root.resolve("missing-home")));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("skipped-dependency-unavailable"));
        String warning=Files.readString(out.resolve("WARNINGS.md"));assertTrue(warning.startsWith("# PRS-CS SKIPPED"));assertTrue(warning.contains("Original PRS-CS dependency unavailable"));assertTrue(warning.contains("--allow-install"));assertTrue(warning.contains("No reference panel is downloaded"));assertFalse(Files.exists(out.resolve("posterior-means.tsv")));
    }

    @Test void optionsFileRejectsUnknownRecursiveNonStringAndOversizedDataAndHelpDocumentsRequiredChoices()throws Exception {
        Path recipe=root.resolve("recipe.json");for(String text:List.of("[]","{\"format\":12}","{\"options-file\":\"again\"}","{\"unknown\":\"value\"}"," ".repeat(1048577))) {
            Files.writeString(recipe,text);Run result=run(new String[]{"--options-file",recipe.toString()});assertEquals(2,result.code());assertTrue(result.error().contains("NOT COMPLETED"));
        }
        Run help=run(new String[]{"--help"});assertEquals(0,help.code());assertTrue(help.output().contains("legacy-use offline-reproduction"));assertTrue(help.output().contains("Analysis never installs"));
    }

    @Test void actualCurrentBothPosteriorModesMatchOriginalThroughGzipUnicodeAndReorderedTables()throws Exception {
        Path home=actualHome(),parent=root.resolve("métadonnées Ω");Files.createDirectory(parent);Path data=PrsCsInputsTest.fixture(parent),out=root.resolve("result");var options=base(data,out);
        for(String name:List.of("target.bim","beta-se.txt")) {
            Path file=data.resolve(name);var rows=new ArrayList<>(Files.readAllLines(file));String header=name.endsWith("txt")?rows.remove(0):null;Collections.reverse(rows);if(header!=null)rows.add(0,header);try(var gzip=new GZIPOutputStream(Files.newOutputStream(file))){gzip.write((String.join("\n",rows)+"\n").getBytes(StandardCharsets.UTF_8));}
        }
        options.put("home",home.toString());options.put("posterior","both");options.put("phi","auto");options.put("score-export","per-allele");Path recipe=root.resolve("UTF8-options.json");Files.writeString(recipe,RegenieRuntime.json(options)+"\n");
        Run result=run(new String[]{"--options-file",recipe.toString()});success(result,out);passed(out);reference(out,"mean","source-fbc9ec0-beta-se-auto-allele-mean");reference(out,"draws","source-fbc9ec0-beta-se-auto-allele-draws");
        assertEquals(21,Files.readAllLines(out.resolve("posterior-draws.tsv")).size());assertEquals(21,Files.readAllLines(out.resolve("score-weights.tsv")).size());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("DeprecationWarnings"));assertTrue(Files.exists(out.resolve("options-input.json")));String session=Files.readString(out.resolve("session.yaml"));assertTrue(session.contains("mcmc-mean_command"));assertTrue(session.contains("mcmc-draws_command"));
    }

    @Test void actualHistorical110UsesCorrectTitleCaseForPerAlleleAndPsiOutputs()throws Exception {
        Path home=actualHome(),data=PrsCsInputsTest.fixture(root),out=root.resolve("result");var options=base(data,out);options.put("home",home.toString());options.put("profile","1.1.0");options.put("legacy-use","offline-reproduction");options.put("format","or-se");options.put("summary",data.resolve("or-se.txt").toString());options.put("trait-kind","binary");options.put("sample-size-kind","effective");options.put("ld-population","different-explicit-fixture-label");
        Run result=run(options);success(result,out);passed(out);reference(out,"mean","1.1.0-or-se-fixed-allele-mean");assertEquals(21,Files.readAllLines(out.resolve("shrinkage.tsv")).size());String warning=Files.readString(out.resolve("WARNINGS.md"));assertTrue(warning.contains("UNSUPPORTED LEGACY PYTHON"));assertTrue(warning.contains("population labels differ"));assertTrue(warning.contains("NumPy-alias DeprecationWarnings"));
    }

    @Test void actualHistorical100PvalueAutoModelPreservesStandardizedUnitsWithoutInventedPsi()throws Exception {
        Path home=actualHome(),data=PrsCsInputsTest.fixture(root),out=root.resolve("result");var options=base(data,out);options.put("home",home.toString());options.put("profile","1.0.0");options.put("legacy-use","offline-reproduction");options.put("format","or-p");options.put("summary",data.resolve("or-p.txt").toString());options.put("trait-kind","binary");options.put("sample-size-kind","effective");options.put("effect-units","standardized");options.put("write-psi","false");options.put("p-floor","original-floor-with-warning");options.put("phi","auto");options.put("score-export","per-allele");
        Run result=run(options);success(result,out);passed(out);reference(out,"mean","1.0.0-or-p-auto-std-mean");assertFalse(Files.exists(out.resolve("shrinkage.tsv")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("1e-323 floor"));assertEquals(21,Files.readAllLines(out.resolve("score-weights.tsv")).size());
    }

    @Test void actualInputFailureRetainsLedgersAndNeverPublishesCanonicalPosteriors()throws Exception {
        Path home=actualHome(),data=PrsCsInputsTest.fixture(root),out=root.resolve("result"),bim=data.resolve("target.bim");Files.writeString(bim,Files.readString(bim)+Files.readAllLines(bim).get(0)+"\n");var options=base(data,out);options.put("home",home.toString());Run result=run(options);
        assertEquals(2,result.code(),result.error());String warning=Files.readString(out.resolve("WARNINGS.md"));assertTrue(warning.startsWith("# PRS-CS FAILED — DO NOT USE"));assertTrue(warning.contains("Duplicate target BIM SNP"));assertTrue(Files.exists(out.resolve("inputs/variant-ledger.tsv")));assertFalse(Files.exists(out.resolve("posterior-means.tsv")));assertFalse(Files.exists(out.resolve("upstream-mean")));
    }

    @Test void actualOwnedDependentChildTimeoutRetainsPidTerminationAndActionableFailure()throws Exception {
        Path home=actualHome(),data=PrsCsInputsTest.fixture(root),out=root.resolve("result");var options=base(data,out);options.put("home",home.toString());options.put("iterations","1000000");options.put("burnin","500000");options.put("timeout-seconds","2");options.put("write-psi","false");Run result=run(options);
        assertEquals(2,result.code(),result.error());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("timed out; owned process terminated"));assertFalse(Files.exists(out.resolve("posterior-means.tsv")));
        Object value=new org.yaml.snakeyaml.Yaml().load(Files.readString(out.resolve("session.yaml")));Map<?,?> session=(Map<?,?>)value;boolean found=false;
        for(var entry:session.entrySet())if(entry.getKey().toString().endsWith("_timed_out")&&Boolean.TRUE.equals(entry.getValue())) {String prefix=entry.getKey().toString().replace("_timed_out","");long pid=((Number)session.get(prefix+"_owned_pid")).longValue();assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));found=true;}
        assertTrue(found);
    }
}
