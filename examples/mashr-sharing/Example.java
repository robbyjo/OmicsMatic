import java.util.List;
import org.jlinalg.shrinkage.MashGaussian;
import org.jlinalg.shrinkage.MashSharing;
import org.jlinalg.shrinkage.MashSignScores;

/** Synthetic fitted posteriors; no patient data or missing-value imputation. */
public final class Example {
    private Example() { }
    public static void main(String[] args) {
        double[][] identity={{1,0,0},{0,1,0},{0,0,1}};
        var model=MashGaussian.prepare(List.<double[][]>of(identity),new double[]{1},identity,
                MashGaussian.Options.defaults(),MashGaussian.Limits.defaults(),()->{});
        double[][] effects={{4,4,0},{0,0,0},{-4,4,2}};
        double[][] means=new double[3][],lfsr=new double[3][];
        for(int i=0;i<effects.length;i++){
            var posterior=model.evaluate(effects[i],new double[]{1,1,1},()->{});
            means[i]=posterior.mean();lfsr[i]=posterior.lfsr();
        }
        var limits=MashSharing.Limits.defaults();
        var sharing=MashSharing.pairwise(means,lfsr,3,.5,.05,MashSharing.Transform.IDENTITY,limits,()->{});
        var cell=sharing.cell(0,1);
        System.out.println("A/B posterior-mean sharing: status="+cell.status()+", selected="+cell.selectedRows()+", shared="+cell.sharedRows()+", fraction="+cell.fraction());
        var empty=sharing.cell(2,2);
        System.out.println("C/C at lfsr<0.05: status="+empty.status());
        var diagnostic=MashSharing.pairwise(means,lfsr,3,.5,1,MashSharing.Transform.IDENTITY,limits,()->{}).cell(0,1);
        System.out.println("A/B with deliberately permissive lfsr<1: status="+diagnostic.status()+", undefined ratios="+diagnostic.undefinedRatios());
        // Explicit synthetic nominal P values; they are separate from posterior LFSR.
        double[][] nominalP={{.0001,.0001,1},{1,1,1},{.0001,.0001,.1}};
        var scores=MashSignScores.calculate(lfsr,nominalP,3,List.of(new int[]{0,1}),.05,2,.9,limits,()->{});
        for(int r=0;r<3;r++)System.out.println("Feature "+(r+1)+": marginal sign score="+scores.score(r,0).product()+", nominal gate="+scores.score(r,0).eligible()+", score>0.9="+scores.score(r,0).exceedsThreshold());
        System.out.println("The sign score can be high for opposite effect directions. It is not pairwise same-sign sharing or a joint posterior probability.");
        if(cell.fraction()!=.5||empty.status()!=MashSharing.Status.EMPTY_SELECTED_POPULATION||diagnostic.status()!=MashSharing.Status.UNDEFINED_SELECTED_RATIO||!scores.score(0,0).exceedsThreshold()||scores.score(1,0).eligible()||!scores.score(2,0).exceedsThreshold())throw new AssertionError("Example did not retain the declared summary semantics");
    }
}
