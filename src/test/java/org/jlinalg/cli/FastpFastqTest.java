/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FastpFastqTest {
    @TempDir Path temporary;
    private Path data(String name,byte[] raw)throws IOException {Path p=temporary.resolve(name);Files.write(p,raw);return p;}
    private Path text(String raw)throws IOException{return data("reads.fq",raw.getBytes(StandardCharsets.UTF_8));}
    private FastpFastq.Reader open(Path path)throws IOException{return new FastpFastq.Reader(path,new FastpFastq.Limits(1_048_576,1_048_576,100,10000,1000,100),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.MATCH_FIRST_TOKEN_OR_EMPTY,false,()->{});}
    private static byte[] gzip(byte[] raw)throws IOException {var out=new ByteArrayOutputStream();try(var z=new GZIPOutputStream(out)){z.write(raw);}return out.toByteArray();}
    @Test void completeUTF8HeaderQualityAndCRLFCounts()throws Exception {
        Path path=text("@read-α comment\r\nACGTN\r\n+read-α\r\n!5?I~\r\n");
        try(var input=open(path)){
            var read=input.next();assertEquals("read-α",read.token());assertEquals("@read-α comment",read.header());assertNull(input.next());
            var s=input.completeStats();assertEquals(1,s.reads());assertEquals(5,s.bases());assertEquals(4,s.q20Bases());assertEquals(3,s.q30Bases());
            assertEquals(183,s.totalPhred());assertEquals(1,s.uppercaseNs());assertEquals(2,s.gcBases());assertEquals(Files.size(path),s.encodedBytes());assertFalse(s.gzip());
        }
    }
    @Test void prefixStatisticsCannotBeAdmitted()throws Exception {try(var input=open(text("@a\nA\n+\nI\n"))){assertNotNull(input.next());assertThrows(IOException.class,input::completeStats);}}
    @Test void validatesFinalReadInsteadOfTruncatingAtPrefix()throws Exception {try(var input=open(text("@a\nA\n+\nI\n@b\nAA\n+\nI\n"))){assertNotNull(input.next());assertThrows(IOException.class,input::next);}}
    @Test void concatenatedGzipMembersAndMagicRatherThanSuffix()throws Exception {
        var out=new ByteArrayOutputStream();out.write(gzip("@a\nA\n+\nI\n".getBytes(StandardCharsets.US_ASCII)));out.write(gzip("@b\nC\n+\n5\n".getBytes(StandardCharsets.US_ASCII)));
        try(var input=open(data("unusual.original",out.toByteArray()))){assertEquals("a",input.next().token());assertEquals("b",input.next().token());assertNull(input.next());assertTrue(input.completeStats().gzip());assertEquals(2,input.completeStats().reads());}
    }
    @ParameterizedTest @ValueSource(strings={"trailing","truncated","crc","size","reserved"})
    void completeGzipIntegrityIsRequired(String issue)throws Exception {
        byte[] raw=gzip("@a\nA\n+\nI\n".getBytes(StandardCharsets.US_ASCII));
        switch(issue){
            case "trailing" -> {raw=java.util.Arrays.copyOf(raw,raw.length+1);raw[raw.length-1]=1;}
            case "truncated" -> raw=java.util.Arrays.copyOf(raw,raw.length-1);
            case "crc" -> raw[raw.length-8]^=1;
            case "size" -> raw[raw.length-4]^=1;
            case "reserved" -> raw[3]|=32;
            default -> throw new AssertionError();
        }
        Path path=data("broken.gz",raw);assertThrows(IOException.class,()->{try(var input=open(path)){while(input.next()!=null){}}});
    }
    @ParameterizedTest @ValueSource(strings={"read","bases","line","decoded","encoded"})
    void everyCompleteInputCapacityIsEnforced(String which)throws Exception {
        Path path=text("@a\nACGT\n+\nIIII\n@b\nACGT\n+\nIIII\n");var l=new FastpFastq.Limits(which.equals("encoded")?5:1000,which.equals("decoded")?5:1000,which.equals("read")?1:100,which.equals("bases")?5:100,which.equals("line")?3:100,100);
        assertThrows(IOException.class,()->{try(var input=new FastpFastq.Reader(path,l,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,false,()->{})){while(input.next()!=null){}}});
    }
    @ParameterizedTest @ValueSource(strings={"empty","wrapped","bare-cr","bad-quality","bad-sequence","bad-plus","blank-header","control-header","truncated"})
    void explicitStrictInputProfileRejectsMalformedOrOutOfProfileData(String name)throws Exception {
        String raw=switch(name){case "empty"->"@a\n\n+\n\n";case "wrapped"->"@a\nAC\nGT\n+\nIIII\n";case "bare-cr"->"@a\rA\n+\nI\n";case "bad-quality"->"@a\nA\n+\n \n";case "bad-sequence"->"@a\na\n+\nI\n";case "bad-plus"->"@a\nA\nnotplus\nI\n";case "blank-header"->"@ a\nA\n+\nI\n";case "control-header"->"@a\u0001\nA\n+\nI\n";case "truncated"->"@a\nA\n+\n";default->throw new AssertionError();};
        Path path=text(raw);assertThrows(IOException.class,()->{try(var input=open(path)){while(input.next()!=null){}}});
    }
    @Test void malformedUTF8IsNotReplaced()throws Exception {Path path=data("utf8.fq",new byte[]{'@',(byte)0xff,'\n','A','\n','+','\n','I','\n'});assertThrows(IOException.class,()->{try(var input=open(path)){input.next();}});}
    @Test void sourceIgnoredPlusPolicyRetainsExplicitMismatchCounts()throws Exception {
        Path path=text("@a\nA\n+other\nI\n");assertThrows(IOException.class,()->{try(var input=open(path)){input.next();}});
        try(var input=new FastpFastq.Reader(path,FastpFastq.Limits.defaults(),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.SOURCE_IGNORED,false,()->{})){assertNotNull(input.next());assertNull(input.next());assertEquals(1,input.completeStats().mismatchedPlus());}
    }
    @Test void explicitPhred64AndLiteralAlphabetAreCountedWithoutSilentNormalization()throws Exception {
        Path path=text("@a\nAnC\n+\n@T~\n");
        try(var input=new FastpFastq.Reader(path,FastpFastq.Limits.defaults(),FastpFastq.Encoding.PHRED64,FastpFastq.Alphabet.LITERAL_ASCII,FastpFastq.Plus.EMPTY_ONLY,false,()->{})){
            assertEquals("AnC",input.next().sequence());assertNull(input.next());var s=input.completeStats();assertEquals(82,s.totalPhred());assertEquals(2,s.q20Bases());assertEquals(1,s.q30Bases());assertEquals(0,s.uppercaseNs());assertEquals(1,s.noncanonicalBases());
        }
    }
    @Test void unterminatedFinalQualityIsExplicitAndComplete()throws Exception {try(var input=open(text("@a\nA\n+\nI"))){assertNotNull(input.next());assertNull(input.next());assertTrue(input.completeStats().unterminatedFinalLine());}}
    @Test void cancellationIsNotACompletedPrefix()throws Exception {Path path=text("@a\nA\n+\nI\n");int[] calls={0};assertThrows(IllegalStateException.class,()->{try(var input=new FastpFastq.Reader(path,FastpFastq.Limits.defaults(),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,false,()->{if(++calls[0]>2)throw new IllegalStateException("cancel");})){input.next();}});}
    @Test void pairSuffixPolicyRejectsReversalAndMissingMates()throws Exception {
        Path a=data("a.fq","@x/1\nA\n+\nI\n".getBytes(StandardCharsets.US_ASCII)),b=data("b.fq","@x/2\nT\n+\nI\n".getBytes(StandardCharsets.US_ASCII));
        var result=FastpFastq.inspect(a,b,FastpFastq.Limits.defaults(),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.TERMINAL_MATE_SUFFIX,false,()->{});assertTrue(result.mateIdentityVerified());
        assertThrows(IOException.class,()->FastpFastq.inspect(b,a,FastpFastq.Limits.defaults(),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.TERMINAL_MATE_SUFFIX,false,()->{}));
    }
    @Test void ordinalOnlyPairingIsExplicitlyUnverified()throws Exception {
        Path a=data("a.fq","@different-a\nA\n+\nI\n".getBytes(StandardCharsets.US_ASCII)),b=data("b.fq","@different-b\nT\n+\nI\n".getBytes(StandardCharsets.US_ASCII));
        var r=FastpFastq.inspect(a,b,FastpFastq.Limits.defaults(),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.ORDINAL_ONLY_UNVERIFIED,false,()->{});assertFalse(r.mateIdentityVerified());
        assertThrows(IOException.class,()->FastpFastq.inspect(a,b,FastpFastq.Limits.defaults(),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.EXACT_FIRST_TOKEN,false,()->{}));
    }
    @Test void casavaPolicyChecksReadLabelsInsteadOfInferringThemFromOrder()throws Exception {
        var a=new FastpFastq.Read(1,"@x 1:N:0:ABC","A","I","+");var b=new FastpFastq.Read(1,"@x 2:N:0:ABC","T","I","+");
        FastpFastq.checkPair(a,b,FastpFastq.Pairing.CASAVA_FIRST_TOKEN);
        assertThrows(IOException.class,()->FastpFastq.checkPair(a,a,FastpFastq.Pairing.CASAVA_FIRST_TOKEN));
        assertThrows(IOException.class,()->FastpFastq.checkPair(new FastpFastq.Read(1,"@x","A","I","+"),b,FastpFastq.Pairing.CASAVA_FIRST_TOKEN));
    }
}
