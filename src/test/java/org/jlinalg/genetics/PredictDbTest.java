/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PredictDbTest {
    @TempDir Path temporary;
    final PredictDb.Limits limits=new PredictDb.Limits(1048576,100,100,10);
    Path model(boolean phi)throws Exception {
        Class.forName("org.sqlite.JDBC");Path p=temporary.resolve(UUID.randomUUID()+" model.db");
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+p);var s=db.createStatement()) {
            s.execute("CREATE TABLE weights(rsid TEXT,gene TEXT,weight REAL,ref_allele TEXT,eff_allele TEXT)");
            s.execute("CREATE TABLE extra(gene TEXT,genename TEXT,`n.snps.in.model` INTEGER,`pred.perf.R2` REAL,`pred.perf.pval` REAL,`pred.perf.qval` REAL"+(phi?",phi REAL":"")+")");
            s.execute("INSERT INTO weights VALUES('rs1','G1.1',.5,'A','G'),('rs2','G1.1',-.3,'C','T')");
            s.execute("INSERT INTO extra VALUES('G1.1','Name, with comma',99,.2,NULL,.03"+(phi?",-.003":"")+")");
        }return p.toRealPath();
    }
    void sql(Path p,String sql)throws Exception{try(var db=DriverManager.getConnection("jdbc:sqlite:"+p);var s=db.createStatement()){s.execute(sql);}}
    @Test void readOnlyPreservesBytesIdentifiersAndDistinctAnnotationCount()throws Exception {
        Path p=model(true);byte[] before=Files.readAllBytes(p);var m=PredictDb.read(p,limits,()->{});
        assertArrayEquals(before,Files.readAllBytes(p));assertEquals(2,m.weights().size());assertEquals(99,m.extra().get("G1.1").annotatedSnps());assertEquals("Name, with comma",m.extra().get("G1.1").name());assertEquals(-.003,m.extra().get("G1.1").phi());assertNull(m.extra().get("G1.1").performanceP());assertTrue(m.hasPhi());assertEquals(List.of("rs1","rs2"),m.byGene().get("G1.1").stream().map(PredictDb.Weight::variant).toList());assertFalse(Files.exists(Path.of(p+"-journal")));
        assertThrows(UnsupportedOperationException.class,()->m.byGene().clear());assertThrows(UnsupportedOperationException.class,()->m.weights().clear());
    }
    @Test void historicalModelsDoNotAcquireInventedPhi()throws Exception{var m=PredictDb.read(model(false),limits,()->{});assertFalse(m.hasPhi());assertNull(m.extra().get("G1.1").phi());}
    @Test void integerAnnotationCountsNeverRoundThroughDouble()throws Exception{Path p=model(false);sql(p,"UPDATE extra SET `n.snps.in.model`=9007199254740993");assertEquals(9007199254740993L,PredictDb.read(p,limits,()->{}).extra().get("G1.1").annotatedSnps());}
    @Test void duplicateWeightsAndAnnotationsAreNotSilentlyCollapsed()throws Exception {
        Path p=model(false);sql(p,"INSERT INTO weights SELECT * FROM weights WHERE rsid='rs1'");assertTrue(assertThrows(IOException.class,()->PredictDb.read(p,limits,()->{})).getMessage().contains("Duplicate"));
        Path q=model(false);sql(q,"INSERT INTO extra SELECT * FROM extra");assertThrows(IOException.class,()->PredictDb.read(q,limits,()->{}));
    }
    @Test void conflictingOrUnresolvedAllelesAreRejectedWithoutRepair()throws Exception {
        Path p=model(false);sql(p,"INSERT INTO weights VALUES('rs1','G2',.1,'G','A')");assertTrue(assertThrows(IOException.class,()->PredictDb.read(p,limits,()->{})).getMessage().contains("Conflicting"));
        Path q=model(false);sql(q,"UPDATE weights SET eff_allele='g' WHERE rsid='rs1'");assertThrows(IOException.class,()->PredictDb.read(q,limits,()->{}));
    }
    @Test void journalStateNeverBecomesAnIncompleteCapturedDatabase()throws Exception {
        Path p=model(false);for(String suffix:List.of("-wal","-shm","-journal")){Path sidecar=Path.of(p+suffix);Files.writeString(sidecar,"retained original sidecar");assertThrows(IOException.class,()->PredictDb.read(p,limits,()->{}));assertEquals("retained original sidecar",Files.readString(sidecar));Files.delete(sidecar);}
    }
    @Test void viewsAndCompleteCapacityOverrunsAreRefused()throws Exception {
        Path p=model(false);assertThrows(IOException.class,()->PredictDb.read(p,new PredictDb.Limits(1048576,1,100,10),()->{}));
        sql(p,"ALTER TABLE weights RENAME TO source_weights");sql(p,"CREATE VIEW weights AS SELECT * FROM source_weights");assertTrue(assertThrows(IOException.class,()->PredictDb.read(p,limits,()->{})).getMessage().contains("ordinary"));
    }
    @Test void nonexistentFilesAreNeverCreated()throws Exception {Path p=temporary.resolve("absent.db");assertThrows(IOException.class,()->PredictDb.read(p,limits,()->{}));assertFalse(Files.exists(p));}
    @Test void alternateKeyUsesTheOriginalFirstRowRuleForEveryIdentifier()throws Exception {
        Path p=model(false);sql(p,"ALTER TABLE weights ADD COLUMN varID TEXT");sql(p,"UPDATE weights SET varID=CASE rsid WHEN 'rs1' THEN '1_10_A_G_b38' ELSE 'chr2_20_C_T_b38' END");byte[] original=Files.readAllBytes(p);
        var selected=PredictDb.readSelected(p,limits,new PredictDb.Key("varid"),()->{});assertEquals("varid",selected.requestedColumn());assertEquals("varID",selected.effectiveColumn());assertTrue(selected.sourceB38PrefixApplied());assertEquals("1_10_A_G_b38",selected.firstSourceVariant());assertEquals(List.of("chr1_10_A_G_b38","chrchr2_20_C_T_b38"),selected.model().weights().stream().map(PredictDb.Weight::variant).toList());assertEquals("chr2_20_C_T_b38",selected.sourceVariant("chrchr2_20_C_T_b38"));assertArrayEquals(original,Files.readAllBytes(p));assertEquals("rs1",PredictDb.read(p,limits,()->{}).weights().get(0).variant());
    }
    @Test void firstPrefixedIdDisablesTransformationAndExplicitRsidDiffersFromDefault()throws Exception {
        Path p=model(false);sql(p,"ALTER TABLE weights ADD COLUMN varID TEXT");sql(p,"UPDATE weights SET varID=CASE rsid WHEN 'rs1' THEN 'chr1_10_A_G_b38' ELSE '2_20_C_T_b38' END");var selected=PredictDb.readSelected(p,limits,new PredictDb.Key("varID"),()->{});assertFalse(selected.sourceB38PrefixApplied());assertEquals("2_20_C_T_b38",selected.model().weights().get(1).variant());
        sql(p,"UPDATE weights SET rsid='rs1_b38' WHERE rsid='rs1'");assertEquals("rs1_b38",PredictDb.read(p,limits,()->{}).weights().get(0).variant());var explicit=PredictDb.readSelected(p,limits,new PredictDb.Key("rsid"),()->{});assertTrue(explicit.sourceB38PrefixApplied());assertEquals("chrrs2",explicit.model().weights().get(1).variant());
    }
    @Test void missingOrNonliteralKeyNeverGetsAnAliasOrSqlExpression()throws Exception {
        Path p=model(false);var e=assertThrows(IOException.class,()->PredictDb.readSelected(p,limits,new PredictDb.Key("SNP_ID"),()->{}));assertTrue(e.getMessage().contains("absent: SNP_ID"));assertTrue(e.getMessage().contains("no SNP_ID/varID/rsid alias"));for(String key:List.of("rsid;DROP TABLE weights","rsid || gene","'rsid'","","rsid,weight"))assertThrows(IllegalArgumentException.class,()->new PredictDb.Key(key));assertEquals(2,PredictDb.read(p,limits,()->{}).weights().size());
    }
}
