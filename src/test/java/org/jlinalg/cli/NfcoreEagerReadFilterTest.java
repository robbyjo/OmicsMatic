/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.jlinalg.workflow.NfcoreEagerReadFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Compare every original helper BAM record, not merely the filtering formula. */
class NfcoreEagerReadFilterTest {
    @TempDir Path temporary;

    @Test void everyOriginalPopulationHasTheSameCompleteBamSemantics() throws Exception {
        Path reference=extract();var cases=NextflowFiles.list(NextflowFiles.json(reference.resolve("cases.json"),1048576));
        assertEquals(36,cases.size());int empty=0;
        for(Object raw:cases) {
            var row=NextflowFiles.object(raw);Path input=reference.resolve((String)row.get("input_member"));
            Path expected=reference.resolve((String)row.get("expected_member")),actual=temporary.resolve(row.get("name")+".bam");
            long minimum=NextflowFiles.integer(row.get("minimum_length"),Long.MIN_VALUE,Long.MAX_VALUE);boolean all=(Boolean)row.get("include_unmapped");
            var before=NextflowFiles.pin(input,16777216,()->{});
            var result=NfcoreEagerReadFilter.filterBam(input,actual,minimum,all,1000000);
            assertEquals(before,NextflowFiles.pin(input,16777216,()->{}));
            assertEquals(NextflowFiles.integer(row.get("input_records"),0,1000000),result.inputRecords());
            assertEquals(NextflowFiles.integer(row.get("output_records"),0,1000000),result.outputRecords());
            assertEquals(NextflowFiles.integer(row.get("output_unmapped"),0,1000000),result.outputUnmappedRecords());
            if(result.outputRecords()==0)empty++;
            try(var original=reader(expected);var nativeBam=reader(actual)) {
                assertEquals(original.getFileHeader(),nativeBam.getFileHeader(),"Every header field: "+row.get("name"));
                var left=original.iterator();var right=nativeBam.iterator();long count=0;
                while(left.hasNext()) {assertTrue(right.hasNext());assertEquals(canonical(left.next()),canonical(right.next()),"Complete record "+count+" in "+row.get("name"));count++;}
                assertFalse(right.hasNext());assertEquals(result.outputRecords(),count);
            }
        }
        assertTrue(empty>0,"Original zero-output controls are retained and assessed");
    }

    @Test void refuseOverwriteCapacityAndMalformedInputWithoutAdmittingAPartialResult() throws Exception {
        Path reference=extract(),input=reference.resolve("inputs/input0.bam"),output=temporary.resolve("existing.bam");
        var before=NextflowFiles.pin(input,16777216,()->{});Files.writeString(output,"keep original output");
        assertThrows(IOException.class,()->NfcoreEagerReadFilter.filterBam(input,output,30,true,1000000));
        assertEquals("keep original output",Files.readString(output));
        assertThrows(IOException.class,()->NfcoreEagerReadFilter.filterBam(input,input,30,true,1000000));
        assertEquals(before,NextflowFiles.pin(input,16777216,()->{}));
        Path partial=temporary.resolve("retained-partial.bam");
        var capacity=assertThrows(IOException.class,()->NfcoreEagerReadFilter.filterBam(input,partial,0,true,1));
        assertTrue(capacity.getMessage().contains("capacity"));assertTrue(capacity.getMessage().contains("Required action:"));assertTrue(Files.exists(partial));
        Path malformed=temporary.resolve("malformed.bam");Files.writeString(malformed,"not a BAM");
        assertThrows(IOException.class,()->NfcoreEagerReadFilter.filterBam(malformed,temporary.resolve("refused.bam"),0,true,1000000));
    }

    private htsjdk.samtools.SamReader reader(Path path) {
        return SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS)
            .validationStringency(ValidationStringency.STRICT).open(path);
    }
    private List<String> canonical(SAMRecord record) {
        String text=record.getSAMString();if(text.endsWith("\n"))text=text.substring(0,text.length()-1);
        String[] fields=text.split("\t",-1);Arrays.sort(fields,11,fields.length);return Arrays.asList(fields);
    }
    private Path extract() throws Exception {
        Path root=Files.createDirectory(temporary.resolve("reference"));
        java.util.Map<?,?> metadata;
        try(var source=getClass().getResourceAsStream("/org/jlinalg/workflow/nfcore-eager-filter/SOURCE.json")) {
            assertNotNull(source);metadata=NextflowFiles.object(SimpleJson.parse(new String(source.readAllBytes(),StandardCharsets.UTF_8)));
        }
        var expected=NextflowFiles.object(metadata.get("reference_files"));var seen=new java.util.HashSet<String>();
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/workflow/nfcore-eager-filter/reference.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)) {
                int count=0;long total=0;
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    Path path=root.resolve(entry.getName()).normalize();assertTrue(path.startsWith(root));assertFalse(entry.isDirectory());assertTrue(++count<100);
                    byte[] bytes=zip.readNBytes(16777217);assertTrue(bytes.length<=16777216);total+=bytes.length;assertTrue(total<=67108864);
                    assertTrue(seen.add(entry.getName()));assertEquals(NextflowFiles.fingerprint(expected.get(entry.getName()),16777216),new NextflowFiles.Pin(bytes.length,NextflowFiles.sha(bytes)));
                    Files.createDirectories(path.getParent());Files.write(path,bytes);
                }
            }
        }
        assertEquals(expected.keySet(),seen);
        return root;
    }
}
