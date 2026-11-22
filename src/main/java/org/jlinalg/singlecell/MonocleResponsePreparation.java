/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

/** Explicit response arithmetic; no graph, size-factor estimation, model fitting or feature filtering. */
public final class MonocleResponsePreparation {
    public enum Transform { SIZE_FACTOR_LOG10_PLUS_POINT_ONE, AS_IS }
    public static final long DEFAULT_MAXIMUM_OPERATIONS=500_000_000L;
    private MonocleResponsePreparation() { }

    public static Result prepare(double[][] expression,double[] sizeFactors,double[] detectedGenes,
            Transform transform,long maximumOperations) {
        require(transform!=null,"Choose the response transform explicitly");
        require(expression!=null&&expression.length>0&&expression.length<=100000,"Require a complete1..100000-gene expression population");
        require(expression[0]!=null&&expression[0].length>0&&expression[0].length<=1000000,"Require a complete1..1000000-cell axis");
        int genes=expression.length,cells=expression[0].length;
        require((long)genes*cells<=16000000,"Complete expression matrix exceeds16000000 entries; no prefix is admitted");
        require(maximumOperations>0&&maximumOperations<=2_000_000_000L,"Declare an operation limit in1..2000000000");
        long operations=(long)genes*cells*(transform==Transform.AS_IS?1:4)+(detectedGenes==null?0:cells);
        require(operations<=maximumOperations,"Complete response preparation exceeds declared operation capacity");
        if(transform==Transform.AS_IS)require(sizeFactors==null,"AS_IS accepts no ignored size factors");
        else {
            require(sizeFactors!=null&&sizeFactors.length==cells,"Supply the complete cell-matched size factors; none are estimated");
            for(double value:sizeFactors)require(Double.isFinite(value)&&value>0,"Size factors must be finite and positive; no repair is performed");
        }
        double[] logDetected=null;
        if(detectedGenes!=null) {
            require(detectedGenes.length==cells,"Supplied detected-gene metadata must match the complete cell axis");logDetected=new double[cells];
            for(int cell=0;cell<cells;cell++) {
                double value=detectedGenes[cell];require(Double.isFinite(value)&&value>0&&value<=9007199254740992.0&&value==StrictMath.rint(value),"Detected-gene metadata requires positive exactly represented counts; do not recompute it from selected rows");
                logDetected[cell]=StrictMath.log(value);
            }
        }
        double[][] responses=new double[genes][cells];
        for(int gene=0;gene<genes;gene++) {
            require(expression[gene]!=null&&expression[gene].length==cells,"Complete rectangular expression matrix required");
            for(int cell=0;cell<cells;cell++) {
                double value=expression[gene][cell];require(Double.isFinite(value),"Missing/nonfinite expression is refused, not removed");
                if(transform!=Transform.AS_IS) {
                    require(value>=0,"Size-factor/log response preparation requires nonnegative supplied expression");
                    value=StrictMath.log10(value/sizeFactors[cell]+0.1);
                    if(!Double.isFinite(value))throw new ArithmeticException("Original response arithmetic is nonfinite at zero-based gene"+gene+", cell"+cell+"; no value is replaced");
                }
                responses[gene][cell]=value;
            }
        }
        return new Result(transform,responses,logDetected,operations);
    }

    /** Owns its complete outputs; array getters return copies. */
    public static final class Result {
        private final Transform transform;
        private final double[][] responses;
        private final double[] logDetected;
        private final long operations;
        private Result(Transform transform,double[][] responses,double[] logDetected,long operations) {
            this.transform=transform;this.responses=responses;this.logDetected=logDetected;this.operations=operations;
        }
        public Transform transform(){return transform;}
        public int genes(){return responses.length;}
        public int cells(){return responses[0].length;}
        public long operations(){return operations;}
        public double[] response(int gene){return responses[gene].clone();}
        public double[][] responses(){double[][] copy=new double[responses.length][];for(int i=0;i<copy.length;i++)copy[i]=responses[i].clone();return copy;}
        public double[] logDetectedGenes(){return logDetected==null?null:logDetected.clone();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
