/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MatrixEqtlNativeCliTest {
    @TempDir Path root;
    private Map<String,Object> fixture() throws IOException {
        Files.writeString(root.resolve("snps.tsv"),"id\tA\tB\tC\tD\tE\tF\tG\tH\nv\t0\t1\t2\t0\t1\t2\t0\t1\n");
        Files.writeString(root.resolve("genes.tsv"),"id\tA\tB\tC\tD\tE\tF\tG\tH\ng\t.1\t1.2\t1.8\t.3\t1.8\t2.1\t.5\t1.1\n");
        var p=new LinkedHashMap<String,Object>();
        p.put("schema_version",1);p.put("source_version","2.3");p.put("model","modelLINEAR");
        p.put("snps","snps.tsv");p.put("genes","genes.tsv");
        for(String k:List.of("covariates","snp_positions","gene_positions","error_covariance"))p.put(k,null);
        p.put("threshold",1);p.put("cis_threshold",0);p.put("cis_distance",1000000);p.put("block_size",2);
        p.put("max_input_bytes",100000);p.put("max_entries",1000);p.put("max_pairs",100);p.put("max_retained",100);p.put("max_seconds",60);
        p.put("execution_policy","ALLOW");p.put("population","Synthetic complete sample set");p.put("preparation","No normalization");p.put("allele_orientation","Input counted allele");p.put("out","capture");return p;
    }
    private int request(Map<String,Object> p) throws IOException {
        NextflowFiles.writeJson(root.resolve("request.json"),p);return run("--request",root.resolve("request.json").toString());
    }
    private int run(String... args) {var bytes=new ByteArrayOutputStream();try(var stream=new PrintStream(bytes)){return MatrixEqtlNativeCli.run(args,stream,stream);}}
    private Map<?,?> status(String directory) throws IOException {return NextflowFiles.object(NextflowFiles.json(root.resolve(directory+"/analysis-status.json"),1048576));}
    @Test void capturesAndRecomputesAllSavedInputs() throws IOException {
        assertEquals(0,request(fixture()));assertEquals(true,status("capture").get("result_admitted"));
        Files.writeString(root.resolve("genes.tsv"),"Original caller path may change after capture\n");
        assertEquals(0,run("--verify",root.resolve("capture").toString(),"--out",root.resolve("replay").toString()));
        assertEquals(Files.readString(root.resolve("capture/results.json")),Files.readString(root.resolve("replay/results.json")));
    }
    @Test void changedSavedInputCannotBeAdmitted() throws IOException {
        assertEquals(0,request(fixture()));Files.writeString(root.resolve("capture/inputs/genes.tsv"),"changed\n");
        assertEquals(2,run("--verify",root.resolve("capture").toString(),"--out",root.resolve("replay").toString()));assertEquals(false,status("replay").get("result_admitted"));
    }
    @Test void sampleReorderingFailsInsteadOfSilentAlignment() throws IOException {
        var p=fixture();Files.writeString(root.resolve("genes.tsv"),Files.readString(root.resolve("genes.tsv")).replace("id\tA\tB","id\tB\tA"));
        assertEquals(2,request(p));assertEquals(false,status("capture").get("result_admitted"));
    }
    @Test void unavailableInputSkipsDependentAnalysis() throws IOException {
        var p=fixture();p.put("genes","missing.tsv");assertEquals(3,request(p));assertEquals(false,status("capture").get("result_admitted"));assertTrue(Files.readString(root.resolve("capture/WARNINGS.md")).contains("SKIP3"));
    }
    @Test void policyForbidSkipsBeforeLoadingData() throws IOException {
        var p=fixture();p.put("execution_policy","FORBID");assertEquals(3,request(p));assertFalse(Files.exists(root.resolve("capture/inputs")));assertEquals(false,status("capture").get("result_admitted"));
    }
    @Test void entryCapacityFailureAdmitsNoPartialResult() throws IOException {
        var p=fixture();p.put("max_entries",10);assertEquals(2,request(p));assertEquals(false,status("capture").get("result_admitted"));assertFalse(Files.exists(root.resolve("capture/results.json")));
    }
    @Test void priorOutputIsPreserved() throws IOException {
        var p=fixture();Files.createDirectory(root.resolve("capture"));Files.writeString(root.resolve("capture/KEEP"),"unique");assertEquals(2,request(p));assertEquals("unique",Files.readString(root.resolve("capture/KEEP")));assertFalse(Files.exists(root.resolve("capture/analysis-status.json")));
    }
    @Test void interactionModelCannotBeSilentlyReplaced() throws IOException {
        var p=fixture();p.put("model","modelLINEAR_CROSS");assertEquals(2,request(p));assertEquals(false,status("capture").get("result_admitted"));
    }
}
