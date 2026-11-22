/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import org.jlinalg.sequence.HomerMotifs;

/**
 * Conservative sequence-presence envelopes for HOMER's twice-normalized printed
 * discovery matrices. These bounds do not reconstruct the internal optimizer.
 */
final class HomerDiscoveryIntervals {
    private HomerDiscoveryIntervals() { }
    // Final scoring uses floored, previously 3-decimal rows. The two post-score
    // getConsensus/expxform/justNormalize calls contribute <0.005525+0.002511
    // absolute cell drift, including rounding. 0.0081 encloses that sum.
    static final double CELL_RADIUS=.0081;
    static final double THRESHOLD_RADIUS=.000000501;
    record Summary(long windows,long ambiguousWindows,long nominalSites,long definiteSites,
            long possibleSites,double bestLowerScore,double bestUpperScore) {
        boolean definitePresence(){return definiteSites>0;}
        boolean possiblePresence(){return possibleSites>0;}
    }
    static final class Model {
        private final double[][] lower,upper,nominal;
        private final double threshold;
        private Model(HomerMotifs.Motif motif){
            double[][] rows=motif.probabilities();lower=new double[rows.length][4];upper=new double[rows.length][4];nominal=new double[rows.length][4];threshold=motif.threshold();
            for(int i=0;i<rows.length;i++)for(int j=0;j<4;j++){
                lower[i][j]=StrictMath.log(Math.max(.001,rows[i][j]-CELL_RADIUS)/.25);
                upper[i][j]=StrictMath.log(Math.max(.001,Math.min(1,rows[i][j]+CELL_RADIUS))/.25);
                nominal[i][j]=StrictMath.log(Math.max(.001,rows[i][j])/.25);
            }
        }
        int width(){return lower.length;}
    }
    static Model compile(HomerMotifs.Motif motif){return new Model(motif);}
    static Summary scan(String dna,Model model,HomerMotifs.Strands strands)throws IOException{
        if(strands==null)throw new IOException("Discovery interval strands are required");
        for(int i=0;i<dna.length();i++)if("ACGTN".indexOf(dna.charAt(i))<0)throw new IOException("Interval audit requires staged uppercase A/C/G/T/N");
        int n=dna.length(),width=model.width();long windows=Math.max(0L,(long)n-width+1),ambiguous=0,point=0,definite=0,possible=0;
        double bestLower=Double.NEGATIVE_INFINITY,bestUpper=Double.NEGATIVE_INFINITY;
        for(int left=0;left<=n-width;left++){
            boolean valid=true;
            for(int j=0;j<width;j++){char c=dna.charAt(left+j);if(c=='N'){valid=false;break;}if("ACGT".indexOf(c)<0)throw new IOException("Interval audit requires staged uppercase A/C/G/T/N");}
            if(!valid){ambiguous++;continue;}
            for(int orientation=0;orientation<2;orientation++){
                if(orientation==0&&strands==HomerMotifs.Strands.REVERSE||orientation==1&&strands==HomerMotifs.Strands.FORWARD)continue;
                double lower=0,upper=0,nominal=0;
                for(int j=0;j<width;j++){
                    int base="ACGT".indexOf(dna.charAt(left+(orientation==0?j:width-j-1)));if(orientation==1)base=3-base;
                    lower+=model.lower[j][base];upper+=model.upper[j][base];nominal+=model.nominal[j][base];
                }
                double guard=1e-10*width;lower-=guard;upper+=guard;
                bestLower=Math.max(bestLower,lower);bestUpper=Math.max(bestUpper,upper);
                if(nominal>=model.threshold)point++;
                if(lower>=model.threshold+THRESHOLD_RADIUS)definite++;
                if(upper>=model.threshold-THRESHOLD_RADIUS)possible++;
            }
        }
        return new Summary(windows,ambiguous,point,definite,possible,bestLower,bestUpper);
    }
}
