"""Apply/check the source-bound GEMMA catalog admission; never runs a study analysis."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import sys
import zipfile
import gemma_catalog_model as model
from gemma_primary import audit as primary_audit, encoded, require
from verify_gemma_workflows import pin, member, STATUS as WORKFLOW_STATUS

ROOT = Path(__file__).resolve().parents[2]
ADMISSION = 'docs/literature/gemma-catalog-admission'
# These exact nine legacy entries were retained in the committed877-file
# manifest. They resolve to sibling documentation inside the same repository.
# All other members retain the ordinary no-parent-component grammar.
LEGACY_CHECKSUM_MEMBERS = frozenset('../' + name for name in (
    'MALTEXTRACT_INTEGRATION.md',
    'maltextract17-integration/catalog-before.zip',
    'maltextract17-integration/LICENSE-project.txt',
    'maltextract17-integration/maltextract17-workflow-jdk21.jar',
    'maltextract17-integration/maltextract17-workflow-jdk25.jar',
    'maltextract17-integration/NOTICE-source-attribution.txt',
    'maltextract17-integration/SOURCE_AUDIT_RECOVERY.json',
    'maltextract17-integration/OFFLINE_UI_VALIDATION.json',
    'maltextract17-integration/PACKAGED_APPLICATION_IDENTITY.json'))

def read(path):
    return json.loads(Path(path).read_bytes())

def before(root):
    folder = root / ADMISSION
    baseline = read(folder / 'BASELINE.json')
    require(pin(folder / 'metadata-before.zip') == baseline['metadata_archive']
            and pin(folder / 'PREVIOUS-SHA256SUMS') == baseline['previous_checksum_manifest']
            and pin(folder / 'PREVIOUS-REGISTRY.json') == baseline['previous_registry'], 'Predecessor snapshot changed')
    with zipfile.ZipFile(folder / 'metadata-before.zip') as archive:
        require(set(archive.namelist()) == set(model.META), 'Changed predecessor metadata namespace')
        result = {name: archive.read(name) for name in archive.namelist()}
    require({name: dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) for name, raw in result.items()}
            == baseline['metadata'], 'Predecessor metadata bytes changed')
    return result

def checksum_rows(raw):
    result = {}
    for row in raw.decode().splitlines():
        fields = row.split('  ', 1)
        require(len(fields) == 2 and len(fields[0]) == 64 and all(c in '0123456789abcdef' for c in fields[0]), 'Invalid catalog checksum row')
        name = fields[1] if fields[1] in LEGACY_CHECKSUM_MEMBERS else member(fields[1])
        require(name not in result, 'Duplicated catalog checksum member')
        result[name] = fields[0]
    return result

def verify_checksums(root, artifact_root, declared):
    source = root / model.DATA
    artifacts = artifact_root / model.DATA
    for name, digest in declared.items():
        path = (source / name).resolve()
        require(path.is_relative_to(root), 'Catalog artifact escapes the declared source root')
        if not path.is_file():
            path = (artifacts / name).resolve()
            require(path.is_relative_to(artifact_root), 'Catalog artifact escapes the explicit artifact root')
        require(pin(path)['sha256'] == digest, 'Changed complete catalog artifact: ' + name)

def check(root, artifact_root):
    root, artifact_root = Path(root).absolute(), Path(artifact_root).absolute()
    old = before(root)
    roles = read(root / 'docs/literature/gemma-source-audit/ROLES.json')
    q = read(root / 'docs/literature/gemma-workflow-checkpoint/QUALIFICATION.json')
    current = {name: (root / name).read_bytes() for name in model.META}
    result = model.check(old, current, roles, q, root)
    require(read(root / ADMISSION / 'REGISTRY.json') == model.registry(root), 'Different effective GEMMA provider registry')
    coverage = read(root / model.COVERAGE)
    require(coverage == dict(schema_version=1, status='FOUR_GEMMA_SOURCE_ROLES_ADMITTED_WITH_EXPLICIT_LIMITS',
            roles=[model.applied(r) for r in roles['roles']], new_native_family_credit=0,
            new_upstream_family_credit=1, original_paper_analyses_reexecuted=False, whole_goal_complete=False),
            'Source-role coverage differs')
    proof = read(root / model.PROOF)
    require(proof['status'] == 'PASS_SOURCE_BOUND_GEMMA_CATALOG_ADMISSION' and proof['result'] == result,
            'Missing/different catalog admission result')
    require(proof['workflow_qualification'] == pin(root / 'docs/literature/gemma-workflow-checkpoint/QUALIFICATION.json'),
            'Workflow qualification changed')
    rows = checksum_rows((root / model.DATA / 'SHA256SUMS').read_bytes())
    oldrows = checksum_rows((root / ADMISSION / 'PREVIOUS-SHA256SUMS').read_bytes())
    require(len(oldrows) == 877 and len(rows) == 879 and set(rows) == set(oldrows) |
            {Path(model.COVERAGE).name, Path(model.PROOF).name}, 'Changed canonical checksum population')
    allowed_changes = {Path(name).name for name in model.META if name.startswith(model.DATA + '/')}
    for name in oldrows:
        require(rows[name] == oldrows[name] or name in allowed_changes, 'Unrelated historical artifact checksum was refreshed')
    verify_checksums(root, artifact_root, rows)
    return result

def apply(root, private, cache, out):
    root, private, out = map(lambda p: Path(p).absolute(), (root, private, out))
    require(root.resolve() == root and private.resolve() == private and out.is_relative_to(private)
            and out.parent.resolve() == out.parent and not out.exists(), 'Require canonical source/private roots and NEW private receipt path')
    old = before(root)
    require(all((root / name).read_bytes() == raw for name, raw in old.items()), 'Current metadata differs from preserved predecessor; do not overwrite local edits')
    prior_manifest = (root / ADMISSION / 'PREVIOUS-SHA256SUMS').read_bytes()
    require((root / model.DATA / 'SHA256SUMS').read_bytes() == prior_manifest, 'Current canonical manifest changed')
    oldrows = checksum_rows(prior_manifest)
    require(len(oldrows) == 877, 'Changed predecessor artifact population')
    verify_checksums(root, root, oldrows)
    for name in ('workflow-readback-Windows-1/COMPLETED.json', 'workflow-readback-Linux-1/COMPLETED.json'):
        receipt = read(private / name)
        require(receipt['status'] == WORKFLOW_STATUS and receipt['saved_captures_checked'] == 60
                and receipt['saved_refusals_checked'] == 17, 'Complete scoped workflow readback is required before catalog credit')
    for name in ('catalog-tests-Windows-2/COMPLETED.json', 'catalog-tests-Linux-2/COMPLETED.json'):
        require(read(private / name)['tests'] == 6, 'Complete frozen catalog integration tests required')
    roles = read(root / 'docs/literature/gemma-source-audit/ROLES.json')
    qualification = read(root / 'docs/literature/gemma-workflow-checkpoint/QUALIFICATION.json')
    primary_audit(root, cache)
    after = model.transition(old, roles, qualification, root)
    result = model.check(old, after, roles, qualification, root)
    require(not (root / model.COVERAGE).exists() and not (root / model.PROOF).exists()
            and not (root / ADMISSION / 'STATUS.json').exists(), 'GEMMA admission already exists; do not overwrite')
    out.mkdir()
    (out / 'PLAN.json').write_bytes(encoded(dict(before={n: pin(root / n) for n in model.META},
        after={n: dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) for n, raw in after.items()},
        exact_preserved_predecessor=str(root / ADMISSION), result=result)))
    for i, (name, raw) in enumerate(after.items()):
        target = root / name
        require(target.resolve() == target and target.is_relative_to(root) and target.read_bytes() == old[name],
                'Metadata changed during application; preserve partial attempt and investigate: ' + name)
        staged = out / ('staged-' + str(i))
        staged.write_bytes(raw)
        os.replace(staged, target)
    coverage = dict(schema_version=1, status='FOUR_GEMMA_SOURCE_ROLES_ADMITTED_WITH_EXPLICIT_LIMITS',
        roles=[model.applied(r) for r in roles['roles']], new_native_family_credit=0,
        new_upstream_family_credit=1, original_paper_analyses_reexecuted=False, whole_goal_complete=False)
    proof = dict(schema_version=1, status='PASS_SOURCE_BOUND_GEMMA_CATALOG_ADMISSION', result=result,
        workflow_qualification=pin(root / 'docs/literature/gemma-workflow-checkpoint/QUALIFICATION.json'),
        previous_metadata_archive=pin(root / ADMISSION / 'metadata-before.zip'),
        previous_checksum_manifest=pin(root / ADMISSION / 'PREVIOUS-SHA256SUMS'),
        raw_source_and_near_name_version_fields_preserved=True, original_full_paper_analyses_reexecuted=False,
        source_build_required=True, released_binary_contains_command=False, website_reports_published=False)
    (root / model.COVERAGE).write_bytes(encoded(coverage))
    (root / model.PROOF).write_bytes(encoded(proof))
    updated = dict(oldrows)
    for name in model.META:
        if name.startswith(model.DATA + '/'):
            updated[Path(name).name] = pin(root / name)['sha256']
    for name in (model.COVERAGE, model.PROOF):
        updated[Path(name).name] = pin(root / name)['sha256']
    newline = '\r\n' if prior_manifest.count(b'\r\n') == prior_manifest.count(b'\n') else '\n'
    staged = out / 'new-SHA256SUMS'
    staged.write_bytes(''.join(digest + '  ' + name + newline for name, digest in sorted(updated.items())).encode())
    os.replace(staged, root / model.DATA / 'SHA256SUMS')
    confirmed = check(root, root)
    require(confirmed == result, 'Admission readback differs')
    status = dict(schema_version=1, status='PASS_SOURCE_BOUND_GEMMA_CATALOG_ADMISSION', result=result,
        metadata_before_archive=pin(root / ADMISSION / 'metadata-before.zip'),
        previous_checksum_manifest=pin(root / ADMISSION / 'PREVIOUS-SHA256SUMS'),
        current_checksum_manifest=pin(root / model.DATA / 'SHA256SUMS'), checksum_entries=879,
        effective_provider_registry=pin(root / ADMISSION / 'REGISTRY.json'),
        source_role_coverage=pin(root / model.COVERAGE), catalog_validation=pin(root / model.PROOF),
        current_metadata={name: pin(root / name) for name in model.META}, new_family_credit=1,
        raw_process_runtime_cleanup_receipts_published=False, website_reports_published=False,
        whole_goal_complete=False)
    (root / ADMISSION / 'STATUS.json').write_bytes(encoded(status))
    (out / 'COMPLETED.json').write_bytes(encoded(status))
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--artifact-root', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--private-root', type=Path)
    parser.add_argument('--cache', type=Path)
    parser.add_argument('--out', type=Path)
    a = parser.parse_args()
    try:
        if a.apply:
            require(a.private_root is not None and a.cache is not None and a.out is not None,
                    'Explicit private evidence/cache and NEW private receipt path required for admission')
            result = apply(a.root, a.private_root, a.cache, a.out)
        else:
            result = check(a.root, a.artifact_root or a.root)
        print(json.dumps(dict(status='PASS_SOURCE_BOUND_GEMMA_CATALOG_ADMISSION', result=result), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT CATALOG AUDIT SKIPPED: ' if code == 3 else 'FAIL2 NO CATALOG ADMISSION: ')
            + str(error) + '. Required action: preserve all source/evidence and any partial attempt; '
            'restore the named exact permitted prerequisite or resolve local edits before using NEW paths. '
            'No scientific analysis was run.', file=sys.stderr)
        return code

if __name__ == '__main__':
    raise SystemExit(main())
