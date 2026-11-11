# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refresh only the current README prefix and additional GENESIS warning blocks; retain historical/rendered scientific data."""
import argparse
import html
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import byte_pin

FINDINGS_WARNING = '''## GENESIS: original missing interaction results

FAIL2: NO RESULT ADMITTED. Additional synthetic dominant and recessive GxE controls returned original R_NA estimates,standard errors,statistics and P values. Six dominant variants retain48 missing cells;twenty-one recessive variants retain168. The complete original NA/NaN flags remain unchanged. Both actual root attempts were refused with the named field SE.G:PRS; no finite subset was admitted.

Required action: preserve the complete attempt and inspect WARNINGS.md,unadmitted-worker original association objects/schema/cells,the original BIM and the declared interaction design. Resolve the unassessable input/model combination explicitly before another NEW analysis. Do not fill missing values,relabel variants,drop failed rows silently or change coding/settings merely to hide failure. Read GENESIS_NUMERICAL_FINDINGS.md and genesis-cross-option-findings.json. These recorded controls do not establish that all dominant/recessive interaction analyses fail.

'''
FINDINGS_ACTIONS = '<section id="genesis-additional-findings" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>GENESIS: original missing interaction results</h2>' + ''.join('<p>' + html.escape(p) + '</p>' for p in FINDINGS_WARNING.strip().split('\n\n')[1:]) + '</section>'


def refresh(root=ROOT, data=None):
    data = data or root / 'docs/literature/omics-2023-2026'
    from genesis_catalog import GENESIS_WARNING, GENESIS_ACTIONS
    summary = json.loads((data / 'summary.json').read_bytes()); inventory = json.loads((data / 'tool-inventory.json').read_bytes())
    finding = json.loads((data / 'genesis-cross-option-findings.json').read_bytes())
    assert finding['status'] == 'PASS12_CROSS_OPTION_DISPOSITIONS_AND_TWO_ACTUAL_ROOT_SOURCE_NA_REFUSALS'
    assert (finding['source_process_successes'], finding['native_component_successes'], finding['native_component_refusals'], finding['actual_root_refusals']) == (12, 10, 2, 2)
    assert [(r['affected_variants'], r['missing_cells']) for r in finding['findings']] == [(6, 48), (21, 168)]
    assert all(r['all_missing_values_are_R_NA_not_NaN'] for r in finding['findings'])
    assert next(r for r in inventory if r['id'] == 'genesis')['implementation_status'] == 'upstream-adapter-validated'
    protected = {p.name: pin(p) for p in [data / 'papers.json', data / 'tool-inventory.json', data / 'summary.json']}
    changed = {}

    def encoded(text, like):
        value = text.encode('utf8')
        return value.replace(b'\n', b'\r\n') if like.count(b'\n') == like.count(b'\r\n') else value

    for path in [root / 'site/literature-review.html', root / 'site/literature-warnings.html', data / 'WARNINGS.md']:
        before = path.read_bytes(); warning = path.name == 'WARNINGS.md'
        anchor = encoded(GENESIS_WARNING if warning else GENESIS_ACTIONS, before); extra = encoded(FINDINGS_WARNING if warning else FINDINGS_ACTIONS, before)
        assert before.count(anchor) == 1
        if extra in before:
            assert before.count(extra) == 1; after = before
        else:
            after = before.replace(anchor, anchor + extra, 1)
        if after != before:
            path.write_bytes(after)
        changed[path.relative_to(root).as_posix()] = dict(before=byte_pin(before), after=byte_pin(after), only_additional_warning_block=True)
    path = data / 'README.md'; before = path.read_bytes(); start = before.index(b'<details>'); history = before[start:]
    counts = summary['implementation_status_counts']; native = counts['native-subset-validated']; adapters = counts['upstream-adapter-validated']; existing = counts['existing-adapter']; missing = counts['missing']
    reviews = json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    role_count = len(reviews); pointers = sum(len(r.get('supporting_evidence', [])) for r in reviews)
    prefix = f'''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: {native + adapters} new scoped integrations**: {native} native Java methods and
{adapters} tested upstream adapters. There are **{native + adapters + existing} including three existing adapters**,
with **{missing} named tools still missing**. Cell Ranger remains excluded at the
user's request. The full toolbox goal remains active.

The fixed collection contains 250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG articles from 2023-10-23 through 2026-10-23. No time extension was needed.
There are {len(inventory)} observed tools, {summary['paper_tool_observations']:,} paper-tool observations,
{role_count:,} source-bound reviewed roles and {pointers} supporting pointers. Paper-wide
semantic review and implementation remain incomplete.

Read [GENESIS workflows](../GENESIS_INTEGRATION.md) for the typed development
`genesis-tools` route, complete synthetic inputs/requests and exact private
runtime preparation. Current GENESIS2.42.0 runs PC-AiR, supplied-PC PC-Relate,
null models and single-variant tests with stated native scientific audits.
Five primary paper roles are reviewed. P128's separately reviewed supplement
names historical2.16.1 pcairPartition cutoff0.044; downstream function/settings
gaps remain explicit. Current2.42.0 does not reconstruct historical or unstated
paper analyses. [Retained interaction findings](../GENESIS_NUMERICAL_FINDINGS.md)
explain two additional R_NA refusals without filling or dropping source values.

[SNPRelate workflows](../SNPRELATE_INTEGRATION.md) remain a distinct integration
for original1.46.0 KING-robust, LD pruning, GRM and PCA. Its four reviewed paper
roles and all other401 tool objects are preserved. Current source contains new
root routes; the released executable is a separately pinned dependency and
has no new route by itself. Saved scientific verification works without R.

**Warnings and required actions:** SKIP3 means skip dependent original analysis
when exact prerequisites are unavailable or forbidden. Install/download only
where allowed into NEW private paths; otherwise leave analysis skipped.
FAIL2 admits no result, including partial output. Preserve every attempt and
resolve its named source/input/process/scientific cause before retrying NEW
paths. Require exit0, result_admitted=true and complete --verify; then inspect
warnings, effective methods and every variant disposition. Analysis installs
nothing. Original true mixed CMP errors, probabilities above1, malformed PC
shapes and required missing interaction fields admit no result; source values
are not repaired.

Current records: [tool inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings and required actions](WARNINGS.md),
[GENESIS source roles](genesis-source-role-coverage.json),
[GENESIS validation](genesis-catalog-validation.json) and
[the earlier SNPRelate checkpoint](snprelate-catalog-validation.json).

The generated reference and earlier checkpoints below are retained exactly.
Their historical counts describe those checkpoints; the records above describe
the current checkpoint.

'''
    after = encoded(prefix, before) + history; path.write_bytes(after); assert path.read_bytes().endswith(history)
    changed[path.relative_to(root).as_posix()] = dict(before=byte_pin(before), after=byte_pin(after), retained_history=byte_pin(history))
    assert all(pin(data / name) == value for name, value in protected.items())
    # The renderer calls this hook after its ordinary manifest construction.
    manifest = {p.name: pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'}
    old = (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines(); order = [r.split('  ', 1)[1] for r in old]
    order = [name for name in order if name in manifest] + [name for name in manifest if name not in order]
    assert len(order) == len(set(order)); (data / 'SHA256SUMS').write_text(''.join(manifest[name] + '  ' + name + '\n' for name in order), encoding='utf8', newline='\n')
    return dict(status='PASS_GENESIS_CURRENT_README_AND_ADDITIONAL_WARNING_METADATA_ONLY', changed=changed, protected_catalog_data=protected, finding_receipt=pin(data / 'genesis-cross-option-findings.json'), producer=pin(Path(__file__)), whole_goal_complete=False)


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'); out.mkdir(parents=True)
    save(out / 'COMPLETED.json', refresh()); print('PASS_GENESIS_CURRENT_README_AND_ADDITIONAL_WARNING_METADATA_ONLY')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact warning-only or retained-history metadata checks')
    main()
