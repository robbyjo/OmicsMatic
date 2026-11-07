# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the exact original current Monocle3 sources and declared R profile without installation."""
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, pin, save


def main():
    base=ROOT/'build/literature-review/monocle-runtime'
    out=base/'workflow-profile-1'
    assert not out.exists();out.mkdir()
    setup=base/'reference-setup-3'
    libraries=[str(setup/'R-lib'),*json.loads((setup/'STARTED.json').read_bytes())['libraries']]
    source=base/'source-intake-1/1.4.27/source'
    original=json.loads((base/'source-intake-1/1.4.27/SOURCE.json').read_bytes())
    resource=ROOT/'src/main/resources/org/jlinalg/omics/monocle3-workflow'
    assert not resource.exists();resource.mkdir(parents=True)
    paths=[source/n for n in ['DESCRIPTION','LICENSE','LICENSE.md']]+sorted((source/'R').glob('*.R'))
    files={}
    for path in paths:
        name='1.4.27/'+path.relative_to(source).as_posix()
        target=resource/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes());files[name]=pin(target)
    worker=out/'profile.R'
    worker.write_text('''a<-commandArgs(TRUE);.libPaths(c(a[-1],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
packages<-c("monocle3","BPCells","jsonlite","Matrix","SingleCellExperiment","SummarizedExperiment","S4Vectors","BiocGenerics","BiocParallel","igraph","uwot","RcppAnnoy","RcppHNSW","leidenbase","batchelor","DelayedArray","DelayedMatrixStats","irlba","proxy","MASS","lme4","pscl","speedglm","dplyr","tibble","tidyr","purrr","RhpcBLASctl")
versions<-setNames(lapply(packages,function(n)as.character(packageVersion(n))),packages)
jsonlite::write_json(list(r_version=as.character(getRversion()),platform=R.version$platform,required_packages=versions),a[1],pretty=TRUE,auto_unbox=TRUE)
''',encoding='utf8',newline='\n')
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(worker),str(out/'profile.json'),*libraries]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60)
    (out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr)
    assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    profile=json.loads((out/'profile.json').read_bytes())
    profile.update(schema_version=1,version='1.4.27',official_repository=original['official_repository'],commit=original['commit'],files=files,
        guarded_functions=['new_cell_data_set','estimate_size_factors','detect_genes','preprocess_cds','normalize_expr_data','reduce_dimension',
            'cluster_cells','learn_graph','order_cells','find_nearest_vertex','extract_general_graph_ordering',
            'fit_models','fit_model_helper','smart_es_apply','extract_model_status_helper','extract_coefficient_helper',
            'coefficient_table','graph_test','save_monocle_objects','load_monocle_objects'],
        authentication_limit='Exact package versions and selected original R function bodies/formals; not whole-library or native-DLL source authentication',
        declared_author_forks={'speedglm':'cole-trapnell-lab/speedglm 0.3-4','BPCells':'bnprks.r-universe.dev 0.3.1 Windows binary'},
        historical_editions_unavailable=['1.0.0','1.3.1','1.3.3','1.3.7'])
    save(resource/'SOURCE-PROFILE.json',profile)
    (resource/'NOTICE.txt').write_text('Original Monocle3 1.4.27 R sources, DESCRIPTION and MIT notices are retained byte for byte.\nRepository: https://github.com/cole-trapnell-lab/monocle3\nCommit: '+profile['commit']+'\nThe separate worker is GPL-3.0-or-later project integration code.\nNo historical edition, modified paper-specific graph_test or whole-paper reproduction is claimed.\n',encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',dict(command=command,source_pin=pin(Path(__file__)),resource_pins={p.relative_to(resource).as_posix():pin(p) for p in resource.rglob('*') if p.is_file()},installed_nothing=True))
    print('Captured',len(files),'original members and exact R dependency profile')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source checks')
    main()
