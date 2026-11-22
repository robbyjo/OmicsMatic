package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DoubletScoresTest {
    @Test void independentBayesEndpointAndMonotonicityChecks(){
        assertEquals(1.0/199,DoubletScores.score(0,10,2,.1,0).value(),1e-16);assertEquals(11.0/29,DoubletScores.score(10,10,2,.1,0).value(),1e-15);
        double previous=-1;for(int n=0;n<=10;n++){var score=DoubletScores.score(n,10,2,.1,.02);assertTrue(score.value()>previous);assertTrue(score.standardError()>0);previous=score.value();}
        assertFalse(DoubletScores.predicted(.2,.2));assertTrue(DoubletScores.predicted(Math.nextUp(.2),.2));
    }
    @Test void invalidNeighborhoodRateAndRatiosFail(){
        assertThrows(IllegalArgumentException.class,()->DoubletScores.score(11,10,2,.1,.02));assertThrows(IllegalArgumentException.class,()->DoubletScores.score(1,10,0,.1,.02));assertThrows(IllegalArgumentException.class,()->DoubletScores.score(1,10,2,1,.02));assertThrows(IllegalArgumentException.class,()->DoubletScores.predicted(Double.NaN,.1));
    }
    @Test void actualScrubletPipelineNeighborhoodScoresAndErrorsAgree()throws Exception{
        int count=0;
        for(String line:Files.readAllLines(Path.of("src/test/resources/single-cell-qc/scrublet-neighbor-scores.tsv")).subList(1,757)){
            String[] f=line.split("\t",-1);var result=DoubletScores.score(Integer.parseInt(f[1]),Integer.parseInt(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Double.parseDouble(f[5]));
            assertEquals(Double.parseDouble(f[6]),result.value(),2e-12,f[0]);assertEquals(Double.parseDouble(f[7]),result.standardError(),2e-12,f[0]);count++;
        }
        assertEquals(756,count);
    }
}
