package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.jlinalg.omics.methylation.AllcoolsPosterior;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

class AllcoolsPosteriorTest {
    @TempDir Path temporary;
    static final String HEADER="cell\tmc_type\tfeature\tmc\tcov\n";
    AllcoolsPosterior.Settings settings(AllcoolsPosterior.Representation repr,boolean norm,Double clip){return new AllcoolsPosterior.Settings(repr,AllcoolsPosterior.Operation.POSTERIOR_FRACTION,norm,clip,0.01);}
    @Test void momentPriorAndZeroCoverageFollowTheOriginalAlgebra() {
        var r=AllcoolsPosterior.calculate(new long[]{1,2,3,4,0},new long[]{5,5,5,5,0},settings(AllcoolsPosterior.Representation.NUMPY,true,10.0),()->{});
        assertEquals(0.5,r.mean(),1e-15);assertEquals(0.05,r.variance(),1e-15);assertEquals(2,r.alpha(),1e-14);assertEquals(2,r.beta(),1e-14);
        assertArrayEquals(new double[]{2.0/3,8.0/9,10.0/9,4.0/3,1},r.values(),1e-14);assertEquals(1,r.zeroCoverageFeatures());assertEquals(4,r.coveredFeatures());
    }
    @Test void xarrayClippingCanTurnDegenerateNaNsIntoFiniteValues() {
        for(long[][] input:List.of(new long[][]{{0,0},{0,0}},new long[][]{{0,0},{10,10}},new long[][]{{5,5},{10,10}},new long[][]{{0,10},{10,10}})) {
            var numpy=AllcoolsPosterior.calculate(input[0],input[1],settings(AllcoolsPosterior.Representation.NUMPY,true,10.0),()->{});
            var xarray=AllcoolsPosterior.calculate(input[0],input[1],settings(AllcoolsPosterior.Representation.XARRAY,true,10.0),()->{});
            for(double x:numpy.values())assertTrue(Double.isNaN(x));assertArrayEquals(new double[]{10,10},xarray.values());assertEquals(2,xarray.preclipNaNsReplaced());
            for(double x:AllcoolsPosterior.calculate(input[0],input[1],settings(AllcoolsPosterior.Representation.XARRAY,true,null),()->{}).values())assertTrue(Double.isNaN(x));
        }
    }
    @Test void clippingIsInactiveWhenNormalizationIsDisabled() {
        var r=AllcoolsPosterior.calculate(new long[]{0,10},new long[]{10,10},settings(AllcoolsPosterior.Representation.XARRAY,false,0.1),()->{});
        assertArrayEquals(new double[]{0,1},r.values());assertEquals(0,r.clippedFeatures());assertEquals(0,r.preclipNaNsReplaced());
    }
    @Test void mValueRegularizationIsAppliedAfterUnnormalizedPosterior() {
        var r=AllcoolsPosterior.calculate(new long[]{1,2,3,4},new long[]{5,5,5,5},new AllcoolsPosterior.Settings(AllcoolsPosterior.Representation.XARRAY,AllcoolsPosterior.Operation.M_VALUE,false,null,0.01),()->{});
        assertEquals(Math.log((1.0/3+0.01)/(2.0/3+0.01))/Math.log(2),r.values()[0],1e-14);
        assertThrows(IllegalArgumentException.class,()->new AllcoolsPosterior.Settings(AllcoolsPosterior.Representation.XARRAY,AllcoolsPosterior.Operation.M_VALUE,true,10.0,0.01));
    }
    @Test void invalidCountsAndCooperativeCancellationAreRefused() {
        for(long[][] a:List.of(new long[][]{{-1},{1}},new long[][]{{2},{1}},new long[][]{{1},{4294967296L}},new long[][]{{},{}}))assertThrows(IllegalArgumentException.class,()->AllcoolsPosterior.calculate(a[0],a[1],settings(AllcoolsPosterior.Representation.NUMPY,true,10.0),()->{}));
        assertThrows(CancellationException.class,()->AllcoolsPosterior.calculate(new long[]{1},new long[]{2},settings(AllcoolsPosterior.Representation.NUMPY,true,10.0),()->{throw new CancellationException("test");}));
    }
    @Test void nearZeroMValueNormalizationIsRefusedWithoutChangingRawValues() {
        var s=new AllcoolsPosterior.Settings(AllcoolsPosterior.Representation.XARRAY,AllcoolsPosterior.Operation.M_VALUE,true,null,0.01);
        for(long[][] x:List.of(new long[][]{{1,2,3,4,0},{5,5,5,5,0}},new long[][]{{4294967294L,4294967293L,0,1},{4294967295L,4294967295L,4294967295L,4294967295L}})) {
            var e=assertThrows(ArithmeticException.class,()->AllcoolsPosterior.calculate(x[0],x[1],s,()->{}));assertTrue(e.getMessage().contains("M-value normalization mean"));assertTrue(e.getMessage().contains("normalize_per_cell:false"));
            var raw=AllcoolsPosterior.calculate(x[0],x[1],new AllcoolsPosterior.Settings(s.representation(),s.operation(),false,null,s.mValueAlpha()),()->{});for(double v:raw.values())assertTrue(Double.isFinite(v));
        }
    }
    Map<String,Object> request(int cells,int types,int features){var r=AllcoolsPosteriorCli.example();r.put("dimensions",map("cells",cells,"mc_types",types,"features",features));r.put("capacity",map("input_bytes",1000000,"output_bytes",1000000,"wall_millis",60000));return r;}
    Path fixture(String name,String body)throws IOException {Path p=temporary.resolve(name);Files.createDirectories(p.resolve("inputs"));Files.createDirectory(p.resolve("outputs"));Files.writeString(p.resolve("inputs/counts.tsv"),HEADER+body);return p;}
    String body(){return "cellα\tmCG\tr1\t1\t5\ncellα\tmCG\tr2\t2\t5\ncellα\tmCG\tr3\t3\t5\ncellα\tmCG\tr4\t4\t5\ncellα\tmCG\tr5\t0\t0\n";}
    @Test void everyValueAndFittedPriorParticipatesInSavedReplay()throws Exception {
        Path root=fixture("replay",body());var s=AllcoolsPosteriorRequest.parse(request(1,1,5),false);var audit=AllcoolsPosteriorCompute.run(root,s,false,()->{});
        assertEquals(5L,audit.get("complete_rows"));assertEquals(1L,audit.get("zero_coverage_rows"));
        assertEquals(NextflowJson.json(audit),NextflowJson.json(AllcoolsPosteriorCompute.run(root,s,true,()->{})));
        for(String name:List.of("values.tsv","priors.tsv")){Path p=root.resolve("outputs/"+name);String original=Files.readString(p);Files.writeString(p,original.replace("cellα","changed"));assertThrows(IOException.class,()->AllcoolsPosteriorCompute.run(root,s,true,()->{}));Files.writeString(p,original);}
    }
    @Test void completeRectangularCellTypeAndFeatureOrderIsRequired()throws Exception {
        int i=0;for(String b:List.of(body().replace("r2","r1"),body().replace("cellα\tmCG\tr2","cellβ\tmCG\tr2"),body().replace("mCG\tr2","mCH\tr2"),body()+"extra\tmCG\tr1\t0\t0\n",body().substring(0,body().lastIndexOf("cellα")))) {
            Path root=fixture("axes"+(i++),b);assertThrows(IOException.class,()->AllcoolsPosteriorCompute.run(root,AllcoolsPosteriorRequest.parse(request(1,1,5),false),false,()->{}));
        }
    }
    @Test void malformedCountsBlankRowsAndInvalidUtf8AreNotRepaired()throws Exception {
        int i=0;for(String field:List.of("-1","NaN","Inf","1.0","01","4294967296","+1","","0x1")){Path root=fixture("count"+(i++),body().replace("r1\t1\t5","r1\t"+field+"\t5"));assertThrows(IOException.class,()->AllcoolsPosteriorCompute.run(root,AllcoolsPosteriorRequest.parse(request(1,1,5),false),false,()->{}));}
        Path root=fixture("utf8",body());Files.write(root.resolve("inputs/counts.tsv"),new byte[]{(byte)0xc3,0x28},StandardOpenOption.APPEND);assertThrows(IOException.class,()->AllcoolsPosteriorCompute.run(root,AllcoolsPosteriorRequest.parse(request(1,1,5),false),false,()->{}));
        Path blank=fixture("blank",body()+"\n");assertThrows(IOException.class,()->AllcoolsPosteriorCompute.run(blank,AllcoolsPosteriorRequest.parse(request(1,1,5),false),false,()->{}));
    }
    @Test void unknownSourceSettingsAndMismatchedDimensionsAreRefused()throws Exception {
        for(var edit:List.of(map("source_behavior","guess"),map("engine","allcools"),map("process",Map.of()),map("unknown",true))){var r=request(1,1,5);r.putAll(edit);assertThrows(IOException.class,()->AllcoolsPosteriorRequest.parse(r,false));}
        var r=request(1,1,5);r.put("dimensions",map("cells",Integer.MAX_VALUE-8,"mc_types",Integer.MAX_VALUE-8,"features",Integer.MAX_VALUE-8));assertThrows(IOException.class,()->AllcoolsPosteriorRequest.parse(r,false));
    }
    @Test void fullOutputCapacityAndCancellationAreEnforced()throws Exception {
        Path root=fixture("capacity",body());var r=request(1,1,5);r.put("capacity",map("input_bytes",1000000,"output_bytes",1,"wall_millis",60000));assertThrows(IOException.class,()->AllcoolsPosteriorCompute.run(root,AllcoolsPosteriorRequest.parse(r,false),false,()->{}));
        Path cancelled=fixture("cancelled",body());assertThrows(CancellationException.class,()->AllcoolsPosteriorCompute.run(cancelled,AllcoolsPosteriorRequest.parse(request(1,1,5),false),false,()->{throw new CancellationException("test");}));
    }
    @Test void absentPrerequisitesSkipAndExplainTheRequiredAction()throws Exception {
        var r=request(1,1,5);r.put("input",map("counts",temporary.resolve("missing.tsv").toString(),"format","literal-utf8-tsv"));Path p=temporary.resolve("request.json");Files.writeString(p,NextflowJson.json(r));var err=new ByteArrayOutputStream();
        assertEquals(3,AllcoolsPosteriorCli.run(new String[]{"--request",p.toString(),"--out",temporary.resolve("capture").toString()},new PrintStream(OutputStream.nullOutputStream()),new PrintStream(err)));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertTrue(err.toString(StandardCharsets.UTF_8).contains("Required action:"));assertFalse(Files.exists(temporary.resolve("capture")));
    }
    @Test void sourceDefinedMissingValuesAreCountedEvenWhenXarrayClipsThem()throws Exception {
        Path root=fixture("markers","c\tmCG\tr1\t0\t0\nc\tmCG\tr2\t0\t0\n");var a=AllcoolsPosteriorCompute.run(root,AllcoolsPosteriorRequest.parse(request(1,1,2),false),false,()->{});
        assertEquals(2L,a.get("preclip_nans_replaced"));assertEquals(2L,NextflowFiles.object(a.get("posterior_markers")).get("NaN"));assertEquals(2L,NextflowFiles.object(a.get("value_markers")).get("finite"));
    }
}
