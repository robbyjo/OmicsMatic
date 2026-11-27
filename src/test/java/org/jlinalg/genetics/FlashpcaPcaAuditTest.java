/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Independent closed-form Hadamard PCs, with unequal eigenvalues and raw BED input. */
class FlashpcaPcaAuditTest {
    @TempDir Path root;
    private static final FlashpcaPcaAudit.Limits LIMITS=new FlashpcaPcaAudit.Limits(1000,1000,1000000,10000000,10000000,65536);
    private record Fixture(Path prefix,Path output,FlashpcaPcaAudit.Options options) { }
    private Fixture fixture(boolean legacy,boolean transpose)throws IOException {
        Path p=root.resolve("data"),out=Files.createDirectory(root.resolve("output"));
        StringBuilder fam=new StringBuilder(),bim=new StringBuilder();var bytes=new ByteArrayOutputStream();bytes.write(new byte[]{108,27,1});
        for(int i=0;i<16;i++)fam.append("F").append(i/2).append(" S").append(i).append(" 0 0 1 -9\n");
        int[] counts={8,5,3};int[] axis=new int[16];for(int j=0;j<16;j++)axis[j]=j<8?0:j<13?1:2;
        for(int j=0;j<16;j++) {
            bim.append("1 v").append(j).append(" 0 ").append(j+1).append(" A G\n");
            for(int b=0;b<4;b++){int packed=0;for(int t=0;t<4;t++){int i=4*b+t;boolean positive=((i>>axis[j])&1)==1;packed|=(positive?0:3)<<(2*t);}bytes.write(packed);}
        }
        Files.write(Path.of(p+".bed"),bytes.toByteArray());Files.writeString(Path.of(p+".fam"),fam);Files.writeString(Path.of(p+".bim"),bim);
        double[] d=legacy?new double[]{512.0/15,320.0/15,192.0/15}:new double[]{16,10,6};double trace=Arrays.stream(d).sum();
        Files.writeString(out.resolve("eigenvalues.txt"),d[0]+"\n"+d[1]+"\n"+d[2]+"\n");
        Files.writeString(out.resolve("pve.txt"),d[0]/trace+"\n"+d[1]/trace+"\n"+d[2]/trace+"\n");
        StringBuilder u=new StringBuilder(legacy?"":"FID\tIID\tU1\tU2\tU3\n"),pc=new StringBuilder(legacy?"":"FID\tIID\tPC1\tPC2\tPC3\n");
        for(int i=0;i<16;i++) {
            if(!legacy){u.append("F").append(i/2).append("\tS").append(i).append('\t');pc.append("F").append(i/2).append("\tS").append(i).append('\t');}
            for(int a=0;a<3;a++) {
                double value=(((i>>a)&1)==1?1:-1)*.25;
                // These are the original editions' distinct reported score conventions.
                double score=legacy?transpose?value*Math.sqrt(d[a]*15):value*d[a]:value*Math.sqrt(d[a]);
                if(a>0){u.append('\t');pc.append('\t');}u.append(value);pc.append(score);
            }u.append('\n');pc.append('\n');
        }
        Files.writeString(out.resolve("eigenvectors.txt"),u);Files.writeString(out.resolve("pcs.txt"),pc);
        var load=new StringBuilder(legacy?"":"SNP\tRefAllele\tV1\tV2\tV3\n");var moments=new StringBuilder(legacy?"":"SNP\tRefAllele\tMean\tSD\n");
        for(int j=0;j<16;j++) {
            if(!legacy){load.append("v").append(j).append("\tA\t");moments.append("v").append(j).append("\tA\t");}
            for(int a=0;a<3;a++){if(a>0)load.append('\t');load.append(axis[j]==a?1/Math.sqrt(counts[a]):0);}load.append('\n');moments.append("1\t").append(legacy?.5:Math.sqrt(.5)).append('\n');
        }
        Files.writeString(out.resolve("loadings.txt"),load);Files.writeString(out.resolve("meansd.txt"),moments);
        var options=new FlashpcaPcaAudit.Options(legacy?FlashpcaPcaAudit.Edition.V1_2_6:FlashpcaPcaAudit.Edition.V2_0,3,legacy?FlashpcaPcaAudit.Standardization.BINOM:FlashpcaPcaAudit.Standardization.BINOM2,legacy?FlashpcaPcaAudit.Divisor.N1:FlashpcaPcaAudit.Divisor.P,transpose,1e-9);
        return new Fixture(p,out,options);
    }
    private FlashpcaPcaAudit.Report audit(Fixture f)throws IOException{return FlashpcaPcaAudit.audit(f.prefix,f.output,f.options,LIMITS,()->{});}
    @Test void modernIndependentClosedFormFullCovarianceAndLoadings()throws IOException{
        var r=audit(fixture(false,false));assertEquals(32,r.covarianceTrace(),1e-12);assertEquals(1,r.retainedVarianceFraction(),1e-12);assertEquals(256,r.input().calledGenotypes());assertTrue(r.completeGenotypesAudited());assertFalse(r.partialPcaRefit());assertFalse(r.globalLargestEigenvalueOrderCertified());
    }
    @Test void legacyNontransposedPreservesSourceEigenvalueScaledScores()throws IOException{var r=audit(fixture(true,false));assertEquals(1024.0/15,r.covarianceTrace(),1e-12);assertFalse(r.input().legacyTransposed());}
    @Test void legacyForcedTransposePreservesDifferentRawScoreScale()throws IOException{var r=audit(fixture(true,true));assertTrue(r.input().legacyTransposed());assertTrue(r.maximumPcScalingError()<1e-12);}
    @Test void wrongPcConventionFailsEvenWhenEigenvectorsAreValid()throws IOException{
        var f=fixture(true,false);Path p=f.output.resolve("pcs.txt");Files.writeString(p,Files.readString(f.output.resolve("eigenvectors.txt")));assertThrows(IllegalArgumentException.class,()->audit(f));
    }
    @Test void alteredEigenvalueOrLoadingCannotPassNativeMatrixCheck()throws IOException{
        var f=fixture(false,false);Path p=f.output.resolve("eigenvalues.txt");Files.writeString(p,"17\n10\n6\n");assertThrows(IllegalArgumentException.class,()->audit(f));
    }
    @Test void sampleIdentityOrderCannotBeSilentlyRepaired()throws IOException{
        var f=fixture(false,false);Path p=f.output.resolve("eigenvectors.txt");Files.writeString(p,Files.readString(p).replace("F0\tS0\t","F0\tS1\t"));assertThrows(IllegalArgumentException.class,()->audit(f));
    }
    @Test void omittedOrExtraSourceRecordsFailCompletePopulationCheck()throws IOException{
        var f=fixture(false,false);Files.writeString(f.output.resolve("pve.txt"),"0.5\n0.3125\n");assertThrows(IllegalArgumentException.class,()->audit(f));
    }
    @Test void allMissingVariantIsReportedAsInputFailure()throws IOException{
        var f=fixture(false,false);Path p=Path.of(f.prefix+".bed");byte[] b=Files.readAllBytes(p);Arrays.fill(b,3,7,(byte)85);Files.write(p,b);assertThrows(IllegalArgumentException.class,()->FlashpcaPcaAudit.inspect(f.prefix,f.options,LIMITS,()->{}));
    }
    @Test void fullWorkCapacityAndCancellationAdmitNoPartialAudit()throws IOException{
        var f=fixture(false,false);var cap=new FlashpcaPcaAudit.Limits(1000,1000,1000000,10000000,1,65536);assertThrows(IllegalArgumentException.class,()->FlashpcaPcaAudit.inspect(f.prefix,f.options,cap,()->{}));assertThrows(IllegalStateException.class,()->FlashpcaPcaAudit.inspect(f.prefix,f.options,LIMITS,()->{throw new IllegalStateException("cancelled");}));
    }
    @Test void modernDimensionClampIsRefusedBeforeOriginalAnalysis()throws IOException{
        var f=fixture(false,false);var invalid=new FlashpcaPcaAudit.Options(FlashpcaPcaAudit.Edition.V2_0,6,FlashpcaPcaAudit.Standardization.BINOM2,FlashpcaPcaAudit.Divisor.P,false,1e-5);assertThrows(IllegalArgumentException.class,()->FlashpcaPcaAudit.inspect(f.prefix,invalid,LIMITS,()->{}));
    }
}
