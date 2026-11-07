/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.MemeMotifInputs;
import org.jlinalg.sequence.MemeXml;
import org.jlinalg.sequence.TomtomResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MemeMotifInputsTest {
    @TempDir Path temporary;
    static final String RESOURCE="/org/jlinalg/sequence/tomtom-inputs/original-input-controls.zip";
    static final MemeXml.Limits XML=new MemeXml.Limits(8_000_000,8_000_000,100000,64,128);
    static final MemeMotifInputs.Limits LIMITS=new MemeMotifInputs.Limits(8_000_000,8_000_000,10000,512,1_000_000,XML);
    static Stream<String> complete()throws IOException{return records(archive()).stream().filter(r->r.get("kind").equals("original-complete-input-and-output")).map(r->(String)r.get("id"));}
    @ParameterizedTest @MethodSource("complete")
    void everyIndependentRawInputAndEveryOriginalOutputScalarPass(String id)throws Exception {
        var files=archive();var row=record(files,id);var query=read(files,(String)row.get("query_input"));var target=read(files,"inputs/targets.meme");
        var bg=TomtomResults.backgrounds(query.background(),true);var command=strings(row.get("command"));
        String qSource=command.get(command.size()-2),tSource=command.get(command.size()-1);
        var qdb=database(qSource,query,bg.firstQuery());var tdb=database(tSource,target,bg.followingDatabases());
        var argv=new ArrayList<>(command);argv.set(0,"tomtom");
        var expected=new TomtomResults.Expected(String.join(" ",argv),"ed",true,"qvalue",1,.1,1,false,"--query--",bg.firstQuery(),List.of(qdb),List.of(tdb));
        Path xml=write(files,id+"/result/tomtom.xml"),tsv=write(files,id+"/result/tomtom.tsv");
        var audit=TomtomResults.read(xml,tsv,expected,new TomtomResults.Limits(XML,8_000_000,100000,100000,1_000_000),()->{});
        assertEquals(2,query.entries().size());assertEquals(4,audit.pairs().size());assertEquals(4,audit.matches().size());
        assertEquals(NextflowFiles.sha(files.get((String)row.get("query_input"))),query.sha256());
        assertArrayEquals(files.get((String)row.get("query_input")),Files.readAllBytes(temporary.resolve((String)row.get("query_input"))));
        assertTrue(query.warnings().stream().anyMatch(s->s.startsWith("INPUT_COMPONENT_ONLY")));
        if(id.equals("equals-id")){assertEquals("TF=first",query.entries().get(0).model().id());assertEquals("",query.entries().get(0).model().alternateId());}
        if(id.equals("unicode-id"))assertEquals("TFα",query.entries().get(0).model().id());
        if(id.equals("metadata-url")){assertEquals(new BigDecimal("1.234e-200"),query.entries().get(0).model().evalue());assertEquals("https://example.test/motif?x=1&y=2",query.entries().get(0).model().url());}
    }
    @Test void originalZeroAndMissingSitesUseTwentyForSmoothingButKeepDistinctDeclaredMetadata()throws Exception {
        var files=archive();var zero=read(files,"inputs/zero-sites.meme").entries().get(0);var missing=read(files,"inputs/missing-sites.meme").entries().get(0);
        for(var entry:List.of(zero,missing)){assertEquals(0,entry.model().sites());assertEquals(20,entry.pseudocountSites());}
        assertEquals("declared",zero.metadata().get("sites.rule"));assertEquals("missing-default-zero",missing.metadata().get("sites.rule"));
        assertEquals(BigDecimal.ONE,read(files,"inputs/missing-evalue.meme").entries().get(0).model().evalue());
        assertEquals(BigDecimal.ZERO,read(files,"inputs/zero-sites.meme").entries().get(0).model().evalue());
    }
    @Test void sourceNormalizationPreservesCloseRowMassAndRawTokens()throws Exception {
        var files=archive();var close=read(files,"inputs/mass-1.000009.meme").entries().get(0);var far=read(files,"inputs/mass-1.000011.meme").entries().get(0);
        assertEquals(List.of(),close.normalizedPositions());assertEquals(List.of(1,2,3,4,5,6),far.normalizedPositions());
        double[] bg={.25,.25,.25,.25};double closeMass=java.util.Arrays.stream(TomtomResults.preprocess(close.model(),0,bg)[0]).sum();
        assertEquals(1.000009,closeMass,1e-15);assertEquals(1,java.util.Arrays.stream(TomtomResults.preprocess(far.model(),0,bg)[0]).sum(),1e-15);
        assertEquals(1.000011,java.util.Arrays.stream(far.model().probabilities()[0]).sum(),1e-15);
        assertEquals("0.7000076999999999",far.probabilityTokens().get(0).get(0));
        double[][] raw=far.model().probabilities();raw[0][0]=0;assertTrue(far.model().probabilities()[0][0]>.7);
        assertThrows(UnsupportedOperationException.class,()->far.metadata().put("E","0"));
        assertThrows(UnsupportedOperationException.class,()->far.probabilityTokens().get(0).set(0,"0"));
    }
    @Test void stremeXmlSourceMappingsStayDifferentFromTextAndRawDiscoveryFields()throws Exception {
        var files=archive();var xml=read(files,"inputs/STREME-xml.xml");var text=read(files,"inputs/STREME-txt.txt");
        assertArrayEquals(new double[]{.25,.25,.25,.25},xml.background());assertArrayEquals(new double[]{.247,.253,.253,.247},text.background());
        var motif=xml.entries().get(0);assertEquals(62,motif.model().sites());assertEquals("45",motif.metadata().get("total_sites"));
        assertEquals("4.1e-007",motif.metadata().get("test_evalue"));assertEquals("source-test-P-from-log10-not-E",motif.metadata().get("E.rule"));
        assertTrue(motif.model().evalue().compareTo(new BigDecimal("2.02e-7"))>0);assertTrue(motif.model().evalue().compareTo(new BigDecimal("2.04e-7"))<0);
        assertEquals(new BigDecimal("4.1e-7"),text.entries().get(0).model().evalue());assertTrue(xml.warnings().get(0).startsWith("STREME_XML_5_5_9_SOURCE_MAPPING"));
        double[] bg=xml.background();bg[0]=1;assertEquals(.25,xml.background()[0]);
    }
    @Test void originalInvalidRowsRemainFailuresAndOriginalHtmlSuccessRemainsExplicitUnsupported()throws Exception {
        var files=archive();
        for(String id:List.of("mass-0.5","mass-0.0")){assertEquals(1,number(record(files,id),"exit_code"));assertThrows(IOException.class,()->read(files,"inputs/"+id+".meme"));}
        for(String id:List.of("MEME-html","STREME-html")){assertEquals(0,number(record(files,id),"exit_code"));var error=assertThrows(MemeMotifInputs.UnsupportedInput.class,()->read(files,"inputs/"+id+".html"));assertTrue(error.getMessage().contains("skip the dependent comparison"));}
    }
    @Test void appendedMotifAndShortenedMatrixCannotBecomeACompletePrefix()throws Exception {
        var files=archive();Path path=write(files,"inputs/mass-1.0.meme");String original=Files.readString(path);
        Files.writeString(path,original+"MOTIF unfinished alt\n");assertThrows(IOException.class,()->MemeMotifInputs.read(path,LIMITS,()->{}));
        Files.writeString(path,original.substring(0,original.lastIndexOf("0.699999")));assertThrows(IOException.class,()->MemeMotifInputs.read(path,LIMITS,()->{}));
        Files.writeString(path,original.replace("nsites= 20","nsites= 0.5"));assertThrows(MemeMotifInputs.UnsupportedInput.class,()->MemeMotifInputs.read(path,LIMITS,()->{}));
    }
    @Test void completeModelScalarWidthAndByteCapacitiesFailWithoutTruncation()throws Exception {
        var files=archive();Path path=write(files,"inputs/MEME-txt.txt");
        for(var limits:List.of(new MemeMotifInputs.Limits(10,8_000_000,10000,512,1_000_000,XML),new MemeMotifInputs.Limits(8_000_000,10,10000,512,1_000_000,XML),new MemeMotifInputs.Limits(8_000_000,8_000_000,1,512,1_000_000,XML),new MemeMotifInputs.Limits(8_000_000,8_000_000,10000,5,1_000_000,XML),new MemeMotifInputs.Limits(8_000_000,8_000_000,10000,512,4,XML)))
            assertThrows(IOException.class,()->MemeMotifInputs.read(path,limits,()->{}));
    }
    @Test void sameLengthSameTimestampMutationIsDetectedByFinalRawHash()throws Exception {
        var files=archive();Path path=write(files,"inputs/mass-1.0.meme");var time=Files.getLastModifiedTime(path);var calls=new AtomicInteger();
        assertThrows(IOException.class,()->MemeMotifInputs.read(path,LIMITS,()->{if(calls.incrementAndGet()==4)try{Files.writeString(path,Files.readString(path).replace("first","other"));Files.setLastModifiedTime(path,time);}catch(IOException e){throw new java.io.UncheckedIOException(e);}}));
    }
    @Test void cancellationNeverReturnsACompleteInputReceipt()throws Exception {
        Path path=write(archive(),"inputs/STREME-xml.xml");var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->MemeMotifInputs.read(path,LIMITS,()->{if(calls.incrementAndGet()==50)throw new CancellationException("controlled");}));
    }
    @Test void duplicateIdsRemainVisibleAndUnknownModelMetadataIsNotSilentlyDropped()throws Exception {
        var files=archive();Path path=write(files,"inputs/mass-1.0.meme");String original=Files.readString(path);
        Files.writeString(path,original.replace("second","first"));var doc=MemeMotifInputs.read(path,LIMITS,()->{});assertEquals(2,doc.entries().size());assertTrue(doc.warnings().stream().anyMatch(s->s.startsWith("DUPLICATE_IDS")));
        assertThrows(IllegalArgumentException.class,()->database("/not-opened.meme",doc,new double[]{.25,.25,.25,.25}));
        Files.writeString(path,original.replace(" E= 0"," surprising= 7 E= 0"));assertThrows(MemeMotifInputs.UnsupportedInput.class,()->MemeMotifInputs.read(path,LIMITS,()->{}));
    }
    @Test void sourceSizedDatabasesAreNotSilentlyLimitedToOneThousandMotifs()throws Exception {
        var files=archive();String original=new String(files.get("inputs/mass-1.0.meme"),StandardCharsets.UTF_8);String header=original.substring(0,original.indexOf("MOTIF"));String first=original.substring(original.indexOf("MOTIF"),original.indexOf("MOTIF second"));var text=new StringBuilder(header);
        for(int i=0;i<1201;i++)text.append(first.replace("first","motif"+i));Path path=temporary.resolve("large.meme");Files.writeString(path,text);var doc=MemeMotifInputs.read(path,LIMITS,()->{});
        assertEquals(1201,doc.entries().size());assertEquals(1201,doc.entries().get(1200).ordinal());
    }
    @Test void extraProbabilityRowsAndLongAlternateIdsRequireExplicitFailure()throws Exception {
        var files=archive();Path path=write(files,"inputs/mass-1.0.meme");String original=Files.readString(path);String[] lines=original.split("\n",-1);
        int matrix=-1;for(int i=0;i<lines.length;i++)if(lines[i].startsWith("letter-probability")){matrix=i;break;}
        var changed=new ArrayList<>(List.of(lines));changed.add(matrix+7,"0.7 0.1 0.1 0.1");Files.writeString(path,String.join("\n",changed));
        assertTrue(assertThrows(IOException.class,()->MemeMotifInputs.read(path,LIMITS,()->{})).getMessage().contains("Extra original probability row"));
        Files.writeString(path,original.replace("alt-first","a".repeat(201)));assertTrue(assertThrows(MemeMotifInputs.UnsupportedInput.class,()->MemeMotifInputs.read(path,LIMITS,()->{})).getMessage().contains("200-byte"));
    }
    @Test void learnedXmlUsesFormatSpecificBackgroundToleranceAndRefusesUncontrolledEditions()throws Exception {
        Path path=write(archive(),"inputs/MEME-xml.xml");String original=Files.readString(path);
        String marker="<background_frequencies";int begin=original.indexOf(marker),end=original.indexOf("</background_frequencies>",begin);
        String background=original.substring(begin,end);assertTrue(background.contains(">0.250</value>")||background.contains(">0.25</value>"));
        String changed=background.replaceFirst(">0\\.250?</value>",">0.3</value>");assertNotEquals(background,changed);
        Files.writeString(path,original.substring(0,begin)+changed+original.substring(end));assertTrue(assertThrows(IOException.class,()->MemeMotifInputs.read(path,LIMITS,()->{})).getMessage().contains("format-specific 0.01"));
        Files.writeString(path,original.replace("version=\"5.5.9\"","version=\"4.9.1\""));assertThrows(MemeMotifInputs.UnsupportedInput.class,()->MemeMotifInputs.read(path,LIMITS,()->{}));
    }
    @Test void invalidUtf8CannotBeReplacedSilently()throws Exception {
        Path path=temporary.resolve("invalid.meme");Files.write(path,new byte[]{(byte)0xc3,(byte)0x28});
        assertTrue(assertThrows(IOException.class,()->MemeMotifInputs.read(path,LIMITS,()->{})).getMessage().contains("complete UTF-8"));
    }
    private MemeMotifInputs.Document read(Map<String,byte[]> files,String name)throws IOException{return MemeMotifInputs.read(write(files,name),LIMITS,()->{});}
    private Path write(Map<String,byte[]> files,String name)throws IOException{Path path=temporary.resolve(name);Files.createDirectories(path.getParent());Files.write(path,files.get(name));return path;}
    private static TomtomResults.Database database(String source,MemeMotifInputs.Document doc,double[] bg){String name=source.substring(source.lastIndexOf('/')+1);if(name.endsWith(".meme"))name=name.substring(0,name.length()-5);return new TomtomResults.Database(source,name,doc.entries().stream().map(MemeMotifInputs.Entry::model).toList(),bg);}
    private static Map<String,byte[]> archive()throws IOException {
        byte[] raw;try(var input=MemeMotifInputsTest.class.getResourceAsStream(RESOURCE)){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals("e7b94b363c89a9bc91e27528c395178217f8646c55c41ed9b98c87aaf79d9c66",NextflowFiles.sha(raw));
        var files=new LinkedHashMap<String,byte[]>();try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.isDirectory());assertNull(files.put(e.getName(),z.readAllBytes()));}}return files;
    }
    private static List<Map<?,?>> records(Map<String,byte[]> files)throws IOException {var manifest=(Map<?,?>)SimpleJson.parse(new String(files.get("EXPECTED.json"),StandardCharsets.UTF_8));var rows=new ArrayList<Map<?,?>>();for(Object value:(List<?>)manifest.get("cases"))rows.add((Map<?,?>)value);return List.copyOf(rows);}
    private static Map<?,?> record(Map<String,byte[]> files,String id)throws IOException{return records(files).stream().filter(r->r.get("id").equals(id)).findFirst().orElseThrow();}
    private static int number(Map<?,?> row,String key){return ((Number)row.get(key)).intValue();}
    private static List<String> strings(Object value){var strings=new ArrayList<String>();for(Object part:(List<?>)value)strings.add((String)part);return List.copyOf(strings);}
}
