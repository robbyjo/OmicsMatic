/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import jdistlib.accelerator.CpuComputeBackend;
import org.jlinalg.protein.MolecularGeometry.Degeneracy;
import org.jlinalg.protein.MolecularGeometry.Point;
import org.junit.jupiter.api.Test;

class MolecularGeometryTest {
    private static Point p(double x,double y,double z) { return new Point(x,y,z); }
    private static final List<Point> CLOUD=List.of(p(0,0,0),p(1,0,0),p(0,2,0),p(0,0,3),p(1,2,3));
    private static MolecularGeometry.Fit fit(List<Point> a,List<Point> b) {
        return MolecularGeometry.fit(a,b,Degeneracy.ERROR,new CpuComputeBackend());
    }
    private static double determinant(double[][] a) {
        return a[0][0]*(a[1][1]*a[2][2]-a[1][2]*a[2][1])-a[0][1]*(a[1][0]*a[2][2]-a[1][2]*a[2][0])+a[0][2]*(a[1][0]*a[2][1]-a[1][1]*a[2][0]);
    }

    @Test void euclideanDistancesPreserveUnitsAndSymmetry() {
        Point a=p(2,-3,5),b=p(5,1,17);
        assertEquals(13,MolecularGeometry.distance(a,b));
        assertEquals(MolecularGeometry.distance(a,b),MolecularGeometry.distance(b,a));
        assertEquals(0,MolecularGeometry.distance(a,a));
    }
    @Test void anglesUseTheMiddleVertex() {
        Point zero=p(0,0,0),right=p(1,0,0);
        assertEquals(90,MolecularGeometry.angle(right,zero,p(0,7,0)),1e-13);
        assertEquals(180,MolecularGeometry.angle(right,zero,p(-5,0,0)),1e-13);
        assertEquals(0,MolecularGeometry.angle(right,zero,p(5,0,0)),1e-13);
        assertEquals(60,MolecularGeometry.angle(right,zero,p(.5,Math.sqrt(3)/2,0)),1e-13);
    }
    @Test void signedTorsionsRetainHandedness() {
        Point a=p(0,1,0),b=p(0,0,0),c=p(1,0,0);
        assertEquals(90,MolecularGeometry.dihedral(a,b,c,p(1,0,1)),1e-13);
        assertEquals(-90,MolecularGeometry.dihedral(a,b,c,p(1,0,-1)),1e-13);
        assertEquals(0,MolecularGeometry.dihedral(a,b,c,p(1,1,0)),1e-13);
        assertEquals(180,Math.abs(MolecularGeometry.dihedral(a,b,c,p(1,-1,0))),1e-13);
    }
    @Test void undefinedAnglesAndTorsionsFail() {
        assertThrows(IllegalArgumentException.class,()->MolecularGeometry.angle(p(0,0,0),p(0,0,0),p(1,0,0)));
        assertThrows(IllegalArgumentException.class,()->MolecularGeometry.dihedral(p(0,0,0),p(1,0,0),p(2,0,0),p(2,1,0)));
    }
    @Test void identityFitPreservesEveryPair() {
        var result=fit(CLOUD,CLOUD);
        assertEquals(5,result.pairs());assertTrue(result.rotationUnique());
        assertEquals(0,result.rmsd(),1e-14);assertEquals(0,result.initialRmsd());
        assertEquals(1,determinant(result.rotation()),1e-13);
    }
    @Test void quarterTurnTranslationMapsMobileIntoTarget() {
        var target=CLOUD.stream().map(a->p(10-a.y(),20+a.x(),30+a.z())).toList();
        var result=fit(CLOUD,target);
        assertEquals(0,result.rmsd(),1e-13);
        for (int i=0;i<CLOUD.size();i++)assertEquals(0,MolecularGeometry.distance(result.apply(CLOUD.get(i)),target.get(i)),1e-13);
        assertArrayEquals(new double[]{10,20,30},result.translation(),1e-13);
    }
    @Test void halfTurnIsNotLostToQuaternionPowerIteration() {
        var target=CLOUD.stream().map(a->p(a.x(),-a.y(),-a.z())).toList();
        assertEquals(0,fit(CLOUD,target).rmsd(),1e-13);
    }
    @Test void planarNoncollinearPairsDetermineAProperRotation() {
        var a=List.of(p(0,0,0),p(1,0,0),p(0,2,0),p(2,2,0));
        var b=a.stream().map(q->p(q.x(),0,q.y())).toList();
        var result=fit(a,b);assertTrue(result.rotationUnique());
        assertEquals(0,result.rmsd(),1e-13);assertEquals(1,determinant(result.rotation()),1e-13);
    }
    @Test void reflectedStructureCannotObtainAnImproperFit() {
        var reflected=CLOUD.stream().map(a->p(-a.x(),a.y(),a.z())).toList();
        var result=fit(CLOUD,reflected);
        assertTrue(result.rmsd()>.1);assertEquals(1,determinant(result.rotation()),1e-13);
        assertTrue(result.rmsd()<result.initialRmsd());
    }
    @Test void uniformScaleIsNotFittedAway() {
        var expanded=CLOUD.stream().map(a->p(a.x()*2,a.y()*2,a.z()*2)).toList();
        var result=fit(CLOUD,expanded);assertTrue(result.rmsd()>.5);
        assertEquals(1,determinant(result.rotation()),1e-13);
    }
    @Test void collinearOrCollapsedFitsRequireExplicitPermission() {
        var line=List.of(p(0,0,0),p(1,0,0),p(2,0,0));
        assertThrows(IllegalArgumentException.class,()->fit(line,line));
        var allowed=MolecularGeometry.fit(line,line,Degeneracy.ALLOW_NONUNIQUE,new CpuComputeBackend());
        assertFalse(allowed.rotationUnique());assertEquals(0,allowed.rmsd(),1e-13);
        var collapsed=List.of(p(2,3,4),p(2,3,4),p(2,3,4));
        assertThrows(IllegalArgumentException.class,()->fit(collapsed,collapsed));
        var result=MolecularGeometry.fit(collapsed,collapsed,Degeneracy.ALLOW_NONUNIQUE,new CpuComputeBackend());
        assertFalse(result.rotationUnique());assertEquals(0,result.rmsd());
    }
    @Test void residualsRetainOutliersAndTheirRootMeanSquare() {
        var target=new ArrayList<>(CLOUD);target.set(4,p(10,20,30));
        var result=fit(CLOUD,target);assertEquals(5,result.residuals().length);
        double sum=0,maximum=0;
        for(int i=0;i<5;i++) { double observed=MolecularGeometry.distance(result.apply(CLOUD.get(i)),target.get(i));assertEquals(observed,result.residuals()[i],1e-13);sum+=observed*observed;maximum=Math.max(maximum,observed); }
        assertEquals(Math.sqrt(sum/5),result.rmsd(),1e-13);assertEquals(maximum,result.maximumResidual(),1e-13);
    }
    @Test void manyKnownMotionsPreserveDistancesAndFitDirection() {
        var random=new Random(74321);
        for(int trial=0;trial<80;trial++) {
            double angle=random.nextDouble()*2*Math.PI,c=Math.cos(angle),s=Math.sin(angle);
            double tx=random.nextGaussian()*10,ty=random.nextGaussian()*10,tz=random.nextGaussian()*10;
            var a=new ArrayList<Point>();var b=new ArrayList<Point>();
            for(int i=0;i<11;i++) { Point q=p(random.nextGaussian(),random.nextGaussian()*2,random.nextGaussian()*3);a.add(q);b.add(p(c*q.x()-s*q.y()+tx,s*q.x()+c*q.y()+ty,q.z()+tz)); }
            var result=fit(a,b);assertEquals(0,result.rmsd(),2e-13);
            for(int i=0;i<a.size();i++)assertEquals(0,MolecularGeometry.distance(result.apply(a.get(i)),b.get(i)),2e-13);
        }
    }
    @Test void largeCommonOffsetsRetainSubunitGeometry() {
        var a=CLOUD.stream().map(q->p(q.x()+1e8,q.y()-2e8,q.z()+3e8)).toList();
        var b=CLOUD.stream().map(q->p(-q.y()+1e8,q.x()-2e8,q.z()+3e8)).toList();
        var result=fit(a,b);assertEquals(0,result.rmsd(),1e-7);
        for(int i=0;i<a.size();i++)assertEquals(0,MolecularGeometry.distance(result.apply(a.get(i)),b.get(i)),1e-7);
    }
    @Test void arraysAreDefensivelyCopied() {
        var result=fit(CLOUD,CLOUD);result.rotation()[0][0]=100;result.translation()[0]=100;result.residuals()[0]=100;
        assertEquals(0,MolecularGeometry.distance(result.apply(CLOUD.get(1)),CLOUD.get(1)),1e-13);
        assertEquals(0,result.residuals()[0],1e-13);
    }
    @Test void malformedPointsAndPairCountsFail() {
        assertThrows(IllegalArgumentException.class,()->p(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->p(Double.POSITIVE_INFINITY,0,0));
        assertThrows(IllegalArgumentException.class,()->p(1e10,0,0));
        assertThrows(IllegalArgumentException.class,()->fit(CLOUD,CLOUD.subList(0,3)));
        assertThrows(IllegalArgumentException.class,()->fit(CLOUD.subList(0,2),CLOUD.subList(0,2)));
    }
    private static List<String[]> reference(String name) throws Exception {
        var stream=MolecularGeometryTest.class.getResourceAsStream("/org/jlinalg/protein/pymol-geometry/"+name);
        assertNotNull(stream);
        try(var input=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            return input.lines().skip(1).map(line->line.split("\t",-1)).toList();
        }
    }
    private static Point point(String[] fields,int offset) {
        return p(Double.parseDouble(fields[offset]),Double.parseDouble(fields[offset+1]),Double.parseDouble(fields[offset+2]));
    }
    @Test void measurementsMatch128ActualOriginalPyMolCases() throws Exception {
        var rows=reference("measurements.tsv");assertEquals(128,rows.size());
        for(var row:rows) {
            Point a=point(row,1),b=point(row,4),c=point(row,7),d=point(row,10);
            assertEquals(Double.parseDouble(row[13]),MolecularGeometry.distance(a,b),1e-5,"distance case"+row[0]);
            assertEquals(Double.parseDouble(row[14]),MolecularGeometry.angle(a,b,c),5e-5,"angle case"+row[0]);
            assertEquals(Double.parseDouble(row[15]),MolecularGeometry.dihedral(a,b,c,d),5e-5,"torsion case"+row[0]);
        }
    }
    @Test void rigidFitsMatch96ActualOriginalExplicitPairFits() throws Exception {
        var summaries=reference("fit-summary.tsv");var pairs=reference("fit-pairs.tsv");
        assertEquals(96,summaries.size());assertEquals(1335,pairs.size());int offset=0;
        for(var summary:summaries) {
            int count=Integer.parseInt(summary[2]);var mobile=new ArrayList<Point>();var target=new ArrayList<Point>();
            for(int i=0;i<count;i++) {
                var row=pairs.get(offset+i);assertEquals(summary[0],row[0]);assertEquals(i+1,Integer.parseInt(row[1]));
                mobile.add(point(row,2));target.add(point(row,5));
            }
            var result=fit(mobile,target);
            assertEquals(Double.parseDouble(summary[3]),result.rmsd(),1e-5,"original pair_fit case"+summary[0]);
            assertEquals(Double.parseDouble(summary[4]),result.rmsd(),1e-5,"post-coordinate RMSD case"+summary[0]);
            for(int i=0;i<count;i++) {
                var row=pairs.get(offset+i);var actual=result.apply(mobile.get(i));var expected=point(row,8);
                assertEquals(expected.x(),actual.x(),5e-5,"post x case"+summary[0]);
                assertEquals(expected.y(),actual.y(),5e-5,"post y case"+summary[0]);
                assertEquals(expected.z(),actual.z(),5e-5,"post z case"+summary[0]);
                assertEquals(Double.parseDouble(row[11]),result.residuals()[i],5e-5,"residual case"+summary[0]);
            }
            offset+=count;
        }
        assertEquals(pairs.size(),offset);
    }
}
