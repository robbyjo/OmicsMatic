/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipInputStream;
import org.jlinalg.alignment.SambambaBamAudit;

/** Saved-original BAM readback only; never launches or refits an original tool. */
class Readback {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("fixture.zip NEW_OUTPUT_DIRECTORY");
        Path fixture=Path.of(args[0]),out=Path.of(args[1]).toAbsolutePath().normalize();
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture)));
        if(!hash.equals("a2727c48839058a5f24688f356904e0d21005a9523e8c99cf7e1c51be26df1e8"))throw new IllegalArgumentException("Exact original fixture differs");
        Files.createDirectory(out);long bytes=0;int files=0;
        try(var zip=new ZipInputStream(Files.newInputStream(fixture))){
            for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()){
                Path p=out.resolve(e.getName()).normalize();
                if(e.isDirectory()||!p.startsWith(out)||e.getName().contains(":"))throw new IllegalArgumentException("Unsafe fixture member");
                byte[] raw=zip.readNBytes(16*1024*1024+1);bytes+=raw.length;
                if(bytes>16*1024*1024||++files>300)throw new IllegalArgumentException("Fixture exceeds scope");
                Files.createDirectories(p.getParent());Files.write(p,raw,StandardOpenOption.CREATE_NEW);
            }
        }
        Map<String,Integer> expected=Map.of("empty",0,"missing_base_qualities",1,"missing_read_group",1,
            "paired_and_fragment",1,"paired_quality",2,"preexisting_flags",2,"quality_tie",1,"single_clipping_libraries_tags",7);
        int audited=0;
        for(var e:expected.entrySet())for(String version:List.of("0.6.6","0.8.2")){
            Path base=out.resolve(e.getKey()),run=base.resolve(version);
            var result=SambambaBamAudit.audit(base.resolve("input.bam"),run.resolve("mark.bam"),run.resolve("remove.bam"),version,
                Files.readString(run.resolve("mark.cl.txt")),Files.readString(run.resolve("remove.cl.txt")),SambambaBamAudit.Limits.defaults());
            if(result.markedDuplicates()!=e.getValue())throw new IllegalStateException("Original duplicate observation differs: "+e.getKey()+version);
            audited+=2;
        }
        Properties proof=new Properties();proof.setProperty("status","PASS_NATIVE_READBACK_OF_32_ORIGINAL_BAM_OUTPUTS");
        proof.setProperty("original_BAM_outputs",Integer.toString(audited));proof.setProperty("java_runtime",System.getProperty("java.runtime.version"));
        proof.setProperty("os",System.getProperty("os.name"));proof.setProperty("fixture_sha256",hash);
        proof.setProperty("fresh_original_analyses_executed","false");proof.setProperty("independent_duplicate_classification","false");
        try(var writer=Files.newBufferedWriter(out.resolve("READBACK.properties"))){proof.store(writer,null);}
        System.out.println(proof.getProperty("status")+" "+proof.getProperty("java_runtime")+" "+proof.getProperty("os"));
    }
}
