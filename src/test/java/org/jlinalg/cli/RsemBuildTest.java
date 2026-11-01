package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class RsemBuildTest {
    @TempDir Path root;
    private Path archive(String name,byte[] bytes)throws Exception{
        Path archive=root.resolve("source-"+java.util.UUID.randomUUID()+".tar.gz");try(var gzip=new GZIPOutputStream(Files.newOutputStream(archive));var tar=new TarArchiveOutputStream(gzip)){
            var entry=new TarArchiveEntry(name);entry.setMode(0644);entry.setSize(bytes.length);tar.putArchiveEntry(entry);tar.write(bytes);tar.closeArchiveEntry();
        }return archive;
    }
    @Test @EnabledOnOs(OS.LINUX) void original_source_extraction_preserves_every_byte_and_does_not_overwrite_a_prior_tree()throws Exception{
        byte[] code="original scientific source\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);Path source=archive("RSEM-test/source.cpp",code),home=Files.createDirectory(root.resolve("home"));
        Map<String,RsemBuild.SourceFile> actual=RsemBuild.extract(source,home,"RSEM-test",()->{});assertEquals(1,actual.size());assertArrayEquals(code,Files.readAllBytes(home.resolve("RSEM-test/source.cpp")));
        assertEquals(AdmixtureInputs.digest(code),actual.get("source.cpp").sha256());assertThrows(java.nio.file.FileAlreadyExistsException.class,()->RsemBuild.extract(source,home,"RSEM-test",()->{}));
    }
    @Test @EnabledOnOs(OS.LINUX) void unsafe_member_names_are_refused_before_any_outside_write()throws Exception{
        for(String name:List.of("../escape","RSEM-test/../escape","RSEM-test//duplicate","wrong-prefix/file","RSEM-test/back\\slash")){
            Path source=archive(name,new byte[]{1});Path home=Files.createDirectory(root.resolve("bad-"+java.util.UUID.randomUUID()));assertThrows(IOException.class,()->RsemBuild.extract(source,home,"RSEM-test",()->{}));assertFalse(Files.exists(root.resolve("escape")));
        }
    }
    @Test @EnabledOnOs(OS.LINUX) void source_archives_cannot_supply_precompiled_elf_programs()throws Exception{
        Path source=archive("RSEM-test/program",new byte[]{127,'E','L','F'}),home=Files.createDirectory(root.resolve("precompiled"));assertThrows(IOException.class,()->RsemBuild.extract(source,home,"RSEM-test",()->{}));
    }
    @Test void include_search_retains_reported_missing_and_ordered_active_directories()throws Exception{
        String data="ignoring nonexistent directory \"/sdk/missing\"\n#include \"...\" search starts here:\n#include <...> search starts here:\n /sdk/include\n /sdk/sysroot/include\nEnd of search list.\n";
        assertEquals(List.of("/sdk/missing","/sdk/include","/sdk/sysroot/include"),RsemBuild.includeSearch(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(String bad:List.of(data.strip(),data.replace("End of search list.\n",""),data.replace(" /sdk/include"," relative"),data+data))assertThrows(IOException.class,()->RsemBuild.includeSearch(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void complete_library_search_population_requires_one_terminated_nonempty_declaration()throws Exception{
        String data="install: /sdk/lib/gcc/\nprograms: =/sdk/bin/\nlibraries: =/sdk/lib/:/sdk/sysroot/lib/\n";
        assertEquals(List.of("/sdk/lib/","/sdk/sysroot/lib/"),RsemBuild.librarySearch(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(String bad:List.of(data.strip(),data+data,data.replace("/sdk/lib/:",":"),"programs: =/sdk/bin\n"))assertThrows(IOException.class,()->RsemBuild.librarySearch(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void forbidden_installation_never_creates_a_home_or_invokes_a_tool()throws Exception{
        Path home=root.resolve("forbidden");assertThrows(RsemProcess.UnavailableDependency.class,()->RsemBuild.build("1.3.3",null,null,false,false,null,home,RsemBuild.Limits.defaults(),()->{}));assertFalse(Files.exists(home));
    }
}
