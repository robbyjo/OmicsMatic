/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Original diploid calls and two-decimal posterior identities; no genotype reconstruction. */
final class BeagleOutputs {
    private BeagleOutputs() { }
    // Two-decimal rounding uses half-cent units: one probability unit is1/200.
    // Exact integer interval arithmetic avoids floating tolerances and per-cell BigDecimals.
    private record Interval(int lower,int upper,int hundredths) { }
    record Counts(long variants,long genotypeCells,long observedPreserved,long completed,long providedPhaseChanges,
            long posteriorFields,long naiveArgmaxDifferences,long strictNonmaxGenotypes) { }

    static Counts validate(Path report,BeagleInputs.Prepared input,String family,String method,String phasePolicy,
            Path output,long maximumReportBytes)throws Exception {
        long cells=Math.multiplyExact((long)input.expected().size(),input.target().samples().size());if(cells>input.limits().cells())throw new IOException("Beagle output genotype-cell work exceeds explicit bound");
        String[] expectedFormat=method.equals("phase")?new String[]{"GT"}:family.equals("4.1")?new String[]{"GT","DS","GP"}:new String[]{"GT","DS","AP1","AP2","GP"};
        var expected=input.expected().entrySet().iterator();long variants=0,observed=0,completed=0,phaseChanges=0,posteriors=0,naive=0,nonmax=0;boolean header=false;
        try(var source=BeagleInputs.open(report,maximumReportBytes);var calls=FileChannel.open(input.callStore(),StandardOpenOption.READ);
                var writer=Files.newBufferedWriter(output.resolve("genotypes.vcf"));var diagnostic=Files.newBufferedWriter(output.resolve("variant-results.tsv"))) {
            diagnostic.write("chromosome\tposition\tvariant\toriginal_input_called\tcompleted_calls\tprovided_phase_order_changes\tnaive_GP_argmax_differences\tstrict_GP_nonmax_genotypes\tupstream_DR2_raw\n");
            for(String line;(line=PlinkGlmInputs.line(source,input.limits().line()))!=null;) {
                if(line.startsWith("##")){if(header)throw new IOException("Beagle output metadata after sample header");if(!line.startsWith("##contig="))writer.write(line+"\n");continue;}
                String[] f=line.split("\t",-1);
                if(line.startsWith("#CHROM\t")) {
                    if(header||f.length!=input.target().samples().size()+9)throw new IOException("Original Beagle output sample header differs");
                    for(int i=9;i<f.length;i++)if(!f[i].equals("T"+(i-8)))throw new IOException("Original Beagle sample order/identity differs");
                    writer.write("##contig=<ID="+input.target().chromosome()+">\n");writer.write(String.join("\t",Arrays.copyOf(f,9)));for(String sample:input.target().samples())writer.write("\t"+sample);writer.write("\n");header=true;continue;
                }
                if(!header||f.length!=input.target().samples().size()+9||!expected.hasNext())throw new IOException("Unexpected Beagle output row/width/extra marker");
                var entry=expected.next();var marker=entry.getValue();
                if(!f[0].equals("1")||!f[1].equals(Integer.toString(marker.position()))||!f[2].equals(input.stagedIds().get(marker.position()))||!f[3].equals(marker.ref())||!f[4].equals(marker.alt()))throw new IOException("Original Beagle marker order/identity/alleles differ");
                if(!Arrays.equals(f[8].split(":"),expectedFormat))throw new IOException("Original Beagle output FORMAT differs from requested version/method");
                var before=input.target().markers().get(marker.position());byte[] previous=before==null?null:BeagleInputs.calls(calls,before,input.target().samples().size());
                long rowObserved=0,rowCompleted=0,rowPhase=0,rowNaive=0,rowNonmax=0;
                for(int i=9;i<f.length;i++) {
                    String[] fields=f[i].split(":",-1);if(fields.length!=expectedFormat.length)throw new IOException("Original Beagle output sample FORMAT width differs");byte after=BeagleInputs.genotype(fields[0]);
                    if(!BeagleInputs.called(after)||(after&4)==0)throw new IOException("Original Beagle output is not complete phased diploid GT");
                    if(previous!=null&&BeagleInputs.called(previous[i-9])) {
                        byte prior=previous[i-9];if(BeagleInputs.dosage(prior)!=BeagleInputs.dosage(after))throw new IOException("Original Beagle changed an observed GT at "+marker.position()+"/"+input.target().samples().get(i-9));
                        rowObserved++;
                        if((prior&4)!=0&&(prior&3)!=(after&3)){rowPhase++;if(phasePolicy.equals("preserve-provided"))throw new IOException("Original Beagle changed provided phase under preserve-provided policy");}
                    } else rowCompleted++;
                    if(method.equals("impute")) {
                        String gpLiteral=fields[family.equals("4.1")?2:4];String[] gpText=gpLiteral.split(",",-1);if(gpText.length!=3)throw new IOException("Biallelic diploid GP requires exactly three values");
                        Interval[] gp={number(gpText[0],1),number(gpText[1],1),number(gpText[2],1)};Interval ds=number(fields[1],2);
                        int lower=0,upper=0;for(var value:gp){lower+=value.lower();upper+=value.upper();}
                        if(lower>200||upper<200)throw new IOException("Original GP cannot sum to1 within its two-decimal rounding intervals");
                        overlap(ds,gp[1].lower()+2*gp[2].lower(),gp[1].upper()+2*gp[2].upper(),"DS/GP");
                        int best=0;for(int j=1;j<3;j++)if(gp[j].hundredths()>gp[best].hundredths())best=j;
                        int genotype=BeagleInputs.dosage(after);if(best!=genotype)rowNaive++;if(gp[genotype].hundredths()<gp[best].hundredths())rowNonmax++;posteriors+=4;
                        if(!family.equals("4.1")) {
                            Interval a=number(fields[2],1),b=number(fields[3],1);overlap(ds,a.lower()+b.lower(),a.upper()+b.upper(),"DS/AP");
                            productOverlap(gp[0],(200-a.upper())*(200-b.upper()),(200-a.lower())*(200-b.lower()),"GP0/AP");
                            productOverlap(gp[2],a.lower()*b.lower(),a.upper()*b.upper(),"GP2/AP");
                            int minimum=40000,maximum=0;for(int x:new int[]{a.lower(),a.upper()})for(int y:new int[]{b.lower(),b.upper()}){int h=200*(x+y)-2*x*y;minimum=Math.min(minimum,h);maximum=Math.max(maximum,h);}
                            productOverlap(gp[1],minimum,maximum,"GP1/AP");
                            if((((after>>1)&1)==0?a.lower()>100:a.upper()<100)||((after&1)==0?b.lower()>100:b.upper()<100))throw new IOException("Original phased GT contradicts its rounded haplotype allele probabilities");posteriors+=2;
                        }
                    }
                }
                variants++;observed+=rowObserved;completed+=rowCompleted;phaseChanges+=rowPhase;naive+=rowNaive;nonmax+=rowNonmax;
                String dr2="";for(String token:f[7].split(";"))if(token.startsWith("DR2="))dr2=token.substring(4);
                diagnostic.write(input.target().chromosome()+"\t"+marker.position()+"\t"+marker.id()+"\t"+rowObserved+"\t"+rowCompleted+"\t"+rowPhase+"\t"+rowNaive+"\t"+rowNonmax+"\t"+dr2+"\n");
                f[0]=input.target().chromosome();f[2]=marker.id();writer.write(String.join("\t",f)+"\n");
            }
            if(!header||expected.hasNext())throw new IOException("Original Beagle output omitted declared samples/markers");
        }
        return new Counts(variants,cells,observed,completed,phaseChanges,posteriors,naive,nonmax);
    }
    private static Interval number(String text,int maximum)throws IOException {
        int dot=text.indexOf('.'),fraction=dot<0?0:text.length()-dot-1,unscaled=0;
        if(text.isEmpty()||text.length()>8||fraction>2||dot==0||dot==text.length()-1)throw new IOException("Unreportable Beagle posterior: "+text);
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='.'&&i==dot)continue;if(c<'0'||c>'9'||unscaled>200)throw new IOException("Unreportable Beagle posterior: "+text);unscaled=10*unscaled+c-'0';}
        int value=unscaled*(fraction==0?100:fraction==1?10:1);if(value<0||value>maximum*100)throw new IOException("Original posterior outside diploid probability/dosage range: "+text);
        return new Interval(Math.max(0,2*value-1),Math.min(maximum*200,2*value+1),value);
    }
    private static void overlap(Interval a,int lower,int upper,String label)throws IOException {if(Math.max(a.lower(),lower)>Math.min(a.upper(),upper))throw new IOException("Original Beagle "+label+" intervals disagree;raw values are retained without renormalization");}
    private static void productOverlap(Interval a,int lower,int upper,String label)throws IOException {if(Math.max(200*a.lower(),lower)>Math.min(200*a.upper(),upper))throw new IOException("Original Beagle "+label+" intervals disagree;raw values are retained without renormalization");}
}
