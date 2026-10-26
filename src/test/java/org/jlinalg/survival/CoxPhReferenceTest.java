/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.compute.BackendPolicy;
import org.junit.jupiter.api.Test;

class CoxPhReferenceTest {
    private static List<String[]> rows(String name)throws Exception{return Files.readAllLines(Path.of(CoxPhReferenceTest.class.getResource("/study-survival/"+name).toURI())).stream().skip(1).map(line->line.split("\t")).toList();}
    @Test void rightCensoredAndDelayedEntryStratifiedTestsMatchIndependentRLogTimeScoreTests()throws Exception{
        var inputs=rows("inputs.tsv");var expected=rows("expected.tsv");
        for(String name:List.of("right-efron","entry-strata-efron","entry-strata-breslow")){
            var data=inputs.stream().filter(row->row[0].equals(name)).toList();int n=data.size();double[] start=new double[n],stop=new double[n],offset=new double[n];boolean[] events=new boolean[n];int[] strata=new int[n];double[][] x=new double[n][2];
            for(int i=0;i<n;i++){var row=data.get(i);start[i]=Double.parseDouble(row[2]);stop[i]=Double.parseDouble(row[3]);events[i]=row[4].equals("1");strata[i]=Integer.parseInt(row[5]);x[i][0]=Double.parseDouble(row[6]);x[i][1]=Double.parseDouble(row[7]);offset[i]=Double.parseDouble(row[8]);}
            var response=new CoxSurvivalData(start,stop,events,strata);var controls=CoxOptions.defaults().withTies(name.endsWith("breslow")?CoxTies.BRESLOW:CoxTies.EFRON);var fit=CoxRegression.fit(response,x,offset,controls,BackendPolicy.CPU);assertTrue(fit.converged());var ph=CoxDiagnostics.analyze(response,x,fit,offset,null).proportionalHazards();var ref=expected.stream().filter(row->row[0].equals(name)).toList();
            for(int j=0;j<2;j++){var r=ref.get(j);assertEquals(Double.parseDouble(r[2]),fit.beta()[j],2e-6,name+" beta");assertEquals(Double.parseDouble(r[3]),fit.standardErrors()[j],2e-6,name+" SE");assertEquals(Double.parseDouble(r[4]),fit.logPartialLikelihood(),2e-7,name+" likelihood");assertEquals(Double.parseDouble(r[6]),ph.statistics()[j],3e-6,name+" PH statistic");assertEquals(Double.parseDouble(r[7]),ph.pValues()[j],2e-6,name+" PH P");assertEquals(Double.parseDouble(r[8]),ph.globalStatistic(),3e-6,name+" global PH");assertEquals(Double.parseDouble(r[9]),ph.globalPValue(),2e-6,name+" global PH P");}
        }
    }
    @Test void aSingleDistinctEventTimeProvidesNoTimeTrendInformation(){
        var data=CoxSurvivalData.rightCensored(new double[]{1,1,1,2,2,2,2,2},new boolean[]{true,true,true,false,false,false,false,false});double[][] x={{-.7},{.8},{.1},{-.5},{.9},{.3},{-.1},{1.1}};
        var fit=CoxRegression.fit(data,x);var ph=CoxDiagnostics.analyze(data,x,fit,null,null).proportionalHazards();assertTrue(Double.isNaN(ph.pValues()[0]));assertTrue(Double.isNaN(ph.globalPValue()));
    }
}
