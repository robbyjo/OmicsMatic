/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.jlinalg.alignment.SambambaBamAudit;

/** Compare actual adapter outputs against the separately executed original fixtures. */
class CompareAll {
    static String command(Path path)throws Exception{
        Properties p=new Properties();try(var reader=Files.newBufferedReader(path)){p.load(reader);}
        int n=Integer.parseInt(p.getProperty("source_argc"));var args=new ArrayList<String>();
        for(int i=1;i<n;i++)args.add(p.getProperty("source_argv."+i));return String.join(" ",args);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("reference_directory runs_directory NEW_report.properties");
        Path reference=Path.of(args[0]),runs=Path.of(args[1]);int compared=0;
        for(String name:List.of("empty","missing_base_qualities","missing_read_group","paired_and_fragment","paired_quality","preexisting_flags","quality_tie","single_clipping_libraries_tags"))
            for(String version:List.of("0.6.6","0.8.2"))for(String mode:List.of("mark","remove"))for(String kind:List.of("analysis","verification")){
                Path original=reference.resolve(name).resolve(version),run=runs.resolve(name).resolve(version).resolve(mode).resolve(kind);
                SambambaBamAudit.compareActualRuns(original.resolve("mark.bam"),run.resolve("marked.bam"),
                    Files.readString(original.resolve("mark.cl.txt")),command(run.resolve("mark.pid.properties")),SambambaBamAudit.Limits.defaults());compared++;
                if(mode.equals("remove")){
                    SambambaBamAudit.compareActualRuns(original.resolve("remove.bam"),run.resolve("deduplicated.bam"),
                        Files.readString(original.resolve("remove.cl.txt")),command(run.resolve("remove.pid.properties")),SambambaBamAudit.Limits.defaults());compared++;
                }
            }
        if(compared!=96)throw new IllegalStateException("Complete comparison count differs");
        Properties proof=new Properties();proof.setProperty("status","PASS_96_COMPLETE_BAM_COMPARISONS_AGAINST_SEPARATE_ORIGINAL_EXECUTIONS");
        proof.setProperty("complete_BAM_comparisons",Integer.toString(compared));proof.setProperty("independent_duplicate_classifier","false");
        proof.setProperty("index_independently_qualified","false");
        Path dest=Path.of(args[2]);try(var writer=Files.newBufferedWriter(dest,java.nio.file.StandardOpenOption.CREATE_NEW)){proof.store(writer,null);}
        System.out.println(proof.getProperty("status"));
    }
}
