/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnrichmentToolsCliTest {
    @TempDir Path directory;private String console;
    private Path file(String name,String value)throws Exception {Path path=directory.resolve(name);Files.writeString(path,value);return path;}
    private void fixture()throws Exception {
        file("rank.tsv","gene\tstatistic\n4\t4\n001\t3\nNA\t2\na\t1\nb\t-1\nc\t-2\nd\t-3\ne\t-4\n");
        file("sets.tsv","term\tgene\ntop\t4\ntop\t001\nbottom\td\nbottom\te\ncenter\ta\ncenter\tb\nall\t4\nall\t001\nall\tNA\nall\ta\nall\tb\nall\tc\nall\td\nall\te\n");
        file("names.tsv","term\tname\ntop\tβ activity\nbottom\tnegative process\ncenter\tbalanced set\nall\tcomplete background\n");
        file("universe.tsv","gene\n4\n001\nNA\na\nb\nc\nd\ne\n");file("selected.tsv","gene\n4\n001\nNA\n");
    }
    private List<String> common(String output,String method){return new ArrayList<>(List.of("enrichment-tools","--method",method,"--memberships",directory.resolve("sets.tsv").toString(),"--term-names",directory.resolve("names.tsv").toString(),"--set-format","tsv","--annotation-id","synthetic-v1","--min-size","1","--max-size","3","--duplicate-membership","error","--unmapped-members","drop","--empty","error","--out",directory.resolve(output).toString(),"--no-config"));}
    private void ranked(List<String> args){args.addAll(List.of("--ranking",directory.resolve("rank.tsv").toString(),"--ranking-format","tsv","--ties","error","--exponent","1","--tail","std","--zero-weight","error"));}
    private static void replace(List<String> args,String... extra){for(int i=0;i<extra.length;i+=2){int at=args.indexOf(extra[i]);if(at>=0)args.set(at+1,extra[i+1]);else{args.add(extra[i]);args.add(extra[i+1]);}}}
    private int call(List<String> args){var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);console=bytes.toString(StandardCharsets.UTF_8);return code;}
    private List<String> score(String output,String...extra){var args=common(output,"score");ranked(args);args.addAll(List.of("--score-convention","fgsea"));replace(args,extra);return args;}
    private static String runtime(String environment,String marker){String value=System.getenv(environment);Assumptions.assumeTrue(value!=null&&Files.exists(Path.of(value).resolve(marker)),"Supply permitted pinned enrichment runtimes for original numerical integration tests");return value;}
    private List<String> external(String output,String method,String engine) {
        var args=common(output,method);String r=System.getenv("JLINALG_ENRICHMENT_RSCRIPT");Assumptions.assumeTrue(r!=null&&Files.isRegularFile(Path.of(r)),"Supply JLINALG_ENRICHMENT_RSCRIPT pointing to permitted R4.6.1");
        args.addAll(List.of("--engine",engine,"--rscript",r,"--r-library",runtime("JLINALG_ENRICHMENT_R_LIBRARY",".jlinalg-enrichment-runtime.json"),"--seed","77","--p-adjust","BH","--p-threshold","0.05","--undefined","error"));
        if(engine.equals("dose"))args.addAll(List.of("--dose-library",runtime("JLINALG_DOSE_LEGACY_R_LIBRARY",".jlinalg-dose-legacy-runtime.json")));
        if(method.equals("ora"))args.addAll(List.of("--selected",directory.resolve("selected.tsv").toString(),"--universe",directory.resolve("universe.tsv").toString(),"--universe-policy","supplied","--query-unmapped","error"));
        else {ranked(args);args.addAll(List.of("--algorithm","multilevel","--eps","0.0001","--sample-size","101","--simple-permutations","500"));}
        return args;
    }
    private Map<String,String[]> results(String output)throws Exception {var table=DelimitedData.read(directory.resolve(output+"/results.tsv"));var rows=new LinkedHashMap<String,String[]>();for(String[] row:table.rows())rows.put(row[0],row);return rows;}

    @Test void missingDependencySkipsAndNamesRecoveryWithoutAResult()throws Exception {
        fixture();var args=common("missing","ora");args.addAll(List.of("--engine","clusterprofiler","--rscript",directory.resolve("absent-Rscript.exe").toString(),"--r-library",directory.resolve("absent-library").toString(),"--seed","77","--p-adjust","BH","--p-threshold",".05","--undefined","error","--selected",directory.resolve("selected.tsv").toString(),"--universe",directory.resolve("universe.tsv").toString(),"--universe-policy","supplied","--query-unmapped","error"));
        assertEquals(3,call(args),console);assertFalse(Files.exists(directory.resolve("missing/results.tsv")));String warnings=Files.readString(directory.resolve("missing/WARNINGS.md"));assertTrue(warnings.contains("ANALYSIS SKIPPED"));assertTrue(warnings.contains("install_enrichment_dependencies.R"));assertTrue(Files.readString(directory.resolve("missing/manifest.yaml")).contains("skipped-dependency-unavailable"));
    }
    @Test void nativeTsvRnkGmtAndNumericLookingIdentifiersNeedNoR()throws Exception {
        fixture();String before=Files.readString(directory.resolve("rank.tsv"));assertEquals(0,call(score("tsv")),console);assertEquals(0,Double.parseDouble(results("tsv").get("center")[3]));assertEquals("β activity",results("tsv").get("top")[1]);
        file("rank.rnk",before.substring(before.indexOf('\n')+1));assertEquals(0,call(score("rnk","--ranking",directory.resolve("rank.rnk").toString(),"--ranking-format","rnk")),console);assertEquals(Files.readString(directory.resolve("tsv/results.tsv")),Files.readString(directory.resolve("rnk/results.tsv")));
        file("sets.gmt","top\tβ activity\t4\t001\nbottom\tnegative process\td\te\ncenter\tbalanced set\ta\tb\nall\tcomplete background\t4\t001\tNA\ta\tb\tc\td\te\n");var args=score("gmt");int at=args.indexOf("--term-names");args.remove(at+1);args.remove(at);replace(args,"--memberships",directory.resolve("sets.gmt").toString(),"--set-format","gmt","--score-convention","enrichit-multilevel");assertEquals(0,call(args),console);assertEquals(.5,Double.parseDouble(results("gmt").get("center")[3]));assertTrue(Files.readString(directory.resolve("gmt/leading-edges.tsv")).contains("top\t001\t2"));assertEquals(before,Files.readString(directory.resolve("rank.tsv")));
    }
    @Test void tiesDuplicateMembershipAndZeroWeightsHaveExplicitProtocols()throws Exception {
        fixture();file("rank.tsv","gene\tstatistic\n4\t4\n001\t4\nNA\t0\na\t0\nb\t0\nc\t0\nd\t-3\ne\t-4\n");assertEquals(2,call(score("ties")),console);assertTrue(console.contains("Tied"));
        var args=score("ordered","--ties","input");assertEquals(2,call(args),console);assertTrue(console.contains("Zero selected power-weight"));
        replace(args,"--out",directory.resolve("uniform").toString(),"--zero-weight","original");assertEquals(0,call(args),console);assertTrue(Files.readString(directory.resolve("uniform/WARNINGS.md")).contains("ZERO HIT WEIGHT"));
        fixture();Files.writeString(directory.resolve("sets.tsv"),"top\t4\n",StandardOpenOption.APPEND);assertEquals(2,call(score("duplicates")),console);assertEquals(0,call(score("collapse","--duplicate-membership","collapse")),console);assertTrue(console.contains("DUPLICATE MEMBERSHIPS"));
    }
    @Test void emptyAndResourcePoliciesRetainDiagnosticsWithoutInventedInference()throws Exception {
        fixture();assertEquals(2,call(score("small-bytes","--max-bytes","4")),console);assertFalse(Files.exists(directory.resolve("small-bytes/results.tsv")));
        assertEquals(2,call(score("work","--score-convention","enrichit-running","--max-score-cells","1")),console);assertTrue(console.contains("max-score-cells"));
        assertEquals(2,call(score("empty","--min-size","20","--max-size","20")),console);assertEquals(0,call(score("empty-retained","--min-size","20","--max-size","20","--empty","retain")),console);assertEquals(1,Files.readAllLines(directory.resolve("empty-retained/results.tsv")).size());assertTrue(Files.readString(directory.resolve("empty-retained/manifest.yaml")).contains("completed-no-tested-terms"));
    }
    @Test void helpAndInapplicableOptionsDoNotHideIgnoredParameters()throws Exception {
        fixture();assertEquals(0,call(List.of("enrichment-tools","--help","--no-config")),console);assertTrue(console.contains("Native score requires no R"));assertEquals(2,call(score("wrong-mode","--rscript","ignored.exe")),console);assertTrue(console.contains("does not apply"));assertEquals(2,call(score("wrong-tail","--score-convention","enrichit-running","--tail","pos")),console);
    }
    @Test void originalOraPreservesLiteralIdsDescriptionsAndKnownTail()throws Exception {
        fixture();for(String engine:List.of("clusterprofiler","dose")){assertEquals(0,call(external(engine,"ora",engine)),console);String[] row=results(engine).get("top");assertEquals("β activity",row[1]);assertEquals("2",row[3]);assertEquals("8",row[4]);assertEquals("3",row[5]);assertEquals(3.0/28,Double.parseDouble(row[8]),1e-13);assertTrue(Files.readString(directory.resolve(engine+"/query-audit.tsv")).contains("NA\tretained-annotated"));}
    }
    @Test void originalUnannotatedQueryRulesKeepEngineSpecificDenominators()throws Exception {
        fixture();Files.writeString(directory.resolve("universe.tsv"),"unannotated\n",StandardOpenOption.APPEND);Files.writeString(directory.resolve("selected.tsv"),"unannotated\n",StandardOpenOption.APPEND);
        for(String engine:List.of("clusterprofiler","dose")){var args=external("unmapped-"+engine,"ora",engine);replace(args,"--query-unmapped","original");assertEquals(0,call(args),console);String[] row=results("unmapped-"+engine).get("top");assertEquals(engine.equals("dose")?"3":"4",row[5]);assertEquals(engine.equals("dose")?1.0/12:1.0/6,Double.parseDouble(row[8]),1e-13);assertTrue(console.contains("QUERY MAPPING"));}
    }
    @Test void originalGseaRetainsDistinctBalancedScoresAndLegacyCoreDefinition()throws Exception {
        fixture();for(String engine:List.of("clusterprofiler","fgsea","dose")){assertEquals(0,call(external("gsea-"+engine,"gsea",engine)),console);var table=results("gsea-"+engine);assertEquals(engine.equals("clusterprofiler")?.5:0,Double.parseDouble(table.get("center")[6]),1e-12);assertTrue(Files.exists(directory.resolve("gsea-"+engine+"/upstream/original-object.rds")));if(engine.equals("dose")){String audit=Files.readString(directory.resolve("gsea-dose/legacy-leading-audit.tsv"));assertTrue(audit.contains("center\t0.0\t-0.5\t1\ttrue"));assertTrue(console.contains("LEGACY DOSE SCORE/CORE DIFFERENCE"));}}
    }
    @Test void zeroOneTailedScoresRetainOriginalDirectionalCoreGenes()throws Exception {
        fixture();for(String engine:List.of("clusterprofiler","fgsea"))for(String tail:List.of("pos","neg")) {
            String output=engine+"-"+tail;var args=external(output,"gsea",engine);replace(args,"--tail",tail);
            if(engine.equals("clusterprofiler")&&tail.equals("neg")) {
                assertEquals(2,call(args),console);assertTrue(console.contains("UNREPORTED ELIGIBLE TERMS"));
                assertFalse(Files.exists(directory.resolve(output+"/results.tsv")));assertTrue(Files.exists(directory.resolve(output+"/partial/upstream/original-object.rds")));
                String retained=output+"-retained";replace(args,"--out",directory.resolve(retained).toString(),"--undefined","retain");assertEquals(0,call(args),console);
                assertFalse(results(retained).containsKey("top"));
                for(String[] row:results(retained).values()){assertEquals("NA",row[10]);assertEquals("NA",row[13]);}
                assertTrue(Files.readString(directory.resolve(retained+"/reported-family.tsv")).contains("top\tunreported-eligible"));continue;
            }
            assertEquals(0,call(args),console);String term=tail.equals("pos")?"bottom":"top";
            assertEquals(0,Double.parseDouble(results(output).get(term)[6]),1e-12);
            var leading=DelimitedData.read(directory.resolve(output+"/upstream/leading-edges.tsv"));var genes=new HashSet<String>();
            for(String[] row:leading.rows())if(row[0].equals(term))genes.add(row[1]);
            assertEquals(tail.equals("pos")?Set.of("d","e"):Set.of("4","001"),genes);
            assertTrue(console.contains("ZERO ONE-TAILED SCORE/CORE"));
        }
    }
}
