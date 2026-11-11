package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RsemResultsTest {
    @TempDir static Path fixtures;
    private static final String[] RELEASES = {"1.2.28","1.3.0","1.3.1","1.3.2","1.3.3"};
    private static final RsemReference.Limits REF = new RsemReference.Limits(1000,1000,1_000_000,10000,5_000_000,1_000_000);
    private static final RsemModel.Limits MODEL = new RsemModel.Limits(1000,100000,100000,100000,1_000_000,5_000_000,1_000_000);
    private static final RsemResults.Limits LIMITS = new RsemResults.Limits(1000,1000,1000,100000,1_000_000,5_000_000,1_000_000);
    private static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void unpackOriginalFilesAndVerifyEveryPayload() throws Exception {
        for (String name : new String[]{"reference-model-fixtures.zip","result-fixtures.zip","writer-fixtures.zip"}) {
            Path root = name.startsWith("writer-") ? fixtures.resolve("writer") : name.startsWith("result-") ? fixtures.resolve("extra") : fixtures;
            byte[] archive;
            try (var stream = RsemResultsTest.class.getResourceAsStream("/org/jlinalg/sequence/rsem-reference/" + name)) {
                assertNotNull(stream); archive = stream.readAllBytes();
            }
            if (name.startsWith("result-")) assertEquals("b8ed9638384dccf91b09ac124d84313c6815b1eac9bb4bcef8f0477e032adddb",sha(archive));
            if (name.startsWith("writer-")) assertEquals("a0938c035a0f57a0ad0583dda1aa4ad476d8f900465530bf0e89b2356c68c954",sha(archive));
            int files = 0; long bytes = 0;
            try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path target = root.resolve(entry.getName()).normalize(); assertTrue(target.startsWith(root)); assertFalse(entry.isDirectory());
                    byte[] data = zip.readNBytes(5_000_001); assertTrue(data.length <= 5_000_000); bytes += data.length; assertTrue(bytes < 20_000_000); assertTrue(++files < 1000);
                    Files.createDirectories(target.getParent()); Files.write(target,data);
                }
            }
            assertEquals(name.startsWith("writer-") ? 121 : name.startsWith("result-") ? 87 : 306,files);
            for (String line : Files.readAllLines(root.resolve("SHA256SUMS"))) {
                String[] fields = line.split("  ",2); assertEquals(fields[0],sha(Files.readAllBytes(root.resolve(fields[1]))));
            }
        }
    }
    private record Input(RsemReference.Reference reference, RsemCounts.Counts counts, RsemModel.FitState fit,
                         RsemAbundance.Release release, Path iso, Path gene, boolean names, RsemResults.Posterior posterior) { }
    private static Input input(String version, String kind) throws IOException {
        boolean extra = kind.startsWith("genome-") || kind.equals("posterior-mean-only") || kind.equals("no-alignments-with-posterior");
        boolean writer = kind.contains("epsilon") || kind.contains("tiny-weighted");
        Path root = writer ? fixtures.resolve("writer") : extra ? fixtures.resolve("extra") : fixtures;
        String referenceKind = kind.startsWith("genome-") ? "genome" : kind.startsWith("polyA-") ? "polyA" : "transcripts";
        var ref = RsemReference.read(fixtures.resolve(version + "/" + referenceKind + "/reference"),REF);
        Path prefix = root.resolve(version + "/" + kind + "/sample");
        var counts = RsemCounts.read(Path.of(prefix + ".stat/sample.cnt"),1000000,10000000,100000,5000000,1000000);
        var release = RsemAbundance.Release.valueOf("V" + version.replace('.','_'));
        var fit = RsemModel.readFitState(Path.of(prefix + ".stat/sample.model"),Path.of(prefix + ".stat/sample.theta"),counts,ref.transcripts().size(),release,MODEL);
        var mode = kind.equals("paired-posterior") || kind.equals("no-alignments-with-posterior") ? RsemResults.Posterior.INTERVAL
                : kind.equals("posterior-mean-only") ? RsemResults.Posterior.MEAN : RsemResults.Posterior.NONE;
        return new Input(ref,counts,fit,release,Path.of(prefix + ".isoforms.results"),Path.of(prefix + ".genes.results"),kind.equals("genome-appended-names"),mode);
    }
    private static RsemResults.Audit audit(Input x, Path iso, Path gene) throws IOException {
        return RsemResults.audit(iso,gene,x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),x.names(),LIMITS);
    }
    private static Path change(Path dir, Path source, int row, int column, String value) throws IOException {
        var lines = new ArrayList<>(Files.readAllLines(source)); String[] fields = lines.get(row).split("\t",-1); fields[column] = value; lines.set(row,String.join("\t",fields));
        Path target = dir.resolve(source.getFileName()); Files.writeString(target,String.join("\n",lines)+"\n",StandardCharsets.UTF_8); return target;
    }
    @Test void allFiftyNineOriginalResultPairsConsumeEveryRowAndField() throws Exception {
        int pairs = 0, isoforms = 0, genes = 0, learned = 0; long cells = 0;
        for (String release : RELEASES) {
            var kinds = new ArrayList<>(List.of("single-no-quality","paired-no-quality","single-no-fragment-prior","polyA-estimated-start",
                    "paired-em","single-em","reverse-em","paired-posterior","genome-raw-ids","genome-appended-names","posterior-mean-only"));
            if (release.equals("1.3.2") || release.equals("1.3.3")) kinds.addAll(List.of("noise","no-alignments-with-posterior"));
            for (String kind : kinds) {
                Input x = input(release,kind); var rows = new ArrayList<RsemResults.Row>();
                var sink = new RsemResults.Sink() {
                    @Override public void isoform(RsemResults.Row row) {rows.add(row);}
                    @Override public void gene(RsemResults.Row row) {rows.add(row);}
                };
                var result = RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),x.names(),LIMITS,sink);
                assertEquals(x.reference().transcripts().size(),result.transcripts()); assertEquals(4,result.genes());
                assertEquals(result.transcripts()+result.genes(),rows.size()); assertEquals(result.numericCells(),rows.stream().mapToLong(r -> r.values().size()).sum());
                assertEquals(sha(Files.readAllBytes(x.iso())),result.resultEvidence().get(0).sha256()); assertEquals(sha(Files.readAllBytes(x.gene())),result.resultEvidence().get(1).sha256());
                assertEquals(0,result.precisionAmbiguousGroups()); assertFalse(result.warnings().isEmpty());
                pairs++; isoforms+=result.transcripts(); genes+=result.genes(); cells+=result.numericCells(); if(result.learnedModel())learned++;
            }
        }
        assertEquals(59,pairs); assertEquals(334,isoforms); assertEquals(236,genes); assertEquals(3944,cells); assertEquals(55,learned);
    }
    @Test void commaIdentifiersAndAppendedUnicodeNamesAreNeverSplitOrNormalized() throws Exception {
        Input x = input("1.3.3","genome-appended-names"); var rows = new ArrayList<RsemResults.Row>();
        RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),true,LIMITS,new RsemResults.Sink() {
            @Override public void isoform(RsemResults.Row row) {rows.add(row);}
            @Override public void gene(RsemResults.Row row) { }
        });
        assertEquals("iso,comma.1",rows.get(0).rawId()); assertEquals(List.of("gene,comma"),rows.get(0).rawAssociatedIds());
        assertEquals("geneA_Gene Alpha β",rows.get(1).displayedAssociatedIds());
        assertThrows(UnsupportedOperationException.class,() -> rows.get(0).values().clear());
    }
    @Test void allTwentyFourOriginalWriterProbesPreserveSubPrintedExpressionBranches() throws Exception {
        int pairs=0,rows=0;long cells=0;
        for(String version:RELEASES){
            var modes=new ArrayList<>(List.of("below-epsilon","equal-epsilon","above-epsilon","tiny-weighted"));
            if(version.equals("1.3.3"))modes.addAll(List.of("strict-below-epsilon","strict-equal-epsilon","strict-above-epsilon","strict-tiny-weighted"));
            for(String kind:modes){
                Input x=input(version,kind);var result=audit(x,x.iso(),x.gene());pairs++;rows+=result.transcripts()+result.genes();cells+=result.numericCells();
                String[] gene=Files.readAllLines(x.gene()).get(1).split("\t");assertEquals("0.00",gene[5]);
                if(kind.endsWith("below-epsilon"))assertEquals("900.00",gene[2]);
                if(kind.endsWith("above-epsilon"))assertEquals("800.00",gene[2]);
                if(kind.endsWith("tiny-weighted"))assertEquals("854.52",gene[2]);
            }
        }
        assertEquals(24,pairs);assertEquals(240,rows);assertEquals(1344,cells);
    }
    @Test void printedZeroTpmDoesNotImplyUnweightedGeneLengthOrZeroIsoformPercent() throws Exception {
        Input x=input("1.3.3","above-epsilon");audit(x,x.iso(),x.gene());
        String[] iso=Files.readAllLines(x.iso()).get(1).split("\t");String[] gene=Files.readAllLines(x.gene()).get(1).split("\t");
        assertEquals("0.00",iso[5]);assertEquals("100.00",iso[7]);assertEquals("800.00",gene[2]);
    }
    @Test void sourceCallerThresholdDoesNotCertifyTheRecomputedHiddenWriterBranch() throws Exception {
        Input production=input("1.3.3","equal-epsilon"),strict=input("1.3.3","strict-equal-epsilon");
        var a=audit(production,production.iso(),production.gene());var b=audit(strict,strict.iso(),strict.gene());
        assertEquals(1,a.precisionAmbiguousGroups());assertEquals(1,b.precisionAmbiguousGroups());
        assertTrue(a.warnings().stream().anyMatch(s->s.contains("EPSILON")));assertTrue(b.warnings().stream().anyMatch(s->s.contains("EPSILON")));
        assertEquals("800.00",Files.readAllLines(production.gene()).get(1).split("\t")[2]);
        assertEquals("0.00",Files.readAllLines(strict.gene()).get(1).split("\t")[2]);
    }
    @Test void requestedPosteriorWithoutAlignmentsHasAnExplicitEmOnlyActualSchema() throws Exception {
        Input x=input("1.3.3","no-alignments-with-posterior");var a=audit(x,x.iso(),x.gene());
        assertEquals(RsemResults.Posterior.INTERVAL,a.requestedPosterior());assertEquals(RsemResults.Posterior.NONE,a.actualPosterior());
        assertFalse(a.learnedModel());assertNull(a.posteriorTpm());assertTrue(a.warnings().stream().anyMatch(s->s.contains("did not run")));
    }
    @Test void precomputedHeaderDoesNotOverrideRequestedPosteriorMode() throws Exception {
        Input x=input("1.3.3","posterior-mean-only");
        assertThrows(IOException.class,()->RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),RsemResults.Posterior.INTERVAL,false,LIMITS));
    }
    @Test void zeroPosteriorCountCanRetainPositivePosteriorTpm() throws Exception {
        Input x=input("1.3.3","posterior-mean-only");audit(x,x.iso(),x.gene());
        String[] gene=Files.readAllLines(x.gene()).get(3).split("\t");assertEquals("0.00",gene[7]);assertTrue(Double.parseDouble(gene[9])>0);
    }
    @Test void genePosteriorStandardDeviationIsNotTheSumOfIsoformDeviations() throws Exception {
        Input x=input("1.3.3","posterior-mean-only");audit(x,x.iso(),x.gene());
        var iso=Files.readAllLines(x.iso());String[] gene=Files.readAllLines(x.gene()).get(1).split("\t");
        double sum=Double.parseDouble(iso.get(1).split("\t")[9])+Double.parseDouble(iso.get(2).split("\t")[9]);
        assertEquals(0,Double.parseDouble(gene[8]));assertTrue(sum>0);
    }
    @Test void completePosteriorCountFamilyCannotExceedAlignedGroups(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","posterior-mean-only");Path badIso=change(dir,x.iso(),1,8,"100.00");
        var iso=Files.readAllLines(badIso);double total=100+Double.parseDouble(iso.get(2).split("\t")[8]);
        Path badGene=change(dir,x.gene(),1,7,String.format(java.util.Locale.ROOT,"%.2f",total));
        assertThrows(IOException.class,()->audit(x,badIso,badGene));
    }
    @Test void countStandardDeviationCannotExceedTheCompleteReadPopulation(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","posterior-mean-only");Path bad=change(dir,x.gene(),1,8,"40.00");
        assertThrows(IOException.class,()->audit(x,x.iso(),bad));
    }
    @Test void arbitraryModelIncompatibleEffectiveLengthFails(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-em");Path bad=change(dir,x.iso(),1,3,"600.00");
        assertTrue(assertThrows(IOException.class,()->audit(x,bad,x.gene())).getMessage().contains("effective length"));
    }
    @Test void swappedExactIdsAndCommaJoinsFail(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-em");Path badGene=change(dir,x.gene(),1,1,"tA2.1,tA1.1");assertThrows(IOException.class,()->audit(x,x.iso(),badGene));
        Path badIso=change(dir,x.iso(),1,0,"tA1");assertThrows(IOException.class,()->audit(x,badIso,x.gene()));
    }
    @Test void wrongAppendNamesSelectionFails() throws Exception {
        Input x=input("1.3.3","genome-appended-names");
        assertThrows(IOException.class,()->RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),false,LIMITS));
    }
    @Test void geneCountsMustMatchCompleteIsoforms(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-em");Path bad=change(dir,x.gene(),1,4,"21.00");assertThrows(IOException.class,()->audit(x,x.iso(),bad));
    }
    @Test void geneLengthsRemainEmWeightedWhenPosteriorColumnsArePresent(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-posterior");Path bad=change(dir,x.gene(),3,2,"610.00");assertThrows(IOException.class,()->audit(x,x.iso(),bad));
    }
    @Test void expressionValuesAreBoundToSavedTheta(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-em");Path bad=change(dir,x.iso(),1,5,"1.00");assertThrows(IOException.class,()->audit(x,bad,x.gene()));
    }
    @Test void withinGenePercentagesAreNotExpectedCountFractions(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-em");Path bad=change(dir,x.iso(),1,7,"50.00");assertThrows(IOException.class,()->audit(x,bad,x.gene()));
    }
    @Test void posteriorIntervalsRetainTheirOwnFormatAndOrder(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-posterior");Path bad=change(dir,x.iso(),1,13,"999999");assertThrows(IOException.class,()->audit(x,bad,x.gene()));
        Path malformed=change(dir,x.iso(),1,13,"1234567");assertThrows(IOException.class,()->audit(x,malformed,x.gene()));
    }
    @Test void impossibleQuartileVariationFails(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-posterior");Path bad=change(dir,x.gene(),1,13,"1.1");assertThrows(IOException.class,()->audit(x,x.iso(),bad));
    }
    @Test void nonfiniteNegativeAndLostFieldsFail(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-em");
        for(String value:List.of("NaN","Infinity","-1.00","1.000")){Path bad=change(dir,x.iso(),1,4,value);assertThrows(IOException.class,()->audit(x,bad,x.gene()));}
        var lines=Files.readAllLines(x.gene());Path bad=dir.resolve("missing.tsv");Files.writeString(bad,String.join("\n",lines.subList(0,lines.size()-1))+"\n");assertThrows(IOException.class,()->audit(x,x.iso(),bad));
    }
    @Test void extraRowsAndCrLfFail(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-em");Path bad=dir.resolve("extra.tsv");Files.writeString(bad,Files.readString(x.iso())+Files.readAllLines(x.iso()).get(1)+"\n");assertThrows(IOException.class,()->audit(x,bad,x.gene()));
        Files.writeString(bad,Files.readString(x.iso()).replace("\n","\r\n"));assertThrows(IOException.class,()->audit(x,bad,x.gene()));
    }
    @Test void noAlignmentRawEffectiveLengthsAndTrueZerosAreRequired(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","noise");Path bad=change(dir,x.iso(),1,3,"0.00");assertThrows(IOException.class,()->audit(x,bad,x.gene()));
        Path badGene=change(dir,x.gene(),1,4,"0.01");assertThrows(IOException.class,()->audit(x,x.iso(),badGene));
    }
    @Test void modelZeroEffectiveLengthCannotInventPosteriorIntervals(@TempDir Path dir) throws Exception {
        Input x=input("1.3.3","paired-posterior");Path bad=change(dir,x.iso(),6,14,"1");assertThrows(IOException.class,()->audit(x,bad,x.gene()));
    }
    @Test void outputBudgetAppliesToCompletePopulation() throws Exception {
        Input x=input("1.3.3","paired-posterior");
        var small=new RsemResults.Limits(100,100,100,100000,100,5000000,1000000);
        assertThrows(IOException.class,()->RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),false,small));
        var group=new RsemResults.Limits(100,100,1,100000,10000,5000000,1000000);
        assertThrows(IOException.class,()->RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),false,group));
    }
    @Test void byteLineAndSavedLengthSupportCapacitiesApplyBeforeCompletion() throws Exception {
        Input x=input("1.3.3","paired-em");
        for(var small:List.of(new RsemResults.Limits(100,100,100,1,10000,5000000,1000000),
                new RsemResults.Limits(100,100,100,100000,10000,10,1000000),new RsemResults.Limits(100,100,100,100000,10000,5000000,5)))
            assertThrows(IOException.class,()->RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),false,small));
    }
    @Test void sinkFailureCannotReturnACompletedAudit() throws Exception {
        Input x=input("1.3.3","paired-em");
        assertThrows(IOException.class,()->RsemResults.audit(x.iso(),x.gene(),x.reference(),x.counts(),x.fit(),x.release(),x.posterior(),false,LIMITS,new RsemResults.Sink(){
            @Override public void isoform(RsemResults.Row row)throws IOException{throw new IOException("destination full");}
            @Override public void gene(RsemResults.Row row){ }
        }));
    }
}
