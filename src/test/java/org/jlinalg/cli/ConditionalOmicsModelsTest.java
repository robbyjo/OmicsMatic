/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.glm.GlmFamilies;
import org.jlinalg.glmm.GlmmLaplace;
import org.jlinalg.glmm.GlmmLaplaceOptions;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;
import org.jlinalg.reml.Reml;
import org.jlinalg.reml.RemlOptions;
import org.jlinalg.reml.VarianceComponent;
import org.junit.jupiter.api.Test;

class ConditionalOmicsModelsTest {
    @Test void subsetsWeightsAndOffsetsByOriginalObservationIds() {
        int[] rows = {7, 2, 4, 1, 9, 5};
        double[] w = {1,2,3,4,5,6,7,8,9,10}, o = {0,.1,.2,.3,.4,.5,.6,.7,.8,.9};
        double[] y = {1,4,2,5,3,8}, x = {1,1,1,3,1,2,1,4,1,0,1,5};
        var actual = ConditionalOmicsModels.independent("ols", GlmFamilies.gaussian(), w, o)
            .fit(y, x, 2, rows, BackendPolicy.CPU);
        var expected = Ols.fit(y, x, 6, 2, new double[] {8,3,5,2,10,6},
            new double[] {.7,.2,.4,.1,.9,.5}, OlsOptions.defaults(), BackendPolicy.CPU).associationStatistics();
        assertArrayEquals(expected.beta(), actual.beta(), 1e-12);
        assertArrayEquals(expected.standardErrors(), actual.standardErrors(), 1e-12);
    }

    @Test void subsetsBothAxesOfRelatednessForLmmAndGlmm() {
        int n = 36;
        double[] covariance = new double[n*n];
        for (int i=0;i<n;i++) for (int j=0;j<n;j++) covariance[i*n+j] = Math.pow(.7, Math.abs(i-j));
        int[] rows = IntStream.range(0,n).filter(i -> i%7 != 0).map(i -> n-1-i).toArray();
        double[] selectedCovariance = new double[rows.length*rows.length], x = new double[rows.length*2], y = new double[rows.length];
        Random random = new Random(412);
        for (int i=0;i<rows.length;i++) {
            x[2*i]=1; x[2*i+1]=random.nextGaussian(); y[i]=.5*x[2*i+1]+random.nextGaussian();
            for(int j=0;j<rows.length;j++) selectedCovariance[i*rows.length+j]=covariance[rows[i]*n+rows[j]];
        }
        var full = new VarianceComponent("kinship",n,covariance);
        var subset = new VarianceComponent("kinship",rows.length,selectedCovariance);
        var expected = Reml.fit(y,x,rows.length,2,List.of(subset,
            VarianceComponent.identity("residual", rows.length)),RemlOptions.defaults(),BackendPolicy.CPU);
        assertTrue(expected.converged());
        var actual = ConditionalOmicsModels.dense("lmm",GlmFamilies.gaussian(),List.of(full),null,null,RemlOptions.defaults())
            .fit(y,x,2,rows,BackendPolicy.CPU);
        assertArrayEquals(expected.beta(),actual.beta(),1e-9);
        assertArrayEquals(expected.associationStatistics().standardErrors(),actual.standardErrors(),1e-9);
        for (int i=0;i<y.length;i++) y[i]=i%3==0?1:0;
        var glm = GlmmLaplace.fit(y,x,rows.length,2,GlmFamilies.binomial(),List.of(subset),null,null,
            GlmmLaplaceOptions.defaults(),BackendPolicy.CPU);
        assertTrue(glm.converged());
        var adjusted = ConditionalOmicsModels.dense("glmm",GlmFamilies.binomial(),List.of(full),null,null,RemlOptions.defaults())
            .fit(y,x,2,rows,BackendPolicy.CPU);
        assertArrayEquals(glm.associationStatistics().beta(),adjusted.beta(),1e-9);
        assertArrayEquals(glm.associationStatistics().standardErrors(),adjusted.standardErrors(),1e-9);
    }
}
