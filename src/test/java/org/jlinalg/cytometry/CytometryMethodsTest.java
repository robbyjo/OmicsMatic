/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CytometryMethodsTest {
    @TempDir Path temporary;
    @Test void completeNumpyCompensationReferencesFixMatrixDirectionAndNonSquareSolutions() throws Exception {
        byte[] raw;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/cytometry/compensation-controls.tsv")) { assertNotNull(input); raw = input.readAllBytes(); }
        assertEquals("808f7fe817c4c36df8cb79b354f750ee54f5fbbe804a419babcbb60630d3e04d", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        int count = 0;
        for (String line : new String(raw, StandardCharsets.UTF_8).lines().filter(s -> !s.startsWith("#")).toList()) {
            String[] f = line.split("\t", -1); assertEquals(10, f.length); String id = f[0]; int m = Integer.parseInt(f[2]), n = Integer.parseInt(f[3]);
            List<String> detectors = IntStream.range(0,m).mapToObj(i -> "D"+i).toList(), signals = IntStream.range(0,n).mapToObj(i -> "F"+i).toList();
            double[] flat = doubles(f[7]); int rows = f[1].equals("spectrum") ? n : m, columns = f[1].equals("spectrum") ? m : n; double[][] matrix = new double[rows][columns];
            for (int i = 0; i < rows; i++) System.arraycopy(flat,i*columns,matrix[i],0,columns);
            CytometryCompensation model = f[1].equals("spectrum") ? CytometryCompensation.spectrum(detectors,signals,matrix,Double.parseDouble(f[4])) : CytometryCompensation.alreadyInverted(detectors,signals,matrix);
            assertEquals(Integer.parseInt(f[5]), model.diagnostics().rank(), id); double[] expected = doubles(f[9]), measured = doubles(f[8]);
            double[] observed = model.apply(measured); double condition = Double.parseDouble(f[6]);
            for (int j = 0; j < n; j++) assertEquals(expected[j],observed[j],2e-12*(1+condition)*(1+Math.abs(expected[j])),id+" channel "+j);
            assertArrayEquals(doubles(f[8]),measured); count++;
        }
        assertEquals(88,count);
    }
    @Test void compensationRefusesUnresolvedSquareModelsAndRetainsNegativeResults() {
        var names = List.of("X","Y");
        assertThrows(IllegalArgumentException.class, () -> CytometryCompensation.spectrum(names,names,new double[][]{{1,2},{2,4}},1e-12));
        assertThrows(IllegalArgumentException.class, () -> CytometryCompensation.spectrum(List.of("X","X"),names,new double[][]{{1,0},{0,1}},1e-12));
        assertThrows(IllegalArgumentException.class, () -> CytometryCompensation.spectrum(names,names,new double[][]{{Double.NaN,0},{0,1}},1e-12));
        double[][] input = {{1,.1},{.2,1}}; var model = CytometryCompensation.spectrum(names,names,input,1e-12); double[] expected = model.apply(new double[]{1,10}); assertTrue(expected[0]<0);
        input[0][0]=1000; double[] copy = model.inverse(); Arrays.fill(copy,0); assertArrayEquals(expected,model.apply(new double[]{1,10}));
        assertThrows(UnsupportedOperationException.class, () -> model.detectors().clear());
        assertThrows(IllegalArgumentException.class, () -> model.apply(new double[]{Double.POSITIVE_INFINITY,1}));
        assertThrows(IllegalArgumentException.class, () -> model.apply(new double[]{1}));
    }
    @Test void exactGeometricBoundaryRulesIncludeNonSimplePolygons() {
        var rectangle = CytometryGates.rectangle(new double[]{0,Double.NEGATIVE_INFINITY},new double[]{1,5});
        assertTrue(rectangle.contains(new double[]{0,-100})); assertFalse(rectangle.contains(new double[]{1,0})); assertFalse(rectangle.contains(new double[]{.5,5}));
        var polygon = CytometryGates.polygon(new double[][]{{0,0},{2,0},{2,2},{0,2}});
        for (double[] p : List.of(new double[]{0,0},new double[]{1,0},new double[]{2,1},new double[]{1,2},new double[]{1,1})) assertTrue(polygon.contains(p));
        assertFalse(polygon.contains(new double[]{Math.nextUp(2.),1})); assertFalse(polygon.contains(new double[]{-Double.MIN_VALUE,1}));
        var crossing = CytometryGates.polygon(new double[][]{{0,0},{2,2},{0,2},{2,0}});
        assertTrue(crossing.contains(new double[]{1,1})); assertTrue(crossing.contains(new double[]{1,.25})); assertFalse(crossing.contains(new double[]{.25,1}));
        var ellipse = CytometryGates.ellipsoid(new double[]{0,0},new double[][]{{4,0},{0,1}},1);
        assertTrue(ellipse.contains(new double[]{2,0})); assertTrue(ellipse.contains(new double[]{0,1})); assertFalse(ellipse.contains(new double[]{Math.nextUp(2.),0}));
        assertThrows(IllegalArgumentException.class, () -> CytometryGates.ellipsoid(new double[]{0,0},new double[][]{{1,1},{0,1}},1));
        assertThrows(IllegalArgumentException.class, () -> CytometryGates.ellipsoid(new double[]{0,0},new double[][]{{1,2},{2,1}},1));
        assertThrows(IllegalArgumentException.class, () -> polygon.contains(new double[]{Double.NaN,1}));
    }
    @Test void transformsRetainTheirDomainAndNeverClipToDisplay() {
        List<CytometryTransforms.Scale> scales = List.of(CytometryTransforms.linear(1000,100),CytometryTransforms.arcsinh(1000,4,1),CytometryTransforms.logicle(1000,1,4,.5),CytometryTransforms.hyperlog(1000,1,4,.5));
        for (var scale : scales) for (double x : new double[]{-1000,-1,0,.01,1,1000,1000000}) assertEquals(x,scale.inverse(scale.apply(x)),3e-11*(1+Math.abs(x)));
        assertTrue(CytometryTransforms.linear(1000,0).apply(-1)<0); assertTrue(CytometryTransforms.linear(1000,0).apply(1001)>1);
        assertEquals(.5,CytometryTransforms.linear(100,100).apply(0)); assertEquals(1,CytometryTransforms.linear(100,100).apply(100));
        var logarithm = CytometryTransforms.logarithmic(1000,4); assertEquals(1,logarithm.apply(1000),1e-15); assertThrows(ArithmeticException.class, () -> logarithm.apply(0));
        assertThrows(IllegalArgumentException.class, () -> CytometryTransforms.arcsinh(1000,4,5));
        assertThrows(IllegalArgumentException.class, () -> CytometryTransforms.logicle(Double.NaN,1,4,0));
        assertEquals(2.5,CytometryTransforms.ratio(10,25,10,5,5)); assertThrows(ArithmeticException.class, () -> CytometryTransforms.ratio(10,5,1,0,5));
    }
    private Path xml(String body) throws IOException {
        Path file = Files.createTempFile(temporary,"gates-",".xml");
        return Files.writeString(file,"<g:Gating-ML xmlns:g=\""+GatingMl20.GATING+"\" xmlns:d=\"http://www.isac-net.org/std/Gating-ML/v2.0/datatypes\" xmlns:t=\"http://www.isac-net.org/std/Gating-ML/v2.0/transformations\">"+body+"</g:Gating-ML>",StandardCharsets.UTF_8);
    }
    private GatingMl20 program(String body) throws IOException { return GatingMl20.read(xml(body),List.of("X","Y"),GatingMl20.FcsCompensation.absent(),1e-12,GatingMl20.Limits.defaults()); }
    private static String range(String id,String parent,String name,String minimum,String maximum) {
        return "<g:RectangleGate g:id=\""+id+"\""+(parent==null?"":" g:parent_id=\""+parent+"\"")+"><g:dimension g:compensation-ref=\"uncompensated\" g:min=\""+minimum+"\" g:max=\""+maximum+"\"><d:fcs-dimension d:name=\""+name+"\"/></g:dimension></g:RectangleGate>";
    }
    @Test void xmlParentsBooleanComplementsRatiosAndUnknownGraphsAreExplicit() throws Exception {
        String expression = "<g:BooleanGate g:id=\"Both\" g:parent_id=\"Parent\"><g:and><g:gateReference g:ref=\"Child\"/><g:gateReference g:ref=\"Outside\" g:use-as-complement=\"true\"/></g:and></g:BooleanGate>"
            +range("Child","Parent","Y","0","10")+range("Parent",null,"X","0","10")+range("Outside",null,"X","20","30");
        GatingMl20 plan = program(expression); boolean[] inside = new boolean[4]; plan.evaluator().evaluate(new double[]{1,2},inside); assertArrayEquals(new boolean[]{true,true,true,false},inside);
        plan.evaluator().evaluate(new double[]{-1,2},inside); assertArrayEquals(new boolean[]{false,false,false,false},inside);
        assertThrows(IOException.class, () -> program(range("A","B","X","0","10")+range("B","A","X","0","10")));
        assertThrows(IOException.class, () -> program(range("A",null,"MISSING","0","10")));
        assertThrows(IOException.class, () -> program(range(" A ",null,"X","0","10")));
        assertThrows(IOException.class, () -> program(range("A",null,"X","10","0")));
        assertThrows(IOException.class, () -> program(range("A",null,"X","0","10")+range("A",null,"Y","0","10")));
        String ratio = "<t:transformation t:id=\"Ratio\"><t:fratio t:A=\"1\" t:B=\"0\" t:C=\"0\"><d:fcs-dimension d:name=\"X\"/><d:fcs-dimension d:name=\"Y\"/></t:fratio></t:transformation><g:RectangleGate g:id=\"R\"><g:dimension g:compensation-ref=\"uncompensated\" g:min=\"1\" g:max=\"3\"><d:new-dimension d:transformation-ref=\"Ratio\"/></g:dimension></g:RectangleGate>";
        var evaluator = program(ratio).evaluator(); boolean[] result={false}; evaluator.evaluate(new double[]{4,2},result); assertTrue(result[0]);
        result[0]=true; assertThrows(ArithmeticException.class, () -> evaluator.evaluate(new double[]{4,0},result)); assertTrue(result[0],"A failed event must not publish partial membership");
    }
    @Test void originalFileCompensationLeavesUnlistedChannelsUnchanged() throws Exception {
        var matrix = CytometryCompensation.spectrum(List.of("Y"),List.of("Y"),new double[][]{{2}},1e-12);
        String body=range("A",null,"X","1","2").replace("uncompensated","FCS");
        GatingMl20 plan=GatingMl20.read(xml(body),List.of("X","Y"),GatingMl20.FcsCompensation.available(matrix),1e-12,GatingMl20.Limits.defaults());
        boolean[] inside={false}; plan.evaluator().evaluate(new double[]{1,100},inside); assertTrue(inside[0]);
        assertThrows(GatingMl20.UnsupportedDefinitionException.class, () -> GatingMl20.read(xml(body),List.of("X","Y"),GatingMl20.FcsCompensation.unavailable("Original compensation form is not supported"),1e-12,GatingMl20.Limits.defaults()));
    }
    private static double[] doubles(String text) { return Arrays.stream(text.split(",",-1)).mapToDouble(Double::parseDouble).toArray(); }
    @Test void quadrantsUseInclusiveLowerCutsAndDoNotExposeTheirGroupAsAPopulation()throws Exception {
        String body="<g:QuadrantGate g:id=\"Group\"><g:divider g:id=\"CutX\" g:compensation-ref=\"uncompensated\"><d:fcs-dimension d:name=\"X\"/><g:value>0</g:value></g:divider><g:Quadrant g:id=\"Below\"><g:position g:divider_ref=\"CutX\" g:location=\"-1\"/></g:Quadrant><g:Quadrant g:id=\"Above\"><g:position g:divider_ref=\"CutX\" g:location=\"1\"/></g:Quadrant></g:QuadrantGate>";
        var plan=program(body);assertEquals(List.of("Below","Above"),plan.gates().stream().map(GatingMl20.Gate::id).toList());boolean[] result=new boolean[2];var evaluator=plan.evaluator();evaluator.evaluate(new double[]{0,0},result);assertArrayEquals(new boolean[]{false,true},result);evaluator.evaluate(new double[]{-Double.MIN_VALUE,0},result);assertArrayEquals(new boolean[]{true,false},result);
        assertThrows(IOException.class,()->program(body.replace("g:location=\"-1\"","g:location=\"0\"")));
        assertThrows(IOException.class,()->program(body+range("Child","Group","Y","0","1")));
        String duplicateDivider="<g:divider g:id=\"CutXagain\" g:compensation-ref=\"uncompensated\"><d:fcs-dimension d:name=\"X\"/><g:value>1</g:value></g:divider>";
        assertThrows(IOException.class,()->program(body.replace("<g:Quadrant g:id=\"Below\">",duplicateDivider+"<g:Quadrant g:id=\"Below\">")));
    }
    @Test void externalEntitiesAndCapacityExcessRefuseTheWholeDefinition()throws Exception {
        Path valid=xml(range("A",null,"X","0","1"));Path attack=temporary.resolve("xxe.xml");Files.writeString(attack,"<!DOCTYPE x [<!ENTITY exfil SYSTEM \"file:///never-read-this\">]>"+Files.readString(valid).replace("d:name=\"X\"","d:name=\"&exfil;\""));
        assertThrows(IOException.class,()->GatingMl20.read(attack,List.of("X","Y"),GatingMl20.FcsCompensation.absent(),1e-12,GatingMl20.Limits.defaults()));
        assertThrows(IOException.class,()->GatingMl20.read(valid,List.of("X","Y"),GatingMl20.FcsCompensation.absent(),1e-12,new GatingMl20.Limits(1,4096,128,100000)));
        Path two=xml(range("A",null,"X","0","1")+range("B",null,"Y","0","1"));assertThrows(IOException.class,()->GatingMl20.read(two,List.of("X","Y"),GatingMl20.FcsCompensation.absent(),1e-12,new GatingMl20.Limits(16384,1,128,100000)));
    }
    @Test void namedSpectraBindRectangularAndLiteralInverseAxesWithoutRenaming()throws Exception {
        String axes="<t:fluorochromes><d:fcs-dimension d:name=\"F1\"/><d:fcs-dimension d:name=\"F2\"/></t:fluorochromes><t:detectors><d:fcs-dimension d:name=\"X\"/><d:fcs-dimension d:name=\"Y\"/><d:fcs-dimension d:name=\"Z\"/></t:detectors>";
        String gate=range("A",null,"F1","2.5","3.5").replace("uncompensated","Model");
        double[][][] matrices={{{1,0,1},{0,1,1}},{{2./3,-1./3},{-1./3,2./3},{1./3,1./3}}};
        for(int variant=0;variant<2;variant++) {
            StringBuilder matrix=new StringBuilder("<t:spectrumMatrix t:id=\"Model\" t:matrix-inverted-already=\"").append(variant==1).append("\">").append(axes);
            for(double[] row:matrices[variant]) {matrix.append("<t:spectrum>");for(double x:row)matrix.append("<t:coefficient t:value=\"").append(x).append("\"/>");matrix.append("</t:spectrum>");}matrix.append("</t:spectrumMatrix>");
            var plan=GatingMl20.read(xml(matrix+gate),List.of("X","Y","Z"),GatingMl20.FcsCompensation.absent(),1e-12,GatingMl20.Limits.defaults());boolean[] inside={false};plan.evaluator().evaluate(new double[]{3,-1,2},inside);assertTrue(inside[0]);assertEquals(variant==0?2:-1,plan.compensationDiagnostics().get(0).rank());
            assertThrows(IOException.class,()->GatingMl20.read(xml(matrix+gate.replace("d:name=\"F1\"","d:name=\"X\"")),List.of("X","Y","Z"),GatingMl20.FcsCompensation.absent(),1e-12,GatingMl20.Limits.defaults()));
        }
        String deficient="<t:spectrumMatrix t:id=\"Model\">"+axes+"<t:spectrum><t:coefficient t:value=\"1\"/><t:coefficient t:value=\"0\"/><t:coefficient t:value=\"0\"/></t:spectrum><t:spectrum><t:coefficient t:value=\"2\"/><t:coefficient t:value=\"0\"/><t:coefficient t:value=\"0\"/></t:spectrum></t:spectrumMatrix>";
        var plan=GatingMl20.read(xml(deficient+gate),List.of("X","Y","Z"),GatingMl20.FcsCompensation.absent(),1e-12,GatingMl20.Limits.defaults());assertEquals(1,plan.compensationDiagnostics().get(0).rank());assertTrue(plan.warnings().stream().anyMatch(s->s.contains("not identifiable")));
    }
}
