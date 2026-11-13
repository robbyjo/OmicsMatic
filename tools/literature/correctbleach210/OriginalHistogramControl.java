/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import histogram2.HistogramMatcher;
import histogram2.Util;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Independent complete lookup/CDF controls using the unchanged published upstream JAR. */
public final class OriginalHistogramControl {
    private OriginalHistogramControl() { }
    private record Case(String id,int[] target,int[] reference) { }
    public static void main(String[] args)throws Exception {
        var cases=new ArrayList<Case>();
        for(int a=0;a<27;a++)for(int b=0;b<27;b++)cases.add(new Case("exhaustive-"+a+"-"+b,ternary(a),ternary(b)));
        Random random=new Random(21020261113L);
        for(int i=0;i<300;i++){
            int[] a=new int[1+random.nextInt(9)],r=new int[1+random.nextInt(10)];
            for(int j=0;j<a.length;j++)a[j]=i%3==0?random.nextInt():random.nextInt(8)-2;
            for(int j=0;j<r.length;j++)r[j]=i%3==0?random.nextInt():random.nextInt(8)-2;
            cases.add(new Case("overflow-length-"+i,a,r));
        }
        for(int bins:new int[]{1,2,256,4096,65536}){
            int[] zero=new int[bins],ramp=new int[bins],sparse=new int[bins],reversed=new int[bins];
            for(int i=0;i<bins;i++){ramp[i]=(i*13+3)%7;sparse[i]=i%97==0?1+i%11:0;reversed[i]=((bins-1-i)*13+3)%7;}
            cases.add(new Case("full-ramp-"+bins,ramp,reversed));cases.add(new Case("full-sparse-"+bins,sparse,ramp));cases.add(new Case("full-zero-"+bins,zero,zero));
        }
        cases.add(new Case("target-empty",new int[0],new int[]{1}));cases.add(new Case("reference-empty",new int[]{1},new int[0]));cases.add(new Case("target-null",null,new int[]{1}));cases.add(new Case("reference-null",new int[]{1},null));
        long rows=0;
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0])),65536))){
            out.writeUTF("CORRECTBLEACH210_COMPLETE_LOOKUP_V1");out.writeInt(cases.size());
            for(Case c:cases){
                out.writeUTF(c.id());array(out,c.target());array(out,c.reference());int[] beforeA=c.target()==null?null:c.target().clone(),beforeR=c.reference()==null?null:c.reference().clone();
                try{int[] f=new HistogramMatcher().matchHistograms(c.target(),c.reference());out.writeBoolean(true);array(out,f);rows+=f.length;}catch(RuntimeException e){out.writeBoolean(false);out.writeUTF(e.getClass().getName());}
                cdf(out,c.target());cdf(out,c.reference());
                if(!Arrays.equals(beforeA,c.target())||!Arrays.equals(beforeR,c.reference()))throw new IllegalStateException("Original mutated histogram inputs");
            }
        }
        System.out.println("PASS_COMPLETE_ORIGINAL_HISTOGRAM_REFERENCE cases="+cases.size()+" lookup_rows="+rows);
    }
    private static int[] ternary(int n){return new int[]{n%3,n/3%3,n/9%3};}
    private static void array(DataOutputStream out,int[] a)throws Exception {out.writeInt(a==null?-1:a.length);if(a!=null)for(int x:a)out.writeInt(x);}
    private static void cdf(DataOutputStream out,int[] histogram)throws Exception {
        try{double[] values=Util.Cdf(histogram);out.writeBoolean(true);out.writeInt(values.length);for(double x:values)out.writeLong(Double.doubleToRawLongBits(x));}catch(RuntimeException e){out.writeBoolean(false);out.writeUTF(e.getClass().getName());}
    }
}
