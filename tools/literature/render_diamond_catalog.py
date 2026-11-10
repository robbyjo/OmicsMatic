# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render DIAMOND availability and verify all399 unrelated tool objects remain unchanged."""
import argparse, json, subprocess, sys, traceback
from collections import Counter
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    base = ROOT/'build/literature-review/diamond-runtime'
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(base)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_DIAMOND_CATALOG_RENDER', whole_goal_complete=False)
    try:
        before = {r['id']: r for r in json.loads((base/'catalog-preparation-1/tool-inventory.json').read_bytes())}
        data = ROOT/'docs/literature/omics-2023-2026'
        cmd = [sys.executable, str(ROOT/'tools/literature/render_review.py')]
        save(out/'command.json', cmd)
        with (out/'stdout').open('xb') as so, (out/'stderr').open('xb') as se:
            r = subprocess.run(cmd, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=1800)
        save(out/'process.json', dict(exit_code=r.returncode))
        assert r.returncode == 0, 'Read retained renderer stdout/stderr'
        after = {r['id']: r for r in json.loads((data/'tool-inventory.json').read_bytes())}
        assert set(before) == set(after) and len(after) == 400
        assert all(before[k] == after[k] for k in before if k != 'diamond'), 'Unrelated metadata changed'
        counts = dict(Counter(r['implementation_status'] for r in after.values()))
        summary = json.loads((data/'summary.json').read_bytes())
        assert counts == summary['implementation_status_counts']
        assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'],
                counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'],
                counts['missing']) == (250, 400, 2466, 19, 76, 3, 246)
        row = after['diamond']
        assert row['paper_count'] == row['source_roles_reviewed'] == 5
        assert row['papers'] == ['P017', 'P037', 'P054', 'P171', 'P248']
        assert row['implementation_status'] == 'upstream-adapter-validated' and row['root_command'] == 'diamond-tools'
        assert after['cellranger']['implementation_status'] == 'excluded-by-user'
        assert after['spyrrow']['implementation_status'] == 'missing'
        assert (data/'WARNINGS.md').read_text(encoding='utf8').startswith('## DIAMOND: warnings and required actions')
        files = [Path(__file__).resolve(), ROOT/'tools/literature/render_review.py', ROOT/'tools/literature/diamond_catalog.py']
        rendered = [data/n for n in ['tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md', 'SHA256SUMS']]
        rendered += [ROOT/'site/literature-review.html', ROOT/'site/literature-warnings.html']
        state.update(status='PASS_DIAMOND_CATALOG_RENDER399_UNRELATED_TOOL_OBJECTS_UNCHANGED',
            source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in files},
            source_records=250, distinct_named_tools=400, observations=2466, new_scoped_integrations=95,
            including_existing_adapters=98, missing=246, unchanged_unrelated_tool_objects=399,
            rendered_files={p.relative_to(ROOT).as_posix(): pin(p) for p in rendered})
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:1000], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable unrelated-tool preservation checks')
    raise SystemExit(main())
