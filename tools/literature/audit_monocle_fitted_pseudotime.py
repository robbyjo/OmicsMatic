# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the native API with a graph fitted and saved by the actual Monocle3 package."""
import argparse
import csv
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir(parents=True)
    base=ROOT/'build/literature-review/monocle-runtime';probe=base/'current-readiness-probe-2'
    result=json.loads((probe/'RESULT.json').read_bytes());summary=json.loads((probe/'summary.json').read_bytes())
    assert result['status']=='COMPLETED_ORIGINAL_MONOCLE3_SYNTHETIC_READINESS_PROBE'
    assert (summary['genes'],summary['cells'],summary['models'],summary['coefficient_rows'],summary['graph_test_rows'])==(80,160,80,240,80)
    with (probe/'coefficients.tsv').open(encoding='utf8',newline='') as f:coefficients=list(csv.DictReader(f,delimiter='\t'))
    # The retained R write.table(row.names=TRUE, col.names=TRUE) format puts
    # source row names in an extra, unheaded first column. Read that declared
    # R convention explicitly; never shift scientific columns silently.
    with (probe/'graph-test.tsv').open(encoding='utf8',newline='') as f:graph_rows=list(csv.reader(f,delimiter='\t'))
    assert all(len(r)==len(graph_rows[0])+1 for r in graph_rows[1:])
    tests=[dict(zip(['source_row_name',*graph_rows[0]],row)) for row in graph_rows[1:]]
    assert len(coefficients)==240 and {r['status'] for r in coefficients}=={'OK'}
    assert len(tests)==80 and {r['status'] for r in tests}=={'OK'}
    setup=base/'reference-setup-2';libraries=[str(setup/'R-lib'),*json.loads((setup/'STARTED.json').read_bytes())['libraries']]
    worker=out/'export.R'
    worker.write_text('''a<-commandArgs(TRUE);out<-a[1];saved<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
stopifnot(identical(as.character(packageVersion("monocle3")),"1.4.27"))
cds<-monocle3::load_monocle_objects(saved)
aux<-cds@principal_graph_aux[["UMAP"]];coords<-SingleCellExperiment::reducedDims(cds)[["UMAP"]]
principal<-aux$dp_mst;roots<-aux$root_pr_nodes;graph<-aux$pr_graph_cell_proj_tree
vertices<-igraph::V(graph)$name;edges<-igraph::as_data_frame(graph,what="edges")
has_weights<-"weight" %in% colnames(edges)
if(!has_weights)edges$weight<-rep(1,nrow(edges))
rows<-function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
tokens<-function(x)as.list(vapply(as.numeric(x),function(v)if(is.infinite(v))"Inf" else sprintf("%.17g",v),character(1)))
original<-get("extract_general_graph_ordering",asNamespace("monocle3"))(cds,roots,verbose=FALSE,reduction_method="UMAP")
selected<-get("find_nearest_vertex",asNamespace("monocle3"))(principal[,roots,drop=FALSE],t(coords))
record<-list(cell_ids=as.list(unname(colnames(cds))),cell_coordinates=rows(coords),principal_ids=as.list(unname(colnames(principal))),
 principal_coordinates=rows(t(principal)),roots=as.list(unname(roots)),graph_vertex_ids=as.list(unname(vertices)),
 edges=lapply(seq_len(nrow(edges)),function(i)list(from=match(edges$from[i],vertices)-1L,to=match(edges$to[i],vertices)-1L,weight=edges$weight[i])),
 expected_cell_pseudotime=tokens(monocle3::pseudotime(cds)),expected_graph_pseudotime=tokens(original$pseudo_time),
 expected_root_indices=as.list(as.integer(selected)-1L),source_had_weight_attribute=has_weights)
stopifnot(identical(as.character(original$sample_name),vertices),all(is.finite(coords)),all(is.finite(principal)))
jsonlite::write_json(record,file.path(out,"prepared-graph.json"),auto_unbox=TRUE,pretty=TRUE,digits=17)
base::saveRDS(list(cds=cds,original=original,selected=selected),file.path(out,"original-graph-values.rds"))
''',encoding='utf8',newline='\n')
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(worker),str(out),str(probe/'ordered'),*libraries]
    r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en'),capture_output=True,timeout=120)
    (out/'export.stdout').write_bytes(r.stdout);(out/'export.stderr').write_bytes(r.stderr);save(out/'export.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    source=out/'CompareFittedMonocle.java'
    source.write_text('''package org.jlinalg.cli;
import java.nio.file.*;import java.util.*;import org.jlinalg.singlecell.Monocle3Pseudotime;
public final class CompareFittedMonocle {
 static double value(Object x){return Double.parseDouble(((NextflowParameters.NumberToken)x).text());}
 static double[][] matrix(Object x)throws Exception{var a=NextflowFiles.list(x);double[][] out=new double[a.size()][];for(int i=0;i<a.size();i++){var row=NextflowFiles.list(a.get(i));out[i]=new double[row.size()];for(int j=0;j<row.size();j++)out[i][j]=value(row.get(j));}return out;}
 static double compare(Object tokens,double[] actual)throws Exception{var expected=NextflowFiles.strings(tokens);if(expected.size()!=actual.length)throw new AssertionError("Wrong population");double max=0;for(int i=0;i<actual.length;i++){double e=expected.get(i).equals("Inf")?Double.POSITIVE_INFINITY:Double.parseDouble(expected.get(i));if(Double.isInfinite(e)){if(e!=actual[i])throw new AssertionError("Missing Inf");}else{double error=Math.abs(e-actual[i]);if(!Double.isFinite(actual[i])||error>5e-12*Math.max(1,Math.abs(e)))throw new AssertionError("Original fitted graph distance mismatch at "+i+": "+e+" vs "+actual[i]);max=Math.max(max,error);}}return max;}
 public static void main(String[] args)throws Exception{
  if(!Monocle3Pseudotime.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new java.io.File(args[0]).toURI()))throw new AssertionError("Wrong candidate JAR");
  var m=NextflowFiles.object(NextflowParameters.parse(Files.readString(Path.of(args[1])),8388608,1000000));var edges=new ArrayList<Monocle3Pseudotime.Edge>();
  for(Object raw:NextflowFiles.list(m.get("edges"))){var e=NextflowFiles.object(raw);edges.add(new Monocle3Pseudotime.Edge((int)value(e.get("from")),(int)value(e.get("to")),value(e.get("weight"))));}
  var result=Monocle3Pseudotime.order(NextflowFiles.strings(m.get("cell_ids")),matrix(m.get("cell_coordinates")),NextflowFiles.strings(m.get("principal_ids")),matrix(m.get("principal_coordinates")),NextflowFiles.strings(m.get("roots")),NextflowFiles.strings(m.get("graph_vertex_ids")),edges);
  var roots=NextflowFiles.list(m.get("expected_root_indices"));if(roots.size()!=result.selectedRoots().size())throw new AssertionError("Root count");for(int i=0;i<roots.size();i++)if((int)value(roots.get(i))!=result.selectedRoots().get(i).zeroBasedCellIndex())throw new AssertionError("Root selection");
  double cellError=compare(m.get("expected_cell_pseudotime"),result.pseudotime()),graphError=compare(m.get("expected_graph_pseudotime"),result.graphPseudotime());
  System.out.println("{\\"cells\\":"+result.cellIds().size()+",\\"graph_vertices\\":"+result.graphVertexIds().size()+",\\"cell_max_absolute_error\\":"+cellError+",\\"graph_max_absolute_error\\":"+graphError+"}");
 }
}
''',encoding='utf8',newline='\n')
    artifact=json.loads((base/'native-build-1/COMPLETED.json').read_bytes())['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    fixture=out/'prepared-graph.json';runs=[]
    commands={'windows-java21':[str(JAVA21/'java.exe'),'-cp',str(jar)+os.pathsep+str(classes),'org.jlinalg.cli.CompareFittedMonocle',str(jar),str(fixture)],
        'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-cp',str(jar)+os.pathsep+str(classes),'org.jlinalg.cli.CompareFittedMonocle',str(jar),str(fixture)],
        'linux-java21':['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-cp',linux(jar)+':'+linux(classes),'org.jlinalg.cli.CompareFittedMonocle',linux(jar),linux(fixture)]}
    for name,command in commands.items():
        r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
        assert r.returncode==0,r.stderr.decode('utf8',errors='replace');row=dict(JVM=name,command=command,exit_code=r.returncode,**json.loads(r.stdout));runs.append(row);print(json.dumps(row),flush=True)
    save(out/'COMPLETED.json',dict(status='matched-actual-original-fitted-graph-pseudotime-on-three-JVMs',artifact=artifact,
        source_pin=pin(Path(__file__)),original_summary=summary,prepared_fixture=pin(fixture),JVMs=runs,
        original_coefficient_rows_status_OK=240,original_graph_test_rows_status_OK=80,original_graph_table_R_row_name_convention=True,original_warnings=result['warnings'],
        Java_original_workflow_adapter_validated=False,implementation_credit=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original fitted graph or artifact checks')
    main()
