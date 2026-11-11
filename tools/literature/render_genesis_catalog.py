# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render GENESIS catalog admission while preserving all401 other tool objects exactly."""
import argparse
from collections import Counter
import json
import subprocess
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True)
    p.add_argument('--reuse-render', type=Path, help='Reuse a fully completed render whose wrapper stopped only on the stale manual README; apply and verify the pure metadata hook separately')
    a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'); out.mkdir(parents=True)
    from verify_genesis_tools_evidence import inspect
    inspect(); data = ROOT / 'docs/literature/omics-2023-2026'
    baseline = a.reuse_render / 'tool-inventory.json.before' if a.reuse_render else data / 'tool-inventory.json'
    before = {r['id']: r for r in json.loads(baseline.read_bytes())}; assert len(before) == 402 and before['genesis']['implementation_status'] == 'missing'
    for name in ['tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md']:
        (out / (name + '.before')).write_bytes((data / name).read_bytes())
    if a.reuse_render:
        process = json.loads((a.reuse_render / 'process.json').read_bytes()); assert process['exit_code'] == 0
        summary_from_process = json.loads((a.reuse_render / 'stdout').read_text(encoding='utf8').strip()); assert summary_from_process == json.loads((data / 'summary.json').read_bytes())
        assert not (a.reuse_render / 'COMPLETED.json').exists(), 'Use only the explicitly retained stale-README wrapper attempt'
        from refresh_genesis_catalog_metadata import refresh
        metadata = refresh(); save(out / 'metadata-refresh.json', metadata)
        save(out / 'process.json', dict(exit_code=0, reused_completed_render=pin(a.reuse_render / 'process.json'), metadata_hook_executed_separately=True, scientific_renderer_reexecuted=False))
    else:
        with (out / 'stdout').open('xb') as stdout, (out / 'stderr').open('xb') as stderr:
            result = subprocess.run([sys.executable, str(ROOT / 'tools/literature/render_review.py')], cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=1800)
        save(out / 'process.json', dict(exit_code=result.returncode)); assert result.returncode == 0, 'Inspect retained renderer logs'
    after = {r['id']: r for r in json.loads((data / 'tool-inventory.json').read_bytes())}; assert set(before) == set(after)
    assert all(after[key] == value for key, value in before.items() if key != 'genesis'), 'An unrelated tool object changed'
    gene = after['genesis']; assert gene['implementation_status'] == 'upstream-adapter-validated' and gene['source_roles_reviewed'] == 5
    assert gene['supported_source_versions'] == ['2.42.0'] and gene['root_command'] == 'genesis-tools' and not gene['canonical_adapter_available']
    counts = dict(Counter(r['implementation_status'] for r in after.values())); summary = json.loads((data / 'summary.json').read_bytes())
    assert counts == summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'], counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing']) == (250, 402, 2475, 19, 78, 3, 246)
    assert (data / 'WARNINGS.md').read_bytes().startswith(b'## GENESIS: warnings and required actions')
    readme = (data / 'README.md').read_text(encoding='utf8'); assert '97 new scoped integrations' in readme and '100 including three existing adapters' in readme and '246 named tools still missing' in readme
    save(out / 'COMPLETED.json', dict(status='PASS_GENESIS_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED', papers=250, tools=402, observations=2475,
         new_scoped_integrations=97, including_existing=100, missing=246, unchanged_other_tool_objects=401, whole_goal_complete=False,
         source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), ROOT / 'tools/literature/render_review.py', ROOT / 'tools/literature/genesis_catalog.py']},
         rendered_files={p.relative_to(ROOT).as_posix(): pin(p) for p in [*[data / n for n in ['tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md', 'SHA256SUMS']], ROOT / 'site/literature-review.html', ROOT / 'site/literature-warnings.html']}))
    print('PASS_GENESIS_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED;97 new/100 including existing;246 missing')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete predecessor tool-object or count checks')
    main()
