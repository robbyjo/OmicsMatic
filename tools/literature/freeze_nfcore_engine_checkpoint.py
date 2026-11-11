# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the tested nf-core foundation and retained findings without catalog or canonical changes."""
import datetime, hashlib, json, shutil, subprocess, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, linux, wsl

DEST = ROOT / 'docs/literature/nfcore-engine-development'


def main():
    assert not (DEST / 'VALIDATION.json').exists() and not (DEST / 'controls.zip').exists()
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    assert canonical['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    matrix = json.loads((BASE / 'engine-controls-5/COMPLETED.json').read_bytes())
    integrity = json.loads((BASE / 'engine-integrity-2/COMPLETED.json').read_bytes())
    derivation = json.loads((BASE / 'engine-derivation-1/COMPLETED.json').read_bytes())
    census = json.loads((BASE / 'source-role-census-2/COMPLETED.json').read_bytes())
    assert matrix['status'] == 'PASS_NFCORE_PRIMARY_CONFIG_ENGINE_AND_LITERAL_CAPTURE_CONTROLS_ONLY'
    assert len(matrix['controls']) == 15 and all(r['passed'] for r in matrix['controls'])
    assert integrity['status'] == 'PASS_NFCORE_CROSS_COMPILER_EXECUTION_AND_SEMANTIC_INTEGRITY_CONTROLS_ONLY'
    assert len(integrity['executions']) == 3 and len(integrity['saved_verification']) == 12
    assert derivation['status'] == 'PASS_EXACT_BOUNDED_PRIMARY_CONFIG_ENGINE_DERIVATION'
    assert census['status'] == 'PASS250_SOURCE_HASHES_AND_NFCORE_CENSUS_DRAFT_ONLY' and len(census['sources']) == 250
    assert census['matching_papers'] == ['P002', 'P027', 'P032', 'P050', 'P169', 'P228']
    assert len(census['matching_paragraphs']) == 11 and not census['body_hits_outside_standard_paragraphs']
    baseline = json.loads((BASE / 'original-chipseq121-2/COMPLETED.json').read_bytes())
    additional_path_check = json.loads((BASE / 'original-chipseq121-3/COMPLETED.json').read_bytes())
    additional = json.loads((BASE / 'original-chipseq121-3-audit-1/COMPLETED.json').read_bytes())
    assert baseline['status'] == 'FAIL2_ORIGINAL_CHIPSEQ121_REFERENCE_ATTEMPT' and not baseline['scientific_result_admitted']
    assert additional_path_check['cause'] == "AssertionError('Complete original report missing')"
    assert additional['status'] == 'PASS_ORIGINAL_CHIPSEQ121_ADDITIONAL_PCR_CONTROL_EXECUTION_AUDIT_ONLY'
    assert not additional['scientific_result_admitted'] and additional['further_native_scientific_audit_required']
    artifacts, builds = {}, {}
    for compiler in ['21', '25']:
        build = json.loads((BASE / ('engine-build' + compiler + '-5/COMPLETED.json')).read_bytes())
        assert build['status'] == 'PASS_NFCORE_PRIMARY_CONFIG_ENGINE_FOUNDATION_BUILD_ONLY'
        artifact = ROOT / build['artifact']['path']
        expected = matrix['artifact'] if compiler == '21' else integrity['artifact']
        assert pin(artifact) == {k: expected[k] for k in ['bytes', 'sha256']}
        assert artifact.read_bytes() == (BASE / ('engine-build' + compiler + '-3/nfcore-engine-development.jar')).read_bytes()
        target = DEST / ('nfcore-engine-javac' + compiler + '.jar')
        assert not target.exists(); shutil.copyfile(artifact, target)
        artifacts[target.name] = pin(target); builds[compiler] = build
    # Retain complete standalone attempts for failed and successful controls; exclude live workspaces.
    retention = BASE / 'checkpoint-retention-1'; assert not retention.exists(); retention.mkdir()
    owner_folders = [*(BASE / ('engine-controls-' + str(i)) for i in range(1, 6)),
        BASE / 'engine-integrity-1', BASE / 'engine-integrity-2']
    for folder in owner_folders:
        owner = folder / 'OWNER.json'
        if not owner.exists(): continue
        remote = json.loads(owner.read_bytes())['owned_linux_root']
        target = retention / folder.name
        wsl('/usr/bin/python3', '-c', '''import shutil,sys
from pathlib import Path
root,target=map(Path,sys.argv[1:]);target.mkdir()
for session in sorted(root.iterdir()):
 if not session.is_dir() or not any((session/n).is_file() for n in ('request.json','session.json','FAILURE.json')):continue
 dest=target/session.name;dest.mkdir()
 for p in session.iterdir():
  if p.is_file():shutil.copyfile(p,dest/p.name)
 for name in ('attempts','objects'):
  if (session/name).exists():shutil.copytree(session/name,dest/name)
''', remote, linux(target))
    sources = [*sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('NfcoreEngine*.java')),
        ROOT / 'src/main/java/org/jlinalg/workflow/NfcoreInputBindings.java',
        *sorted((ROOT / 'src/main/resources/org/jlinalg/omics/nfcore').iterdir()),
        ROOT / 'tools/literature/java/NfcoreBindingsControl.java',
        *(ROOT / 'tools/literature' / name for name in ['build_nfcore_engine.py', 'validate_nfcore_engine.py',
            'validate_nfcore_engine_integrity.py', 'review_nfcore_sources.py', 'extract_nfcore_engine_dependency.py',
            'verify_nfcore_engine_derivation.py', 'freeze_nfcore_engine_checkpoint.py', 'verify_nfcore_engine_checkpoint.py'])]
    old_proof = json.loads((ROOT / 'docs/literature/nextflow-typed-workflow/VALIDATION.json').read_bytes())
    for name, identity in old_proof['source_pins'].items():
        if name.startswith(('src/main/java/org/jlinalg/cli/Nextflow', 'src/main/java/org/jlinalg/workflow/Nextflow', 'src/main/resources/org/jlinalg/cli/nextflow-setup/')):
            assert pin(ROOT / name) == identity
            sources.append(ROOT / name)
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    files = {'sources/' + n: ROOT / n for n in source_pins}
    folders = [*(BASE / ('engine-controls-' + str(i)) for i in range(1, 6)), BASE / 'engine-integrity-1', BASE / 'engine-integrity-2',
        BASE / 'engine-derivation-1', BASE / 'source-role-census-1', BASE / 'source-role-census-2', retention,
        *(BASE / ('engine-build' + compiler + '-' + str(i)) for compiler in ['21', '25'] for i in range(1, 6))]
    for folder in folders:
        if not folder.exists(): continue
        for p in sorted(folder.rglob('*')):
            if 'classes' in p.relative_to(folder).parts or p.suffix == '.jar': continue
            assert not p.is_symlink(), ('Unexpected link in immutable control evidence', p)
            if p.is_file(): files['evidence/' + p.relative_to(BASE).as_posix()] = p
    # Original pipeline references are execution intake only: preserve full raw traces/logs,
    # requests and immutable-input identities; leave large read/image/work trees locally retained.
    for folder_name in ['original-chipseq121-1', 'original-chipseq121-2', 'original-chipseq121-3', 'original-chipseq121-3-audit-1',
        'chipseq121-controls-1', 'chipseq121-pcr-controls-1', 'apptainer-setup-1', 'apptainer-admission-1', 'chipseq121-image-1']:
        folder = BASE / folder_name
        for p in sorted(folder.iterdir()):
            if p.is_file() and (p.suffix in ['.json', '.md', '.config', '.py', '.tsv', '.log', '.stdout', '.stderr']):
                files['reference-intake/' + folder_name + '/' + p.name] = p
    archive = DEST / 'controls.zip'
    identities = {}; directories = set()
    for name in files:
        parent = Path(name).parent
        while parent.as_posix() != '.': directories.add(parent.as_posix() + '/'); parent = parent.parent
    # Keep empty directory members from the complete immutable attempts as well.
    for folder in folders:
        if not folder.exists(): continue
        for d in folder.rglob('*'):
            if d.is_dir() and 'classes' not in d.relative_to(folder).parts:
                directories.add('evidence/' + d.relative_to(BASE).as_posix() + '/')
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(directories):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.external_attr = 0o40755 << 16
            z.writestr(info, b'')
        for name, path in sorted(files.items()):
            raw = path.read_bytes(); identities[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16
            z.writestr(info, raw)
    assert all(pin(ROOT / n) == v for n, v in source_pins.items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    catalog = {name: pin(ROOT / name) for name in ['docs/literature/omics-2023-2026/papers.json', 'tools/literature/reviewed-uses.json', 'tools/literature/tool-registry.json']}
    assert catalog['docs/literature/omics-2023-2026/papers.json'] == census['catalog_unchanged']['papers']
    assert catalog['tools/literature/reviewed-uses.json'] == census['catalog_unchanged']['reviews']
    save(DEST / 'VALIDATION.json', dict(schema_version=1, status='PASS_NFCORE_ENGINE_FOUNDATION_ONLY_NO_TOOL_CREDIT',
        recorded_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), artifacts=artifacts,
        helper_dependency=pin(DEST / 'nextflow-engine-dependency.jar'), builds=builds, source_pins=source_pins,
        archive=pin(archive), archive_files=identities, archive_directories=sorted(directories),
        matrix=matrix, integrity=integrity, derivation=derivation, census=census,
        original_fixture=baseline, additional_pcr_control=additional, historical_pcr_report_path_check=additional_path_check,
        canonical_executable_unchanged=canonical, catalog_unchanged=catalog,
        claims=dict(new_tool_credit=0, total_new_integrations=99, native=19, upstream_adapters=80,
            including_three_existing=102, missing=244, reviewed_roles=1049, paper_tool_observations=2476,
            nfcore_status='missing', source_role_drafts=6, full_nfcore_adapter=False, biological_accuracy=False,
            whole_paper_reproduction=False, whole_goal_complete=False),
        retained_findings=['Build1 lacked a complete inherited classpath.', 'Control1 used an incorrect inventory path.',
            'Control2 correctly skipped stale setup reports after a kernel change.', 'Control3 exposed an unsupported renamed metadata record; the original metadata record is now reused.',
            'Control4 had a test-only Groovy assignment error.', 'Integrity1 required the exact observed baseline error wording.',
            'Apptainer setup initially expected version1.5.5; the original provider RPM identifies itself literally as1.5.5-1. A separate unchanged-runtime identity admission retained that exact build suffix and the earlier receipt.',
            'Original full fixture ignored two genuine Preseq depth failures; no result admitted.',
            'Additional PCR control includes all original reads plus explicitly labeled simulated duplicates; no independent biological claim.',
            'PCR reference completed all97 tasks; the initial helper guessed a MultiQC path and failed. A separate read-only audit checked the original broadPeak publishDir, complete outputs and unchanged inputs; the historical path-check failure remains intact.',
            'Effective runtime POSIX modes differ on the mounted filesystem; exact bytes/links and required execution remain checked.',
            'Graphviz is absent for the optional original pipeline DAG image.'],
        required_action='Development foundation only. Read NFCORE_FOUNDATION.md. SKIP3: skip dependent analysis and complete permitted setup. FAIL2: no result admitted; preserve evidence, inspect the named cause and use new paths after correction. Require complete original pipeline/scientific contracts before nf-core tool admission. Hashes establish integrity, not authenticity. Cell Ranger remains excluded.'))
    names = ['VALIDATION.json', 'controls.zip', 'README.md', '.gitattributes', 'nextflow-engine-dependency.jar', 'nextflow-engine-dependency.json', *artifacts]
    (DEST / 'SHA256SUMS').write_text(''.join(pin(DEST / n)['sha256'] + '  ' + n + '\n' for n in names), encoding='utf8', newline='\n')
    print(json.dumps(dict(status='FROZEN_FOUNDATION_ONLY', files=len(identities), directories=len(directories), archive=pin(archive), new_tool_credit=0)), flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable completed-control, source, predecessor or catalog checks')
    main()
