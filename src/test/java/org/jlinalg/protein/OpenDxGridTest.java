/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import org.jlinalg.protein.MolecularGeometry.Point;
import org.junit.jupiter.api.Test;

class OpenDxGridTest {
    private static final String HEADER="""
        # Values have explicitly caller-declared units
        object 1 class gridpositions counts 2 3 4
        origin 10 -2 3
        delta 1 1 0
        delta -1 1 0
        delta .25 -.5 2
        object 2 class gridconnections counts 2 3 4
        object 3 class array type double rank 0 items 24 data follows
        """;
    private static final String FOOTER="""
        attribute "dep" string "positions"
        object "regular positions regular connections" class field
        component "positions" value 1
        component "connections" value 2
        component "data" value 3
        """;
    private static String data(){var out=new StringBuilder();for(int i=0;i<2;i++)for(int j=0;j<3;j++)for(int k=0;k<4;k++)out.append(100*i+10*j+k).append('\n');return out.toString();}
    private static OpenDxGrid grid()throws Exception {return OpenDxGrid.read(new StringReader(HEADER+data()+FOOTER),100);}
    @Test void noncubicOrderIsZFastestAndAllNodesRetainValues()throws Exception {
        var grid=grid();assertEquals(24,grid.gridPoints());assertEquals(2,grid.nx());assertEquals(3,grid.ny());assertEquals(4,grid.nz());
        for(int i=0;i<2;i++)for(int j=0;j<3;j++)for(int k=0;k<4;k++)assertEquals(100*i+10*j+k,grid.value(i,j,k));
        assertThrows(IndexOutOfBoundsException.class,()->grid.value(2,0,0));
    }
    @Test void affineSkewInterpolationMatchesIndependentLinearTruth()throws Exception {
        var grid=grid();var sample=grid.sample(new Point(9.425,-1.65,8),OpenDxGrid.Outside.ERROR);assertTrue(sample.inside());assertEquals(36.5,sample.value(),1e-12);assertEquals(.2,sample.fractionalIndex().x(),1e-13);assertEquals(1.4,sample.fractionalIndex().y(),1e-13);assertEquals(2.5,sample.fractionalIndex().z(),1e-13);
    }
    @Test void negativeAxisDirectionsAreNotSilentlyReordered()throws Exception {
        var grid=OpenDxGrid.read(new StringReader(HEADER.replace("origin 10 -2 3","origin 10 20 30").replace("delta 1 1 0","delta -2 0 0").replace("delta -1 1 0","delta 0 3 0").replace("delta .25 -.5 2","delta 0 0 -4")+data()+FOOTER),100);
        assertEquals(61.5,grid.sample(new Point(9,23,24),OpenDxGrid.Outside.ERROR).value(),1e-12);assertEquals(new Point(8,26,18),grid.coordinate(1,2,3));
    }
    @Test void closedCornersAndNodesHaveExactStoredValues()throws Exception {
        var grid=grid();for(int i=0;i<2;i++)for(int j=0;j<3;j++)for(int k=0;k<4;k++){var sample=grid.sample(grid.coordinate(i,j,k),OpenDxGrid.Outside.ERROR);assertTrue(sample.inside());assertEquals(grid.value(i,j,k),sample.value(),1e-12);}
    }
    @Test void outsidePolicyRetainsMissingWithoutExtrapolationOrZeroFilling()throws Exception {
        var grid=grid();var missing=grid.sample(new Point(100,100,100),OpenDxGrid.Outside.MISSING);assertFalse(missing.inside());assertTrue(Double.isNaN(missing.value()));assertThrows(IllegalArgumentException.class,()->grid.sample(new Point(100,100,100),OpenDxGrid.Outside.ERROR));
    }
    @Test void fractionalIndicesAreNotWorldCoordinateBounds() {
        var grid=OpenDxGrid.of(2,2,2,new Point(0,0,0),new Point[]{new Point(.1,0,0),new Point(0,.1,0),new Point(0,0,.1)},new double[8]);var missing=grid.sample(new Point(1e9,0,0),OpenDxGrid.Outside.MISSING);assertFalse(missing.inside());assertEquals(1e10,missing.fractionalIndex().x());
    }
    @Test void zeroAndConstantGridsHaveValidZeroDispersion() {
        double[] values=new double[8];var axes=new Point[]{new Point(1,0,0),new Point(0,1,0),new Point(0,0,1)};var zero=OpenDxGrid.of(2,2,2,new Point(0,0,0),axes,values);assertEquals(0,zero.statistics().mean());assertEquals(0,zero.statistics().populationStandardDeviation());assertEquals(0,zero.sample(new Point(.5,.5,.5),OpenDxGrid.Outside.ERROR).value());java.util.Arrays.fill(values,4);var constant=OpenDxGrid.of(2,2,2,new Point(0,0,0),axes,values);assertEquals(4,constant.statistics().minimum());assertEquals(4,constant.statistics().mean());assertEquals(0,constant.statistics().populationStandardDeviation());
    }
    @Test void constructorsAndAccessorsDefendArrays() {
        double[] values=new double[8];Point[] axes={new Point(1,0,0),new Point(0,1,0),new Point(0,0,1)};var grid=OpenDxGrid.of(2,2,2,new Point(0,0,0),axes,values);values[0]=7;axes[0]=new Point(7,0,0);grid.deltas()[0]=new Point(8,0,0);assertEquals(0,grid.value(0,0,0));assertEquals(new Point(1,0,0),grid.deltas()[0]);
    }
    @Test void singularAndNearlyCoplanarGeometryFails() {
        for(Point second:new Point[]{new Point(2,0,0),new Point(1,1e-15,0),new Point(0,0,0)})assertThrows(IllegalArgumentException.class,()->OpenDxGrid.of(2,2,2,new Point(0,0,0),new Point[]{new Point(1,0,0),second,new Point(0,0,1)},new double[8]));
    }
    @Test void malformedCountsReferencesRankAndFooterFailBeforeSampling() {
        for(String source:new String[]{HEADER.replace("gridconnections counts 2 3 4","gridconnections counts 2 4 3")+data()+FOOTER,HEADER.replace("items 24","items 25")+data()+FOOTER,HEADER.replace("rank 0","rank 1 shape 1")+data()+FOOTER,HEADER+data()+FOOTER.replace("component \"data\" value 3","component \"data\" value 2"),HEADER+data()+FOOTER.replace("object \"regular positions regular connections\" class field","object 1 class field"),HEADER+data()+FOOTER.replace("attribute \"dep\" string \"positions\"\n","")})assertThrows(IOException.class,()->OpenDxGrid.read(new StringReader(source),100));
    }
    @Test void truncatedExtraAndNonfiniteValuesAreRejected() {
        for(String body:new String[]{"1 2\n",data()+"4\n",data().replaceFirst("100","NaN"),data().replaceFirst("100","-nan(ind)"),data().replaceFirst("100","Infinity")})assertThrows(IOException.class,()->OpenDxGrid.read(new StringReader(HEADER+body+FOOTER),100));
    }
    @Test void shapeAndMemoryBoundsApplyBeforeAllocation() {
        assertThrows(IOException.class,()->OpenDxGrid.read(new StringReader(HEADER+data()+FOOTER),8));assertThrows(IOException.class,()->OpenDxGrid.read(new StringReader(HEADER.replace("2 3 4","2147483647 2147483647 2147483647")+data()+FOOTER),100));assertThrows(IllegalArgumentException.class,()->OpenDxGrid.read(new StringReader(HEADER+data()+FOOTER),1));assertThrows(IllegalArgumentException.class,()->OpenDxGrid.of(1,2,2,new Point(0,0,0),new Point[]{new Point(1,0,0),new Point(0,1,0),new Point(0,0,1)},new double[4]));
    }
    @Test void storageTypesControlFloat32RoundingAndOverflow()throws Exception {
        String values=data().replaceFirst("100", "16777217");var single=OpenDxGrid.read(new StringReader(HEADER.replace("type double","type float")+values+FOOTER),100);var full=OpenDxGrid.read(new StringReader(HEADER+values+FOOTER),100);assertEquals("float",single.scalarType());assertEquals(16777216,single.value(1,0,0));assertEquals(16777217,full.value(1,0,0));assertThrows(IOException.class,()->OpenDxGrid.read(new StringReader(HEADER.replace("type double","type float")+data().replaceFirst("100","1e40")+FOOTER),100));
    }
    @Test void explicitCoordinateScalingRetainsScalarsAndWhitespaceParsing()throws Exception {
        var grid=OpenDxGrid.read(new StringReader((HEADER+data()+FOOTER).replace("origin ","origin\t").replace("delta ","delta\t")),100);var scaled=grid.scaleCoordinates(10);assertEquals(new Point(100,-20,30),scaled.origin());assertEquals(36.5,scaled.sample(new Point(94.25,-16.5,80),OpenDxGrid.Outside.ERROR).value(),1e-12);assertEquals(grid.value(1,1,1),scaled.value(1,1,1));assertThrows(IllegalArgumentException.class,()->grid.scaleCoordinates(0));
    }
    private static Properties properties()throws Exception {var properties=new Properties();try(var input=OpenDxGridTest.class.getResourceAsStream("/org/jlinalg/protein/apbs-grid/reference.properties")){assertNotNull(input);properties.load(input);}return properties;}
    static byte[] original(String name,Properties properties,String key)throws Exception {byte[] compressed;try(var input=OpenDxGridTest.class.getResourceAsStream("/org/jlinalg/protein/apbs-grid/"+name)){assertNotNull(input);compressed=input.readAllBytes();}assertEquals(properties.getProperty(key),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compressed)));try(var gzip=new GZIPInputStream(new ByteArrayInputStream(compressed))){return gzip.readAllBytes();}}
    @Test void all549250OriginalFiniteNodesMatchIndependentBinaryTruth()throws Exception {
        Properties properties=properties();for(String name:new String[]{"sphere-q1-65","protein-lpbe-salt"}) {
            byte[] raw=original(name+".dx.gz",properties,name+".compressed_sha256");OpenDxGrid grid;try(var reader=new InputStreamReader(new ByteArrayInputStream(raw),StandardCharsets.US_ASCII)){grid=OpenDxGrid.read(reader,300000);}
            assertEquals(274625,grid.gridPoints());var digest=MessageDigest.getInstance("SHA-256");var buffer=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            for(int x=0;x<grid.nx();x++)for(int y=0;y<grid.ny();y++)for(int z=0;z<grid.nz();z++){buffer.putDouble(0,grid.value(x,y,z));digest.update(buffer.array());}
            assertEquals(properties.getProperty(name+".float64_le_sha256"),HexFormat.of().formatHex(digest.digest()));assertEquals(Double.parseDouble(properties.getProperty(name+".minimum")),grid.statistics().minimum());assertEquals(Double.parseDouble(properties.getProperty(name+".maximum")),grid.statistics().maximum());assertEquals(Double.parseDouble(properties.getProperty(name+".arithmetic_mean")),grid.statistics().mean(),1e-11);
        }
    }
    @Test void originalZeroRhsNaNOutputIsRejectedDespiteSolverExitZero()throws Exception {
        var properties=properties();assertTrue(Integer.parseInt(properties.getProperty("sphere-q0-65.invalid_scalar_count"))>0);byte[] raw=original("sphere-q0-65.dx.gz",properties,"sphere-q0-65.compressed_sha256");assertTrue(assertThrows(IOException.class,()->OpenDxGrid.read(new InputStreamReader(new ByteArrayInputStream(raw),StandardCharsets.US_ASCII),300000)).getMessage().contains("nonfinite"));
    }
}
