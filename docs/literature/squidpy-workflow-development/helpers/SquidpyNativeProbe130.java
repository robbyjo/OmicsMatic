import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import org.jlinalg.squidpy.SquidpyAutocorrelation;

public final class SquidpyNativeProbe130 {
    private SquidpyNativeProbe130() { }
    private static int[] ints(List<?> x) {return x.stream().mapToInt(v->Integer.parseInt(((GprofilerJson.Numeric)v).token())).toArray();}
    private static double[] doubles(List<?> x) {return x.stream().mapToDouble(v->Double.parseDouble(((GprofilerJson.Numeric)v).token())).toArray();}
    public static void main(String[] args) throws Exception {
        Map<?,?> input=(Map<?,?>)GprofilerJson.read(Files.readAllBytes(Path.of(args[0])));
        System.out.println("graph\tfeature\tedition\tmode\tnormalized\ttwo_tailed\tscore\texpected\tvariance\tp_value\tstatus");
        for(Object item:(List<?>)input.get("graphs")) {
            Map<?,?> graph=(Map<?,?>)item;
            int[] rows=ints((List<?>)graph.get("row_pointers")),columns=ints((List<?>)graph.get("columns"));
            double[] weights=doubles((List<?>)graph.get("weights"));
            for(var edition:SquidpyAutocorrelation.Edition.values())
                for(var statistic:SquidpyAutocorrelation.Statistic.values())
                    for(boolean normalized:new boolean[]{false,true})for(boolean two:new boolean[]{false,true})
                        for(var feature:((Map<?,?>)graph.get("features")).entrySet()) {
                            var result=SquidpyAutocorrelation.compute(edition,statistic,doubles((List<?>)feature.getValue()),rows,columns,weights,normalized,two);
                            System.out.println(String.join("\t",graph.get("name").toString(),feature.getKey().toString(),edition.toString(),statistic.toString().toLowerCase(Locale.ROOT),Boolean.toString(normalized),Boolean.toString(two),Double.toString(result.score()),Double.toString(result.expected()),Double.toString(result.normalityVariance()),Double.toString(result.normalityPValue()),result.status().toString()));
                        }
        }
    }
}
