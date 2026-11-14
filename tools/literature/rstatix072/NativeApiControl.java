/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Arrays;
import java.util.List;
import org.jlinalg.stats.LeveneTest;

/** Public API boundary/invariance checks, independent of the CLI implementation. */
public final class NativeApiControl {
    private NativeApiControl() { }
    public static void main(String[] ignored) {
        int checks=0;
        double[] x={-5,-2,-1,0,2,4,-6,-1,3,8,12,20};
        String[] g={"α","α","α","α","α","α","NA","NA","NA","NA","NA","NA"};
        for(var center:LeveneTest.Center.values()) {
            double[] before=x.clone();String[] names=g.clone();
            var result=LeveneTest.test(x,g,center,LeveneTest.MissingPolicy.FAIL);
            require(Arrays.equals(x,before)&&Arrays.equals(g,names),"Inputs mutated");checks++;
            for(double scale:new double[]{1e-120,0.125,64,1e120}) {
                double[] changed=x.clone();for(int i=0;i<changed.length;i++)changed[i]*=scale;
                var actual=LeveneTest.test(changed,g,center,LeveneTest.MissingPolicy.FAIL);
                close(actual.statistic(),result.statistic());close(actual.pValue(),result.pValue());checks+=2;
            }
            double[] shifted=x.clone();for(int i=0;i<shifted.length;i++)shifted[i]+=0x1.0p40;
            close(LeveneTest.test(shifted,g,center,LeveneTest.MissingPolicy.FAIL).statistic(),result.statistic());checks++;
        }
        var missing=LeveneTest.test(new double[]{1,Double.NaN,2,4,7,8,1,4,6,9},new String[]{"","","","",null,"","NA","NA","NA","NA"},LeveneTest.Center.MEDIAN,LeveneTest.MissingPolicy.OMIT);
        require(missing.levels().equals(List.of("","NA")),"Literal labels changed");checks++;
        require(missing.keptRows().equals(List.of(0,2,3,5,6,7,8,9))&&missing.omittedRows().equals(List.of(1,4)),"Row identity changed");checks++;
        require(missing.groupSizes().equals(List.of(4,4)),"Complete-case counts differ");checks++;
        try {missing.levels().add("forged");throw new AssertionError("Mutable result");}catch(UnsupportedOperationException expected){checks++;}
        expect(()->LeveneTest.test(new double[]{1,2,Double.NaN,4},new String[]{"a","a","b","b"}));checks++;
        expect(()->LeveneTest.test(new double[]{1,2,Double.POSITIVE_INFINITY,4},new String[]{"a","a",null,"b"},LeveneTest.Center.MEDIAN,LeveneTest.MissingPolicy.OMIT));checks++;
        expect(()->LeveneTest.test(new double[]{1,2,3},new String[]{"a","b"}));checks++;
        expect(()->LeveneTest.test(new double[]{1,2,3},new String[]{"a","b","c"}));checks++;
        expect(()->LeveneTest.test(new double[]{1,1,3,3,5,5,9,9},new String[]{"a","a","a","a","b","b","b","b"}));checks++;
        System.out.println("PASS_PUBLIC_NATIVE_LEVENE_API checks="+checks);
    }
    private static void expect(Runnable r) {try {r.run();throw new AssertionError("Invalid numerical input admitted");}catch(IllegalArgumentException|ArithmeticException expected){}}
    private static void close(double actual,double expected) {require(Double.isFinite(actual)&&Math.abs(actual-expected)<=2e-12*Math.max(1,Math.abs(expected)),"Invariance failed: "+actual+" vs "+expected);}
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
