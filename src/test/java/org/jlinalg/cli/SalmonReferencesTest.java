/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.sequence.SalmonIndexReferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonReferencesTest {
    @TempDir Path temporary;
    private static final RepeatFasta.Limits FASTA = new RepeatFasta.Limits(1000000, 1000000, 100000, 100000, 1000, 1024, 512, 2000000);
    private static final SalmonReferences.Limits LIMITS = new SalmonReferences.Limits(FASTA, 100000, 1000, 512, 4000000);
    @Test void rawCaseExactVersionedIdsAndHeadersArePreserved() throws Exception {
        Path fasta = file("raw.fa", ">tx.1 original description\nacguNRY\n>tx.2\nACGTACGT\n");
        var input = inspect(fasta, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, null);
        assertEquals(List.of("tx.1", "tx.2"), input.references().stream().map(SalmonReferences.Reference::indexName).toList());
        assertEquals("tx.1 original description", input.references().get(0).raw().header()); assertEquals(7, input.references().get(0).raw().length());
        assertEquals(NextflowFiles.sha("acguNRY".getBytes(StandardCharsets.US_ASCII)), input.references().get(0).raw().sequenceSha256()); input.verify(() -> { });
    }
    @Test void gencodeSplittingIsDeclaredAndNeverStripsTranscriptVersions() throws Exception {
        Path fasta = file("gencode.fa", ">tx.1|gene1 comment\nACGT\n>tx.2|gene1\nTGCA\n");
        assertEquals(List.of("tx.1", "tx.2"), inspect(fasta, SalmonReferences.Headers.GENCODE_FIRST_PIPE, null).references().stream().map(SalmonReferences.Reference::indexName).toList());
        assertEquals(List.of("tx.1|gene1", "tx.2|gene1"), inspect(fasta, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, null).references().stream().map(SalmonReferences.Reference::indexName).toList());
    }
    @Test void collidingGencodePrefixesCannotMergeDistinctInputs() throws Exception {
        Path fasta = file("collision.fa", ">tx.1|geneA\nACGT\n>tx.1|geneB\nTGCA\n");
        assertThrows(IOException.class, () -> inspect(fasta, SalmonReferences.Headers.GENCODE_FIRST_PIPE, null));
    }
    @Test void decoyNamesBindRawTokensBeforeGencodeSplitting() throws Exception {
        Path fasta = file("decoy.fa", ">tx.1|gene1\nACGT\n>chrD|raw comment\nTGCA\n"), decoy = file("decoy.txt", "chrD|raw\n");
        var result = inspect(fasta, SalmonReferences.Headers.GENCODE_FIRST_PIPE, decoy); assertTrue(result.references().get(1).declaredDecoy()); assertEquals("chrD", result.references().get(1).indexName());
        Path wrong = file("wrong-decoy.txt", "chrD\n"); assertThrows(IOException.class, () -> inspect(fasta, SalmonReferences.Headers.GENCODE_FIRST_PIPE, wrong));
    }
    @ParameterizedTest @ValueSource(strings={"chrD\r\n", "chrD\nchrD\n", "chrD\n\n", " chrD\n", "chrD \n", ""})
    void malformedDecoyFilesFailWithoutImplicitLineOrIdentityRepair(String raw) throws Exception {
        Path fasta = file("input.fa", ">tx\nACGT\n>chrD\nTGCA\n"), decoy = file("decoys.txt", raw);
        assertThrows(IOException.class, () -> inspect(fasta, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, decoy));
    }
    @Test void finalDecoyLineNeedNotHaveATerminalLf() throws Exception {
        var result = inspect(file("input.fa", ">tx\nACGT\n>chrD\nTGCA\n"), SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, file("decoys.txt", "chrD")); assertEquals(1, result.declaredRawDecoys().size());
    }
    @Test void missingAndNonterminalDecoysAreRejected() throws Exception {
        Path decoys = file("decoys.txt", "chrD\n");
        assertThrows(IOException.class, () -> inspect(file("absent.fa", ">tx\nACGT\n"), SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, decoys));
        assertThrows(IOException.class, () -> inspect(file("order.fa", ">chrD\nTGCA\n>tx\nACGT\n"), SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, decoys));
    }
    @Test void noDeclaredNondecoyCannotCreateAnExpressionPopulation() throws Exception {
        assertThrows(IOException.class, () -> inspect(file("input.fa", ">chrD\nTGCA\n"), SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, file("decoys.txt", "chrD\n")));
    }
    @Test void concatenatedGzipIsCompleteAndItsRawCompressedPinIsRetained() throws Exception {
        byte[] first = gzip(">tx1\nAC"), second = gzip("GT\n>tx2\nTGCA\n"); var joined = new ByteArrayOutputStream(); joined.write(first); joined.write(second);
        Path fasta = temporary.resolve("input.fa.gz").toAbsolutePath(); Files.write(fasta, joined.toByteArray());
        var result = SalmonReferences.inspect(fasta, RepeatFasta.Encoding.GZIP_UTF8, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, null, LIMITS, () -> { });
        assertEquals(2, result.references().size()); assertEquals(NextflowFiles.sha(joined.toByteArray()), result.fastaPin().sha256());
    }
    @Test void gzipTrailerFailureCannotReturnAnInputCensus() throws Exception {
        byte[] raw = gzip(">tx\nACGT\n"); raw[raw.length - 8] ^= 1; Path fasta = temporary.resolve("corrupt.gz").toAbsolutePath(); Files.write(fasta, raw);
        assertThrows(IOException.class, () -> SalmonReferences.inspect(fasta, RepeatFasta.Encoding.GZIP_UTF8, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, null, LIMITS, () -> { }));
    }
    @ParameterizedTest @ValueSource(strings={"fasta", "decoy"})
    void changedSourceFilesInvalidateTheProtectedContext(String which) throws Exception {
        Path fasta = file("input.fa", ">tx\nACGT\n>chrD\nTGCA\n"), decoy = file("decoys.txt", "chrD\n"); var input = inspect(fasta, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, decoy);
        Files.writeString(which.equals("fasta") ? fasta : decoy, "changed\n"); assertThrows(IOException.class, () -> input.verify(() -> { }));
    }
    @ParameterizedTest @ValueSource(strings={"decoy-bytes", "decoy-count", "decoy-name", "retained"})
    void completeInputMetadataCapacityIsExplicit(String dimension) throws Exception {
        Path fasta = file("input.fa", ">tx\nACGT\n>chrD\nTGCA\n>chrE\nGCGC\n"), decoy = file("decoys.txt", "chrD\nchrE\n");
        var limits = new SalmonReferences.Limits(FASTA, dimension.equals("decoy-bytes") ? 2 : 100000, dimension.equals("decoy-count") ? 1 : 1000,
            dimension.equals("decoy-name") ? 2 : 512, dimension.equals("retained") ? 262144 : 4000000);
        assertThrows(IOException.class, () -> SalmonReferences.inspect(fasta, RepeatFasta.Encoding.PLAIN_UTF8, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, decoy, limits, () -> { }));
    }
    @Test void cancellationCannotReturnAProtectedInput() throws Exception {
        Path fasta = file("input.fa", ">tx\nACGT\n"); assertThrows(CancellationException.class, () -> SalmonReferences.inspect(fasta, RepeatFasta.Encoding.PLAIN_UTF8, SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, null, LIMITS, () -> { throw new CancellationException(); }));
    }
    @ParameterizedTest @ValueSource(strings={"mixed-default", "mixed-keep", "gencode-keep"})
    void originalIndexesBindEveryRawInputAndKeepSourcePopulationDisagreement(String name) throws Exception {
        Path fasta = temporary.resolve("input.fa").toAbsolutePath(), decoy = temporary.resolve("decoys.txt").toAbsolutePath();
        Files.write(fasta, SalmonMetadataTest.original(name + "/inputs/reference.fa")); Files.write(decoy, SalmonMetadataTest.original(name + "/inputs/decoys.txt"));
        var input = inspect(fasta, name.startsWith("gencode") ? SalmonReferences.Headers.GENCODE_FIRST_PIPE : SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, decoy);
        var index = index(name); var info = SalmonMetadata.parse(new String(SalmonMetadataTest.original(name + "/index/info.json"), StandardCharsets.UTF_8), 8192, 1000);
        var ledger = SalmonReferences.bind(input, index, 15, SalmonMetadata.integer(info.get("num_decoys"), 1000), SalmonMetadata.unsigned(info.get("first_decoy_index")), () -> { });
        assertEquals(input.references().size(), ledger.inputs().size()); assertEquals(index.references().size(), ledger.retained());
        assertEquals(name.equals("mixed-default") ? 3 : name.equals("mixed-keep") ? 2 : 0, ledger.notRetained());
        assertEquals(List.of(name.equals("gencode-keep") ? "ENST3.3" : "shortAtK"), ledger.decoys().disagreements());
        for (var row : ledger.inputs()) if (row.retained() == null) assertEquals("not-retained-by-original-index-reason-separate", row.status());
    }
    @Test void indexIdentitiesLengthsAndInputOrderAreProtected() throws Exception {
        var input = inspect(file("input.fa", ">first\nACGTACGTACGTACGT\n>second\nTGCAACGTACGTACGT\n"), SalmonReferences.Headers.SOURCE_WHITESPACE_TOKEN, null);
        for (var refs : List.of(
            List.of(new SalmonIndexReferences.Reference(0, "alien", 16, 16, 0)),
            List.of(new SalmonIndexReferences.Reference(0, "first", 17, 16, 0)),
            List.of(new SalmonIndexReferences.Reference(0, "second", 16, 16, 0), new SalmonIndexReferences.Reference(1, "first", 16, 16, 1)))) {
            assertThrows(IOException.class, () -> SalmonReferences.bind(input, summary(refs, refs.size()), 15, 0, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), () -> { }));
        }
    }
    private SalmonReferences.Context inspect(Path fasta, SalmonReferences.Headers headers, Path decoys) throws IOException { return SalmonReferences.inspect(fasta, RepeatFasta.Encoding.PLAIN_UTF8, headers, decoys, LIMITS, () -> { }); }
    private Path file(String name, String text) throws IOException { Path path = temporary.resolve(name).toAbsolutePath(); Files.writeString(path, text); return path.toRealPath(); }
    private static byte[] gzip(String text) throws IOException { var raw = new ByteArrayOutputStream(); try (var output = new GZIPOutputStream(raw)) { output.write(text.getBytes(StandardCharsets.UTF_8)); } return raw.toByteArray(); }
    private static SalmonIndexReferences.Summary summary(List<SalmonIndexReferences.Reference> refs, int indexed) { var pin = new SalmonIndexReferences.FileReceipt(0, "0".repeat(64)); return new SalmonIndexReferences.Summary(refs, indexed, 0, 0, 262144, pin, pin, pin); }
    private static SalmonIndexReferences.Summary index(String name) throws Exception {
        return SalmonIndexReferences.read(new ByteArrayInputStream(SalmonMetadataTest.original(name + "/index/ctable.bin")), new ByteArrayInputStream(SalmonMetadataTest.original(name + "/index/complete_ref_lens.bin")),
            new ByteArrayInputStream(SalmonMetadataTest.original(name + "/index/reflengths.bin")), new SalmonIndexReferences.Limits(100, 1000, 8192, 100000, 1000000, 16777216), (o, c, f, n, p, fw) -> { }, () -> { });
    }
}
