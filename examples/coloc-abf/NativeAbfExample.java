import java.util.Arrays;
import java.util.List;
import org.jlinalg.coloc.ColocAbf;
import org.jlinalg.coloc.ColocAbfInput;

/** Synthetic supplied summary statistics; no file IO, R, clinical or historical-paper claim. */
public final class NativeAbfExample {
    public static void main(String[] args) {
        var ids=List.of("rs1","rs2","rs3");
        var first=new ColocAbfInput(ColocAbfInput.TraitType.QUANTITATIVE,ids,
            new double[]{.1,.5,-.15},new double[]{.01,.01,.02},null,null,null,1.0,null,null);
        var second=new ColocAbfInput(ColocAbfInput.TraitType.CASE_CONTROL,ids,
            new double[]{.08,.45,.02},new double[]{.01,.02,.02},null,null,null,null,null,null);
        var result=ColocAbf.analyze(first,second);
        System.out.println("H0..H4="+Arrays.toString(result.hypothesisPosteriors()));
        for(var variant:result.variants())System.out.println(variant.id()+"\t"+variant.sourceConditionalSharedPosterior());
        for(var warning:result.warnings())System.err.println("WARNING: "+warning);
    }
}
