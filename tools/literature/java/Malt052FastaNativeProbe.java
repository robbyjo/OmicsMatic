/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.jlinalg.metagenomics.Malt052FastaIndexAudit;

/** Native complete intake; source-probe canonical fields are compared by the harness. */
public final class Malt052FastaNativeProbe {
    private Malt052FastaNativeProbe() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Complete ordered FASTA path list and NEW output required");
        var inputs=Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8).stream().map(Path::of).map(Path::toAbsolutePath).map(Path::normalize).toList();
        Path out=Path.of(args[1]);Files.createDirectory(out);
        var table=new StringBuilder("ascii\tnormalized_ascii\n");
        for(int i=0;i<127;i++)table.append(i).append('\t').append((int)Malt052FastaIndexAudit.sourceDna5(i)).append('\n');
        Files.writeString(out.resolve("dna5.tsv"),table,StandardCharsets.UTF_8);
        var population=Malt052FastaIndexAudit.read(inputs,new Malt052FastaIndexAudit.Limits(1000000,100,1000,1000000,100000));
        var rows=new StringBuilder("ordinal\theader_base64\tbases\tsequence_sha256\n");
        for(var reference:population.references())rows.append(reference.ordinal()).append('\t').append(Base64.getEncoder().encodeToString(reference.header().getBytes(StandardCharsets.UTF_8))).append('\t').append(reference.bases()).append('\t').append(reference.dna5Sha256()).append('\n');
        Files.writeString(out.resolve("references.tsv"),rows,StandardCharsets.UTF_8);
        Files.writeString(out.resolve("counts.tsv"),"references\tbases\n"+population.references().size()+"\t"+population.bases()+"\n",StandardCharsets.UTF_8);
    }
}
