/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SignacNormalizationContractsTest {
    @TempDir Path temp;
    private static final class Fixture {
        static final Map<String,String> DATA=load();
        static Map<String,String> load(){try(var raw=SignacNormalizationContractsTest.class.getResourceAsStream("/org/jlinalg/sequence/signac-normalization/original-controls.zip")){assertNotNull(raw);Map<String,String> files=new HashMap<>();try(var zip=new ZipInputStream(raw)){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null)files.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}assertEquals(350,files.size());return files;}catch(IOException e){throw new UncheckedIOException(e);}}
    }
    static Map<String,Object> request(){var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","NORMALIZE");r.put("source_profile","Signac-1.9.0");r.put("counts","counts.mtx");r.put("features","features.tsv");r.put("cells","cells.tsv");r.put("selection",Map.of("kind","ALL"));r.put("out","result-结果");return r;}
    static Stream<Arguments> invalid(){return Stream.of(Arguments.of("schema_version",1.5),Arguments.of("mode","FIT"),Arguments.of("source_profile","latest"),Arguments.of("tfidf_method",5),Arguments.of("tfidf_method",1.5),Arguments.of("scale_factor",0),Arguments.of("scale_factor",-1),Arguments.of("selection",null),Arguments.of("selection",Map.of("kind","COUNT")),Arguments.of("selection",Map.of("kind","PERCENTILE","value",101)),Arguments.of("selection",Map.of("kind","ALL","value",1)),Arguments.of("selection",Map.of("kind","KEEP_EXISTING")),Arguments.of("existing_features","unused.tsv"),Arguments.of("maximum_features",0),Arguments.of("maximum_cells",0),Arguments.of("maximum_entries",-1),Arguments.of("maximum_entries",20000001),Arguments.of("maximum_input_bytes",0),Arguments.of("maximum_output_bytes",0),Arguments.of("maximum_line_characters",4095),Arguments.of("rscript","unused-Rscript"),Arguments.of("arbitrary_command","never execute"));}
    @ParameterizedTest @MethodSource("invalid") void invalidUnusedOrUnboundedOptionsFailBeforeAnalysis(String key,Object value){var r=request();r.put(key,value);assertThrows(IOException.class,()->SignacNormalizationCli.validate(r));}
    static Stream<Arguments> originalNormalization(){return Stream.of(
        Arguments.of("hand-count-versus-document-frequency",1,10000.0,false,"Signac-1.6.0"),Arguments.of("hand-count-versus-document-frequency",2,1.0,true,"Signac-1.7.0"),
        Arguments.of("hand-count-versus-document-frequency",3,1e308,true,"Signac-1.9.0"),Arguments.of("hand-count-versus-document-frequency",1,1e308,true,"Signac-1.9.0"),Arguments.of("hand-count-versus-document-frequency",4,1.0,false,"Signac-1.14.0"),
        Arguments.of("zero-row-and-cell-stored",1,10000.0,false,"Signac-1.17.1"),Arguments.of("all-zero-implicit",4,10000.0,false,"Signac-1.17.1"),
        Arguments.of("exact-integer-limit",2,10000.0,false,"Signac-1.9.0"),Arguments.of("one-by-one-empty-stored",1,10000.0,false,"Signac-1.6.0"));}
    private void writeInput(String id)throws IOException{
        for(String key:List.of("features","cells"))Files.writeString(temp.resolve(key+".tsv"),Fixture.DATA.get(id+"/"+key+".tsv"));var features=Fixture.DATA.get(id+"/features.tsv").lines().toList();var cells=Fixture.DATA.get(id+"/cells.tsv").lines().toList();String[] pointers=Fixture.DATA.get(id+"/pointers.tsv").strip().split(","),indices=tokens(Fixture.DATA.get(id+"/indices.tsv")),values=tokens(Fixture.DATA.get(id+"/values.tsv"));
        var text=new StringBuilder("%%MatrixMarket matrix coordinate integer general\n").append(features.size()).append(' ').append(cells.size()).append(' ').append(values.length).append('\n');for(int cell=0;cell<cells.size();cell++)for(int k=Integer.parseInt(pointers[cell]);k<Integer.parseInt(pointers[cell+1]);k++)text.append(Integer.parseInt(indices[k])+1).append(' ').append(cell+1).append(' ').append((long)Double.parseDouble(values[k])).append('\n');Files.writeString(temp.resolve("counts.mtx"),text);
    }
    @ParameterizedTest @MethodSource("originalNormalization") void wholeOriginalReferenceValuesAndSparseGeometryMatch(String id,int method,double scale,boolean supplied,String profile)throws Exception{
        writeInput(id);var r=request();r.put("tfidf_method",method);r.put("scale_factor",scale);r.put("source_profile",profile);
        if(supplied){var features=Fixture.DATA.get(id+"/features.tsv").lines().toList();String[] values=tokens(Fixture.DATA.get(id+"/idf.tsv"));var text=new StringBuilder("feature\tidf\n");for(int i=0;i<values.length;i++)text.append(features.get(i)).append('\t').append(values[i]).append('\n');Files.writeString(temp.resolve("idf.tsv"),text);r.put("idf","idf.tsv");}
        run(r);String line=Fixture.DATA.get(id+"/normalized.tsv").lines().filter(x->{String[] f=x.split("\t",-1);return Integer.parseInt(f[0])==method&&Double.parseDouble(f[1])==scale&&Boolean.parseBoolean(f[2])==supplied;}).findFirst().orElseThrow();String[] fields=line.split("\t",-1);double[] expected=Arrays.stream(tokens(fields[3])).mapToDouble(SignacNormalizationContractsTest::number).toArray();var lines=Files.readAllLines(output().resolve("normalized.mtx")).stream().filter(x->!x.startsWith("%")).toList();assertEquals(expected.length+1,lines.size());for(int i=0;i<expected.length;i++)same(expected[i],number(lines.get(i+1).split(" ")[2]));
        assertEquals(Fixture.DATA.get(id+"/features.tsv"),Files.readString(output().resolve("features.tsv")));assertEquals(Fixture.DATA.get(id+"/cells.tsv"),Files.readString(output().resolve("cells.tsv")));for(String code:tokens(fields[4]))assertTrue(Files.readString(output().resolve("WARNINGS.md")).contains(code));SignacNormalizationCli.verify(output());
    }
    static Stream<Arguments> sourceSelection(){return Stream.of(Arguments.of("COUNT",5.0),Arguments.of("PERCENTILE",50.0),Arguments.of("KEEP_EXISTING",0.0));}
    @ParameterizedTest @MethodSource("sourceSelection") void explicitSelectionMatchesOriginalAndDoesNotNormalize(String kind,double cutoff)throws Exception{
        String id="tied-feature-totals";writeInput(id);var r=request();r.put("mode","TOP_FEATURES");r.put("selection",Map.of("kind",kind,"value",cutoff));String[] fields=Fixture.DATA.get(id+"/selections.tsv").lines().filter(x->{String[] f=x.split("\t",-1);return f[0].equals(kind)&&Double.parseDouble(f[1])==cutoff;}).findFirst().orElseThrow().split("\t",-1);if(kind.equals("KEEP_EXISTING")){Files.writeString(temp.resolve("existing.tsv"),String.join("\n",tokens(fields[2]))+"\n");r.put("existing_features","existing.tsv");}run(r);assertEquals(List.of(tokens(fields[3])),Files.readAllLines(output().resolve("variable-features.tsv")));assertFalse(Files.exists(output().resolve("normalized.mtx")));SignacNormalizationCli.verify(output());
    }
    @Test void duplicateCoordinateTransportAndExplicitZeroAreAuditedThroughRootRoute()throws Exception{
        Files.writeString(temp.resolve("features.tsv"),"peak-α\nempty\n");Files.writeString(temp.resolve("cells.tsv"),"c1\nc2\n");Files.writeString(temp.resolve("counts.mtx"),"%%MatrixMarket matrix coordinate integer general\n2 2 3\n1 1 1\n1 1 2\n2 2 0\n");var r=request();r.put("tfidf_method",4);run(r);String matrix=Files.readString(output().resolve("normalized.mtx"));assertTrue(matrix.contains("2 2 2\n1 1 2.0\n2 2 0.0\n"));var d=SeuratDifferentialContracts.read(output().resolve("diagnostics.json"));assertEquals(1,((Number)d.get("duplicate_coordinates_coalesced")).intValue());assertEquals(1,((Number)d.get("explicit_zero_input_entries")).intValue());assertTrue(Files.readString(output().resolve("WARNINGS.md")).contains("ZERO_FEATURE_COUNTS"));
    }
    @Test void derivedValueTamperingIsRefusedAfterResealing()throws Exception{writeInput("hand-count-versus-document-frequency");run(request());Path matrix=output().resolve("normalized.mtx");var lines=Files.readAllLines(matrix);String[] row=lines.get(3).split(" ");lines.set(3,row[0]+" "+row[1]+" 999.0");Files.write(matrix,lines,StandardCharsets.UTF_8);SignacPeaksCli.seal(output());assertThrows(IOException.class,()->SignacNormalizationCli.verify(output()));}
    @Test void topFeaturesRejectsIgnoredNormalizationFields()throws Exception{var r=request();r.put("mode","TOP_FEATURES");r.put("tfidf_method",1);assertThrows(IOException.class,()->SignacNormalizationCli.validate(r));}
    @Test void sourceFailureRetainsActionAndNeverAdmitsPartialOutput()throws Exception{Path p=temp.resolve("request.json");SeuratDifferentialContracts.json(p,request());var log=new ByteArrayOutputStream();var stream=new PrintStream(log,true,StandardCharsets.UTF_8);assertEquals(2,JLinAlgCli.run(new String[]{"signac-tools","--workflow","normalize","--request",p.toString()},stream,stream));assertFalse(Files.exists(output().resolve("COMPLETED.json")));assertTrue(Files.readString(output().resolve("analysis-status.json")).contains("FAIL2_NO_RESULT_ADMITTED"));assertTrue(log.toString(StandardCharsets.UTF_8).contains("Cause:"));assertThrows(IOException.class,()->SignacNormalizationCli.verify(output()));}
    private void run(Map<String,Object> request)throws IOException{Path p=temp.resolve("request.json");SeuratDifferentialContracts.json(p,request);var log=new ByteArrayOutputStream();var stream=new PrintStream(log,true,StandardCharsets.UTF_8);assertEquals(0,JLinAlgCli.run(new String[]{"signac-tools","--workflow","normalize","--request",p.toString()},stream,stream),log.toString(StandardCharsets.UTF_8));}
    private Path output(){return temp.resolve("result-结果");}
    private static String[] tokens(String s){return s.strip().isEmpty()?new String[0]:s.strip().split(",",-1);}
    private static double number(String s){return switch(s){case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;case "NaN"->Double.NaN;default->Double.parseDouble(s);};}
    private static void same(double expected,double actual){if(!Double.isFinite(expected))assertEquals(expected,actual);else assertEquals(expected,actual,Math.max(16*Math.ulp(expected),Math.abs(expected)*3e-13));}
}
