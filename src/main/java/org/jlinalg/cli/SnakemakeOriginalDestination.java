/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Reject output overlap before creating anything in declared inputs/software. */
final class SnakemakeOriginalDestination {
    private SnakemakeOriginalDestination() { }
    static void check(Path output,SnakemakeOriginalRecipe.Request request,Path runtime)throws IOException {
        for(String path:request.sources().values())protect(output,Path.of(path));
        for(var tool:request.tools())protect(output,Path.of(tool.executable()));
        protect(output,runtime.toAbsolutePath().normalize());
        Path ready=runtime.resolve("READY.json");
        if(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))protect(output,Path.of(text(object(json(ready,4_194_304)).get("environment"))));
        if(request.managed()!=null){
            var managed=request.managed();Path providers=Path.of(managed.providers());protect(output,providers);
            if(managed.environments()!=null)protect(output,Path.of(managed.environments()));
            if(managed.images()!=null)protect(output,Path.of(managed.images()));
            Path adoption=providers.resolve("ADOPTED.json");
            if(Files.isRegularFile(adoption,LinkOption.NOFOLLOW_LINKS))for(Object component:object(object(json(adoption,4_194_304)).get("components")).values())protect(output,Path.of(text(object(component).get("prefix"))));
        }
    }
    static void protect(Path output,Path declared)throws IOException {
        Path path=declared.toAbsolutePath().normalize();if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))path=path.toRealPath();
        if(output.startsWith(path)||path.startsWith(output))throw new IOException("NEW Snakemake output overlaps a protected input/software/bundle path: "+path+". Choose a separate output location before retrying; the protected path was not modified.");
    }
}
