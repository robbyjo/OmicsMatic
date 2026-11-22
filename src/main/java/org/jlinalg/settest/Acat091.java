/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import java.util.ArrayList;
import java.util.List;

/** Selected checked ACAT0.91 matrix-combination behavior at upstream e737d7d.
 * Algorithm:Yaowu Liu,yaowuliu/ACAT (GPLv3),R/ACAT.R,ACAT function.
 * R4.6.1 x86 extended column accumulation is explicit;the transform and Cauchy
 * tail use doubles. Legacy {@link Acat} retains its separate stable mathematical
 * API. This class never changes input arrays or repairs zero/one probabilities.
 * Finite probabilities/weights,nonempty rectangular matrices and at most ten
 * million cells form the supported intake. is.check=FALSE is not supplied.
 */
public final class Acat091 {
    public static final String SOURCE_COMMIT="e737d7d188ba6731800c5e6971539ca6a3888405";
    public static final String SOURCE_SHA256="cab2a44bb165ee2da87cb0c57d2170b1f7a5c5e09bc231367d7e74b47f9c28eb";
    public static final long MAXIMUM_CELLS=10_000_000;
    public static final String ZERO_WARNING="There are p-values that are exactly 0!";
    public static final String ONE_WARNING="There are p-values that are exactly 1!";
    private Acat091(){ }

    /** Hard source/intake refusal,including any source warnings emitted first. */
    public static final class Failure extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        private final ArrayList<String> warnings;
        Failure(String message,List<String> warnings){super(message);this.warnings=new ArrayList<>(warnings);}
        public List<String> sourceWarnings(){return List.copyOf(warnings);}
    }

    /** Raw source diagnostic plus explicit admission. pValue refuses unadmitted output. */
    public record Column(double statistic,double rawPValue,String admissionIssue) {
        public boolean admitted(){return admissionIssue==null;}
        public double pValue(){if(!admitted())throw new ArithmeticException(admissionIssue);return rawPValue;}
    }

    public record Result(List<Column> columns,List<String> sourceWarnings) {
        public Result {columns=List.copyOf(columns);sourceWarnings=List.copyOf(sourceWarnings);}
        public boolean admitted(){return columns.stream().allMatch(Column::admitted);}
    }

    public static Result combine(double[] probabilities) {return combine(probabilities,null);}

    public static Result combine(double[] probabilities,double[] weights) {
        if(probabilities==null)throw failure("P-values are required",List.of());
        if(probabilities.length>MAXIMUM_CELLS||weights!=null&&weights.length>MAXIMUM_CELLS)throw failure("ACAT0.91 vector capacity exceeded",List.of());
        double[][] p=new double[probabilities.length][1],w=weights==null?null:new double[weights.length][1];
        for(int i=0;i<p.length;i++)p[i][0]=probabilities[i];
        if(w!=null)for(int i=0;i<w.length;i++)w[i][0]=weights[i];
        return combineMatrix(p,w);
    }

    /** Each column is one combination;weights must have exactly the same shape. */
    public static Result combineMatrix(double[][] probabilities,double[][] weights) {
        var warnings=new ArrayList<String>();int columns=shape(probabilities,"P-values",warnings),rows=probabilities.length;
        if((long)rows*columns>MAXIMUM_CELLS)throw failure("ACAT0.91 matrix capacity exceeded",warnings);
        boolean[] zeros=new boolean[columns],ones=new boolean[columns],positiveInput=new boolean[columns];
        // Source checks every probability before validating weights,including
        // zero-weight rows. Endpoint errors precede both endpoint warnings.
        for(double[] row:probabilities)for(double value:row)if(Double.isNaN(value))throw failure("Cannot have NAs in the p-values!",warnings);
        for(double[] row:probabilities)for(double value:row)if(value<0||value>1)throw failure("P-values must be between 0 and 1!",warnings);
        for(double[] row:probabilities)for(int j=0;j<columns;j++){zeros[j]|=row[j]==0;ones[j]|=row[j]==1;positiveInput[j]|=row[j]>0;}
        for(int j=0;j<columns;j++)if(zeros[j]&&ones[j])throw failure("Cannot have both 0 and 1 p-values in the same column!",warnings);
        if(any(zeros))warnings.add(ZERO_WARNING);if(any(ones))warnings.add(ONE_WARNING);
        double[] sums=new double[columns];
        if(weights!=null) {
            int wc=shape(weights,"weights",warnings);if(wc!=columns||weights.length!=rows)throw failure("The dimensions of weights and Pvals must be the same!",warnings);
            for(double[] row:weights)for(double value:row)if(!Double.isFinite(value))throw failure("Finite weights are required for admitted checked ACAT0.91 intake",warnings);
            for(double[] row:weights)for(double value:row)if(value<0)throw failure("All the weights must be nonnegative!",warnings);
            for(int j=0;j<columns;j++){var sum=new RLongDoubleSum();for(double[] row:weights)sum.add(row[j]);sums[j]=sum.value();}
            for(double sum:sums)if(sum<=0)throw failure("At least one weight should be positive in each column!",warnings);
        }
        var results=new ArrayList<Column>();
        for(int j=0;j<columns;j++) {
            var sum=new RLongDoubleSum();boolean termOverflow=false;
            for(int i=0;i<rows;i++) {
                double value=probabilities[i][j],term;
                if(weights==null)term=value<1e-15?(1/value)/Math.PI:StrictMath.tan((.5-value)*Math.PI);
                else {double weight=weights[i][j]/sums[j];term=value<1e-15?(weight/value)/Math.PI:weight*StrictMath.tan((.5-value)*Math.PI);}
                termOverflow|=value>0&&!Double.isFinite(term);sum.add(term);
            }
            double statistic=weights==null?sum.mean(rows):sum.value(),probability=upperCauchy(statistic);String issue=null;
            if(!Double.isFinite(probability)||probability<0||probability>1)issue="Original0.91 arithmetic returns a nonfinite or invalid probability;no complete result admitted";
            else if(weights!=null&&!Double.isFinite(sums[j]))issue="Original0.91 weight sum overflows;normalized weights vanish and its raw probability is retained without admission";
            else if(probability==0&&!zeros[j]&&positiveInput[j]&&(termOverflow||!Double.isFinite(statistic)))issue="Original0.91 arithmetic overflows for positive probabilities;raw numeric zero is retained without admission";
            results.add(new Column(statistic,probability,issue));
        }
        return new Result(results,warnings);
    }

    private static int shape(double[][] matrix,String name,List<String> warnings) {
        if(matrix==null||matrix.length==0||matrix[0]==null||matrix[0].length==0)throw failure("Require a nonempty rectangular "+name+" matrix",warnings);
        int width=matrix[0].length;
        for(double[] row:matrix)if(row==null||row.length!=width)throw failure("Require a nonempty rectangular "+name+" matrix",warnings);
        return width;
    }
    private static boolean any(boolean[] values){for(boolean value:values)if(value)return true;return false;}
    private static Failure failure(String message,List<String> warnings){return new Failure(message,warnings);}
    private static double upperCauchy(double statistic) {
        if(Double.isNaN(statistic))return Double.NaN;
        if(statistic==Double.POSITIVE_INFINITY)return 0;
        if(statistic==Double.NEGATIVE_INFINITY)return 1;
        double x=-statistic;
        if(Math.abs(x)>1){double y=StrictMath.atan(1/x)/Math.PI;return x>0?1-y:-y;}
        return .5+StrictMath.atan(x)/Math.PI;
    }
}
