/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * CellChat RNA communication on prepared, normalized nonnegative expression,
 * explicit interactions and optional caller-supplied permutations. No database
 * selection, differential expression, PPI projection, spatial model or R RNG is
 * inferred. Formula profiles refer to immutable original sources documented in
 * CELLCHAT_DEVELOPMENT.md; they do not identify a paper's historical commit.
 */
public final class CellChatRna {
    private CellChatRna() { }

    public enum Profile {
        CELLCHAT_1_0_0, CELLCHAT_1_1_3_FAST, CELLCHAT_1_1_3_SLOW,
        CELLCHAT_2_1_2_RNA, CELLCHAT_2_2_0_RNA;
        private boolean beforeAverage() {
            return this == CELLCHAT_1_0_0 || this == CELLCHAT_1_1_3_SLOW;
        }
        private boolean inclusiveTail() {
            return this == CELLCHAT_1_0_0 || this == CELLCHAT_1_1_3_FAST
                || this == CELLCHAT_1_1_3_SLOW;
        }
    }

    public enum Average { TRI_MEAN, TRUNCATED_MEAN, MEDIAN, THRESHOLDED_MEAN }

    /** Empty cofactor names mean no cofactor. Names are exact and never repaired. */
    public record Interaction(String id, String ligand, String receptor,
            String agonist, String antagonist, String activatingReceptor,
            String inhibitingReceptor) {
        public Interaction {
            identifier(id); identifier(ligand); identifier(receptor);
            require(agonist != null && antagonist != null && activatingReceptor != null
                && inhibitingReceptor != null, "Use empty strings for absent cofactors");
        }
    }

    public record Parameters(Average average, double trim, double kh,
            double hill, boolean populationSize) {
        public Parameters {
            require(average != null, "An explicit averaging method is required");
            require(Double.isFinite(trim) && trim >= 0
                && trim <= (average==Average.THRESHOLDED_MEAN ? 1 : 0.5),
                "Trim must be finite in [0,0.5], or [0,1] for thresholded mean");
            require(Double.isFinite(kh) && kh > 0 && Double.isFinite(hill) && hill > 0,
                "Hill parameters must be finite and positive");
        }
    }

    /** Arrays use interaction, source group, target group order. Accessors copy. */
    public static final class Result {
        private final double[][][] probabilities, permutationPValues;
        private final double maximum;
        private final long nearBoundaryComparisons;
        private final List<String> warnings;
        private Result(double[][][] probabilities, double[][][] permutationPValues,
                double maximum, long nearBoundaryComparisons, List<String> warnings) {
            this.probabilities=probabilities; this.permutationPValues=permutationPValues;
            this.maximum=maximum; this.nearBoundaryComparisons=nearBoundaryComparisons;
            this.warnings=List.copyOf(warnings);
        }
        public double[][][] probabilities() { return copy(probabilities); }
        /** Empty first dimension means no permutations or p-values were requested. */
        public double[][][] permutationPValues() { return copy(permutationPValues); }
        public double globalExpressionMaximum() { return maximum; }
        public long nearBoundaryComparisons() { return nearBoundaryComparisons; }
        public List<String> warnings() { return warnings; }
    }

    /**
     * Each permutation is a complete zero-based ordering of cell positions.
     * It permutes group labels, not expression columns. An empty permutation
     * array requests observed scores only. Cell order and group indices are
     * supplied by the caller; every group must be represented. Resources are
     * explicit maps from complex/cofactor names to ordered component genes.
     */
    public static Result infer(Profile profile, String[] genes, double[][] expression,
            int[] groups, int groupCount, List<Interaction> interactions,
            Map<String,List<String>> complexes, Map<String,List<String>> cofactors,
            Parameters parameters, int[][] permutations) {
        require(profile != null && parameters != null, "Explicit source profile and parameters required");
        require(genes != null && expression != null && genes.length > 0
            && genes.length == expression.length, "Expression needs one named row per gene");
        require(groups != null && groups.length >= 2 && groupCount >= 2 && groupCount<=groups.length,
            "Prepared RNA component requires at least two cells and two represented groups");
        require(interactions != null && !interactions.isEmpty() && complexes != null
            && cofactors != null && permutations != null, "Explicit resources, interactions and permutation array required");
        require(parameters.average != Average.THRESHOLDED_MEAN
            || profile == Profile.CELLCHAT_2_1_2_RNA || profile == Profile.CELLCHAT_2_2_0_RNA,
            "Thresholded mean is unsupported by the selected historical profile");
        Map<String,Integer> index=new HashMap<>();
        double max=0;
        for (int i=0;i<genes.length;i++) {
            identifier(genes[i]); require(index.put(genes[i],i)==null,"Duplicate gene identifier");
            require(expression[i]!=null && expression[i].length==groups.length,"Ragged expression matrix");
            for (double v:expression[i]) {
                require(Double.isFinite(v) && v>=0,"Expression must be finite, normalized and nonnegative");
                max=Math.max(max,v);
            }
        }
        require(max>0,"All-zero expression would make original global scaling undefined");
        validateGroups(groups,groupCount);
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for (Interaction pair:interactions) require(pair!=null && ids.add(pair.id),"Null or duplicate interaction identifier");
        validateResource(complexes); validateResource(cofactors);
        if (profile==Profile.CELLCHAT_1_0_0 || profile==Profile.CELLCHAT_1_1_3_FAST
                || profile==Profile.CELLCHAT_1_1_3_SLOW) for (Interaction pair:interactions)
            for (String name:List.of(pair.ligand,pair.receptor)) if (!index.containsKey(name))
                require(complexes.containsKey(name) && complexes.get(name).size()>=2,
                    "Historical single-subunit complex dimension dropping is outside this component; use original R");
        for (int[] permutation:permutations) {
            require(permutation!=null && permutation.length==groups.length,"Permutation length mismatch");
            boolean[] seen=new boolean[groups.length];
            for (int j:permutation) {
                require(j>=0 && j<groups.length && !seen[j],"Not a complete cell permutation"); seen[j]=true;
            }
        }
        double[][] data=copy(expression);
        for (double[] row:data) for (int c=0;c<row.length;c++) row[c]/=max;
        double[][][] probabilities=scores(profile,data,index,groups,groupCount,interactions,complexes,cofactors,parameters);
        double[][][] pvalues=permutations.length==0 ? new double[0][][]
            : new double[interactions.size()][groupCount][groupCount];
        long boundary=0;
        for (int[] permutation:permutations) {
            int[] permuted=new int[groups.length];
            for (int c=0;c<groups.length;c++) permuted[c]=groups[permutation[c]];
            double[][][] boot=scores(profile,data,index,permuted,groupCount,interactions,complexes,cofactors,parameters);
            for (int i=0;i<probabilities.length;i++) for (int s=0;s<groupCount;s++) for (int t=0;t<groupCount;t++) {
                double delta=boot[i][s][t]-probabilities[i][s][t];
                if (delta>=0 && profile.inclusiveTail() || delta>0) pvalues[i][s][t]++;
                if (probabilities[i][s][t]>0 && Math.abs(delta)<=32*Math.ulp(Math.max(boot[i][s][t],probabilities[i][s][t]))) boundary++;
            }
        }
        if (permutations.length>0) for (int i=0;i<probabilities.length;i++) for (int s=0;s<groupCount;s++) for (int t=0;t<groupCount;t++) {
            pvalues[i][s][t]=probabilities[i][s][t]==0 ? 1 : pvalues[i][s][t]/permutations.length;
        }
        List<String> warnings=new ArrayList<>();
        warnings.add("Prepared RNA calculations only: no database/interaction selection, normalization, overexpression, PPI projection, spatial inference, sample-consistency filtering, centrality or paper reproduction.");
        warnings.add(permutations.length==0 ? "Observed scores only; no statistical p-values requested."
            : "Permutation replay uses caller-supplied cell orderings; it does not implement R's seed or sample.int generator.");
        if (boundary>0) warnings.add("Exact or near floating-point permutation ties observed: upstream uses an edition-specific exact tail. Native replay preserves that rule without tolerance repair; use original R for authoritative p-values at numerical boundaries.");
        return new Result(probabilities,pvalues,max,boundary,warnings);
    }

    private static double[][][] scores(Profile profile, double[][] data, Map<String,Integer> index,
            int[] groups, int groupCount, List<Interaction> interactions,
            Map<String,List<String>> complexes, Map<String,List<String>> cofactors, Parameters p) {
        double[][] average=aggregate(data,groups,groupCount,p);
        double[][][] result=new double[interactions.size()][groupCount][groupCount];
        int[] counts=new int[groupCount]; for (int g:groups) counts[g]++;
        for (int i=0;i<interactions.size();i++) {
            Interaction pair=interactions.get(i);
            double[][] selected=profile.beforeAverage() ? data : average;
            double[] ligand=ligand(pair.ligand,selected,index,complexes);
            double[] receptor=ligand(pair.receptor,selected,index,complexes);
            double[] ca=cofactor(pair.activatingReceptor,selected,index,cofactors,p,0);
            double[] ci=cofactor(pair.inhibitingReceptor,selected,index,cofactors,p,0);
            for (int c=0;c<receptor.length;c++) receptor[c]=receptor[c]*ca[c]/ci[c];
            double[] ligandPopulation=new double[groupCount], receptorPopulation=new double[groupCount];
            if (profile.beforeAverage()) {
                for (int c=0;c<groups.length;c++) {
                    if (ligand[c]>0) ligandPopulation[groups[c]]++;
                    if (receptor[c]>0) receptorPopulation[groups[c]]++;
                }
                ligand=aggregate(new double[][]{ligand},groups,groupCount,p)[0];
                receptor=aggregate(new double[][]{receptor},groups,groupCount,p)[0];
            } else for (int g=0;g<groupCount;g++) {
                ligandPopulation[g]=counts[g]; receptorPopulation[g]=counts[g];
            }
            double[] agonist=cofactor(pair.agonist,average,index,cofactors,p,1);
            double[] antagonist=cofactor(pair.antagonist,average,index,cofactors,p,-1);
            for (int s=0;s<groupCount;s++) for (int t=0;t<groupCount;t++) {
                double base=hill(ligand[s]*receptor[t],p.kh,p.hill);
                double p2=agonist[s]*agonist[t], p3=antagonist[s]*antagonist[t];
                double p4=p.populationSize ? (ligandPopulation[s]/groups.length)*(receptorPopulation[t]/groups.length) : 1;
                result[i][s][t]=base*p2*p3*p4;
                require(Double.isFinite(result[i][s][t]),"Non-finite score; no result admitted");
            }
        }
        return result;
    }

    private static double[] ligand(String name,double[][] data,Map<String,Integer> index,
            Map<String,List<String>> complexes) {
        Integer row=index.get(name);
        if (row!=null) return data[row].clone();
        List<String> components=complexes.get(name);
        require(components!=null && !components.isEmpty(),"Ligand/receptor is neither a measured gene nor an explicit complex: "+name);
        double[] result=new double[data[0].length];
        for (int c=0;c<result.length;c++) {
            double[] logs=new double[components.size()];
            for (int i=0;i<components.size();i++) {
                Integer j=index.get(components.get(i));
                require(j!=null,"Missing required complex subunit: "+components.get(i));
                logs[i]=Math.log(data[j][c]);
            }
            result[c]=Math.exp(mean(logs));
        }
        return result;
    }

    private static double[] cofactor(String name,double[][] data,Map<String,Integer> index,
            Map<String,List<String>> cofactors,Parameters p,int kind) {
        double[] result=new double[data[0].length]; Arrays.fill(result,1);
        if (name.isEmpty()) return result;
        List<String> components=cofactors.get(name);
        require(components!=null,"Unknown cofactor resource: "+name);
        // Original intersect() removes duplicates and ignores unmeasured cofactor genes.
        for (String component:new LinkedHashSet<>(components)) {
            Integer row=index.get(component); if (row==null) continue;
            for (int c=0;c<result.length;c++) {
                double x=data[row][c];
                double value=kind==0 ? 1+x : kind==1 ? 1+hill(x,p.kh,p.hill)
                    : Math.pow(p.kh,p.hill)/(Math.pow(p.kh,p.hill)+Math.pow(x,p.hill));
                result[c]*=value;
            }
        }
        return result;
    }

    private static double hill(double x,double kh,double n) {
        double xn=Math.pow(x,n); return xn/(Math.pow(kh,n)+xn);
    }

    private static double[][] aggregate(double[][] data,int[] groups,int groupCount,Parameters p) {
        int[] count=new int[groupCount]; for (int g:groups) count[g]++;
        double[][] result=new double[data.length][groupCount];
        for (int i=0;i<data.length;i++) for (int g=0;g<groupCount;g++) {
            double[] values=new double[count[g]]; int at=0;
            for (int c=0;c<groups.length;c++) if (groups[c]==g) values[at++]=data[i][c];
            result[i][g]=average(values,p.average,p.trim);
        }
        return result;
    }

    /** Nonnegative finite values, R type-7 quantiles, source threshold inequality. */
    public static double average(double[] input,Average method,double trim) {
        require(input!=null && input.length>0 && method!=null && Double.isFinite(trim)
            && trim>=0 && trim<=(method==Average.THRESHOLDED_MEAN ? 1 : 0.5),"Invalid averaging request");
        double[] values=input.clone();
        int positive=0;
        for (double x:values) {
            require(Double.isFinite(x) && x>=0,"Averaging requires nonnegative finite values");
            if (x!=0) positive++;
        }
        Arrays.sort(values);
        return switch(method) {
            case TRI_MEAN -> mean(new double[]{quantile(values,0.25),quantile(values,0.5),quantile(values,0.5),quantile(values,0.75)});
            case MEDIAN -> quantile(values,0.5);
            case THRESHOLDED_MEAN -> (double)positive/values.length<trim ? 0 : mean(values);
            case TRUNCATED_MEAN -> {
                int remove=(int)(values.length*trim);
                yield trim==0.5 ? quantile(values,0.5) : mean(Arrays.copyOfRange(values,remove,values.length-remove));
            }
        };
    }

    private static double quantile(double[] x,double probability) {
        double position=(x.length-1)*probability;
        int lo=(int)Math.floor(position), hi=(int)Math.ceil(position);
        if (lo==hi || x[lo]==x[hi]) return x[lo];
        double h=position-lo; return (1-h)*x[lo]+h*x[hi];
    }

    private static double mean(double[] x) {
        double total=0; for (double v:x) total+=v;
        if (!Double.isFinite(total)) return total/x.length;
        double avg=total/x.length, correction=0;
        for (double v:x) correction+=v-avg;
        return avg+correction/x.length;
    }

    private static void validateGroups(int[] groups,int count) {
        boolean[] seen=new boolean[count];
        for (int g:groups) { require(g>=0 && g<count,"Group index out of bounds"); seen[g]=true; }
        for (boolean present:seen) require(present,"Unused group level");
    }
    private static void validateResource(Map<String,List<String>> resource) {
        for (var entry:resource.entrySet()) {
            identifier(entry.getKey()); require(entry.getValue()!=null,"Null component list");
            for (String component:entry.getValue()) identifier(component);
        }
    }
    private static void identifier(String value) {
        require(value!=null && !value.isEmpty() && value.equals(value.strip())
            && value.chars().noneMatch(c->Character.isISOControl(c)),"Invalid exact identifier");
    }
    private static void require(boolean condition,String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private static double[][] copy(double[][] value) {
        return Arrays.stream(value).map(double[]::clone).toArray(double[][]::new);
    }
    private static double[][][] copy(double[][][] value) {
        return Arrays.stream(value).map(CellChatRna::copy).toArray(double[][][]::new);
    }
}
