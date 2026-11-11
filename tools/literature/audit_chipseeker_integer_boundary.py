# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the captured integer-boundary warning to official and installed GenomicRanges source."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import urllib.request

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from setup_deseq2_reference import BASES,RSCRIPT,environment,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    url='https://bioconductor.org/packages/3.23/bioc/src/contrib/GenomicRanges_1.64.0.tar.gz'
    archive=out/'GenomicRanges_1.64.0.tar.gz'
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=120) as response:
        raw=response.read(10485761);assert len(raw)<=10485760;resolved=response.url
    archive.write_bytes(raw)
    with tarfile.open(archive) as package:
        description=package.extractfile('GenomicRanges/DESCRIPTION').read();assert b'\nVersion: 1.64.0\n' in description
        source=package.extractfile('GenomicRanges/R/nearest-methods.R').read()
    target=out/'nearest-methods.R';target.write_bytes(source);(out/'DESCRIPTION').write_bytes(description)
    assert b'endq <- start(query) + width(query)' in source and b'ends <- start(subject) + width(subject)' in source
    script=out/'audit-installed.R'
    script.write_text('''args<-commandArgs(trailingOnly=TRUE)
.libPaths(c(strsplit(args[[1]],.Platform$path.sep,fixed=TRUE)[[1]],.Library))
suppressPackageStartupMessages(library(GenomicRanges))
stopifnot(as.character(packageVersion("GenomicRanges"))=="1.64.0")
original<-parse(args[[2]],keep.source=FALSE)
selected<-Filter(function(e)is.call(e)&&identical(e[[1]],as.name("<-"))&&identical(e[[2]],as.name(".GenomicRanges_findPrecedeFollow")),as.list(original))
stopifnot(length(selected)==1L)
scope<-new.env(parent=asNamespace("GenomicRanges"));eval(selected[[1]],envir=scope)
installed<-getFromNamespace(".GenomicRanges_findPrecedeFollow","GenomicRanges")
stopifnot(identical(formals(installed),formals(scope$.GenomicRanges_findPrecedeFollow)),identical(body(installed),body(scope$.GenomicRanges_findPrecedeFollow)))
observed<-withCallingHandlers({as.integer(.Machine$integer.max)+1L},warning=function(w){cat("CAPTURED_WARNING:",conditionMessage(w),"\\n");invokeRestart("muffleWarning")})
stopifnot(is.na(observed))
cat("SOURCE_AST_EQUALS_INSTALLED_GENOMICRANGES_1_64_0\\n")
''',encoding='utf8',newline='\n')
    command=[str(RSCRIPT),'--vanilla',str(script),os.pathsep.join(map(str,BASES)),str(target)]
    r=subprocess.run(command,cwd=ROOT,env=environment(),capture_output=True,timeout=120);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    assert b'SOURCE_AST_EQUALS_INSTALLED_GENOMICRANGES_1_64_0' in r.stdout and b'NAs produced by integer overflow' in r.stdout
    controls=ROOT/'build/literature-review/chipseeker-runtime/nearest-original-2/ORIGINAL.json';rows=json.loads(controls.read_bytes())['cases']
    overflow=[c for c in rows if c['case_id'].startswith('random-unstranded-') and any('integer overflow' in w for w in c['warnings'])]
    assert len(overflow)==128 and all(c['source_error'] is None for c in overflow)
    save(out/'COMPLETED.json',dict(status='bound-original-integer-overflow-controls-to-official-and-installed-GenomicRanges-source',
        source_url=url,resolved_url=resolved,archive=pin(archive),source=pin(target),source_script=pin(Path(__file__)),
        installed_version='1.64.0',original_source_function_AST_equals_installed=True,original_warning_controls=128,original_controls=pin(controls),
        native_policy='Refuse a peak ending at 2147483647 or resized feature TSS at 2147483647; preserve source warnings and dropped rows. Do not silently repair coordinates or claim parity for this domain.',
        analysis_admitted=False))
    print('Bound 128 original overflow controls to exact official/installed GenomicRanges source; native boundary remains unsupported')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/boundary checks')
    main()
