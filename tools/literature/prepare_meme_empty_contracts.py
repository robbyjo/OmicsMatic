# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze unchanged original empty report controls and their provenance, without fitting or normalizing."""
import argparse
import json
from pathlib import Path
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import pin


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--traces', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); traces = args.traces.resolve(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/meme-runtime') and not out.exists()
    proof = json.loads((traces / 'COMPLETED.json').read_bytes())
    assert proof['profiles'] == 4 and proof['original_exits'] == [0]*4 and proof['original_motif_counts'] == [0]*4
    source = traces / 'captured-native'; cases = json.loads((traces / 'SOURCE_TRACES.json').read_bytes())['cases']
    assert len(cases) == 4 and len({c['profile'] for c in cases}) == 4
    files = {}
    for case in cases:
        profile = case['profile']; root = source / profile
        recipe = json.loads((root / 'prepared/request.json').read_bytes())
        assert recipe['motifs'] == (2 if profile.startswith('MEME_4_9_1_') else 1000)
        assert case['effective_motif_limit'] == str(recipe['motifs']) and case['original_exit_code'] == 0
        assert case['reported_motifs'] == 0 and case['supervised_process_completed']
        selected = [root/'TRACE.json', root/'prepared/request.json', *(root/'prepared/inputs').rglob('*'), *(root/'process/result').rglob('*')]
        for path in selected:
            if path.is_file(): files[path.relative_to(source).as_posix()] = path
    out.mkdir(); artifact = out / 'original-empty-contracts.zip'
    provenance = dict(scope='Unchanged synthetic original zero-motif report controls; not public runtime or biological admission.',
        source_trace_completion=dict(path=(traces/'COMPLETED.json').relative_to(ROOT).as_posix(), **pin(traces/'COMPLETED.json')),
        files={n: pin(p) for n,p in files.items()}, source_trace_profiles=cases,
        collector_sources=proof['source_pins'], preparation_source=pin(Path(__file__)))
    with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for name,path in sorted(files.items()): archive.write(path, name)
        archive.writestr('ORIGINS.json', json.dumps(provenance, indent=2)+'\n')
    with zipfile.ZipFile(artifact) as archive:
        assert archive.testzip() is None and len(archive.namelist()) == len(files)+1
        assert all(archive.read(n) == p.read_bytes() for n,p in files.items())
    target = ROOT/'src/test/resources/org/jlinalg/sequence/meme-discovery-empty/original-empty-contracts.zip'
    assert not target.exists(); target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(artifact, target)
    shutil.copyfile(__file__, out/'preparation-source.py')
    (out/'COMPLETED.json').write_text(json.dumps(dict(status='frozen-four-original-empty-output-controls',
        fixture=dict(path=target.relative_to(ROOT).as_posix(), **pin(target)), files=len(files), provenance=provenance), indent=2)+'\n', encoding='utf8')
    print(json.dumps(dict(fixture=target.relative_to(ROOT).as_posix(), **pin(target))))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable original source bindings')
    main()
