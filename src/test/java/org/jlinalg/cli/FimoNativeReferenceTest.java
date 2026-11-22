/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FimoNativeReferenceTest {
    @TempDir Path root;
    @Test void all72ActualOriginalScannerWorkflowsAnd6805ReportedSitesMatch() throws Exception {
        byte[] packed;
        try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/pssm/original-5.5.9-scans.json.gz")) { assertNotNull(in);packed=in.readAllBytes(); }
        assertEquals("ea3243dda675ce226dd1af8a2b66b2a27d0bf2d03bb9ad6ba591fb0ebb35f442",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed)));
        String json;try(var in=new GZIPInputStream(new java.io.ByteArrayInputStream(packed))) { json=new String(in.readAllBytes(),StandardCharsets.UTF_8); }
        var document=(Map<?,?>)SimpleJson.parse(json);var cases=(List<?>)document.get("cases");assertEquals(72,cases.size());int compared=0;
        for(Object value:cases) {
            var c=(Map<?,?>)value;String name=(String)c.get("name");Path directory=root.resolve(name);Files.createDirectory(directory);
            Path motifs=directory.resolve("motifs.meme"),fasta=directory.resolve("seq.fa"),output=directory.resolve("output");
            Files.writeString(motifs,(String)c.get("motif"));Files.writeString(fasta,(String)c.get("fasta"));
            var args=new ArrayList<>(List.of("motif-scan","--motifs",motifs.toString(),"--fasta",fasta.toString(),"--out",output.toString()));
            for(var entry:((Map<?,?>)c.get("options")).entrySet()) args.addAll(List.of("--"+entry.getKey(),(String)entry.getValue()));
            var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);
            assertEquals(0,JLinAlgCli.run(args.toArray(String[]::new),stream,stream),name+" "+bytes.toString(StandardCharsets.UTF_8));
            var expected=(List<?>)c.get("rows");var lines=Files.readAllLines(output.resolve("results.tsv"));assertEquals(expected.size()+1,lines.size(),name);
            String[] header=lines.get(0).split("\t",-1);
            for(int i=0;i<expected.size();i++) {
                var reference=(Map<?,?>)expected.get(i);String[] row=lines.get(i+1).split("\t",-1);assertEquals(header.length,row.length,name);
                for(int col=0;col<10;col++) {
                    String field=header[col],ref=(String)reference.get(field);
                    if(field.equals("score") || field.equals("p-value")) {
                        double p=Double.parseDouble(ref),actual=Double.parseDouble(row[col]);int precision=field.equals("score")?6:3;
                        double tolerance=p==0?0:.5*Math.pow(10,Math.floor(Math.log10(Math.abs(p)))-precision+1);
                        assertEquals(p,actual,tolerance+Math.abs(p)*1e-12,name+" site "+i+" "+field);
                    } else assertEquals(ref,row[col],name+" site "+i+" "+field);
                }
                compared++;
            }
        }
        assertEquals(6805,compared);
    }
}
