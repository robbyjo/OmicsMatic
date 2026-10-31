/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatRawSearchTest {
    @TempDir Path directory;
    private static String current(){return "## RepeatMasker version 4.2.6 , sensitive mode\n## run with rmblastn version 2.17.1+\n## RM Library: \n## Total Sequences: 1\n## Total Length: 4\n## RAW Annotations:\n";}
    private RepeatRawSearch.Report read(String text,String version,boolean gzip)throws Exception{
        Path path=directory.resolve("raw"+(gzip?".gz":".cat"));byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(gzip)bytes=gzip(bytes);Files.write(path,bytes);return RepeatRawSearch.read(path,gzip,version,RepeatRecipe.Speed.SENSITIVE,1,4,1048576,4096,()->{});
    }
    private static byte[] gzip(byte[] data)throws IOException{var bytes=new ByteArrayOutputStream();try(var output=new GZIPOutputStream(bytes)){output.write(data);}return bytes.toByteArray();}
    @Test void completeCurrentHeadersPreserveAnEmptyCustomLibraryLabel()throws Exception{
        var report=read(current(),"4.2.6",false);assertEquals(1,report.queries());assertEquals(4,report.bases());assertEquals("",report.libraryLabel());assertEquals(current().getBytes(StandardCharsets.UTF_8).length,report.decodedBytes());assertFalse(report.gzip());
        assertEquals("  Dfam label  ",read(current().replace("RM Library: \n","RM Library:   Dfam label  \n"),"4.2.6",false).libraryLabel());
    }
    @Test void historicalMetadataAtPhysicalEofIsReadAfterEveryRawAlignmentLine()throws Exception{
        String text="raw alignment and statistics\n\n## Total Sequences: 1\n## Total Length: 4\nRepeatMasker version 4.1.5 , sensitive mode\nrun with rmblastn version 2.17.1+\nRM Library: Dfam 3.7\n";
        var report=read(text,"4.1.5",false);assertEquals("Dfam 3.7",report.libraryLabel());assertEquals(7,report.lines());
    }
    @Test void duplicateMissingMalformedAndUnterminatedMetadataNeverAdmitsANoHitResult()throws Exception{
        for(String text:List.of("",current().replace("## Total Sequences: 1\n",""),current()+"## Total Sequences: 1\n",current().replace("Total Length: 4","Total Length: invalid"),current().substring(0,current().length()-1))){assertThrows(IOException.class,()->read(text,"4.2.6",false));}
    }
    @Test void wrongEditionEngineModeOrProtectedPopulationIsRefused()throws Exception{
        for(String text:List.of(current().replace("4.2.6","4.1.5"),current().replace("2.17.1+","2.16.0"),current().replace("sensitive","default"),current().replace("Sequences: 1","Sequences: 2"),current().replace("Length: 4","Length: 3"))){assertThrows(IOException.class,()->read(text,"4.2.6",false));}
        for(var speed:RepeatRecipe.Speed.values()){
            String label=switch(speed){case DEFAULT->"default";case SENSITIVE->"sensitive";case QUICK->"quick";case RUSH->"rushjob";};Path path=directory.resolve("raw-"+label+".cat");Files.writeString(path,current().replace("sensitive",label));assertEquals(label,RepeatRawSearch.read(path,false,"4.2.6",speed,1,4,1048576,4096,()->{}).speed());
        }
    }
    @Test void concatenatedGzipMembersRequireAllTrailersAndPhysicalEof()throws Exception{
        byte[] a=gzip(current().getBytes(StandardCharsets.UTF_8)),b=gzip("raw alignment tail\n".getBytes(StandardCharsets.UTF_8));byte[] joined=new byte[a.length+b.length];System.arraycopy(a,0,joined,0,a.length);System.arraycopy(b,0,joined,a.length,b.length);Path path=directory.resolve("raw.gz");Files.write(path,joined);
        assertTrue(RepeatRawSearch.read(path,true,"4.2.6",RepeatRecipe.Speed.SENSITIVE,1,4,1048576,4096,()->{}).gzip());
        byte[] crc=joined.clone();crc[crc.length-8]^=1;byte[] garbage=java.util.Arrays.copyOf(joined,joined.length+1);
        for(byte[] invalid:List.of(crc,garbage,java.util.Arrays.copyOf(joined,joined.length-1))){Files.write(path,invalid);assertThrows(IOException.class,()->RepeatRawSearch.read(path,true,"4.2.6",RepeatRecipe.Speed.SENSITIVE,1,4,1048576,4096,()->{}));}
    }
    @Test void wholeDecodedStreamAndRetainedMetadataHaveIndependentCapacities()throws Exception{
        Path path=directory.resolve("raw.gz");Files.write(path,gzip(current().replace("RM Library: \n","RM Library: "+"A".repeat(2000)+"\n").getBytes(StandardCharsets.UTF_8)));
        assertTrue(Files.size(path)<512);assertThrows(IOException.class,()->RepeatRawSearch.read(path,true,"4.2.6",RepeatRecipe.Speed.SENSITIVE,1,4,512,4096,()->{}));
        assertThrows(IOException.class,()->RepeatRawSearch.read(path,true,"4.2.6",RepeatRecipe.Speed.SENSITIVE,1,4,1048576,1024,()->{}));
    }
    @Test void longUnneededBatchBoundaryLinesAreStreamedWithoutUsingTheMetadataRetentionBudget()throws Exception{
        String boundaries="## LONG_N "+"12345,".repeat(40000)+"\n";
        for(String text:List.of(current()+boundaries,boundaries+current().replace("4.2.6","4.1.5"))){
            Path path=directory.resolve("large-boundaries.cat");Files.writeString(path,text);String version=text.indexOf("4.1.5")>=0?"4.1.5":"4.2.6";
            var report=RepeatRawSearch.read(path,false,version,RepeatRecipe.Speed.SENSITIVE,1,4,1048576,64,()->{});assertEquals(text.getBytes(StandardCharsets.UTF_8).length,report.decodedBytes());
        }
    }
    @Test void malformedUtf8AndControlCharactersAreRefusedThroughCompleteEof()throws Exception{
        Path path=directory.resolve("raw.cat");byte[] valid=current().getBytes(StandardCharsets.UTF_8),invalid=java.util.Arrays.copyOf(valid,valid.length+2);invalid[valid.length]=(byte)0xff;invalid[valid.length+1]='\n';Files.write(path,invalid);
        assertThrows(IOException.class,()->RepeatRawSearch.read(path,false,"4.2.6",RepeatRecipe.Speed.SENSITIVE,1,4,1048576,4096,()->{}));assertThrows(IOException.class,()->read(current()+"bad\u0000data\n","4.2.6",false));
    }
    @Test void cancellationAndUnsupportedRequestsCannotProduceACensus()throws Exception{
        Path path=directory.resolve("raw.cat");Files.writeString(path,current());assertThrows(CancellationException.class,()->RepeatRawSearch.read(path,false,"4.2.6",RepeatRecipe.Speed.SENSITIVE,1,4,1048576,4096,()->{throw new CancellationException("controlled stop");}));
        assertThrows(IOException.class,()->RepeatRawSearch.read(path,false,"4.0.9",RepeatRecipe.Speed.SENSITIVE,1,4,1048576,4096,()->{}));assertThrows(IOException.class,()->RepeatRawSearch.read(path,false,"4.2.6",RepeatRecipe.Speed.SENSITIVE,0,4,1048576,4096,()->{}));
    }
}
