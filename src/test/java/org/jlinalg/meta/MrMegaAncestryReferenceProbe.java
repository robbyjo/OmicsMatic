/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/** Standalone development probe; this is not a production GWAS file interface. */
public final class MrMegaAncestryReferenceProbe {
    private MrMegaAncestryReferenceProbe() { }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--contracts")) { contracts(); return; }
        if (args.length != 2) throw new IllegalArgumentException("Use CONTROL_INPUT NEW_OUTPUT or --contracts");
        Path out = Files.createDirectory(Path.of(args[1])); int cases = 0;
        try (BufferedReader input = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] header = line.split("\\s+"); require(header.length == 4, "Complete case header required");
                int n = Integer.parseInt(header[1]), p = Integer.parseInt(header[2]), m = Integer.parseInt(header[3]);
                require(header[0].matches("[A-Za-z0-9-]+") && n <= 512 && m <= 10000, "Control capacity/name mismatch");
                var variants = new ArrayList<MrMegaAncestry.Variant>();
                for (int i = 0; i < m; i++) {
                    String[] fields = input.readLine().split("\\s+"); require(fields.length == n + 3, "Complete frequency row required");
                    double[] frequencies = new double[n]; for (int j = 0; j < n; j++) frequencies[j] = Double.parseDouble(fields[j + 3]);
                    variants.add(new MrMegaAncestry.Variant(fields[0],Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),frequencies));
                }
                double[] y = new double[n], w = new double[n];
                for (int i = 0; i < n; i++) { String[] fields = input.readLine().split("\\s+"); require(fields.length == 2,"Complete prepared model row required"); y[i] = Double.parseDouble(fields[0]); w[i] = Double.parseDouble(fields[1]); }
                var selection = MrMegaAncestry.select(variants,n); var axes = MrMegaAncestry.infer(selection,p);
                var model = MrMegaRegression.fitWeighted(y,w,axes.coordinates()); Path home = Files.createDirectory(out.resolve(header[0]));
                var chosen = new StringBuilder(); int[] ordinals = selection.sourceOrdinals();
                for (int i = 0; i < ordinals.length; i++) chosen.append(ordinals[i]).append('\t').append(selection.ids().get(i)).append('\n');
                write(home.resolve("selected.tsv"),chosen.toString()); var dispositions = new StringBuilder();
                for (var row : selection.dispositions()) dispositions.append(row.sourceOrdinal()).append('\t').append(row.id()).append('\t').append(row.reason()).append('\t').append(row.sourceMatrixSlot()).append('\n');
                write(home.resolve("dispositions.tsv"),dispositions.toString());
                matrix(home.resolve("frequencies.tsv"),selection.storedFrequencies()); matrix(home.resolve("distances.tsv"),selection.squaredDistances());
                matrix(home.resolve("gram.tsv"),axes.centeredGram()); matrix(home.resolve("coordinates.tsv"),axes.coordinates());
                matrix(home.resolve("spectrum.tsv"),new double[][]{axes.gramEigenvalues()});
                var stats = new StringBuilder(); stats.append("coefficients\t").append(vector(model.coefficients())).append('\n');
                stats.append("errors\t").append(vector(model.standardErrors())).append('\n');
                var covariance = new StringJoiner(","); for (double[] row : model.covariance()) for (double value : row) covariance.add(Double.toString(value));
                stats.append("covariance\t").append(covariance).append('\n');
                stats.append("statistics\t").append(vector(new double[]{model.centeredTotalSquares(),model.zeroTotalSquares(),model.residualSquares(),model.associationChiSquare(),model.ancestryChiSquare(),model.residualChiSquare(),model.logBayesFactor()})).append('\n');
                write(home.resolve("model.tsv"),stats.toString());
                write(home.resolve("warnings.txt"),String.join("\n",selection.warnings())+"\n"+String.join("\n",axes.warnings())+"\n");
                write(home.resolve("receipt.txt"),"COMPLETE_PREPARED_ANCESTRY_COMPONENT_ONLY\ncohorts="+n+"\naxes="+p+"\neligible="+selection.eligibleVariants()+"\nselected="+ordinals.length+"\nsweeps="+axes.jacobiSweeps()+"\n"); cases++;
            }
        }
        require(cases > 0,"No complete cases"); System.out.println("PASS_MRMEGA_PREPARED_ANCESTRY_PROBE_ONLY "+cases);
    }
    private static void write(Path path,String value) throws Exception { Files.writeString(path,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
    private static String vector(double[] values) { var out = new StringJoiner(","); for (double value : values) out.add(Double.toString(value)); return out.toString(); }
    private static void matrix(Path path,double[][] values) throws Exception { var out = new StringBuilder(); for (double[] row : values) { var line = new StringJoiner("\t"); for (double value : row) line.add(Double.toString(value)); out.append(line).append('\n'); } write(path,out.toString()); }
    private static void require(boolean condition,String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid/unsupported ancestry input admitted"); }
    private static void near(double a,double b) { if (Math.abs(a-b)>1e-11*Math.max(1,Math.abs(b))) throw new AssertionError(a+" != "+b); }
    private static void contracts() {
        double[][] square = {{0,4,4,8},{4,0,8,4},{4,8,0,4},{8,4,4,0}};
        var axes = MrMegaAncestry.inferFromSquaredDistances(square,2); double[][] coordinates = axes.coordinates();
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            double d = 0; for (int k = 0; k < 2; k++) d += Math.pow(coordinates[i][k]-coordinates[j][k],2); near(d,square[i][j]);
        }
        axes.coordinates()[0][0] = 999; axes.centeredGram()[0][0] = 999; axes.gramEigenvalues()[0] = 999;
        near(axes.gramEigenvalues()[0],4); near(axes.centeredGram()[0][0],2);
        if (axes.coordinates()[0][0] == 999 || axes.warnings().isEmpty()) throw new AssertionError("Defensive coordinate copy/tie warning missing");
        reject(() -> MrMegaAncestry.inferFromSquaredDistances(square,1));
        reject(() -> MrMegaAncestry.inferFromSquaredDistances(new double[4][4],1));
        reject(() -> MrMegaAncestry.inferFromSquaredDistances(new double[][]{{0,1},{2,0}},1));
        reject(() -> MrMegaAncestry.inferFromSquaredDistances(new double[][]{{0,Double.NaN},{Double.NaN,0}},1));
        reject(() -> MrMegaAncestry.inferFromSquaredDistances(square,4));
        double[] f = {.2,.3,.4,.5}; var v = new MrMegaAncestry.Variant("first",1,100,f); f[0] = .9; v.frequencies()[0] = .8; near(v.frequencies()[0],.2);
        var second = new MrMegaAncestry.Variant("second",1,1100000,new double[]{.5,.6,.3,.4});
        var selection = MrMegaAncestry.select(List.of(v,second),4); if (!selection.ids().equals(List.of("second"))) throw new AssertionError("Source index-zero sentinel changed");
        selection.sourceOrdinals()[0] = 999; selection.storedFrequencies()[0][0] = 999; if (selection.sourceOrdinals()[0]!=1) throw new AssertionError("Selection mutation");
        near(selection.storedFrequencies()[0][0],(double)(float).5);
        reject(() -> MrMegaAncestry.select(List.of(v),4));
        reject(() -> MrMegaAncestry.select(List.of(v,v),4));
        reject(() -> MrMegaAncestry.select(List.of(v,new MrMegaAncestry.Variant("bad",1,1100000,new double[]{.2,Double.NaN,.4,.5})),4));
        reject(() -> MrMegaAncestry.select(List.of(v,second),3));
        if (!Arrays.deepEquals(square,new double[][]{{0,4,4,8},{4,0,8,4},{4,8,0,4},{8,4,4,0}})) throw new AssertionError("Distance input mutation");
        System.out.println("PASS_MRMEGA_MDS_GEOMETRY_IMMUTABILITY_AND_NINE_REFUSAL_CONTRACTS_ONLY");
    }
}
