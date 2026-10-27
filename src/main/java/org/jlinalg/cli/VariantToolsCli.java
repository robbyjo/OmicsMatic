/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

/** Managed software download/setup entry point, also usable by application frontends. */
final class VariantToolsCli {
    private VariantToolsCli() { }
    static int run(String[] args, PrintStream output, PrintStream error) {
        if (args.length == 0 || Arrays.asList(args).contains("--help")) { output.println(help()); return 0; }
        try {
            if (args.length < 2 || !Set.of("install", "status").contains(args[0]) || !Set.of("alphagenome", "annovar").contains(args[1]))
                throw new IllegalArgumentException("Use variant-tools install|status alphagenome|annovar --help");
            var options = FollowupSupport.options(Arrays.copyOfRange(args, 2, args.length), Set.of("home", "python", "downloads", "source-file", "source-url-env", "expected-sha256", "accept-license"));
            String tool = args[1]; Path home = options.containsKey("home") ? Path.of(options.get("home")) : VariantToolRuntime.defaultHome(tool);
            if (args[0].equals("install")) VariantToolRuntime.install(tool, home, options.getOrDefault("python", VariantToolRuntime.defaultPython()), options,
                FollowupSupport.bool(options, "downloads", true), error);
            output.println(StudyAssistance.json(VariantToolRuntime.describe(tool, home))); return 0;
        } catch (Exception failure) { error.println("jlinalg: " + failure.getMessage()); return 2; }
    }
    static String help() { return """
        Managed variant software:
          variant-tools install alphagenome [--home DIRECTORY --python PYTHON]
          variant-tools status alphagenome [--home DIRECTORY]
          variant-tools install annovar --accept-license true
            (--source-url-env ANNOVAR_DOWNLOAD_URL | --source-file annovar.latest.tar.gz)
            [--expected-sha256 SHA256 --home DIRECTORY --python PYTHON]
          variant-tools status annovar [--home DIRECTORY]
        JLinAlg downloads AlphaGenome 0.10.0 from PyPI, verifies its pinned wheel,
        resolves binary dependencies, records their versions/hashes and installs a private
        environment. Python 3.10+ is the bootstrap prerequisite. System packages are unchanged.
        --downloads false permits reuse of an installed home or import of a local ANNOVAR
        archive, with no software network request. Existing or changed homes are retained.
        ANNOVAR requires personal registration and licence acknowledgement at:
          https://www.openbioinformatics.org/annovar/annovar_download_form.php
        Set its issued download link in the named environment variable; the link is not
        written to manifests. JLinAlg does not redistribute ANNOVAR or fabricate registration.
        ANNOVAR Perl/databases and AlphaGenome API credentials are separate from installing
        the software. Installation does not request predictions or submit study data.
        Status is JSON for application frontends. Tool homes contain source hashes and logs.
        """; }
}
