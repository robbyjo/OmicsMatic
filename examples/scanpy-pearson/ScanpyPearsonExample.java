import java.util.Arrays;
import java.util.List;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;
import org.jlinalg.singlecell.ScanpyPearsonResiduals;
import org.jlinalg.singlecell.ScanpyPearsonResiduals.*;

/** Pure-Java synthetic example: two cells, three genes, including a zero-count gene. */
public final class ScanpyPearsonExample {
    private ScanpyPearsonExample() { }
    public static void main(String[] args) {
        Matrix counts=new Matrix(List.of("gene-A","gene-zero","gene-C"),List.of("001","002"),
            new int[]{0,2,4},new int[]{0,2,0,2},new double[]{3,1,1,4});
        for(Profile profile:Profile.values()) {
            var result=ScanpyPearsonResiduals.normalize(counts,Storage.CSR,profile,Options.defaults(),Limits.defaults(),()->false);
            System.out.println(profile+" usable="+result.usable()+" effective_clip="+result.effectiveClip());
            System.out.println((result.usable()?"complete_matrix=":"diagnostic_only=")+Arrays.toString(result.usable()?result.matrix().values():result.diagnosticValues()));
            for(var warning:result.warnings())System.out.println(warning.code()+": "+warning.message()+" Action: "+warning.requiredAction());
        }
    }
}
