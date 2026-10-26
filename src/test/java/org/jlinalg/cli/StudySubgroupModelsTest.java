/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudySubgroupInteractionPolicy;
import org.junit.jupiter.api.Test;

class StudySubgroupModelsTest {
    private record Data(double[] continuous,double[] binary,double[] x,double[][] c,int[] group,double[] offset) { }
    private Data data()throws Exception {
        var table=DelimitedData.readResult(Path.of(getClass().getResource("/subgroup-interactions/data.tsv").toURI()));int n=table.rows().size();
        double[] y=new double[n],binary=new double[n],x=new double[n],offset=new double[n];double[][] c=new double[n][1];int[] group=new int[n];
        for(int i=0;i<n;i++){String[] row=table.rows().get(i);y[i]=Double.parseDouble(row[table.column("continuous")]);binary[i]=Double.parseDouble(row[table.column("binary")]);x[i]=Double.parseDouble(row[table.column("feature")]);c[i][0]=Double.parseDouble(row[table.column("age")]);offset[i]=Double.parseDouble(row[table.column("offset")]);group[i]=Integer.parseInt(row[table.column("group")]);}
        return new Data(y,binary,x,c,group,offset);
    }
    private Map<String,Object> policy(){return StudySubgroupInteractionPolicy.resolve(Map.of());}
    @Test void allFourMethodsMatchIndependentUnscaledStatsmodelsAndScipyReferences()throws Exception {
        var d=data();var expected=DelimitedData.readResult(Path.of(getClass().getResource("/subgroup-interactions/expected.tsv").toURI()));
        for(String[] row:expected.rows()) {
            String method=row[expected.column("method")];var fit=StudySubgroupModels.fit(method,method.startsWith("ols-")?d.continuous():d.binary(),d.c(),d.group(),d.x(),d.offset(),policy());
            double[] values={fit.beta(),fit.se(),fit.lower(),fit.upper(),fit.p(),fit.statistic(),fit.df(),fit.referenceBeta(),fit.referenceSe(),fit.comparisonBeta(),fit.comparisonSe()};
            String[] fields={"beta","se","ci_lower","ci_upper","p_value","statistic","df","reference_beta","reference_se","comparison_beta","comparison_se"};
            for(int i=0;i<fields.length;i++){double reference=Double.parseDouble(row[expected.column(fields[i])]);assertEquals(reference,values[i],(method.equals("firth-lrt")?2e-5:2e-7)*Math.max(1,Math.abs(reference)),method+":"+fields[i]);}
            double referenceP=Double.parseDouble(row[expected.column("p_value")]);assertEquals(referenceP,fit.p(),referenceP*2e-5,method+":relative_P");assertEquals(Math.log(referenceP),fit.logP(),2e-5,method+":log_P");
            assertEquals(7,fit.parameters());assertEquals(fit.p(),Math.exp(fit.logP()),1e-13);
        }
    }
    @Test void swappingContrastOrientationSwapsSlopesAndReversesOnlyTheDifference()throws Exception {
        var d=data();int[] reverse=Arrays.stream(d.group()).map(g->1-g).toArray();
        for(String method:List.of("ols-hc3","ols-classical","logit-lrt","firth-lrt")) {
            double[] y=method.startsWith("ols-")?d.continuous():d.binary();var a=StudySubgroupModels.fit(method,y,d.c(),d.group(),d.x(),d.offset(),policy());var b=StudySubgroupModels.fit(method,y,d.c(),reverse,d.x(),d.offset(),policy());
            assertEquals(-a.beta(),b.beta(),2e-6);assertEquals(a.se(),b.se(),2e-6);assertEquals(a.p(),b.p(),2e-6);assertEquals(a.referenceBeta(),b.comparisonBeta(),2e-6);assertEquals(a.comparisonBeta(),b.referenceBeta(),2e-6);
        }
    }
    @Test void featureUnitAndLocationChangesRetainProbabilityAndInputUnitEstimates()throws Exception {
        var d=data();double[] transformed=Arrays.stream(d.x()).map(x->100-17.5*x).toArray();
        for(String method:List.of("ols-hc3","ols-classical","logit-lrt","firth-lrt")) {
            double[] y=method.startsWith("ols-")?d.continuous():d.binary();var a=StudySubgroupModels.fit(method,y,d.c(),d.group(),d.x(),d.offset(),policy());var b=StudySubgroupModels.fit(method,y,d.c(),d.group(),transformed,d.offset(),policy());
            assertEquals(a.beta()/-17.5,b.beta(),2e-6);assertEquals(a.se()/17.5,b.se(),2e-6);assertEquals(a.p(),b.p(),2e-6);
        }
    }
    @Test void defaultCovariateFeatureInteractionRemovesTheConstructedAlternativeExplanation() {
        int n=200;double[] y=new double[n],x=new double[n],offset=new double[n];double[][] c=new double[n][1];int[] group=new int[n];
        for(int i=0;i<n;i++){group[i]=i%2;c[i][0]=1+3*group[i]+Math.sin(i*1.9);x[i]=Math.cos(i*.31);y[i]=c[i][0]*x[i]+.2*c[i][0]+.5*group[i]+.1*Math.sin(i*4.1);}
        var proper=StudySubgroupModels.fit("ols-hc3",y,c,group,x,offset,policy());var omitted=StudySubgroupInteractionPolicy.resolve(Map.of("nuisance_interactions","group_only","rationale","Synthetic omitted-confounder control, not a recommended analysis"));var improper=StudySubgroupModels.fit("ols-hc3",y,c,group,x,offset,omitted);
        assertTrue(proper.p()>.05,proper.toString());assertTrue(improper.p()<1e-8,improper.toString());assertTrue(proper.lower()<0&&proper.upper()>0,proper.toString());
    }
    @Test void separationDoesNotAutomaticallySwitchLogitToFirth() {
        int n=80;double[] y=new double[n],x=new double[n],offset=new double[n];double[][] c=new double[n][0];int[] group=new int[n];
        for(int i=0;i<n;i++){group[i]=i%2;x[i]=(i/2-20.)/10;y[i]=group[i]==1&&x[i]>0?1:0;}
        assertThrows(IllegalArgumentException.class,()->StudySubgroupModels.fit("logit-lrt",y,c,group,x,offset,policy()));var firth=StudySubgroupModels.fit("firth-lrt",y,c,group,x,offset,policy());assertTrue(Double.isFinite(firth.beta()));assertTrue(firth.p()>=0&&firth.p()<=1);assertTrue(firth.inference().contains("full")||firth.inference().contains("Full"));
    }
    @Test void singularNuisanceAndFeatureDesignDoesNotAcquireAMinimumNormPValue()throws Exception {
        var d=data();double[] duplicate=Arrays.stream(d.c()).mapToDouble(row->row[0]).toArray();
        assertThrows(IllegalArgumentException.class,()->StudySubgroupModels.fit("ols-hc3",d.continuous(),d.c(),d.group(),duplicate,d.offset(),policy()));
    }
    @Test void oneClassOutcomesAreUnavailableAndNumericalBudgetsAreNotCompletedNulls()throws Exception {
        var d=data();
        for(String method:List.of("logit-lrt","firth-lrt")) {
            assertThrows(IllegalArgumentException.class,()->StudySubgroupModels.fit(method,new double[d.binary().length],d.c(),d.group(),d.x(),d.offset(),policy()));
            var limited=StudySubgroupInteractionPolicy.resolve(Map.of("maximum_iterations",1));
            assertThrows(StudySubgroupModels.Unresolved.class,()->StudySubgroupModels.fit(method,d.binary(),d.c(),d.group(),d.x(),d.offset(),limited));
        }
    }
    @Test void unrepresentableInputUnitEffectsCannotCarryAnEstimableInteractionP()throws Exception {
        var d=data();double[] tiny=Arrays.stream(d.x()).map(v->v*1e-320).toArray();
        for(String method:List.of("ols-hc3","ols-classical","logit-lrt","firth-lrt")) {
            double[] y=method.startsWith("ols-")?d.continuous():d.binary();
            var invalid=assertThrows(StudySubgroupModels.Unresolved.class,()->StudySubgroupModels.fit(method,y,d.c(),d.group(),tiny,d.offset(),policy()));
            assertTrue(invalid.getMessage().contains("unrepresentable_in_input_units"));
        }
    }
}
