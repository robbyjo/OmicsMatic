/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Strict data-only prepared JSON; literal identities and requested methods remain explicit. */
final class GseapyWorkflowInputs {
    private GseapyWorkflowInputs(){ }
    record Prepared(Map<String,Object> raw,int rows,int columns,long memberships,long matrixValues){ }
    static Prepared read(Path captured,Map<String,Object> analysis,GseapyWorkflowRequest.Request request)throws IOException{
        var raw=GseapyWorkflowRuntime.read(captured,Math.toIntExact(Math.min(request.inputBytes(),1000000000)));String method=text(analysis.get("method"));integer(raw.get("schema_version"),1,1);require(text(raw.get("numeric_dtype")).equals("float64"),"Declare finite float64 numeric input; no dtype inference");
        if(method.equals("enrichr"))GseapyWorkflowRequest.allowed(raw,"schema_version","numeric_dtype","gene_sets","query","background");
        else if(method.equals("gsea"))GseapyWorkflowRequest.allowed(raw,"schema_version","numeric_dtype","gene_sets","ids","columns","values","classes");
        else GseapyWorkflowRequest.allowed(raw,"schema_version","numeric_dtype","gene_sets","ids","columns","values");
        var sets=object(raw.get("gene_sets"));require(!sets.isEmpty(),"Require explicit nonempty local gene sets; remote library names and downloads are not accepted by RUN");long membership=0;
        for(var entry:sets.entrySet()){identity(entry.getKey());var genes=list(entry.getValue());membership=Math.addExact(membership,genes.size());require(membership<=request.maximumMatrixCells(),"Complete gene-set membership capacity exceeded");for(Object gene:genes)identity(gene);}
        if(method.equals("enrichr")){
            var query=list(raw.get("query"));require(query.size()<=request.maximumMatrixCells(),"Complete query capacity exceeded");for(Object gene:query)identity(gene);Object background=raw.get("background");require(raw.containsKey("background"),"Declare a numeric population or explicit background list, including deliberate null for source library-union behavior");
            if(background instanceof List<?>){var genes=list(background);require(genes.size()<=request.maximumMatrixCells(),"Complete background capacity exceeded");for(Object gene:genes)identity(gene);}else if(background!=null)integer(background,0,Integer.MAX_VALUE);
            return new Prepared(raw,query.size(),0,membership,0);
        }
        var ids=list(raw.get("ids"));var columns=list(raw.get("columns"));var values=list(raw.get("values"));require(!ids.isEmpty()&&!columns.isEmpty()&&values.size()==ids.size()&&(long)ids.size()*columns.size()<=request.maximumMatrixCells(),"Require complete nonempty gene-by-sample float64 matrix within the declared capacity");for(Object id:ids)identity(id);var uniqueColumns=new HashSet<String>();for(Object column:columns)require(uniqueColumns.add(identity(column)),"Duplicate sample/score columns are outside the declared unambiguous profile");
        for(Object row:values){var entries=list(row);require(entries.size()==columns.size(),"Ragged source matrix");for(Object v:entries){double d=finite(v,-Double.MAX_VALUE,Double.MAX_VALUE);if(d==0&&v instanceof NextflowParameters.NumberToken token)try{require(new BigDecimal(token.text()).signum()==0,"Nonzero JSON numeric value underflows float64; declare a supported input without silently replacing values");}catch(NumberFormatException error){throw new IOException("Invalid/excessive numeric exponent",error);}}}
        if(method.equals("prerank"))require(columns.size()==1,"Declare a single explicit preranked statistic; separate rankings are separate cases");
        if(method.equals("gsea")){var classes=list(raw.get("classes"));require(classes.size()==columns.size(),"Phenotype classes must match exact sample-column order");var levels=new HashSet<String>();for(Object label:classes)levels.add(identity(label));require(levels.size()==2,"Declare exactly two phenotype classes; no implicit sample exclusion");}
        return new Prepared(raw,ids.size(),columns.size(),membership,(long)ids.size()*columns.size());
    }
    private static String identity(Object raw)throws IOException{
        String value=text(raw);for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){require(i+1<value.length()&&Character.isLowSurrogate(value.charAt(i+1)),"Unpaired surrogate in literal data identity");i++;}else require(!Character.isLowSurrogate(c),"Unpaired surrogate in literal data identity");}return value;
    }
}
