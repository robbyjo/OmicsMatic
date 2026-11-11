/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Literal five-edition source tables, closed options and explicit skip/failure contracts. */
final class HtseqFileCountTest {
    @TempDir Path temp;
    static List<?> cases()throws IOException{
        try(var in=new GZIPInputStream(HtseqFileCountTest.class.getResourceAsStream("/org/jlinalg/sequence/htseq-file-count/original-tables.json.gz"))){
            return (List<?>)HtseqFileContract.object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases");
        }
    }
    static Stream<Arguments> originalTables()throws IOException{
        var out=new ArrayList<Arguments>();for(Object value:cases()){var c=HtseqFileContract.object(value);if(c.get("source_exit_code").equals(0L))out.add(Arguments.of(c.get("id"),c));}assertEquals(171,out.size());return out.stream();
    }
    static Stream<Arguments> originalFailures()throws IOException{
        var out=new ArrayList<Arguments>();for(Object value:cases()){var c=HtseqFileContract.object(value);if(!c.get("source_exit_code").equals(0L))out.add(Arguments.of(c.get("id"),c));}assertEquals(14,out.size());return out.stream();
    }
    @ParameterizedTest(name="source table {0}") @MethodSource("originalTables")
    void allLiteralSourceFields(String id,Map<String,Object> c)throws Exception{
        Path file=temp.resolve("table.txt");Files.writeString(file,(String)c.get("raw"),StandardCharsets.UTF_8);
        assertEquals(c.get("raw_pin"),HtseqFileContract.pin(file,1048576));
        var result=HtseqCountTable.read(file,(String)c.get("version"),(String)c.get("nonunique"),1048576,1000);
        var expected=HtseqFileContract.object(c.get("counts"));assertFields((List<?>)expected.get("genes"),result.genes());assertFields((List<?>)expected.get("summary"),result.summary());
        assertTrue(result.genesTsv().startsWith("feature_id\tcount\n"));assertTrue(result.summaryTsv().startsWith("category\tcount\n"));
        assertThrows(UnsupportedOperationException.class,()->result.genes().add(new HtseqCountTable.Field("repair","1")));
    }
    private static void assertFields(List<?> expected,List<HtseqCountTable.Field> actual){
        assertEquals(expected.size(),actual.size());for(int i=0;i<actual.size();i++){List<?> row=(List<?>)expected.get(i);assertEquals(row.get(0),actual.get(i).name());assertEquals(row.get(1),actual.get(i).value());}
    }
    @ParameterizedTest(name="original source failure {0}") @MethodSource("originalFailures")
    void originalErrorsDoNotBecomeCounts(String id,Map<String,Object> c)throws Exception{
        Path file=temp.resolve("error.stdout");Files.writeString(file,(String)c.get("raw"),StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->HtseqCountTable.read(file,(String)c.get("version"),(String)c.get("nonunique"),1048576,1000));
    }
    private static final String SUMMARY="__no_feature\t0\n__ambiguous\t0\n__too_low_aQual\t0\n__not_aligned\t0\n__alignment_not_unique\t0\n";
    static Stream<Arguments> invalidTables(){return Stream.of(
        Arguments.of("NaN","A\tNaN\n"+SUMMARY),Arguments.of("Inf","A\tInf\n"+SUMMARY),Arguments.of("negative","A\t-1\n"+SUMMARY),
        Arguments.of("duplicate","A\t1\nA\t2\n"+SUMMARY),Arguments.of("unsorted","B\t1\nA\t2\n"+SUMMARY),
        Arguments.of("extra column","A\t1\t2\n"+SUMMARY),Arguments.of("blank identity","\t1\n"+SUMMARY),
        Arguments.of("fraction category",SUMMARY.replace("__ambiguous\t0","__ambiguous\t0.5")),
        Arguments.of("wrong edition summary",SUMMARY.replace("__no_feature","no_feature")),Arguments.of("incomplete",SUMMARY.substring(0,SUMMARY.lastIndexOf("__alignment"))),
        Arguments.of("bare CR","A\t1\r"+SUMMARY),Arguments.of("nonfraction gene","A\t0.5\n"+SUMMARY),
        Arguments.of("nonfinite exponent","A\t1e999\n"+SUMMARY));}
    @ParameterizedTest(name="reject {0}") @MethodSource("invalidTables")
    void refuseInvalidLiteralTables(String label,String value)throws Exception{
        Path file=temp.resolve("invalid.txt");Files.writeString(file,value,StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->HtseqCountTable.read(file,"2.1.2","none",1048576,1000));
    }
    @Test void fractionalLiteralAndCodepointIdentitiesArePreserved()throws Exception{
        Path file=temp.resolve("fraction.txt");String bmp="\uE000",supplementary="\uD800\uDC00";
        Files.writeString(file,bmp+"\t0.3333333333333333\n"+supplementary+"\t0.6666666666666666\n"+SUMMARY,StandardCharsets.UTF_8);
        var table=HtseqCountTable.read(file,"2.1.2","fraction",1048576,1000);
        assertEquals(List.of(bmp,supplementary),table.genes().stream().map(HtseqCountTable.Field::name).toList());assertEquals("0.3333333333333333",table.genes().get(0).value());
    }
    @Test void zeroFeatureTableIsExplicitlyEmpty()throws Exception{
        Path file=temp.resolve("zero.txt");Files.writeString(file,SUMMARY);assertTrue(HtseqCountTable.read(file,"2.1.2","none",1048576,5).genes().isEmpty());
    }
    @Test void completeByteRowLineAndUtf8Bounds()throws Exception{
        Path file=temp.resolve("limits.txt");Files.writeString(file,"A\t1\n"+SUMMARY);
        assertThrows(IOException.class,()->HtseqCountTable.read(file,"2.1.2","none",1,1000));assertThrows(IOException.class,()->HtseqCountTable.read(file,"2.1.2","none",1048576,5));
        Files.write(file,new byte[]{(byte)0xFF,10});assertThrows(IOException.class,()->HtseqCountTable.read(file,"2.1.2","none",1048576,1000));
        Files.writeString(file,"A".repeat(65537)+"\t1\n"+SUMMARY);assertThrows(IOException.class,()->HtseqCountTable.read(file,"2.1.2","none",1048576,1000));
    }
    private Map<String,Object> request(String version){
        var r=new TreeMap<String,Object>();r.putAll(Map.of("schema_version",1L,"operation","COUNT","version",version,"runtime_home","missing-runtime","alignment","reads.sam","annotation","features.gtf","format","sam","order","name","stranded","no","overlap","union"));
        r.putAll(Map.of("nonunique","none","secondary",version.equals("0.5.3")||version.equals("0.6.0")?"score":"ignore","supplementary",version.equals("0.5.3")||version.equals("0.6.0")?"score":"ignore","minimum_mapq",21L,"feature_type","exon","id_attribute","gene_id","genome_label","hg38-declared","annotation_label","explicit-annotation","counting_unit","original-auto","out","new-output"));return r;
    }
    private Path document(Map<String,Object> request)throws IOException{Path path=temp.resolve("request.json");Files.write(path,RsemJson.bytes(request));return path;}
    static Stream<Arguments> editions(){return Stream.of("0.5.3","0.6.0","0.11.1","0.13.5","2.1.2").map(Arguments::of);}
    @ParameterizedTest @MethodSource("editions") void exactProfileRequestsAndStrictMapq(String version)throws Exception{
        var r=HtseqFileContract.request(document(request(version)));assertEquals(21L,r.values().get("minimum_mapq"));assertEquals(temp.resolve("reads.sam").toAbsolutePath(),r.path("alignment"));
        var argv=HtseqFileContract.arguments(r,temp.resolve("a.sam"),temp.resolve("a.gtf"));assertEquals("21",argv.get(argv.indexOf("-a")+1));
        assertEquals(!version.equals("0.5.3"),argv.contains("-f"));assertEquals(!List.of("0.5.3","0.6.0").contains(version),argv.contains("--secondary-alignments"));
    }
    @Test void refuseUnspecifiedOrUnsupportedEditionPolicies()throws Exception{
        for(String version:List.of("0.13","latest","2.1.3"))assertThrows(IOException.class,()->HtseqFileContract.request(document(request(version))));
        for(String version:List.of("0.5.3","0.6.0")){var r=request(version);r.put("secondary","ignore");assertThrows(IOException.class,()->HtseqFileContract.request(document(r)));}
        var fraction=request("0.11.1");fraction.put("nonunique","fraction");assertThrows(IOException.class,()->HtseqFileContract.request(document(fraction)));
        var badFormat=request("0.5.3");badFormat.put("format","bam");assertThrows(IOException.class,()->HtseqFileContract.request(document(badFormat)));
        var random=request("2.1.2");random.put("nonunique","random");assertThrows(IOException.class,()->HtseqFileContract.request(document(random)));
    }
    @Test void refuseUnknownImplicitAndInexactRequestFields()throws Exception{
        for(String name:List.of("annotation_label","counting_unit","minimum_mapq","stranded")){var r=request("2.1.2");r.remove(name);assertThrows(IOException.class,()->HtseqFileContract.request(document(r)));}
        var inexact=request("2.1.2");inexact.put("minimum_mapq",20.0);assertThrows(IOException.class,()->HtseqFileContract.request(document(inexact)));
        var unknown=request("2.1.2");unknown.put("custom_script","untrusted.py");assertThrows(IOException.class,()->HtseqFileContract.request(document(unknown)));
    }
    @ParameterizedTest @MethodSource("editions") void sourceGuardRejectsMissingChangedAndAddedScientificModules(String version)throws Exception{
        var profile=HtseqFileContract.profile(version);var sources=HtseqFileContract.object(profile.get("scientific_sources"));
        var packages=new TreeMap<String,Object>(sources);HtseqCountRuntime.sources(Map.of("package_files",packages),profile);
        String first=packages.firstKey();var original=packages.remove(first);assertThrows(IOException.class,()->HtseqCountRuntime.sources(Map.of("package_files",packages),profile));
        packages.put(first,Map.of("bytes",0L,"sha256","0".repeat(64)));assertThrows(IOException.class,()->HtseqCountRuntime.sources(Map.of("package_files",packages),profile));
        packages.put(first,original);packages.put("HTSeq/injected.py",original);assertThrows(IOException.class,()->HtseqCountRuntime.sources(Map.of("package_files",packages),profile));
    }
    @Test void missingRuntimeSkipsAnalysisWithVisibleRecovery()throws Exception{
        var messages=new ByteArrayOutputStream();int code=HtseqToolsCli.run(new String[]{"--request",document(request("2.1.2")).toString()},new PrintStream(messages),new PrintStream(messages));assertEquals(3,code);
        Path out=temp.resolve("new-output");assertTrue(Files.exists(out.resolve("INCOMPLETE.json")));assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertFalse(Files.exists(out.resolve("counts.tsv")));
        String warning=Files.readString(out.resolve("WARNINGS.md"));assertTrue(warning.contains("SKIP3"));assertTrue(warning.contains("skip dependent analysis"));assertTrue(warning.contains("NEW paths"));
    }
    @Test void existingOutputIsPreservedAndUnknownRouteFails()throws Exception{
        Path out=Files.createDirectory(temp.resolve("new-output"));Files.writeString(out.resolve("sentinel"),"preserve");var messages=new ByteArrayOutputStream();
        assertEquals(2,HtseqToolsCli.run(new String[]{"--request",document(request("2.1.2")).toString()},new PrintStream(messages),new PrintStream(messages)));assertEquals("preserve",Files.readString(out.resolve("sentinel")));assertFalse(Files.exists(out.resolve("WARNINGS.md")));
        assertEquals(2,HtseqToolsCli.run(new String[]{"--shell","anything"},new PrintStream(messages),new PrintStream(messages)));assertEquals(0,HtseqToolsCli.run(new String[]{"--help"},new PrintStream(messages),new PrintStream(messages)));
    }
    @Test void preparedCopyEnforcesCompleteBytesCapacityAndNewPath()throws Exception{
        Path source=temp.resolve("source"),target=temp.resolve("copy");Files.writeString(source,"ABC");var expected=HtseqFileContract.pin(source,100);
        HtseqFileContract.copy(source,target,expected,100);assertEquals("ABC",Files.readString(target));
        assertThrows(IOException.class,()->HtseqFileContract.copy(source,target,expected,100));assertEquals("ABC",Files.readString(target));
        assertThrows(IOException.class,()->HtseqFileContract.copy(source,temp.resolve("small"),expected,2));
        Files.writeString(source,"ABCD");assertThrows(IOException.class,()->HtseqFileContract.copy(source,temp.resolve("grown"),expected,100));
        Files.writeString(source,"A");assertThrows(IOException.class,()->HtseqFileContract.copy(source,temp.resolve("short"),expected,100));
    }
    @Test void binaryFormatGateRefusesCrAmAndGzipSubstitutionPreservingLiteralNames()throws Exception{
        Path alignment=temp.resolve("a.sam"),annotation=temp.resolve("a.gtf");Files.writeString(annotation,"CRAM\ts\texon\t1\t2\t.\t+\t.\tgene_id \"A\";\n");
        Files.writeString(alignment,"CRAM\t0\tchr1\t1\t60\t1M\t*\t0\t0\tA\tI\n");HtseqFileContract.formats(alignment,"sam",annotation);
        Files.write(alignment,new byte[]{'C','R','A','M',3,0,0});assertThrows(IOException.class,()->HtseqFileContract.formats(alignment,"bam",annotation));assertThrows(IOException.class,()->HtseqFileContract.formats(alignment,"sam",annotation));
        try(var out=new java.util.zip.GZIPOutputStream(Files.newOutputStream(alignment))){out.write("@HD\tVN:1.6\n".getBytes(StandardCharsets.UTF_8));}
        assertThrows(IOException.class,()->HtseqFileContract.formats(alignment,"sam",annotation));assertThrows(IOException.class,()->HtseqFileContract.formats(alignment,"bam",annotation));
        try(var out=new java.util.zip.GZIPOutputStream(Files.newOutputStream(alignment))){out.write(new byte[]{'B','A','M',1,0,0});}
        HtseqFileContract.formats(alignment,"bam",annotation);
        try(var out=new java.util.zip.GZIPOutputStream(Files.newOutputStream(annotation))){out.write("chr1".getBytes(StandardCharsets.UTF_8));}
        assertThrows(IOException.class,()->HtseqFileContract.formats(alignment,"bam",annotation));
    }
}
