# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck unchanged predecessor scientific evidence under the exact new catalog metadata context."""
import argparse
import contextlib
import gzip
import importlib
import io
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from catalog_source_integrity import verify_source
from run_diamond_catalog_checks import PREVIOUS,COMPONENTS
from run_mrmega_catalog_checks import ArchivedCatalogView
from snprelate_catalog import current_catalog_review_context,verify_review_extension
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';render=json.loads((BASE/'catalog-render-2/COMPLETED.json').read_bytes())
    assert render['status']=='PASS_SNPRELATE_CATALOG_RENDER400_PREVIOUS_TOOL_OBJECTS_UNCHANGED'
    save(out/'STARTED.json',dict(producer=pin(Path(__file__)),render=pin(BASE/'catalog-render-2/COMPLETED.json')))
    overrides={'tools/literature/reviewed-uses.json':gzip.decompress((data/'palantir-prior-reviews.json.gz').read_bytes()),
        'docs/literature/omics-2023-2026/papers.json':gzip.decompress((data/'palantir-prior-papers.json.gz').read_bytes())}
    checks={}
    with current_catalog_review_context():
        verify_review_extension(ROOT,data,json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
        for name in PREVIOUS:
            module=importlib.import_module(name);capture=io.StringIO();saved=None
            if name=='verify_cellphonedb_review_context':saved=module.ROOT;module.ROOT=ArchivedCatalogView(ROOT,overrides)
            try:
                with contextlib.redirect_stdout(capture):result=module.verify()
            finally:
                if saved is not None:module.ROOT=saved
            (out/(name+'.stdout')).write_text(capture.getvalue(),encoding='utf8',newline='\n')
            if isinstance(result,tuple):result=result[0]
            checks[name]=dict(status=result['status'],scope='exact980-role archived metadata only;all scientific sources live' if saved is not None else 'unchanged verifier and live scientific sources')
            print('PASS',name,flush=True)
    for name,folder in COMPONENTS:
        module=importlib.import_module(name);home=ROOT/'docs/literature'/folder;proof=json.loads((home/'VALIDATION.json').read_bytes())
        for relative,value in proof['source_pins'].items():verify_source(ROOT,data,relative,value['sha256'])
        with contextlib.redirect_stdout(io.StringIO()):module.verify(home,False)
        checks[name]=dict(status='passed-frozen-component-and-live-scientific-pins');print('PASS',name,flush=True)
    for name in ['verify_diamond_evidence','verify_native_king_evidence','verify_snprelate_tools_evidence']:
        proof,_=importlib.import_module(name).verify();checks[name]=dict(status=proof['status']);print('PASS',name,flush=True)
    proof,_=importlib.import_module('verify_diamond_catalog').verify(False);checks['verify_diamond_catalog']=dict(status=proof['status'],scope='exact archived catalog plus live scientific sources')
    importlib.import_module('mafft_workflow_validation').verify(emit=False);checks['mafft_workflow_validation']=dict(status='passed57-retained-protocols-and-live-source-pins')
    # Native SNPRelate's independently replayed proof remains byte-pinned; this
    # static gate does not relabel the earlier replay as a new execution.
    home=ROOT/'docs/literature/snprelate-native-development';proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items()) and pin(home/'controls.zip')==proof['archive']
    assert all(pin(home/n)==v for n,v in proof['artifacts'].items());checks['native_snprelate_frozen_pins']=dict(status='passed-unchanged-live-sources-and-prior-replay-artifacts')
    assert len(checks)==46
    save(out/'COMPLETED.json',dict(status='PASS46_SNPRELATE_AND_UNCHANGED_PREDECESSOR_SCIENTIFIC_GATES',checks=checks,current_reviews=1038,
        scientific_verifiers_changed=False,scientific_source_allowlist_changed=False,whole_goal_complete=False,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/snprelate_catalog.py',ROOT/'tools/literature/run_diamond_catalog_checks.py']}))
    print('PASS46_SNPRELATE_AND_UNCHANGED_PREDECESSOR_SCIENTIFIC_GATES',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable live scientific-source or predecessor evidence checks')
    main()
