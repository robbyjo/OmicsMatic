/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonInferentialReplicatesTest {
    private static byte[] gzip(byte[] raw) throws IOException {
        var out = new ByteArrayOutputStream(); try (var gzip = new GZIPOutputStream(out)) { gzip.write(raw); } return out.toByteArray();
    }
    private static byte[] values(double... values) {
        var buffer = ByteBuffer.allocate(8 * values.length).order(ByteOrder.LITTLE_ENDIAN);
        for (double value : values) buffer.putDouble(value); return buffer.array();
    }
    private static SalmonInferentialReplicates.Summary read(String names, List<String> expected, int samples, byte[] raw,
            SalmonInferentialReplicates.Consumer consumer) throws IOException {
        return SalmonInferentialReplicates.read(new ByteArrayInputStream(gzip(names.getBytes(StandardCharsets.UTF_8))),
            new ByteArrayInputStream(gzip(raw)), expected, samples, 1000000, consumer, () -> { });
    }
    private static final SalmonInferentialReplicates.Consumer IGNORE = (sample, i, name, value) -> { };

    @Test void completeSampleMajorBinary64PopulationIsPreservedBitForBit() throws Exception {
        double[] original = {0, 0.25, 1.5e-30, 9007199254740992d, 3.125, 0};
        var received = new ArrayList<Double>(); var positions = new ArrayList<String>();
        var summary = read("a\tb\tc\n", List.of("a", "b", "c"), 2, values(original),
            (sample, i, name, value) -> { received.add(value); positions.add(sample+":"+i+":"+name); });
        assertEquals(6, summary.values()); assertEquals(2, summary.replicates());
        assertEquals(List.of("0:0:a", "0:1:b", "0:2:c", "1:0:a", "1:1:b", "1:2:c"), positions);
        for (int i=0;i<original.length;i++) assertEquals(Double.doubleToRawLongBits(original[i]), Double.doubleToRawLongBits(received.get(i)));
        assertFalse(summary.negativeZeroObserved()); assertEquals(0.25, summary.minimumReplicateTotal());
    }
    @Test void exactUtf8IdentitiesAndPipeVersionsRemainUnchanged() throws Exception {
        var names=List.of("alpha.1|gene", "\u03b2.2");
        var summary=read(String.join("\t",names)+"\n", names, 1, values(2,3), IGNORE);
        assertEquals(names,summary.targets()); assertEquals(5,summary.maximumReplicateTotal());
        assertThrows(UnsupportedOperationException.class,()->summary.targets().add("extra"));
    }
    @Test void signedZeroIsRetainedAndReported() throws Exception {
        var received=new ArrayList<Double>();var summary=read("a\n",List.of("a"),1,values(-0.0),(s,i,n,v)->received.add(v));
        assertTrue(summary.negativeZeroObserved());assertEquals(Long.MIN_VALUE,Double.doubleToRawLongBits(received.get(0)));
    }
    @ParameterizedTest @ValueSource(doubles={-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void invalidCountsCannotProduceAReceipt(double value) {
        assertThrows(IOException.class,()->read("a\n",List.of("a"),1,values(value),IGNORE));
    }
    @Test void completeFamilyMustMatchDeclaredOrder() {
        assertThrows(IOException.class,()->read("b\ta\n",List.of("a","b"),1,values(1,2),IGNORE));
    }
    @Test void namesAreNeitherTrimmedNorSilentlyExtended() {
        for(String names:List.of("a ","a\nextra\n","a\tb\n","a\r\n","a"))
            assertThrows(IOException.class,()->read(names,List.of("a"),1,values(1),IGNORE),names);
    }
    @Test void duplicateDeclaredIdentitiesFailBeforeAnyValue() {
        assertThrows(IOException.class,()->read("a\ta\n",List.of("a","a"),1,values(1,2),IGNORE));
    }
    @Test void invalidUnicodeCannotBecomeReplacementCharacters() {
        assertThrows(IOException.class,()->read("?\n",List.of("\ud800"),1,values(1),IGNORE));
    }
    @Test void truncatedLastDoubleFailsWithoutASuccessfulPrefixReceipt() throws Exception {
        assertThrows(IOException.class,()->read("a\n",List.of("a"),1,Arrays.copyOf(values(1),7),IGNORE));
    }
    @Test void metadataCannotInferFewerReplicatesFromEndOfFile() throws Exception {
        assertThrows(IOException.class,()->read("a\n",List.of("a"),2,values(1),IGNORE));
    }
    @Test void metadataCannotHideExtraReplicatesAfterAValidPrefix() throws Exception {
        var received=new ArrayList<Double>();
        assertThrows(IOException.class,()->read("a\n",List.of("a"),1,values(1,2),(s,i,n,v)->received.add(v)));
        assertEquals(List.of(1d),received); // Provisional consumer output is deliberately not a receipt.
    }
    @ParameterizedTest @ValueSource(ints={1,4,8})
    void truncatedGzipFooterIsRejected(int removed) throws Exception {
        byte[] names=gzip("a\n".getBytes(StandardCharsets.UTF_8)), counts=gzip(values(1));
        assertThrows(IOException.class,()->SalmonInferentialReplicates.read(new ByteArrayInputStream(names),
            new ByteArrayInputStream(Arrays.copyOf(counts,counts.length-removed)),List.of("a"),1,1,IGNORE,()->{}));
    }
    @ParameterizedTest @ValueSource(ints={4,8})
    void completeGzipSizeAndCrcAreBothChecked(int offset) throws Exception {
        byte[] counts=gzip(values(1));counts[counts.length-offset]^=1;
        assertThrows(IOException.class,()->SalmonInferentialReplicates.read(new ByteArrayInputStream(gzip("a\n".getBytes(StandardCharsets.UTF_8))),
            new ByteArrayInputStream(counts),List.of("a"),1,1,IGNORE,()->{}));
    }
    @Test void extraCompressedMemberIsNotIgnored() throws Exception {
        byte[] first=gzip(values(1)), second=gzip(new byte[0]);var combined=new ByteArrayOutputStream();combined.write(first);combined.write(second);
        assertThrows(IOException.class,()->SalmonInferentialReplicates.read(new ByteArrayInputStream(gzip("a\n".getBytes(StandardCharsets.UTF_8))),
            new ByteArrayInputStream(combined.toByteArray()),List.of("a"),1,1,IGNORE,()->{}));
    }
    @Test void trailingCompressedGarbageIsNotIgnored() throws Exception {
        byte[] counts=gzip(values(1));counts=Arrays.copyOf(counts,counts.length+1);final byte[] altered=counts;
        assertThrows(IOException.class,()->SalmonInferentialReplicates.read(new ByteArrayInputStream(gzip("a\n".getBytes(StandardCharsets.UTF_8))),
            new ByteArrayInputStream(altered),List.of("a"),1,1,IGNORE,()->{}));
    }
    @Test void invalidDeflateCannotBecomeAnEmptySample() throws Exception {
        byte[] counts=gzip(values(1));counts[10]=(byte)255;
        assertThrows(IOException.class,()->SalmonInferentialReplicates.read(new ByteArrayInputStream(gzip("a\n".getBytes(StandardCharsets.UTF_8))),
            new ByteArrayInputStream(counts),List.of("a"),1,1,IGNORE,()->{}));
    }
    @Test void completePopulationCapacityIsCheckedBeforeConsumption() throws Exception {
        var names=new ClosedStream(new byte[0]);var counts=new ClosedStream(new byte[0]);
        assertThrows(IOException.class,()->SalmonInferentialReplicates.read(names,counts,List.of("a","b"),100,199,IGNORE,()->{}));
        assertTrue(names.closed);assertTrue(counts.closed);assertEquals(0,names.position());
    }
    @Test void cancellationClosesBothStreamsAndCannotSealAReceipt() throws Exception {
        var cancelled=new AtomicBoolean();var received=new ArrayList<Double>();
        var names=new ClosedStream(gzip("a\n".getBytes(StandardCharsets.UTF_8)));var counts=new ClosedStream(gzip(values(1,2)));
        assertThrows(CancellationException.class,()->SalmonInferentialReplicates.read(names,counts,List.of("a"),2,2,
            (s,i,n,v)->{received.add(v);cancelled.set(true);},()->{if(cancelled.get())throw new CancellationException();}));
        assertEquals(List.of(1d),received);assertTrue(names.closed);assertTrue(counts.closed);
    }
    @Test void consumerFailurePropagatesAndDoesNotAdmitPartialValues() throws Exception {
        IOException failure=new IOException("staged sink failed");
        assertSame(failure,assertThrows(IOException.class,()->read("a\n",List.of("a"),1,values(1),(s,i,n,v)->{throw failure;})));
    }
    @Test void finiteValuesCannotOverflowTheReplicateTotalSilently() {
        assertThrows(IOException.class,()->read("a\tb\n",List.of("a","b"),1,values(Double.MAX_VALUE,Double.MAX_VALUE),IGNORE));
    }
    @Test void optionalGzipNameAndHeaderCrcAreValidated() throws Exception {
        byte[] original=gzip(values(1));var header=new ByteArrayOutputStream();header.write(original,0,10);
        byte[] h=header.toByteArray();h[3]=10;header.reset();header.write(h);header.write("source\0".getBytes(StandardCharsets.US_ASCII));
        CRC32 crc=new CRC32();crc.update(header.toByteArray());header.write((int)crc.getValue()&255);header.write((int)(crc.getValue()>>>8)&255);header.write(original,10,original.length-10);
        var summary=SalmonInferentialReplicates.read(new ByteArrayInputStream(gzip("a\n".getBytes(StandardCharsets.UTF_8))),
            new ByteArrayInputStream(header.toByteArray()),List.of("a"),1,1,IGNORE,()->{});
        assertEquals(1,summary.values());byte[] invalid=header.toByteArray();invalid[17]^=1;
        assertThrows(IOException.class,()->SalmonInferentialReplicates.read(new ByteArrayInputStream(gzip("a\n".getBytes(StandardCharsets.UTF_8))),
            new ByteArrayInputStream(invalid),List.of("a"),1,1,IGNORE,()->{}));
    }
    private static final class ClosedStream extends ByteArrayInputStream {
        boolean closed;ClosedStream(byte[] bytes){super(bytes);}
        int position(){return pos;}
        @Override public void close(){closed=true;}
    }
}
