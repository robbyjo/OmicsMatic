package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class FumaMagmaReportTestData {
    private FumaMagmaReportTestData() { }
    static Map<String,String> options(Path base)throws Exception {
        Files.createDirectories(base);Files.writeString(base.resolve("genes.tsv"),"GENE\tZSTAT\tP\nE1\t2\t.02\nE2\t-1\t.8\nAbsent\t0\t.5\n");
        Files.writeString(base.resolve("symbols.tsv"),"ensembl_gene_id\texternal_gene_name\nE1\tFirst\nE1\tLater\nE2\t\n");
        Files.writeString(base.resolve("gsa.out"),"# synthetic input\n# no fit performed\n# supplied results\nFULL_NAME\tNGENES\tBETA\tBETA_STD\tSE\tP\nA\t3\t.1\t.2\t.25\t.2\nB\t4\t.2\t.3\t.25\t.001\nC\t5\t.3\t.4\t.25\t.03\n");
        var options=new LinkedHashMap<String,String>();options.put("method","magma-report");options.put("build","synthetic-v1");options.put("annotation-id","synthetic-symbols");options.put("analysis-id","synthetic-model");options.put("gene-results",base.resolve("genes.tsv").toString());options.put("symbols",base.resolve("symbols.tsv").toString());options.put("gsa",base.resolve("gsa.out").toString());options.put("sets",base.resolve("unused-and-absent-fallback.tsv").toString());options.put("sets-format","magma-skip3");options.put("duplicates","source-first");options.put("missing-p","source-na");options.put("placeholders","source-rows");options.put("out",base.resolve("analysis").toString());return options;
    }
    static Path carrier(Path path,Map<String,?> options)throws Exception{Files.writeString(path,RegenieRuntime.json(options)+"\n");return path;}
    static Path parent(Path base,Map<String,String> options,Map<String,?> extra)throws Exception {
        var root=new LinkedHashMap<String,Object>();root.put("schema_version",1);root.put("build","synthetic-v1");root.put("out",base.resolve("workflow").toString());var stage=new LinkedHashMap<>(options);stage.remove("out");root.put("magma_report",stage);root.putAll(extra);return carrier(base.resolve("parent.json"),root);
    }
}
