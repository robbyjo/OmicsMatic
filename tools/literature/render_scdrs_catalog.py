# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render scDRS admission while preserving every other tool object exactly."""
import argparse
from collections import Counter
import json
import subprocess
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    actual_before = (data / 'tool-inventory.json').read_bytes()
    baseline = BASE / 'catalog-render-1/tool-inventory.json.before'
    before = {r['id']: r for r in json.loads(baseline.read_bytes() if baseline.exists() else actual_before)}
    assert len(before) == 402 and before['scdrs']['implementation_status'] == 'missing'
    (out / 'actual-tool-inventory.before.json').write_bytes(actual_before)
    assert all(r == before[r['id']] for r in json.loads(actual_before) if r['id'] != 'scdrs')
    for n in ['tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md']:
        (out / (n + '.before')).write_bytes((data / n).read_bytes())
    # The newly source-audited P169 observation already belongs to papers.json.
    # Keep the live global summary consistent before legacy providers inspect
    # it; some deliberately convert supplied views back to physical Paths.
    summary_path = data / 'summary.json'
    summary_before = json.loads(summary_path.read_bytes())
    observed = sum(len(p['tools']) for p in json.loads((data / 'papers.json').read_bytes()))
    assert observed == 2476 and summary_before['paper_tool_observations'] in (2475, 2476)
    staged = dict(summary_before, paper_tool_observations=observed)
    save(summary_path, staged)
    assert {k: v for k, v in staged.items() if k != 'paper_tool_observations'} == {k: v for k, v in summary_before.items() if k != 'paper_tool_observations'}
    save(out / 'pre-render-summary-staging.json', dict(reason='Reflect the source-audited added observation before internally consistent legacy metadata checks',
        changed_fields=[] if summary_before['paper_tool_observations'] == 2476 else ['paper_tool_observations'],
        before=summary_before['paper_tool_observations'], after=2476, implementation_status_counts_unchanged=True))
    with (out / 'stdout').open('xb') as so, (out / 'stderr').open('xb') as se:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/literature/render_review.py')], cwd=ROOT, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=2400)
    save(out / 'process.json', dict(exit_code=result.returncode))
    assert result.returncode == 0, 'Inspect retained complete renderer logs'
    after = {r['id']: r for r in json.loads((data / 'tool-inventory.json').read_bytes())}
    assert set(after) == set(before) and all(after[n] == v for n, v in before.items() if n != 'scdrs')
    tool = after['scdrs']
    assert tool['implementation_status'] == 'upstream-adapter-validated' and tool['root_command'] == 'scdrs-tools'
    assert tool['source_roles_reviewed'] == tool['paper_count'] == 6
    assert tool['papers'] == ['P021', 'P024', 'P055', 'P085', 'P123', 'P169']
    assert tool['supported_source_versions'] == ['1.0.0', '1.0.1', '1.0.2']
    assert not tool['canonical_adapter_available'] and not tool['analysis_installs_software']
    summary = json.loads((data / 'summary.json').read_bytes())
    counts = dict(Counter(r['implementation_status'] for r in after.values()))
    assert counts == summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'], counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing']) == (250, 402, 2476, 19, 80, 3, 244)
    assert (data / 'WARNINGS.md').read_bytes().startswith(b'## scDRS: warnings and required actions')
    readme = (data / 'README.md').read_text(encoding='utf8')
    for term in ['99 new scoped integrations', '102 including three existing adapters', '244 named tools still missing', '1,049', '375']:
        assert term in readme, term
    save(out / 'COMPLETED.json', dict(status='PASS_SCDRS_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED', papers=250, tools=402, observations=2476,
        new_scoped_integrations=99, including_existing=102, missing=244, unchanged_other_tool_objects=401, whole_goal_complete=False,
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), ROOT / 'tools/literature/render_review.py', ROOT / 'tools/literature/scdrs_catalog.py']},
        rendered_files={p.relative_to(ROOT).as_posix(): pin(p) for p in [*[data / n for n in ['tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md', 'SHA256SUMS']], ROOT / 'site/literature-review.html', ROOT / 'site/literature-warnings.html']}))
    print('PASS_SCDRS_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED;99 new/102 including existing;244 missing', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable complete catalog counts or unrelated-tool preservation')
    main()
