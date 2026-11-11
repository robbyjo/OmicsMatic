# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render one proved Sniffles row, preserving every other inventory row and membership."""
import argparse,json,subprocess,sys,traceback
from collections import Counter
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from render_palantir_catalog import refresh_manifest


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prepared','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    before=a.prepared.resolve()/'before';data=ROOT/'docs/literature/omics-2023-2026';state=dict(status='FAIL2_SNIFFLES_CATALOG_RENDER',whole_goal_complete=False)
    protected=[Path(__file__).resolve(),ROOT/'tools/literature/sniffles_catalog.py',ROOT/'tools/literature/render_review.py',
        ROOT/'tools/literature/reviewed-uses.json',ROOT/'tools/literature/check_review_ui.cjs',data/'papers.json']
    pins={path.relative_to(ROOT).as_posix():pin(path) for path in protected}
    try:
        command=[sys.executable,'-B',str(ROOT/'tools/literature/render_review.py')]
        with (out/'render.stdout').open('xb') as first,(out/'render.stderr').open('xb') as second:
            process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1800,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'process.json',dict(command=command,exit_code=process.returncode));assert process.returncode==0,'Read complete render.stderr; preserve this attempt and retry NEW paths'
        old={row['id']:row for row in json.loads((before/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())}
        new={row['id']:row for row in json.loads((data/'tool-inventory.json').read_bytes())}
        assert set(old)==set(new) and len(new)==400 and all(old[key]==new[key] for key in old if key!='sniffles')
        assert old['sniffles']['implementation_status']=='missing' and new['sniffles']['implementation_status']=='upstream-adapter-validated'
        counts=dict(Counter(row['implementation_status'] for row in new.values()))
        assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,74,250)
        summary=json.loads((data/'summary.json').read_bytes());assert summary['implementation_status_counts']==counts
        assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,400,2465)
        assert all(pin(ROOT/name)==value for name,value in pins.items())
        assert (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==(before/'tools/literature/check_review_ui.cjs').read_bytes()
        # Retain the entire prior catalog guide verbatim behind current status.
        prior=(before/'docs/literature/omics-2023-2026/README.md').read_bytes();(out/'rendered-readme.md').write_bytes((data/'README.md').read_bytes())
        header='''# 250-paper omics/GWAS toolbox catalog

**Current checkpoint: 91 new integrations**: 17 native Java subsets and 74 tested
upstream adapters. There are **94 including three existing adapters**, with
**250 named tools still missing**. These are scoped workflows, not complete
packages or whole-paper reproduction. Cell Ranger remains excluded.

The fixed cohort contains 250 papers: Nature 71, Nature Genetics 104 and AJHG 75,
published from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 400 observed tools from 438 definitions, 2,465 paper/tool observations,
1,009 reviewed roles and 353 supporting pointers. This checkpoint adds six
primary Sniffles reviews and one availability row. All 1,003 earlier reviews,
399 other inventory rows, paper identities and observed memberships remain exact.

Read [Sniffles workflows](../SNIFFLES_WORKFLOWS.md) for the standalone Java
entry points, exact original editions, permitted private installation and a
complete toy CRAM/reference example. Original calling remains upstream. The
canonical executable is unchanged.

**Require exit 0 AND root COMPLETED.json analysis_admitted=true for the whole
batch.** Read [WARNINGS.md](WARNINGS.md), complete original logs/VCFs and process
receipts. **SKIP3 skips dependent calling** when exact permitted software is
unavailable or execution/installation is forbidden. **FAIL2 admits no case,
including earlier complete cases.** Preserve failed/provisional output, correct
the named cause and retry NEW paths. Analysis installs nothing.

Legacy event-segment size and modern alignment length are distinct settings.
Retain the original legacy missing-deletion and DUP-to-INV discrepancies; never
relabel calls. A fatal zero-byte VCF is failed output, even with original exit0.
Existing read HP/PS phasing does not perform haplotagging. Unreported paper
commands, Boolean filters and depth fields remain unresolved. Frozen verification
checks integrity/consistency, not fresh calling, independent SV refitting,
authenticity, biological accuracy or whole-paper reproduction.

The preceding guide follows verbatim as history. Its counts and missing badges
describe earlier checkpoints. The broader implementation goal remains active.

<details>
<summary>Historical prior catalog document, retained verbatim</summary>

'''.encode()
        (data/'README.md').write_bytes(header+prior+b'\n</details>\n')
        refresh_manifest(data,(before/'docs/literature/omics-2023-2026/SHA256SUMS').read_bytes())
        state.update(status='passed-one-Sniffles-availability-and-six-role-catalog-render',source_pins=pins,
            unchanged_tool_rows=399,new_scoped_integrations=91,including_existing_adapters=94,missing=250,
            reviewed_roles=1009,supporting_pointers=353,prior_guide_retained_verbatim=True,implementation_status_counts=counts)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/('after-'+name)).write_bytes((data/name).read_bytes())
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog preservation checks')
    raise SystemExit(main())
