/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.jlinalg.sequence.*;

/** Small native API example; the output directory must be NEW. */
public final class BiasExample {
    private BiasExample() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: BiasExample reference.fa NEW-output-directory");
        Path fasta=Path.of(args[0]),out=Path.of(args[1]).toAbsolutePath().normalize();if(Files.exists(out))throw new java.io.IOException("Output already exists; preserve it and choose a NEW path: "+out);
        var peaks=List.of(new SignacFragments.Region("chr1",1,6),new SignacFragments.Region("chr1",7,10),new SignacFragments.Region("chr1",11,14),new SignacFragments.Region("chr1",15,18));
        var result=ChromVarGcBias.calculate(fasta,null,peaks,SignacReferenceStats.Limits.defaults(),()->Thread.currentThread().isInterrupted());
        Files.createDirectories(out.getParent());Files.createDirectory(out);
        try(var writer=Files.newBufferedWriter(out.resolve("bias.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            writer.write("peak_id\tbias\n");for(var row:result.rows())writer.write(row.peak()+"\t"+Double.toString(row.bias().fraction())+"\n");
        }
        String warnings=String.join("\n\n",result.warnings())+"\n\nRequired action: review every undefined bias before background sampling. Preserve this example; use real matching reference/range identities and NEW paths for another analysis. No background peaks or biological scores were computed.\n";
        Files.writeString(out.resolve("WARNINGS.md"),warnings,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(warnings);
        System.out.println("Wrote native sequence bias: "+out.resolve("bias.tsv"));
    }
}
