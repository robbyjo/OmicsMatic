/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SmartpcaAnalysisReferenceTest {
    private static final Pattern REMOVED=Pattern.compile("REMOVED outlier (\\S+) iter (\\d+) evec (\\d+) sigmage (\\S+) pop: (\\S+)");
    @Test void comparesSixteenOriginalWholeProgramCasesAndRefusesTwoUnidentifiedShrinkageWitnesses()throws Exception {
        int compared=0,refused=0,totalRemoved=0;
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/genetics/smartpca-original-analysis-reference.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)) {
                java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    if(!entry.getName().endsWith("/reference.json"))continue;
                    var row=object(GprofilerJson.read(zip.readAllBytes()));String name=(String)row.get("case");
                    var input=object(row.get("input"));var values=list(input.get("genotypes"));byte[][] calls=new byte[values.size()][];
                    for(int s=0;s<calls.length;s++){var raw=list(values.get(s));calls[s]=new byte[raw.size()];for(int i=0;i<raw.size();i++)calls[s][i]=(byte)integer(raw.get(i));}
                    int[] basis=list(input.get("basis_indices")).stream().mapToInt(SmartpcaAnalysisReferenceTest::integer).toArray();
                    var o=object(row.get("options"));var pca=new SmartpcaPrepared.Options(integer(o.get("components")),(Boolean)o.get("use_norm"),(Boolean)o.get("alternate_norm_style"),
                        integer(o.get("minimum_allele_count")),integer(o.get("maximum_missing")),SmartpcaPrepared.Projection.LEAST_SQUARES,SmartpcaPrepared.Limits.defaults());
                    var options=new SmartpcaAnalysis.Options(pca,integer(o.get("maximum_outlier_iterations")),integer(o.get("outlier_axes")),number(o.get("outlier_threshold")),
                        SmartpcaOutliers.Mode.values()[integer(o.get("outlier_mode"))],(Boolean)o.get("shrinkage"));
                    if(!row.get("expected_native").equals("COMPARE_FINITE_SOURCE_OUTPUTS")) {
                        assertThrows(IllegalArgumentException.class,()->SmartpcaAnalysis.analyze(calls,basis,options),name);refused++;continue;
                    }
                    assertEquals(0,integer(row.get("exit_code")),name);var observed=new ArrayList<SmartpcaAnalysis.Pass>();
                    var actual=SmartpcaAnalysis.analyze(calls,basis,options,observed::add);assertEquals(observed,actual.passes(),name);
                    String[] lambda=((String)row.get("original_eval")).trim().split("\\s+");assertEquals(lambda.length,actual.eigenvalues().length,name);
                    for(int i=0;i<lambda.length;i++)assertEquals(Double.parseDouble(lambda[i]),actual.eigenvalues()[i],.50001e-6,name+" eigenvalue "+i);
                    var ids=list(input.get("sample_ids"));var originalRemovals=new ArrayList<String[]>();
                    for(String line:((String)row.get("original_outliers")).lines().toList()) {
                        if(line.isBlank())continue;var match=REMOVED.matcher(line.trim());assertTrue(match.matches(),name+" unexpected removal log "+line);
                        originalRemovals.add(new String[]{match.group(1),match.group(2),match.group(3),match.group(4)});
                    }
                    assertEquals(originalRemovals.size(),actual.removals().size(),name+" removed count");
                    for(int i=0;i<originalRemovals.size();i++) {
                        String[] expected=originalRemovals.get(i);var removed=actual.removals().get(i);
                        assertEquals(ids.indexOf(expected[0]),removed.originalSampleIndex(),name);
                        assertEquals(Integer.parseInt(expected[1]),removed.iteration(),name);assertEquals(Integer.parseInt(expected[2]),removed.zeroBasedAxis(),name);
                        assertEquals(Math.abs(Double.parseDouble(expected[3])),Math.abs(removed.signedDeviation()),.50001e-3,name+" source three-decimal deviation");
                    }
                    totalRemoved+=actual.removals().size();
                    Map<String,double[]> reference=new LinkedHashMap<>();
                    for(String line:((String)row.get("original_evec")).lines().skip(1).toList()) {
                        if(line.isBlank())continue;String[] fields=line.trim().split("\\s+");double[] coordinates=new double[pca.components()];
                        for(int c=0;c<coordinates.length;c++)coordinates[c]=Double.parseDouble(fields[c+1]);assertNull(reference.put(fields[0],coordinates),name);
                    }
                    double[][] scores=actual.scores();double envelope=.50001*Math.pow(10,-integer(row.get("original_coordinate_decimal_places")));
                    double[] signs=new double[pca.components()];
                    for(int c=0;c<signs.length;c++) {
                        double dot=0;for(int i=0;i<scores.length;i++)if(reference.containsKey(ids.get(i))&&Double.isFinite(scores[i][c]))dot+=scores[i][c]*reference.get(ids.get(i))[c];
                        assertNotEquals(0,dot,name);signs[c]=Math.copySign(1,dot);
                    }
                    for(int i=0;i<scores.length;i++) {
                        final int originalIndex=i;
                        double[] expected=reference.get(ids.get(i));
                        if(expected==null) {
                            assertEquals(SmartpcaAnalysis.SampleStatus.REMOVED_OUTLIER,actual.sampleStatus().get(i),name);
                            for(double score:scores[i])assertTrue(Double.isNaN(score),name);
                        } else {
                            for(int c=0;c<expected.length;c++)assertEquals(expected[c],scores[i][c]*signs[c],envelope,name+" score "+ids.get(i)+"/"+c);
                            if(actual.removals().stream().anyMatch(removed->removed.originalSampleIndex()==originalIndex))
                                assertEquals(SmartpcaAnalysis.SampleStatus.SOURCE_SHRINKAGE_OUTPUT_FOR_REMOVED_OUTLIER,actual.sampleStatus().get(i),name);
                        }
                    }
                    compared++;
                }
            }
        }
        assertEquals(16,compared);assertEquals(2,refused);assertTrue(totalRemoved>=5,"Whole-program cases must exercise actual removal");
    }
    private static Map<?,?> object(Object value){if(value instanceof Map<?,?> map)return map;throw new IllegalArgumentException("Expected object");}
    private static List<?> list(Object value){if(value instanceof List<?> rows)return rows;throw new IllegalArgumentException("Expected array");}
    private static double number(Object value){return Double.parseDouble(((GprofilerJson.Numeric)value).token());}
    private static int integer(Object value){return Integer.parseInt(((GprofilerJson.Numeric)value).token());}
    public static void main(String[] arguments)throws Exception {
        new SmartpcaAnalysisReferenceTest().comparesSixteenOriginalWholeProgramCasesAndRefusesTwoUnidentifiedShrinkageWitnesses();
        System.out.println("PASS:16 original complete outlier/shrinkage comparisons;2 undefined original witnesses refused. Workflow qualification remains open.");
    }
}
