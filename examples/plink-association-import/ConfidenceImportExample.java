import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.jlinalg.genetics.PlinkAssociations;
import org.jlinalg.genetics.PlinkConfidenceIntervals;

/** Imports reported limits; the level must come from original invocation provenance. */
public final class ConfidenceImportExample {
    private ConfidenceImportExample() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=6)throw new IllegalArgumentException("REPORT BIM ENGINE MODEL COHORT_N ORIGINAL_REQUESTED_LEVEL");
        var engine=PlinkAssociations.Engine.valueOf(args[2].toUpperCase(Locale.ROOT));var model=PlinkAssociations.Model.valueOf(args[3].toUpperCase(Locale.ROOT).replace('-','_'));double level=Double.parseDouble(args[5]);PlinkConfidenceIntervals.validateLevel(level);
        Path bim=Path.of(args[1]);if(Files.size(bim)>128L*1024*1024)throw new IllegalArgumentException("Example BIM limit is128MiB");var alleles=new LinkedHashMap<String,PlinkAssociations.Alleles>();
        try(var reader=Files.newBufferedReader(bim)){for(String text;(text=reader.readLine())!=null;){String[] f=text.strip().split("\\s+");if(f.length!=6 || alleles.size()>=100000)throw new IllegalArgumentException("Require bounded six-field BIM");if(alleles.putIfAbsent(f[1],new PlinkAssociations.Alleles(f[4],f[5]))!=null)throw new IllegalArgumentException("Duplicate BIM variant ID");}}
        System.out.println("variant\tea\toa\tbeta\tstandard_error\tconfidence_level_requested\tci_scale\tci_lower\tci_upper\tci_status\teffect_status\tupstream_error");
        PlinkAssociations.scan(Path.of(args[0]),engine,model,alleles,Integer.parseInt(args[4]),PlinkAssociations.Limits.defaults(),row->{
            try {var ci=PlinkConfidenceIntervals.read(row,engine,model,level);System.out.println(row.variant()+"\t"+row.effectAllele()+"\t"+row.otherAllele()+"\t"+number(row.beta())+"\t"+number(row.standardError())+"\t"+ci.requestedLevel()+"\t"+ci.scale()+"\t"+number(ci.lower())+"\t"+number(ci.upper())+"\t"+ci.status()+"\t"+row.status()+"\t"+row.upstreamError());}
            catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        });
        System.err.println("Discard partial stdout if import fails. The level must be established by original command provenance; rounded headers cannot prove it. Bounds are imported, not recalculated. Binary SE is log-odds SE; binary limits retain OR scale.");
    }
    private static String number(double value){return Double.isNaN(value)?"":Double.toString(value);}
}
