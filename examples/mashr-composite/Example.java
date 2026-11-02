import java.util.Arrays;
import java.util.List;
import org.jlinalg.shrinkage.MashCompositeScores;
import org.jlinalg.shrinkage.MashSharing;

/** Synthetic inputs illustrating the reported independent-marginal proxy formulas. */
public final class Example {
    private Example() { }
    public static void main(String[] args){
        double[][] lfsr=new double[3][8];Arrays.fill(lfsr[0],.01);Arrays.fill(lfsr[1],.5);Arrays.fill(lfsr[2],1);lfsr[2][0]=.01;
        int[] all={0,1,2,3,4,5,6,7},others={1,2,3,4,5,6,7};
        var patterns=List.of(new MashCompositeScores.Pattern("all-eight",all,new int[0],MashCompositeScores.Mode.ALL),new MashCompositeScores.Pattern("only-first",new int[]{0},others,MashCompositeScores.Mode.ALL),new MashCompositeScores.Pattern("any-first-two",new int[]{0,1},new int[0],MashCompositeScores.Mode.AT_LEAST_ONE));
        var result=MashCompositeScores.calculate(lfsr,8,patterns,MashCompositeScores.Scale.LFSR,MashCompositeScores.Missing.REJECT,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,MashSharing.Limits.defaults(),()->{});
        for(int r=0;r<3;r++)for(int p=0;p<patterns.size();p++)System.out.println("feature="+(r+1)+" pattern="+patterns.get(p).name()+" source_score="+result.score(r,p).sourceScore()+" stable_log="+result.score(r,p).stableLogScore()+" state="+result.score(r,p).state());
        System.out.println("P024-style all-eight threshold >=0.05: "+result.score(0,0).passes(.05,MashSharing.Boundary.INCLUSIVE)+", "+result.score(1,0).passes(.05,MashSharing.Boundary.INCLUSIVE)+", "+result.score(2,0).passes(.05,MashSharing.Boundary.INCLUSIVE));
        if(!result.score(0,0).passes(.05,MashSharing.Boundary.INCLUSIVE)||result.score(1,0).passes(.05,MashSharing.Boundary.INCLUSIVE)||!result.score(2,1).passes(.5,MashSharing.Boundary.STRICT))throw new AssertionError("Declared composite formulas/gates changed");
        var loss=MashCompositeScores.calculate(new double[][]{{1e-100}},1,List.of(new MashCompositeScores.Pattern("tiny-at-least-one",new int[]{0},new int[0],MashCompositeScores.Mode.AT_LEAST_ONE)),MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,MashSharing.Limits.defaults(),()->{}).score(0,0);
        System.out.println("Cancellation example: source_score="+loss.sourceScore()+" stable_log="+loss.stableLogScore()+" state="+loss.state());
        if(loss.state()!=MashCompositeScores.State.SOURCE_ROUNDING_TO_ZERO||!Double.isFinite(loss.stableLogScore()))throw new AssertionError("Arithmetic loss was hidden");
        System.out.println("These are explicitly independent marginal-proxy scores. No joint posterior integration, calibrated P value or biological specificity is established.");
    }
}
