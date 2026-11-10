/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Exact original author-distributed historical executables, with distinct Linux library populations. */
final class KingHistoricalProfiles {
    private KingHistoricalProfiles() { }
    record Profile(String id,String version,String platform,Pin executable,Set<String> libraries) { }
    private static final Set<String> BASE=Set.of("libz.so.1","libstdc++.so.6","libm.so.6","libgomp.so.1","libgcc_s.so.1","libc.so.6","ld-linux-x86-64.so.2");
    private static Set<String> with(String... names) {
        var result=new HashSet<String>(BASE);result.addAll(java.util.List.of(names));return Set.copyOf(result);
    }
    static Profile get(String id) throws IOException {
        return switch(id) {
            case "2.2.7-windows" -> new Profile(id,"2.2.7","windows",new Pin(1088512,"bb536db9d357af51d92c626c981f235f9a6b03f5bbf325ad070122b3d1b0b483"),Set.of());
            case "2.2.7-linux" -> new Profile(id,"2.2.7","linux",new Pin(2504176,"823b9bd59735edb28305ab574dc4bfbcfaac6fd236d9137cb079e35a5974da87"),with("libquadmath.so.0","libpthread.so.0"));
            case "2.0-linux" -> new Profile(id,"2.0","linux",new Pin(1270425,"76cf4742f335a6071fe3a068bf2a250017b9cac38322ecc604e8a7f14e605f0c"),with("libpthread.so.0"));
            case "2.0-ubuntu" -> new Profile(id,"2.0","linux",new Pin(1280640,"4a54137db879d0721d0e7f39163ef850245050598d3e1c9770df4c23e5c51dbd"),BASE);
            default -> throw new IOException("Unknown exact historical KING profile");
        };
    }
}
