/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/** Standalone pinned source setup export; installation and download require independent explicit flags. */
final class TomtomSetupExport {
    private TomtomSetupExport() { }
    static void export(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();TomtomBundle.create(root);
        try{
            Files.createDirectories(root.resolve("reference-builder/tools/literature"));var files=new TreeMap<String,Object>();
            capture(root,"setup_tomtom.py","setup_tomtom.py",new NextflowFiles.Pin(11255,"5734bebb0c59404020fb5943b284507f933d16981ec57553ac9080a87b311a54"),files);
            capture(root,"build_meme_linux_reference.py","reference-builder/tools/literature/build_meme_linux_reference.py",new NextflowFiles.Pin(7305,"7c791f202b66d754c5e33a842b7772af965485140560f8dbe0aaba8d81ec3fa6"),files);
            capture(root,"UPSTREAM-COPYING","UPSTREAM-COPYING",new NextflowFiles.Pin(1917,"ecf04a70ce5a5f5232d5e279af6f85ffca10d61b35d158ffb68b627c98bba373"),files);
            TomtomBundle.write(root.resolve("KIT-SOURCES.json"),files);
            String guide="""
                Tomtom original5.5.9 source setup — no analysis is performed by this kit

                Read UPSTREAM-COPYING and setup_tomtom.py --help. This kit is for the
                confirmed educational/research/nonprofit use; it supplies no commercial
                permission. On permitted Linux x86_64 Python3.12+ (Linux Python inside
                WSL on Windows), use canonical ASCII kit/report paths without spaces:

                python3 -I -B ./setup_tomtom.py --report /var/tmp/tomtom-setup-NEW --archive /ABS/meme-5.5.9.tar.gz --allow-build --research-nonprofit

                With separately permitted network acquisition, omit --archive and add
                --allow-download. The exact official56,535,171-byte archive must have
                SHA2560406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12.
                Every download/partial, original build log, source/native area and report
                is retained. No package manager, elevation or automatic dependency install
                is attempted. The existing host needs gcc, g++, make, Perl, zlib development
                headers/libraries and Python. FULL_REPORT also requires XML::Parser::Expat
                and the original installed Perl helpers. If a needed dependency cannot be
                installed where permitted, leave the dependent analysis skipped (SKIP3).

                Require exit0 and report/COMPLETED.json. Read report/WARNINGS.md, both
                builder logs, BUILD-LOCATION.json and every original build-evidence log.
                The completion prints home and build_evidence. Supply those exact paths:

                java -jar /ABS/omicsmatic.jar tomtom-tools --recipe /ABS/request.json --home EXACT_HOME --build-evidence EXACT_BUILD_EVIDENCE --out /var/tmp/tomtom-run-NEW

                Keep the kit, native build area and compiled home at their recorded paths.
                Original binaries embed their prefix; a copied home is not relocatable.
                Setup proves unchanged original615 C/H files and successful original build
                and version probes. The analysis command independently checks the complete
                current-host runtime and fresh fixed references, then the entire user fit.
                Setup completion alone admits no analysis and certifies no biology.

                Cancellation/TERM cleans up owned descendants including detached original
                build sessions. Do not forcibly kill the supervisor or power off the host;
                if that occurs, retain OWNER/BUILD-LOCATION and inspect only its owned
                workers/native area before recovery. Never launch a duplicate setup.
                SKIP3: required permission/dependency unavailable; skip dependent analysis.
                FAIL2: input/hash/build/capacity/cancellation/cleanup failure; no admitted
                setup. Preserve partials, correct the named cause and use NEW paths.
                """;
            Files.writeString(root.resolve("RUN-SETUP.txt"),guide,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);TomtomBundle.write(root.resolve("export.json"),Map.of("schema_version",1,"status","EXPORTED_SOURCE_SETUP_NOT_RUN","files",files,"analysis_admitted",false));
        }catch(IOException|RuntimeException failure){try{Files.writeString(root.resolve("FAILURE.txt"),"FAIL2 — setup export incomplete; no build or analysis was run.\n"+failure+"\nRequired action: retain this export, correct the named cause and export into a NEW directory.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;}
    }
    private static void capture(Path root,String resource,String member,NextflowFiles.Pin expected,Map<String,Object> files)throws IOException {byte[] raw=MemeRuntimeFiles.resource(MemeRuntimeFiles.RESOURCE+resource,expected);Files.write(root.resolve(member),raw,StandardOpenOption.CREATE_NEW);files.put(member,MemeRuntimeFiles.pin(expected));}
}
