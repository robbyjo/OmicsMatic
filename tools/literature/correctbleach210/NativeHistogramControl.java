/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import org.jlinalg.imaging.CorrectBleachHistogram210;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Compare every lookup/CDF value and original failure from the complete independent packet. */
public final class NativeHistogramControl {
    private NativeHistogramControl() { }
    public static void main(String[] args)throws Exception {
        long rows=0,cdfValues=0;int cases;
        try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(args[0])),65536))){
            require(input.readUTF().equals("CORRECTBLEACH210_COMPLETE_LOOKUP_V1"),"Reference schema differs");cases=input.readInt();require(cases==1048,"Complete original case population differs");
            for(int i=0;i<cases;i++){
                String id=input.readUTF();int[] a=array(input),r=array(input),beforeA=a==null?null:a.clone(),beforeR=r==null?null:r.clone();boolean success=input.readBoolean();
                if(success){int[] expected=array(input),actual=CorrectBleachHistogram210.matchHistograms(a,r);require(Arrays.equals(actual,expected),"Complete original lookup differs: "+id);rows+=expected.length;}
                else{String expected=input.readUTF(),actual="NO_EXCEPTION";try{CorrectBleachHistogram210.matchHistograms(a,r);}catch(RuntimeException e){actual=e.getClass().getName();}require(actual.equals(expected),"Original lookup failure differs: "+id+" expected="+expected+" actual="+actual);}
                cdfValues+=cdf(input,a,id+"/target");cdfValues+=cdf(input,r,id+"/reference");require(Arrays.equals(a,beforeA)&&Arrays.equals(r,beforeR),"Native modified inputs");
            }
            require(input.read()==-1,"Unexpected trailing reference data");
        }
        boolean capacity=false;try{CorrectBleachHistogram210.matchHistograms(new int[256],new int[256],1);}catch(IllegalArgumentException e){capacity=e.getMessage().contains("no prefix");}require(capacity,"Complete comparison budget not enforced");
        System.out.println("PASS_COMPLETE_ORIGINAL_HISTOGRAM_NATIVE_PARITY cases="+cases+" lookup_rows="+rows+" cdf_values="+cdfValues);
    }
    private static int[] array(DataInputStream in)throws Exception {int n=in.readInt();if(n==-1)return null;require(n>=0&&n<=1000000,"Reference histogram capacity differs");int[] a=new int[n];for(int i=0;i<n;i++)a[i]=in.readInt();return a;}
    private static long cdf(DataInputStream in,int[] histogram,String id)throws Exception {
        if(in.readBoolean()){int n=in.readInt();double[] actual=CorrectBleachHistogram210.cdf(histogram);require(actual.length==n,"Complete CDF length differs");for(int i=0;i<n;i++)require(Double.doubleToRawLongBits(actual[i])==in.readLong(),"Original complete raw CDF bits differ: "+id+"/"+i);return n;}
        String expected=in.readUTF(),actual="NO_EXCEPTION";try{CorrectBleachHistogram210.cdf(histogram);}catch(RuntimeException e){actual=e.getClass().getName();}require(actual.equals(expected),"Original CDF failure differs: "+id);return 0;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
