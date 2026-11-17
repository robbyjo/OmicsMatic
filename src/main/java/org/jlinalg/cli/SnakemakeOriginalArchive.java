/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.ZipFile;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Scoped archive-byte namespace; no extraction and no interpretation of literal payload links. */
final class SnakemakeOriginalArchive {
    @FunctionalInterface interface Read<T> {T apply(Path root)throws IOException;}
    private SnakemakeOriginalArchive() { }
    static <T> T read(Path supplied,Read<T> action,Runnable check)throws IOException {
        Path source=supplied.toAbsolutePath().normalize().toRealPath();
        if(Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS))return action.apply(source);
        Pin before=pin(source,1_000_000_000_000L,check);var names=new HashSet<String>();
        try(var zip=new ZipFile(source.toFile())){
            var entries=zip.entries();
            while(entries.hasMoreElements()){
                check.run();var entry=entries.nextElement();String name=entry.getName();if(entry.isDirectory())name=name.substring(0,name.length()-1);member(name);
                if(!names.add(name)||names.size()>1_000_000||entry.getSize()<0||entry.isDirectory()&&entry.getSize()!=0)throw new IOException("Duplicate, conflicting, nonempty directory or excessive standalone ZIP member");
            }
        }
        T result=null;Exception failure=null;
        try(var archive=FileSystems.newFileSystem(source,Map.of())){result=action.apply(archive.getPath("/"));}
        catch(IOException|RuntimeException error){failure=error;}
        try{if(!before.equals(pin(source,1_000_000_000_000L,check)))throw new IOException("Standalone workflow archive changed during complete verification/replay");}
        catch(IOException|RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        if(failure instanceof IOException io)throw io;if(failure instanceof RuntimeException runtime)throw runtime;return result;
    }
}
