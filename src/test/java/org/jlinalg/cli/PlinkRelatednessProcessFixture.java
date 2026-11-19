/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded child-process fixture. It never reads research data or starts PLINK. */
public final class PlinkRelatednessProcessFixture {
    private PlinkRelatednessProcessFixture() { }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("parent")) {
            String executable = ProcessHandle.current().info().command().orElseThrow();
            Process child = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                    PlinkRelatednessProcessFixture.class.getName(), "leaf").start();
            Files.writeString(Path.of(args[1]), Long.toString(child.pid()));
        } else if (args.length != 1 || !args[0].equals("leaf")) {
            throw new IllegalArgumentException("Require parent READY_FILE or leaf");
        }
        Thread.sleep(120_000); // Watchdog bounds even a failed test's leftover fixture.
    }
}
