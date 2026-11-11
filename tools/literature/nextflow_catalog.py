# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify bounded Nextflow workflow coverage before catalog credit."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import zipfile
from catalog_source_integrity import verify_source

STATUS = 'passed-typed-Nextflow-workflows-portable-bundles-and-recovery'


def sha(raw): return hashlib.sha256(raw).hexdigest()


def verify(root, data):
    checkpoint = root / 'docs/literature/nextflow-typed-workflow'
    proof = json.loads((checkpoint / 'VALIDATION.json').read_bytes())
    assert proof['schema_version'] == 1 and proof['status'] == STATUS
    sums = dict(line.split('  ', 1)[::-1] for line in (checkpoint / 'SHA256SUMS').read_text().splitlines())
    assert set(sums) == {'controls.zip', 'VALIDATION.json'}
    for name, digest in sums.items(): assert sha((checkpoint / name).read_bytes()) == digest
    archive = checkpoint / 'controls.zip'
    assert archive.stat().st_size == proof['archive']['bytes'] and sha(archive.read_bytes()) == proof['archive']['sha256']
    with zipfile.ZipFile(archive) as z:
        assert len(z.infolist()) == len(set(z.namelist())) == len(proof['archive_files']) == proof['archive']['entries']
        assert set(z.namelist()) == set(proof['archive_files']) and z.testzip() is None
        for entry in z.infolist():
            path = PurePosixPath(entry.filename)
            assert not path.is_absolute() and '..' not in path.parts and '\\' not in entry.filename
            raw = z.read(entry); row = proof['archive_files'][entry.filename]
            assert len(raw) == row['bytes'] and sha(raw) == row['sha256']
            assert stat.S_IFMT(entry.external_attr >> 16) == {'file': stat.S_IFREG, 'directory': stat.S_IFDIR, 'symlink': stat.S_IFLNK}[row['kind']]
        for relative, value in proof['source_pins'].items():
            raw = z.read('source/' + relative)
            assert len(raw) == value['bytes'] and sha(raw) == value['sha256']
            verify_source(root, data, relative, value['sha256'])
    current_artifact = root / proof['artifact']['path']
    if current_artifact.exists():
        assert current_artifact.stat().st_size == proof['artifact']['bytes'] and sha(current_artifact.read_bytes()) == proof['artifact']['sha256']
    assert proof['exact_editions'] == ['21.04.1', '22.10.6', '24.04.4']
    assert proof['user_workflow_validation']['cases'] == 36
    assert len(proof['native_validation']['runs']) == 3 and all(r['tests'] == 166 for r in proof['native_validation']['runs'])
    assert len(proof['recovery_validation']['runs']) == 3 and all(r['passed'] for r in proof['recovery_validation']['runs'])
    assert proof['example_validation']['counts_exact']
    assert len(proof['post_matrix_exact_runtime_verification']) == 3
    roles = json.loads((data / 'nextflow-role-coverage.json').read_bytes())
    assert roles == proof['source_role_validation']
    assert roles['status'] == 'passed-ten-Nextflow-primary-source-role-reviews' and roles['applied']
    assert roles['reviewed_roles'] == 10 and roles['total_reviews'] == 785 and roles['supporting_pointers'] == 235
    verify_source(root, data, 'tools/literature/reviewed-uses.json', roles['reviews_sha256'])
    assert sha((root / 'tools/literature/curate_nextflow_uses.py').read_bytes()) == roles['curator_sha256']
    assert sha((root / 'tools/literature/nextflow-use-reviews.json').read_bytes()) == roles['draft_sha256']
    papers = json.loads((data / 'papers.json').read_bytes())
    assert len(papers) == 250 and sum(len(p['tools']) for p in papers) == 2430
    observed = {p['paper_id']: p for p in papers if any(t['tool_id'] == 'nextflow' for t in p['tools'])}
    assert set(observed) == {p['paper_id'] for p in roles['source_proof']}
    for row in roles['source_proof']:
        paper = observed[row['paper_id']]
        tool = next(t for t in paper['tools'] if t['tool_id'] == 'nextflow')
        assert paper['pmcid'] == row['pmcid'] and paper['xml_sha256'] == row['xml_sha256']
        assert tool['evidence_paragraph_sha256'] == row['paragraph_sha256']
    assert all(proof['claims'][k] for k in ['typed_public_analysis_command_available', 'stable_session_resume_available', 'portable_analysis_bundle_verification_available', 'complete_declared_population_verified'])
    assert not any(proof['claims'][k] for k in ['whole_paper_reproduced', 'biological_accuracy_certified', 'whole_goal_complete', 'released_or_deployed'])
    return proof


def nextflow_metadata(root, data):
    proof = verify(root, data)
    return dict(implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/NextflowToolsCli.java',
        coverage_note='New scoped engine integration, with ten reviewed primary-source roles; orchestration does not implement each paper pipeline component.',
        implementation_scope='Current-source nextflow-tools runs exact original Linux x86_64 engines 21.04.1, 22.10.6 and 24.04.4. Complete declared workflow/data trees, explicit author configs/profiles, lossless JSON parameters, pinned required executables and complete published output families are captured. Stable sessions retain original cache and hash-bound whole-workspace resume. Every completed attempt carries all immutable input/output/runtime evidence blobs and verifies independently on Windows Java. Original logs/trace retain retries and failure counts; ignored failures cannot admit results. Pure dataflow requires explicit require_tasks=false and valid outputs. Runtime setup is separate and analysis never installs. Remote scheduler/service lifecycles, undeclared external resources, unresolved paper settings and full biological pipelines require separate admission. Read NEXTFLOW_DEVELOPMENT.md and examples/nextflow-workflow/README.md.',
        validation_note='36 packaged public-command controls across three exact engines passed, reusing one tiny three-sequence count population. They cover complete module/config/Unicode/link capture, cached resume, retry recovery/exhaustion, ignored failures, empty-task policies, output failures and dependency skips. Additional three-engine recovery controls prove preflight-skip recovery, repeated workspace-tamper rejection, named profiles and prominent late-finalization FAIL2 warnings. All 166 actual-JAR cases pass on Linux Java21; Windows Java21/25 each pass162 with four Linux-only filesystem aborts. Six retained original-engine attempts and16 corruption controls form22 portable bundle checks. The documented example matches exact counts. The original-request binding defect found during validation was corrected; failed controls and earlier capacity-fixture calibration are retained. No biological-accuracy, whole-paper or public-release certificate is claimed.',
        project_adapter_available=True, canonical_adapter_available=False, development_adapter_cli='nextflow-tools',
        development_candidate=proof['artifact']['path'], development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../nextflow-typed-workflow/VALIDATION.json', source_role_coverage='nextflow-role-coverage.json', source_roles_reviewed=10,
        runtime_profiles=['linux-x86_64-nextflow-' + v for v in proof['exact_editions']],
        runtime_setup_export_available=True, download_install_available=True, runtime_installer='tools/literature/install_nextflow.py',
        whole_paper_reproduced=False, biological_accuracy_certified=False, paper_exact_unknown_settings_available=False)


NEXTFLOW_WARNING = '''
## Nextflow: declared workflows, complete attempts and required recovery

Use current-source **nextflow-tools** and read **NEXTFLOW_DEVELOPMENT.md** plus **examples/nextflow-workflow/README.md**. Older released 0.3.7 executables lack this command. Execution needs Linux x86_64 Java (WSL on Windows), an exact separately admitted 21.04.1, 22.10.6 or 24.04.4 home, a completed matching setup report and NEW canonical ASCII session paths. Original input names can contain Unicode/spaces. Export setup with **--export-runtime-setup NEW_DIRECTORY**; permitted installation/download uses explicit **--allow-install**/**--allow-download** or complete offline archives. Analysis never installs.

**SKIP3: skip dependent analysis when an exact runtime, permitted dependency or capability is unavailable/forbidden. FAIL2: no result is admitted for invalid input, changed evidence, process, capacity, cancellation or finalization failure. Required action:** inspect the latest **WARNINGS.md**, **FAILURE.json**, original **nextflow.log**, **trace.tsv** and process logs; preserve attempts, correct the named cause and use NEW sessions for changed requests. Require **COMPLETED.json** and successful **nextflow-tools --verify**. Review the 130 effective permission-mode departures reported on the validated DrvFS runtime; hashes and access checks remain separate.

**Historical outputs are immutable blobs.** Read **outputs.json** and its **objects/SHA256** copies in the completed attempt; later workspace cache/published files can change. A failed latest attempt does not fall back to an old success. Verify an old attempt explicitly. Resume checks the whole working state against a proven baseline; do not edit cached files or use changed original inputs as an implicit new recipe. Ignored failures remain incomplete even after engine exit zero; no-task workflows require explicit **require_tasks=false** and valid outputs.

**Do not infer paper settings or component availability.** Engine versions differ from pipeline versions; citation numbers are not patch digits. Ten reviewed roles retain ancient-DNA, chromosome-conformation, single-cell/image, duplex, HLA, ChIP/ATAC and RNA boundaries. The 36 controls reuse a tiny synthetic population, not 36 independent biological datasets. The guard observes owned local processes; remote schedulers/services and undeclared external resources need separate supervision. Portable verification checks captured bytes/contracts and completion consistency, without rerunning biology or authenticating history. Hashes establish integrity, not authenticity. Cell Ranger remains excluded.

'''

NEXTFLOW_ACTIONS = '''<section id="nextflow-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Nextflow workflows: declared inputs and required recovery</h2>
<p>Use current-source <code>nextflow-tools</code>; read <code>NEXTFLOW_DEVELOPMENT.md</code>, <code>nextflow-typed-workflow/VALIDATION.json</code>, <code>nextflow-role-coverage.json</code> and <code>examples/nextflow-workflow/README.md</code>. Older released 0.3.7 executables lack this command. Exact original Linux x86_64 engines: <strong>21.04.1, 22.10.6, 24.04.4</strong>. Supply complete declared workflow/data trees, explicit configs/profiles, lossless parameters, required executables and complete output contracts.</p>
<p><strong>Setup is separate.</strong> Use Linux Java, including WSL on Windows, an exact admitted home and completed matching setup report. Export with <code>--export-runtime-setup NEW_DIRECTORY</code>. Install/download only where permitted, with explicit <code>--allow-install</code>/<code>--allow-download</code>; complete offline archives use <code>--archive-dir</code>. Analysis never installs. Use NEW canonical ASCII session paths; original input names can contain Unicode/spaces. Review the <strong>130 filesystem permission-mode departures</strong> reported for the validated DrvFS runtime.</p>
<p><strong>SKIP3: skip dependent analysis when the exact runtime, dependency or capability is unavailable/forbidden. FAIL2: no result is admitted for input, integrity, process, capacity, cancellation or finalization failure.</strong> Required action: inspect latest <code>WARNINGS.md</code>, <code>FAILURE.json</code>, original <code>nextflow.log</code>, <code>trace.tsv</code> and process logs. Preserve attempts, correct the named cause, use NEW sessions for changed requests and require <code>COMPLETED.json</code> plus successful <code>--verify</code>. A late finalization failure still has a prominent FAIL2 heading after engine success.</p>
<p><strong>Use immutable output copies.</strong> Each completed attempt maps every logical published file through <code>outputs.json</code> to <code>objects/SHA256</code>. Later workspace files are mutable. Windows Java verifies copied completed attempts without the producer runtime. Default verification rejects a failed latest attempt; verify old successes by explicit attempt path. Resume checks the whole workspace against a hash-bound proven state. Ignored failures cannot admit results; no-task workflows require explicit <code>require_tasks=false</code> and valid outputs.</p>
<p><strong>Validation:</strong> 36 public-command controls across three editions plus recovery/profile/late-capacity controls; 166 actual-JAR cases pass on Linux, and 162 pass with four Linux-only aborts on each Windows JVM. Six real retained attempts and sixteen corruption controls check portable verification. The runnable example matches exact counts. These reuse tiny synthetic inputs and do not certify biological accuracy or reproduce ten papers.</p>
<p><strong>Paper settings and components remain explicit.</strong> Engine and pipeline editions differ; citations are not patch digits. The local guard does not supervise remote scheduler/service lifecycles. Undeclared resources and external libraries/containers/databases need separate assessment. Hashes establish integrity, not authenticity. Cell Ranger remains excluded.</p></section>'''


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[2]
    proof = verify(root, root / 'docs/literature/omics-2023-2026')
    print(json.dumps(dict(status=proof['status'], source_roles=10, focused_cases=166, workflow_controls=36)))
