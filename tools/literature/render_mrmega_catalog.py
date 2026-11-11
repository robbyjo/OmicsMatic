# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render one native MR-MEGA row and preserve all399 other inventory rows."""
import argparse,json,subprocess,sys,traceback
from collections import Counter
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from render_palantir_catalog import refresh_manifest


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=args.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    before=base/'catalog-dispatch-1/before';data=ROOT/'docs/literature/omics-2023-2026';state=dict(status='FAIL2_MRMEGA_CATALOG_RENDER',whole_goal_complete=False)
    paths=[Path(__file__).resolve(),ROOT/'tools/literature/mrmega_catalog.py',ROOT/'tools/literature/render_review.py',ROOT/'tools/literature/reviewed-uses.json',ROOT/'tools/literature/check_review_ui.cjs',data/'papers.json']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
    try:
        command=[sys.executable,'-B',str(ROOT/'tools/literature/render_review.py')]
        with (out/'render.stdout').open('xb') as a,(out/'render.stderr').open('xb') as b:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=1800,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'process.json',dict(command=command,exit_code=process.returncode));assert process.returncode==0,'Read render.stderr; preserve attempt and retry NEW paths'
        old={r['id']:r for r in json.loads((before/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())};new={r['id']:r for r in json.loads((data/'tool-inventory.json').read_bytes())}
        assert len(new)==400 and set(old)==set(new) and all(old[k]==new[k] for k in old if k!='mrmega')
        assert old['mrmega']['implementation_status']=='missing' and new['mrmega']['implementation_status']=='native-subset-validated'
        counts=dict(Counter(r['implementation_status'] for r in new.values()));assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(18,74,249)
        summary=json.loads((data/'summary.json').read_bytes());assert summary['implementation_status_counts']==counts and (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,400,2465)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==(before/'tools/literature/check_review_ui.cjs').read_bytes()
        prior=(before/'docs/literature/omics-2023-2026/README.md').read_bytes();(out/'rendered-readme.md').write_bytes((data/'README.md').read_bytes())
        header='''# 250-paper omics/GWAS toolbox catalog

**Current checkpoint: 92 new integrations**: 18 native Java methods and 74 tested
upstream adapters. There are **95 including three existing adapters**, with
**249 named tools still missing**. These are scoped implementations, not whole
packages or whole-paper reproductions. Cell Ranger remains excluded by the user.

The unchanged fixed cohort contains 250 papers: Nature71, Nature Genetics104 and
AJHG75, published from2023-10-23 through2026-10-23. No time extension was needed.
There are400 observed tools from438 definitions,2465 observations,1,012 reviewed
roles and355 supporting pointers. This checkpoint adds three primary MR-MEGA
reviews and one native availability row. All1,009 earlier reviews,399 other
inventory rows, paper identities and observed memberships remain exact.

Read [MR-MEGA integration](../MRMEGA_INTEGRATION.md) for the mrmega-tools project
command, settings, native float32/ancestry/GC behavior and tested root supplement.
Native user analysis needs no original MR-MEGA, compiler, R or installation.
The canonical executable is unchanged. Two errors in unpublished review drafts
were corrected against retained primary XML: P072 requires at least six of seven
cohorts; P162 reports six significant index variants. The incorrect drafts remain
frozen as history and were never applied to the catalog.

**Require exit0, run.json PASS_NATIVE_MRMEGA_WITH_WARNINGS and an admitted
results.tsv. Read [WARNINGS.md](WARNINGS.md) and every row/variant disposition.**
FAIL2 admits no result table; partials stay under partial/. Correct the named
cause and retry NEW paths. SmallCohortCount rows retain NA tests. Source odd
lambda medians, duplicate/N behavior, no_alleles sign flips and float32 boundaries
are explicit. Native stable P fields never overwrite original approximation
fields. Original-reference prerequisites are separate: if installation/execution
is forbidden or unavailable, skip dependent reference work with named recovery.

The broader250-paper goal remains active. Frozen verification checks integrity
and stored relationships, not fresh scientific execution, independent user-data
refitting, authenticity or whole-paper reproduction. The preceding guide follows
verbatim as history; its counts and badges describe earlier checkpoints.

<details>
<summary>Historical prior catalog document, retained verbatim</summary>

'''.encode()
        (data/'README.md').write_bytes(header+prior+b'\n</details>\n');refresh_manifest(data,(before/'docs/literature/omics-2023-2026/SHA256SUMS').read_bytes())
        state.update(status='passed-one-native-MRMEGA-availability-and-three-role-catalog-render',source_pins=pins,unchanged_tool_rows=399,
            new_scoped_integrations=92,including_existing_adapters=95,missing=249,reviewed_roles=1012,supporting_pointers=355,prior_guide_retained_verbatim=True,implementation_status_counts=counts)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/('after-'+name)).write_bytes((data/name).read_bytes())
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact catalog preservation checks')
    raise SystemExit(main())
