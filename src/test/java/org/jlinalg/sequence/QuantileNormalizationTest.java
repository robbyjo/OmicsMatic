package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class QuantileNormalizationTest {
    @Test void independentlyCalculatedRankMeansAndNonadjacentTies() {
        double[][] input = {{5,4,3},{2,1,4},{3,4,6},{4,2,8}};
        var result = QuantileNormalization.normalizeColumns(input);
        double[][] expected = {{17.0/3,31.0/6,2},{2,2,3},{3,31.0/6,14.0/3},{14.0/3,3,17.0/3}};
        for (int row=0;row<4;row++) assertArrayEquals(expected[row],result.normalized()[row],1e-14);
        assertArrayEquals(new double[]{2,3,14.0/3,17.0/3},result.target(),1e-14);
    }
    @Test void suppliedTargetIsSortedAndInputsAndResultAccessAreIsolated() {
        double[][] input = {{3,1},{1,3},{3,2},{2,3}};
        double[] target = {8,2,6,4};
        var result=QuantileNormalization.normalizeColumns(input,target);
        double[][] expected={{7,2},{2,7},{7,4},{4,7}};
        for(int row=0;row<4;row++)assertArrayEquals(expected[row],result.normalized()[row]);
        assertArrayEquals(new double[]{8,2,6,4},target);
        assertArrayEquals(new double[]{3,1},input[0]);
        result.normalized()[0][0]=999;result.target()[0]=999;
        assertEquals(7,result.normalized()[0][0]);assertEquals(2,result.target()[0]);
        input[0][0]=999;target[0]=999;assertEquals(7,result.normalized()[0][0]);
    }
    @Test void fiveHundredActualOriginalFloat64ColumnCases() throws Exception {
        try(var stream=getClass().getResourceAsStream("/qnorm/column-reference.tsv")) {
            assertNotNull(stream);
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                assertEquals("case\trows\tcolumns\tinput_row_major\ttarget\texpected_row_major",reader.readLine());
                int cases=0,positions=0;
                for(String line;(line=reader.readLine())!=null;) {
                    String[] fields=line.split("\t",-1);int rows=Integer.parseInt(fields[1]),columns=Integer.parseInt(fields[2]);
                    double[] values=numbers(fields[3]),expected=numbers(fields[5]);double[][] input=new double[rows][columns];
                    for(int row=0;row<rows;row++)System.arraycopy(values,row*columns,input[row],0,columns);
                    var result=QuantileNormalization.normalizeColumns(input,fields[4].equals("default")?null:numbers(fields[4]));
                    double[][] actual=result.normalized();
                    for(int row=0;row<rows;row++)for(int column=0;column<columns;column++){
                        int index=row*columns+column;assertEquals(expected[index],actual[row][column],1e-12*Math.max(1,Math.abs(expected[index])),"case"+fields[0]+" row"+row+" column"+column);positions++;
                        assertEquals(values[index],input[row][column]);
                    }
                    cases++;
                }
                assertEquals(500,cases);assertEquals(61403,positions);
            }
        }
    }
    @Test void dimensionsMissingValuesAndOverflowFailRatherThanInventingValues() {
        assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(null));
        assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[0][]));
        assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[][]{{}}));
        assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[][]{{1},{2,3}}));
        for(double invalid:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}){
            assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[][]{{invalid}}));
            assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[][]{{1}},new double[]{invalid}));
        }
        assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[][]{{1}},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[][]{{1e308,1e308}}));
        assertThrows(IllegalArgumentException.class,()->QuantileNormalization.normalizeColumns(new double[][]{{1},{1}},new double[]{1e308,1e308}));
        assertArrayEquals(new double[]{7.5},QuantileNormalization.normalizeColumns(new double[][]{{7.5}}).normalized()[0]);
    }
    private static double[] numbers(String text){return Arrays.stream(text.split(",")).mapToDouble(Double::parseDouble).toArray();}
}
