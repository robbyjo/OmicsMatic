/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.coloc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone capture probe; no original R code is executed and all attempts are retained. */
public final class ColocAbfReferenceProbe {
    private ColocAbfReferenceProbe() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input and NEW output directories required");
        Path input=Path.of(args[0]), out=Path.of(args[1]);
        Files.createDirectory(out);
        for (String line : Files.readAllLines(input.resolve("cases.tsv"),StandardCharsets.UTF_8).subList(1,
                Files.readAllLines(input.resolve("cases.tsv"),StandardCharsets.UTF_8).size())) {
            String name=line.split("\t",-1)[0];
            if (!name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("unsafe case name");
            Path source=input.resolve(name), dest=out.resolve(name);Files.createDirectory(dest);
            try {
                var settings=table(source.resolve("settings.tsv"));var options=new LinkedHashMap<String,String>();
                for (String[] row : settings.rows()) options.put(row[0],row[1]);
                var one=table(source.resolve("trait1.tsv"));var two=table(source.resolve("trait2.tsv"));
                var first=dataset(one,options,"1");var second=dataset(two,options,"2");
                var config=new ColocAbfOptions(scalar(options,"p1",1e-4),scalar(options,"p2",1e-4),
                    scalar(options,"p12",1e-5),vector(one,"prior_weight"),vector(two,"prior_weight"));
                ColocAbfResult result=ColocAbf.analyze(first,second,config);
                var summary=new StringBuilder("field\tvalue\n");summary.append("nsnps\t").append(result.variants().size()).append('\n');
                for (int h=0;h<5;h++) summary.append("PP.H").append(h).append(".abf\t").append(result.hypothesisPosteriors()[h]).append('\n');
                Files.writeString(dest.resolve("summary.tsv"),summary,StandardCharsets.UTF_8);
                var priors=new StringBuilder("field\tvalue\np1\t"+result.effectivePriors()[0]+"\np2\t"+result.effectivePriors()[1]+"\np12\t"+result.effectivePriors()[2]+"\n");
                Files.writeString(dest.resolve("priors.tsv"),priors,StandardCharsets.UTF_8);
                var variants=new StringBuilder("snp\tV.df1\tz.df1\tr.df1\tlABF.df1\tV.df2\tz.df2\tr.df2\tlABF.df2\tSNP.PP.H4\n");
                for (var v:result.variants()) variants.append(v.id()).append('\t').append(v.variance1()).append('\t')
                    .append(v.z1()).append('\t').append(v.shrinkage1()).append('\t').append(v.logAbf1()).append('\t')
                    .append(v.variance2()).append('\t').append(v.z2()).append('\t').append(v.shrinkage2()).append('\t')
                    .append(v.logAbf2()).append('\t').append(v.sourceConditionalSharedPosterior()).append('\n');
                Files.writeString(dest.resolve("variants.tsv"),variants,StandardCharsets.UTF_8);
                Files.write(dest.resolve("warnings.txt"),result.warnings(),StandardCharsets.UTF_8);
                Files.writeString(dest.resolve("scales.tsv"),"sdY1\tsdY2\n"+result.trait1StandardDeviation()+"\t"+result.trait2StandardDeviation()+"\n",StandardCharsets.UTF_8);
                Files.writeString(dest.resolve("STATUS.txt"),"0\n",StandardCharsets.UTF_8);
            } catch (RuntimeException error) {
                Files.writeString(dest.resolve("ERROR.txt"),error.toString()+"\n",StandardCharsets.UTF_8);
                Files.writeString(dest.resolve("STATUS.txt"),"2\n",StandardCharsets.UTF_8);
            }
        }
    }
    private record Table(List<String> header,List<String[]> rows) { }
    private static Table table(Path file) throws Exception {
        var lines=Files.readAllLines(file,StandardCharsets.UTF_8);var rows=new ArrayList<String[]>();
        var header=List.of(lines.get(0).split("\t",-1));
        for (String line:lines.subList(1,lines.size())) {
            var fields=line.split("\t",-1);
            if(fields.length!=header.size())throw new IllegalArgumentException("ragged table");rows.add(fields);
        }
        return new Table(header,rows);
    }
    private static double[] vector(Table table,String field) {
        int c=table.header().indexOf(field);if(c<0)return null;
        var values=new double[table.rows().size()];for(int i=0;i<values.length;i++)values[i]=Double.parseDouble(table.rows().get(i)[c]);return values;
    }
    private static Double scalar(Map<String,String> values,String name,Double fallback) {
        return values.containsKey(name)?Double.valueOf(values.get(name)):fallback;
    }
    private static ColocAbfInput dataset(Table table,Map<String,String> values,String suffix) {
        int c=table.header().indexOf("snp");var ids=new ArrayList<String>();for(var row:table.rows())ids.add(row[c]);
        long[] positions=null;c=table.header().indexOf("position");
        if(c>=0) {positions=new long[ids.size()];for(int i=0;i<positions.length;i++)positions[i]=Long.parseLong(table.rows().get(i)[c]);}
        var type=switch(values.get("type"+suffix)) {case "quant"->ColocAbfInput.TraitType.QUANTITATIVE;case "cc"->ColocAbfInput.TraitType.CASE_CONTROL;default->throw new IllegalArgumentException("unknown trait type");};
        return new ColocAbfInput(type,ids,vector(table,"beta"),vector(table,"varbeta"),vector(table,"pvalues"),
            vector(table,"MAF"),scalar(values,"N"+suffix,null),scalar(values,"sdY"+suffix,null),
            scalar(values,"s"+suffix,null),positions);
    }
}
