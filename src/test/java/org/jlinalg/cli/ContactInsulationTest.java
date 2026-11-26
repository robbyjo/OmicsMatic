package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.jlinalg.omics.contact.PreparedInsulation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.ContactInsulationCompute.map;

class ContactInsulationTest {
    @TempDir Path temporary;
    static final String HEADER="bin_id\tchrom\tstart\tend\tregion\tweight\n";
    static final String PIXELS="bin1_id\tbin2_id\tcount\n";
    Path fixture(String name,String weights,String pixels)throws IOException {
        Path root=temporary.resolve(name);Files.createDirectories(root.resolve("inputs"));Files.createDirectory(root.resolve("outputs"));
        String[] values=weights.split(",",-1);StringBuilder bins=new StringBuilder(HEADER);
        for(int i=0;i<values.length;i++)bins.append(i+"\tchrα\t"+(10*i)+"\t"+(10*i+10)+"\tregion😀\t"+values[i]+"\n");
        Files.writeString(root.resolve("inputs/bins.tsv"),bins);Files.writeString(root.resolve("inputs/pixels.tsv"),PIXELS+pixels);return root;
    }
    PreparedInsulation.Settings settings(){return new PreparedInsulation.Settings(10,List.of(30L),2,0,0.66,0.1,100,1000,1000000);}
    PreparedInsulation.Result compute(Path root)throws IOException {return PreparedInsulation.calculate(root.resolve("inputs/bins.tsv"),root.resolve("inputs/pixels.tsv"),settings(),()->{});}
    ContactInsulationRequest.Spec spec()throws IOException {
        var value=ContactInsulationCli.example();value.put("settings",map("bin_width",10,"windows_bp",List.of(30),"ignore_diagonals",2,"min_distance_bad",0,"min_fraction_valid",0.66,"strength_threshold",0.1,"threshold_comparison",">=","weights","supplied-weight-column","normalization","region-nanmedian"));
        value.put("capacity",map("bins",100,"pixel_rows",1000,"input_bytes",1000000,"output_bytes",1000000,"wall_millis",60000));return ContactInsulationRequest.parse(value,false);
    }
    @Test void implicitZeroPixelsContributeToTheFullDiamondDenominator()throws Exception {
        var result=compute(fixture("known","1,1,1,1,1","0\t2\t4\n1\t3\t8\n2\t4\t12\n"));
        var rows=result.windows().get(0).rows();
        assertArrayEquals(new double[]{1,3,6,3,1},rows.stream().mapToDouble(PreparedInsulation.Row::nValidPixels).toArray());
        assertArrayEquals(new double[]{4,12,24,20,12},rows.stream().mapToDouble(PreparedInsulation.Row::sumCounts).toArray());
        assertArrayEquals(new double[]{1,1,1,5.0/3,3},rows.stream().mapToDouble(PreparedInsulation.Row::score).toArray(),1e-14);
        assertEquals(3,result.pixelRecords());assertEquals(5,result.bins());assertEquals(0,result.outsideRegionRecords());
    }
    @Test void recordOrderRepetitionsAndBetweenRegionCountsAreRetained()throws Exception {
        Path root=fixture("repeated","1,1,1,1,1","2\t4\t12\n0\t2\t4\n1\t3\t8\n0\t2\t2\n0\t5\t100\n");
        Files.writeString(root.resolve("inputs/bins.tsv"),"5\tchrβ\t0\t10\tsecond\t1\n",StandardOpenOption.APPEND);
        var result=compute(root);assertEquals(5,result.pixelRecords());assertEquals(4,result.cisPixelRecords());assertEquals(1,result.outsideRegionRecords());
        assertArrayEquals(new double[]{6,14,26,20,12},result.windows().get(0).rows().subList(0,5).stream().mapToDouble(PreparedInsulation.Row::sumCounts).toArray());
    }
    @Test void zeroWeightRemainsGoodWhileNaNRemovesOnlyBalancedPairs()throws Exception {
        var zero=compute(fixture("zero","0,1,1","0\t2\t4\n"));
        var bad=compute(fixture("bad","NaN,1,1","0\t2\t4\n"));
        assertEquals(0,zero.badBins());assertEquals(1,bad.badBins());
        assertEquals(1,zero.windows().get(0).rows().get(0).nValidPixels());assertEquals(0,bad.windows().get(0).rows().get(0).nValidPixels());
        assertEquals(4,bad.windows().get(0).rows().get(0).sumCounts());assertEquals(0,bad.windows().get(0).rows().get(0).sumBalanced());
        assertTrue(Double.isNaN(bad.windows().get(0).rows().get(0).log2Score()));
    }
    @Test void sourceDistanceMaskUsesEdgesEvenWithoutMarkedBadBins()throws Exception {
        Path root=fixture("edges","1,1,1,1,1","0\t2\t4\n1\t3\t8\n2\t4\t12\n");
        var s=new PreparedInsulation.Settings(10,List.of(30L),2,2,0.66,0.1,100,1000,1000000);
        var rows=PreparedInsulation.calculate(root.resolve("inputs/bins.tsv"),root.resolve("inputs/pixels.tsv"),s,()->{}).windows().get(0).rows();
        assertTrue(Double.isNaN(rows.get(0).log2Score()));assertTrue(Double.isNaN(rows.get(4).log2Score()));
        assertFalse(Double.isNaN(rows.get(1).log2Score()));assertEquals(1,rows.get(0).distanceToBad());assertFalse(rows.get(0).boundaryEligible());
    }
    @Test void fullSavedReplayChecksEveryOutputValue()throws Exception {
        Path root=fixture("replay","1,1,1,1,1","0\t2\t4\n1\t3\t8\n2\t4\t12\n");var s=spec();
        var audit=ContactInsulationCompute.run(root,s,false,()->{});assertEquals(5L,audit.get("output_rows"));
        assertEquals(NextflowJson.json(audit),NextflowJson.json(ContactInsulationCompute.run(root,s,true,()->{})));
        Path output=root.resolve("outputs/insulation.tsv");String before=Files.readString(output);Files.writeString(output,before.replace("region😀","altered"));
        assertThrows(IOException.class,()->ContactInsulationCompute.run(root,s,true,()->{}));
    }
    @Test void invalidPixelNumbersAxesBlankRowsAndUtf8AreNotDropped()throws Exception {
        int index=0;for(String row:List.of("2\t0\t1\n","0\t5\t1\n","0\t2\t-1\n","0\t2\tInf\n","0\t2\tNaN\n","0\t2\t0x1p0\n","00\t2\t1\n","0\t2\t1\n\n","0\t2\n")) {
            Path root=fixture("invalid"+(index++),"1,1,1",row);assertThrows(IOException.class,()->compute(root),row);
        }
        Path root=fixture("utf8","1,1,1","");Files.write(root.resolve("inputs/pixels.tsv"),new byte[]{(byte)0xc3,0x28},StandardOpenOption.APPEND);assertThrows(IOException.class,()->compute(root));
    }
    @Test void noWindowOrRegionAxisRepairIsPermitted()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new PreparedInsulation.Settings(10,List.of(25L),2,0,0.66,0.1,100,1000,1000000));
        assertThrows(IllegalArgumentException.class,()->new PreparedInsulation.Settings(10,List.of(30L,30L),2,0,0.66,0.1,100,1000,1000000));
        Path root=fixture("gap","1,1,1","");Path bins=root.resolve("inputs/bins.tsv");Files.writeString(bins,Files.readString(bins).replace("\t10\t20\t","\t11\t20\t"));assertThrows(IOException.class,()->compute(root));
    }
    @Test void policyThresholdAndUnknownOptionsAreExplicit()throws Exception {
        for(var edit:List.of(map("engine","cooltools"),map("source_behavior","guess"),map("unknown_option",true),map("process",Map.of()))) {
            var r=ContactInsulationCli.example();r.putAll(edit);assertThrows(IOException.class,()->ContactInsulationRequest.parse(r,false));
        }
        var r=ContactInsulationCli.example();var s=new LinkedHashMap<String,Object>();NextflowFiles.object(r.get("settings")).forEach((k,v)->s.put(k.toString(),v));s.put("threshold_comparison",">");r.put("settings",s);assertThrows(IOException.class,()->ContactInsulationRequest.parse(r,false));
    }
    @Test void capacitiesAndCancellationRefuseIncompleteWork()throws Exception {
        Path root=fixture("capacity","1,1,1","0\t2\t4\n0\t2\t4\n");
        var s=new PreparedInsulation.Settings(10,List.of(30L),2,0,0.66,0.1,100,1,1000000);
        assertThrows(IOException.class,()->PreparedInsulation.calculate(root.resolve("inputs/bins.tsv"),root.resolve("inputs/pixels.tsv"),s,()->{}));
        assertThrows(CancellationException.class,()->PreparedInsulation.calculate(root.resolve("inputs/bins.tsv"),root.resolve("inputs/pixels.tsv"),settings(),()->{throw new CancellationException("test");}));
        var r=new LinkedHashMap<String,Object>(spec().value());r.put("capacity",map("bins",100,"pixel_rows",1000,"input_bytes",1000000,"output_bytes",1,"wall_millis",60000));
        assertThrows(IOException.class,()->ContactInsulationCompute.run(root,ContactInsulationRequest.parse(r,false),false,()->{}));
    }
    @Test void missingPrerequisitesSkipWithAnActionAndNoCapture()throws Exception {
        var request=ContactInsulationCli.example();request.put("input",map("bins",temporary.resolve("missing-bins.tsv").toString(),"pixels",temporary.resolve("missing-pixels.tsv").toString(),"format","literal-utf8-tsv"));
        Path path=temporary.resolve("request.json");Files.writeString(path,NextflowJson.json(request));var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(3,ContactInsulationCli.run(new String[]{"--request",path.toString(),"--out",temporary.resolve("absent-capture").toString()},new PrintStream(out),new PrintStream(err)));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertTrue(err.toString(StandardCharsets.UTF_8).contains("Required action:"));assertFalse(Files.exists(temporary.resolve("absent-capture")));
    }
    @Test void finiteMedianAdditionOverflowFailsInsteadOfAdmittingZeroScores()throws Exception {
        var settings=new PreparedInsulation.Settings(10,List.of(10L),0,0,0.66,0.1,100,1000,1000000);
        Path overflow=fixture("median-overflow","1,1,1,1","0\t0\t1e308\n1\t1\t1e308\n2\t2\t1e308\n3\t3\t1e308\n");
        var error=assertThrows(ArithmeticException.class,()->PreparedInsulation.calculate(overflow.resolve("inputs/bins.tsv"),overflow.resolve("inputs/pixels.tsv"),settings,()->{}));
        assertTrue(error.getMessage().contains("median addition"));
        Path finite=fixture("large-finite-median","1,1,1,1","0\t0\t1e307\n1\t1\t1e307\n2\t2\t1e307\n3\t3\t1e307\n");
        var result=PreparedInsulation.calculate(finite.resolve("inputs/bins.tsv"),finite.resolve("inputs/pixels.tsv"),settings,()->{});
        assertArrayEquals(new double[]{1,1,1,1},result.windows().get(0).rows().stream().mapToDouble(PreparedInsulation.Row::score).toArray());
    }
}
