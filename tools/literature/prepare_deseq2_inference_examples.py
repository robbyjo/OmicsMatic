# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare literal synthetic examples from the validated original-runtime cases."""
import json
from pathlib import Path
import shutil
from setup_deseq2_reference import ROOT,save


def main():
    base=ROOT/'build/literature-review/deseq2-runtime/inference-workflow-matrix-1'
    assert json.loads((base/'COMPLETED.json').read_bytes())['status']=='passed-typed-original-DESeq2-inference-and-portable-captures'
    out=ROOT/'examples/deseq2-inference';assert not out.exists();out.mkdir()
    paired=base/'windows-java21-paired';lrt=base/'windows-java21-lrt'
    for source,name in [(paired/'counts.tsv','counts.tsv'),(paired/'samples.tsv','samples.tsv'),(paired/'keep.tsv','keep.tsv'),(lrt/'counts.tsv','lrt-counts.tsv'),(lrt/'samples.tsv','lrt-samples.tsv')]:shutil.copyfile(source,out/name)
    p=json.loads((paired/'request.json').read_bytes());p['r_libraries']=['../../build/deseq2-user-runtime/R-lib'];p['out']='../../build/deseq2-example-paired'
    p['results']=[x for x in p['results'] if x['id'] in ['paired','threshold']];p['transforms']=[x for x in p['transforms'] if x['id'] in ['vst_full','rlog']];save(out/'paired.json',p)
    p=json.loads((paired/'request.json').read_bytes());p['r_libraries']=['../../build/deseq2-user-runtime/R-lib'];p['out']='../../build/deseq2-example-shrinkage'
    p['results']=[x for x in p['results'] if x['id'] in ['normal','apeglm','ashr']];p['transforms']=[];save(out/'shrinkage.json',p)
    p=json.loads((lrt/'request.json').read_bytes());p.update(counts='lrt-counts.tsv',samples='lrt-samples.tsv',r_libraries=['../../build/deseq2-user-runtime/R-lib'],out='../../build/deseq2-example-lrt');save(out/'lrt.json',p)
    for name in ['paired','shrinkage','lrt']:save(out/('verify-'+name+'.json'),dict(schema_version=1,mode='VERIFY',analysis='../../build/deseq2-example-'+name))
    print('Prepared three literal synthetic examples with a clean private runtime binding')


if __name__=='__main__':main()
