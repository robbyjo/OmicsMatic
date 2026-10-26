/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;
import org.junit.jupiter.api.Test;
class OlsResidualPermutationTest {
    private static final double[] Y={1.2,3.1,.8,5.2,3.9,4.7,2.4,6.1};
    private static final double[][] X={{2,3,1,5,2,4,3,8},{4,1,2,6,3,2,9,1},{2,3,1,5,2,4,3,8}};
    private static double[][] design(){double[][] z=new double[Y.length][2];for(int i=0;i<z.length;i++){z[i][0]=1;z[i][1]=i*.3+(i%3)*.7;}return z;}
    private static double t(double[] y,double[][] z,double[] x){double[][] full=new double[y.length][z[0].length+1];for(int i=0;i<y.length;i++){System.arraycopy(z[i],0,full[i],0,z[i].length);full[i][z[i].length]=x[i];}return Ols.fit(y,full,OlsOptions.defaults(),BackendPolicy.CPU).tStatistics()[z[0].length];}
    @Test void sharedResidualPermutationMatchesDirectFullRefitsWithNuisanceAndStrata(){
        double[][] z=design();var strata=List.of("A","A","A","A","B","B","B","B");var fit=new OlsResidualPermutation(Y,z,X,strata,25,0,17);assertFalse(fit.exhaustive());assertEquals(25,fit.drawCount());assertEquals(5,fit.residualDegreesOfFreedom());
        double[] observed=fit.observedStatistics();for(int j=0;j<X.length;j++)assertEquals(t(Y,z,X[j]),observed[j],1e-12);
        var reduced=Ols.fit(Y,z,OlsOptions.defaults(),BackendPolicy.CPU);double[] e=reduced.residuals(),mu=reduced.fittedValues();Random rng=new Random(17);
        for(int b=0;b<25;b++){int[] order={0,1,2,3,4,5,6,7};for(int start:new int[]{0,4})for(int j=3;j>0;j--){int k=rng.nextInt(j+1),v=order[start+j];order[start+j]=order[start+k];order[start+k]=v;}double[] simulated=new double[Y.length];for(int i=0;i<Y.length;i++)simulated[i]=mu[i]+e[order[i]];double[] stats=fit.nextStatistics();for(int j=0;j<X.length;j++)assertEquals(t(simulated,z,X[j]),stats[j],2e-11);assertEquals(stats[0],stats[2],0,"Duplicated features must stay perfectly dependent in every shared draw");}
        assertThrows(IllegalStateException.class,fit::nextStatistics);
    }
    @Test void exhaustiveStratumProductIncludesIdentityAndEveryState(){
        var strata=List.of("A","A","B","B","C","C","D","D");var fit=new OlsResidualPermutation(Y,design(),X,strata,2,100,1);assertTrue(fit.exhaustive());assertEquals(16,fit.drawCount());assertArrayEquals(fit.observedStatistics(),fit.nextStatistics(),1e-12);for(int b=1;b<16;b++)assertEquals(3,fit.nextStatistics().length);assertThrows(IllegalStateException.class,fit::nextStatistics);
    }
    @Test void monteCarloReplayAndScalingAreInvariant(){
        double[][] z=design();double[] ys=Y.clone();double[][] xs=new double[X.length][];for(int i=0;i<ys.length;i++)ys[i]*=1e200;for(int j=0;j<X.length;j++){xs[j]=X[j].clone();for(int i=0;i<xs[j].length;i++)xs[j][i]*=1e-200;}var a=new OlsResidualPermutation(Y,z,X,null,9,0,42);var b=new OlsResidualPermutation(ys,z,xs,null,9,0,42);assertArrayEquals(a.observedStatistics(),b.observedStatistics(),1e-11);for(int i=0;i<9;i++)assertArrayEquals(a.nextStatistics(),b.nextStatistics(),1e-11);
    }
    @Test void invalidOrUnestimableDesignsDoNotDropDrawsOrFeatures(){
        assertThrows(IllegalArgumentException.class,()->new OlsResidualPermutation(Y,design(),X,List.of("a","b","c","d","e","f","g","h"),10,10,1));
        double[][] rankDeficient=new double[Y.length][2];for(var row:rankDeficient){row[0]=1;row[1]=1;}assertThrows(IllegalArgumentException.class,()->new OlsResidualPermutation(Y,rankDeficient,X,null,10,0,1));
        double[][] constant={{1,1,1,1,1,1,1,1},X[1]};assertThrows(IllegalArgumentException.class,()->new OlsResidualPermutation(Y,design(),constant,null,10,0,1));
        double[][] perfect={Y,X[1]};assertThrows(IllegalArgumentException.class,()->new OlsResidualPermutation(Y,design(),perfect,null,10,0,1));
    }
}
