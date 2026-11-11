/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import htsjdk.samtools.BAMRecordCodec;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StringTieBamTest {
    @TempDir Path temporary;
    private static final String HEADER="@HD\tVN:1.6\tSO:coordinate\n@SQ\tSN:chrA\tLN:12000\n@SQ\tSN:chrB\tLN:10000\n";
    private static final Map<String,Long> REFERENCE=Map.of("chrA",12000L,"chrB",10000L);
    private static final StringTieBam.Limits LIMITS=new StringTieBam.Limits(10000000,20000000,100000,1000000,1000000,100,10000,1000,100,10000);
    private static final StringTieBam.Policies STRICT=new StringTieBam.Policies(StringTieBam.MissingNh.ERROR,StringTieBam.MissingXs.ERROR,StringTieBam.SortHeader.REQUIRE_COORDINATE);
    private static String read(String name,int flags,String ref,int start,String cigar,String tags){return name+"\t"+flags+"\t"+ref+"\t"+start+"\t60\t"+cigar+"\t*\t0\t0\tACGT\tIIII"+(tags.isEmpty()?"":"\t"+tags)+"\n";}
    private static String mapped(String name,int start){return read(name,0,"chrA",start,"4M","NH:i:1\tXS:A:+");}
    private static int integer(byte[] b,int i){return b[i]&255|(b[i+1]&255)<<8|(b[i+2]&255)<<16|b[i+3]<<24;}
    private static void integer(byte[] b,int i,int value){for(int n=0;n<4;n++)b[i+n]=(byte)(value>>>(8*n));}
    private static void integer(ByteArrayOutputStream out,int value){for(int n=0;n<4;n++)out.write((byte)(value>>>(8*n)));}
    private Path bam(String header,String records)throws Exception{
        Path sam=Files.createTempFile(temporary,"source-",".sam").toAbsolutePath();Files.writeString(sam,header+records,StandardCharsets.UTF_8);
        var decoded=new ByteArrayOutputStream();decoded.write(new byte[]{'B','A','M',1});byte[] text=header.getBytes(StandardCharsets.UTF_8);integer(decoded,text.length);decoded.write(text);
        try(var source=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(sam)){
            var refs=source.getFileHeader().getSequenceDictionary().getSequences();integer(decoded,refs.size());for(var r:refs){byte[] name=r.getSequenceName().getBytes(StandardCharsets.UTF_8);integer(decoded,name.length+1);decoded.write(name);decoded.write(0);integer(decoded,r.getSequenceLength());}
            var codec=new BAMRecordCodec(source.getFileHeader());codec.setOutputStream(decoded);try(var rows=source.iterator()){while(rows.hasNext())codec.encode(rows.next());}}
        return compressed(decoded.toByteArray());
    }
    private Path compressed(byte[] decoded)throws IOException{Path file=Files.createTempFile(temporary,"control-",".bam").toAbsolutePath();try(var out=new BlockCompressedOutputStream(file.toFile())){out.write(decoded);}return file;}
    private byte[] decoded(Path file)throws IOException{try(var in=new GZIPInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))){return in.readAllBytes();}}
    private static int firstRecord(byte[] data){int offset=8+integer(data,4),refs=integer(data,offset);offset+=4;for(int i=0;i<refs;i++){int n=integer(data,offset);offset+=4+n+4;}return offset;}
    private Path appendTag(Path file,byte[] tag)throws IOException{byte[] body=decoded(file);int record=firstRecord(body);byte[] changed=Arrays.copyOf(body,body.length+tag.length);System.arraycopy(tag,0,changed,body.length,tag.length);integer(changed,record,integer(body,record)+tag.length);return compressed(changed);}
    private StringTieBam.Report inspect(Path input)throws IOException{return StringTieBam.inspect(input,REFERENCE,STRICT,LIMITS,r->{},()->{});}
    private Path original(String name)throws Exception{
        byte[] raw;try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/stringtie-gtf/original-gtf-controls.zip")){assertNotNull(in);raw=in.readAllBytes();}
        assertEquals("c090420cb1896d6a52454c651af627b35200383a3469d52db446febc9ae3c1f2",NextflowFiles.sha(raw));
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().equals("inputs/"+name)){Path file=Files.createTempFile(temporary,"original-",".bam").toAbsolutePath();Files.write(file,zip.readAllBytes());return file;}}
        throw new AssertionError("Missing protected original fixture");
    }
    @Test void completeOriginal840AlignmentPopulationHasIndependentKnownGeometryAndRawFlags()throws Exception{
        Path input=original("reads.bam");var rows=new ArrayList<StringTieBam.Row>();var report=StringTieBam.inspect(input,REFERENCE,STRICT,LIMITS,rows::add,()->{});
        assertEquals(840,report.records());assertEquals(840,report.mapped());assertEquals(120,report.spliced());assertEquals(0,report.unresolvedSpliced());assertEquals(42000,report.matchedBases());assertEquals(0,report.deletedBases());assertEquals(26000,report.skippedBases());
        assertEquals(Map.of(0,560L,16,280L),report.flagCounts());assertEquals(840,rows.size());assertEquals(120,rows.stream().filter(r->!r.junctions().isEmpty()).count());
        assertTrue(rows.stream().allMatch(r->r.effectiveNh()==1&&r.reportedNh()==1));assertEquals(2,report.dictionary().size());assertTrue(report.bgzfBlocks()>1);report.verify(()->{});
        assertEquals(Boolean.FALSE,report.document().get("StringTie_usable_read_population_inferred"));assertEquals(Boolean.FALSE,report.document().get("mate_reciprocity_certified"));
    }
    @Test void emptyOriginalBamIsExplicitAndDoesNotInventMeasuredAbsence()throws Exception{var report=inspect(original("empty.bam"));assertEquals(0,report.records());assertEquals(2,report.dictionary().size());assertEquals(0,report.mapped());}
    @Test void fullFlagsAndUnplacedTailAreRetainedWithoutSecondaryDuplicateOrQcFiltering()throws Exception{
        String rows=mapped("primary",1)+read("secondary",256,"chrA",2,"4M","NH:i:2")+read("supplementary",2048,"chrA",3,"4M","NH:i:1")+read("duplicate",1024,"chrA",4,"4M","NH:i:1")+read("qc",512,"chrA",5,"4M","NH:i:1")+"unmapped\t4\t*\t0\t0\t*\t*\t0\t0\t*\t*\n";
        var out=new ArrayList<StringTieBam.Row>();var report=StringTieBam.inspect(bam(HEADER,rows),REFERENCE,STRICT,LIMITS,out::add,()->{});assertEquals(6,report.records());assertEquals(5,report.mapped());assertEquals(1,report.missingSequenceUnmapped());assertEquals(6,out.size());assertEquals(2,out.get(1).effectiveNh());assertEquals(2048,out.get(2).flags());
    }
    @Test void spliceAndDeletionGeometryIsInclusiveAndNeverFillsIntronsWithAlignedBases()throws Exception{
        Path input=bam(HEADER,read("spliced",16,"chrA",10,"1M3D1M10N2M","NH:i:1\tXS:A:-"));var rows=new ArrayList<StringTieBam.Row>();var report=StringTieBam.inspect(input,REFERENCE,STRICT,LIMITS,rows::add,()->{});
        assertEquals(4,report.matchedBases());assertEquals(3,report.deletedBases());assertEquals(10,report.skippedBases());assertEquals(List.of(new StringTieBam.Junction(15,24)),rows.get(0).junctions());assertEquals(26,rows.get(0).end1());assertEquals('-',rows.get(0).xs());
    }
    @ParameterizedTest @ValueSource(strings={"absent","zero"})
    void missingOrZeroNhRequiresExplicitOriginalFallbackAndRetainsItsReportedState(String state)throws Exception{
        Path input=bam(HEADER,read("one",0,"chrA",1,"4M",state.equals("zero")?"NH:i:0":""));assertThrows(IOException.class,()->inspect(input));var rows=new ArrayList<StringTieBam.Row>();
        var policies=new StringTieBam.Policies(StringTieBam.MissingNh.ORIGINAL_ONE,StringTieBam.MissingXs.ERROR,StringTieBam.SortHeader.REQUIRE_COORDINATE);
        var report=StringTieBam.inspect(input,REFERENCE,policies,LIMITS,rows::add,()->{});assertEquals(1,report.assumedNh());assertEquals(state.equals("absent")?1:0,report.missingNh());assertEquals(state.equals("zero")?1:0,report.zeroNh());assertEquals(1,rows.get(0).effectiveNh());
        if(state.equals("absent"))assertNull(rows.get(0).reportedNh());else assertEquals(0,rows.get(0).reportedNh());
    }
    @ParameterizedTest @ValueSource(strings={"NH:i:-1","NH:f:1.0","NH:Z:1"})
    void invalidNhTypeOrDomainCannotBeConvertedToAUsableMultiplicity(String tag)throws Exception{Path input=bam(HEADER,read("one",0,"chrA",1,"4M",tag));assertThrows(IOException.class,()->inspect(input));}
    @ParameterizedTest @ValueSource(strings={"","XS:A:.","XS:i:-2","XS:Z:+","XS:A:?","ts:A:+"})
    void unresolvedSpliceStrandRequiresExplicitRetentionAndIsNeverFilledFromOrientationOrTs(String tag)throws Exception{
        Path input=bam(HEADER,read("one",0,"chrA",1,"2M10N2M","NH:i:1"+(tag.isEmpty()?"":"\t"+tag)));assertThrows(IOException.class,()->inspect(input));var rows=new ArrayList<StringTieBam.Row>();
        var report=StringTieBam.inspect(input,REFERENCE,new StringTieBam.Policies(StringTieBam.MissingNh.ERROR,StringTieBam.MissingXs.KEEP_UNRESOLVED,StringTieBam.SortHeader.REQUIRE_COORDINATE),LIMITS,rows::add,()->{});
        assertEquals(1,report.unresolvedSpliced());assertTrue(rows.get(0).unresolvedSpliceStrand());if(tag.startsWith("XS:i"))assertEquals("INTEGER",rows.get(0).xsTagKind());if(tag.equals("ts:A:+")){assertNull(rows.get(0).xs());assertEquals('+',rows.get(0).ts());}
    }
    @Test void nonCharacterXsOnUnsplicedReadsRemainsDistinctFromAnRnaStrand()throws Exception{
        var rows=new ArrayList<StringTieBam.Row>();var report=StringTieBam.inspect(bam(HEADER,read("one",0,"chrA",1,"4M","NH:i:1\tXS:i:-2")),REFERENCE,STRICT,LIMITS,rows::add,()->{});assertEquals(1,report.nonCharacterXs());assertEquals("INTEGER",rows.get(0).xsTagKind());assertNull(rows.get(0).xs());
    }
    @ParameterizedTest @ValueSource(strings={"reverse-positions","reverse-contigs","after-unplaced"})
    void completeRecordOrderIsCheckedInsteadOfTrustingTheHeader(String kind)throws Exception{
        String body=switch(kind){case "reverse-positions"->mapped("one",10)+mapped("two",1);case "reverse-contigs"->read("one",0,"chrB",1,"4M","NH:i:1")+mapped("two",1);default->"one\t4\t*\t0\t0\t*\t*\t0\t0\tACGT\tIIII\n"+mapped("two",1);};Path input=bam(HEADER,body);assertThrows(IOException.class,()->inspect(input));
    }
    @Test void unspecifiedSortNeedsAnExplicitPolicyAndStillChecksEveryRecord()throws Exception{
        Path input=bam(HEADER.replace("SO:coordinate","SO:unsorted"),mapped("one",1));assertThrows(IOException.class,()->inspect(input));var policies=new StringTieBam.Policies(StringTieBam.MissingNh.ERROR,StringTieBam.MissingXs.ERROR,StringTieBam.SortHeader.VERIFY_RECORDS);assertEquals(1,StringTieBam.inspect(input,REFERENCE,policies,LIMITS,r->{},()->{}).records());
    }
    @ParameterizedTest @ValueSource(strings={"length","missing","alias"})
    void dictionaryMustMatchAllNamesAndLengthsWithoutAliases(String change)throws Exception{
        Path input=bam(HEADER,mapped("one",1));Map<String,Long> expected=switch(change){case "length"->Map.of("chrA",12001L,"chrB",10000L);case "missing"->Map.of("chrA",12000L);default->Map.of("A",12000L,"chrB",10000L);};assertThrows(IOException.class,()->StringTieBam.inspect(input,expected,STRICT,LIMITS,r->{},()->{}));
    }
    @Test void textAndBinaryReferenceDictionariesCannotDisagree()throws Exception{
        Path file=bam(HEADER,mapped("one",1));byte[] body=decoded(file);byte[] old="LN:12000".getBytes(StandardCharsets.US_ASCII),replacement="LN:12001".getBytes(StandardCharsets.US_ASCII);int index=new String(body,StandardCharsets.ISO_8859_1).indexOf(new String(old,StandardCharsets.US_ASCII));assertTrue(index>0);System.arraycopy(replacement,0,body,index,replacement.length);Path changed=compressed(body);assertThrows(IOException.class,()->inspect(changed));
    }
    @ParameterizedTest @ValueSource(strings={"missing-eof","trailing-junk","repeated-eof","crc","flags","block-size","expanded-size","deflate"})
    void damagedPhysicalBamNeverPassesOnItsValidDecodedPrefix(String change)throws Exception{
        Path input=bam(HEADER,mapped("one",1));byte[] raw=Files.readAllBytes(input);int first=(raw[16]&255|(raw[17]&255)<<8)+1;
        switch(change){case "missing-eof"->raw=Arrays.copyOf(raw,raw.length-28);case "trailing-junk"->{raw=Arrays.copyOf(raw,raw.length+1);raw[raw.length-1]=1;}case "repeated-eof"->{byte[] eof=Arrays.copyOfRange(raw,raw.length-28,raw.length);int n=raw.length;raw=Arrays.copyOf(raw,n+28);System.arraycopy(eof,0,raw,n,28);}case "crc"->raw[first-8]^=1;case "flags"->raw[3]|=8;case "block-size"->{raw[16]=0;raw[17]=0;}case "expanded-size"->integer(raw,first-4,65537);case "deflate"->raw[18]^=127;default->throw new AssertionError();}
        Files.write(input,raw);assertThrows(IOException.class,()->inspect(input));
    }
    @Test void ordinaryGzipAndSamCannotMasqueradeAsDeclaredBam()throws Exception{
        Path input=bam(HEADER,mapped("one",1));byte[] body=decoded(input);var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes)){gzip.write(body);}Path gzip=Files.createTempFile(temporary,"gzip-",".bam").toAbsolutePath();Files.write(gzip,bytes.toByteArray());assertThrows(IOException.class,()->inspect(gzip));Path sam=Files.createTempFile(temporary,"sam-",".bam").toAbsolutePath();Files.writeString(sam,HEADER+mapped("one",1));assertThrows(IOException.class,()->inspect(sam));
    }
    @ParameterizedTest @ValueSource(strings={"duplicate","unterminated","invalid-array","array-subtype","long-cigar","invalid-type","invalid-tag","hex"})
    void completeAuxiliaryPopulationIsCheckedBeforeLazyHtsjdkDecoding(String mutation)throws Exception{
        byte[] tag=switch(mutation){case "duplicate"->new byte[]{'N','H','i',1,0,0,0};case "unterminated"->new byte[]{'X','Z','Z','a'};case "invalid-array"->new byte[]{'X','Z','B','i',-1,-1,-1,127};case "array-subtype"->new byte[]{'X','Z','B','Z',0,0,0,0};case "long-cigar"->new byte[]{'C','G','B','I',0,0,0,0};case "invalid-type"->new byte[]{'X','Z','q',1};case "invalid-tag"->new byte[]{'1','1','i',1,0,0,0};default->new byte[]{'X','Z','H','A',0};};Path changed=appendTag(bam(HEADER,mapped("one",1)),tag);assertThrows(IOException.class,()->inspect(changed));
    }
    @Test void hostileHeaderAndRecordLengthsFailBeforeLargeLibraryAllocations()throws Exception{
        byte[] original=decoded(bam(HEADER,mapped("one",1)));for(int offset:new int[]{4,firstRecord(original)}){byte[] data=original.clone();integer(data,offset,Integer.MAX_VALUE);Path file=compressed(data);assertThrows(IOException.class,()->inspect(file));}
    }
    @Test void capacitiesAndCancellationNeverReturnAPassingPrefix()throws Exception{
        Path input=bam(HEADER,mapped("one",1)+mapped("two",2));var tiny=new StringTieBam.Limits(10000000,20000000,1,1000000,1000000,100,10000,1000,100,10000);assertThrows(IOException.class,()->StringTieBam.inspect(input,REFERENCE,STRICT,tiny,r->{},()->{}));assertThrows(CancellationException.class,()->StringTieBam.inspect(input,REFERENCE,STRICT,LIMITS,r->{},()->{throw new CancellationException();}));
        assertThrows(IOException.class,()->StringTieBam.inspect(input,REFERENCE,STRICT,LIMITS,r->{throw new IOException("ledger failed");},()->{}));
    }
    @Test void encodedDecodedMetadataRecordReferenceReadAndTagBoundsHaveExactCompleteBoundaries()throws Exception{
        Path input=bam(HEADER,mapped("one",1));byte[] body=decoded(input);long encoded=Files.size(input),expanded=body.length;int metadata=HEADER.getBytes(StandardCharsets.UTF_8).length+26,record=integer(body,firstRecord(body));
        var exact=new StringTieBam.Limits(encoded,expanded,1,metadata,record,2,4,1,2,1);assertEquals(1,StringTieBam.inspect(input,REFERENCE,STRICT,exact,r->{},()->{}).records());
        for(var reduced:List.of(new StringTieBam.Limits(encoded-1,expanded,1,metadata,record,2,4,1,2,1),new StringTieBam.Limits(encoded,expanded-1,1,metadata,record,2,4,1,2,1),
                new StringTieBam.Limits(encoded,expanded,1,metadata-1,record,2,4,1,2,1),new StringTieBam.Limits(encoded,expanded,1,metadata,record-1,2,4,1,2,1),
                new StringTieBam.Limits(encoded,expanded,1,metadata,record,1,4,1,2,1),new StringTieBam.Limits(encoded,expanded,1,metadata,record,2,3,1,2,1),new StringTieBam.Limits(encoded,expanded,1,metadata,record,2,4,1,1,1)))
            assertThrows(IOException.class,()->StringTieBam.inspect(input,REFERENCE,STRICT,reduced,r->{},()->{}));
    }
    @Test void completeCigarAndArrayItemCapacitiesRejectInsteadOfTruncating()throws Exception{
        Path spliced=bam(HEADER,read("one",0,"chrA",1,"2M10N2M","NH:i:1\tXS:A:+"));
        assertThrows(IOException.class,()->StringTieBam.inspect(spliced,REFERENCE,STRICT,new StringTieBam.Limits(10000000,20000000,100,1000000,1000000,100,10000,2,100,10000),r->{},()->{}));
        Path array=bam(HEADER,read("one",0,"chrA",1,"4M","NH:i:1\tZZ:B:i,1,2,3"));assertEquals(1,inspect(array).records());
        assertThrows(IOException.class,()->StringTieBam.inspect(array,REFERENCE,STRICT,new StringTieBam.Limits(10000000,20000000,100,1000000,1000000,100,10000,1000,100,2),r->{},()->{}));
    }
    @Test void completeSourceIdentityIsRecheckedAfterAllRowsAndOnLaterVerification()throws Exception{
        Path input=bam(HEADER,mapped("one",1));var report=inspect(input);Files.write(input,new byte[]{1},java.nio.file.StandardOpenOption.APPEND);assertThrows(IOException.class,()->report.verify(()->{}));
        Path changed=bam(HEADER,mapped("one",1));assertThrows(IOException.class,()->StringTieBam.inspect(changed,REFERENCE,STRICT,LIMITS,r->Files.write(changed,new byte[]{1},java.nio.file.StandardOpenOption.APPEND),()->{}));
    }
    @Test void reportCollectionsAndJunctionsCannotBeMutated()throws Exception{
        var rows=new ArrayList<StringTieBam.Row>();var report=StringTieBam.inspect(bam(HEADER,read("one",0,"chrA",1,"2M10N2M","NH:i:1\tXS:A:+")),REFERENCE,STRICT,LIMITS,rows::add,()->{});assertThrows(UnsupportedOperationException.class,()->report.dictionary().clear());assertThrows(UnsupportedOperationException.class,()->report.flagCounts().clear());assertThrows(UnsupportedOperationException.class,()->rows.get(0).junctions().clear());
    }
}
