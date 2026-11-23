import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jlinalg.enrichment.PyScenicAucell;

/** Artificial expression values; no fitted network or biological interpretation. */
public final class PyScenicAucellExample {
    private PyScenicAucellExample() { }
    public static void main(String[] arguments) {
        var cells=List.of("cell-B","NA");
        var genes=List.of("TF","target-A","target-B","g3","g4","g5","g6","g7","g8","g9",
                "g10","g11","g12","g13","g14","g15","g16","g17","g18","g19");
        double[][] expression={{10,8,6,4,2,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
                {0,0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17}};
        var signatures=List.of(new PyScenicAucell.Signature("supplied-regulon",Map.of("target-A",2.,"target-B",1.)));
        var options=new PyScenicAucell.Options(.25,false,false,17,PyScenicAucell.Layout.SERIAL_UNSTACK,PyScenicAucell.Limits.defaults());
        var result=PyScenicAucell.scoreExpression(cells,genes,expression,signatures,options);
        System.out.println(PyScenicAucell.PROFILE);
        for(int i=0;i<result.cells().size();i++)System.out.println(result.cells().get(i)+"\t"+Arrays.toString(result.scores()[i])+"\teligible="+result.eligible(i,0));
        System.out.println(result.diagnostics().get(0));
    }
}
