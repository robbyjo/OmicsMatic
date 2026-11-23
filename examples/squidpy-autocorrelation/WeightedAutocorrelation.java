import org.jlinalg.squidpy.SquidpyAutocorrelation;

/** Artificial path graph; run with the newly built OmicsMatic JAR on the classpath. */
public final class WeightedAutocorrelation {
    private WeightedAutocorrelation() { }
    public static void main(String[] args) {
        if (args.length>1 || args.length==1 && !args[0].equals("constant")) {
            System.err.println("FAIL2: use no argument for the artificial path or 'constant' for an undefined feature.");
            System.exit(2);
        }
        double[] values=args.length==0 ? new double[]{1,2,3,4} : new double[]{3,3,3,3};
        int[] rows={0,1,3,5,6}, columns={1,0,2,1,3,2};
        double[] weights={1,1,1,1,1,1};
        boolean complete=true;
        System.out.println("mode\tscore\tvariance\tp_value\tstatus");
        for (var mode:SquidpyAutocorrelation.Statistic.values()) {
            var result=SquidpyAutocorrelation.compute(SquidpyAutocorrelation.Edition.SQUIDPY_1_8_4,
                    mode,values,rows,columns,weights,false,false);
            System.out.println(mode+"\t"+result.score()+"\t"+result.normalityVariance()
                    +"\t"+result.normalityPValue()+"\t"+result.status());
            if (result.status()!=SquidpyAutocorrelation.Status.COMPLETE) {
                complete=false;
                System.err.println("FAIL2: "+result.condition()+". Inspect constant features, graph weights and analytic variance; preserve these values and retry with scientifically valid NEW inputs. No complete analytic result.");
            }
        }
        if (!complete) System.exit(2);
    }
}
