/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Declared programs/providers for RSEM execution or its original-source compilation. */
interface RsemProvider {
    Path home();
    String profile();
    String lockSha256();
    List<Path> libraryDirectories();
    List<Path> providerRoots();
    List<Path> executablePaths();
    Map<String,String> environment(Path ownedWork)throws IOException;
    void verify(Runnable check)throws IOException;
}
