package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerTagLedgerTest {
    @TempDir Path root;
    private static HomerTagLedger.Key key(String role,int position,int length){return new HomerTagLedger.Key(role,"chr1",position,'+',length);}
    private HomerTagLedger ledger(String name,int chunk,int maximum,long records,long bytes)throws Exception{return new HomerTagLedger(root.resolve(name),chunk,maximum,records,bytes,()->{});}
    @Test void independentChunkOrderCannotChangeCompleteWeightedPopulation()throws Exception{var a=ledger("a",2,10,100,100000);var b=ledger("b",3,10,100,100000);for(int i:new int[]{4,1,4,2,1,3,4})a.add(key("R1",i,100),1);for(int i:new int[]{4,4,3,1,2,4,1})b.add(key("R1",i,100),1);var x=a.finish(root.resolve("a.tsv"));var y=b.finish(root.resolve("b.tsv"));assertEquals(-1,Files.mismatch(root.resolve("a.tsv"),root.resolve("b.tsv")));assertEquals(4,x.fullKeys());assertEquals(7L,x.roleHalfUnits().get("R1"));assertEquals(x.sha256(),y.sha256());}
    @Test void fullIntegerLengthsStaySeparateDespiteOriginalSignedByteComparatorAlias()throws Exception{var a=ledger("a",1,10,100,100000);a.add(key("MERGED",100,356),1);a.add(key("MERGED",100,100),2);a.add(key("MERGED",100,356),1);var result=a.finish(root.resolve("out.tsv"));assertEquals(2,result.fullKeys());assertTrue(Files.readString(root.resolve("out.tsv")).endsWith("MERGED\tchr1\t100\t+\t100\t1.0\nMERGED\tchr1\t100\t+\t356\t1.0\n"));}
    @Test void replicateAndMergedRolesCannotBeSilentlyCoalesced()throws Exception{var a=ledger("a",2,10,100,100000);a.add(key("R1",100,100),1);a.add(key("R2",100,100),1);a.add(key("MERGED",100,100),2);var result=a.finish(root.resolve("out.tsv"));assertEquals(3,result.fullKeys());assertEquals(2L,result.roleHalfUnits().get("MERGED"));}
    @Test void emptyNativeActualPopulationRemainsDistinguishableFromPositiveExpectation()throws Exception{var a=ledger("empty",2,10,100,100000);var result=a.finish(root.resolve("out.tsv"));assertEquals(0,result.fullKeys());assertEquals("role\tcontig\tposition1\tstrand\tstored_length\tweight\n",Files.readString(root.resolve("out.tsv")));assertThrows(IOException.class,()->a.add(key("R1",100,100),1));}
    @Test void chunkContributionAndByteCapsFailWithoutACompletedLedger()throws Exception{var a=ledger("a",1,1,100,100000);a.add(key("R1",100,100),1);assertThrows(IOException.class,()->a.add(key("R1",200,100),1));var b=ledger("b",10,10,1,100000);b.add(key("R1",100,100),1);assertThrows(IOException.class,()->b.add(key("R1",100,100),1));var c=ledger("c",1,10,100,1);assertThrows(IOException.class,()->c.add(key("R1",100,100),1));}
    @Test void changedDiskChunkIsRejectedBeforePublication()throws Exception{var a=ledger("a",1,10,100,100000);a.add(key("R1",100,100),1);Files.writeString(root.resolve("a/chunk-0000.tsv"),"R1\tchr1\t200\t+\t100\t1\n");assertThrows(IOException.class,()->a.finish(root.resolve("out.tsv")));}
    @Test void exactHalfUnitsArePreservedBeyondFloat32CountPrecision()throws Exception{var a=ledger("a",1,10,100,100000);a.add(key("R1",100,100),16777216);a.add(key("R1",100,100),1);var result=a.finish(root.resolve("out.tsv"));assertEquals(16777217L,result.roleHalfUnits().get("R1"));assertTrue(Files.readString(root.resolve("out.tsv")).endsWith("8388608.5\n"));}
    @Test void callerFailureAndUnsafeSourceKeysCannotProduceCompletedResults()throws Exception{assertThrows(IllegalArgumentException.class,()->new HomerTagLedger.Key("R1","../chr1",100,'+',100));var a=new HomerTagLedger(root.resolve("a"),1,10,100,100000,()->{throw new IOException("deadline");});assertThrows(IOException.class,()->a.add(key("R1",100,100),1));}
    @Test void aFailedAddCannotLaterPublishASeeminglyCompletePrefix()throws Exception{var a=ledger("a",10,10,1,100000);a.add(key("R1",100,100),1);assertThrows(IOException.class,()->a.add(key("R1",200,100),1));assertThrows(IOException.class,()->a.finish(root.resolve("out.tsv")));assertFalse(Files.exists(root.resolve("out.tsv")));}
    @Test void byteAllowanceIncludesBothRetainedChunksAndFinalLedger()throws Exception{var a=ledger("a",1,10,100,90);a.add(key("R1",100,100),1);assertThrows(IOException.class,()->a.finish(root.resolve("out.tsv")));}
}
