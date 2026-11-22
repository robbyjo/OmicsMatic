package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerAtacInputsTest {
    @TempDir Path root;
    private static final String HEADER="@HD\tVN:1.6\n@SQ\tSN:chr1\tLN:10000\n";
    private static String row(String name,int flag,int position,int quality,int tlen){return name+"\t"+flag+"\tchr1\t"+position+"\t"+quality+"\t10M\t*\t0\t"+tlen+"\tACGTACGTAC\tIIIIIIIIII\n";}
    private static String pair(){return HEADER+row("rα",99,100,60,100)+row("rα",147,190,60,-100);}
    private Map<String,String> setup(String sam)throws Exception{
        Files.writeString(root.resolve("reads.sam"),sam);Files.writeString(root.resolve("reference.tsv"),"chr1\t10000\n");Files.writeString(root.resolve("list.tsv"),"replicate_id\tsam_path\nRep α\treads.sam\n");var o=new LinkedHashMap<String,String>();o.put("sam-list",root.resolve("list.tsv").toString());o.put("reference-lengths",root.resolve("reference.tsv").toString());o.put("maximum-records","1000");o.put("maximum-ledger-keys","10");o.put("duplicate-alignments","retain-upstream");o.put("supplementary-alignments","retain-upstream");o.put("unpaired-alignments","retain-upstream");o.put("improper-pairs","retain-upstream");o.put("zero-template-length","use-sequence-length");o.put("identical-replicates","error");return o;
    }
    private HomerAtacInputs.Data stage(Map<String,String> o)throws Exception{
        Path home=Files.createDirectory(root.resolve("runtime"));Path marker=home.resolve(HomerAtacRuntime.MARKER);Files.writeString(marker,"{}\n");var context=new HomerAtacRuntime.Context(home,"unit-test-intake-only",FollowupSupport.hash(marker),Map.of(),Map.of(),Map.of());Path work=Files.createDirectory(root.resolve("work"));return HomerAtacInputs.stage(o,work,HomerAtacInputs.Limits.read(o),context);
    }
    @Test void completePairedIntakeKeepsUnicodeIdentityAndBothRolePopulations()throws Exception{var d=stage(setup(pair()));assertEquals(2,d.totalRecords());assertEquals(1,d.totalWeight());assertEquals(2L,d.expected().roleHalfUnits().get("MERGED"));assertEquals(2L,d.expected().roleHalfUnits().get("R0001"));assertEquals("Rep α",d.replicates().get(0).id());assertTrue(Files.readString(d.work().resolve("alignments.tsv")).contains("rα\t99\tACCEPTED"));d.verify();}
    @Test void excludedRowsRemainInAlignmentLedgerAndPrimaryCounter()throws Exception{var d=stage(setup(pair()+row("low",0,500,20,0)+row("secondary",256,600,60,0)));assertEquals(4,d.totalRecords());assertEquals(1,d.totalWeight());assertEquals(2,d.replicates().get(0).summary().originalAllPrimaryWeight());String text=Files.readString(d.work().resolve("alignments.tsv"));assertTrue(text.contains("MAPQ_FILTER"));assertTrue(text.contains("SECONDARY_FILTER"));}
    @Test void originalHalfWeightFalseEmptyGuardIsReportedBeforeSourceExecution()throws Exception{var failure=assertThrows(IOException.class,()->stage(setup(HEADER+row("one-mate",147,190,60,-100))));assertTrue(failure.getMessage().contains("at least1.0"));assertTrue(failure.getMessage().contains("exit0"));}
    @Test void acceptedDuplicateFlagsNeedExplicitUpstreamRetention()throws Exception{var o=setup(HEADER+row("dup",1024,100,60,0));o.put("duplicate-alignments","error");var failure=assertThrows(IOException.class,()->stage(o));assertTrue(failure.getMessage().contains("deduplication"));}
    @Test void sourceSequenceFallbackForZeroTlenCannotBeAssumed()throws Exception{var o=setup(HEADER+row("orphan",73,100,60,0)+row("orphan2",73,200,60,0));o.put("zero-template-length","error");assertTrue(assertThrows(IOException.class,()->stage(o)).getMessage().contains("Zero SAM TLEN"));}
    @Test void samHeaderLengthMustMatchProvidedReferenceBeforeTagsAreAdmitted()throws Exception{var o=setup(pair().replace("LN:10000","LN:9999"));assertTrue(assertThrows(IOException.class,()->stage(o)).getMessage().contains("@SQ dictionary"));}
    @Test void compressedBinaryInputsProduceAnUnavailableCapabilityInsteadOfPartialParsing()throws Exception{var o=setup(pair());Files.write(root.resolve("reads.sam"),new byte[]{31,(byte)139,8,0});assertThrows(HomerRuntime.Unavailable.class,()->stage(o));}
    @Test void byteIdenticalReplicatesRequireAnExplicitRetentionPolicy()throws Exception{var o=setup(pair());Files.writeString(root.resolve("list.tsv"),"replicate_id\tsam_path\nA\treads.sam\nB\treads.sam\n");o.put("identical-replicates","retain-explicit");var d=stage(o);assertEquals(1,d.identicalSourceFiles());assertEquals(2,d.totalWeight());assertEquals(4L,d.expected().roleHalfUnits().get("MERGED"));}
    @Test void changedSourceCannotPassFinalImmutableInputVerification()throws Exception{var d=stage(setup(pair()));Files.writeString(root.resolve("reads.sam"),pair()+row("extra",0,500,60,0));assertThrows(IOException.class,d::verify);}
    @Test void unsafeContigsAndIncompatibleDiskChunkPlanFailBeforeOriginalLaunch()throws Exception{assertThrows(IOException.class,()->HomerAtacInputs.Limits.read(Map.of("maximum-records","100000000","maximum-ledger-keys","10")));var o=setup(pair());Files.writeString(root.resolve("reference.tsv"),"../chr1\t10000\n");assertThrows(IOException.class,()->stage(o));}
}
