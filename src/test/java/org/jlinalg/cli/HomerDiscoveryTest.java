package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jlinalg.sequence.HomerEnrichment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomerDiscoveryTest {
    @TempDir Path root;
    private record Fixture(Path work,Map<String,String> options,HomerCoreData.Data data,HomerDiscovery.Prepared prepared) { }
    private Map<String,String> options(Path directory)throws Exception{
        Files.createDirectories(directory);Files.writeString(directory.resolve("targets.fa"),">target1\nAACCGGTT\n>target2\nAANNCCGG\n>target3\nNNNNNNNN\n");
        Files.writeString(directory.resolve("background.fa"),">b1\nAACCAACC\n>b2\nTTGGTTGG\n>b3\nACGTACGT\n");
        var o=new LinkedHashMap<String,String>();o.put("targets",directory.resolve("targets.fa").toString());o.put("background",directory.resolve("background.fa").toString());
        o.put("strands","both");o.put("alphabet","strict-dna");o.put("duplicate-ids","error");o.put("overlap-policy","error");o.put("statistic","hypergeometric");o.put("normalization","none");
        o.put("motif-length","2");o.put("mismatches","0");o.put("motifs-to-find","2");o.put("discovery-mode","full-mask");return o;
    }
    private Fixture fixture(Path directory,Map<String,String> overrides)throws Exception{
        var o=options(directory.resolve("source"));o.putAll(overrides);Path work=directory.resolve("work");Files.createDirectory(work);
        var plan=HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS);var data=HomerCoreData.stage(o,"denovo",work,HomerCoreFixture.LIMITS);var prepared=HomerDiscovery.prepare(data,plan,o,work);return new Fixture(work,o,data,prepared);
    }
    private String motif(Fixture f,int t,int b,boolean thresholdCase){
        var statistic=f.options.get("statistic").equals("binomial")?HomerEnrichment.Statistic.BINOMIAL:HomerEnrichment.Statistic.HYPERGEOMETRIC;
        var stats=HomerEnrichment.test(new HomerEnrichment.Counts(3,t,3,b),statistic);int w=f.prepared.plan().width();
        String result=">"+"A".repeat(w)+"\t1-"+"A".repeat(w)+"\t"+(thresholdCase?"1.355835":"2.600000")+"\t"+String.format(Locale.ROOT,"%.6f",stats.logP())+"\t0\tT:"+t+".0("+String.format(Locale.ROOT,"%.2f",100.0*t/3)+"%),B:"+b+".0("+String.format(Locale.ROOT,"%.2f",100.0*b/3)+"%),P:1e"+stats.originalDisplayExponent()+"\n";
        return result+(".970\t.010\t.010\t.010\n").repeat(w);
    }
    private void original(Fixture f,String output)throws Exception{
        var seed=new StringBuilder("OligoSequence\tTarget Occurences\tBackground Occurences\tEnrichment(ln p, up to "+f.prepared.plan().mismatches()+" mismatches)\n");
        for(var oligo:f.prepared.oligos().values())seed.append(oligo.representative()).append('\t').append(String.format(Locale.ROOT,"%.1f",oligo.target())).append('\t').append(String.format(Locale.ROOT,"%.1f",oligo.background())).append('\t').append(oligo.target()<.01?"1.000":"-0.123").append('\n');
        Files.writeString(f.work.resolve("original-oligo-enrichment.tsv"),seed);Files.writeString(f.work.resolve("original.stdout"),output);
        Files.writeString(f.work.resolve("original.stderr"),"Finalizing Enrichment Statistics (new in v3.4)\nCleaning up temporary sequence and group files:\n");
        if(f.prepared.plan().normalization()==1){
            var norm=new StringBuilder("Oligo\tTargetCounts\tBackgroundCounts\tNormalizationFactor\n");for(char base:"ACGT".toCharArray())norm.append("\t\t").append(base).append("\t25.00%\t25.00%\t1.000\n");
            Files.writeString(f.work.resolve("original-normalization.tsv"),norm);
        }
    }
    private long audit(Fixture f,Map<String,Object> session,List<String> warnings)throws Exception{return HomerDiscovery.audit(f.work,f.data,f.prepared,f.options,session,warnings);}
    @Test void stagesNoSuppliedModelsAndRetainsAllFinalPopulationRecords()throws Exception{
        var f=fixture(root,Map.of());assertEquals(0,f.data.models().size());assertEquals(3,f.data.targetCount());assertEquals(3,f.data.backgroundCount());assertFalse(Files.exists(f.work.resolve("input-motifs")));
        assertEquals(42,f.prepared.rawWindows());assertEquals(32,f.prepared.validWindows());assertEquals(2,f.prepared.eligibleTargets());assertEquals(3,f.prepared.eligibleBackground());assertEquals(8,f.prepared.averageSequenceLength());
        assertEquals(3,f.prepared.oligos().get("AA").target());assertEquals(4,f.prepared.oligos().get("AA").background());f.data.verify();
    }
    @Test void weightedLongSequencesUseIntegerFloorMeanAndPreserveOriginalRoles()throws Exception{
        var o=options(root.resolve("source"));Files.writeString(Path.of(o.get("targets")),">t1\nAAAAAAAAAAAAAAAAA\n>t2\nACGT\n");
        Files.writeString(Path.of(o.get("background")),">b1\nCCCCCCCC\n>b2\nGGGGGGGG\n>b3\nTTTTTTTT\n");Path work=root.resolve("work");Files.createDirectory(work);
        var data=HomerCoreData.stage(o,"denovo",work,HomerCoreFixture.LIMITS);var prepared=HomerDiscovery.prepare(data,HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS),o,work);
        assertEquals(9,prepared.averageSequenceLength());assertEquals(9,prepared.oligos().get("AA").target());assertEquals(12,prepared.targetOligos());assertEquals(3.5,prepared.effectiveBackground(),1e-13);
    }
    @Test void planPreservesAllThreeOriginalDiscoveryModesAndExplicitParameters()throws Exception{
        var o=options(root.resolve("source"));
        for(String mode:List.of("full-mask","quick-mask-fixed","quick-mask-optimize")){
            o.put("discovery-mode",mode);var plan=HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS);var command=plan.command(o);
            assertEquals("denovo",command.get(0));assertFalse(command.contains("-m"));assertTrue(command.contains(mode.equals("full-mask")?"-fullMask":"-quickMask"));assertTrue(command.contains(mode.equals("quick-mask-fixed")?"-nozoops":"-zoops"));assertEquals(.04,plan.expectedSites());
        }
    }
    @Test void invalidOrSilentlyRetunedTrialAndSeedSettingsFailExplicitly()throws Exception{
        var o=options(root.resolve("source"));o.put("threads","4");assertThrows(IOException.class,()->HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS));o.put("trials","12");assertEquals(12,HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS).trials());
        o.put("minimum-seed-logp","-10");assertThrows(IOException.class,()->HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS));o.put("minimum-seed-logp","auto");assertEquals("auto",HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS).seedText());
    }
    @Test void normalizationOrderMustFitSourceIntegerAndMotifLengthBounds()throws Exception{
        var o=options(root.resolve("source"));o.put("normalization","oligo3");assertThrows(IOException.class,()->HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS));
        o.put("normalization","oligo16");assertThrows(IOException.class,()->HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS));o.put("normalization","oligo2");assertEquals(2,HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS).normalization());
    }
    @Test void nativeUniqueOligoNeighborhoodAndMemoryLimitsFailWithoutTruncation()throws Exception{
        int i=0;for(var settings:List.of(Map.of("maximum-unique-oligos","1"),Map.of("maximum-neighborhoods","1"),Map.of("maximum-upstream-memory-bytes","1"))){
            Path dir=root.resolve("case"+i++);assertThrows(IOException.class,()->fixture(dir,settings));
        }
    }
    @Test void missingCanonicalRoleWindowsFailWithoutDroppingInputRecords()throws Exception{
        var o=options(root.resolve("source"));o.put("overlap-policy","retain");Files.writeString(Path.of(o.get("background")),">n1\nNNNNNNNN\n>n2\nNNNNNNNN\n");Path work=root.resolve("work");Files.createDirectory(work);
        var data=HomerCoreData.stage(o,"denovo",work,HomerCoreFixture.LIMITS);assertEquals(2,data.backgroundCount());assertThrows(IOException.class,()->HomerDiscovery.prepare(data,HomerDiscovery.Plan.read(o,HomerCoreFixture.LIMITS),o,work));
    }
    @Test void completeOriginalModelAndStatisticsAreRetainedBesideDeterminedCounts()throws Exception{
        var f=fixture(root,Map.of());String output=motif(f,2,2,false);original(f,output);var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
        assertEquals(1,audit(f,session,warnings));assertEquals(output,Files.readString(f.work.resolve("learned-motifs.motif")));assertEquals(0L,session.get("models_with_presence_uncertainty"));assertTrue(Files.readString(f.work.resolve("results.tsv")).contains("exact-presence-determined"));assertTrue(warnings.stream().anyMatch(s->s.contains("sentinels")));
    }
    @Test void compatibleOriginalCountsAreNotOverwrittenByPrintedModelRescan()throws Exception{
        var f=fixture(root,Map.of("motif-length","1","motifs-to-find","1"));original(f,motif(f,0,0,true));var session=new LinkedHashMap<String,Object>();
        assertEquals(1,audit(f,session,new ArrayList<>()));assertEquals(1L,session.get("models_with_presence_uncertainty"));assertEquals(1L,session.get("models_with_printed_rescan_count_differences"));assertTrue(Files.readString(f.work.resolve("results.tsv")).contains("compatible-precision-uncertainty"));
    }
    @Test void reportedCountsOutsideConservativePresenceBoundsFail()throws Exception{
        var f=fixture(root,Map.of());original(f,motif(f,3,2,false));assertTrue(assertThrows(IOException.class,()->audit(f,new LinkedHashMap<>(),new ArrayList<>())).getMessage().contains("envelope"));
    }
    @Test void missingDuplicateOrIncorrectOligoRowsCannotBeAdmitted()throws Exception{
        int i=0;for(String change:List.of("missing","duplicate","count","sentinel")){
            var f=fixture(root.resolve("case"+i++),Map.of());original(f,motif(f,2,2,false));Path ledger=f.work.resolve("original-oligo-enrichment.tsv");var rows=Files.readAllLines(ledger);
            if(change.equals("missing"))rows.remove(1);else if(change.equals("duplicate"))rows.add(rows.get(1));else if(change.equals("sentinel")){String[] row=rows.get(1).split("\t");row[3]="1.500";rows.set(1,String.join("\t",row));}else{String[] row=rows.get(1).split("\t");row[1]="999.0";rows.set(1,String.join("\t",row));}
            Files.write(ledger,rows);assertThrows(IOException.class,()->audit(f,new LinkedHashMap<>(),new ArrayList<>()));
        }
    }
    @Test void finalizationAndInternalErrorLogsAreCheckedEvenWithZeroExit()throws Exception{
        int i=0;for(String stderr:List.of("help only\n","Something is wrong\nFinalizing Enrichment Statistics\nCleaning up temporary sequence and group files:\n")){
            var f=fixture(root.resolve("case"+i++),Map.of());original(f,motif(f,2,2,false));Files.writeString(f.work.resolve("original.stderr"),stderr);assertThrows(IOException.class,()->audit(f,new LinkedHashMap<>(),new ArrayList<>()));
        }
    }
    @Test void emptyDiscoveryRetainsZeroMotifWarningAfterCompleteFinalization()throws Exception{
        var f=fixture(root,Map.of());original(f,"");var warnings=new ArrayList<String>();assertEquals(0,audit(f,new LinkedHashMap<>(),warnings));assertTrue(warnings.stream().anyMatch(s->s.contains("NO DISCOVERED MOTIFS")));assertEquals(1,Files.readAllLines(f.work.resolve("results.tsv")).size());
    }
    @Test void normalizationSnapshotHasSourcePercentSemanticsAndCompleteWordFamily()throws Exception{
        var f=fixture(root,Map.of("normalization","oligo1"));original(f,motif(f,2,2,false));var warnings=new ArrayList<String>();assertEquals(1,audit(f,new LinkedHashMap<>(),warnings));assertTrue(warnings.stream().anyMatch(s->s.contains("not the final")||s.contains("not interpret it as the final")));
    }
    @Test void malformedOrMissingNormalizationWordsFail()throws Exception{
        var f=fixture(root,Map.of("normalization","oligo1"));original(f,motif(f,2,2,false));Path path=f.work.resolve("original-normalization.tsv");Files.writeString(path,"Oligo\tTargetCounts\tBackgroundCounts\tNormalizationFactor\n\t\tA\t25.00%\t25.00%\t1.000\n");assertThrows(IOException.class,()->audit(f,new LinkedHashMap<>(),new ArrayList<>()));
    }
    @Test void fractionalFinalCountsWrongCoarsePAndWrongLogPFail()throws Exception{
        int i=0;for(String change:List.of("fraction","coarse","log")){
            var f=fixture(root.resolve("case"+i++),Map.of());String model=motif(f,2,2,false);
            if(change.equals("fraction"))model=model.replace("T:2.0","T:2.1");else if(change.equals("coarse"))model=model.replace("P:1e0","P:1e-2");else model=model.replace("\t-0.223144\t","\t-0.900000\t");
            original(f,model);assertThrows(IOException.class,()->audit(f,new LinkedHashMap<>(),new ArrayList<>()));
        }
    }
}
