/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.FavorCsvJoin.Disposition;
import org.jlinalg.genetics.FavorCsvJoin.Limits;
import org.jlinalg.genetics.FavorCsvJoin.Options;
import org.jlinalg.genetics.FavorCsvJoin.Reference;
import org.jlinalg.genetics.FavorCsvJoin.Rows;
import org.jlinalg.genetics.FavorCsvJoin.Shard;
import org.jlinalg.genetics.FavorCsvJoin.Variant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FavorCsvJoinTest {
    private static final List<String> COLUMNS=List.of("variant_vcf","score","label");
    private static Variant v(int position){return new Variant(22,position,"A","G");}
    private static List<String> row(int position,String score){return List.of("22-"+position+"-A-G",score,"Gene,α");}
    private static Rows rows(List<List<String>> values){return consumer->{for(var value:values)consumer.accept(value);};}
    private static Shard shard(String id,int start,int end,List<List<String>> values){return new Shard(id,22,start,end,rows(values));}
    private static List<Shard> standard(){return List.of(shard("first",1,500,List.of(row(100,"0.1"))),shard("second",501,1000,List.of(row(600,"NA"))));}
    private static FavorCsvJoin.Result run(List<Variant> variants,List<Shard> shards)throws IOException{return FavorCsvJoin.join(variants,new Reference("synthetic-v1","GRCh38",COLUMNS,shards),new Options("GRCh38",22,Limits.defaults()),()->{});}
    private static FavorCsvJoin.Result limited(Limits limits)throws IOException{return FavorCsvJoin.join(List.of(v(100),v(600)),new Reference("synthetic-v1","GRCh38",COLUMNS,standard()),new Options("GRCh38",22,limits),()->{});}

    @Test void exactLeftJoinRetainsEveryRawCellAndOriginalRowIdentity()throws Exception {
        var r=run(List.of(v(100),v(600)),standard());assertTrue(r.exactlyOneRowPerVariant());assertTrue(r.sourceOrderAligned());assertEquals(List.of(1,2),r.joined().stream().map(FavorCsvJoin.Joined::variantRow).toList());assertEquals("0.1",r.joined().get(0).databaseValues().get(1));assertEquals("NA",r.joined().get(1).databaseValues().get(1));assertEquals("Gene,α",r.joined().get(0).databaseValues().get(2));assertEquals(2,r.databaseRows());assertEquals(17,r.cells());
    }
    @Test void missingDatabaseKeysEmitOneSourceRowWithExplicitNoMatchProvenance()throws Exception {
        var r=run(List.of(v(200),v(600)),standard());assertEquals(Disposition.MISSING_DATABASE_MATCH,r.variants().get(0).disposition());assertEquals(0,r.joined().get(0).databaseRow());assertEquals(List.of("","",""),r.joined().get(0).databaseValues());assertTrue(r.sourceOrderAligned());assertTrue(r.warnings().stream().anyMatch(s->s.contains("MISSING DATABASE MATCHES: 1")));
    }
    @Test void literalMissingAndEmptyDatabaseValuesRemainMatchedRawValues()throws Exception {
        var r=run(List.of(v(100),v(600)),List.of(shard("a",1,500,List.of(List.of(v(100).key(),"NA",""))),standard().get(1)));assertEquals(Disposition.MATCHED,r.variants().get(0).disposition());assertEquals(1,r.joined().get(0).databaseRow());assertEquals(List.of(v(100).key(),"NA",""),r.joined().get(0).databaseValues());
    }
    @Test void exactAlleleKeysAreNotSwappedNormalizedOrTrimmed()throws Exception {
        var r=run(List.of(v(100),v(600)),List.of(shard("a",1,500,List.of(List.of("22-100-G-A","1","swapped"),List.of(" 22-100-A-G","2","space"))),standard().get(1)));assertEquals(Disposition.MISSING_DATABASE_MATCH,r.variants().get(0).disposition());assertEquals(2,r.shards().get(0).unusedDatabaseRows());
    }
    @Test void insertionAndDeletionKeysPreserveOriginalAlleleStrings()throws Exception {
        var insert=new Variant(22,100,"A","AT");var delete=new Variant(22,600,"AT","A");var r=run(List.of(insert,delete),List.of(shard("a",1,500,List.of(List.of(insert.key(),"1","insertion"))),shard("b",501,1000,List.of(List.of(delete.key(),"2","deletion")))));assertTrue(r.sourceOrderAligned());assertEquals(List.of("22-100-A-AT","22-600-AT-A"),r.joined().stream().map(FavorCsvJoin.Joined::key).toList());
    }
    @Test void duplicateDatabaseKeysRetainAllMatchesAndRefuseAnAlignedAttachment()throws Exception {
        var r=run(List.of(v(100),v(600)),List.of(shard("a",1,500,List.of(row(100,"first"),row(100,"second"))),standard().get(1)));assertFalse(r.exactlyOneRowPerVariant());assertFalse(r.sourceOrderAligned());assertEquals(Disposition.MULTIPLE_SOURCE_ROWS,r.variants().get(0).disposition());assertEquals(List.of(1,2),r.variants().get(0).sourceOutputRows());assertEquals(List.of(1L,2L),r.joined().subList(0,2).stream().map(FavorCsvJoin.Joined::databaseRow).toList());assertEquals("second",r.joined().get(1).databaseValues().get(1));
    }
    @Test void repeatedQueryKeysKeepSeparateOriginalRowsWithoutDeduplication()throws Exception {
        var r=run(List.of(v(100),v(100),v(600)),standard());assertTrue(r.sourceOrderAligned());assertEquals(3,r.joined().size());assertEquals(1,r.shards().get(0).matchedDatabaseRows());assertEquals(2,r.shards().get(0).selectedVariants());assertTrue(r.warnings().stream().anyMatch(s->s.contains("REPEATED QUERY KEYS: 1")));
    }
    @Test void duplicateQueriesAndDuplicateDatabaseKeysHaveCartesianMultiplicity()throws Exception {
        var r=run(List.of(v(100),v(100),v(600)),List.of(shard("a",1,500,List.of(row(100,"x"),row(100,"y"))),standard().get(1)));assertEquals(5,r.joined().size());assertEquals(List.of(1,1,2,2,3),r.joined().stream().map(FavorCsvJoin.Joined::variantRow).toList());assertEquals(List.of(1L,2L,1L,2L,1L),r.joined().stream().map(FavorCsvJoin.Joined::databaseRow).toList());
    }
    @Test void partitionGapsRetainDroppedQueryDispositions()throws Exception {
        var r=run(List.of(v(100),v(500),v(600)),List.of(shard("a",1,499,List.of(row(100,"1"))),standard().get(1)));assertEquals(3,r.variants().size());assertEquals(Disposition.DROPPED_NO_SHARD,r.variants().get(1).disposition());assertTrue(r.variants().get(1).sourceOutputRows().isEmpty());assertEquals(List.of(1,3),r.joined().stream().map(FavorCsvJoin.Joined::variantRow).toList());assertFalse(r.sourceOrderAligned());
    }
    @Test void overlappingPartitionsRetainRepeatedMembershipAndMissingMatches()throws Exception {
        var r=run(List.of(v(100),v(600)),List.of(shard("a",1,700,List.of(row(100,"1"))),standard().get(1)));assertEquals(2,r.variants().get(1).selectedShards());assertEquals(2,r.variants().get(1).outputRows());assertEquals(1,r.variants().get(1).databaseMatches());assertFalse(r.exactlyOneRowPerVariant());
    }
    @Test void sourceShardOrderIsNeverRepairedToMatchOriginalGenotypes()throws Exception {
        var s=standard();var r=run(List.of(v(100),v(600)),List.of(s.get(1),s.get(0)));assertTrue(r.exactlyOneRowPerVariant());assertFalse(r.sourceOrderAligned());assertEquals(List.of(2,1),r.joined().stream().map(FavorCsvJoin.Joined::variantRow).toList());assertEquals(List.of(2),r.variants().get(0).sourceOutputRows());assertTrue(r.warnings().stream().anyMatch(w->w.contains("UNSAFE POSITIONAL GDS ATTACHMENT")));
    }
    @Test void interleavedOriginalQueriesExposeConcatenationReordering()throws Exception {
        var r=run(List.of(v(600),v(100)),standard());assertFalse(r.sourceOrderAligned());assertEquals(List.of(2,1),r.joined().stream().map(FavorCsvJoin.Joined::variantRow).toList());
    }
    @Test void shardBoundsAreInclusiveAtBothEnds()throws Exception {
        var r=run(List.of(v(1),v(500),v(501),v(1000)),standard());assertTrue(r.sourceOrderAligned());assertEquals(List.of(1,1,2,2),r.joined().stream().map(FavorCsvJoin.Joined::shardRow).toList());
    }
    @Test void unrelatedChromosomeShardsAreNotOpenedAndCountsRemainUnavailable()throws Exception {
        var input=new ArrayList<>(standard());input.add(1,new Shard("chr1",1,1,1000,consumer->{throw new AssertionError("unselected source opened");}));var r=run(List.of(v(100),v(600)),input);assertFalse(r.shards().get(1).selected());assertNull(r.shards().get(1).databaseRows());assertNull(r.shards().get(1).matchedDatabaseRows());assertEquals(List.of(1,3),r.joined().stream().map(FavorCsvJoin.Joined::shardRow).toList());assertTrue(r.sourceOrderAligned());
    }
    @Test void noQueryMembershipStillScansAndAuditsTheSelectedDatabase()throws Exception {
        var r=run(List.of(v(100)),List.of(standard().get(0),shard("unused",900,1000,List.of(row(950,"5")))));assertEquals(1,r.shards().get(1).databaseRows());assertEquals(0,r.shards().get(1).selectedVariants());assertEquals(1,r.shards().get(1).unusedDatabaseRows());
    }
    @Test void streamedUnmatchedDatabaseRowsAreCountedWithoutRetainingThem()throws Exception {
        Rows stream=consumer->{for(int i=0;i<10000;i++)consumer.accept(List.of("unused-"+i,"1","unused"));};var r=run(List.of(v(100),v(600)),List.of(new Shard("a",22,1,500,stream),new Shard("b",22,501,1000,stream)));assertEquals(20000,r.databaseRows());assertEquals(2,r.joined().size());assertTrue(r.sourceOrderAligned());assertEquals(10000,r.shards().get(0).unusedDatabaseRows());
    }
    @Test void changedCallbackRowsDoNotMutateRetainedMatchSnapshots()throws Exception {
        var fields=new ArrayList<>(row(100,"original"));var s=new Shard("a",22,1,500,consumer->{consumer.accept(fields);fields.set(1,"changed");});var r=run(List.of(v(100),v(600)),List.of(s,standard().get(1)));assertEquals("original",r.joined().get(0).databaseValues().get(1));assertThrows(UnsupportedOperationException.class,()->r.joined().get(0).databaseValues().clear());assertThrows(UnsupportedOperationException.class,()->r.variants().get(0).sourceOutputRows().clear());assertThrows(UnsupportedOperationException.class,()->r.joined().clear());
    }
    @Test void arbitraryKeyColumnPositionAndRawQuotedCellContentAreRetained()throws Exception {
        var columns=List.of("score","variant_vcf","label");var ref=new Reference("fixture","GRCh38",columns,List.of(shard("a",1,500,List.of(List.of("1",v(100).key(),"A,\"B\"\nC"))),shard("b",501,1000,List.of())));var r=FavorCsvJoin.join(List.of(v(100)),ref,new Options("GRCh38",22,Limits.defaults()),()->{});assertEquals("A,\"B\"\nC",r.joined().get(0).databaseValues().get(2));assertTrue(r.sourceOrderAligned());
    }
    @Test void unqueriedMalformedRowsAreNotSkipped()throws Exception {
        var bad=shard("a",1,500,List.of(List.of("unused","only-two")));assertTrue(assertThrows(IllegalArgumentException.class,()->run(List.of(v(100)),List.of(bad,standard().get(1)))).getMessage().contains("width"));
    }
    @Test void nullAndNulCellsAreRejected()throws Exception {
        for(List<String> fields:List.of(java.util.Arrays.asList("unused",null,"x"),List.of("unused","\u0000","x"))){var bad=shard("a",1,500,List.of(fields));assertTrue(assertThrows(IllegalArgumentException.class,()->run(List.of(v(100)),List.of(bad,standard().get(1)))).getMessage().contains("null/NUL"));}
    }
    @Test void databaseTransportErrorsPropagateWithoutReturningAPartialResult()throws Exception {
        var problem=new IOException("incomplete shard");var shard=new Shard("a",22,1,500,consumer->{consumer.accept(row(100,"1"));throw problem;});assertSame(problem,assertThrows(IOException.class,()->run(List.of(v(100)),List.of(shard,standard().get(1)))));
    }
    @Test void exactInputDatabaseCellAndOutputCapacitiesAreEnforced()throws Exception {
        var full=limited(new Limits(2,2,17,2,1000));assertEquals(2,full.joined().size());for(Limits limits:List.of(new Limits(1,2,17,2,1000),new Limits(2,1,17,2,1000),new Limits(2,2,16,2,1000),new Limits(2,2,17,1,1000)))assertThrows(IllegalArgumentException.class,()->limited(limits));
    }
    @Test void zeroDatabaseAndOutputLimitsPermitACompleteDroppedPopulation()throws Exception {
        var ref=new Reference("fixture","GRCh38",COLUMNS,List.of(shard("a",1,10,List.of()),shard("b",11,20,List.of())));var r=FavorCsvJoin.join(List.of(v(100)),ref,new Options("GRCh38",22,new Limits(1,0,7,0,100)),()->{});assertTrue(r.joined().isEmpty());assertEquals(Disposition.DROPPED_NO_SHARD,r.variants().get(0).disposition());assertFalse(r.sourceOrderAligned());
    }
    @Test void duplicateMatchesCannotExceedTheCompleteOutputBudget()throws Exception {
        var ref=new Reference("fixture","GRCh38",COLUMNS,List.of(shard("a",1,500,List.of(row(100,"1"),row(100,"2"))),standard().get(1)));assertTrue(assertThrows(IllegalArgumentException.class,()->FavorCsvJoin.join(List.of(v(100),v(600)),ref,new Options("GRCh38",22,new Limits(2,3,20,2,1000)),()->{})).getMessage().contains("joined-row"));
    }
    @Test void workBudgetAndCancellationCoverScanAndOutputConstruction()throws Exception {
        var full=limited(Limits.defaults());assertEquals(full,limited(new Limits(2,2,17,2,full.operations())));assertThrows(IllegalArgumentException.class,()->limited(new Limits(2,2,17,2,full.operations()-1)));var calls=new AtomicInteger();var r=FavorCsvJoin.join(List.of(v(100),v(600)),new Reference("fixture","GRCh38",COLUMNS,standard()),new Options("GRCh38",22,Limits.defaults()),calls::incrementAndGet);assertEquals(r.operations(),calls.get());calls.set(0);assertThrows(IllegalStateException.class,()->FavorCsvJoin.join(List.of(v(100),v(600)),new Reference("fixture","GRCh38",COLUMNS,standard()),new Options("GRCh38",22,Limits.defaults()),()->{if(calls.incrementAndGet()==r.operations())throw new IllegalStateException("cancelled");}));
    }
    @Test void buildAndSelectedChromosomeMismatchRejectBeforeReadingTheDatabase()throws Exception {
        var ref=new Reference("fixture","GRCh38",COLUMNS,standard());assertThrows(IllegalArgumentException.class,()->FavorCsvJoin.join(List.of(v(100)),ref,new Options("hg38",22,Limits.defaults()),()->{}));assertThrows(IllegalArgumentException.class,()->run(List.of(new Variant(1,100,"A","G")),standard()));
    }
    @Test void duplicateShardIdsAndUnsupportedSinglePartitionAreRejected()throws Exception {
        assertThrows(IllegalArgumentException.class,()->run(List.of(v(100)),List.of(standard().get(0))));assertThrows(IllegalArgumentException.class,()->run(List.of(v(100)),List.of(standard().get(0),standard().get(0))));
    }
    @Test void malformedSchemasIdentitiesAllelesAndLimitsReject()throws Exception {
        for(List<String> columns:List.of(List.of("score"),List.of("variant_vcf","variant_vcf"),List.of("variant_vcf","VarInfo")))assertThrows(IllegalArgumentException.class,()->new Reference("fixture","GRCh38",columns,standard()));assertThrows(IllegalArgumentException.class,()->new Variant(23,1,"A","G"));assertThrows(IllegalArgumentException.class,()->new Variant(22,0,"A","G"));assertThrows(IllegalArgumentException.class,()->new Variant(22,1,"a","G"));assertThrows(IllegalArgumentException.class,()->new Variant(22,1,"A","A"));assertThrows(IllegalArgumentException.class,()->new Variant(22,1,"A","G,T"));assertThrows(IllegalArgumentException.class,()->new Limits(0,0,1,0,1));assertThrows(IllegalArgumentException.class,()->new Shard("bad id",22,1,100,rows(List.of())));
    }
}
