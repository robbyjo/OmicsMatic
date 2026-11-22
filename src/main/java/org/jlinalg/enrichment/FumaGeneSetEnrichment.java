/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Independent Java implementation of the FUMA GENE2FUNC gene-set test.
 * Uses explicitly supplied annotation and GMT resources, one correction family
 * per GMT, and the fixed 0.05 first-stage level for two-stage BH/BKY.
 * This is not SNP2GENE, MAGMA, tissue-expression, DEG or a hosted FUMA client.
 */
public final class FumaGeneSetEnrichment {
    private FumaGeneSetEnrichment() { }

    /** The ten correction choices exposed by the inspected FUMA gene-set form. */
    public enum Adjustment {
        BONFERRONI("bonferroni"), SIDAK("sidak"), HOLM_SIDAK("holm-sidak"),
        HOLM("holm"), SIMES_HOCHBERG("simes-hochberg"), HOMMEL("hommel"),
        BH("fdr_bh"), BY("fdr_by"), TWO_STAGE_BH("fdr_tsbh"), TWO_STAGE_BKY("fdr_tsbky");
        private final String id;
        Adjustment(String id) { this.id=id; }
        /** FUMA parameter spelling. */
        public String id() { return id; }
        /** Parse an exact FUMA parameter spelling. */
        public static Adjustment parse(String value) {
            for (Adjustment method:values()) if (method.id.equals(value)) return method;
            throw new IllegalArgumentException("Unknown FUMA adjustment: "+value);
        }
    }
    /** Duplicate GMT entries are either rejected or counted as in the original backend. */
    public enum Duplicates { ERROR, ORIGINAL_COUNT }

    /** One annotation row; order and repeated Entrez-to-symbol mappings are retained. */
    public record GeneRow(String ensembl, String entrez, String symbol, String biotype,
            String chromosome, long start, long end) {
        public GeneRow {
            token(ensembl);token(entrez);token(symbol);token(biotype);token(chromosome);
            if (!entrez.matches("[1-9][0-9]*") || start<0 || end<start)
                throw new IllegalArgumentException("Annotation requires a positive Entrez ID and ordered nonnegative coordinates");
        }
    }
    /** One GMT row, including repeated member IDs and the original description/link. */
    public record Term(String name, String link, List<String> members) {
        public Term {
            field(name);if (link==null || link.indexOf('\t')>=0 || link.indexOf('\n')>=0 || link.indexOf('\r')>=0)
                throw new IllegalArgumentException("Invalid GMT link");
            members=List.copyOf(members);members.forEach(FumaGeneSetEnrichment::token);
        }
    }
    /** A single GMT file is one correction family, including valid zero-hit rows. */
    public record Family(String category, List<Term> terms) {
        public Family { field(category);terms=List.copyOf(terms); }
    }
    /** All input IDs are reported, including IDs dropped by global namespace choice. */
    public record IdMapping(String input, String upperInput, String namespace, List<String> entrez) {
        public IdMapping { entrez=List.copyOf(entrez); }
    }
    /** Inspect filtering and ID interpretation even when too few genes remain for a test. */
    public record Preparation(List<String> universe, List<String> selected, List<GeneRow> annotation,
            List<IdMapping> queryMapping, List<IdMapping> backgroundMapping, String queryNamespace,
            String backgroundNamespace, int mhcRemovedRows) {
        public Preparation {
            universe=List.copyOf(universe);selected=List.copyOf(selected);annotation=List.copyOf(annotation);
            queryMapping=List.copyOf(queryMapping);backgroundMapping=List.copyOf(backgroundMapping);
        }
    }
    /** Prepared unique Entrez lists and the remaining annotation in original row order. */
    public record Prepared(List<String> universe, List<String> selected, List<GeneRow> annotation,
            List<IdMapping> queryMapping, List<IdMapping> backgroundMapping, String queryNamespace,
            String backgroundNamespace, int mhcRemovedRows) {
        public Prepared {
            universe=List.copyOf(universe);selected=List.copyOf(selected);annotation=List.copyOf(annotation);
            queryMapping=List.copyOf(queryMapping);backgroundMapping=List.copyOf(backgroundMapping);
            if (universe.size()<2 || new HashSet<>(universe).size()!=universe.size()
                    || selected.size()<2 || new HashSet<>(selected).size()!=selected.size()
                    || !new HashSet<>(universe).containsAll(selected))
                throw new IllegalArgumentException("At least two distinct selected Entrez IDs must remain in the unique background");
        }
    }
    /** One tested hypothesis. A retained row satisfies both final display filters. */
    public record Result(String category, int row, String name, int universeSize, int selectedSize,
            int setSize, int overlap, int duplicateMembers, double pValue, double logPValue,
            double adjustedPValue, double logAdjustedPValue, List<String> genes, String link,
            boolean retained) {
        public Result { genes=List.copyOf(genes); }
    }

    /**
     * Apply the original global namespace priority: symbol, Ensembl, Entrez, then
     * unchanged IDs. Exactly one of background IDs or selected biotypes is supplied.
     * A biotype value of {@code all} retains all rows. MHC exclusion uses the first
     * MOG and COL11A2 start coordinates in the already biotype-filtered annotation.
     * Missing/NA annotation IDs must be cleaned explicitly before this API is used.
     */
    public static Prepared prepare(List<GeneRow> annotation, List<String> query,
            List<String> background, Set<String> biotypes, boolean excludeMhc) {
        var inspection=inspect(annotation,query,background,biotypes,excludeMhc);
        return new Prepared(inspection.universe(),inspection.selected(),inspection.annotation(),
                inspection.queryMapping(),inspection.backgroundMapping(),inspection.queryNamespace(),
                inspection.backgroundNamespace(),inspection.mhcRemovedRows());
    }
    /** Apply the same preparation as {@link #prepare}, retaining the audit before statistical eligibility.
     * @param annotation supplied gene annotation
     * @param query supplied query IDs
     * @param background supplied background IDs, or null when biotypes are selected
     * @param biotypes selected biotypes, or null when background IDs are supplied
     * @param excludeMhc whether to remove the explicitly anchored MHC interval
     * @return inspected inputs; zero or one selected gene is permitted and must not be tested
     */
    public static Preparation inspect(List<GeneRow> annotation, List<String> query,
            List<String> background, Set<String> biotypes, boolean excludeMhc) {
        if (annotation==null || annotation.isEmpty() || query==null || query.isEmpty()
                || (background==null)==(biotypes==null)
                || (background!=null && background.isEmpty()) || (biotypes!=null && biotypes.isEmpty()))
            throw new IllegalArgumentException("Supply annotation, query and exactly one nonempty background specification");
        List<GeneRow> rows=new ArrayList<>();
        for (GeneRow row:annotation) if (biotypes==null || biotypes.contains("all") || biotypes.contains(row.biotype())) rows.add(row);
        List<String> selectedBackground=biotypes==null?background:rows.stream().map(GeneRow::entrez).toList();
        int removed=0;
        if (excludeMhc) {
            long lo=anchor(rows,"MOG"),hi=anchor(rows,"COL11A2");
            if (hi<=lo) throw new IllegalArgumentException("MHC anchors require COL11A2 start > MOG start");
            int before=rows.size();rows.removeIf(r->r.chromosome().equals("6") && r.end()>lo && r.start()<hi);removed=before-rows.size();
        }
        String qNamespace=namespace(query,rows),bNamespace=biotypes==null?namespace(selectedBackground,rows):"biotype-selection";
        List<IdMapping> qAudit=map(query,rows,qNamespace),bAudit=biotypes==null?map(selectedBackground,rows,bNamespace):List.of();
        Set<String> valid=new HashSet<>();for (GeneRow r:rows) valid.add(r.entrez());
        Set<String> universe=new TreeSet<>();
        if (biotypes!=null) universe.addAll(selectedBackground);else for (IdMapping m:bAudit) universe.addAll(m.entrez());
        universe.retainAll(valid);Set<String> chosen=new TreeSet<>();for (IdMapping m:qAudit) chosen.addAll(m.entrez());chosen.retainAll(universe);
        return new Preparation(new ArrayList<>(universe),new ArrayList<>(chosen),rows,qAudit,bAudit,qNamespace,bNamespace,removed);
    }
    private static long anchor(List<GeneRow> rows,String symbol) {
        for (GeneRow row:rows) if (row.symbol().equals(symbol)) return row.start();
        throw new IllegalArgumentException("MHC exclusion requires annotation anchor "+symbol);
    }
    private static String namespace(List<String> input,List<GeneRow> rows) {
        Set<String> ids=new HashSet<>();for (String id:input) {token(id);ids.add(id.toUpperCase(Locale.ROOT));}
        for (String kind:List.of("symbol","ensembl","entrez")) for (GeneRow row:rows) if (ids.contains(id(row,kind))) return kind;
        return "unmatched";
    }
    private static String id(GeneRow row,String namespace) {
        return switch(namespace) {case "symbol"->row.symbol();case "ensembl"->row.ensembl();default->row.entrez();};
    }
    private static List<IdMapping> map(List<String> input,List<GeneRow> rows,String namespace) {
        Map<String,List<String>> index=new LinkedHashMap<>();
        for (GeneRow row:rows) index.computeIfAbsent(id(row,namespace),k->new ArrayList<>()).add(row.entrez());
        List<IdMapping> result=new ArrayList<>();
        for (String raw:input) {
            token(raw);String upper=raw.toUpperCase(Locale.ROOT);
            List<String> mapped=namespace.equals("unmatched")?List.of(upper):index.getOrDefault(upper,List.of());
            result.add(new IdMapping(raw,upper,namespace,new ArrayList<>(new LinkedHashSet<>(mapped))));
        }
        return List.copyOf(result);
    }

    /**
     * Test all valid GMT rows and correct each file before strict adjusted-P and
     * minimum-overlap filtering. Empty member lists are ignored, as short GMT rows.
     * Equal raw probabilities keep row order (the original NumPy quicksort does
     * not guarantee tie order). Every tested row is returned, including hidden rows.
     */
    public static List<Result> test(Prepared data, List<Family> families, Adjustment correction,
            double adjustedCutoff, int minimumOverlap, Duplicates duplicates, long maximumHommelOperations) {
        if (data==null || families==null || families.isEmpty() || correction==null || duplicates==null
                || !Double.isFinite(adjustedCutoff) || adjustedCutoff<0 || adjustedCutoff>1 || minimumOverlap<0)
            throw new IllegalArgumentException("Invalid FUMA gene-set protocol");
        Set<String> universe=new HashSet<>(data.universe()),selected=new HashSet<>(data.selected());
        List<GeneRow> selectedAnnotation=data.annotation().stream().filter(g->selected.contains(g.entrez())).toList();
        int N=universe.size(),m=selected.size();List<Result> output=new ArrayList<>();
        for (Family family:families) {
            List<Result> tested=new ArrayList<>();int rowNumber=0;
            for (Term term:family.terms()) {
                rowNumber++;if (term.members().isEmpty()) continue;
                Set<String> unique=new HashSet<>();int K=0;
                for (String gene:term.members()) if (universe.contains(gene)) { K++;unique.add(gene); }
                int repeated=K-unique.size();
                if (repeated>0 && duplicates==Duplicates.ERROR) throw new IllegalArgumentException("Repeated background member in "+family.category()+"/"+term.name());
                if (K>N) throw new IllegalArgumentException("Original-count GMT size exceeds background in "+family.category()+"/"+term.name());
                int k=0;for (String gene:selected) if (unique.contains(gene)) k++;
                double logP=k==0?0:jdistlib.HyperGeometric.cumulative(k-1.0,K,N-K,m,false,true);
                if (!Double.isFinite(logP) || logP>0) throw new ArithmeticException("Invalid hypergeometric probability in "+family.category()+"/"+term.name());
                List<String> symbols=new ArrayList<>();
                for (GeneRow gene:selectedAnnotation) if (unique.contains(gene.entrez())) symbols.add(gene.symbol());
                tested.add(new Result(family.category(),rowNumber,term.name(),N,m,K,k,repeated,Math.exp(logP),logP,1,0,symbols,term.members().size()>1?term.link():"",false));
            }
            if (tested.isEmpty()) throw new IllegalArgumentException("GMT family contains no testable rows: "+family.category());
            double[] adjusted=adjustLog(tested.stream().mapToDouble(Result::logPValue).toArray(),correction,maximumHommelOperations);
            List<Result> corrected=new ArrayList<>();
            for (int i=0;i<tested.size();i++) {
                Result r=tested.get(i);double value=Math.exp(adjusted[i]);
                boolean retained=adjustedCutoff>0 && adjusted[i]<Math.log(adjustedCutoff) && r.overlap()>=minimumOverlap;
                corrected.add(new Result(r.category(),r.row(),r.name(),N,m,r.setSize(),r.overlap(),r.duplicateMembers(),r.pValue(),r.logPValue(),value,adjusted[i],r.genes(),r.link(),retained));
            }
            corrected.sort(Comparator.comparingDouble(Result::logPValue).thenComparingInt(Result::row));output.addAll(corrected);
        }
        return List.copyOf(output);
    }

    /**
     * Correct natural-log probabilities in input order. Two-stage methods use
     * alpha=0.05 independently of the later display cutoff, including the original
     * zero/all first-stage rejection branches. Log space preserves underflowed P.
     * The explicit Hommel work budget bounds its quadratic loop.
     */
    public static double[] adjustLog(double[] logP, Adjustment method, long maximumHommelOperations) {
        if (logP==null || logP.length==0 || method==null || maximumHommelOperations<1)
            throw new IllegalArgumentException("A nonempty correction family and positive work budget are required");
        for (double p:logP) if (Double.isNaN(p) || p>0) throw new IllegalArgumentException("Log P must be <= 0");
        int n=logP.length;Integer[] order=new Integer[n];for (int i=0;i<n;i++) order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble(i->logP[i]));double[] sorted=new double[n];for (int i=0;i<n;i++) sorted[i]=logP[order[i]];
        double[] a=sorted.clone();double logN=Math.log(n);
        switch(method) {
            case BONFERRONI -> {for (int i=0;i<n;i++) a[i]=sorted[i]+logN;}
            case SIDAK -> {for (int i=0;i<n;i++) a[i]=sidak(sorted[i],n);}
            case HOLM, HOLM_SIDAK -> {
                double previous=Double.NEGATIVE_INFINITY;
                for (int i=0;i<n;i++) {double raw=method==Adjustment.HOLM?sorted[i]+Math.log(n-i):sidak(sorted[i],n-i);previous=Math.max(previous,raw);a[i]=previous;}
            }
            case SIMES_HOCHBERG -> {
                double next=Double.POSITIVE_INFINITY;
                for (int i=n-1;i>=0;i--) {next=Math.min(next,sorted[i]+Math.log(n-i));a[i]=next;}
            }
            case HOMMEL -> {
                if ((long)n*n>maximumHommelOperations) throw new IllegalArgumentException("Hommel work budget exceeded; increase maximumHommelOperations or choose another declared correction");
                for (int size=n;size>=2;size--) {
                    double local=Double.POSITIVE_INFINITY;
                    for (int j=0;j<size;j++) local=Math.min(local,Math.log(size)+sorted[n-size+j]-Math.log(j+1.0));
                    for (int j=n-size;j<n;j++) a[j]=Math.max(a[j],local);
                    for (int j=0;j<n-size;j++) a[j]=Math.max(a[j],Math.min(Math.log(size)+sorted[j],local));
                }
            }
            case BH, BY, TWO_STAGE_BH, TWO_STAGE_BKY -> {
                double factor=1;
                if (method==Adjustment.BY) {factor=0;for (int i=1;i<=n;i++) factor+=1.0/i;}
                double next=0;
                for (int i=n-1;i>=0;i--) {next=Math.min(next,sorted[i]+logN+Math.log(factor)-Math.log(i+1.0));a[i]=next;}
                if (method==Adjustment.TWO_STAGE_BH || method==Adjustment.TWO_STAGE_BKY) {
                    double inflation=method==Adjustment.TWO_STAGE_BKY?1.05:1;
                    double alphaPrime=.05/inflation;int rejected=0;
                    for (int i=0;i<n;i++) if (sorted[i]<=Math.log(((i+1.0)/n)*alphaPrime)) rejected=i+1;
                    double scale=inflation;
                    if (rejected>0 && rejected<n) scale*=((double)(n-rejected)/n);
                    for (int i=0;i<n;i++) a[i]+=Math.log(scale);
                }
            }
        }
        double[] result=new double[n];for (int i=0;i<n;i++) result[order[i]]=Math.min(0,a[i]);return result;
    }
    private static double sidak(double logP,int count) {
        if (logP==0) return 0;
        double p=Math.exp(logP);
        if (p==0) return logP+Math.log(count);
        return Math.log(-Math.expm1(count*Math.log1p(-p)));
    }
    private static void token(String value) {
        if (value==null || value.isBlank() || value.chars().anyMatch(c->Character.isWhitespace(c) || c==':'))
            throw new IllegalArgumentException("IDs, symbols and categories must be nonempty tokens without whitespace or colon");
    }
    private static void field(String value) {
        if (value==null || value.isBlank() || value.indexOf('\t')>=0 || value.indexOf('\r')>=0 || value.indexOf('\n')>=0)
            throw new IllegalArgumentException("A nonempty single-line field is required");
    }
}
