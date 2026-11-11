/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.workflow.NfcoreEagerScreeningReads;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Complete original-provider populations and late-record corruptions; no upstream installation required. */
class NfcoreEagerScreeningReadsTest {
    @TempDir Path temporary;

    @Test void everyOriginalLibraryMatchesIndependentProviderMeasurements() throws Exception {
        Path root=extract(),reference=root.resolve("reference");
        var refs=references(reference,root.resolve("mutations"),Map.of());
        assertEquals(NextflowFiles.object(NextflowFiles.json(root.resolve("expected/expected-references.json"),1048576)),refs);
        var cases=NextflowFiles.list(NextflowFiles.json(reference.resolve("cases.json"),1048576));
        var expected=NextflowFiles.list(NextflowFiles.json(root.resolve("expected/expected-populations.json"),1048576));
        assertEquals(2,cases.size());assertEquals(2,expected.size());long bam=0,queries=0,alignments=0;
        for(int i=0;i<cases.size();i++) {
            var row=NextflowFiles.object(cases.get(i));var wanted=NextflowFiles.object(expected.get(i));
            assertEquals(wanted.get("library"),row.get("library"));
            var result=inspect(reference,root.resolve("mutations"),row,Map.of(),Map.of(),refs);
            var actual=NextflowFiles.object(SimpleJson.parse(NextflowJson.json(result.document())));
            assertEquals(wanted.get("host"),actual.get("host"));assertEquals(wanted.get("malt"),actual.get("malt"));
            bam+=result.host().records();queries+=result.host().unmappedQueries();alignments+=result.malt().alignments();
        }
        assertEquals(19720,bam);assertEquals(16850,queries);assertEquals(175,alignments);
    }

    @Test void changedLateRecordsAndIncompletePopulationsRefuseWhileEmptyStatisticsStayAbsent() throws Exception {
        Path root=extract(),reference=root.resolve("reference"),mutations=root.resolve("mutations");
        var row=NextflowFiles.object(NextflowFiles.list(NextflowFiles.json(reference.resolve("cases.json"),1048576)).get(0));
        var cases=NextflowFiles.list(NextflowFiles.json(mutations.resolve("mutations.json"),1048576));
        assertEquals(25,cases.size());int refused=0,empty=0;
        for(Object raw:cases) {
            var mutation=NextflowFiles.object(raw);var replacements=NextflowFiles.object(mutation.get("replacements"));
            var limits=NextflowFiles.object(mutation.get("limits"));
            if(mutation.get("expected").equals("refused")) {
                assertThrows(IOException.class,()->inspect(reference,mutations,row,replacements,limits,references(reference,mutations,replacements)),(String)mutation.get("name"));refused++;
            } else {
                assertEquals("empty-alignments-valid-counts-unassessable-identity",mutation.get("expected"));
                var result=inspect(reference,mutations,row,replacements,limits,references(reference,mutations,replacements));
                assertEquals(0,result.malt().alignments());assertEquals(0,result.malt().alignedQueries());
                assertNull(result.malt().minimumIdentity());assertNull(result.malt().maximumIdentity());
                assertTrue(result.warnings().stream().anyMatch(x->x.contains("No aligned queries")));empty++;
            }
        }
        assertEquals(24,refused);assertEquals(1,empty);
    }

    private NfcoreEagerScreeningReads.Inspection inspect(Path reference,Path mutations,Map<?,?> row,
            Map<?,?> replacements,Map<?,?> limits,Map<String,Long> refs) throws IOException {
        return NfcoreEagerScreeningReads.inspect(path(reference,mutations,row,replacements,"mapped_bam"),
            path(reference,mutations,row,replacements,"filtered_bam"),path(reference,mutations,row,replacements,"unmapped_fastq"),
            path(reference,mutations,row,replacements,"malt_sam_gzip"),refs,(int)value(limits,"minimum_mapq",37),
            value(limits,"minimum_length",0),new NfcoreEagerScreeningReads.Limits(value(limits,"records",1000000),
                value(limits,"queries",1000000),value(limits,"query_bases",50000000),value(limits,"alignments",1000000)));
    }
    private Map<String,Long> references(Path reference,Path mutations,Map<?,?> replacements) throws IOException {
        return NfcoreEagerScreeningReads.references(path(reference,mutations,Map.of("reference_db","ref.db"),replacements,"reference_db"),
            path(reference,mutations,Map.of("reference_index","ref.idx"),replacements,"reference_index"),
            path(reference,mutations,Map.of("reference_info","ref.inf"),replacements,"reference_info"),1000000,10000000000L);
    }
    private long value(Map<?,?> values,String name,long fallback) throws IOException {
        return values.containsKey(name)?NextflowFiles.integer(values.get(name),0,Long.MAX_VALUE):fallback;
    }
    private Path path(Path reference,Path mutations,Map<?,?> row,Map<?,?> replacements,String name) throws IOException {
        return replacements.containsKey(name)?mutations.resolve(NextflowFiles.member((String)replacements.get(name))):reference.resolve(NextflowFiles.member((String)row.get(name)));
    }
    private Path extract() throws Exception {
        Path root=Files.createDirectory(temporary.resolve("reference-fixture"));Map<?,?> metadata;
        try(var source=getClass().getResourceAsStream("/org/jlinalg/workflow/nfcore-eager-screening/SOURCE.json")) {
            assertNotNull(source);metadata=NextflowFiles.object(SimpleJson.parse(new String(source.readAllBytes(),StandardCharsets.UTF_8)));
        }
        var expected=NextflowFiles.object(metadata.get("reference_files"));var seen=new HashSet<String>();
        try(var source=getClass().getResourceAsStream("/org/jlinalg/workflow/nfcore-eager-screening/reference.zip")) {
            assertNotNull(source);byte[] all=source.readNBytes(16777217);assertTrue(all.length<=16777216);
            assertEquals(NextflowFiles.fingerprint(metadata.get("reference_zip"),16777216),new NextflowFiles.Pin(all.length,NextflowFiles.sha(all)));
            try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(all))) {
                int count=0;long total=0;
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    Path path=root.resolve(entry.getName()).normalize();assertTrue(path.startsWith(root));assertFalse(entry.isDirectory());assertTrue(++count<100);
                    byte[] bytes=zip.readNBytes(16777217);assertTrue(bytes.length<=16777216);total+=bytes.length;assertTrue(total<=67108864);
                    assertTrue(seen.add(entry.getName()));assertEquals(NextflowFiles.fingerprint(expected.get(entry.getName()),16777216),new NextflowFiles.Pin(bytes.length,NextflowFiles.sha(bytes)));
                    Files.createDirectories(path.getParent());Files.write(path,bytes);
                }
            }
        }
        assertEquals(expected.keySet(),seen);return root;
    }
}
