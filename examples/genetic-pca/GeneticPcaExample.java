/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.jlinalg.genetics.GeneticPrincipalComponents;
import org.jlinalg.genetics.GeneticPrincipalComponents.Marker;
import org.jlinalg.genetics.GeneticPrincipalComponents.SampleId;

/** Runnable synthetic reference example, not a general-purpose genotype file importer. */
public final class GeneticPcaExample {
    private GeneticPcaExample() { }
    public static void main(String[] args) throws IOException {
        if (args.length != 4 || !List.of("all","founders").contains(args[1]) || !List.of("mean","pairwise").contains(args[2]))
            throw new IllegalArgumentException("Usage: GeneticPcaExample SYNTHETIC_INPUT_DIR all|founders mean|pairwise NEW_OUTPUT_DIR");
        Path input=Path.of(args[0]),output=Path.of(args[3]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output must be a new directory: " + output);
        var samples=new ArrayList<SampleId>();var reference=new ArrayList<Integer>();
        List<String> sampleRows=Files.readAllLines(input.resolve("samples.tsv"),StandardCharsets.UTF_8);
        if(!sampleRows.get(0).equals("fid\tiid\tfrequency_founder"))throw new IllegalArgumentException("Unexpected synthetic sample header");
        for(int i=1;i<sampleRows.size();i++) {
            String[] row=sampleRows.get(i).split("\t",-1);
            if(row.length!=3 || !List.of("0","1").contains(row[2]))throw new IllegalArgumentException("Invalid synthetic sample row");
            samples.add(new SampleId(row[0],row[1]));if(args[1].equals("all")||row[2].equals("1"))reference.add(i-1);
        }
        var markers=new ArrayList<Marker>();List<String> markerRows=Files.readAllLines(input.resolve("markers.tsv"),StandardCharsets.UTF_8);
        if(!markerRows.get(0).equals("id\tcounted_allele\tother_allele"))throw new IllegalArgumentException("Unexpected synthetic marker header");
        for(String line:markerRows.subList(1,markerRows.size())) {
            String[] row=line.split("\t",-1);if(row.length!=3)throw new IllegalArgumentException("Invalid synthetic marker row");
            markers.add(new Marker(row[0],row[1],row[2]));
        }
        double[][] dosages=Files.readAllLines(input.resolve("dosages.tsv"),StandardCharsets.UTF_8).stream()
            .map(line->Arrays.stream(line.split("\t",-1)).mapToDouble(s->s.equals("NA")?Double.NaN:Double.parseDouble(s)).toArray()).toArray(double[][]::new);
        var limits=GeneticPrincipalComponents.Limits.defaults();
        var frequencies=GeneticPrincipalComponents.estimateFrequencies(dosages,reference.stream().mapToInt(Integer::intValue).toArray(),limits);
        var missingness=args[2].equals("mean")?GeneticPrincipalComponents.Missingness.MEAN_IMPUTE:GeneticPrincipalComponents.Missingness.PAIRWISE_COMPLETE;
        var model=GeneticPrincipalComponents.fit(markers,samples,dosages,IntStream.range(0,samples.size()).toArray(),frequencies.values(),GeneticPrincipalComponents.Options.defaults(5,missingness));
        var projected=model.project(markers,samples,dosages,limits);
        Files.createDirectories(output);Files.writeString(output.resolve("STATUS.txt"),"RUNNING\n",StandardCharsets.UTF_8);
        try {
            double[][] fitted=model.fittedCoordinates(),scores=projected.coordinates();
            var coordinateRows=new ArrayList<String>();coordinateRows.add("fid\tiid\tobserved_variants\tstatus\tkind\tPC1\tPC2\tPC3\tPC4\tPC5");
            for(int i=0;i<samples.size();i++)for(String kind:List.of("fitted","projected")) {
                double[] row=kind.equals("fitted")?fitted[i]:scores[i];
                coordinateRows.add(samples.get(i).familyId()+"\t"+samples.get(i).individualId()+"\t"+projected.observedVariants()[i]+"\t"+projected.statuses().get(i)+"\t"+kind+"\t"+join(row));
            }
            Files.write(output.resolve("coordinates.tsv"),coordinateRows,StandardCharsets.UTF_8);
            var matrixRows=new ArrayList<String>();double[] matrix=model.relationshipMatrix();int n=samples.size();
            for(int i=0;i<n;i++)matrixRows.add(join(Arrays.copyOfRange(matrix,i*n,(i+1)*n)));
            Files.write(output.resolve("relationship.tsv"),matrixRows,StandardCharsets.UTF_8);
            Files.writeString(output.resolve("spectrum.tsv"),join(model.spectrum())+"\n",StandardCharsets.UTF_8);
            var weightRows=new ArrayList<String>();double[][] weights=model.projectionWeights();double[] p=model.frequencies();
            int[] observed=frequencies.observedSamples();weightRows.add("id\tcounted_allele\tother_allele\tfrequency\tfrequency_observed_samples\tPC1\tPC2\tPC3\tPC4\tPC5");
            for(int v=0;v<markers.size();v++){Marker marker=markers.get(v);weightRows.add(marker.id()+"\t"+marker.countedAllele()+"\t"+marker.otherAllele()+"\t"+p[v]+"\t"+observed[v]+"\t"+join(weights[v]));}
            Files.write(output.resolve("weights.tsv"),weightRows,StandardCharsets.UTF_8);
            Files.writeString(output.resolve("WARNINGS.md"),"# Review before using coordinates\n\nSynthetic demonstration; arbitrary labels do not identify ancestry. Recorded founders are not certified unrelated.\n\n"+
                (missingness==GeneticPrincipalComponents.Missingness.PAIRWISE_COMPLETE?"**PAIRWISE FIT:** The GRM can be indefinite. Projected coordinates use a mean-imputed linear transform and need not equal fitted coordinates. Use the explicitly labelled kind column.\n":"Mean-imputed fitted coordinates and projections of the basis agree to numerical precision.\n")+
                "\nAll-missing projected targets are NaN with NO_OBSERVED_DOSAGES; obtain observed panel genotypes before interpreting them. Never replace this status with a zero coordinate.\n",StandardCharsets.UTF_8);
            Files.writeString(output.resolve("STATUS.txt"),"PASSED_SYNTHETIC_EXAMPLE\nfrequency_reference="+args[1]+"\nfrequency_reference_samples="+reference.size()+"\nfitted_samples="+samples.size()+"\nmarkers="+markers.size()+"\nmissingness="+missingness+"\nmaximum_eigen_residual="+model.maximumEigenResidual()+"\n",StandardCharsets.UTF_8);
        } catch(IOException|RuntimeException failure) {
            Files.writeString(output.resolve("STATUS.txt"),"FAILED_DO_NOT_USE\n"+failure+"\n",StandardCharsets.UTF_8);
            Files.writeString(output.resolve("WARNINGS.md"),"# FAILED — DO NOT USE\n\n"+failure+"\n\nCorrect the named input/output cause and rerun to a new directory. Incomplete output is retained for diagnosis.\n",StandardCharsets.UTF_8);
            throw failure;
        }
    }
    private static String join(double[] row) { return String.join("\t",Arrays.stream(row).mapToObj(Double::toString).toList()); }
}
