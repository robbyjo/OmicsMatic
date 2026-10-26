/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.jlinalg.pipeline.HumanPloidyProfile;
import org.jlinalg.pipeline.HumanPloidyVariantSource;
import org.jlinalg.pipeline.VariantSource;

/** Streamed local QC receipts; invalid-call/sample files contain participant identifiers. */
final class HumanPloidySession implements AutoCloseable {
    private final HumanPloidyProfile profile;private final HumanPloidySamples samples;private final HumanPloidySamples.Selection selection;private final Path summary;
    private final BufferedWriter variants,calls;private long scanned,inScope,invalid;private boolean finished;
    HumanPloidySession(Path prefix,HumanPloidyProfile profile,HumanPloidySamples samples,HumanPloidySamples.Selection selection)throws IOException {
        this.profile=profile;this.samples=samples;this.selection=selection;summary=Path.of(prefix+".human-ploidy.yaml");Files.createDirectories(summary.toAbsolutePath().getParent());
        variants=Files.newBufferedWriter(Path.of(prefix+".human-ploidy-variants.tsv"));calls=Files.newBufferedWriter(Path.of(prefix+".human-ploidy-invalid-calls.tsv"));
        variants.write("variant_id\tchromosome\tposition\tregion\tdisposition\tsource_ploidy_status\tcalled_samples\tmissing_samples\tinvalid_calls\talternate_copies\tcalled_alleles\n");calls.write("variant_id\tsample_id\tobserved_source_ploidy\tsource_dosage\n");
        FollowupSupport.table(Path.of(prefix+".human-ploidy-samples.tsv"),List.of("id","declared_complement","disposition"),selection.audit());
    }
    VariantSource wrap(VariantSource source,boolean hardCalls) {
        return new HumanPloidyVariantSource(source,profile,hardCalls,row->{try{scanned++;if(!row.disposition().equals("outside_registered_region"))inScope++;invalid+=row.invalid();variants.write(String.join("\t",row.variant(),row.chromosome(),Long.toString(row.position()),row.region(),row.disposition(),row.sourcePloidyStatus(),Integer.toString(row.called()),Integer.toString(row.missing()),Integer.toString(row.invalid()),Double.toString(row.alternateCopies()),Double.toString(row.calledAlleles()))+"\n");}catch(IOException e){throw new UncheckedIOException(e);}},row->{try{calls.write(String.join("\t",row.variant(),row.sampleId(),Integer.toString(row.observedPloidy()),Double.toString(row.sourceDosage()))+"\n");}catch(IOException e){throw new UncheckedIOException(e);}});
    }
    void finish()throws IOException{samples.verify();if(inScope==0)throw new IOException("No variants in the registered human ploidy region; an empty scan is not evidence of no association");finished=true;}
    public void close()throws IOException {
        try{variants.close();}finally{calls.close();}
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("scan_finished",finished);result.put("region",profile.region());result.put("genome_build",profile.genomeBuild());result.put("haploid_source_scale",profile.haploidSourceScale());result.put("invalid_calls_policy",profile.invalidCalls());result.put("unknown_samples_policy",profile.unknownSamples());result.put("sample_declaration_sha256",samples.hash);result.put("sample_selection",selection.summary());result.put("scanned_source_variants",scanned);result.put("in_scope_variants",inScope);result.put("invalid_calls",invalid);result.put("interpretation",profile.interpretation());result.put("privacy","Sample and invalid-call TSV files are local participant data, excluded from remote evidence");StudyPublicEvidence.write(summary,result);
    }
}
