import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.jlinalg.genetics.PlinkAssociations;

/** Minimal import of one complete declared additive association family. */
public final class AssociationImportExample {
    private AssociationImportExample() { }
    public static void main(String[] args) throws Exception {
        if (args.length!=5) throw new IllegalArgumentException("REPORT BIM plink1|plink2 linear|logistic|firth|firth-fallback COHORT_SAMPLE_COUNT");
        Path bim=Path.of(args[1]);if (Files.size(bim)>128L*1024*1024) throw new IllegalArgumentException("This small example caps BIM metadata at128MiB");
        var alleles=new LinkedHashMap<String,PlinkAssociations.Alleles>();
        try(var reader=Files.newBufferedReader(bim)) {
            String line;while ((line=reader.readLine())!=null) {
                String[] fields=line.strip().split("\\s+");if (fields.length!=6 || alleles.size()>=100000) throw new IllegalArgumentException("Require a bounded six-field BIM;example maximum100000variants");
                if (alleles.putIfAbsent(fields[1],new PlinkAssociations.Alleles(fields[4],fields[5]))!=null) throw new IllegalArgumentException("Duplicate BIM variant ID");
            }
        }
        var engine=PlinkAssociations.Engine.valueOf(args[2].toUpperCase(Locale.ROOT));
        var model=PlinkAssociations.Model.valueOf(args[3].toUpperCase(Locale.ROOT).replace('-','_'));
        System.out.println("variant\teffect_allele\tother_allele\tstatus\tbeta\tOR\tSE\tP\tnegative_log10_P\tupstream_error");
        var counts=PlinkAssociations.scan(Path.of(args[0]),engine,model,alleles,Integer.parseInt(args[4]),PlinkAssociations.Limits.defaults(),row ->
            System.out.println(row.variant()+"\t"+row.effectAllele()+"\t"+row.otherAllele()+"\t"+row.status()+"\t"+number(row.beta())+"\t"+number(row.oddsRatio())+"\t"+number(row.standardError())+"\t"+number(row.pValue())+"\t"+number(row.negativeLog10PValue())+"\t"+row.upstreamError()));
        System.err.println(counts);
        System.err.println("Review status/error and raw source fields; discard partial stdout if import fails. Binary beta is log of the reported rounded OR.");
    }
    private static String number(double value) { return Double.isNaN(value)?"":Double.toString(value); }
}
