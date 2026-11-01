/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

/** Real original-source compilation control; does not admit a study analysis. */
public final class SourceBuildProtocol {
    private SourceBuildProtocol() { }
    /**
     * @param args exact release, verified offline archive, declared private GNU toolchain prefix, NEW build home
     * @throws Exception any source, compiler, resource, ownership or receipt failure
     */
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("RELEASE OFFLINE_ARCHIVE PRIVATE_GNU_TOOLCHAIN_PREFIX NEW_BUILD_HOME");
        Path prefix=Path.of(args[2]),bin=prefix.resolve("bin");
        var tools=new RsemBuild.Tools(bin.resolve("x86_64-conda-linux-gnu-gcc"),bin.resolve("x86_64-conda-linux-gnu-g++"),bin.resolve("make"),
                bin.resolve("x86_64-conda-linux-gnu-ar"),bin.resolve("x86_64-conda-linux-gnu-ranlib"),List.of(prefix),List.of(prefix.resolve("include")),List.of(prefix.resolve("lib")));
        var result=RsemBuild.build(args[0],Path.of(args[1]),null,false,true,tools,Path.of(args[3]),RsemBuild.Limits.defaults(),()->{});
        result.verify(()->{});var report=new LinkedHashMap<String,Object>();report.put("status","passed-exact-original-source-build-provenance");
        report.put("version",result.profile().version());report.put("source",result.source());report.put("decoder",result.decoder());report.put("build_receipt_sha256",result.recordSha256());
        report.put("source_to_build_provenance_verified",true);report.put("scientific_runtime_admitted",false);report.put("public_fitting_workflow_available",false);
        Files.write(Path.of(args[3]).resolve("observed.json"),RsemJson.bytes(report),java.nio.file.StandardOpenOption.CREATE_NEW);System.out.println(RsemJson.json(report));
    }
}
