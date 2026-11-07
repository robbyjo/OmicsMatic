/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.MemeXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MemeXmlTest {
    @TempDir Path temporary;
    private int caseNumber;
    static final String RESOURCE="/org/jlinalg/sequence/meme-suite/";
    static final MemeXml.Limits LIMITS=new MemeXml.Limits(8_000_000,8_000_000,100000,64,128);
    static Stream<String> documents() {
        var names=new ArrayList<String>();
        for(String mode:List.of("oops","zoops","anr"))for(String strand:List.of("forward","both"))names.add("meme-"+mode+"-"+strand+"/result/meme.xml");
        for(String distance:List.of("ed","kullback","pearson"))for(String strand:List.of("forward","both"))names.add("tomtom-"+distance+"-"+strand+"/result/tomtom.xml");
        names.add("streme-shuffled-control/result/streme.xml");names.add("streme-supplied-control/result/streme.xml");return names.stream();
    }
    @ParameterizedTest @MethodSource("documents")
    void completeOriginalDocumentsRetainEveryElementAndLiteralMetadata(String name)throws Exception {
        Map<String,byte[]> archive=archive();Path path=temporary.resolve("original.xml");Files.write(path,archive.get(name));
        Map<?,?> expected=expected(archive,name);var parsed=MemeXml.read(path,LIMITS,()->{});
        assertEquals(expected.get("root"),parsed.root().name());assertEquals("5.5.9",parsed.root().attribute("version"));
        assertEquals(((Number)expected.get("elements")).intValue(),parsed.elements());assertEquals(archive.get(name).length,parsed.bytes());assertEquals(expected.get("sha256"),parsed.sha256());
        assertEquals(name.startsWith("meme-"),parsed.internalMemeDtd());
        assertTrue(parsed.root().one("model").one("command_line").text().contains("/var/tmp/omicsmatic-meme-controls-"));
        assertThrows(IOException.class,()->parsed.root().one("not-present"));
        assertThrows(UnsupportedOperationException.class,()->parsed.root().attributes().put("version","wrong"));
        assertThrows(UnsupportedOperationException.class,()->parsed.root().children().clear());
        if(parsed.root().name().equals("tomtom")){
            assertEquals(3,parsed.root().one("queries").children().size());assertEquals(3,parsed.root().one("targets").children().size());
            assertEquals("0.0102996",parsed.root().one("queries").children().get(0).children().get(0).attribute("A"));
        }
    }
    @Test void lateJunkCannotReturnACompletePrefix()throws Exception {
        Path path=file("<MEME version='5.5.9'><model/></MEME>trailing");assertThrows(IOException.class,()->MemeXml.read(path,LIMITS,()->{}));
    }
    @Test void truncatedClosingElementAndDuplicateAttributeFail()throws Exception {
        for(String text:List.of("<MEME><model/></MEM", "<MEME version='x' version='y'/>","<MEME/><MEME/>")){
            Path path=file(text);assertThrows(IOException.class,()->MemeXml.read(path,LIMITS,()->{}));
        }
    }
    @ParameterizedTest @ValueSource(strings={
        "<!DOCTYPE MEME SYSTEM 'file:///unavailable'><MEME/>",
        "<!DOCTYPE MEME [<!ENTITY x 'expanded'>]><MEME>&x;</MEME>",
        "<!DOCTYPE MEME [<!ENTITY x SYSTEM 'https://example.invalid/'>]><MEME>&x;</MEME>",
        "<MEME xmlns='urn:other'/>","<MEME xmlns:x='urn:other' x:version='5'/>",
        "<?xml-stylesheet href='https://example.invalid/style'?><MEME/>","<MEME>mixed<model/>text</MEME>",
        "<?xml version='1.1'?><MEME/>","<?xml version='1.0' encoding='ISO-8859-1'?><MEME/>"})
    void entityNamespaceEncodingOrMixedContentCannotChangeTheContract(String text)throws Exception {
        Path path=file(text);assertThrows(IOException.class,()->MemeXml.read(path,LIMITS,()->{}));
    }
    @Test void originalDeclarationOnlyDtdAndEscapedTextRemainLiteral()throws Exception {
        Path path=file("<!DOCTYPE MEME [<!ELEMENT MEME ANY>]><MEME><model value='1.00e-2000'>A &amp; B &lt; C</model></MEME>");
        var result=MemeXml.read(path,LIMITS,()->{});assertTrue(result.internalMemeDtd());assertEquals("1.00e-2000",result.root().one("model").attribute("value"));assertEquals("A & B < C",result.root().one("model").text());
    }
    @Test void invalidLateUtf8IsNotReplaced()throws Exception {
        Path path=temporary.resolve("bad.xml");byte[] raw="<MEME/>".getBytes(StandardCharsets.UTF_8);byte[] invalid=java.util.Arrays.copyOf(raw,raw.length+1);invalid[raw.length]=(byte)0xff;Files.write(path,invalid);
        assertThrows(IOException.class,()->MemeXml.read(path,LIMITS,()->{}));
    }
    @Test void everyDeclaredCapacityHasAWholeDocumentGate()throws Exception {
        Path path=file("<MEME a='one' b='two'><model><x/></model></MEME>");
        for(var limit:List.of(new MemeXml.Limits(5,1000,10,10,10),new MemeXml.Limits(1000,5,10,10,10),new MemeXml.Limits(1000,1000,2,10,10),new MemeXml.Limits(1000,1000,10,2,10),new MemeXml.Limits(1000,1000,10,10,1)))
            assertThrows(IOException.class,()->MemeXml.read(path,limit,()->{}));
    }
    @Test void cancellationNeverReturnsAStagedTree()throws Exception {
        Path path=file("<MEME><model/><model/><model/></MEME>");var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->MemeXml.read(path,LIMITS,()->{if(calls.incrementAndGet()==5)throw new CancellationException("controlled");}));
    }
    @Test void sameLengthSameTimestampMutationBeforeFinalizationIsRejected()throws Exception {
        Path path=file("<MEME><model value='123'/></MEME>");FileTime time=Files.getLastModifiedTime(path);var calls=new AtomicInteger();
        assertThrows(IOException.class,()->MemeXml.read(path,LIMITS,()->{
            if(calls.incrementAndGet()==4)try{Files.writeString(path,"<MEME><model value='456'/></MEME>");Files.setLastModifiedTime(path,time);}catch(IOException failed){throw new IllegalStateException(failed);}
        }));
    }
    @Test void missingDirectoryAndInvalidLimitsAreNamedFailures()throws Exception {
        assertThrows(IOException.class,()->MemeXml.read(temporary,LIMITS,()->{}));assertThrows(IOException.class,()->MemeXml.read(temporary.resolve("missing"),LIMITS,()->{}));
        assertThrows(IllegalArgumentException.class,()->new MemeXml.Limits(0,1,1,1,1));
    }
    Path file(String text)throws IOException{Path path=temporary.resolve("case-"+(caseNumber++)+".xml");Files.writeString(path,text,StandardCharsets.UTF_8);return path;}
    static Map<String,byte[]> archive()throws IOException{
        byte[] raw;try(var stream=MemeXmlTest.class.getResourceAsStream(RESOURCE+"original-format-contracts.zip")){assertNotNull(stream);raw=stream.readAllBytes();}
        assertEquals("f3c93b73502484f329a99d3deeb5da3cb285fa6a7703e01824393f3a33216097",NextflowFiles.sha(raw));
        var result=new LinkedHashMap<String,byte[]>();try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.isDirectory());assertNull(result.put(e.getName(),z.readAllBytes()));}}return result;
    }
    static Map<?,?> expected(Map<String,byte[]> archive,String name)throws IOException{
        Map<?,?> expected=(Map<?,?>)SimpleJson.parse(new String(archive.get("EXPECTED.json"),StandardCharsets.UTF_8));
        return (Map<?,?>)((List<?>)expected.get("xml_documents")).stream().filter(row->((Map<?,?>)row).get("path").equals(name)).findFirst().orElseThrow();
    }
}
