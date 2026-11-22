/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DnaMotifScannerTest {
    @TempDir Path root;
    private static DnaPssm model() { return DnaPssm.build(new double[][]{{.8,.05,.05,.1},{.05,.8,.1,.05},{.1,.05,.8,.05},{.05,.1,.05,.8}},new double[]{.25,.25,.25,.25},20,.1); }
    private Path fasta(String text) throws IOException { Path p=root.resolve("test.fa");Files.writeString(p,text);return p; }
    private static DnaMotifScanner.Options options(boolean both,boolean genomic,double p) { return new DnaMotifScanner.Options(both,genomic,p,10000,100000,false); }

    @Test void sequenceOnlyInspectionPreservesMetadataAndCountsWithoutScoring() throws Exception {
        var records=new ArrayList<DnaMotifScanner.Sequence>();var counts=DnaMotifScanner.inspect(fasta(">chr1:11-16 full\nUuX.Z-\n>empty\n"),true,100,true,records::add);
        assertEquals(2,counts.sequences());assertEquals(6,counts.residues());assertEquals(2,counts.canonicalResidues());assertEquals(2,counts.uracilAliases());assertEquals(2,counts.ambiguityAliases());assertEquals(2,counts.convertedInvalid());assertEquals(0,counts.coordinateSpanMismatches());assertEquals(11,records.get(0).start());assertEquals(0,records.get(1).residues());
    }

    @Test void streamingWindowsPreserveCaseStrandsCoordinatesHeadersAndDuplicateOrdinals() throws Exception {
        var hits = new ArrayList<DnaMotifScanner.Hit>();var ids = new ArrayList<DnaMotifScanner.Sequence>();
        var counts = DnaMotifScanner.scan(fasta(">chr2:101-104 full\tcomment\nac\ngt\n>chr2:101-104 again\nACGT\n>empty\n>short\nAC\n"),model(),options(true,true,1.0/256),hits::add,ids::add);
        assertEquals(4,counts.sequences());assertEquals(10,counts.residues());assertEquals(2,counts.windows());assertEquals(4,counts.scoredSites());assertEquals(4,counts.hits());
        assertEquals(0,counts.coordinateSpanMismatches());assertEquals(4,ids.size());assertEquals(0,ids.get(2).residues());
        assertEquals("chr2:101-104",ids.get(0).originalId());assertEquals("chr2",hits.get(0).sequenceName());assertEquals(101,hits.get(0).start());assertEquals(104,hits.get(0).stop());
        assertEquals("acgt",hits.get(0).word());assertEquals("ACGT",hits.get(1).word());assertEquals('-',hits.get(1).strand());assertEquals(2,hits.get(2).sequenceOrdinal());
        assertEquals("chr2:101-104 full\tcomment",ids.get(0).header());
    }

    @Test void ambiguityMasksEveryOverlappingWindowAndNeverBridgesRecords() throws Exception {
        var hits=new ArrayList<DnaMotifScanner.Hit>();var counts=DnaMotifScanner.scan(fasta(">first\nACGTNACGT\n>tail\nAC\n>head\nGT\n"),model(),options(false,false,1),hits::add,s -> { });
        assertEquals(6,counts.windows());assertEquals(4,counts.ambiguousWindows());assertEquals(2,counts.scoredSites());assertEquals(2,hits.size());assertEquals(6,hits.get(1).start());
    }

    @Test void gzipMagicWrappedWhitespaceAndRecordedAlphabetAliasesWork() throws Exception {
        Path path=root.resolve("not-a-gzip-extension");try(var out=new GZIPOutputStream(Files.newOutputStream(path))) { out.write(">alias\r\nac gu\r\nX.\r\n".getBytes(StandardCharsets.US_ASCII)); }
        var hits=new ArrayList<DnaMotifScanner.Hit>();var counts=DnaMotifScanner.scan(path,model(),options(false,false,1),hits::add,s -> { });
        assertEquals(6,counts.residues());assertEquals(1,counts.uracilAliases());assertEquals(2,counts.ambiguityAliases());assertEquals(2,counts.ambiguousWindows());assertEquals("acgu",hits.get(0).word());assertEquals(model().score("acgt"),hits.get(0).score());
    }

    @Test void unknownResiduesNeedExplicitConversionAndNonAsciiIsAlwaysRejected() throws Exception {
        assertThrows(IOException.class,() -> DnaMotifScanner.scan(fasta(">id\nACGTZ-\n"),model(),options(false,false,1),h -> { },s -> { }));
        var counts=DnaMotifScanner.scan(fasta(">id\nACGTZ-\n"),model(),new DnaMotifScanner.Options(false,false,1,100,100,true),h -> { },s -> { });assertEquals(2,counts.convertedInvalid());
        assertThrows(IOException.class,() -> DnaMotifScanner.scan(fasta(">id\nACGé\n"),model(),new DnaMotifScanner.Options(false,false,1,100,100,true),h -> { },s -> { }));
    }

    @Test void nativeHitAndDecompressionLimitsFailRatherThanTruncate() throws Exception {
        Path path=fasta(">id\nACGTACGT\n");
        assertThrows(IOException.class,() -> DnaMotifScanner.scan(path,model(),new DnaMotifScanner.Options(true,false,1,1,100,false),h -> { },s -> { }));
        assertThrows(IOException.class,() -> DnaMotifScanner.scan(path,model(),new DnaMotifScanner.Options(false,false,1,100,4,false),h -> { },s -> { }));
    }

    @Test void malformedAndOversizedHeadersAndCoordinatesFailWithExplicitErrors() throws Exception {
        for(String text:new String[]{"ACGT\n",">\nACGT\n","> leading\nACGT\n",">"+"a".repeat(16385)+"\nACGT\n",">chr:0-4\nACGT\n",">chr:9-4\nACGT\n"})
            assertThrows(IOException.class,() -> DnaMotifScanner.scan(fasta(text),model(),options(false,true,1),h -> { },s -> { }),text.substring(0,Math.min(40,text.length())));
    }

    @Test void coordinateLengthMismatchIsCountedAndSequenceModePreservesFullName() throws Exception {
        Path p=fasta(">chr1:100-200\nACGT\n");var hits=new ArrayList<DnaMotifScanner.Hit>();
        assertEquals(1,DnaMotifScanner.scan(p,model(),options(false,true,1),hits::add,s -> { }).coordinateSpanMismatches());assertEquals(100,hits.get(0).start());
        hits.clear();DnaMotifScanner.scan(p,model(),options(false,false,1),hits::add,s -> { });assertEquals("chr1:100-200",hits.get(0).sequenceName());assertEquals(1,hits.get(0).start());
    }
}
