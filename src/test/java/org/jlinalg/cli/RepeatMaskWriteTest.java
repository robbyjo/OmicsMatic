/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.RepeatAnnotations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatMaskWriteTest {
    @TempDir Path directory;
    private int serial;
    private Path file(String value) throws IOException { Path path=directory.resolve("input-"+serial++); Files.writeString(path,value); return path; }
    private Path target() { return directory.resolve("output-"+serial++); }
    private static RepeatAnnotations.Hit hit(String query,int start,int end,int length,String name,String kind,boolean marked) {
        return RepeatAnnotations.parseRow("50 0 0 0 "+query+" "+(start+1)+" "+end+" ("+(length-end)+") + "+name+" "+kind+" 1 "+(end-start)+" (0) 1"+(marked?" *":""),"fixture.out",1,RepeatAnnotations.Format.OUT_WITH_ID);
    }
    private static RepeatMaskAudit.Report write(Path input,Path output,List<RepeatAnnotations.Hit> hits,RepeatAnnotations.Mask mode) throws IOException {
        return RepeatMaskAudit.write(input,RepeatFasta.Encoding.PLAIN_UTF8,output,hits,RepeatMaskAudit.Selection.all(),mode,RepeatFastaTest.limits(),1048576,10000000,()->{});
    }
    @Test void writesAllModesAndExactUnionsPreservingCompleteNegativeQueries() throws Exception {
        Path input=file(">q description\nAcGTnnACGT\n>zero\nAcgT\n");
        var hits=List.of(hit("q",5,9,10,"B","SINE/Alu",false),hit("q",1,7,10,"A","DNA/hAT",true));
        for(var mode:RepeatAnnotations.Mask.values()) {
            Path output=target();var report=write(input,output,hits,mode);
            String expected=mode==RepeatAnnotations.Mask.SOFT?"AcgtnnacgT":mode==RepeatAnnotations.Mask.N?"ANNNNNNNNT":"AXXXXXXXXT";
            assertEquals(">q description\n"+expected+"\n>zero\nAcgT\n",Files.readString(output));
            assertEquals(14,report.comparedBases());assertEquals(8,report.coveredBases());assertEquals(1,report.unionIntervals());
            assertEquals(0,report.sequences().get(1).coveredBases());
        }
    }
    @Test void explicitClassNameAndOverlapIntersectionControlsTheWrittenPopulation() throws Exception {
        Path input=file(">q\nAAAAAAAAAA\n"),output=target();
        var hits=List.of(hit("q",0,2,10,"wanted","SINE/Alu",false),hit("q",3,5,10,"other","SINE/Alu",false),hit("q",6,8,10,"wanted","LINE/L1",false),hit("q",8,10,10,"wanted","SINE/Alu",true));
        var selection=new RepeatMaskAudit.Selection(RepeatMaskAudit.Choice.EXACT,Set.of("SINE"),RepeatMaskAudit.Choice.EXACT,Set.of("wanted"),RepeatMaskAudit.MarkedOverlap.EXCLUDE);
        var report=RepeatMaskAudit.write(input,RepeatFasta.Encoding.PLAIN_UTF8,output,hits,selection,RepeatAnnotations.Mask.N,RepeatFastaTest.limits(),1048576,10000,()->{});
        assertEquals(">q\nNNAAAAAAAA\n",Files.readString(output));assertEquals(1,report.selectedRows());assertEquals(2,report.coveredBases());
    }
    @Test void zeroRowsPreserveUnicodeHeadersCaseAndEveryQueryWithoutClaimingSearchCompletion() throws Exception {
        Path input=file(">β.seq\t日本 original\nAcgTNn\n>zero\nTT\n");
        for(var mode:RepeatAnnotations.Mask.values()) {
            Path output=target();var report=write(input,output,List.of(),mode);
            assertEquals(Files.readString(input),Files.readString(output));assertEquals(0,report.changedCharacters());assertEquals(0,report.coveredBases());assertEquals(2,report.sequences().size());
        }
    }
    @Test void independentBooleanOracleMatchesWrittenRandomMasksAcrossModes() throws Exception {
        var random=new Random(520187);
        for(int population=0;population<25;population++) {
            var source=new StringBuilder();for(int i=0;i<301;i++)source.append("AaCcGgTtNnXx".charAt(random.nextInt(12)));
            var hits=new ArrayList<RepeatAnnotations.Hit>();boolean[] covered=new boolean[301];
            for(int row=0;row<20;row++){int start=random.nextInt(301),end=start+1+random.nextInt(301-start);hits.add(hit("q",start,end,301,"R"+row,"DNA/hAT",false));for(int i=start;i<end;i++)covered[i]=true;}
            Path input=file(">q independent oracle\n"+source+"\n");
            for(var mode:RepeatAnnotations.Mask.values()) {
                var expected=new StringBuilder(source);long count=0,changed=0;
                for(int i=0;i<covered.length;i++)if(covered[i]){count++;char value=mode==RepeatAnnotations.Mask.N?'N':mode==RepeatAnnotations.Mask.X?'X':Character.toLowerCase(source.charAt(i));if(value!=source.charAt(i))changed++;expected.setCharAt(i,value);}
                Path output=target();var report=write(input,output,hits,mode);String actual=Files.readString(output).substring(">q independent oracle\n".length()).replace("\n","");
                assertEquals(expected.toString(),actual);assertEquals(count,report.coveredBases());assertEquals(changed,report.changedCharacters());
            }
        }
    }
    @Test void gzipInputUsesPhysicalEofAndOutputBudgetCountsActualUtf8Bytes() throws Exception {
        String text=">β full header\n"+"AcgTNn".repeat(23)+"\n";Path input=target();var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes)){gzip.write(text.getBytes(StandardCharsets.UTF_8));}Files.write(input,bytes.toByteArray());
        Path output=target();var report=RepeatMaskAudit.write(input,RepeatFasta.Encoding.GZIP_UTF8,output,List.of(),RepeatMaskAudit.Selection.all(),RepeatAnnotations.Mask.X,RepeatFastaTest.limits(),1048576,10000,()->{});
        assertEquals(138,report.comparedBases());assertEquals(60,Files.readAllLines(output).get(1).length());
        Path exact=target();RepeatMaskAudit.write(input,RepeatFasta.Encoding.GZIP_UTF8,exact,List.of(),RepeatMaskAudit.Selection.all(),RepeatAnnotations.Mask.X,RepeatFastaTest.limits(),1048576,Files.size(output),()->{});
        assertArrayEquals(Files.readAllBytes(output),Files.readAllBytes(exact));Path shortOutput=target();assertThrows(IOException.class,()->RepeatMaskAudit.write(input,RepeatFasta.Encoding.GZIP_UTF8,shortOutput,List.of(),RepeatMaskAudit.Selection.all(),RepeatAnnotations.Mask.X,RepeatFastaTest.limits(),1048576,Files.size(output)-1,()->{}));assertTrue(Files.size(shortOutput)<=Files.size(output)-1);
        byte[] corrupt=bytes.toByteArray();corrupt[corrupt.length-8]^=1;Files.write(input,corrupt);Path refused=target();assertThrows(IOException.class,()->RepeatMaskAudit.write(input,RepeatFasta.Encoding.GZIP_UTF8,refused,List.of(),RepeatMaskAudit.Selection.all(),RepeatAnnotations.Mask.N,RepeatFastaTest.limits(),1048576,10000,()->{}));assertFalse(Files.exists(refused));
    }
    @Test void filteredBadCoordinatesAndInsufficientIndexFailBeforeOutputCreation() throws Exception {
        Path input=file(">q\nACGT\n");var none=new RepeatMaskAudit.Selection(RepeatMaskAudit.Choice.EXACT,Set.of("absent"),RepeatMaskAudit.Choice.ALL,Set.of(),RepeatMaskAudit.MarkedOverlap.INCLUDE);
        for(var bad:List.of(hit("Q",0,2,4,"R","DNA/hAT",false),hit("q",0,5,5,"R","DNA/hAT",false),hit("q",0,2,5,"R","DNA/hAT",false))) {
            Path output=target();assertThrows(IOException.class,()->RepeatMaskAudit.write(input,RepeatFasta.Encoding.PLAIN_UTF8,output,List.of(bad),none,RepeatAnnotations.Mask.N,RepeatFastaTest.limits(),1048576,10000,()->{}));assertFalse(Files.exists(output));
        }
        Path output=target();assertThrows(IOException.class,()->RepeatMaskAudit.write(input,RepeatFasta.Encoding.PLAIN_UTF8,output,List.of(),none,RepeatAnnotations.Mask.N,RepeatFastaTest.limits(),65536,10000,()->{}));assertFalse(Files.exists(output));
    }
    @Test void preexistingOutputsAndInputItselfAreNeverOverwritten() throws Exception {
        Path input=file(">q\nACGT\n"),output=file("existing evidence");
        assertThrows(IOException.class,()->write(input,output,List.of(),RepeatAnnotations.Mask.N));assertEquals("existing evidence",Files.readString(output));
        assertThrows(IOException.class,()->write(input,input,List.of(),RepeatAnnotations.Mask.N));assertEquals(">q\nACGT\n",Files.readString(input));
    }
    @Test void cancellationAndCoveredBaseMutationLeavePartialFilesWithoutReturningAReport() throws Exception {
        for(boolean mutate:List.of(false,true)) {
            Path input=file(">q\nACGT\n"),output=target();
            Runnable check=()->{if(Files.exists(output)){if(!mutate)throw new CancellationException("controlled stop");try{Files.writeString(input,">q\nCCGT\n");}catch(IOException error){throw new UncheckedIOException(error);}}};
            if(mutate)assertThrows(IOException.class,()->RepeatMaskAudit.write(input,RepeatFasta.Encoding.PLAIN_UTF8,output,List.of(hit("q",0,4,4,"R","DNA/hAT",false)),RepeatMaskAudit.Selection.all(),RepeatAnnotations.Mask.N,RepeatFastaTest.limits(),1048576,10000,check));
            else assertThrows(CancellationException.class,()->RepeatMaskAudit.write(input,RepeatFasta.Encoding.PLAIN_UTF8,output,List.of(),RepeatMaskAudit.Selection.all(),RepeatAnnotations.Mask.N,RepeatFastaTest.limits(),1048576,10000,check));
            assertTrue(Files.exists(output));
        }
    }
}
