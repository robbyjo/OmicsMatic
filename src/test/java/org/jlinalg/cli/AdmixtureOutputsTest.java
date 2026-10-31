package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdmixtureOutputsTest {
    @TempDir Path temp;
    private record Case(AdmixtureTestData f,AdmixtureInputs.Prepared p,Path work,double likelihood) { }
    private Case data()throws Exception {var f=new AdmixtureTestData(temp);var p=f.prepare(f.recipe(),"work");Path w=p.prefix().getParent();return new Case(f,p,w,f.originalLikeOutput(w));}
    @Test void completeNativeAuditChecksIdentityAndExportsOriginalRoundedTokens()throws Exception {
        var c=data();var a=AdmixtureOutputs.audit(c.p,c.work);assertEquals(11L,a.get("observed_genotype_cells"));assertEquals(22L,a.get("observed_model_alleles"));assertEquals(c.likelihood,(double)a.get("native_loglikelihood"),1e-12);assertTrue(Files.readString(c.work.resolve("Q.tsv")).contains("f\ta\t0.200000\t0.800000"));assertTrue(Files.readString(c.work.resolve("P.tsv")).contains("1\tv1\t1\tG\tA\t0.100000\t0.900000"));assertTrue(Files.exists(c.work.resolve("model.json")));
    }
    @Test void wrongAlleleOrientationIsRejectedByLikelihoodNotJustShape()throws Exception {var c=data();Files.writeString(c.work.resolve("cohort.2.P"),"0.900000 0.100000\n".repeat(3));assertThrows(IOException.class,()->AdmixtureOutputs.audit(c.p,c.work));}
    @Test void truncatedExtraAndNonfiniteMatricesFail()throws Exception {var c=data();Files.writeString(c.work.resolve("cohort.2.P"),"0.100000 0.900000\n");assertThrows(IOException.class,()->AdmixtureOutputs.audit(c.p,c.work));c.f.originalLikeOutput(c.work);Files.writeString(c.work.resolve("cohort.2.P"),"0.100000 0.900000\n".repeat(4));assertThrows(IOException.class,()->AdmixtureOutputs.audit(c.p,c.work));c.f.originalLikeOutput(c.work);Files.writeString(c.work.resolve("cohort.2.Q"),"nan 0.800000\n".repeat(4));assertThrows(IOException.class,()->AdmixtureOutputs.audit(c.p,c.work));}
    @Test void originalVersionSeedDimensionsAndDuplicateLikelihoodAreChecked()throws Exception {var c=data();String original=Files.readString(c.work.resolve("fit.stdout"));for(String value:List.of(original.replace("Version 1.4.0","Version 1.3.0"),original.replace("seed: 11","seed: 12"),original.replace("4x3","3x4"),original+"Loglikelihood: 0\n")){Files.writeString(c.work.resolve("fit.stdout"),value);assertThrows(IOException.class,()->AdmixtureOutputs.audit(c.p,c.work));}}
    @Test void falseConvergedTextDoesNotAdmitAnUnsatisfiedDelta()throws Exception {var c=data();Files.writeString(c.work.resolve("fit.stdout"),Files.readString(c.work.resolve("fit.stdout")).replace("(delta): 0","(delta): 0.1"));assertThrows(IOException.class,()->AdmixtureOutputs.audit(c.p,c.work));}
    @Test void matrixBoundsRejectInvalidProbabilitiesAndRowWidths()throws Exception {Path p=temp.resolve("matrix");Files.writeString(p,"1.000001 0.000000\n");assertThrows(IOException.class,()->AdmixtureOutputs.matrix(p,1,2,0,1,256,true));Files.writeString(p,"0.500000\n");assertThrows(IOException.class,()->AdmixtureOutputs.matrix(p,1,2,0,1,256,true));}
    @Test void oversizedOriginalLogIsRetainedButNotLoadedForAdmission()throws Exception {var c=data();Files.writeString(c.work.resolve("fit.stdout"),"X".repeat(300000));assertThrows(IOException.class,()->AdmixtureOutputs.audit(c.p,c.work));assertEquals(300000,Files.size(c.work.resolve("fit.stdout")));}
    @Test void tinyStoppingCriteriaHavePrintedRelativePrecisionWithoutAbsoluteFloor()throws Exception {
        assertEquals(0,new java.math.BigDecimal("9.99995E-21").compareTo(AdmixtureOutputs.deltaLower("1.00000E-20")));assertTrue(AdmixtureOutputs.deltaLower("1E-15").compareTo(new java.math.BigDecimal("1E-20"))>0);assertEquals(java.math.BigDecimal.ZERO,AdmixtureOutputs.deltaLower("0"));
        var f=new AdmixtureTestData(temp);var recipe=f.recipe();recipe.put("major_stop",Map.of("kind","delta","value",1e-20));var p=f.prepare(recipe,"work");Path work=p.prefix().getParent();f.originalLikeOutput(work);Files.writeString(work.resolve("fit.stdout"),Files.readString(work.resolve("fit.stdout")).replace("(delta): 0","(delta): 1e-15"));assertThrows(IOException.class,()->AdmixtureOutputs.audit(p,work));
    }
}
