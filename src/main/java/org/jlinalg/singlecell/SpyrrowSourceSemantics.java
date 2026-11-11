/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source-compatible CPython3.11.17 norm/hash/set algorithms. The original PSF
 * license and source notices are retained in omics/spyrrow-communication. */
package org.jlinalg.singlecell;

import java.util.*;

/** Explicit Linux little-endian64-bit CPython3.11.17/SipHash13/PYTHONHASHSEED=0 profile.
 * This is not an inference of the paper's unspecified hash seed or historical runtime. */
public final class SpyrrowSourceSemantics {
    private SpyrrowSourceSemantics() { }
    /** Literal two-coordinate math.dist norm, including subtraction overflow. */
    public static double distance(double ax,double ay,double bx,double by) {
        double[] values={Math.abs(ax-bx),Math.abs(ay-by)};double maximum=0;boolean foundNan=false;for(double value:values){foundNan|=Double.isNaN(value);if(value>maximum)maximum=value;}
        if(Double.isInfinite(maximum))return maximum;if(foundNan)return Double.NaN;if(maximum==0)return maximum;
        int exponent=Math.getExponent(maximum)+1;if(exponent==-1022){long mantissa=Double.doubleToRawLongBits(maximum)&0xfffffffffffffL;exponent=(64-Long.numberOfLeadingZeros(mantissa))-1074;}
        double sum=1,first=0,second=0,third=0;
        if(exponent>=-1023){double scale=Math.scalb(1.0,-exponent);for(double original:values){double x=original*scale,t=x*134217729.0,high=t-(t-x),low=x-high;double part=high*high,old=sum;sum+=part;first+=(old-sum)+part;part=2.0*high*low;old=sum;sum+=part;second+=(old-sum)+part;third+=low*low;}
            double h=Math.sqrt(sum-1.0+(first+second+third)),t=h*134217729.0,high=t-(t-h),low=h-high;double part=-high*high,old=sum;sum+=part;first+=(old-sum)+part;part=-2.0*high*low;old=sum;sum+=part;second+=(old-sum)+part;part=-low*low;old=sum;sum+=part;third+=(old-sum)+part;double remainder=sum-1.0+(first+second+third);return (h+remainder/(2.0*h))/scale;
        }
        for(double original:values){double x=original/maximum;double part=x*x,old=sum;sum+=part;first+=(old-sum)+part;}return maximum*Math.sqrt(sum-1.0+first);
    }
    /** PEP393 native Unicode storage is hashed, not UTF8. Empty strings hash to0. */
    public static long stringHash(String text) {
        Objects.requireNonNull(text);if(text.isEmpty())return 0;int[] codepoints=text.codePoints().toArray();int maximum=0;for(int codepoint:codepoints){require(codepoint<0xd800||codepoint>0xdfff,"Unpaired surrogate is outside the source string profile");maximum=Math.max(maximum,codepoint);}int width=maximum<=255?1:maximum<=65535?2:4;byte[] bytes=new byte[Math.multiplyExact(codepoints.length,width)];int cursor=0;for(int codepoint:codepoints)for(int j=0;j<width;j++)bytes[cursor++]=(byte)(codepoint>>>(8*j));long hash=siphash(bytes);return hash==-1?-2:hash;
    }
    private static long siphash(byte[] bytes) {
        long[] v={0x736f6d6570736575L,0x646f72616e646f6dL,0x6c7967656e657261L,0x7465646279746573L};int at=0;for(;at+8<=bytes.length;at+=8){long word=0;for(int j=0;j<8;j++)word|=(long)Byte.toUnsignedInt(bytes[at+j])<<(8*j);v[3]^=word;round(v);v[0]^=word;}long last=(long)bytes.length<<56;for(int j=0;at+j<bytes.length;j++)last|=(long)Byte.toUnsignedInt(bytes[at+j])<<(8*j);v[3]^=last;round(v);v[0]^=last;v[2]^=0xff;round(v);round(v);round(v);return (v[0]^v[1])^(v[2]^v[3]);
    }
    private static void round(long[] v){v[0]+=v[1];v[1]=Long.rotateLeft(v[1],13)^v[0];v[0]=Long.rotateLeft(v[0],32);v[2]+=v[3];v[3]=Long.rotateLeft(v[3],16)^v[2];v[0]+=v[3];v[3]=Long.rotateLeft(v[3],21)^v[0];v[2]+=v[1];v[1]=Long.rotateLeft(v[1],17)^v[2];v[2]=Long.rotateLeft(v[2],32);}
    /** Exact iteration order for set(list_of_strings) under the declared seed0 profile. */
    public static List<String> setOrder(List<String> input) {
        require(input.size()<=1000000,"Complete source set input capacity exceeded");String[] table=new String[8];long[] hashes=new long[8];int used=0;
        for(String key:input){Objects.requireNonNull(key);long hash=stringHash(key);int mask=table.length-1,index=(int)(hash&mask);long perturb=hash;boolean found=false;
            search:for(;;){int probes=index+9<=mask?9:0;for(int offset=0;offset<=probes;offset++){int slot=index+offset;if(table[slot]==null){table[slot]=key;hashes[slot]=hash;used++;break search;}if(hashes[slot]==hash&&table[slot].equals(key)){found=true;break search;}}perturb>>>=5;index=(int)((index*5L+1+perturb)&mask);}
            if(!found&&(long)used*5>=(long)mask*3){int minimum=Math.multiplyExact(used,used>50000?2:4),size=8;while(size<=minimum)size=Math.multiplyExact(size,2);String[] previous=table;long[] previousHashes=hashes;table=new String[size];hashes=new long[size];mask=size-1;
                for(int i=0;i<previous.length;i++)if(previous[i]!=null){hash=previousHashes[i];index=(int)(hash&mask);perturb=hash;search:for(;;){if(table[index]==null){table[index]=previous[i];hashes[index]=hash;break;}if(index+9<=mask)for(int j=1;j<=9;j++)if(table[index+j]==null){table[index+j]=previous[i];hashes[index+j]=hash;break search;}perturb>>>=5;index=(int)((index*5L+1+perturb)&mask);}}
            }
        }
        var result=new ArrayList<String>();for(String key:table)if(key!=null)result.add(key);return List.copyOf(result);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
