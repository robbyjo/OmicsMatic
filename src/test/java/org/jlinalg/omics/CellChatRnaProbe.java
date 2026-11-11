/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/** Independent process boundary for original-R comparisons; no JUnit dependency. */
public final class CellChatRnaProbe {
    private CellChatRnaProbe() { }
    private static Map<String,List<String>> resource(Scanner input,int count) {
        Map<String,List<String>> result=new HashMap<>();
        for (int i=0;i<count;i++) {
            String key=input.next(); int n=input.nextInt(); List<String> components=new ArrayList<>();
            for (int j=0;j<n;j++) components.add(input.next());
            if (result.put(key,components)!=null) throw new IllegalArgumentException("Duplicate resource");
        }
        return result;
    }
    private static String cofactor(String value) { return value.equals("-") ? "" : value; }
    public static void main(String[] args) throws IOException {
        Path out=Path.of(args[2]); Files.createDirectories(out);
        int completed=0;
        try (Scanner input=new Scanner(Path.of(args[0]),StandardCharsets.UTF_8).useLocale(Locale.ROOT)) {
            while (input.hasNext()) {
                String id=input.next(), label=input.next();
                CellChatRna.Profile profile=CellChatRna.Profile.valueOf(input.next());
                int nr=input.nextInt(),nc=input.nextInt(),ng=input.nextInt();
                CellChatRna.Average average=CellChatRna.Average.valueOf(input.next());
                double trim=input.nextDouble(),kh=input.nextDouble(),hill=input.nextDouble();
                boolean population=input.nextInt()==1;
                int ni=input.nextInt(),nx=input.nextInt(),nf=input.nextInt(),nb=input.nextInt();
                input.nextInt(); // R seed is source metadata; the Java component consumes actual permutations.
                String[] genes=new String[nr]; double[][] data=new double[nr][nc];
                for (int r=0;r<nr;r++) { genes[r]=input.next(); for (int c=0;c<nc;c++) data[r][c]=input.nextDouble(); }
                int[] groups=new int[nc]; for (int c=0;c<nc;c++) groups[c]=input.nextInt();
                List<CellChatRna.Interaction> interactions=new ArrayList<>();
                for (int i=0;i<ni;i++) interactions.add(new CellChatRna.Interaction(input.next(),input.next(),input.next(),
                    cofactor(input.next()),cofactor(input.next()),cofactor(input.next()),cofactor(input.next())));
                Map<String,List<String>> complexes=resource(input,nx),cofactors=resource(input,nf);
                if (!label.equals(args[3])) continue;
                int[][] permutations=new int[nb][nc];
                try (Scanner pi=new Scanner(Path.of(args[1],id,"permutations.tsv"),StandardCharsets.UTF_8)) {
                    for (int b=0;b<nb;b++) for (int c=0;c<nc;c++) permutations[b][c]=pi.nextInt();
                    if (pi.hasNext()) throw new IllegalArgumentException("Extra permutation tokens");
                }
                CellChatRna.Result result=CellChatRna.infer(profile,genes,data,groups,ng,interactions,complexes,cofactors,
                    new CellChatRna.Parameters(average,trim,kh,hill,population),permutations);
                Path home=out.resolve(id); Files.createDirectory(home);
                double[][][] p=result.probabilities(),v=result.permutationPValues();
                try (PrintWriter writer=new PrintWriter(Files.newBufferedWriter(home.resolve("metrics.tsv"),StandardCharsets.UTF_8))) {
                    writer.println("interaction\tsource\ttarget\tprobability\tpvalue");
                    for (int i=0;i<ni;i++) for (int s=0;s<ng;s++) for (int t=0;t<ng;t++)
                        writer.println(i+"\t"+s+"\t"+t+"\t"+p[i][s][t]+"\t"+v[i][s][t]);
                }
                Files.writeString(home.resolve("boundaries.txt"),Long.toString(result.nearBoundaryComparisons()),StandardCharsets.UTF_8);
                Files.write(home.resolve("WARNINGS.txt"),result.warnings(),StandardCharsets.UTF_8);
                // Defensive result copies and absence of unintended mutation.
                double first=p[0][0][0]; p[0][0][0]=-1;
                if (result.probabilities()[0][0][0]!=first) throw new IllegalStateException("Result array alias");
                CellChatRna.Result observed=CellChatRna.infer(profile,genes,data,groups,ng,interactions,complexes,cofactors,
                    new CellChatRna.Parameters(average,trim,kh,hill,population),new int[0][]);
                if (observed.permutationPValues().length!=0) throw new IllegalStateException("Invented p-values");
                completed++;
            }
        }
        Files.writeString(out.resolve("COMPLETED.txt"),"PASS_NATIVE_CELLCHAT_PREPARED_RNA_COMPONENT_ONLY\n"+completed+"\n",StandardCharsets.UTF_8);
        System.out.println("PASS native CellChat prepared RNA cases="+completed);
    }
}
