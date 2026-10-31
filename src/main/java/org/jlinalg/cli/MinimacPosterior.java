/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Original-output rounding envelopes and posterior identities, without renormalization or phase recovery. */
final class MinimacPosterior {
    private MinimacPosterior() { }
    record Range(double lower,double upper) {
        Range { if(Double.isNaN(lower)||Double.isNaN(upper)||lower>upper)throw new IllegalArgumentException("Invalid posterior envelope"); }
        boolean overlaps(Range other){return lower<=other.upper&&other.lower<=upper;}
    }
    record Num(String raw,double value,Range range,BigDecimal decimal) { }
    record Cell(List<Integer> calls,Num dosage,List<Num> probabilities,List<Num> haplotypes,
            Range copies,Range squaredCopies,double[] modernHds) { }
    static Num number(String text,int maximum,boolean modern,boolean info)throws IOException{
        BigDecimal decimal;
        try{
            if(text.length()>128||!text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new NumberFormatException();
            decimal=new BigDecimal(text);
            if(decimal.signum()<0||decimal.compareTo(BigDecimal.valueOf(maximum))>0)throw new NumberFormatException();
        }catch(NumberFormatException failure){throw new IOException("Nonfinite/out-of-range original posterior/statistic: "+text,failure);}
        double value=decimal.doubleValue();
        if(!Double.isFinite(value))throw new IOException("Original numeric field exceeds finite capacity");
        BigDecimal low,high;
        if(modern){
            BigDecimal stripped=decimal.stripTrailingZeros();
            if(stripped.precision()>6)throw new IOException("Original current field exceeds its six-significant-digit text contract: "+text);
            if(decimal.signum()==0){low=BigDecimal.ZERO;high=BigDecimal.ZERO;}
            else{
                long exponentLong=(long)stripped.precision()-stripped.scale()-1;
                if(exponentLong < -50 || exponentLong > 10)throw new IOException("Printed value cannot come from the declared binary32 source field");
                int exponent=(int)exponentLong;
                BigDecimal quantum=BigDecimal.ONE.scaleByPowerOfTen(exponent-5);
                BigDecimal lowerQuantum=decimal.compareTo(BigDecimal.ONE.scaleByPowerOfTen(exponent))==0?quantum.movePointLeft(1):quantum;
                low=decimal.subtract(lowerQuantum.divide(BigDecimal.valueOf(2)));
                high=decimal.add(quantum.divide(BigDecimal.valueOf(2)));
            }
        }else{
            int digits=info?5:3;
            if(decimal.stripTrailingZeros().scale()>digits)throw new IOException("Original historical field exceeds its declared decimal precision: "+text);
            BigDecimal half=BigDecimal.valueOf(5).scaleByPowerOfTen(-digits-1);
            low=decimal.subtract(half);high=decimal.add(half);
        }
        double lower=Math.max(0,Math.nextDown(low.doubleValue())),upper=Math.min(maximum,Math.nextUp(high.doubleValue()));
        if(decimal.signum()==0&&modern)lower=upper=0;
        if(modern&&ceilFloat(lower)>floorFloat(upper))throw new IOException("Printed current value contains no compatible binary32 source value");
        return new Num(text,value,new Range(lower,upper),decimal);
    }
    static Cell cell(Map<String,String> fields,int ploidy,boolean modern)throws IOException{
        String gt=required(fields,"GT");
        if(gt.indexOf('/')>=0)throw new IOException("Original output GT is unphased; no phase is reconstructed");
        String[] alleles=gt.split("\\|",-1);
        if(alleles.length!=ploidy)throw new IOException("Original output ploidy differs from declared target copies");
        var calls=new ArrayList<Integer>();
        for(String allele:alleles){if(!allele.equals("0")&&!allele.equals("1"))throw new IOException("Original output requires complete biallelic GT");calls.add(Integer.parseInt(allele));}
        Num ds=number(required(fields,"DS"),ploidy,modern,false);
        List<Num> gp=vector(required(fields,"GP"),ploidy+1,modern);
        Range sum=new Range(0,0);for(Num value:gp)sum=add(sum,value.range);
        require(sum.overlaps(new Range(1,1)),"GP cannot sum to one within original rounding envelopes");
        Range fromGp=new Range(0,0);
        for(int i=1;i<gp.size();i++)fromGp=add(fromGp,scale(gp.get(i).range,i));
        require(ds.range.overlaps(fromGp),"DS and GP disagree beyond original printed precision");
        var hds=new ArrayList<Num>();double[] modernValues=null;
        Range doseRange=intersect(ds.range,fromGp),squared;
        if(modern){
            hds.addAll(vector(required(fields,"HDS"),ploidy,true));modernValues=new double[ploidy];
            Range hsum=new Range(0,0),hsq=new Range(0,0),variance=new Range(0,0);
            for(int i=0;i<ploidy;i++){
                Num h=hds.get(i);modernValues[i]=h.value;
                require(calls.get(i)==0?h.range.lower<.5:h.range.upper>=.5,"Phased GT contradicts source HDS rounding interval");
                hsum=add(hsum,h.range);hsq=add(hsq,multiply(h.range,h.range));
                variance=add(variance,multiply(h.range,oneMinus(h.range)));
            }
            require(ds.range.overlaps(hsum),"DS disagrees with original ordered HDS");
            doseRange=intersect(doseRange,hsum);
            if(ploidy==1){
                require(gp.get(0).range.overlaps(oneMinus(hds.get(0).range))&&gp.get(1).range.overlaps(hds.get(0).range),"Haploid GP/HDS disagree");
            }else{
                Range x=hds.get(0).range,y=hds.get(1).range;
                require(gp.get(0).range.overlaps(multiply(oneMinus(x),oneMinus(y))),"GP0/HDS disagree");
                require(gp.get(1).range.overlaps(add(multiply(x,oneMinus(y)),multiply(y,oneMinus(x)))),"GP1/HDS disagree");
                require(gp.get(2).range.overlaps(multiply(x,y)),"GP2/HDS disagree");
            }
            Num sd=number(required(fields,"SD"),ploidy,true,false);
            require(sd.range.overlaps(variance),"Original SD is posterior variance and disagrees with HDS Bernoulli variance");
            squared=hsq;
        }else if(ploidy==1){
            require(calls.get(0)==0?doseRange.lower<.5:doseRange.upper>=.5,"Historical haploid GT/DS are incompatible");
            squared=new Range(Math.max(0,Math.nextDown(doseRange.lower*doseRange.lower)),Math.nextUp(doseRange.upper*doseRange.upper));
        }else{
            int count=calls.get(0)+calls.get(1);
            double low=Math.max(doseRange.lower,count==0?0:count==1?.5:1);
            double high=Math.min(doseRange.upper,count==0?1:count==1?1.5:2);
            require(low<=high,"Historical phased calls contradict rounded dosage");
            double minProduct=count==0?Math.max(0,.5*(low-.5)):count==1?Math.max(0,low-1):low<=1.5?.5*(low-.5):low-1;
            double maxProduct=count==1?.5*(high-.5):high*high/4;
            require(gp.get(2).range.overlaps(new Range(Math.max(0,Math.nextDown(minProduct)),Math.nextUp(maxProduct))),
                    "Historical GT count and GP2 have no compatible independent-copy probabilities");
            double lower=Math.max(0,doseRange.lower*doseRange.lower-2*gp.get(2).range.upper);
            double upper=Math.min(2,doseRange.upper*doseRange.upper-2*gp.get(2).range.lower);
            require(lower<=upper,"Historical symmetric haplotype moments are inconsistent");
            squared=new Range(Math.max(0,Math.nextDown(lower)),Math.nextUp(upper));
            // GP does not identify ordered haplotype dosages. Never synthesize or publish HDS here.
        }
        return new Cell(List.copyOf(calls),ds,List.copyOf(gp),List.copyOf(hds),doseRange,squared,modernValues);
    }
    static final class Cohort {
        final boolean modern; final int buffer;
        long copies,samples,stableN;double lowSum,highSum,lowSq,highSq,mean,m2,stableSum,stableCorrection;
        float chunkLo,chunkHi,chunkSqLo,chunkSqHi,totalLo,totalHi,totalSqLo,totalSqHi;
        int chunkSamples;
        Cohort(boolean modern,int buffer){this.modern=modern;this.buffer=buffer;}
        void add(Cell cell){
            samples++;copies+=cell.calls.size();
            if(modern){
                for(Num h:cell.haplotypes){
                    float lo=ceilFloat(h.range.lower),hi=floorFloat(h.range.upper);
                    chunkLo+=lo;chunkHi+=hi;chunkSqLo+=lo*lo;chunkSqHi+=hi*hi;
                    double delta=h.value-mean;mean+=delta/(++stableN);m2+=delta*(h.value-mean);
                    double adjusted=h.value-stableCorrection,total=stableSum+adjusted;stableCorrection=(total-stableSum)-adjusted;stableSum=total;
                }
                if(++chunkSamples==buffer)flush();
            }else{
                lowSum=Math.nextDown(lowSum+cell.copies.lower);highSum=Math.nextUp(highSum+cell.copies.upper);
                double error=Math.max(Math.ulp((float)cell.squaredCopies.lower),Math.ulp((float)cell.squaredCopies.upper))*cell.calls.size();
                lowSq=Math.nextDown(lowSq+cell.squaredCopies.lower-error);highSq=Math.nextUp(highSq+cell.squaredCopies.upper+error);
            }
        }
        private void flush(){totalLo+=chunkLo;totalHi+=chunkHi;totalSqLo+=chunkSqLo;totalSqHi+=chunkSqHi;chunkLo=chunkHi=chunkSqLo=chunkSqHi=0;chunkSamples=0;}
        Range sum(){if(modern){if(chunkSamples>0)flush();return new Range(totalLo,totalHi);}return new Range(Math.max(0,lowSum),Math.min(copies,highSum));}
        Range squares(){return modern?new Range(totalSqLo,totalSqHi):new Range(Math.max(0,lowSq),highSq);}
        Range af(){if(!modern&&copies<2)return new Range(0,0);Range x=sum();if(x.upper==0)return new Range(0,0);if(x.lower==copies&&x.upper==copies)return new Range(1,1);Range v=modern?floatEnvelope(x.lower/copies,x.upper/copies):new Range(Math.nextDown(x.lower/copies),Math.nextUp(x.upper/copies));return new Range(Math.max(0,v.lower),Math.min(1,v.upper));}
        Range r2(){
            if(!modern&&copies<2)return new Range(0,0);
            Range x=sum();if(x.upper==0||x.lower==copies&&x.upper==copies)return new Range(0,0);
            Range xx=squares(),af=af();
            double numeratorLo=Math.max(0,xx.lower-x.upper*x.upper/copies)/copies;
            double numeratorHi=Math.max(0,xx.upper-x.lower*x.lower/copies)/copies;
            double d1=af.lower*(1-af.lower),d2=af.upper*(1-af.upper),denLo=Math.min(d1,d2),denHi=af.lower<=.5&&af.upper>=.5?.25:Math.max(d1,d2);
            if(denHi==0&&numeratorHi==0)return new Range(0,0);
            double lower=denHi>0?Math.max(0,Math.nextDown(numeratorLo/denHi)):0,upper=denLo>0?Math.nextUp(numeratorHi/denLo):Double.POSITIVE_INFINITY;
            return modern&&Double.isFinite(upper)?floatEnvelope(lower,upper):new Range(lower,upper);
        }
        Double stableR2(){if(!modern)return null;double af=stableSum/copies;return af>0&&af<1?Math.max(0,Math.min(1,(m2/copies)/(af*(1-af)))):null;}
    }
    static Range minimumAlleleFrequency(Range af){
        double a=Math.min(af.lower,1-af.lower),b=Math.min(af.upper,1-af.upper);
        return new Range(Math.max(0,Math.nextDown(Math.min(a,b))),Math.min(.5,Math.nextUp(af.lower<=.5&&af.upper>=.5?.5:Math.max(a,b))));
    }
    static List<Num> vector(String text,int size,boolean modern)throws IOException{
        String[] values=text.split(",",-1);require(values.length==size,"Original posterior vector/ploidy differs");
        var result=new ArrayList<Num>();for(String value:values)result.add(number(value,1,modern,false));return result;
    }
    static Range add(Range a,Range b){return floatEnvelope(a.lower+b.lower,a.upper+b.upper);}
    static Range scale(Range a,int n){return floatEnvelope(a.lower*n,a.upper*n);}
    static Range multiply(Range a,Range b){return floatEnvelope(Math.max(0,a.lower*b.lower),Math.max(0,a.upper*b.upper));}
    static Range oneMinus(Range a){Range v=floatEnvelope(1-a.upper,1-a.lower);return new Range(Math.max(0,v.lower),Math.min(1,v.upper));}
    static Range floatEnvelope(double low,double high){
        double lower=Math.nextDown((double)(float)low),upper=Math.nextUp((double)(float)high);
        return new Range(Math.max(0,Math.min(low,lower)),Math.max(high,upper));
    }
    static Range intersect(Range a,Range b)throws IOException{require(a.overlaps(b),"Posterior envelopes do not intersect");return new Range(Math.max(a.lower,b.lower),Math.min(a.upper,b.upper));}
    static float ceilFloat(double n){float f=(float)n;return f<n?Math.nextUp(f):f;}
    static float floorFloat(double n){float f=(float)n;return f>n?Math.nextDown(f):f;}
    static String required(Map<String,String> fields,String key)throws IOException{String value=fields.get(key);if(value==null)throw new IOException("Original output omits "+key);return value;}
    static void require(boolean condition,String reason)throws IOException{if(!condition)throw new IOException(reason);}
}

