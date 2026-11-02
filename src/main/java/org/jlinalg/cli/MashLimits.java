/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.jlinalg.shrinkage.MashFit;
import org.jlinalg.shrinkage.MashGaussian;
import org.jlinalg.shrinkage.MashMixtureWeights;

/** Explicit complete native execution, bundle and replay capacities. */
record MashLimits(MashNativeExecution.Limits execution,long bundleBytes,int entries) {
    MashLimits {if(execution==null||bundleBytes<65536||bundleBytes>(8L<<30)||entries<16||entries>20000)throw new IllegalArgumentException("Invalid complete mash bundle capacities");}
    static MashLimits defaults(){return new MashLimits(MashNativeExecution.Limits.defaults(),268_435_456,20000);}
    static MashLimits parse(Object value)throws IOException {
        try {
            Map<?,?> root=keys(value,Set.of("execution","bundleBytes","entries"));
            Map<?,?> execution=keys(root.get("execution"),Set.of("rows","inputBytes","outputBytes","fit"));
            Map<?,?> fit=keys(execution.get("fit"),Set.of("rows","gaussian","weights","cells","ownedBytes","totalGaussianWork","totalWeightCells"));
            Map<?,?> g=keys(fit.get("gaussian"),Set.of("conditions","components","estimatedWork","estimatedOwnedBytes"));
            Map<?,?> w=keys(fit.get("weights"),Set.of("rows","components","cells","evaluatedCells","estimatedOwnedBytes"));
            var gaussian=new MashGaussian.Limits(i(g,"conditions"),i(g,"components"),n(g,"estimatedWork"),n(g,"estimatedOwnedBytes"));
            var weights=new MashMixtureWeights.Limits(i(w,"rows"),i(w,"components"),n(w,"cells"),n(w,"evaluatedCells"),n(w,"estimatedOwnedBytes"));
            var fitting=new MashFit.Limits(i(fit,"rows"),gaussian,weights,n(fit,"cells"),n(fit,"ownedBytes"),n(fit,"totalGaussianWork"),n(fit,"totalWeightCells"));
            return new MashLimits(new MashNativeExecution.Limits(i(execution,"rows"),n(execution,"inputBytes"),n(execution,"outputBytes"),fitting),n(root,"bundleBytes"),i(root,"entries"));
        }catch(IllegalArgumentException|ArithmeticException error){throw new IOException("Invalid explicit mash capacities",error);}
    }
    static Map<?,?> keys(Object value,Set<String> expected)throws IOException {if(!(value instanceof Map<?,?> map)||!map.keySet().equals(expected))throw new IOException("Unexpected/missing native mash JSON fields; expected "+new java.util.TreeSet<>(expected));return map;}
    static long n(Map<?,?> map,String key)throws IOException {Object value=map.get(key);if(!(value instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()!=number.longValue()||number.longValue()<1||number.longValue()>9_007_199_254_740_991L)throw new IOException("Require a positive exact integer capacity: "+key);return number.longValue();}
    static int i(Map<?,?> map,String key)throws IOException {return Math.toIntExact(n(map,key));}
}
