/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FumaAnnotationTestData {
    private FumaAnnotationTestData() { }
    static Map<String,String> options(Path root,String output)throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("snps.tsv"),"uniqID\trsID\tchr\tpos\tgwasP\n"+"u1\trs1\t1\t100\t.01\n"+"u2\trs2\t1\t200\tNA\n");
        Files.writeString(root.resolve("genes.tsv"),"ensembl_gene_id\texternal_gene_name\tchromosome_name\tstart_position\tend_position\n"+"G1\t\t1\t100\t199\n"+"G2\tTWO\t1\t200\t300\n"+"G3\tLEFT\t1\t99\t99\n");
        Files.writeString(root.resolve("annotations.tsv"),"uniqID\tCADD\tRDB\ts1\ts2\n"+"u1\t10\t1a\t10\t2\n"+"u2\tNA\t\t2\t10\n");
        Files.writeString(root.resolve("consequences.tsv"),"uniqID\tgene\tannot\tdist\texonic_func\texon\n"+"u1\tG1\texonic\t0\tnonsynonymous SNV\texon1\n"+"u1\tG2\texonic\t0\tNA\tNA\n"+"u2\tNA\tintergenic\tNA\tNA\tNA\n");
        Files.writeString(root.resolve("ld.tsv"),"SNP1\tSNP2\tr2\n"+"u1\tu1\t1\n"+"u1\tu2\t.7\n"+"u1\tu1\t1\n");
        Files.writeString(root.resolve("independent.tsv"),"uniqID\tlabel\tnSNPs\tnGWASSNPs\n"+"u1\tlead\t999\t999\n"+"u2\tspare\t999\t999\n");
        Files.writeString(root.resolve("reference.tsv"),"annot\tcount\tprop\nexonic\t10\t.1\nintergenic\t10\t.9\n");
        var options=new LinkedHashMap<String,String>();options.put("method","annotate-variants");options.put("build","GRCh38");for(String key:List.of("annotation-id","consequence-id","cadd-id","rdb-id","state-id","reference-id"))options.put(key,"synthetic-"+key+"-v1");
        for(String key:List.of("snps","genes","annotations","consequences","ld","independent","reference-counts"))options.put(key,root.resolve(key.equals("reference-counts")?"reference.tsv":key+".tsv").toString());options.put("states","s1,s2");options.put("duplicates","error");options.put("single-state","error");options.put("bed-manifest","none");options.put("out",root.resolve(output).toString());return options;
    }
}
