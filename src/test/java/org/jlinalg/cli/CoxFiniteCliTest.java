/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class CoxFiniteCliTest {
    @TempDir Path root;
    @Test void aFullyObservedCohortDoesNotRequireTwoBinaryTraitLevels()throws Exception{
        Path phenotype=root.resolve("all-events.tsv"),out=root.resolve("all-events-result.tsv");Files.writeString(phenotype,"id\ttime\tevent\tx\nS1\t1\t1\t0\nS2\t2\t1\t1\nS3\t3\t1\t0\nS4\t4\t1\t1\nS5\t5\t1\t0\nS6\t6\t1\t1\n");var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(new String[]{"--pheno",phenotype.toString(),"--id","id","--formula","Surv(time,event)~x","--model","cox","--out",out.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error));assertEquals(0,code,error.toString());assertTrue(Files.isRegularFile(out));
    }
    @Test void numericAssaysSupportAnUnadjustedNullAndCategoricalReferenceCoding()throws Exception{
        Path phenotype=root.resolve("positive.tsv"),matrix=root.resolve("matrix.tsv");var p=new StringBuilder("id\ttime\tevent\tsex\n");var m=new StringBuilder("feature_id");for(int i=0;i<60;i++){p.append("S"+i+"\t"+(1+i*17%67)+"\t"+(i%4==0?0:1)+"\t"+(i%2==0?"A":"B")+"\n");m.append("\tS"+i);}m.append("\ng");for(int i=0;i<60;i++)m.append("\t"+Math.sin(i*.53));m.append('\n');Files.writeString(phenotype,p);Files.writeString(matrix,m);
        for(boolean adjust:new boolean[]{false,true}){
            Path out=root.resolve(adjust?"adjusted.tsv":"unadjusted.tsv");var error=new ByteArrayOutputStream();var stdout=new ByteArrayOutputStream();
            int code=JLinAlgCli.run(new String[]{"--pheno",phenotype.toString(),"--id","id","--omics",matrix.toString(),"--omics-type","expression","--formula","Surv(time,event)~"+(adjust?"sex+":"")+"<omics>","--model","cox","--threads","1","--out",out.toString()},new PrintStream(stdout),new PrintStream(error));
            assertEquals(0,code,error.toString());var result=DelimitedData.read(out);assertEquals(1,result.rows().size());assertEquals("ok",result.rows().get(0)[result.column("status")]);assertTrue(stdout.toString().contains("one-step"));assertTrue(Files.readString(Path.of(out+".log")).contains("cox_null_finite_maximum=FINITE_MAXIMUM"));
        }
        Path out=root.resolve("coefficients.tsv");var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(new String[]{"--pheno",phenotype.toString(),"--id","id","--formula","Surv(time,event)~sex","--model","cox","--out",out.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error));assertEquals(0,code,error.toString());var result=DelimitedData.read(out);assertEquals(1,result.rows().size());assertFalse(Files.readString(out).contains("(Intercept)"));
    }
    @Test void standaloneCoxDoesNotPublishAnInfiniteCoefficientOrRecodeInvalidEvents()throws Exception{
        Path phenotype=root.resolve("phenotype.tsv"),out=root.resolve("result.tsv");Files.writeString(phenotype,"id\ttime\tevent\tx\nS1\t1\t1\t0\nS2\t2\t1\t0\nS3\t3\t1\t0\nS4\t4\t0\t1\nS5\t5\t0\t1\nS6\t6\t0\t1\n");var error=new ByteArrayOutputStream();
        int code=JLinAlgCli.run(new String[]{"--pheno",phenotype.toString(),"--id","id","--formula","Surv(time,event)~x","--model","cox","--out",out.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error));
        assertNotEquals(0,code);assertFalse(Files.exists(out));assertTrue(error.toString().contains("MONOTONE_LIKELIHOOD"),error.toString());
        Files.writeString(phenotype,Files.readString(phenotype).replace("S1\t1\t1","S1\t1\t2"));out=root.resolve("invalid-event.tsv");error.reset();code=JLinAlgCli.run(new String[]{"--pheno",phenotype.toString(),"--id","id","--formula","Surv(time,event)~x","--model","cox","--out",out.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error));assertNotEquals(0,code);assertFalse(Files.exists(out));assertTrue(error.toString().contains("0 (censored) or 1"),error.toString());
    }
}
