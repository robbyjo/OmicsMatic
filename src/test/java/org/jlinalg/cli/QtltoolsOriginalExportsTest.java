/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Audits full actual-original scientific exports; executes no original or replacement model. */
class QtltoolsOriginalExportsTest {
    @TempDir Path temp;
    @TestFactory Stream<DynamicTest> completeExportsRetainAllEditionSpecificFieldsAndNullRows()throws Exception {
        String folder="/org/jlinalg/cli/qtltools-workflow/";byte[] archive;
        try(var source=getClass().getResourceAsStream(folder+"original-model-exports.zip")){assertNotNull(source);archive=source.readAllBytes();}
        Map<String,Object> provenance;
        try(var source=getClass().getResourceAsStream(folder+"PROVENANCE.json")){assertNotNull(source);provenance=object(SimpleJson.parse(new String(source.readAllBytes(),StandardCharsets.UTF_8)),"provenance");}
        var expected=object(provenance.get("fixture"),"fixture");assertEquals(expected.get("sha256"),NextflowFiles.sha(archive));assertEquals(((Number)expected.get("bytes")).longValue(),archive.length);assertEquals(64L,((Number)provenance.get("cases")).longValue());assertEquals(108L,((Number)provenance.get("original_files")).longValue());
        var files=new LinkedHashMap<String,byte[]>();long total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(archive))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
            String name=entry.getName();assertFalse(entry.isDirectory());assertFalse(name.contains("..")||name.contains("\\")||name.startsWith("/")||name.contains(":"));byte[] raw=zip.readAllBytes();total+=raw.length;assertTrue(files.size()<1024&&raw.length<16*1024*1024&&total<64*1024*1024);assertNull(files.put(name,raw));
        }}
        var pins=object(provenance.get("members"),"members");assertEquals(pins.keySet(),files.keySet());for(var entry:files.entrySet()){var wanted=object(pins.get(entry.getKey()),"pin");assertEquals(wanted.get("sha256"),NextflowFiles.sha(entry.getValue()));assertEquals(((Number)wanted.get("bytes")).longValue(),entry.getValue().length);}
        var cases=list(SimpleJson.parse(new String(files.get("CASES.json"),StandardCharsets.UTF_8)),"cases");assertEquals(64,cases.size());
        return cases.stream().map(value->{try{
            var row=object(value,"case");String id=text(row,"id");return DynamicTest.dynamicTest(id,()->{
                Path root=temp.resolve(id);Files.createDirectories(root.resolve("outputs"));
                for(var entry:files.entrySet())if(entry.getKey().startsWith(id+"/")){Path target=root.resolve(entry.getKey().substring(id.length()+1));Files.createDirectories(target.getParent());Files.write(target,entry.getValue());}
                var request=QtltoolsWorkflowContracts.validate(read(root.resolve("request.json")));var audited=QtltoolsWorkflowAudit.outputs(root,request);assertEquals(((Number)row.get("complete_files")).longValue(),object(audited.get("exports"),"exports").size());
            });
        }catch(Exception invalid){throw new IllegalStateException(invalid);}});
    }
}
