/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Compare the production native tree-scan helper to actual compiled original SciPy controls. */
public final class ScfatesRootScanMatrix {
    private ScfatesRootScanMatrix() { }
    public static void main(String[] args)throws Exception {
        require(args.length==2,"ORIGINAL_COMPLETED NEW_REPORT");Path source=Path.of(args[0]).toAbsolutePath().normalize(),out=Path.of(args[1]).toAbsolutePath().normalize();require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS),"New report only");var original=ScfatesWorkflowRuntime.read(source,16777216);require(text(original.get("status")).equals("PASS_31_COMPILED_SCIPY_POSITIVE_TREE_SCANS_ONLY")&&Boolean.TRUE.equals(original.get("all_runtime_trees_unchanged")),"Complete original tree-scan family required");var meter=new ScfatesWorkflowAudit.Meter(100000000);long numeric=0;int cases=0;for(Object raw:list(original.get("cases"))){var c=object(raw);var rows=list(c.get("weights"));int k=rows.size();require(k>=2&&k<=64,"Bounded complete control tree required");double[][] weights=new double[k][k];int[][] b=new int[k][k];for(int i=0;i<k;i++){var values=list(rows.get(i));require(values.size()==k,"Complete control weight shape required");for(int j=0;j<k;j++){weights[i][j]=finite(values.get(j),0,Double.MAX_VALUE);b[i][j]=weights[i][j]>0?1:0;}}var bits=list(c.get("distance_bits"));require(bits.size()==k,"Complete original all-root family required");for(int root=0;root<k;root++){var expected=list(bits.get(root));require(expected.size()==k,"Complete original distance row required");double[] actual=ScfatesWorkflowAudit.treeTimes(b,weights,root,meter);for(int j=0;j<k;j++){String hex=text(expected.get(j));require(hex.matches("[0-9a-f]{16}")&&Double.doubleToRawLongBits(actual[j])==Long.parseUnsignedLong(hex,16),"Original positive-tree path bits differ: "+text(c.get("id")));numeric++;}}cases++;}require(cases==31&&numeric==integer(original.get("numeric_distance_states"),1,1000000),"Incomplete original tree-scan family");NextflowFiles.writeJson(out,Map.of("status","PASS_NATIVE_SCFATES_TREE_SCAN_EXACT_COMPILED_ORIGINAL_ONLY","cases",cases,"numeric_states",numeric,"general_asymmetric_graph_support",false));
    }
}
