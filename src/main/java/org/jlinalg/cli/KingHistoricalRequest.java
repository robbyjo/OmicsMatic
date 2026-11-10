/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.KingRequest.need;

/** Exact caller-selected legacy version; scientific options are never inferred from a runtime path. */
final class KingHistoricalRequest {
    private KingHistoricalRequest() { }
    record Request(String version,KingRequest.Request base) {
        String mode(){return base.mode();}
        int degree(){return base.degree();}
        int threads(){return base.threads();}
        Path bfile(){return base.bfile();}
        Path profile(){return base.profile();}
        Path out(){return base.out();}
        KingRequest.Limits limits(){return base.limits();}
    }
    static Request parse(Object value,Path base) throws IOException {
        Map<?,?> raw=object(value);String version=text(raw.get("version"));
        need(Set.of("2.0","2.2.7").contains(version),"Historical KING requires explicit version2.0 or2.2.7");
        var rest=new LinkedHashMap<Object,Object>(raw);rest.remove("version");
        KingRequest.Request request=KingRequest.parse(rest,base);
        need(Set.of("kinship","duplicate").contains(request.mode())&&request.degree()==0,
                "Historical profile supports complete --kinship or --duplicate only, degree0; no filter/selection/method substitution");
        return new Request(version,request);
    }
}
