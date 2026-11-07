/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import org.jlinalg.genetics.BedGraphSignals.Segment;
import org.jlinalg.genetics.BigWigSignal;
import org.jlinalg.genetics.BigWigWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BigWigWriterTest {
    @TempDir Path temporary;
    static Stream<BigWigWriter.Options> storage(){var values=new ArrayList<BigWigWriter.Options>();for(boolean compressed:List.of(true,false))for(int items:List.of(1,2,1024))for(int fanout:List.of(2,256))values.add(new BigWigWriter.Options(compressed,items,fanout,10000000,false,false));return values.stream();}
    @ParameterizedTest @MethodSource("storage")
    void independentExistingReaderRecoversEveryRawIntervalAcrossStorageAndIndexProfiles(BigWigWriter.Options options)throws Exception {
        var rows=List.of(new Segment("chr2",4,6,-.1),new Segment("chr2",9,20,-0.0),new Segment("chr1",0,1,2),new Segment("chr1",3,10,3),new Segment("chr10",0,7,1.0/3));Path file=temporary.resolve("track.bw");var report=BigWigWriter.write(file,Map.of("chr1",100L,"chr2",100L,"chr10",100L,"unused",50L),rows,options,()->{});assertEquals(report,BigWigWriter.verify(file,Map.of("chr1",100L,"chr2",100L,"chr10",100L,"unused",50L),rows,options,()->{}));assertEquals(List.of("chr1","chr10","chr2"),new ArrayList<>(report.dictionary().keySet()));assertEquals(28,report.coveredBases());assertEquals(2,report.roundedValues());
        var decoded=BigWigSignal.read(file,10000000,100000,1000000);assertEquals(report.dictionary(),decoded.dictionary());assertEquals(options.compressed(),decoded.compressed());assertEquals(rows.size(),decoded.segments().size());for(var row:rows){var actual=decoded.segments().stream().filter(v->v.chromosome().equals(row.chromosome())&&v.start()==row.start()).findFirst().orElseThrow();assertEquals(row.end(),actual.end());assertEquals(Float.floatToRawIntBits((float)row.value()),Float.floatToRawIntBits((float)actual.value()));}
    }
    @Test void multipleInternalTreeLevelsAndManyRawBlocksRemainQueryableByExistingReader()throws Exception {
        var lengths=new LinkedHashMap<String,Long>();var rows=new ArrayList<Segment>();for(int chr=0;chr<270;chr++){String name="chr"+String.format(java.util.Locale.ROOT,"%04d",chr);lengths.put(name,1000L);for(int row=0;row<7;row++)rows.add(new Segment(name,3L*row,3L*row+2,chr-row));}var options=new BigWigWriter.Options(true,2,2,10000000,true,true);Path file=temporary.resolve("branches.bw");var written=BigWigWriter.write(file,lengths,rows,options,()->{});assertEquals(1080,written.sections());assertEquals(written,BigWigWriter.verify(file,lengths,rows,options,()->{}));var read=BigWigSignal.read(file,10000000,100000,1000000);assertEquals(270,read.dictionary().size());assertEquals(1890,read.segments().size());assertEquals(1080,read.rawBlocks());
    }
    @Test void precisionOverflowUnderflowAndSignedZeroAreExplicit()throws Exception {
        var reference=Map.of("chr",100L);var rows=List.of(new Segment("chr",0,1,1e-50),new Segment("chr",2,3,-1e-50),new Segment("chr",4,5,-0.0),new Segment("chr",6,7,Float.MAX_VALUE));var options=BigWigWriter.Options.defaults();Path file=temporary.resolve("precision.bw");var result=BigWigWriter.write(file,reference,rows,options,()->{});assertEquals(2,result.roundedValues());assertEquals(2,result.underflowedValues());assertTrue(Double.isFinite(result.sumSquares()));assertEquals(result,BigWigWriter.verify(file,reference,rows,options,()->{}));var decoded=BigWigSignal.read(file,100000,1000,100000);assertEquals(Integer.MIN_VALUE,Float.floatToRawIntBits((float)decoded.segments().get(1).value()));assertEquals(Integer.MIN_VALUE,Float.floatToRawIntBits((float)decoded.segments().get(2).value()));
        assertThrows(IOException.class,()->BigWigWriter.write(temporary.resolve("overflow.bw"),reference,List.of(new Segment("chr",0,1,1e40)),options,()->{}));assertFalse(Files.exists(temporary.resolve("overflow.bw")));var exact=new BigWigWriter.Options(true,1024,256,100000,true,false);assertThrows(IOException.class,()->BigWigWriter.write(temporary.resolve("loss.bw"),reference,List.of(new Segment("chr",0,1,.1)),exact,()->{}));assertFalse(Files.exists(temporary.resolve("loss.bw")));
    }
    @Test void uint32CoordinatesAndLengthsAreSupportedWithoutSignedIntTruncation()throws Exception {
        var reference=Map.of("huge",4294967295L);var rows=List.of(new Segment("huge",2147483648L,2147483651L,2),new Segment("huge",4294967294L,4294967295L,3));var options=BigWigWriter.Options.defaults();Path file=temporary.resolve("uint32.bw");var result=BigWigWriter.write(file,reference,rows,options,()->{});assertEquals(4,result.coveredBases());assertEquals(9,result.sum());assertEquals(result,BigWigWriter.verify(file,reference,rows,options,()->{}));
    }
    @Test void usedAndCompleteDictionaryPoliciesPreserveTheirDeclaredPopulations()throws Exception {
        var reference=Map.of("chr1",100L,"unused",50L);var rows=List.of(new Segment("chr1",0,2,0));var all=new BigWigWriter.Options(true,1024,256,100000,true,true);Path file=temporary.resolve("all.bw");var report=BigWigWriter.write(file,reference,rows,all,()->{});assertEquals(2,report.dictionary().size());assertEquals(2,report.coveredBases());assertEquals(0,report.sum());assertEquals(report,BigWigWriter.verify(file,reference,rows,all,()->{}));assertEquals(report.dictionary(),BigWigSignal.read(file,100000,10000,100000).dictionary());
    }
    @Test void emptyDeclaredTrackIsDistinctFromAnExplicitZeroAndRequiresAllReferencePolicy()throws Exception {
        var reference=Map.of("chr1",100L);var all=new BigWigWriter.Options(true,1024,256,100000,true,true);Path file=temporary.resolve("empty.bw");var report=BigWigWriter.write(file,reference,List.of(),all,()->{});assertEquals(0,report.coveredBases());assertEquals(0,report.records());assertEquals(0,report.sections());assertEquals(report,BigWigWriter.verify(file,reference,List.of(),all,()->{}));assertEquals(0,BigWigSignal.read(file,100000,10000,100000).segments().size());assertThrows(IOException.class,()->BigWigWriter.write(temporary.resolve("empty-used.bw"),reference,List.of(),BigWigWriter.Options.defaults(),()->{}));
    }
    @Test void unicodeChromosomeKeysUseUnsignedUtf8OrderingAndRemainLiteral()throws Exception {
        var reference=Map.of("chr\uE000",100L,"chr\uD800\uDC00",100L);var rows=List.of(new Segment("chr\uD800\uDC00",0,2,3),new Segment("chr\uE000",0,1,4));Path file=temporary.resolve("unicode.bw");var report=BigWigWriter.write(file,reference,rows,BigWigWriter.Options.defaults(),()->{});assertEquals(List.of("chr\uE000","chr\uD800\uDC00"),new ArrayList<>(report.dictionary().keySet()));assertEquals(report,BigWigWriter.verify(file,reference,rows,BigWigWriter.Options.defaults(),()->{}));assertEquals(report.dictionary(),BigWigSignal.read(file,100000,10000,100000).dictionary());
    }
    @Test void unsortedOverlappingReappearingAndUnknownContigsFailBeforeCreatingOutput()throws Exception {
        var reference=Map.of("chr1",20L,"chr2",20L);var cases=List.of(List.of(new Segment("chr1",2,4,1),new Segment("chr1",1,2,2)),List.of(new Segment("chr1",0,4,1),new Segment("chr1",3,5,2)),List.of(new Segment("chr1",0,1,1),new Segment("chr2",0,1,1),new Segment("chr1",2,3,1)),List.of(new Segment("unknown",0,1,1)),List.of(new Segment("chr1",0,21,1)));int number=0;for(var rows:cases){Path file=temporary.resolve("invalid-"+(number++)+".bw");assertThrows(IOException.class,()->BigWigWriter.write(file,reference,rows,BigWigWriter.Options.defaults(),()->{}));assertFalse(Files.exists(file));}
    }
    @Test void existingOutputCapacityAndCancellationNeverAdmitAPartialFile()throws Exception {
        var reference=Map.of("chr1",20L);var rows=List.of(new Segment("chr1",0,3,1));Path existing=temporary.resolve("existing.bw");Files.writeString(existing,"keep");assertThrows(IOException.class,()->BigWigWriter.write(existing,reference,rows,BigWigWriter.Options.defaults(),()->{}));assertEquals("keep",Files.readString(existing));var small=new BigWigWriter.Options(true,1,256,256,true,false);Path partial=temporary.resolve("capacity.bw");assertThrows(IOException.class,()->BigWigWriter.write(partial,reference,rows,small,()->{}));assertTrue(Files.exists(partial));assertThrows(IOException.class,()->BigWigWriter.verify(partial,reference,rows,small,()->{}));Path cancelled=temporary.resolve("cancelled.bw");assertThrows(CancellationException.class,()->BigWigWriter.write(cancelled,reference,rows,BigWigWriter.Options.defaults(),()->{try{if(Files.exists(cancelled)&&Files.size(cancelled)>0)throw new CancellationException("controlled");}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}}));assertTrue(Files.exists(cancelled));assertThrows(IOException.class,()->BigWigWriter.verify(cancelled,reference,rows,BigWigWriter.Options.defaults(),()->{}));
    }
    @Test void changedHeadersTreesSummaryRawBlocksAndTrailingBytesFailCompleteVerification()throws Exception {
        var reference=Map.of("chr1",100L);var rows=List.of(new Segment("chr1",0,3,.1),new Segment("chr1",4,5,2));var options=BigWigWriter.Options.defaults();Path file=temporary.resolve("tamper.bw");BigWigWriter.write(file,reference,rows,options,()->{});byte[] raw=Files.readAllBytes(file);var head=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);long data=head.getLong(16),index=head.getLong(24);for(int offset:new int[]{0,4,6,8,16,24,32,36,44,52,56,64,72,104,136,Math.toIntExact(data),Math.toIntExact(data+10),Math.toIntExact(index),Math.toIntExact(index+48),raw.length-1}){byte[] changed=raw.clone();changed[offset]^=1;Files.write(file,changed);assertThrows(IOException.class,()->BigWigWriter.verify(file,reference,rows,options,()->{}),"offset "+offset);}Files.write(file,java.util.Arrays.copyOf(raw,raw.length+1));assertThrows(IOException.class,()->BigWigWriter.verify(file,reference,rows,options,()->{}));
    }
}
