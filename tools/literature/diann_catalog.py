"""Apply or verify the qualified local DIA-NN catalog transition; never launch an analysis."""
from pathlib import Path
import argparse
import json
import os
import sys
import zipfile
import diann_catalog_model as model
from gemma_primary import encoded, pin, require
from gemma_catalog import checksum_rows, verify_checksums
from iq_primary import audit

ROOT = Path(__file__).resolve().parents[2]
Q = 'docs/literature/diann-validation/QUALIFICATION.json'
ROLES = 'docs/literature/diann-source-audit/ROLES.json'
COVERAGE = model.DATA + '/diann-source-role-coverage.json'
PROOF = model.DATA + '/diann-catalog-validation.json'
CASES = {'1.8': 'workflow-corrected-full-1.8-robust-1',
         '1.8.1': 'workflow-corrected-full-1.8.1-1',
         '2.0.2': 'workflow-corrected-full-2.0.2-1'}


def filepin(path):
    return pin(Path(path).read_bytes())


def before(root):
    folder = root / model.ADMISSION
    baseline = json.loads((folder / 'BASELINE.json').read_bytes())
    for name, key in (('metadata-before.zip', 'metadata_archive'),
                      ('PREVIOUS-REGISTRY.json', 'previous_registry'),
                      ('PREVIOUS-SHA256SUMS', 'previous_checksums'),
                      ('PREVIOUS-PROTEOMICS-ROLES.json', 'previous_roles')):
        require(filepin(folder / name) == baseline[key], 'Changed predecessor seal: ' + name)
    with zipfile.ZipFile(folder / 'metadata-before.zip') as archive:
        require(len(archive.namelist()) == len(model.META) and set(archive.namelist()) == set(model.META),
                'Changed or duplicate archived metadata namespace')
        result = {name: archive.read(name) for name in archive.namelist()}
    require({name: pin(raw) for name, raw in result.items()} == baseline['metadata'],
            'Changed predecessor metadata bytes')
    return result


def source_roles(root):
    roles = json.loads((root / ROLES).read_bytes())
    prior = json.loads((root / model.ADMISSION / 'PREVIOUS-PROTEOMICS-ROLES.json').read_bytes())
    selected = [role for role in prior['roles'] if role['tool_id'] == 'diann']
    require(len(selected) == 5 and roles['roles'] == selected,
            'Existing DIA-NN source reviews changed or duplicated')
    require(roles['predecessor_roles'] == filepin(root / model.ADMISSION / 'PREVIOUS-PROTEOMICS-ROLES.json')
            and roles['new_source_role_reviews'] == 0, 'Changed primary source-review provenance')
    return roles


def private_controls(root, private, q):
    require((root / Q).read_bytes() == (private / 'diann-adapter-integrity-1/QUALIFICATION.json').read_bytes(),
            'Public qualification differs from actual private qualification')
    model.qualified(q)
    producer = private / 'workflow-project-build-2/project-build/cli/omicsmatic-0.4.1.jar'
    require(filepin(producer) == q['producer'], 'Changed exact producing JAR')
    controls = json.loads((private / 'diann-adapter-integrity-1/PRIVATE-CONTROLS.json').read_bytes())
    for edition, label in CASES.items():
        capture = private / label / 'capture'
        complete = json.loads((capture / 'COMPLETED.json').read_bytes())
        require(not (capture / 'FAILURE.json').exists() and not (capture / 'INCOMPLETE.json').exists()
                and complete['analysis_admitted'] and complete['manifest'] == filepin(capture / 'manifest.json'),
                'Missing complete typed workflow: ' + edition)
        require(json.loads((capture / 'manifest.json').read_bytes())['producer'] == q['producer'],
                'Typed workflow uses another producer')
        for host in ('Windows', 'Linux'):
            receipt = private / ('saved-' + label + '-' + host + '-1/COMPLETED.json')
            require(filepin(receipt) == controls['replays'][edition][host],
                    'Portable controls changed: ' + edition + ' ' + host)


def coverage(roles):
    return dict(schema_version=1, roles=[model.applied(role) for role in roles['roles']],
                new_upstream_family_credit=1, new_native_family_credit=0,
                new_source_role_reviews=0, full_author_roles_implemented=False, whole_goal_complete=False)


def check(root, artifact_root):
    old = before(root)
    roles = source_roles(root)
    q = json.loads((root / Q).read_bytes())
    result = model.check(old, {name: (root / name).read_bytes() for name in model.META}, roles, q)
    previous = json.loads((root / model.ADMISSION / 'PREVIOUS-REGISTRY.json').read_bytes())
    require(json.loads((root / model.ADMISSION / 'REGISTRY.json').read_bytes()) == model.registry(previous),
            'Different effective provider registry')
    require(json.loads((root / COVERAGE).read_bytes()) == coverage(roles), 'Different source-role coverage')
    proof = json.loads((root / PROOF).read_bytes())
    require(proof['status'] == 'PASS_SCOPED_DIA_NN_CATALOG_ADMISSION' and proof['result'] == result
            and proof['qualification'] == filepin(root / Q), 'Different qualification/catalog result')
    prior = checksum_rows((root / model.ADMISSION / 'PREVIOUS-SHA256SUMS').read_bytes())
    rows = checksum_rows((root / model.DATA / 'SHA256SUMS').read_bytes())
    require(len(prior) == 883 and len(rows) == 885
            and set(rows) == set(prior) | {Path(COVERAGE).name, Path(PROOF).name}, 'Changed checksum population')
    allowed = {Path(name).name for name in model.META if name.startswith(model.DATA + '/')}
    require(all(rows[name] == digest or name in allowed for name, digest in prior.items()),
            'Unrelated checksum refreshed')
    verify_checksums(root, artifact_root, rows)
    return result


def apply(root, private, cache, out):
    require(root.resolve() == root and private.resolve() == private and out.is_relative_to(private)
            and out.parent.resolve() == out.parent and not out.exists(), 'Canonical roots and NEW private receipt path required')
    old = before(root)
    require(all((root / name).read_bytes() == raw for name, raw in old.items()),
            'Current metadata changed; preserve local work')
    prior = (root / model.ADMISSION / 'PREVIOUS-SHA256SUMS').read_bytes()
    require((root / model.DATA / 'SHA256SUMS').read_bytes() == prior, 'Current checksums changed')
    verify_checksums(root, root, checksum_rows(prior))
    audit(root, cache)
    roles = source_roles(root)
    q = json.loads((root / Q).read_bytes())
    private_controls(root, private, q)
    after = model.transition(old, roles, q)
    result = model.check(old, after, roles, q)
    require(not (root / PROOF).exists() and not (root / COVERAGE).exists(), 'Admission already exists')
    previous = json.loads((root / model.ADMISSION / 'PREVIOUS-REGISTRY.json').read_bytes())
    registry = model.registry(previous)
    out.mkdir()
    (out / 'PLAN.json').write_bytes(encoded(dict(before={name: pin(raw) for name, raw in old.items()},
        after={name: pin(raw) for name, raw in after.items()}, qualification=filepin(root / Q), result=result)))
    for index, (name, raw) in enumerate(after.items()):
        target = root / name
        require(target.resolve() == target and target.read_bytes() == old[name],
                'Metadata changed during application: ' + name)
        staged = out / ('staged-' + str(index))
        staged.write_bytes(raw)
        os.replace(staged, target)
    (root / COVERAGE).write_bytes(encoded(coverage(roles)))
    (root / PROOF).write_bytes(encoded(dict(schema_version=1, status='PASS_SCOPED_DIA_NN_CATALOG_ADMISSION',
        result=result, qualification=filepin(root / Q), original_full_paper_analyses_reexecuted=False,
        source_build_required=True, released_binary_contains_workflow=False, website_reports_published=False)))
    (root / model.ADMISSION / 'REGISTRY.json').write_bytes(encoded(registry))
    rows = checksum_rows(prior)
    for name in [name for name in model.META if name.startswith(model.DATA + '/')] + [COVERAGE, PROOF]:
        rows[Path(name).name] = filepin(root / name)['sha256']
    newline = '\r\n' if prior.count(b'\n') == prior.count(b'\r\n') else '\n'
    staged = out / 'new-SHA256SUMS'
    staged.write_bytes(''.join(digest + '  ' + name + newline for name, digest in sorted(rows.items())).encode())
    os.replace(staged, root / model.DATA / 'SHA256SUMS')
    require(check(root, root) == result, 'Readback differs')
    status = dict(schema_version=1, status='PASS_SCOPED_DIA_NN_CATALOG_ADMISSION', result=result,
        checksum_entries=885, current_checksum_manifest=filepin(root / model.DATA / 'SHA256SUMS'),
        effective_provider_registry=filepin(root / model.ADMISSION / 'REGISTRY.json'),
        raw_process_runtime_cleanup_receipts_published=False, website_reports_published=False, whole_goal_complete=False)
    (root / model.ADMISSION / 'STATUS.json').write_bytes(encoded(status))
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
    args = parser.parse_args()
    try:
        if args.apply:
            require(all(value is not None for value in (args.private_root, args.cache, args.out)),
                    'Explicit private/cache/NEW output required')
            result = apply(args.root.absolute(), args.private_root.absolute(), args.cache.absolute(), args.out.absolute())
        else:
            result = check(args.root.absolute(), (args.artifact_root or args.root).absolute())
        print(json.dumps(dict(status='PASS_SCOPED_DIA_NN_CATALOG_ADMISSION', result=result), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT CATALOG AUDIT SKIPPED: ' if code == 3 else 'FAIL2 NO CATALOG ADMISSION: ')
              + str(error) + '. Preserve all attempts and old hashes; supply exact permitted prerequisites or correct changed inputs using NEW paths.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
