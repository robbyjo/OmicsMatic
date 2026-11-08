/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.*;

/**
 * CellPhoneDB 5.0.1 numerical kernels on explicitly prepared multidata axes.
 * No gene/database mapping, normalization, DEG fitting or random generator is
 * inferred. Permutations are supplied in their actual original order.
 */
public final class CellPhoneDbStatistics {
    private CellPhoneDbStatistics() { }
    public enum Precision { FLOAT64, FLOAT32 }

    public record Complex(long id, List<Long> proteins) {
        public Complex { proteins=List.copyOf(proteins);require(!proteins.isEmpty(),"Empty supplied complex"); }
    }
    public record Interaction(String id,long first,long second) {
        public Interaction { token(id); }
    }
    public record Microenvironment(String cellType,String environment) {
        public Microenvironment { token(cellType);token(environment); }
    }
    public record Pair(String first,String second) {
        public Pair { token(first);token(second); }
        public String column(String separator) { return first+separator+second; }
    }
    public record Deg(long protein,String cellType) {
        public Deg { token(cellType); }
    }
    public record Limits(int maximumCells,int maximumFeatures,int maximumPairs,
                         int maximumInteractions,int maximumPermutations,long maximumOperations) {
        public Limits {require(maximumCells>0&&maximumFeatures>0&&maximumPairs>0&&maximumInteractions>0&&maximumPermutations>0&&maximumOperations>0,"Positive capacities required");}
        public static Limits defaults(){return new Limits(100000,100000,1000000,1000000,100000,100000000);}
    }
    public record Prepared(List<Long> proteins,List<String> cells,List<String> cellTypes,
                           double[][] expression,List<Complex> complexes,Precision precision) {
        public Prepared(List<Long> proteins,List<String> cells,List<String> cellTypes,double[][] expression,List<Complex> complexes){this(proteins,cells,cellTypes,expression,complexes,Precision.FLOAT64);}
        public Prepared {proteins=List.copyOf(proteins);cells=List.copyOf(cells);cellTypes=List.copyOf(cellTypes);expression=copy(expression);complexes=List.copyOf(complexes);Objects.requireNonNull(precision);}
        @Override public double[][] expression(){return copy(expression);}
    }
    public record Clusters(List<Long> features,List<String> cellTypes,double[][] means,double[][] percents,Precision precision) {
        public Clusters {features=List.copyOf(features);cellTypes=List.copyOf(cellTypes);means=copy(means);percents=copy(percents);}
        @Override public double[][] means(){return copy(means);}
        @Override public double[][] percents(){return copy(percents);}
    }
    public record Significant(double[][] means,List<Double> ranks) {
        public Significant {means=copy(means);ranks=List.copyOf(ranks);}
        @Override public double[][] means(){return copy(means);}
    }
    public record Degs(List<Long> features,List<String> cellTypes,int[][] flags,List<Deg> ignoredMarks) {
        public Degs {features=List.copyOf(features);cellTypes=List.copyOf(cellTypes);flags=copy(flags);ignoredMarks=List.copyOf(ignoredMarks);}
        @Override public int[][] flags(){return copy(flags);}
    }
    public record Result(Clusters clusters,List<Interaction> interactions,List<Pair> pairs,List<String> columns,
                         double[][] means,int[][] eligible,double[][] pvalues,Significant nonStatistical,
                         Significant statistical,Degs degs,int[][] degRelevant,Significant degSignificant,
                         List<double[][]> permutedMeans,List<byte[]> packedGreater,long operations,List<String> warnings) {
        public Result {
            interactions=List.copyOf(interactions);pairs=List.copyOf(pairs);columns=List.copyOf(columns);means=copy(means);eligible=copy(eligible);pvalues=pvalues==null?null:copy(pvalues);degRelevant=degRelevant==null?null:copy(degRelevant);
            var matrices=new ArrayList<double[][]>();for(double[][] value:permutedMeans)matrices.add(copy(value));permutedMeans=List.copyOf(matrices);var packed=new ArrayList<byte[]>();for(byte[] value:packedGreater)packed.add(value.clone());packedGreater=List.copyOf(packed);warnings=List.copyOf(warnings);
        }
        @Override public double[][] means(){return copy(means);}
        @Override public int[][] eligible(){return copy(eligible);}
        @Override public double[][] pvalues(){return pvalues==null?null:copy(pvalues);}
        @Override public int[][] degRelevant(){return degRelevant==null?null:copy(degRelevant);}
        @Override public List<double[][]> permutedMeans(){var result=new ArrayList<double[][]>();for(double[][] value:permutedMeans)result.add(copy(value));return List.copyOf(result);}
        @Override public List<byte[]> packedGreater(){var result=new ArrayList<byte[]>();for(byte[] value:packedGreater)result.add(value.clone());return List.copyOf(result);}
    }

    /** Run all supplied prepared kernels; null DEG marks omits method 3. */
    public static Result analyze(Prepared input,List<Interaction> interactions,List<Microenvironment> microenvironments,
                                 double threshold,String separator,List<List<String>> permutations,Double pvalueCutoff,
                                 List<Deg> degMarks,Limits limits) {
        Objects.requireNonNull(input);Objects.requireNonNull(limits);require(Double.isFinite(threshold)&&threshold>=0&&threshold<=1,"Finite threshold in [0,1] required");token(separator);
        if(pvalueCutoff!=null)require(Double.isFinite(pvalueCutoff)&&pvalueCutoff>=0&&pvalueCutoff<=1,"Finite P cutoff in [0,1] required");
        require(degMarks==null||!input.complexes.isEmpty(),"Original CellPhoneDB5.0.1 build_degs_matrix fails on empty complex composition under bound pandas2.2.3; this unsupported DEG component admits no result");
        require(!input.cells.isEmpty()&&!input.proteins.isEmpty()&&input.cells.size()<=limits.maximumCells&&input.proteins.size()+input.complexes.size()<=limits.maximumFeatures,"Complete prepared matrix capacity exceeded or empty");
        require(input.cells.size()==input.cellTypes.size()&&input.expression.length==input.proteins.size(),"Prepared axes are ragged");unique(input.cells);var ids=new HashSet<Long>();for(long id:input.proteins)require(ids.add(id),"Duplicate supplied protein multidata ID");
        for(String label:input.cellTypes)token(label);for(double[] row:input.expression){require(row.length==input.cells.size(),"Ragged prepared expression");for(double value:row){require(Double.isFinite(value)&&value>=0,"Prepared input expression must be finite and nonnegative; no repair");if(input.precision==Precision.FLOAT32)require((double)(float)value==value,"FLOAT32 prepared values must already be exactly represented source float32; no silent input quantization");}}
        var proteins=new HashSet<>(input.proteins);for(Complex complex:input.complexes){require(ids.add(complex.id),"Complex ID collides with another supplied feature");for(long protein:complex.proteins)require(proteins.contains(protein),"Incomplete supplied complex; gene/database filtering is a separate stage");}
        interactions=List.copyOf(interactions);require(!interactions.isEmpty()&&interactions.size()<=limits.maximumInteractions,"Complete interaction capacity exceeded or empty");var seen=new HashSet<String>();for(Interaction interaction:interactions){require(seen.add(interaction.id),"Duplicate interaction identity");require(ids.contains(interaction.first)&&ids.contains(interaction.second),"Interaction endpoint absent from prepared axes");}
        require(permutations.size()<=limits.maximumPermutations,"Permutation capacity exceeded");long clusterCells=Math.multiplyExact((long)ids.size(),new HashSet<>(input.cellTypes).size());require(clusterCells<=limits.maximumOperations/2,"Complete cluster matrix capacity exceeded before allocation");var meter=new Meter(limits.maximumOperations);Clusters clusters=clusters(input,input.cellTypes,meter);List<Pair> pairs=pairs(clusters.cellTypes,microenvironments,limits.maximumPairs,meter);require(!pairs.isEmpty(),"Original empty pair family is outside this prepared kernel profile");long resultCells=Math.multiplyExact((long)interactions.size(),pairs.size());require(resultCells<=limits.maximumOperations/(3L+permutations.size()),"Complete retained result/permutation capacity exceeded before allocation");var columns=pairs.stream().map(pair->pair.column(separator)).toList();double[][] means=means(clusters,interactions,pairs,meter);int[][] eligible=eligible(clusters,interactions,pairs,threshold,meter);double[][] mask=doubles(eligible);Significant nonStatistical=significant(means,mask,null,meter);var shuffled=new ArrayList<double[][]>();var packed=new ArrayList<byte[]>();long[][] hits=new long[interactions.size()][pairs.size()];var counts=frequencies(input.cellTypes);
        for(List<String> labels:permutations){require(frequencies(labels).equals(counts),"Supplied permutation must preserve the complete cell-type multiset and size");var summary=clusters(input,labels,meter);double[][] values=means(summary,interactions,pairs,meter);shuffled.add(values);long size=Math.multiplyExact((long)interactions.size(),pairs.size());require(size<=Integer.MAX_VALUE-7,"Packed comparison capacity exceeded");byte[] bits=new byte[(int)((size+7)/8)];long index=0;
            for(int i=0;i<values.length;i++)for(int j=0;j<pairs.size();j++,index++){meter.spend(1);if(values[i][j]>means[i][j]){hits[i][j]++;bits[(int)(index/8)]|=(byte)(1<<(7-index%8));}}packed.add(bits);
        }
        double[][] pvalues=null;Significant statistical=null;if(!permutations.isEmpty()){pvalues=new double[interactions.size()][pairs.size()];for(int i=0;i<pvalues.length;i++)for(int j=0;j<pairs.size();j++){meter.spend(1);pvalues[i][j]=means[i][j]==0||eligible[i][j]==0?1:hits[i][j]/(double)permutations.size();}statistical=significant(means,pvalues,pvalueCutoff,meter);}
        Degs degs=null;int[][] relevant=null;Significant degSignificant=null;if(degMarks!=null){degs=degs(input,clusters.cellTypes,degMarks,meter);var row=index(degs.features);var col=index(degs.cellTypes);relevant=new int[interactions.size()][pairs.size()];for(int i=0;i<interactions.size();i++)for(int j=0;j<pairs.size();j++){meter.spend(1);var interaction=interactions.get(i);var pair=pairs.get(j);relevant[i][j]=eligible[i][j]&((degs.flags[row.get(interaction.first)][col.get(pair.first)]+degs.flags[row.get(interaction.second)][col.get(pair.second)])>0?1:0);}degSignificant=significant(means,doubles(relevant),null,meter);}
        var warnings=new ArrayList<String>();warnings.add("Prepared multidata numerical component only: no gene/database mapping, expression normalization, DEG fitting, original random generator, full CellPhoneDB workflow or biological communication claim. Original cell/feature axes and supplied permutation labels remain explicit.");warnings.add("Expression fractions use strict >threshold; permutation tails use strict >observed with hits/iterations and no plus-one correction. This differs from inclusive wording in some papers/documentation. Complex means/fractions are minima across subunits; a complex DEG flag is the maximum across mapped subunits.");
        if(pvalueCutoff!=null&&pvalueCutoff==0)warnings.add("Original P-cutoff zero is falsy: get_significant_means uses score==0 masking instead of score>0. This literal source behavior is retained; do not interpret it as an ordinary P<=0 filter.");
        if(new HashSet<>(columns).size()!=columns.size())warnings.add("Encoded cluster-pair column labels collide. Distinct pair identities and ordered columns remain retained; never merge them by header string.");
        if(nonfinite(means)||shuffled.stream().anyMatch(CellPhoneDbStatistics::nonfinite))warnings.add("Original floating arithmetic produced Inf/NaN from finite prepared inputs. Raw states are retained; this component does not admit a downstream statistical analysis or replace overflow with zero.");
        return new Result(clusters,interactions,pairs,columns,means,eligible,pvalues,nonStatistical,statistical,degs,relevant,degSignificant,shuffled,packed,meter.used,warnings);
    }

    private static Clusters clusters(Prepared input,List<String> labels,Meter meter) {
        var sorted=new TreeSet<String>(CellPhoneDbStatistics::compareText);sorted.addAll(labels);var types=List.copyOf(sorted);var positions=index(types);int n=types.size(),rows=input.proteins.size();long[] sizes=new long[n];for(String label:labels)sizes[positions.get(label)]++;double[][] means=new double[rows+input.complexes.size()][n],percents=new double[means.length][n];var features=new ArrayList<>(input.proteins);
        for(int i=0;i<rows;i++){long[] positive=new long[n];for(int j=0;j<labels.size();j++){meter.spend(1);int group=positions.get(labels.get(j));double value=input.expression[i][j];means[i][group]=rounded(means[i][group]+value,input.precision);if(value>0)positive[group]++;}for(int j=0;j<n;j++){meter.spend(1);means[i][j]=rounded(means[i][j]/sizes[j],input.precision);percents[i][j]=positive[j]/(double)sizes[j];}}
        var simple=index(input.proteins);for(int i=0;i<input.complexes.size();i++){Complex complex=input.complexes.get(i);features.add(complex.id);for(int j=0;j<n;j++){double m=Double.POSITIVE_INFINITY,p=Double.POSITIVE_INFINITY;for(long protein:complex.proteins){meter.spend(1);m=Math.min(m,means[simple.get(protein)][j]);p=Math.min(p,percents[simple.get(protein)][j]);}means[rows+i][j]=m;percents[rows+i][j]=p;}}
        return new Clusters(features,types,means,percents,input.precision);
    }
    private static List<Pair> pairs(List<String> types,List<Microenvironment> environments,int maximum,Meter meter) {
        var groups=new LinkedHashMap<String,List<String>>();if(environments.isEmpty())groups.put("",types);else for(Microenvironment row:environments){require(types.contains(row.cellType),"Microenvironment names an unknown supplied cell type");groups.computeIfAbsent(row.environment,unused->new ArrayList<>()).add(row.cellType);}
        var result=new LinkedHashSet<Pair>();for(var group:groups.values())for(String first:group)for(String second:group){meter.spend(1);result.add(new Pair(first,second));require(result.size()<=maximum,"Cluster-pair capacity exceeded");}return List.copyOf(result);
    }
    private static double[][] means(Clusters summary,List<Interaction> interactions,List<Pair> pairs,Meter meter) {
        var row=index(summary.features);var col=index(summary.cellTypes);double[][] values=new double[interactions.size()][pairs.size()];for(int i=0;i<values.length;i++)for(int j=0;j<pairs.size();j++){meter.spend(1);var interaction=interactions.get(i);var pair=pairs.get(j);double x=summary.means[row.get(interaction.first)][col.get(pair.first)],y=summary.means[row.get(interaction.second)][col.get(pair.second)];values[i][j]=rounded((x>0?1.0:0.0)*(y>0?1.0:0.0)*rounded(x+y,summary.precision)/2,summary.precision);}return values;
    }
    private static int[][] eligible(Clusters summary,List<Interaction> interactions,List<Pair> pairs,double threshold,Meter meter) {
        var row=index(summary.features);var col=index(summary.cellTypes);int[][] values=new int[interactions.size()][pairs.size()];for(int i=0;i<values.length;i++)for(int j=0;j<pairs.size();j++){meter.spend(1);var interaction=interactions.get(i);var pair=pairs.get(j);values[i][j]=summary.percents[row.get(interaction.first)][col.get(pair.first)]>threshold&&summary.percents[row.get(interaction.second)][col.get(pair.second)]>threshold?1:0;}return values;
    }
    private static Degs degs(Prepared input,List<String> types,List<Deg> marks,Meter meter) {
        var features=new ArrayList<>(input.proteins);var ordered=new ArrayList<>(input.complexes);ordered.sort(Comparator.comparingLong(Complex::id));for(Complex complex:ordered)features.add(complex.id);int[][] flags=new int[features.size()][types.size()];var row=index(input.proteins);var col=index(types);var ignored=new ArrayList<Deg>();for(Deg mark:marks){meter.spend(1);if(!row.containsKey(mark.protein)||!col.containsKey(mark.cellType))ignored.add(mark);else flags[row.get(mark.protein)][col.get(mark.cellType)]=1;}
        for(int i=0;i<ordered.size();i++)for(int j=0;j<types.size();j++)for(long protein:ordered.get(i).proteins){meter.spend(1);flags[input.proteins.size()+i][j]|=flags[row.get(protein)][j];}return new Degs(features,types,flags,ignored);
    }
    private static Significant significant(double[][] means,double[][] scores,Double cutoff,Meter meter) {
        double[][] values=copy(means);var ranks=new ArrayList<Double>();boolean positive=cutoff!=null&&cutoff!=0;for(int i=0;i<values.length;i++){int retained=0;for(int j=0;j<values[i].length;j++){meter.spend(1);if(positive?scores[i][j]>cutoff:scores[i][j]==0)values[i][j]=Double.NaN;if(!Double.isNaN(values[i][j]))retained++;}ranks.add(Math.rint(retained/(double)values[i].length*1000)/1000);}return new Significant(values,ranks);
    }
    private static final class Meter {
        final long maximum;long used;Meter(long maximum){this.maximum=maximum;}
        void spend(long n){require(!Thread.currentThread().isInterrupted()&&n>=0&&n<=maximum-used,"Native operation capacity/cancellation reached; no partial result");used+=n;}
    }
    private static <T> Map<T,Integer> index(List<T> values){var result=new HashMap<T,Integer>();for(int i=0;i<values.size();i++)result.put(values.get(i),i);return result;}
    private static Map<String,Integer> frequencies(List<String> values){var result=new HashMap<String,Integer>();for(String value:values){token(value);result.merge(value,1,Math::addExact);}return result;}
    private static int compareText(String a,String b){var first=a.codePoints().iterator();var second=b.codePoints().iterator();while(first.hasNext()&&second.hasNext()){int x=first.nextInt(),y=second.nextInt();if(x!=y)return Integer.compare(x,y);}return Integer.compare(a.codePointCount(0,a.length()),b.codePointCount(0,b.length()));}
    private static void unique(List<String> values){var seen=new HashSet<String>();for(String value:values){token(value);require(seen.add(value),"Duplicate original cell identity");}}
    private static void token(String value){Objects.requireNonNull(value);require(!value.isEmpty()&&value.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Empty/control/unpaired-surrogate data label; no normalization");}
    private static boolean nonfinite(double[][] values){for(double[] row:values)for(double value:row)if(!Double.isFinite(value))return true;return false;}
    private static double rounded(double value,Precision precision){return precision==Precision.FLOAT32?(double)(float)value:value;}
    private static double[][] copy(double[][] values){Objects.requireNonNull(values);double[][] result=new double[values.length][];for(int i=0;i<values.length;i++)result[i]=Objects.requireNonNull(values[i]).clone();return result;}
    private static int[][] copy(int[][] values){Objects.requireNonNull(values);int[][] result=new int[values.length][];for(int i=0;i<values.length;i++)result[i]=Objects.requireNonNull(values[i]).clone();return result;}
    private static double[][] doubles(int[][] values){double[][] result=new double[values.length][];for(int i=0;i<values.length;i++){result[i]=new double[values[i].length];for(int j=0;j<values[i].length;j++)result[i][j]=values[i][j];}return result;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
