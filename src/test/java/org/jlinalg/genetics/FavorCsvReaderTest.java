/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.FavorCsvReader.BlankRecords;
import org.jlinalg.genetics.FavorCsvReader.Limits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FavorCsvReaderTest {
    @TempDir Path directory;
    private Path file(String name,String text)throws IOException {Path path=directory.resolve(name);Files.writeString(path,text,StandardCharsets.UTF_8);return path;}
    private FavorCsvReader open(Path path,Limits limits,BlankRecords blanks)throws IOException{return FavorCsvReader.open(path,limits,blanks,()->{});}
    private FavorCsvReader open(String text)throws IOException{return open(file("input.csv",text),Limits.defaults(),BlankRecords.ERROR);}
    private static List<List<String>> all(FavorCsvReader reader)throws IOException {var rows=new ArrayList<List<String>>();List<String> row;while((row=reader.next())!=null)rows.add(row);return rows;}
    private void bad(String name,String text)throws Exception {Path p=file(name,text);assertThrows(IOException.class,()->{try(var reader=open(p,Limits.defaults(),BlankRecords.ERROR)){all(reader);}});Files.delete(p);}

    @Test void exactHeaderRowsCountersAndEofAreReported()throws Exception {
        Path p=file("counts.csv","key,value\na,1\nb,2\n");try(var r=open(p,Limits.defaults(),BlankRecords.ERROR)){assertEquals(List.of("key","value"),r.header());assertEquals(List.of(List.of("a","1"),List.of("b","2")),all(r));assertEquals(new FavorCsvReader.Report(Files.size(p),2,6,0,false,true),r.report());assertNull(r.next());}
    }
    @Test void quotedCommasEscapedQuotesAndUnicodeAreExactCells()throws Exception {
        try(var r=open("key,value\nα,\"Gene,β\"\nγ,\"a\"\"b\"\n")){assertEquals(List.of(List.of("α","Gene,β"),List.of("γ","a\"b")),all(r));}
    }
    @Test void quotedMultilineFieldsRetainTheirOriginalLineEndingBytes()throws Exception {
        try(var r=open("key,value\r\na,\"x\r\ny\nz\rw\"\r\n")){assertEquals(List.of("a","x\r\ny\nz\rw"),r.next());assertNull(r.next());}
    }
    @Test void lfCrlfAndBareCrTerminateRecordsWithoutInventingRows()throws Exception {
        for(String newline:List.of("\n","\r\n","\r")){Path p=file("newline-"+newline.length()+Math.abs(newline.hashCode())+".csv","a,b"+newline+"x,y"+newline);try(var r=open(p,Limits.defaults(),BlankRecords.ERROR)){assertEquals(List.of(List.of("x","y")),all(r));assertEquals(Files.size(p),r.report().bytes());}}
    }
    @Test void finalRecordWithoutNewlineAndTrailingEmptyCellsAreRetained()throws Exception {
        try(var r=open("a,b,c\nx,y,")){assertEquals(List.of("x","y",""),r.next());assertNull(r.next());assertEquals(1,r.report().rows());}
    }
    @Test void literalNaWhitespaceAndEmptyQuotedCellsAreNotInferredOrTrimmed()throws Exception {
        try(var r=open("a,b,c,d\nNA,  value  ,\"\",\" NA \"\n")){assertEquals(List.of("NA","  value  ",""," NA "),r.next());assertNull(r.next());}
    }
    @Test void utf8BomIsExplicitlyRecordedAndRemovedOnlyAtFileStart()throws Exception {
        try(var r=open("\ufeffa,b\nx,\ufeffy\n")){assertEquals(List.of("a","b"),r.header());assertEquals(List.of("x","\ufeffy"),r.next());assertNull(r.next());assertTrue(r.report().utf8Bom());}
    }
    @Test void oneColumnHeaderAndQuotedEmptyDataAreValid()throws Exception {
        try(var r=open("key\n\"\"\n")){assertEquals(List.of(""),r.next());assertNull(r.next());assertEquals(2,r.report().cells());}
    }
    @Test void headerOnlyFilesHaveACompleteZeroRowPopulationAfterEof()throws Exception {
        try(var r=open(file("header.csv","a,b\n"),new Limits(4,0,2,2,1,4),BlankRecords.ERROR)){assertFalse(r.report().complete());assertNull(r.next());assertTrue(r.report().complete());assertEquals(0,r.report().rows());}
    }
    @Test void blankRecordsRequireAnExplicitPolicy()throws Exception {bad("blank.csv","a,b\nx,y\n\n");}
    @Test void ignorePolicyCountsSkippedBlankRecordsIncludingBeforeTheHeader()throws Exception {
        Path p=file("blank-ignore.csv","\n\r\na,b\n\nx,y\r\n\r\n");try(var r=open(p,Limits.defaults(),BlankRecords.IGNORE)){assertEquals(List.of(List.of("x","y")),all(r));assertEquals(4,r.report().skippedBlankRecords());assertEquals(4,r.report().cells());assertEquals(Files.size(p),r.report().bytes());}
    }
    @Test void ignoredTrailingBlanksDoNotConsumeAnExactCellBudget()throws Exception {
        try(var r=open(file("exact-cells.csv","a,b\nx,y\n\n"),new Limits(100,1,4,2,10,20),BlankRecords.IGNORE)){assertEquals(1,all(r).size());assertTrue(r.report().complete());assertEquals(1,r.report().skippedBlankRecords());}
    }
    @Test void spacesAndEmptyCellsSeparatedByCommasAreNotBlankRecords()throws Exception {
        try(var r=open(file("not-blank.csv","a,b\n,\n , \n"),Limits.defaults(),BlankRecords.IGNORE)){assertEquals(List.of(List.of("",""),List.of(" "," ")),all(r));assertEquals(0,r.report().skippedBlankRecords());}
    }
    @Test void unexpectedDataWidthFailsAndCannotBeResumed()throws Exception {
        try(var r=open("a,b\nx,y,z\nu,v\n")){assertTrue(assertThrows(IOException.class,r::next).getMessage().contains("width"));assertFalse(r.report().complete());assertTrue(assertThrows(IOException.class,r::next).getMessage().contains("cannot be resumed"));}
    }
    @Test void unterminatedQuotesAndBareUnquotedQuotesAreRefused()throws Exception {bad("unterminated.csv","a,b\nx,\"oops\n");bad("bare.csv","a,b\nx,foo\"bar\n");}
    @Test void junkOrSpacesAfterAClosingQuoteAreNotSilentlyRepaired()throws Exception {bad("junk.csv","a,b\nx,\"y\"z\n");bad("space.csv","a,b\nx,\"y\" ,\n");}
    @Test void duplicateBlankAndControlHeaderNamesAreRejected()throws Exception {bad("duplicate.csv","a,a\nx,y\n");bad("empty-header.csv","a,\nx,y\n");bad("control-header.csv","a,\"b\nc\"\nx,y\n");}
    @Test void headerWhitespaceIsRetainedRatherThanNormalized()throws Exception {try(var r=open(" key ,value\nx,y\n")){assertEquals(" key ",r.header().get(0));assertEquals(List.of("x","y"),r.next());}}
    @Test void invalidUtf8IsRejectedInsideQuotedAndUnquotedFields()throws Exception {
        for(boolean quoted:List.of(false,true)){Path p=directory.resolve("utf8-"+quoted+".csv");var prefix=("a,b\nx,"+(quoted?"\"":"")).getBytes(StandardCharsets.UTF_8);var bytes=new byte[prefix.length+2+(quoted?2:1)];System.arraycopy(prefix,0,bytes,0,prefix.length);bytes[prefix.length]=(byte)0xc3;bytes[prefix.length+1]=0x28;if(quoted)bytes[prefix.length+2]='"';bytes[bytes.length-1]='\n';Files.write(p,bytes);assertThrows(IOException.class,()->{try(var r=open(p,Limits.defaults(),BlankRecords.ERROR)){all(r);}});}
    }
    @Test void nulBytesAreNeverAcceptedAsAnnotationText()throws Exception {bad("nul.csv","a,b\nx,\"y\u0000z\"\n");}
    @Test void exactFileRowCellFieldColumnAndRecordBoundsAreAccepted()throws Exception {
        Path p=file("limits.csv","a,b\nx,y\n");try(var r=open(p,new Limits(8,1,4,2,1,4),BlankRecords.ERROR)){assertEquals(1,all(r).size());assertEquals(8,r.report().bytes());}
    }
    @Test void oversizedStaticInputsAreRefusedBeforeReading()throws Exception {
        Path p=file("large.csv","a,b\nx,y\n");assertTrue(assertThrows(IOException.class,()->open(p,new Limits(7,10,100,10,100,100),BlankRecords.ERROR)).getMessage().contains("oversized"));
    }
    @Test void appendedInputBytesCannotEvadeTheReadTimeByteBudget()throws Exception {
        Path p=file("growing.csv","a\n");try(var r=open(p,new Limits(2,10,100,2,10,10),BlankRecords.ERROR)){Files.writeString(p,"b\n",StandardOpenOption.APPEND);assertTrue(assertThrows(IOException.class,r::next).getMessage().contains("byte capacity"));assertFalse(r.report().complete());}
    }
    @Test void rowCellAndColumnBoundsRejectTheCompletePopulation()throws Exception {
        Path p=file("row-bounds.csv","a,b\nx,y\n");for(Limits limit:List.of(new Limits(100,0,100,10,10,100),new Limits(100,10,3,10,10,100),new Limits(100,10,100,1,10,100)))assertThrows(IOException.class,()->{try(var r=open(p,limit,BlankRecords.ERROR)){all(r);}});
    }
    @Test void fieldLimitsCountDecodedUtf8BytesRatherThanJavaCharacters()throws Exception {
        Path p=file("field-bytes.csv","a\nα\n");try(var r=open(p,new Limits(100,1,2,1,2,10),BlankRecords.ERROR)){assertEquals(List.of("α"),r.next());assertNull(r.next());}assertThrows(IOException.class,()->{try(var r=open(p,new Limits(100,1,2,1,1,10),BlankRecords.ERROR)){all(r);}});
    }
    @Test void escapedQuotesCountOnceInFieldBytesAndTwiceInRecordBytes()throws Exception {
        Path p=file("escaped-limit.csv","a\n\"a\"\"b\"\n");try(var r=open(p,new Limits(100,1,2,1,3,7),BlankRecords.ERROR)){assertEquals(List.of("a\"b"),r.next());assertNull(r.next());}assertThrows(IOException.class,()->{try(var r=open(p,new Limits(100,1,2,1,3,6),BlankRecords.ERROR)){all(r);}});
    }
    @Test void headerConstructionFailuresCloseTheOwnedFile()throws Exception {
        Path p=file("invalid-header.csv","a,a\n");assertThrows(IOException.class,()->open(p,Limits.defaults(),BlankRecords.ERROR));Files.delete(p);assertFalse(Files.exists(p));
    }
    @Test void earlyCloseNeverCertifiesACompleteScan()throws Exception {
        var r=open("a\nx\ny\n");assertEquals(List.of("x"),r.next());r.close();assertFalse(r.report().complete());assertThrows(IOException.class,r::next);r.close();
    }
    @Test void headersAndRowsAreImmutableSnapshots()throws Exception {
        try(var r=open("a,b\nx,y\n")){assertThrows(UnsupportedOperationException.class,()->r.header().clear());var row=r.next();assertThrows(UnsupportedOperationException.class,row::clear);assertEquals(List.of("x","y"),row);}
    }
    @Test void cancellationDuringALongFieldInvalidatesTheReader()throws Exception {
        Path p=file("cancel.csv","a\n"+"x".repeat(20000)+"\n");var calls=new AtomicInteger();try(var r=FavorCsvReader.open(p,Limits.defaults(),BlankRecords.ERROR,()->{if(calls.incrementAndGet()==5)throw new IllegalStateException("cancelled");})){assertThrows(IllegalStateException.class,r::next);assertFalse(r.report().complete());assertThrows(IOException.class,r::next);}
    }
    @Test void absentDirectoryAndEmptyInputsAreRefused()throws Exception {
        assertThrows(IOException.class,()->open(directory,Limits.defaults(),BlankRecords.ERROR));assertThrows(IOException.class,()->open(directory.resolve("absent.csv"),Limits.defaults(),BlankRecords.ERROR));bad("empty.csv","");bad("bom-only.csv","\ufeff");
    }
    @Test void invalidCapacitiesRejectBeforeFileAccess()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new Limits(0,1,1,1,1,1));assertThrows(IllegalArgumentException.class,()->new Limits(1,-1,1,1,1,1));assertThrows(IllegalArgumentException.class,()->new Limits(1,1,0,1,1,1));assertThrows(IllegalArgumentException.class,()->new Limits(1,1,1,1025,1,1));
    }
}
