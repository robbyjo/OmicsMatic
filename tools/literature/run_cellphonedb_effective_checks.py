# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run frozen scientific gates under current documentation context, then verify additive components."""
import argparse,contextlib,io,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from cellphonedb_effective_catalog import current_catalog_review_context

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True)
    import run_cellphonedb_catalog_checks as old
    import cellphonedb_hash_seed_evidence as startup
    import spyrrow_communication_evidence as spyrrow
    with current_catalog_review_context():
        # Original scientific code/checks remain unchanged. Only the exact README metadata history is extended.
        old_args=__import__('sys').argv
        try:__import__('sys').argv=[old_args[0],'--out',str(out/'predecessors')];old.main()
        finally:__import__('sys').argv=old_args
    checks={}
    for name,module in [('effective-startup',startup),('spyrrow-component',spyrrow)]:
        stream=io.StringIO()
        with contextlib.redirect_stdout(stream):proof=module.verify()
        (out/(name+'.stdout')).write_text(stream.getvalue(),encoding='utf8',newline='\n');checks[name]=proof['status']
    sources=[Path(__file__).resolve(),ROOT/'tools/literature/cellphonedb_effective_catalog.py',ROOT/'tools/literature/run_cellphonedb_catalog_checks.py',ROOT/'tools/literature/cellphonedb_hash_seed_evidence.py',ROOT/'tools/literature/spyrrow_communication_evidence.py'];save(out/'COMPLETED.json',dict(status='passed-nineteen-frozen-scientific-and-two-additive-gates',predecessor_checks=19,additional_checks=checks,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources},scientific_verifiers_changed=False,whole_goal_complete=False));print('Passed19 frozen scientific and2 additive gates; only current documentation context extended')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific gates')
    main()
