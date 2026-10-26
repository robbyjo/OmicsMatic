/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * Quality/length/MAXINFO semantics adapted from UsadelLab Trimmomatic v0.41
 * (Bolger, Lohse, Beier, Usadel and contributors), GPLv3. See NOTICE-Trimmomatic.txt.
 * Adapter sequences/clipping algorithms are not included here. */
package org.jlinalg.sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Ordered native quality/length steps with explicit Phred encoding and original-read spans. */
public final class ReadQualityTrimmer {
    private ReadQualityTrimmer() { }
    private static final Set<String> TYPES=Set.of("LEADING","TRAILING","SLIDINGWINDOW","HEADCROP","TAILCROP","CROP","MINLEN","MAXLEN","AVGQUAL","BASECOUNT","TOPHRED33","TOPHRED64","MAXINFO");
    /** Validated immutable step; values retain the exact upstream numeric tokens. */
    public record Step(String type,List<String> values) {
        public Step {
            values=List.copyOf(values);if(!TYPES.contains(type))throw new IllegalArgumentException("Native step unsupported: "+type+"; use upstream mode for ILLUMINACLIP");
            switch(type){
                case "LEADING","TRAILING","AVGQUAL"->{count(values,1);integer(values.get(0),0,93);}
                case "CROP","MINLEN","MAXLEN"->{count(values,1);integer(values.get(0),type.equals("CROP")?1:0,10_000_000);}
                case "HEADCROP","TAILCROP"->{if(!Set.of(1,2,4).contains(values.size()))throw new IllegalArgumentException("Head/tail crop requires1,2or4values: bases[:maxLength][:secondBases:secondMaxLength]");for(int i=0;i<values.size();i++)integer(values.get(i),i%2==1?1:0,10_000_000);}
                case "SLIDINGWINDOW"->{count(values,2);integer(values.get(0),1,10_000_000);floating(values.get(1),0,93);}
                case "BASECOUNT"->{if(values.size()<1||values.size()>3||values.get(0).isEmpty()||values.get(0).chars().anyMatch(c->"ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(c)<0))throw new IllegalArgumentException("BASECOUNT requires literal IUPAC base characters and optional min/max counts");if(values.size()>1)integer(values.get(1),0,10_000_000);if(values.size()>2&&integer(values.get(2),0,10_000_000)<integer(values.get(1),0,10_000_000))throw new IllegalArgumentException("BASECOUNT max is less than min");}
                case "MAXINFO"->{count(values,2);integer(values.get(0),1,1000);floating(values.get(1),0,1);}
                default->count(values,0);
            }
        }
        /** Canonical command token, with every parameter already validated. */
        public String token(){return type+(values.isEmpty()?"":":"+String.join(":",values));}
    }
    /** Read may be null for a dropped observation. Bounds refer to the original sequence, end exclusive. */
    public record Result(FastqIO.Read read,int first,int end,int phredOffset,String droppedAt) {
        /** Whether this original read survives the complete ordered pipeline. */
        public boolean retained(){return read!=null;}
    }
    /** Precomputed immutable pipeline. MAXINFO keeps the upstream1000-base input limit. */
    public static final class Pipeline {
        private final List<Step> steps;private final List<Information> information;
        /** Build once and reuse on a stream, without per-read model/table rebuilding. */
        public Pipeline(List<Step> supplied){if(supplied.isEmpty())throw new IllegalArgumentException("At least one explicit trimming step required");steps=List.copyOf(supplied);var info=new ArrayList<Information>();for(var s:steps)info.add(s.type().equals("MAXINFO")?new Information(integer(s.values().get(0),1,1000),floating(s.values().get(1),0,1)):null);information=java.util.Collections.unmodifiableList(info);}
        /** Selected steps in their original order. */
        public List<Step> steps(){return steps;}
        /** Process one input mate; secondMate chooses the second crop settings only for paired input. */
        public Result apply(FastqIO.Read original,int offset,boolean secondMate){
            if(offset!=33&&offset!=64)throw new IllegalArgumentException("Explicit Phred offset must be33or64");if(original.sequence().length()!=original.quality().length())throw new IllegalArgumentException("Sequence/quality lengths differ");for(int i=0;i<original.quality().length();i++)if(original.quality().charAt(i)<offset||original.quality().charAt(i)-offset>93)throw new IllegalArgumentException("Quality character differs from declared Phred encoding");
            String sequence=original.sequence(),quality=original.quality();int first=0,end=sequence.length();
            for(int index=0;index<steps.size();index++){
                var step=steps.get(index);var v=step.values();int length=end-first,newFirst=first,newEnd=end;boolean drop=false;
                switch(step.type()){
                    case "HEADCROP","TAILCROP"->{int parameter=secondMate&&v.size()==4?2:0,bases=integer(v.get(parameter),0,10_000_000),maximum=v.size()>1?integer(v.get(parameter+1),1,10_000_000):Integer.MAX_VALUE/2;int remove=Math.max(bases,length-maximum);if(length<=remove)drop=true;else if(step.type().equals("HEADCROP"))newFirst+=remove;else newEnd-=remove;}
                    case "CROP"->newEnd=first+Math.min(length,integer(v.get(0),1,10_000_000));
                    case "MINLEN"->drop=length<integer(v.get(0),0,10_000_000);
                    case "MAXLEN"->drop=length>integer(v.get(0),0,10_000_000);
                    case "LEADING"->{int threshold=integer(v.get(0),0,93),i=first;while(i<end&&q(sequence,quality,i,offset)<threshold)i++;drop=i==end;newFirst=i;}
                    // Preserve the original program's strict i>0 rule: TRAILING drops one-base reads.
                    case "TRAILING"->{int threshold=integer(v.get(0),0,93),i=end-1;while(i>first&&q(sequence,quality,i,offset)<threshold)i--;drop=i<=first;newEnd=i+1;}
                    case "AVGQUAL"->{long total=0;for(int i=first;i<end;i++)total+=q(sequence,quality,i,offset);drop=total<(long)integer(v.get(0),0,93)*length;}
                    case "BASECOUNT"->{String bases=v.get(0);int n=0;for(int i=first;i<end;i++)if(bases.indexOf(sequence.charAt(i))>=0)n++;drop=n<(v.size()>1?integer(v.get(1),0,10_000_000):0)||v.size()>2&&n>integer(v.get(2),0,10_000_000);}
                    case "SLIDINGWINDOW"->{
                        int window=integer(v.get(0),1,10_000_000);float required=floating(v.get(1),0,93),totalRequired=required*window;if(length<window){drop=true;break;}int total=0;for(int i=0;i<window;i++)total+=q(sequence,quality,first+i,offset);if(total<totalRequired){drop=true;break;}int keep=length;for(int i=0;i<length-window;i++){total=total-q(sequence,quality,first+i,offset)+q(sequence,quality,first+i+window,offset);if(total<totalRequired){keep=i+window;break;}}while(keep>1&&q(sequence,quality,first+keep-1,offset)<required)keep--;newEnd=first+keep;
                    }
                    case "MAXINFO"->{if(length>1000)throw new IllegalArgumentException("MAXINFO requires at most1000bases at that step; inspect upstream limits and pipeline order, no automatic crop");int keep=information.get(index).length(sequence,quality,first,end,offset);drop=keep==0;newEnd=first+keep;}
                    case "TOPHRED33","TOPHRED64"->{int desired=step.type().equals("TOPHRED33")?33:64;if(desired!=offset){var converted=new StringBuilder(quality);for(int i=first;i<end;i++)converted.setCharAt(i,(char)(quality.charAt(i)+desired-offset));quality=converted.toString();offset=desired;}}
                    default->throw new IllegalStateException("Unexpected validated step");
                }
                if(drop)return new Result(null,0,0,offset,index+1+":"+step.token());first=newFirst;end=newEnd;
            }
            return new Result(new FastqIO.Read(original.header(),sequence.substring(first,end),quality.substring(first,end)),first,end,offset,null);
        }
    }
    private static void count(List<String> a,int n){if(a.size()!=n)throw new IllegalArgumentException("Wrong number of trimming parameters; expected "+n);}
    private static int integer(String value,int low,int high){int x=Integer.parseInt(value);if(x<low||x>high)throw new IllegalArgumentException("Integer trimming parameter outside "+low+".."+high);return x;}
    private static float floating(String value,float low,float high){float x=Float.parseFloat(value);if(!Float.isFinite(x)||x<low||x>high)throw new IllegalArgumentException("Finite trimming parameter required in "+low+".."+high);return x;}
    private static int q(String sequence,String quality,int i,int offset){return sequence.charAt(i)=='N'?0:quality.charAt(i)-offset;}
    private static final class Information {
        private final long[] lengthScore=new long[1000],qualityScore=new long[61];
        Information(int target,float strictness){
            var lengths=new double[1000];var qualities=new double[61];for(int i=0;i<lengths.length;i++)lengths[i]=Math.log(1.0/(1.0+Math.exp(target-i-1)))+Math.log(i+1)*(1-strictness);for(int i=0;i<qualities.length;i++)qualities[i]=Math.log(1-Math.pow(.1,(.5+i)/10.))*strictness;double ratio=Math.max(normalization(lengths),normalization(qualities));for(int i=0;i<lengths.length;i++)lengthScore[i]=(long)(lengths[i]*ratio);for(int i=0;i<qualities.length;i++)qualityScore[i]=(long)(qualities[i]*ratio);
        }
        private static double normalization(double[] a){double maximum=a[0];for(int i=1;i<a.length;i++){double value=Math.abs(a[i]);if(value>maximum)maximum=value;}return Long.MAX_VALUE/(maximum*2000);}
        int length(String sequence,String quality,int first,int end,int offset){long cumulative=0;double best=-Double.MAX_VALUE;int position=0;for(int i=first;i<end;i++){cumulative+=qualityScore[Math.max(0,Math.min(60,q(sequence,quality,i,offset)))];long score=lengthScore[i-first]+cumulative;if(score>=best){best=score;position=i-first+1;}}return position<1||best==0?0:position;}
    }
}
