# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare seven exact official runtime profiles and original deterministic health outputs; no runtime admission."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/stringtie-runtime'


def pin(path):
    raw = path.read_bytes(); return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    target = ROOT / 'src/main/resources/org/jlinalg/omics/stringtie-runtime-setup'; assert not target.exists()
    intake = BASE / 'source-intake-1'; original = json.loads((intake / 'COMPLETED.json').read_bytes())
    assert len(original['editions']) == 7
    profiles, resources = {}, {}
    target.mkdir(parents=True); (target / 'licenses').mkdir(); (target / 'health-inputs').mkdir()
    for edition in original['editions']:
        version = edition['version']; profile = 'linux-x86_64-stringtie-' + version
        executable = intake / edition['executable']; assert pin(executable) == edition['executable_pin']
        assets = {}
        for kind in ('binary', 'source'):
            item = edition['artifacts'][kind]; archive = intake / item['archive']; captured = original['files'][item['archive']]
            assert pin(archive) == {k: captured[k] for k in ['bytes', 'sha256']}
            assets[kind] = dict(filename=archive.name, url=captured['url'], **pin(archive), publisher_digest_available=captured['publisher_digest_available'])
        binary_member = executable.relative_to(intake / version / 'binary').as_posix()
        license_path = intake / version / 'source' / ('stringtie-' + version) / 'LICENSE'
        license_member = license_path.relative_to(intake / version / 'source').as_posix()
        assert pin(license_path) == edition['licenses'][license_path.relative_to(intake).as_posix()]
        destination = target / 'licenses' / (version + '.txt'); destination.write_bytes(license_path.read_bytes()); resources[destination.relative_to(target).as_posix()] = pin(destination)
        folder = BASE / 'original-controls-1' / version / 'estimate'
        capture = json.loads((BASE / 'original-controls-1' / version / 'CAPTURED.json').read_bytes())
        fitting = next(c for c in capture['cases'] if c['case'] == 'estimate'); assert fitting['process']['exit_code'] == 0 and not fitting['process']['timed_out']
        outputs = {}
        for name in ['gene-abundance.tsv', 't_data.ctab', 'e_data.ctab', 'i_data.ctab', 'e2t.ctab', 'i2t.ctab']:
            assert pin(folder / name) == fitting['files'][name]; outputs[name] = pin(folder / name)
        raw = (folder / 'transcripts.gtf').read_bytes(); assert pin(folder / 'transcripts.gtf') == fitting['files']['transcripts.gtf']
        scientific = b''.join(line for line in raw.splitlines(keepends=True) if not line.startswith(b'#'))
        profiles[profile] = dict(version=version, platform='Linux-x86_64', assets=assets,
                                 executable_member=binary_member, license_member=license_member,
                                 installed_files={'bin/stringtie': edition['executable_pin'], 'LICENSE-StringTie.txt': pin(license_path)},
                                 license_resource='licenses/' + version + '.txt', license_name=license_path.read_text().splitlines()[0],
                                 health=dict(mode='reference-only-estimation-single-thread', expected_outputs=outputs,
                                             expected_GTF_scientific=dict(bytes=len(scientific), sha256=hashlib.sha256(scientific).hexdigest()),
                                             source_process=fitting['process'], transcripts=4, exons=7, genes=3),
                                 biological_accuracy_certified=False, user_analysis_admitted=False)
    for name in ['reads.bam', 'guide.gtf', 'reference.sizes']:
        source = BASE / 'original-controls-1/inputs' / name
        expected = json.loads((BASE / 'original-controls-1/FIXTURE.json').read_bytes())['inputs'][name]
        assert pin(source) == expected
        destination = target / 'health-inputs' / name; destination.write_bytes(source.read_bytes()); resources[destination.relative_to(target).as_posix()] = pin(destination)
    report = dict(schema_version=1, status='EXACT_OFFICIAL_RUNTIME_AND_SYNTHETIC_HEALTH_LOCK_NOT_ADMISSION', profiles=profiles, resources=resources,
                  provenance='Official captured archives. Verify supplied archive and consumed member hashes. Historical assets without publisher digests are identified; captured SHA256 is integrity, not a publisher signature.',
                  policy='Explicit permitted setup only. Missing/forbidden install, download, execution or required host dependency: SKIP3 and skip dependent analysis with named recovery steps. Invalid input/integrity/process/capacity/cancellation/finalization: FAIL2, preserve attempts and retry into NEW paths. Setup does not admit a scientific runtime or user fit.',
                  native_runtime_admitted=False, user_analysis_admitted=False, implementation_credit=False)
    lock = target / 'stringtie-runtime-lock.json'; lock.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'README.txt').write_text('StringTie exact runtime setup inputs\n\nThis resource directory is a pinned setup/health kit, not an admitted runtime or user analysis.\nSelect the exact version/profile and preserve its edition-specific license. Do not substitute\nan edition for an unreported paper run. Original archives are acquired only where permitted;\nunavailable or forbidden requirements mean skip dependent analysis with explicit recovery.\nNative current-host health, typed fit validation and portable output verification remain separate.\nHealth inputs are synthetic (840 records), not biological training/calibration data.\n', encoding='utf-8', newline='\n')
    (target / '.gitattributes').write_text('stringtie-runtime-lock.json -text\nREADME.txt -text\nlicenses/*.txt -text\nhealth-inputs/* -text\ninstall_stringtie.py -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(status=report['status'], profiles=len(profiles), resources=len(resources), lock=pin(lock))))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable exact original runtime/health source checks')
    main()
