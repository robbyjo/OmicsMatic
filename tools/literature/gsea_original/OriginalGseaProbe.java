// Development reference composition. Uses the unchanged upstream API; no project statistics.
import edu.mit.broad.genome.Headers;
import edu.mit.broad.genome.alg.DatasetGenerators;
import edu.mit.broad.genome.alg.Metrics;
import edu.mit.broad.genome.alg.gsea.GeneSetCohort;
import edu.mit.broad.genome.alg.gsea.GeneSetScoringTables;
import edu.mit.broad.genome.alg.gsea.KSTests;
import edu.mit.broad.genome.alg.gsea.PValueCalculatorImpls;
import edu.mit.broad.genome.math.Order;
import edu.mit.broad.genome.math.RandomSeedGenerators;
import edu.mit.broad.genome.math.SortMode;
import edu.mit.broad.genome.math.Vector;
import edu.mit.broad.genome.objects.Dataset;
import edu.mit.broad.genome.objects.GeneSet;
import edu.mit.broad.genome.objects.RankedList;
import edu.mit.broad.genome.objects.Template;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentDb;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentResult;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentScore;
import edu.mit.broad.genome.objects.strucs.DatasetTemplate;
import edu.mit.broad.genome.objects.strucs.TemplateRandomizerType;
import edu.mit.broad.genome.parsers.ParserFactory;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Full float precision export: the original EDB writer rounds vectors to four decimals. */
public final class OriginalGseaProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 8) throw new IllegalArgumentException(
                "mode input cls_with_selector gmt nperm seed min_size max_size; stdout logs, result path via -Dprobe.output");
        // Match AbstractTool's constructor initialization before any parser/cache access.
        edu.mit.broad.xbench.core.api.Application.registerHandler(new xtools.api.XToolsApplication());
        String mode = args[0];
        File input = new File(args[1]);
        GeneSet[] sets = ParserFactory.readGeneSetMatrix(new File(args[3]), false).getGeneSets();
        int nperm = Integer.parseInt(args[4]);
        RandomSeedGenerators.Custom random = new RandomSeedGenerators.Custom(Long.parseLong(args[5]));
        GeneSetCohort.Generator generator = new GeneSetCohort.Generator(new GeneSetScoringTables.Weighted(),
                Integer.parseInt(args[6]), Integer.parseInt(args[7]));
        KSTests tests = new KSTests(System.out);
        EnrichmentDb db;
        if (mode.equals("preranked")) {
            RankedList ranks = ParserFactory.readRankedList(input);
            db = tests.executeGsea(ranks, sets, nperm, random, null, generator);
        } else {
            if (!mode.equals("expression-phenotype") && !mode.equals("expression-geneset"))
                throw new IllegalArgumentException("Unknown original API mode");
            Dataset dataset = ParserFactory.readDataset(input, false, false);
            Template template = ParserFactory.readTemplate(new File(args[2]), false, false, false);
            DatasetTemplate matched = new DatasetGenerators().extract(dataset, template);
            db = tests.executeGsea(matched, sets, nperm, Metrics.lookupMetric("Signal2Noise"), SortMode.REAL,
                    Order.DESCENDING, random, TemplateRandomizerType.NO_BALANCE,
                    Map.of(Headers.USE_MEDIAN, Boolean.FALSE), generator, mode.equals("expression-phenotype"), 100, null);
        }
        EnrichmentResult[] results = new PValueCalculatorImpls.GseaImpl("meandiv").calcNPValuesAndFDR(db.getResults());
        Path output = Path.of(System.getProperty("probe.output"));
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            writer.println("kind\tset_or_rank\tvalues");
            RankedList ranked = db.getRankedList();
            for (int i = 0; i < ranked.getSize(); i++) {
                writer.println("RANK\t" + i + "\t" + ranked.getRankName(i) + "\t" + Float.toString(ranked.getScore(i)));
            }
            for (EnrichmentResult result : results) {
                EnrichmentScore score = result.getScore();
                String name = result.getGeneSetName();
                writer.println("SCORE\t" + name + "\t" + score.getES() + "\t" + score.getNES() + "\t" + score.getNP()
                        + "\t" + score.getFDR() + "\t" + score.getFWER() + "\t" + score.getRankAtES()
                        + "\t" + score.getRankScoreAtES());
                writer.print("MEMBERS\t" + name);
                for (String member : result.getGeneSet().getMembers()) writer.print("\t" + member);
                writer.println();
                writeVector(writer, "NULL", name, result.getRndESS());
                writeVector(writer, "HIT_PROFILE", name, score.getESProfile());
                writeVector(writer, "FULL_PROFILE", name, score.getESProfile_point_by_point_opt());
                writer.print("HITS\t" + name);
                for (int hit : score.getHitIndices()) writer.print("\t" + hit);
                writer.println();
            }
        }
        System.out.println("EXPORTED_ORIGINAL_FULL_FLOAT_OBJECTS\t" + results.length + "\t" + nperm);
    }

    private static void writeVector(PrintWriter writer, String kind, String name, Vector vector) {
        writer.print(kind + "\t" + name);
        if (vector != null) for (int i = 0; i < vector.getSize(); i++) writer.print("\t" + Float.toString(vector.getElement(i)));
        writer.println();
    }
}
