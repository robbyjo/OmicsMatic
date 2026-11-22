package org.jlinalg.cli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synthetic schema fixture for preflight/skip tests; never used as a fitted analysis. */
final class FumaCelltypeTestData {
    private FumaCelltypeTestData() { }
    static Map<String,String> options(Path root)throws Exception {
        Path bundle=root.resolve("gene-bundle");Files.createDirectories(bundle);Files.writeString(bundle.resolve("results.tsv"),"GENE\tCHR\tSTART\tSTOP\tNSNPS\tNPARAM\tN\tZSTAT\tP\nE1\t1\t100\t110\t2\t1\t1000\t1\t.1\nE2\t1\t200\t210\t2\t1\t1000\t2\t.02\nE3\t1\t300\t310\t2\t1\t1000\t3\t.001\n");Files.writeString(bundle.resolve("genes.raw"),"# VERSION = 110\n# COVAR = NSAMP MAC\nE1 1 100 110 2 1 1000 50 1\nE2 1 200 210 2 1 1000 50 2\nE3 1 300 310 2 1 1000 50 3\n");seal(bundle);
        Files.writeString(root.resolve("properties.tsv"),"GENE\tCellA\tAverage\nE1\t1\t.1\nE2\t2\t.2\nE3\t3\t.3\n");Files.writeString(root.resolve("datasets.tsv"),"dataset\tresource_id\tpath\nDataA\tfixture-resource\tproperties.tsv\n");var options=new LinkedHashMap<String,String>();options.put("runtime-profile","windows-x86_64-static");options.put("home",root.resolve("missing-runtime").toString());options.put("gene-bundle",bundle.toString());options.put("datasets",root.resolve("datasets.tsv").toString());options.put("build","fixture-build");options.put("expression-id","fixture-expression");options.put("correction","BH");options.put("step2","true");options.put("step3","true");options.put("gene-coverage","error");options.put("missing-values","error");options.put("out",root.resolve("result").toString());return options;
    }
    static void seal(Path bundle)throws Exception {Files.writeString(bundle.resolve("magma-bundle.json"),RegenieRuntime.json(Map.of("kind","gene","schema_version",1,"build","fixture-build","files",Map.of("results.tsv",FollowupSupport.hash(bundle.resolve("results.tsv")),"genes.raw",FollowupSupport.hash(bundle.resolve("genes.raw"))))));}
    static FumaCelltypeInputs.Limits limits(){return new FumaCelltypeInputs.Limits(1000000,100,10,100,10000);}
}
