/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FlowJoWorkspaceTest {
    @TempDir Path temporary;
    private int sequence;
    private static final String NS=" xmlns:g=\""+FlowJoWorkspace.G+"\" xmlns:d=\""+FlowJoWorkspace.D+"\" xmlns:t=\""+FlowJoWorkspace.T+"\"";
    private String sample(String id,String transforms,String gates) { return "<Sample><DataSet sampleID=\""+id+"\" uri=\"https://example.invalid/never-load.fcs\"/><Transformations>"+transforms+"</Transformations><SampleNode sampleID=\""+id+"\" name=\"same.fcs\" count=\"3\"><Subpopulations>"+gates+"</Subpopulations></SampleNode></Sample>"; }
    private FlowJoWorkspace read(String samples) throws Exception { return xml("<Workspace version=\"20.0\" flowJoVersion=\"10.8.1\""+NS+"><SampleList>"+samples+"</SampleList></Workspace>"); }
    private FlowJoWorkspace xml(String xml) throws Exception { Path p=temporary.resolve("input"+(sequence++)+".wsp");Files.writeString(p,xml);return FlowJoWorkspace.read(p,FlowJoWorkspace.Limits.defaults()); }
    private String interval(String name,String parameter,String min,String max,String children) { return "<Population name=\""+name+"\" count=\"-1\"><Gate><g:RectangleGate eventsInside=\"1\"><g:dimension g:min=\""+min+"\" g:max=\""+max+"\"><d:fcs-dimension d:name=\""+parameter+"\"/></g:dimension></g:RectangleGate></Gate><Subpopulations>"+children+"</Subpopulations></Population>"; }
    private FlowJoWorkspace.Program compile(FlowJoWorkspace w) throws Exception {return w.compile("1",List.of("X","Y"),List.of(),1e-12);}
    @Test void sampleIDsAreExplicitNamesAndUrisDoNotSelectInputs() throws Exception {
        var w=read(sample("1","",interval("same","X","0","1",""))+sample("2","",interval("same","X","1","2","")));
        assertEquals(2,w.samples().size());assertEquals("https://example.invalid/never-load.fcs",w.samples().get(0).uri());
        assertArrayEquals(new boolean[]{true},compile(w).evaluate(new double[]{0,0}));assertArrayEquals(new boolean[]{false},w.compile("2",List.of("X","Y"),List.of(),1e-12).evaluate(new double[]{0,0}));
        assertThrows(IOException.class,()->w.compile("same.fcs",List.of("X","Y"),List.of(),1e-12));
    }
    @Test void hierarchyAndHalfOpenBoundsPreserveEveryEvent() throws Exception {
        var p=compile(read(sample("1","",interval("parent","X","0","2",interval("child","Y","1","3","")))));
        assertArrayEquals(new boolean[]{true,true},p.evaluate(new double[]{0,1}));assertArrayEquals(new boolean[]{false,false},p.evaluate(new double[]{2,1}));assertArrayEquals(new boolean[]{true,false},p.evaluate(new double[]{1,3}));
        assertThrows(IllegalArgumentException.class,()->p.evaluate(new double[]{Double.NaN,1})); assertThrows(IllegalArgumentException.class,()->p.evaluate(new double[]{1}));
    }
    @Test void dotNamesAndDuplicateLeafNamesRetainLiteralPaths() throws Exception {
        var p=compile(read(sample("1","",interval(".","X","0","1",interval("leaf","Y","0","1",""))+interval("other","X","1","2",interval("leaf","Y","0","1","")))));
        assertEquals(List.of(".","leaf"),p.gates().get(1).path());assertEquals(List.of("other","leaf"),p.gates().get(3).path()); assertArrayEquals(new boolean[]{true,true,false,false},p.evaluate(new double[]{.5,.5}));
    }
    @Test void booleanForwardReferencesAreTopologicallyResolvedAndIntersectParents() throws Exception {
        String bool="<NotNode name=\"not\"><Dependents><Dependent name=\"positive\"/></Dependents></NotNode>";
        var p=compile(read(sample("1","",bool+interval("positive","X","0","1",""))));assertArrayEquals(new boolean[]{false,true},p.evaluate(new double[]{.5,0}));assertArrayEquals(new boolean[]{true,false},p.evaluate(new double[]{-1,0}));
    }
    @Test void booleanCyclesAndMissingDependenciesRefuseTheCompleteTree() throws Exception {
        String cycle="<NotNode name=\"a\"><Dependents><Dependent name=\"b\"/></Dependents></NotNode><NotNode name=\"b\"><Dependents><Dependent name=\"a\"/></Dependents></NotNode>";
        var w=read(sample("1","",cycle));assertThrows(IOException.class,()->compile(w));var absent=read(sample("1","",cycle.replace("name=\"a\"/>","name=\"missing\"/>")));assertThrows(IOException.class,()->compile(absent));
    }
    @Test void secureParserRejectsDoctypeAndExternalEntities() {
        assertThrows(IOException.class,()->xml("<!DOCTYPE Workspace [<!ENTITY xxe SYSTEM 'file:///nonexistent'>]><Workspace version=\"20.0\" flowJoVersion=\"10.8.1\">&xxe;</Workspace>"));
    }
    @Test void unknownNodesAndUnsupportedEditionsAreNeverSilentlyDropped() throws Exception {
        var unknown=read(sample("1","","<UnknownPopulation/>"));assertThrows(FlowJoWorkspace.UnsupportedDefinitionException.class,()->compile(unknown));
        assertThrows(FlowJoWorkspace.UnsupportedDefinitionException.class,()->xml("<Workspace version=\"19.0\" flowJoVersion=\"9.0\"/>"));
        var duplicate=sample("1","","");assertThrows(IOException.class,()->read(duplicate+duplicate));
    }
    @Test void savedStatisticsAreExplicitlyNotComputedAndCannotHideGateChildren() throws Exception {
        var p=compile(read(sample("1","","<Statistic name=\"Median\"/>"+interval("p","X","0","1",""))));assertEquals(1,p.gates().size());assertTrue(p.warnings().stream().anyMatch(s->s.startsWith("NOT COMPUTED:")));
        var bad=read(sample("1","","<Statistic name=\"Median\"><Subpopulations/></Statistic>"));assertThrows(IOException.class,()->compile(bad));
    }
    @Test void explicitLogFloorAndLinearDisplayGainInterpretationAreReported() throws Exception {
        String log="<t:log t:offset=\"1\" t:decades=\"4\"><d:parameter d:name=\"X\"/></t:log>";
        var p=compile(read(sample("1",log,interval("p","X","-10","10",""))));assertArrayEquals(new boolean[]{true},p.evaluate(new double[]{-100,0}));assertArrayEquals(new boolean[]{false},p.evaluate(new double[]{10,0}));assertTrue(p.warnings().stream().anyMatch(s->s.contains("intrinsically")));
        String linear="<t:linear t:minRange=\"0\" t:maxRange=\"10\" gain=\"0.01\"><d:parameter d:name=\"X\"/></t:linear>";var q=compile(read(sample("1",linear,interval("p","X","0","1",""))));assertTrue(q.warnings().stream().anyMatch(s->s.contains("saved display gain=0.01")));assertArrayEquals(new boolean[]{false},q.evaluate(new double[]{1,0}));
    }
    @Test void biexRequiresAnExactExplicitCompleteCalibrationAndNeverUsesLogicleInstead() throws Exception {
        String transform="<t:biex t:length=\"256\" t:neg=\"0\" t:pos=\"4.5\" t:width=\"-10\" t:maxRange=\"262144\"><d:parameter d:name=\"X\"/></t:biex>";var w=read(sample("1",transform,interval("p","X","0","1","")));
        assertThrows(FlowJoWorkspace.UnsupportedDefinitionException.class,()->compile(w));double[] x=new double[4097],y=x.clone();for(int i=0;i<x.length;i++){x[i]=i-100;y[i]=i;}var definition=w.transforms("1").get(0);var c=new FlowJoWorkspace.Calibration(definition,new CytometryLookupTable(x,y),"Independent explicit synthetic calibration control");
        var p=w.compile("1",List.of("X","Y"),List.of(c),1e-12);assertArrayEquals(new boolean[]{true},p.evaluate(new double[]{0,0}));assertArrayEquals(new boolean[]{false},p.evaluate(new double[]{1,0}));
        var bad=new FlowJoWorkspace.Calibration(new FlowJoWorkspace.TransformDefinition("X","biex",Map.of()),c.table(),"Wrong attributes");assertThrows(IOException.class,()->w.compile("1",List.of("X","Y"),List.of(bad),1e-12));
    }
    @Test void lookupInterpolationSaturatesOnlyByItsDeclaredDefinitionAndRejectsNonfiniteKnots() {
        double[] x={-2,0,8},y={0,1,2};var lut=new CytometryLookupTable(x,y);x[1]=100;assertEquals(1.5,lut.apply(4));assertEquals(0,lut.apply(-100));assertEquals(2,lut.apply(100));assertEquals(4,lut.inverse(1.5));assertThrows(IllegalArgumentException.class,()->lut.apply(Double.POSITIVE_INFINITY));assertThrows(IllegalArgumentException.class,()->new CytometryLookupTable(new double[]{1,1},new double[]{0,1}));assertThrows(IllegalArgumentException.class,()->new CytometryLookupTable(new double[]{0,1},new double[]{0,Double.NaN}));
    }
    @Test void ambiguousSlashPathsAndChannelCollisionsAreRefused() throws Exception {
        var w=read(sample("1","",interval("a/b","X","0","1","")+interval("a","X","0","1",interval("b","Y","0","1",""))));assertThrows(IOException.class,()->compile(w));var normal=read(sample("1","",interval("a","X","0","1","")));assertThrows(IOException.class,()->normal.compile("1",List.of("X","X"),List.of(),1e-12));assertThrows(FlowJoWorkspace.UnsupportedDefinitionException.class,()->normal.compile("1",List.of("x","Y"),List.of(),1e-12));
    }
    @Test void workspaceSpectrumHasAnExplicitRowDirectionAndNegativeSignalsAreRetained() throws Exception {
        String spectrum="<t:spilloverMatrix prefix=\"Comp-\" suffix=\"\" spectral=\"0\"><d:parameters><d:parameter d:name=\"X\"/><d:parameter d:name=\"Y\"/></d:parameters><t:spillover d:parameter=\"X\"><t:coefficient d:parameter=\"X\" t:value=\"1\"/><t:coefficient d:parameter=\"Y\" t:value=\"0.1\"/></t:spillover><t:spillover d:parameter=\"Y\"><t:coefficient d:parameter=\"X\" t:value=\"0.2\"/><t:coefficient d:parameter=\"Y\" t:value=\"1\"/></t:spillover></t:spilloverMatrix>";
        String source=sample("1","",interval("negative","Comp-X","-3","0","")); var w=read(source.replace("<Sample>","<Sample>"+spectrum));var p=compile(w);assertArrayEquals(new boolean[]{true},p.evaluate(new double[]{-1,1}));assertArrayEquals(new boolean[]{false},p.evaluate(new double[]{1,2}));assertEquals(2,p.compensationDiagnostics().rank());
        var ambiguous=read(source.replace("<Sample>","<Sample>"+spectrum+spectrum));assertThrows(FlowJoWorkspace.UnsupportedDefinitionException.class,()->compile(ambiguous));
    }
}
