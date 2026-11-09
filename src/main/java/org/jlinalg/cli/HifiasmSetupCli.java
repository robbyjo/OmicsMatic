/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Portable original-source export, or explicit permitted compilation with prerequisite skip handling. */
public final class HifiasmSetupCli {
    private HifiasmSetupCli(){ }
    public static void main(String[] args){System.exit(run(args));}
    public static int run(String[] args){
        try{
            require(args.length==2&&(args[0].equals("--out")||args[0].equals("--build-request")),"Use --out NEW-source-kit or --build-request explicit-source-build.json");
            if(args[0].equals("--build-request")){
                Path request=HifiasmRuntime.regular(Path.of(args[1]),1048576);var spec=GseapyExecutionRuntime.read(request,1048576);
                if(Boolean.TRUE.equals(spec.get("allow_install"))&&System.getProperty("os.name").equals("Linux")){
                    boolean header=List.of("/usr/include/zlib.h","/usr/local/include/zlib.h").stream().map(Path::of).anyMatch(Files::isRegularFile);boolean linker=List.of("/usr/lib/x86_64-linux-gnu/libz.so","/lib/x86_64-linux-gnu/libz.so","/usr/lib64/libz.so","/usr/lib/libz.so","/usr/local/lib/libz.so").stream().map(Path::of).anyMatch(Files::isRegularFile);
                    if(!header||!linker){Path target=Path.of(text(spec.get("out"))).toAbsolutePath().normalize();require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&target.getParent().equals(target.getParent().toRealPath()),"Require NEW setup output under canonical parent");Files.createDirectory(target);String error="Required zlib development header/linker files are unavailable. Install the permitted zlib development package on a compatible host, or retain this skipped setup. Analysis itself installs nothing.";Files.writeString(target.resolve("WARNINGS.md"),"# SKIP3_HIFIASM_BUILD_REQUIREMENTS_UNAVAILABLE\n\n**ACTION REQUIRED: "+error+"**\n\n"+HifiasmRuntime.ACTION+"\n");NextflowFiles.writeJson(target.resolve("COMPLETED.json"),Map.of("status","SKIP3_HIFIASM_BUILD_REQUIREMENTS_UNAVAILABLE","error",error,"analysis_admitted",false,"implementation_credit",false));System.err.println("SKIP3_HIFIASM_BUILD_REQUIREMENTS_UNAVAILABLE: "+error);return 3;}
                }
                return HifiasmSourceBuildCli.run(new String[]{"--request",request.toString()});
            }
            Path out=Path.of(args[1]).toAbsolutePath().normalize();require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS)&&out.getParent().equals(out.getParent().toRealPath()),"Require NEW source-kit output under an existing canonical parent");Files.createDirectory(out);var files=new LinkedHashMap<String,Object>();var profile=HifiasmRuntime.profile();
            for(var entry:object(profile.get("files")).entrySet()){String member=NextflowFiles.member(entry.getKey());try(var input=HifiasmSetupCli.class.getResourceAsStream("/org/jlinalg/omics/hifiasm-source/"+member)){require(input!=null,"Missing packaged original archive");byte[] raw=input.readNBytes(33554433);var expected=NextflowFiles.fingerprint(entry.getValue(),33554432);require(raw.length<=33554432&&expected.equals(new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw))),"Packaged original source archive changed");Path target=out.resolve(member);Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);files.put(member,GseapyExecutionRuntime.pinObject(expected));}}
            try(var input=HifiasmSetupCli.class.getResourceAsStream("/org/jlinalg/omics/hifiasm-source/profiles.json")){require(input!=null,"Missing exact source profile");Files.write(out.resolve("profiles.json"),input.readAllBytes(),StandardOpenOption.CREATE_NEW);}
            Files.writeString(out.resolve("SETUP.md"),"# Original hifiasm source kit\n\nThis export installs nothing and admits no assembly. All six immutable upstream source archives include their original MIT license. No Yak runtime is included.\n\n1. Select an explicit edition from profiles.json. Keep this kit unchanged.\n2. On permitted Linux x86_64, provide Java17+, make, GCC/G++ and zlib development files. Use a compatible host; do not upgrade shared libraries underneath existing bound analyses. If prerequisites or installation permission are unavailable, skip dependent analysis.\n3. Extract the selected original/<edition>.tar.gz into a NEW canonical source directory, removing its single archive root component. Do not edit source members.\n4. Supply a source-build request with schema_version1, mode BUILD_REFERENCE_ONLY, allow_install true, exact edition, source, NEW out, and compiler_threads1..4. Run HifiasmSetupCli --build-request request.json. Compilation is local and owned; no OS packages are downloaded or installed.\n5. Require a successful source-build receipt. Bind it with HifiasmRuntimeCli using schema_version1, mode BIND_REFERENCE_ONLY, build_receipt and NEW out.\n6. Run HifiasmWorkflowCli only with the admitted bound runtime, explicit reads, capacities and parameters. Require exit0 AND COMPLETED.json analysis_admitted=true for the entire batch. Read WARNINGS.md and retain all inputs/process logs.\n\nFor copyable requests and exact limitations, read docs/literature/HIFIASM.md in the matching project checkout.\n\n"+HifiasmRuntime.ACTION+"\n");NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","PASS_EXPORTED_ORIGINAL_HIFIASM_SOURCE_KIT_ONLY","archives",files,"analysis_admitted",false,"implementation_credit",false,"software_installed",false));System.out.println("Exported original hifiasm source kit only: "+out);return 0;
        }catch(Exception failure){System.err.println("FAIL2_HIFIASM_SETUP: "+failure+"\n"+HifiasmRuntime.ACTION);return 2;}
    }
}
