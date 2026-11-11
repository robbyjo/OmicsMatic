# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extract unchanged local workflow helpers from the exact frozen Nextflow candidate; no upstream engine binaries."""
import argparse, hashlib, json, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT) and not out.exists() and out.parent.is_dir()
    evidence = json.loads((ROOT / 'docs/literature/nextflow-typed-workflow/VALIDATION.json').read_bytes())
    source = ROOT / evidence['artifact']['path']
    assert pin(source) == {k: evidence['artifact'][k] for k in ['bytes', 'sha256']}
    with zipfile.ZipFile(source) as z:
        selected = {n: z.read(n) for n in z.namelist() if not n.endswith('/') and n.startswith((
            'org/jlinalg/cli/Nextflow', 'org/jlinalg/workflow/Nextflow', 'org/jlinalg/cli/nextflow-setup/'))}
    assert len(selected) == 67
    inherited = {n: dict(bytes=len(b), sha256=hashlib.sha256(b).hexdigest()) for n, b in selected.items()}
    selected['META-INF/LICENSE'] = (ROOT / 'LICENSE').read_bytes()
    selected['META-INF/NOTICE'] = b'Unchanged OmicsMatic Nextflow workflow/capture/verification helper classes and setup resources only. No original Nextflow engine, Java runtime, container image or user analysis is included. GPL-3.0-or-later. Hashes establish integrity, not execution authenticity.\n'
    with zipfile.ZipFile(out, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(selected.items()):
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o100644 << 16
            z.writestr(item, raw)
    save(out.with_suffix('.json'), dict(status='UNCHANGED_NEXTFLOW_HELPER_DEPENDENCY_ONLY',
        artifact=pin(out), original_candidate=evidence['artifact'], inherited_entries=inherited,
        original_nextflow_sources_modified=False, original_engine_or_jre_binaries_included=False,
        new_catalog_credit=False, extractor=pin(Path(__file__))))
    print(json.dumps(dict(artifact=pin(out), exact_inherited_entries=len(inherited))), flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable the frozen candidate or complete inherited-entry checks')
    main()
