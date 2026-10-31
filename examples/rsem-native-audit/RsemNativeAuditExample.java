import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemCounts;
import org.jlinalg.sequence.RsemModel;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemResults;

/** Runs only the bundled synthetic native audit, without fitting an RSEM model. */
public final class RsemNativeAuditExample {
    private static final String EXPECTED = "656c1ed7a470a4b8c442b112c902846c81117a6c5765420958a795eb099d1884";
    private static final Set<String> MEMBERS = Set.of("SHA256SUMS","reference.ti","reference.seq","reference.grp",
            "sample.isoforms.results","sample.genes.results","sample.stat/sample.cnt","sample.stat/sample.model","sample.stat/sample.theta");
    private RsemNativeAuditExample() { }
    private static String hash(byte[] raw) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    /**
     * @param args bundled input ZIP and a new output directory
     * @throws Exception invalid input, existing output, failed audit or destination failure
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: RsemNativeAuditExample synthetic-original-output.zip NEW_OUTPUT");
        Path archive = Path.of(args[0]), output = Path.of(args[1]);
        if (!Files.isRegularFile(archive) || Files.size(archive) > 1_000_000) throw new IOException("Provide the bundled small synthetic ZIP");
        byte[] raw = Files.readAllBytes(archive);
        if (!hash(raw).equals(EXPECTED)) throw new IOException("Bundled example checksum differs; restore the original ZIP");
        Files.createDirectory(output);
        Path input = Files.createDirectory(output.resolve("input")); Set<String> seen = new HashSet<>(); long total = 0;
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !MEMBERS.contains(entry.getName()) || !seen.add(entry.getName())) throw new IOException("Unexpected or repeated synthetic member");
                Path path = input.resolve(entry.getName()); byte[] bytes = zip.readNBytes(1_000_001); total += bytes.length;
                if (bytes.length > 1_000_000 || total > 5_000_000) throw new IOException("Synthetic input exceeds its complete capacity");
                Files.createDirectories(path.getParent()); Files.write(path,bytes);
            }
        }
        if (!seen.equals(MEMBERS)) throw new IOException("Incomplete synthetic input bundle");
        var verified = new HashSet<String>();
        for (String line : Files.readAllLines(input.resolve("SHA256SUMS"),StandardCharsets.UTF_8)) {
            String[] fields = line.split("  ",2);
            if (fields.length != 2 || fields[1].equals("SHA256SUMS") || !MEMBERS.contains(fields[1]) || !verified.add(fields[1])
                    || !hash(Files.readAllBytes(input.resolve(fields[1]))).equals(fields[0])) throw new IOException("Synthetic member checksum differs");
        }
        if (verified.size() != 8) throw new IOException("Incomplete synthetic checksum population");
        var reference = RsemReference.read(input.resolve("reference"),new RsemReference.Limits(100,100,100000,1000,1000000,1000000));
        var counts = RsemCounts.read(input.resolve("sample.stat/sample.cnt"),1000,10000,1000,100000,100000);
        var fit = RsemModel.readFitState(input.resolve("sample.stat/sample.model"),input.resolve("sample.stat/sample.theta"),
                counts,reference.transcripts().size(),RsemAbundance.Release.V1_3_3,
                new RsemModel.Limits(100,10000,10000,10000,100000,1000000,1000000));
        var result = RsemResults.audit(input.resolve("sample.isoforms.results"),input.resolve("sample.genes.results"),reference,counts,fit,
                RsemAbundance.Release.V1_3_3,RsemResults.Posterior.INTERVAL,false,
                new RsemResults.Limits(100,100,100,10000,100000,1000000,1000000));
        var lines = new ArrayList<String>();
        lines.add("status=completed-native-synthetic-result-audit"); lines.add("release="+result.release());
        lines.add("transcripts="+result.transcripts()); lines.add("genes="+result.genes()); lines.add("numeric_cells="+result.numericCells());
        lines.add("requested_posterior="+result.requestedPosterior()); lines.add("actual_posterior="+result.actualPosterior());
        for (String warning : result.warnings()) lines.add("WARNING: "+warning);
        var evidence = new ArrayList<>(result.inputEvidence()); evidence.addAll(result.resultEvidence());
        for (var item : evidence) lines.add(item.sha256()+"  "+item.path());
        Files.write(output.resolve("audit-summary.txt"),lines,StandardCharsets.UTF_8);
        System.out.println("Audited "+result.transcripts()+" transcripts, "+result.genes()+" genes and "+result.numericCells()+" numeric fields. See "+output.resolve("audit-summary.txt"));
    }
}
