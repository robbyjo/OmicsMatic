/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Conditional Bernoulli allocations, compressed by identical genotype patterns. */
public final class ConditionalSetNull {
    private ConditionalSetNull() { }

    /** Fixed stratum counts and known log-odds offsets; no estimated slopes. */
    public static final class Model {
        final double[] y,offset,mean;
        final List<int[]> strata;
        private Model(double[] y,double[] offset,double[] mean,List<int[]> strata){this.y=y.clone();this.offset=offset;this.mean=mean;this.strata=List.copyOf(strata);}
        public int observations(){return y.length;}
        public int strata(){return strata.size();}
        public int cases(){return (int)Arrays.stream(y).sum();}
        public double[] nullMeans(){return mean.clone();}
    }
    /** Prepare once for repeated masks. Intercepts are determined by the conditioned totals. */
    public static Model prepare(double[] response,int[] stratum,double[] knownOffset,long maximumWork){
        int n=response.length;if(n<2||stratum.length!=n||knownOffset!=null&&knownOffset.length!=n)throw new IllegalArgumentException("Conditional binary null dimensions differ");
        var rows=new java.util.TreeMap<Integer,List<Integer>>();double[] offset=knownOffset==null?new double[n]:knownOffset.clone(),mean=new double[n];
        for(int i=0;i<n;i++){if(response[i]!=0&&response[i]!=1||!Double.isFinite(offset[i]))throw new IllegalArgumentException("Conditional binary null needs 0/1 outcomes and finite known offsets");rows.computeIfAbsent(stratum[i],ignored->new ArrayList<>()).add(i);}
        var groups=new ArrayList<int[]>();var budget=new Budget(maximumWork,Long.MAX_VALUE);
        for(var entry:rows.values()){
            int[] index=entry.stream().mapToInt(Integer::intValue).toArray();groups.add(index);int k=0;double loOffset=Double.POSITIVE_INFINITY,hiOffset=Double.NEGATIVE_INFINITY;
            for(int i:index){k+=(int)response[i];loOffset=Math.min(loOffset,offset[i]);hiOffset=Math.max(hiOffset,offset[i]);}
            if(!Double.isFinite(hiOffset-loOffset)||hiOffset-loOffset>700)throw new IllegalArgumentException("Known within-stratum offset range exceeds the supported numerical range of 700 log-odds units");
            for(int i:index)offset[i]-=hiOffset;
            if(k==0||k==index.length){for(int i:index)mean[i]=k==0?0:1;continue;}
            if(loOffset==hiOffset){for(int i:index)mean[i]=(double)k/index.length;continue;}
            double lo=-40-Math.log(index.length),hi=hiOffset-loOffset+40+Math.log(index.length);
            budget.spend(160L*index.length);
            for(int iteration=0;iteration<160;iteration++){double a=lo/2+hi/2,total=0;for(int i:index)total+=sigmoid(a+offset[i]);if(total<k)lo=a;else hi=a;}
            double a=lo/2+hi/2;for(int i:index)mean[i]=sigmoid(a+offset[i]);
        }
        return new Model(response,offset,mean,groups);
    }
    private static double sigmoid(double x){if(x>=0)return 1/(1+Math.exp(-x));double e=Math.exp(x);return e/(1+e);}

    static final class Budget {
        final long maximum,maximumCells;long used;
        Budget(long maximum,long cells){if(maximum<1||cells<1)throw new IllegalArgumentException("Conditional set budgets must be positive");this.maximum=maximum;maximumCells=cells;}
        void spend(long work){if(work<0||work>maximum-used)throw new IllegalArgumentException("Conditional set test exceeds maximum_work; no null draws or variants were silently truncated");used+=work;}
        void cells(long cells){if(cells<0||cells>maximumCells)throw new IllegalArgumentException("Conditional set test exceeds maximum_cells; increase the registered memory budget or use a supported external analysis");}
    }
    static final class Group {
        final double[] genotype,polynomial;final int size,observed;final String key;
        Group(double[] genotype,List<Integer> rows,Model model,int k,Budget budget,String key){
            this.genotype=genotype.clone();this.size=rows.size();this.key=key;int observed=0;double[] offsets=new double[size];
            for(int j=0;j<size;j++){int i=rows.get(j);observed+=(int)model.y[i];offsets[j]=model.offset[i];}this.observed=observed;Arrays.sort(offsets);
            int degree=Math.min(size,k);polynomial=new double[degree+1];Arrays.fill(polynomial,Double.NEGATIVE_INFINITY);polynomial[0]=0;
            if(offsets[0]==offsets[size-1]){budget.spend(degree);int peak=Math.min(degree,(int)Math.floor((size+1.0)*sigmoid(offsets[0])));polynomial[peak]=0;for(int j=peak;j>0;j--)polynomial[j-1]=polynomial[j]-Math.log(size-j+1.0)+Math.log(j)-offsets[0];for(int j=peak+1;j<=degree;j++)polynomial[j]=polynomial[j-1]+Math.log(size-j+1.0)-Math.log(j)+offsets[0];}
            else{budget.spend((long)size*degree);for(int j=0;j<size;j++)for(int t=Math.min(j+1,degree);t>=1;t--)polynomial[t]=add(polynomial[t],polynomial[t-1]+offsets[j]);double peak=Arrays.stream(polynomial).max().orElseThrow();for(int j=0;j<polynomial.length;j++)polynomial[j]-=peak;}
        }
    }
    static final class Stratum {
        final List<Group> groups;final int cases,dimensions;final double[] center;
        final double[][] suffix;final double[] totalLog;final int[] totals;final long states;
        Stratum(Model model,int[] rows,double[][] genotype,int dimensions,long stateCap,Budget budget){
            this.dimensions=dimensions;int cases=0;for(int i:rows)cases+=(int)model.y[i];this.cases=cases;center=new double[dimensions];
            var patterns=new java.util.TreeMap<String,List<Integer>>();var vectors=new LinkedHashMap<String,double[]>();
            for(int i:rows){String key=Arrays.toString(genotype[i]);patterns.computeIfAbsent(key,ignored->new ArrayList<>()).add(i);vectors.putIfAbsent(key,genotype[i]);for(int j=0;j<dimensions;j++)center[j]+=genotype[i][j]*model.mean[i];}
            var built=new ArrayList<Group>();for(var e:patterns.entrySet())built.add(new Group(vectors.get(e.getKey()),e.getValue(),model,cases,budget,e.getKey()));
            built.sort(Comparator.comparingInt((Group g)->g.size).thenComparing(g->g.key));groups=List.copyOf(built);
            int q=groups.size()-1;Group reservoir=groups.get(q);int h=rows.length-reservoir.size;
            budget.cells((long)(q+2)*(Math.min(h,cases)+1)+rows.length+(long)groups.size()*dimensions);
            suffix=new double[q+1][];suffix[q]=new double[]{0};long[] counts={1};
            for(int j=q-1;j>=0;j--){var group=groups.get(j);int degree=Math.min(cases,suffix[j+1].length-1+group.size);double[] mass=new double[degree+1];Arrays.fill(mass,Double.NEGATIVE_INFINITY);long[] next=new long[degree+1];
                budget.spend((long)(degree+1)*group.polynomial.length);
                for(int r=0;r<=degree;r++)for(int t=Math.max(0,r-suffix[j+1].length+1);t<group.polynomial.length&&t<=r;t++){
                    mass[r]=add(mass[r],group.polynomial[t]+suffix[j+1][r-t]);next[r]=saturating(next[r],counts[r-t],stateCap);
                }suffix[j]=mass;counts=next;
            }
            var possible=new ArrayList<Integer>();var logs=new ArrayList<Double>();long states=0;
            for(int r=Math.max(0,cases-reservoir.size);r<suffix[0].length&&r<=cases;r++)if(Double.isFinite(suffix[0][r])){possible.add(r);logs.add(suffix[0][r]+reservoir.polynomial[cases-r]);states=saturating(states,counts[r],stateCap);}
            if(possible.isEmpty())throw new IllegalArgumentException("Conditional allocation has no finite states");
            totals=possible.stream().mapToInt(Integer::intValue).toArray();totalLog=logs.stream().mapToDouble(Double::doubleValue).toArray();this.states=states;
        }
        void score(int[] counts,double[] result){for(int j=0;j<dimensions;j++){double s=-center[j];for(int g=0;g<groups.size();g++)s=Math.fma(counts[g],groups.get(g).genotype[j],s);result[j]+=s;}}
        void observed(double[] result){int[] counts=groups.stream().mapToInt(g->g.observed).toArray();score(counts,result);}
        void sample(SplittableRandom random,double[] result,Budget budget){
            budget.spend((long)groups.size()*(dimensions+2));int q=groups.size()-1,r=totals[choose(totalLog,random)];int[] selected=new int[groups.size()];selected[q]=cases-r;
            for(int g=0;g<q;g++){var group=groups.get(g);int low=Math.max(0,r-suffix[g+1].length+1),high=Math.min(r,group.polynomial.length-1);double[] log=new double[high-low+1];for(int t=low;t<=high;t++)log[t-low]=group.polynomial[t]+suffix[g+1][r-t];int value=low+choose(log,random);selected[g]=value;r-=value;}
            if(r!=0)throw new IllegalArgumentException("Unresolved conditional allocation draw");score(selected,result);
        }
        Table enumerate(Budget budget){
            int n=Math.toIntExact(states),q=groups.size()-1;budget.cells((long)n*(dimensions+1));double[][] scores=new double[n][dimensions];double[] log=new double[n];int out=0;int[] selected=new int[groups.size()];
            for(int total:totals){selected[q]=cases-total;
                if(q==0){score(selected,scores[out]);log[out++]=groups.get(q).polynomial[selected[q]];continue;}
                int[] remaining=new int[q+1],next=new int[q];remaining[0]=total;int level=0;next[0]=Math.max(0,total-suffix[1].length+1);
                while(level>=0){int max=Math.min(remaining[level],groups.get(level).polynomial.length-1);
                    if(next[level]>max){level--;continue;}int value=next[level]++;int remain=remaining[level]-value;
                    if(remain<0||remain>=suffix[level+1].length||!Double.isFinite(suffix[level+1][remain]))continue;
                    selected[level]=value;
                    if(level==q-1){if(remain!=0)continue;budget.spend((long)groups.size()*(dimensions+1));score(selected,scores[out]);double mass=0;for(int j=0;j<groups.size();j++)mass+=groups.get(j).polynomial[selected[j]];log[out++]=mass;}
                    else{remaining[level+1]=remain;level++;next[level]=Math.max(0,remaining[level]-suffix[level+1].length+1);}
                }
            }
            if(out!=n)throw new IllegalArgumentException("Conditional allocation state count disagrees with enumeration");return new Table(scores,log);
        }
    }
    record Table(double[][] scores,double[] logWeights) { }
    static final class Distribution {
        final List<Stratum> strata;final int dimensions;final long states;final double[] observed;
        Distribution(Model model,double[][] genotype,int maximumStates,Budget budget){
            if(genotype.length!=model.observations()||genotype.length==0)throw new IllegalArgumentException("Conditional genotype dimensions differ");dimensions=genotype[0].length;
            if(dimensions<1||dimensions>2000)throw new IllegalArgumentException("Conditional sets support 1..2000 variants");budget.cells((long)genotype.length*dimensions);budget.spend((long)genotype.length*dimensions);
            for(double[] row:genotype){if(row.length!=dimensions)throw new IllegalArgumentException("Conditional genotype rows differ");for(double x:row)if(!Double.isFinite(x)||x<0||x>2)throw new IllegalArgumentException("Conditional genotypes must be fixed finite diploid values in [0,2]");}
            var built=new ArrayList<Stratum>();long states=1,cap=(long)maximumStates+1;observed=new double[dimensions];
            for(int[] rows:model.strata){int k=0;for(int i:rows)k+=(int)model.y[i];if(k==0||k==rows.length)continue;var s=new Stratum(model,rows,genotype,dimensions,cap,budget);built.add(s);s.observed(observed);states=states>cap/s.states?cap:Math.min(cap,states*s.states);}
            strata=List.copyOf(built);this.states=states;
        }
        double[] sample(SplittableRandom random,Budget budget){double[] out=new double[dimensions];for(var s:strata)s.sample(random,out,budget);return out;}
        Table enumerate(Budget budget){
            int n=Math.toIntExact(states);long cells=(long)n*(dimensions+1);for(var s:strata)cells+=s.states*(dimensions+1);budget.cells(cells);
            var tables=new ArrayList<Table>();for(var s:strata)tables.add(s.enumerate(budget));double[][] scores=new double[n][dimensions];double[] log=new double[n];int[] cursor=new int[tables.size()];
            for(int i=0;i<n;i++){budget.spend((long)Math.max(1,tables.size())*dimensions);for(int s=0;s<tables.size();s++){var t=tables.get(s);log[i]+=t.logWeights[cursor[s]];for(int j=0;j<dimensions;j++)scores[i][j]+=t.scores[cursor[s]][j];}
                for(int s=cursor.length-1;s>=0;s--){if(++cursor[s]<tables.get(s).scores.length)break;cursor[s]=0;}}
            return new Table(scores,log);
        }
    }
    static double add(double x,double y){if(x==Double.NEGATIVE_INFINITY)return y;if(y==Double.NEGATIVE_INFINITY)return x;double hi=Math.max(x,y);return hi+Math.log1p(Math.exp(Math.min(x,y)-hi));}
    private static long saturating(long a,long b,long cap){return b>=cap-a?cap:a+b;}
    private static int choose(double[] log,SplittableRandom random){double total=Double.NEGATIVE_INFINITY;for(double v:log)total=add(total,v);if(!Double.isFinite(total))throw new IllegalArgumentException("Invalid conditional sampling weights");double target=Math.log(Math.max(Double.MIN_VALUE,random.nextDouble()))+total,cumulative=Double.NEGATIVE_INFINITY;int last=-1;for(int i=0;i<log.length;i++)if(Double.isFinite(log[i])){last=i;cumulative=add(cumulative,log[i]);if(cumulative>=target)return i;}if(last<0)throw new IllegalArgumentException("Empty conditional draw");return last;}
}
