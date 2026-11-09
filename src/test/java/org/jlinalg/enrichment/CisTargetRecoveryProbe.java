/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;

/** Standalone full-value reference probe, not the user-data file workflow. */
public final class CisTargetRecoveryProbe {
    private CisTargetRecoveryProbe() { }
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("--contracts")){contracts();return;}
        if(args.length!=2)throw new IllegalArgumentException("INPUT NEW_DIRECTORY required");
        Path out=Path.of(args[1]);if(Files.exists(out))throw new IllegalArgumentException("Choose NEW output");Files.createDirectory(out);
        try(Scanner input=new Scanner(Path.of(args[0]),StandardCharsets.UTF_8).useLocale(Locale.ROOT)) {
            while(input.hasNext()) {
                String id=input.next();var profile=CisTargetRecovery.Profile.valueOf(input.next());int total=input.nextInt(),m=input.nextInt(),n=input.nextInt();double parameter=input.nextDouble();int bound=input.nextInt();
                double[] w=new double[n];for(int i=0;i<n;i++)w[i]=input.nextDouble();double[][] r=new double[m][n];
                for(int i=0;i<m;i++)for(int j=0;j<n;j++){String s=input.next();r[i][j]=s.equals("Inf")?Double.POSITIVE_INFINITY:Double.parseDouble(s);}
                var result=profile==CisTargetRecovery.Profile.CTXCORE_0_2_0?CisTargetRecovery.ctxcore(r,w,total,parameter,bound):CisTargetRecovery.rcisTarget(r,total,parameter,bound);
                Path home=out.resolve(id);Files.createDirectory(home);var metrics=new StringBuilder("motif\tauc\tnes\tleading_rank\tleading_recovery\tleading_excess\tstatus\tdirect_auc\n");
                var targets=new StringBuilder("motif\tfeature\trank\tweight\n");
                for(int i=0;i<m;i++) {
                    var v=result.motifs().get(i);double direct=profile==CisTargetRecovery.Profile.CTXCORE_0_2_0?CisTargetRecovery.ctxcoreDirectAuc(r[i],w,total,parameter):Double.NaN;
                    metrics.append(i).append('\t').append(v.auc()).append('\t').append(v.nes()).append('\t').append(v.leadingRank()).append('\t').append(v.leadingRecovery()).append('\t').append(v.leadingExcess()).append('\t').append(v.status()).append('\t').append(direct).append('\n');
                    for(int j:v.targetIndicesInInputOrder())targets.append(i).append('\t').append(j).append('\t').append(r[i][j]).append('\t').append(w[j]).append('\n');
                }
                Files.writeString(home.resolve("metrics.tsv"),metrics);Files.writeString(home.resolve("targets.tsv"),targets);
                var curves=new StringBuilder();for(double[] row:result.recoveryCurves()){for(int j=0;j<row.length;j++){if(j>0)curves.append('\t');curves.append(row[j]);}curves.append('\n');}Files.writeString(home.resolve("curves.tsv"),curves);
                var background=new StringBuilder("rank\tmean\tsd\tmean_plus_two_sd\n");int[] ranks=result.recoveryRanks();double[] mean=result.meanCurve(),sd=result.sdCurve(),line=result.meanPlusTwoSd();
                for(int j=0;j<ranks.length;j++)background.append(ranks[j]).append('\t').append(mean[j]).append('\t').append(sd[j]).append('\t').append(line[j]).append('\n');Files.writeString(home.resolve("background.tsv"),background);
                Files.writeString(home.resolve("parameters.tsv"),"rounded_auc_count\tsource_auc_cutoff\tmean_auc\tsd_auc\n"+result.roundedAucCount()+"\t"+result.sourceAucCutoff()+"\t"+result.meanAuc()+"\t"+result.sdAuc()+"\n");
                Files.writeString(home.resolve("warnings.txt"),String.join("\n",result.warnings())+"\n");
            }
        }
    }
    private static void contracts() {
        double[][] ranks={{0,1,4},{4,6,9},{2,5,7},{7,8,9}};double[] weights={1,1,1};double[][] before=Arrays.stream(ranks).map(double[]::clone).toArray(double[][]::new);
        var p=CisTargetRecovery.ctxcore(ranks,weights,10,.5,7);near(p.motifs().get(0).auc(),7.0/15,1e-15);
        var r=CisTargetRecovery.rcisTarget(ranks,10,5,7);near(r.motifs().get(0).auc(),10.0/15,1e-15);require(p.recoveryRanks()[0]==0 && r.recoveryRanks()[0]==1);
        require(Arrays.deepEquals(ranks,before) && Arrays.equals(weights,new double[]{1,1,1}));
        double value=p.recoveryCurves()[0][0];double[][] returned=p.recoveryCurves();returned[0][0]=123;require(p.recoveryCurves()[0][0]==value);
        int[] target=p.motifs().get(0).targetIndicesInInputOrder();if(target.length>0){int old=target[0];target[0]=99;require(p.motifs().get(0).targetIndicesInInputOrder()[0]==old);}
        require(CisTargetRecovery.ctxcore(ranks,weights,10,.25,7).roundedAucCount()==2);
        var flat=CisTargetRecovery.ctxcore(new double[][]{{9,9},{9,9}},new double[]{1,1},10,.5,7);require(Double.isNaN(flat.motifs().get(0).nes()) && flat.motifs().get(0).status().equals("UNDEFINED_NES"));
        var inf=CisTargetRecovery.rcisTarget(new double[][]{{0,Double.POSITIVE_INFINITY},{8,9}},10,5,7);near(inf.motifs().get(0).auc(),.5,1e-15);require(inf.warnings().stream().anyMatch(s->s.contains("infinity")));
        double[][] equal=new double[7][5];for(double[] row:equal)Arrays.fill(row,3);
        var equalR=CisTargetRecovery.rcisTarget(equal,20,5,8);require(equalR.meanAuc()==.4 && equalR.sdAuc()==0);
        require(equalR.motifs().stream().allMatch(v->Double.isNaN(v.nes()) && v.status().equals("UNDEFINED_NES")));
        var oneR=CisTargetRecovery.rcisTarget(new double[][]{{0,1}},10,5,7);require(Double.isNaN(oneR.sdAuc()) && oneR.motifs().get(0).leadingRank()==-1);
        double[][] eightIdentical=new double[8][1];var equalPython=CisTargetRecovery.ctxcore(eightIdentical,new double[]{1},100,.05,10);
        require(equalPython.motifs().get(0).auc()==.8 && equalPython.sdAuc()==0 && Double.isNaN(equalPython.motifs().get(0).nes()));
        Runnable[] bad={
            ()->CisTargetRecovery.ctxcore(null,weights,10,.5,7),()->CisTargetRecovery.ctxcore(ranks,new double[]{1},10,.5,7),
            ()->CisTargetRecovery.ctxcore(ranks,weights,10,0,7),()->CisTargetRecovery.ctxcore(ranks,weights,10,.01,7),
            ()->CisTargetRecovery.ctxcore(ranks,weights,10,.9,7),()->CisTargetRecovery.ctxcore(ranks,weights,10,.5,10),
            ()->CisTargetRecovery.ctxcore(ranks,new double[]{0,0,0},10,.5,7),()->CisTargetRecovery.ctxcore(ranks,new double[]{1,Double.NaN,1},10,.5,7),
            ()->CisTargetRecovery.ctxcore(new double[][]{{0,-1}},new double[]{1,1},10,.5,7),
            ()->CisTargetRecovery.ctxcore(new double[][]{{0,.5}},new double[]{1,1},10,.5,7),
            ()->CisTargetRecovery.ctxcore(new double[][]{{0,Double.POSITIVE_INFINITY}},new double[]{1,1},10,.5,7),
            ()->CisTargetRecovery.ctxcore(new double[][]{{0,10}},new double[]{1,1},10,.5,7),
            ()->CisTargetRecovery.rcisTarget(ranks,10,.1,7),()->CisTargetRecovery.rcisTarget(ranks,10,5,1),
            ()->CisTargetRecovery.ctxcore(new double[100001][],new double[]{1},10,.5,7)};
        for(Runnable action:bad){boolean refused=false;try{action.run();}catch(IllegalArgumentException expected){refused=true;}require(refused);}
        System.out.println("PASS_CISTARGET_ANALYTIC_PROFILE_BOUNDARIES_IMMUTABILITY_AND_FIFTEEN_REFUSALS_ONLY");
    }
    private static void near(double a,double b,double tol){require(Math.abs(a-b)<=tol*Math.max(1,Math.abs(b)));}
    private static void require(boolean condition){if(!condition)throw new AssertionError("CisTarget component contract failed");}
}
