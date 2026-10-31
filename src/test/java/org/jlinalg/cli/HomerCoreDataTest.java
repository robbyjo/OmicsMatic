package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomerCoreDataTest {
    @TempDir Path root;
    @Test void completePresenceCountsKeepSequencePopulationsAndSafeAliases()throws Exception{
        var data=HomerCoreFixture.data(root,"known");assertEquals(2,data.targetCount());assertEquals(2,data.backgroundCount());assertEquals(3,data.hits());
        assertArrayEquals(new long[]{1,1},data.presence()[0]);assertEquals("α",data.sequences().get(0).id());assertEquals("T000000001",data.sequences().get(0).alias());
        assertTrue(Files.readString(root.resolve("work/models.tsv")).contains("model α"));assertTrue(Files.readString(root.resolve("work/stage-motifs.motif")).startsWith(">N\tM000000001\t2.6"));
        assertTrue(Files.readString(root.resolve("work/sequences.tsv")).contains("α source header"));data.verify();
    }
    @Test void stageDoesNotChangeSourceBytesAndPostMutationIsDetected()throws Exception{
        var data=HomerCoreFixture.data(root,"find");Path source=root.resolve("source/targets.fa");String before=Files.readString(source);
        assertEquals(before,Files.readString(root.resolve("work/input-targets")));Files.writeString(source,before+"\n");assertThrows(IOException.class,data::verify);
    }
    @Test void changedStagedModelsAreDetectedIndependentlyOfOriginalSnapshots()throws Exception{
        var data=HomerCoreFixture.data(root,"find");Files.writeString(root.resolve("work/stage-motifs.motif"),"changed");assertThrows(IOException.class,data::verify);
    }
    @Test void repeatedNamesRequireExplicitOrdinalRetentionAndRemainDistinct()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"find");Files.writeString(Path.of(options.get("targets")),">same a\nAA\n>same b\nTT\n");
        Path failed=root.resolve("failed");Files.createDirectory(failed);assertThrows(IOException.class,()->HomerCoreData.stage(options,"find",failed,HomerCoreFixture.LIMITS));
        options.put("duplicate-ids","retain-ordinal");Path work=root.resolve("retained");Files.createDirectory(work);var data=HomerCoreData.stage(options,"find",work,HomerCoreFixture.LIMITS);
        assertEquals(1,data.duplicates());assertEquals(2,data.sequences().stream().map(HomerCoreData.Sequence::alias).distinct().count());assertEquals("same",data.sequences().get(1).id());
    }
    @Test void overlapErrorsOrExplicitlyRetainsFullRolePopulations()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"known");Files.writeString(Path.of(options.get("background")),">α repeated-source-id\nAACCTT\n>background2\nTT\n");
        Path failed=root.resolve("failed");Files.createDirectory(failed);assertThrows(IOException.class,()->HomerCoreData.stage(options,"known",failed,HomerCoreFixture.LIMITS));
        options.put("overlap-policy","retain");Path work=root.resolve("retained");Files.createDirectory(work);var data=HomerCoreData.stage(options,"known",work,HomerCoreFixture.LIMITS);
        assertEquals(2,data.targetCount());assertEquals(2,data.backgroundCount());assertEquals(1,data.overlappingIds());assertEquals(1,data.overlappingDna());assertArrayEquals(new long[]{1,2},data.presence()[0]);
    }
    @Test void denseOriginalFindFitsItsDoubledSiteCapacityAndKeepsAllRecords()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"find");Files.writeString(Path.of(options.get("motifs")),">N\tdense\t0\n.25\t.25\t.25\t.25\n");
        Path work=root.resolve("work");Files.createDirectory(work);var data=HomerCoreData.stage(options,"find",work,HomerCoreFixture.LIMITS);
        assertEquals(16,data.hits());assertEquals(2,data.targetCount());assertFalse(Files.exists(work.resolve("original.stdout")));data.verify();
    }
    @Test void denseKnownPresenceModeIsSafeBecauseOriginalStopsAfterFirstSite()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"known");Files.writeString(Path.of(options.get("motifs")),">N\tdense\t0\n.25\t.25\t.25\t.25\n");
        Path work=root.resolve("work");Files.createDirectory(work);var data=HomerCoreData.stage(options,"known",work,HomerCoreFixture.LIMITS);assertArrayEquals(new long[]{2,2},data.presence()[0]);
    }
    @Test void unknownResiduesNeedExplicitMaskAndUIsNeverChangedToT()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"find");Files.writeString(Path.of(options.get("targets")),">input\naUrN\n");
        Path failed=root.resolve("failed");Files.createDirectory(failed);assertThrows(IOException.class,()->HomerCoreData.stage(options,"find",failed,HomerCoreFixture.LIMITS));
        options.put("alphabet","mask-n");Path work=root.resolve("masked");Files.createDirectory(work);var data=HomerCoreData.stage(options,"find",work,HomerCoreFixture.LIMITS);
        assertEquals("ANNN",data.sequences().get(0).dna());assertEquals(1,data.lower());assertEquals(2,data.masked());assertEquals(0,data.hits());
    }
    @Test void originalIntegerCoordinateOverflowIsRejectedBeforeExecution()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"find");options.put("offset",Integer.toString(Integer.MAX_VALUE));Path work=root.resolve("work");Files.createDirectory(work);
        assertTrue(assertThrows(IOException.class,()->HomerCoreData.stage(options,"find",work,HomerCoreFixture.LIMITS)).getMessage().contains("signed integer"));
    }
    @Test void globalSequenceAndOperationBudgetsCannotYieldTruncatedCompleteData()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"find");var small=new HomerCoreData.Limits(1000000,1000000,100000,100,10000,100,1000,1,1000,16777216,10000);
        Path work=root.resolve("work");Files.createDirectory(work);assertThrows(IOException.class,()->HomerCoreData.stage(options,"find",work,small));
    }
    @Test void emptySequencesCannotSilentlyDisappearFromOriginalPopulations()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"known");Files.writeString(Path.of(options.get("targets")),">empty\n>valid\nAA\n");Path work=root.resolve("work");Files.createDirectory(work);
        assertTrue(assertThrows(IOException.class,()->HomerCoreData.stage(options,"known",work,HomerCoreFixture.LIMITS)).getMessage().contains("Empty FASTA"));
    }
    @Test void nativeAuditStreamsEnforceTheirByteBudgetBeforePublishingMoreBytes()throws Exception{
        var budget=new HomerCoreData.Budget(8);try(var output=budget.writer(root.resolve("bounded"))){output.write("123456789");assertThrows(IOException.class,output::flush);}
        catch(IOException expected){assertTrue(expected.getMessage().contains("byte budget"));}assertEquals(0,Files.size(root.resolve("bounded")));
    }
}
