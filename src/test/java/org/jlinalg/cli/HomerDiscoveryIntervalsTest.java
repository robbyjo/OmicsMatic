package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.jlinalg.sequence.HomerMotifs;
import org.junit.jupiter.api.Test;

class HomerDiscoveryIntervalsTest {
    private HomerMotifs.Motif model(double threshold,double[][] rows){return new HomerMotifs.Motif(1,"N".repeat(rows.length),"1-"+"N".repeat(rows.length),threshold,0.0,List.of(),rows);}
    @Test void strongFixedSitesHaveDeterminedPresenceOnBothStrands()throws Exception{
        var m=model(2.6,new double[][]{{.97,.01,.01,.01},{.97,.01,.01,.01}});
        var summary=HomerDiscoveryIntervals.scan("AACCTT",HomerDiscoveryIntervals.compile(m),HomerMotifs.Strands.BOTH);
        assertEquals(5,summary.windows());assertEquals(2,summary.nominalSites());assertEquals(2,summary.definiteSites());assertEquals(2,summary.possibleSites());
    }
    @Test void flatPrintedMatrixCanLeaveThresholdPresenceUnresolved()throws Exception{
        var m=model(0,new double[][]{{.25,.25,.25,.25}});
        var summary=HomerDiscoveryIntervals.scan("ACGT",HomerDiscoveryIntervals.compile(m),HomerMotifs.Strands.BOTH);
        assertEquals(8,summary.nominalSites());assertEquals(0,summary.definiteSites());assertEquals(8,summary.possibleSites());
        assertFalse(summary.definitePresence());assertTrue(summary.possiblePresence());
    }
    @Test void ambiguousAndShortSequencesHaveNoPossibleSites()throws Exception{
        var m=HomerDiscoveryIntervals.compile(model(0,new double[][]{{.25,.25,.25,.25},{.25,.25,.25,.25}}));
        var n=HomerDiscoveryIntervals.scan("ANNNT",m,HomerMotifs.Strands.BOTH);assertEquals(4,n.ambiguousWindows());assertEquals(0,n.possibleSites());assertEquals(Double.NEGATIVE_INFINITY,n.bestUpperScore());
        assertEquals(0,HomerDiscoveryIntervals.scan("A",m,HomerMotifs.Strands.BOTH).windows());
    }
    @Test void strandSelectionDoesNotMergeComplementarySites()throws Exception{
        var m=HomerDiscoveryIntervals.compile(model(1.2,new double[][]{{.97,.01,.01,.01}}));
        assertEquals(1,HomerDiscoveryIntervals.scan("AT",m,HomerMotifs.Strands.FORWARD).definiteSites());
        assertEquals(1,HomerDiscoveryIntervals.scan("AT",m,HomerMotifs.Strands.REVERSE).definiteSites());
        assertEquals(2,HomerDiscoveryIntervals.scan("AT",m,HomerMotifs.Strands.BOTH).definiteSites());
    }
    @Test void onlyStagedUppercaseDnaIsAdmittedEvenWhenTooShortOrMaskedByN(){
        var m=HomerDiscoveryIntervals.compile(model(0,new double[][]{{.25,.25,.25,.25},{.25,.25,.25,.25}}));
        for(String dna:List.of("a","Nα","AU","A R",""))if(!dna.isEmpty())assertThrows(IOException.class,()->HomerDiscoveryIntervals.scan(dna,m,HomerMotifs.Strands.BOTH));
    }
    @Test void roundedThresholdCanChangePointRescanWithoutChangingOriginalCounts()throws Exception{
        var printed=model(1.355835,new double[][]{{.97,.01,.01,.01}});
        var original=model(1.35583525,new double[][]{{.97,.01,.01,.01}});
        var hits=new ArrayList<HomerMotifs.Hit>();HomerMotifs.scan("AT",original,new HomerMotifs.Options(HomerMotifs.Strands.BOTH,0,false,2,10,10),hits::add);assertTrue(hits.isEmpty());
        var bounded=HomerDiscoveryIntervals.scan("AT",HomerDiscoveryIntervals.compile(printed),HomerMotifs.Strands.BOTH);
        assertEquals(2,bounded.nominalSites());assertEquals(0,bounded.definiteSites());assertEquals(2,bounded.possibleSites());
    }
    @Test void normalizationRoundTripsStayInsideEnvelopeForEnumeratedDnaWords()throws Exception{
        var random=new Random(20261028);
        for(int trial=0;trial<30;trial++){
            double[][] raw=new double[3][4];
            for(int i=0;i<3;i++){
                int a=random.nextInt(1001),b=random.nextInt(1001-a),c=random.nextInt(1001-a-b);
                raw[i]=new double[]{a/1000.0,b/1000.0,c/1000.0,(1000-a-b-c)/1000.0};
            }
            double[][] floored=new double[3][4];for(int i=0;i<3;i++)for(int j=0;j<4;j++)floored[i][j]=Math.max(.001,raw[i][j]);
            double[][] printed=normalize(normalize(floored));double threshold=(random.nextDouble()-.5)*15;
            var nativeOriginal=model(threshold,raw);var bounded=HomerDiscoveryIntervals.compile(model(Math.rint(threshold*1000000)/1000000,printed));
            for(int encoded=0;encoded<256;encoded++){
                StringBuilder dna=new StringBuilder();int x=encoded;for(int j=0;j<4;j++){dna.append("ACGT".charAt(x%4));x/=4;}
                var hits=new ArrayList<HomerMotifs.Hit>();HomerMotifs.scan(dna,nativeOriginal,new HomerMotifs.Options(HomerMotifs.Strands.BOTH,0,false,4,100,10),hits::add);
                var interval=HomerDiscoveryIntervals.scan(dna.toString(),bounded,HomerMotifs.Strands.BOTH);
                assertTrue(interval.definiteSites()<=hits.size());assertTrue(interval.possibleSites()>=hits.size());
            }
        }
    }
    private double[][] normalize(double[][] rows){
        var result=new double[rows.length][4];
        for(int i=0;i<rows.length;i++){
            double sum=0;for(double p:rows[i])sum+=p;double deficit=0;int small=0;
            for(int j=0;j<4;j++){result[i][j]=rows[i][j]/sum;if(result[i][j]<.001){small++;deficit+=.001-result[i][j];}}
            for(int j=0;j<4;j++){double value=result[i][j]<.001?.001:result[i][j]-deficit/(4-small);result[i][j]=Double.parseDouble(String.format(java.util.Locale.ROOT,"%.3f",value));}
        }return result;
    }
}
