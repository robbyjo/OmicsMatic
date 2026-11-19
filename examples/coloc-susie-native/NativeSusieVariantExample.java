import java.util.Arrays;
import java.util.List;
import org.jlinalg.coloc.*;

/** Synthetic supplied BFs; demonstrates the native stable variant's weighted conditional. */
public final class NativeSusieVariantExample {
    public static void main(String[] args) {
        var ids=List.of("a","b","c");
        var first=new ColocSusieInput(ids,new double[][]{{2,-1,.5}});
        var second=new ColocSusieInput(ids,new double[][]{{1,.2,-.5}});
        var options=new ColocOptions(1e-4,1e-4,5e-6,.5,true,new double[]{1,2,3},new double[]{3,1,2});
        var result=ColocSusie.analyze(first,second,options);
        for(String warning:result.warnings())System.err.println("WARNING: "+warning);
        System.out.println("H0..H4="+Arrays.toString(result.signalPairs().get(0).hypothesisPosteriors()));
        if(!result.isSharedVariantPosteriorDefined(0))throw new IllegalStateException("No defined conditional SNP posterior; do not report the zero marker");
        System.out.println("Native weighted conditional="+Arrays.toString(result.sharedVariantPosterior(0)));
    }
}
