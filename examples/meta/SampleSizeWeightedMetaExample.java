/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import org.jlinalg.meta.SampleSizeWeightedMetaAnalysis;

/** Synthetic, already aligned independent-cohort statistics; not biological data. */
public final class SampleSizeWeightedMetaExample {
    public static void main(String[] args){
        var result=SampleSizeWeightedMetaAnalysis.fitSignedZ(
                new double[]{2.5,-1.0,3.2},new double[]{1000,2000,1500});
        System.out.println("studies\t"+result.studies());
        System.out.println("Z\t"+result.z());
        System.out.println("P\t"+result.pValue());
        System.out.println("natural_log_P\t"+result.logPValue());
        System.out.println("P_underflow\t"+result.pValueUnderflow());
        System.out.println("Q\t"+result.heterogeneityQ());
        System.out.println("heterogeneity_df\t"+result.heterogeneityDegreesOfFreedom());
        System.out.println("heterogeneity_P\t"+result.heterogeneityPValue());
        System.out.println("I_squared_percent\t"+result.iSquaredPercent());
    }
}
