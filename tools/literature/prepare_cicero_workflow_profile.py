# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture unchanged Cicero/Monocle3 R sources and the original validated Windows runtime profile."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    reference=json.loads((base/'cicero-readiness-probe-2/RESULT.json').read_bytes());assert reference['status']=='COMPLETED_ORIGINAL_CICERO_COACCESSIBILITY_CONTROL' and reference['libraries_unchanged'];setup=base/'cicero-reference-setup-1';libraries=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]]
    resource=ROOT/'src/main/resources/org/jlinalg/omics/cicero-workflow';assert not resource.exists();resource.mkdir(parents=True);members={}
    for package,folder in [('cicero',base/'related-source-intake-1/cicero-m3'),('monocle3',base/'source-intake-1/1.4.27')]:
        receipt=json.loads((folder/'SOURCE.json').read_bytes());assert all(pin(folder/'source'/n)==v for n,v in receipt['source_members'].items())
        for path in sorted((folder/'source').rglob('*')):
            if not path.is_file():continue
            relative=path.relative_to(folder/'source')
            if relative.parts[0]!='R' and relative.as_posix() not in ['DESCRIPTION','LICENSE','LICENSE.md','LICENSE.txt']:continue
            name='1.3.9/'+package+'/'+relative.as_posix();target=resource/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes());members[name]=pin(target)
    script=out/'profile.R';script.write_text('''a<-commandArgs(TRUE);.libPaths(c(a[-1],.Library),include.site=FALSE);suppressPackageStartupMessages(library(cicero))
names<-c("cicero","monocle3","Biobase","BiocGenerics","Matrix","data.table","dplyr","plyr","FNN","glasso","VGAM","GenomicRanges","IRanges","S4Vectors","SummarizedExperiment","Gviz","ggplot2","reshape2","tibble","tidyr","stringi","stringr","jsonlite","RhpcBLASctl")
jsonlite::write_json(list(r_version=as.character(getRversion()),platform=R.version$platform,required_packages=setNames(lapply(names,function(n)as.character(packageVersion(n))),names),glasso_formals=paste(deparse(formals(glasso::glasso)),collapse="\\n"),cov2cor_body=paste(deparse(body(stats::cov2cor)),collapse="\\n")),a[1],auto_unbox=TRUE,pretty=TRUE)
''',encoding='utf8',newline='\n')
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out/'profile.json'),*map(str,libraries)];r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en'),capture_output=True,timeout=60);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    profile=json.loads((out/'profile.json').read_bytes());profile.update(schema_version=1,version='1.3.9',files=members,guarded_functions=dict(cicero=['make_atac_cds','split_peak_names','make_cicero_cds','estimate_distance_parameter','generate_cicero_models','assemble_connections','reconcile','generate_windows','get_genomic_range','calc_dist_matrix','find_distance_parameter','get_rho_mat'],monocle3=['new_cell_data_set','estimate_size_factors','estimate_sf_sparse','estimate_sf_dense','detect_genes']),source_authentication_scope='Exact package versions and selected unchanged Cicero/Monocle3 R bodies/formals; not complete dependency/native-DLL authentication',reference_control=pin(base/'cicero-readiness-probe-2/RESULT.json'))
    save(resource/'SOURCE-PROFILE.json',profile);(resource/'NOTICE.txt').write_text('Original Cicero-M3 1.3.9 at495ef0da13cc9ffe55516bfd34f48b671ad55aba and Monocle3 1.4.27 at536f1033d6de7c957f26a1f403f81efbd825e0db are retained byte for byte with their MIT notices.\nSeparate project worker/native adapter: GPL-3.0-or-later.\nNo historical paper environment, py-monocle, custom background filter, CCAN, gene-activity or biological certification is implied.\n',encoding='utf8',newline='\n');save(out/'COMPLETED.json',dict(status='captured-original-Cicero-workflow-profile',source_pin=pin(Path(__file__)),resources={p.relative_to(resource).as_posix():pin(p) for p in resource.rglob('*') if p.is_file()},original_source_members=len(members),software_installed=False,analysis_admitted=False));print('Captured',len(members),'original source/notice members and exact runtime versions')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/profile checks')
    main()
