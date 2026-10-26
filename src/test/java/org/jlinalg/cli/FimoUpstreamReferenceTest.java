/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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

class FimoUpstreamReferenceTest {
    @TempDir Path root;
    @Test void all24OriginalQWorkflowReferencesPreserveScienceAndRejectSixTruncatedResults() throws Exception {
        String home=System.getenv("JLINALG_FIMO_HOME");assumeTrue(home!=null && Files.isRegularFile(Path.of(home).resolve(".jlinalg-fimo.json")),"Set JLINALG_FIMO_HOME for actual original FIMO reference validation");
        byte[] packed;try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/pssm/original-5.5.9-q-scans.json.gz")) { assertNotNull(in);packed=in.readAllBytes(); }
        assertEquals("de6f921ed259135e7165932fd300bba0024b723de8a1b7577424d3f478cc4880",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed)));
        String text;try(var in=new GZIPInputStream(new java.io.ByteArrayInputStream(packed))) { text=new String(in.readAllBytes(),StandardCharsets.UTF_8); }
        var data=(Map<?,?>)SimpleJson.parse(text);var cases=(List<?>)data.get("cases");assertEquals(24,cases.size());int truncated=0;long sites=0;
        for(Object value:cases) {
            var c=(Map<?,?>)value;String name=(String)c.get("name");Path work=root.resolve(name);Files.createDirectory(work);Path mf=work.resolve("input.meme"),fa=work.resolve("input.fa"),out=work.resolve("analysis");Files.writeString(mf,(String)c.get("motif"));Files.writeString(fa,(String)c.get("fasta"));
            var args=new ArrayList<>(List.of("fimo-tools","--home",home,"--motifs",mf.toString(),"--fasta",fa.toString(),"--out",out.toString(),"--background","uniform","--strands","both","--coordinates","genomic","--threshold-type",(String)c.get("threshold_type"),"--threshold",(String)c.get("threshold"),"--maximum-stored-scores",c.get("max_stored_scores").toString()));
            var console=new ByteArrayOutputStream();var stream=new PrintStream(console,true,StandardCharsets.UTF_8);boolean capped=(Boolean)c.get("upstream_truncated");assertEquals(capped?2:0,JLinAlgCli.run(args.toArray(String[]::new),stream,stream),name+" "+console.toString(StandardCharsets.UTF_8));
            if(capped) { truncated++;assertFalse(Files.exists(out.resolve("results.tsv")),name);assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("INCOMPLETE ORIGINAL FIMO OUTPUT"),name); }
            Path table=capped?out.resolve("partial/upstream/fimo.tsv"):out.resolve("results.tsv");var actual=Files.readAllLines(table).stream().filter(l->!l.isBlank() && !l.startsWith("#")).toList();var expected=(List<?>)c.get("expected_rows");
            if(capped && expected.isEmpty()) { assertEquals(0,actual.size(),name);continue; }
            assertEquals(expected.size()+1,actual.size(),name);String[] columns=actual.get(0).split("\t",-1);
            for(int i=0;i<expected.size();i++) { var row=(Map<?,?>)expected.get(i);String[] values=actual.get(i+1).split("\t",-1);assertEquals(columns.length,values.length,name);for(int col=0;col<columns.length;col++)assertEquals(row.get(columns[col]),values[col],name+" row "+i+" "+columns[col]);sites++; }
        }
        assertEquals(6,truncated);assertEquals(24854,sites);
    }
}
