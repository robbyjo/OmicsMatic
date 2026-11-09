/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import jdistlib.ChiSquare;

/**
 * Native MR-MEGA cohort ingestion, allele alignment and complete regression flow.
 * Original float32 boundaries and defined source ordering/GC behavior are explicit.
 * Malformed numeric values and source-undefined operations are refused, not repaired.
 * This development API requires reference validation before catalog availability.
 */
public final class MrMegaWorkflow {
    private MrMegaWorkflow() { }
    public enum Trait { QUANTITATIVE, BINARY }
    public enum Column {
        MARKERNAME, EA, NEA, EAF, BETA, SE, OR, OR_95L, OR_95U, CHROMOSOME, POSITION, N
    }
    public record Filter(String column, String operator, double value) {
        public Filter {
            require(column != null && !column.isBlank(), "Filter column required");
            require(List.of("==", "!=", ">=", "<=", ">", "<").contains(operator), "Unknown numeric filter operator");
            require(Double.isFinite(value), "Filter value must be finite");
        }
        boolean accepts(double x) {
            return switch (operator) {
                case "==" -> x == value; case "!=" -> x != value;
                case ">=" -> x >= value; case "<=" -> x <= value;
                case ">" -> x > value; case "<" -> x < value;
                default -> throw new IllegalArgumentException("Unknown filter");
            };
        }
    }
    public record Settings(Trait trait, int axes, boolean precalculated, boolean noAlleles,
            boolean inputGc, boolean outputGc, boolean standardNames,
            Map<Column, List<String>> aliases, List<Filter> filters) {
        public Settings {
            require(trait != null && axes >= 0 && axes <= 509, "Explicit trait and ancestry-axis count 0..509 required");
            require(precalculated || axes > 0, "Inferred ancestry requires at least one explicitly requested axis");
            require(aliases != null && filters != null, "Explicit aliases/filters required, including empty collections");
            var copied = new EnumMap<Column, List<String>>(Column.class);
            for (var entry : aliases.entrySet()) {
                require(entry.getKey() != null && entry.getValue() != null, "Invalid column alias definition");
                require(entry.getValue().stream().allMatch(s -> s != null && !s.isBlank()), "Column aliases must be nonblank");
                copied.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            aliases = Map.copyOf(copied); filters = List.copyOf(filters);
        }
    }
    public record Cohort(Path path, double[] providedAxes, double lambda, int chiSquareRows) {
        public Cohort { providedAxes = providedAxes.clone(); }
        @Override public double[] providedAxes() { return providedAxes.clone(); }
    }
    public record RowDisposition(int cohort, long line, String marker, String status, String detail) { }
    public record Variant(String id, int chromosome, int position, String effectAllele,
            String otherAllele, double[] frequencies, int[] sampleSizes, double[] effects,
            double[] standardErrors) {
        public Variant {
            frequencies = frequencies.clone(); sampleSizes = sampleSizes.clone();
            effects = effects.clone(); standardErrors = standardErrors.clone();
        }
        @Override public double[] frequencies() { return frequencies.clone(); }
        @Override public int[] sampleSizes() { return sampleSizes.clone(); }
        @Override public double[] effects() { return effects.clone(); }
        @Override public double[] standardErrors() { return standardErrors.clone(); }
        public int cohorts() { int n = 0; for (double f : frequencies) if (f != -1) n++; return n; }
        public double samples() { double n = 0; for (int x : sampleSizes) if (x > 0) n += x; return n; }
        public double averageFrequency() {
            double sum = 0; long n = 0;
            for (int i = 0; i < frequencies.length; i++) if (frequencies[i] != -1) {
                sum += (float)frequencies[i] * (float)sampleSizes[i]; n += sampleSizes[i];
            }
            require(n <= Integer.MAX_VALUE, "Source int32 EAF sample-count overflow; no weighted frequency admitted");
            return n == 0 ? 0 : sum / n;
        }
        public String directions() {
            StringBuilder s = new StringBuilder();
            for (double b : effects) s.append(b == -666 ? '?' : b == 0 ? '0' : b > 0 ? '+' : '-');
            return s.toString();
        }
    }
    public record Parsed(Settings settings, List<Cohort> cohorts, List<Variant> variants,
            List<RowDisposition> rows, List<String> warnings, Map<Path,String> inputSha256) {
        public Parsed { cohorts = List.copyOf(cohorts); variants = List.copyOf(variants); rows = List.copyOf(rows); warnings = List.copyOf(warnings); inputSha256 = Map.copyOf(inputSha256); }
    }
    public record VariantFit(Variant variant, String status, String detail, int[] participatingCohorts,
            double[] usedEffects, double[] usedStandardErrors, double[] weights,
            MrMegaRegression.Result regression, double outputAssociationChiSquare, double stableOutputAssociationP) {
        public VariantFit {
            participatingCohorts = participatingCohorts.clone(); usedEffects = usedEffects.clone();
            usedStandardErrors = usedStandardErrors.clone(); weights = weights.clone();
        }
        @Override public int[] participatingCohorts() { return participatingCohorts.clone(); }
        @Override public double[] usedEffects() { return usedEffects.clone(); }
        @Override public double[] usedStandardErrors() { return usedStandardErrors.clone(); }
        @Override public double[] weights() { return weights.clone(); }
    }
    public record Analysis(Parsed parsed, double[][] coordinates, MrMegaAncestry.Selection selection,
            MrMegaAncestry.Axes inferredAxes, List<VariantFit> variants, double outputLambda, List<String> warnings) {
        public Analysis { coordinates = copy(coordinates); variants = List.copyOf(variants); warnings = List.copyOf(warnings); }
        @Override public double[][] coordinates() { return copy(coordinates); }
    }

    /** Relative cohort paths follow the original process working-directory convention. */
    public static Parsed read(Path fileList, Settings settings) throws IOException {
        require(fileList != null && settings != null, "Cohort file list and settings required");
        var sources = new ArrayList<Path>(); var provided = new ArrayList<double[]>();
        var warnings = new ArrayList<String>();
        var pins = new LinkedHashMap<Path,String>(); fileList = fileList.toAbsolutePath().normalize(); pins.put(fileList,sha256(fileList));
        try (var reader = reader(fileList)) {
            String line; long nr = 0;
            while ((line = reader.readLine()) != null) {
                nr++; String[] t = tokens(line);
                if (t.length == 0) {
                    require(!settings.precalculated, "Blank precalculated-PC list line " + nr + " would misalign original physical-line PC indices; remove the blank line explicitly");
                    continue;
                }
                require(t.length == (settings.precalculated ? settings.axes + 1 : 1), "File-list line " + nr + " must contain a path and exactly the requested precalculated axes (or only a path for inference); whitespace in paths is unsupported");
                sources.add(Path.of(t[0]).toAbsolutePath().normalize()); double[] axes = new double[settings.precalculated ? settings.axes : 0];
                for (int i = 0; i < axes.length; i++) axes[i] = number(t[i + 1], "file-list axis at line " + nr);
                provided.add(axes); require(sources.size() <= 512, "Cohort capacity 512 exceeded");
            }
        } catch (IOException failure) { throw new IOException("Could not read cohort list " + fileList + ": " + failure.getMessage(),failure); }
        require(sources.size() >= settings.axes + 3, "Global cohort count must be at least axis count plus three");
        for (Path source : sources) pins.put(source,sha256(source));
        var markers = new LinkedHashMap<String, MutableVariant>(); var rows = new ArrayList<RowDisposition>(); var cohorts = new ArrayList<Cohort>();
        for (int c = 0; c < sources.size(); c++) {
            Path path = sources.get(c); var chi = new ArrayList<Double>();
            try (var reader = reader(path)) {
                String first = reader.readLine(); require(first != null, "Empty cohort file: " + path);
                String[] header = tokens(first); Map<Column, Integer> positions = header(header, settings, warnings, path);
                int[] filterPositions = new int[settings.filters.size()]; Arrays.fill(filterPositions, -1);
                for (int i = 0; i < filterPositions.length; i++) {
                    for (int j = 0; j < header.length; j++) if (header[j].equalsIgnoreCase(settings.filters.get(i).column)) filterPositions[i] = j;
                    if (filterPositions[i] < 0) warnings.add(path + ": filter " + settings.filters.get(i).column + " absent; original behavior does not apply this filter in this cohort.");
                }
                String line; long nr = 1;
                while ((line = reader.readLine()) != null) {
                    nr++; require(nr <= 10000001 && rows.size() < 10000000, "Input/disposition row capacity exceeded");
                    String[] t = tokens(line);
                    if (t.length < 2) { rows.add(new RowDisposition(c, nr, "", "IGNORED_SHORT_LINE", "Original ignores zero/one-token lines")); continue; }
                    boolean passed = true;
                    for (int i = 0; i < filterPositions.length; i++) if (filterPositions[i] >= 0) {
                        int at = filterPositions[i]; require(at < t.length, location(path,nr) + " truncated filter column");
                        if (!settings.filters.get(i).accepts(number(t[at], location(path,nr) + " filter"))) passed = false;
                    }
                    if (!passed) { rows.add(new RowDisposition(c, nr, "", "FILTERED", "Explicit cohort filter")); continue; }
                    for (int at : positions.values()) require(at < t.length, location(path,nr) + " missing a required value; empty fields are not imputed");
                    String id = get(t,positions,Column.MARKERNAME); String ea = settings.noAlleles ? "" : get(t,positions,Column.EA);
                    String nea = settings.noAlleles ? "" : get(t,positions,Column.NEA); String context = location(path,nr) + " " + id;
                    double frequency = number(get(t,positions,Column.EAF), context + " EAF");
                    int n = integer(get(t,positions,Column.N),context + " N"); require(n >= 0, context + " negative sample count refused");
                    double beta,se;
                    if (settings.trait == Trait.QUANTITATIVE) {
                        beta = number(get(t,positions,Column.BETA),context + " BETA"); se = number(get(t,positions,Column.SE),context + " SE");
                    } else {
                        double odds = number(get(t,positions,Column.OR),context + " OR");
                        double lower = number(get(t,positions,Column.OR_95L),context + " OR_95L");
                        number(get(t,positions,Column.OR_95U),context + " OR_95U"); // mandatory, not used by the source conversion
                        require(odds > 0 && lower > 0,context + " OR and lower CI must be positive");
                        beta = Math.log(odds); se = (beta - Math.log(lower)) / 1.96;
                    }
                    require(Double.isFinite(beta) && Float.isFinite((float)beta) && (float)beta != -666,
                        context + " nonfinite/overflowing effect or collision with original -666 sentinel; no result admitted");
                    require(Double.isFinite(se) && Float.isFinite((float)se) && (float)se > 0,
                        context + " nonpositive/nonfinite or float32-underflowing SE; original downstream weights are undefined");
                    double q = Math.pow(beta / se,2); require(Double.isFinite(q),context + " cohort chi-square overflow");
                    boolean fresh = !markers.containsKey(id); MutableVariant marker = fresh ? new MutableVariant(id,sources.size()) : markers.get(id);
                    boolean duplicate = marker.seen[c]; marker.seen[c] = true;
                    int allele = 0;
                    if (!settings.noAlleles) {
                        if (ea.equals(nea)) allele = 1;
                        else if (marker.ea.equals("N")) { marker.ea = ea; marker.nea = nea; }
                        else if (!(marker.ea.equals(ea) && marker.nea.equals(nea) || marker.ea.equals(nea) && marker.nea.equals(ea))) allele = 2;
                    }
                    int chromosome = chromosome(get(t,positions,Column.CHROMOSOME),context);
                    int position = integer(get(t,positions,Column.POSITION),context + " position");
                    if (chromosome == 0) warnings.add(context + ": invalid source chromosome; retain previous value or zero.");
                    else if (marker.chr == 0) marker.chr = chromosome;
                    else if (marker.chr != chromosome) warnings.add(context + ": chromosome conflict; retain first valid chromosome " + marker.chr + ".");
                    if (position <= 0) warnings.add(context + ": invalid source position; retain previous value or zero.");
                    else if (marker.pos == 0) marker.pos = position;
                    else if (marker.pos != position) warnings.add(context + ": position conflict; retain first valid position " + marker.pos + ".");
                    marker.n[c] = n; String pushedEa = settings.noAlleles ? "N" : ea, pushedNea = settings.noAlleles ? "N" : nea;
                    int eaf = 0;
                    if (frequency < 0 || frequency > 1) eaf = 1;
                    else if (marker.ea.equals(pushedEa) && marker.nea.equals(pushedNea)) marker.f[c] = (float)frequency;
                    else if (marker.ea.equals(pushedNea) && marker.nea.equals(pushedEa)) marker.f[c] = (float)(1-frequency);
                    else eaf = 2;
                    String disposition;
                    if (fresh) {
                        marker.b[c] = (float)beta; marker.s[c] = (float)se; chi.add(q);
                        if (allele != 1 && eaf != 1) { markers.put(id,marker); disposition = "ADMITTED"; }
                        else disposition = allele == 1 ? "REJECTED_EQUAL_ALLELES" : "REJECTED_EAF_OUT_OF_RANGE";
                    } else if (allele == 0 && eaf == 0) {
                        marker.b[c] = (float)(ea.equals(marker.ea) ? beta : -beta); marker.s[c] = (float)se; chi.add(q);
                        disposition = duplicate ? "OVERWROTE_PRIOR_COHORT_ROW" : "ADMITTED";
                    } else disposition = allele == 1 ? "REJECTED_EQUAL_ALLELES" : allele == 2 || eaf == 2 ? "REJECTED_ALLELE_MISMATCH" : "REJECTED_EAF_OUT_OF_RANGE";
                    String detail = fresh ? "New-marker row contributes to source lambda even when rejected" : "Existing-marker N is assigned before allele/frequency validation; prior frequency/effect may remain";
                    rows.add(new RowDisposition(c,nr,id,disposition,detail));
                    if (duplicate || !disposition.equals("ADMITTED")) warnings.add(context + ": " + disposition + ". " + detail + ".");
                }
            } catch (IOException failure) { throw new IOException("Could not read cohort file " + path + ": " + failure.getMessage(),failure); }
            double lambda = sourceMedian(chi,"cohort " + path) / .4549364;
            require(Double.isFinite(lambda),"Cohort lambda overflow; no input GC result admitted: " + path);
            cohorts.add(new Cohort(path,provided.get(c),lambda,chi.size()));
        }
        require(!markers.isEmpty(), "No admitted variants; no analysis result");
        warnings.add("Source odd-count lambda median uses sorted index floor(n/2)-1; this behavior is retained and is not a conventional median. Duplicates overwrite in source order and can contribute multiple lambda rows.");
        warnings.add("Alleles are case-sensitive. No strand complement, build conversion, allele inference or variant-ID repair is performed; STRAND and direction-threshold options have no active effect in source0.2.");
        if (settings.noAlleles) warnings.add("Source no_alleles behavior retained: new-marker beta stays positive as supplied; later occurrences negate beta because the incoming empty EA differs from stored N. Supply actual allele columns to avoid this source behavior; do not silently assume same-direction effects.");
        if (settings.trait == Trait.BINARY) warnings.add("Source binary conversion uses log(OR) and (log(OR)-log(OR_95L))/1.96; the required upper-CI column does not affect the conversion.");
        Parsed result = new Parsed(settings,cohorts,markers.values().stream().map(MutableVariant::freeze).toList(),rows,warnings,pins);
        verifyInputs(result); return result;
    }

    /** Performs input GC, per-variant cohort selection, native regression and optional output GC. */
    public static Analysis analyze(Parsed parsed) {
        require(parsed != null, "Parsed cohort data required"); Settings settings = parsed.settings;
        int axes = settings.axes; double[][] coordinates; MrMegaAncestry.Selection selection = null; MrMegaAncestry.Axes inferred = null;
        var warnings = new ArrayList<>(parsed.warnings);
        if (settings.precalculated) coordinates = parsed.cohorts.stream().map(Cohort::providedAxes).toArray(double[][]::new);
        else {
            var frequencyVariants = parsed.variants.stream().map(v -> new MrMegaAncestry.Variant(v.id,v.chromosome,v.position,v.frequencies)).toList();
            selection = MrMegaAncestry.select(frequencyVariants,parsed.cohorts.size()); inferred = MrMegaAncestry.infer(selection,axes);
            coordinates = inferred.coordinates(); warnings.addAll(selection.warnings()); warnings.addAll(inferred.warnings());
        }
        var results = new ArrayList<VariantFit>(); var q = new ArrayList<Double>();
        for (Variant v : parsed.variants) {
            var selected = new ArrayList<Integer>(); boolean samePopulation = true;
            for (int c = 0; c < v.frequencies.length; c++) { if (v.frequencies[c] != -1) selected.add(c); if ((v.frequencies[c] != -1) != (v.effects[c] != -666)) samePopulation = false; }
            int[] indices = selected.stream().mapToInt(Integer::intValue).toArray(); int n = indices.length;
            double[] y = new double[n],se = new double[n],w = new double[n]; double[][] x = new double[n][axes];
            String status = n < axes + 3 ? "SmallCohortCount" : !samePopulation ? "FAIL2_INCONSISTENT_SOURCE_COHORTS" : "PASS";
            String detail = ""; MrMegaRegression.Result fit = null;
            if (status.equals("PASS")) {
                for (int i = 0; i < n; i++) {
                    int c = indices[i]; y[i] = v.effects[c]; float stored = (float)v.standardErrors[c]; double lambda = parsed.cohorts.get(c).lambda;
                    if (settings.inputGc && lambda > 1) stored = (float)(stored * Math.sqrt(lambda));
                    se[i] = stored; float reciprocal = 1.0f / stored; w[i] = Math.pow(reciprocal,2); x[i] = coordinates[c].clone();
                }
                try { fit = MrMegaRegression.fitWeighted(y,w,x); q.add(fit.associationChiSquare()); }
                catch (IllegalArgumentException failure) { status = "FAIL2_NUMERICALLY_UNRESOLVED"; detail = failure.getMessage(); }
            }
            if (!status.equals("PASS")) warnings.add(v.id + ": " + status + (detail.isEmpty() ? "" : ": " + detail) + "; row retained, no numerical result admitted.");
            results.add(new VariantFit(v,status,detail,indices,y,se,w,fit,Double.NaN,Double.NaN));
        }
        double outputLambda = 1;
        if (settings.outputGc) {
            outputLambda = sourceMedian(q,"output GC") / ChiSquare.quantile(.5,axes+1,true,false);
            require(Double.isFinite(outputLambda) && outputLambda > 0, "Output GC lambda nonpositive/nonfinite; no adjusted result admitted");
            warnings.add("Output GC divides association chi-square by lambda even below one. It does not change ancestry/residual statistics, coefficients or lnBF; raw and adjusted values remain separate.");
        }
        var complete = new ArrayList<VariantFit>();
        for (VariantFit row : results) {
            double statistic = row.regression == null ? Double.NaN : row.regression.associationChiSquare() / outputLambda;
            double p = statistic > 0 ? ChiSquare.cumulative(statistic,axes+1,false,false) : Double.NaN;
            complete.add(new VariantFit(row.variant,row.status,row.detail,row.participatingCohorts,row.usedEffects,row.usedStandardErrors,row.weights,row.regression,statistic,p));
        }
        warnings.add("P-values are explicitly native stable survival probabilities, with source zero-statistic NaN retained; original subtraction-approximation probabilities are not reproduced or relabeled.");
        return new Analysis(parsed,coordinates,selection,inferred,complete,outputLambda,warnings);
    }

    /** Rechecks every input after analysis/output preparation before admitting results. */
    public static void verifyInputs(Parsed parsed) throws IOException {
        for (var entry : parsed.inputSha256.entrySet()) require(sha256(entry.getKey()).equals(entry.getValue()),"Input changed during the analysis; preserve evidence and retry immutable inputs at a new output path: " + entry.getKey());
    }
    public static String sha256(Path path) throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path),"Expected regular input file, not symlink/special file: " + path);
        try (InputStream stream=Files.newInputStream(path)) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;
            while((n=stream.read(buffer))!=-1)digest.update(buffer,0,n);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("Required SHA-256 unavailable",failure); }
    }

    private static final class MutableVariant {
        final String id; int chr,pos; String ea="N",nea="N"; final double[] f,b,s; final int[] n; final boolean[] seen;
        MutableVariant(String id,int count) { this.id=id;f=new double[count];b=new double[count];s=new double[count];n=new int[count];seen=new boolean[count];Arrays.fill(f,-1);Arrays.fill(b,-666);Arrays.fill(s,-666); }
        Variant freeze() { return new Variant(id,chr,pos,ea,nea,f,n,b,s); }
    }
    private static Map<Column,Integer> header(String[] header,Settings settings,List<String> warnings,Path path) {
        var required = new ArrayList<>(List.of(Column.MARKERNAME,Column.EAF,Column.CHROMOSOME,Column.POSITION,Column.N));
        if (!settings.noAlleles) required.addAll(List.of(Column.EA,Column.NEA));
        required.addAll(settings.trait == Trait.QUANTITATIVE ? List.of(Column.BETA,Column.SE) : List.of(Column.OR,Column.OR_95L,Column.OR_95U));
        var positions = new EnumMap<Column,Integer>(Column.class);
        for (Column column : required) {
            var aliases = new ArrayList<>(settings.aliases.getOrDefault(column,List.of())); if (settings.standardNames) aliases.add(column.name()); int matches=0;
            for (int i=0;i<header.length;i++) for (String alias : aliases) if (header[i].equalsIgnoreCase(alias)) { positions.put(column,i);matches++; }
            require(matches>0,path + ": required column missing: " + column + "; supply explicit aliases or correct the header");
            if (matches>1) warnings.add(path + ": several matches for " + column + "; use last source-ordered matching column " + positions.get(column) + ".");
        }
        return positions;
    }
    private static BufferedReader reader(Path path) throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path), "Input must be a readable regular file, not a symlink or special file: " + path);
        InputStream input = Files.newInputStream(path);
        try {
            if (path.toString().endsWith("gz")) input = new GZIPInputStream(input);
            return new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
        } catch (IOException | RuntimeException failure) { input.close(); throw failure; }
    }
    private static double sourceMedian(List<Double> input,String context) {
        require(input.size()>=2,context + ": original median indexes outside its array for fewer than two chi-square values; supply at least two admitted source chi-square rows");
        double[] sorted=input.stream().mapToDouble(Double::doubleValue).sorted().toArray();int half=sorted.length/2;
        return sorted.length%2==1 ? sorted[half-1] : (sorted[half-1]+sorted[half])/2;
    }
    private static String[] tokens(String line) {
        require(line.length()<999999,"Line exceeds supported original input buffer; split or correct the input explicitly");
        String trimmed=line.replace('\r',' ').replace('\t',' ').trim(); return trimmed.isEmpty() ? new String[0] : trimmed.split(" +");
    }
    private static String get(String[] row,Map<Column,Integer> positions,Column column) { return row[positions.get(column)]; }
    private static String location(Path path,long line) { return path + ":" + line; }
    private static double number(String token,String context) {
        try { double value=Double.parseDouble(token);require(Double.isFinite(value),context + ": nonfinite numeric token " + token);return value; }
        catch (NumberFormatException failure) { throw new IllegalArgumentException(context + ": malformed number " + token + "; original atof coercion is not used",failure); }
    }
    private static int integer(String token,String context) {
        require(token.matches("[+-]?[0-9]+"),context + ": malformed integer " + token + "; original atoi prefix/coercion is not used");
        try { return Integer.parseInt(token); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException(context + ": integer outside int32 range",failure); }
    }
    private static int chromosome(String token,String context) {
        return switch(token) {
            case "X" -> 23; case "Y" -> 24; case "XY" -> 25; case "MT" -> 0;
            default -> { int value=integer(token,context + " chromosome");yield value>0 && value<26 ? value : 0; }
        };
    }
    private static double[][] copy(double[][] source) { double[][] result=new double[source.length][];for(int i=0;i<source.length;i++)result[i]=source[i].clone();return result; }
    private static void require(boolean test,String message) { if(!test)throw new IllegalArgumentException(message); }
}
