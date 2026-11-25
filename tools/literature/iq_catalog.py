"""Apply/verify the frozen iq protein aggregation catalog transition without running a study."""
from pathlib import Path
import argparse
import json
import os
import sys
import zipfile
import iq_catalog_model as model
from iq_primary import audit
from gemma_primary import encoded, pin, require
from gemma_catalog import checksum_rows, verify_checksums

ROOT = Path(__file__).resolve().parents[2]
FOLDER = model.ADMISSION
Q = 'docs/literature/iq-maxlfq-validation/QUALIFICATION.json'
COVERAGE = model.DATA + '/iq-proteomics-source-role-coverage.json'
PROOF = model.DATA + '/iq-proteomics-catalog-validation.json'


def filepin(path):
    return pin(Path(path).read_bytes())


def before(root):
    d = root / FOLDER
    baseline = json.loads((d / 'BASELINE.json').read_bytes())
    require(all(filepin(d / name) == baseline[key] for name, key in
                (('metadata-before.zip', 'metadata_archive'), ('PREVIOUS-REGISTRY.json', 'previous_registry'),
                 ('PREVIOUS-SHA256SUMS', 'previous_checksum_manifest'))), 'Changed predecessor seals')
    with zipfile.ZipFile(d / 'metadata-before.zip') as archive:
        require(set(archive.namelist()) == set(model.META), 'Changed archived metadata namespace')
        result = {n: archive.read(n) for n in archive.namelist()}
    require({n: pin(raw) for n, raw in result.items()} == baseline['metadata'], 'Changed predecessor metadata bytes')
    return result


def check(root, artifact_root):
    old = before(root)
    roles = json.loads((root / 'docs/literature/iq-proteomics-source-audit/ROLES.json').read_bytes())
    q = json.loads((root / Q).read_bytes())
    current = {n: (root / n).read_bytes() for n in model.META}
    result = model.check(old, current, roles, q)
    require(json.loads((root / FOLDER / 'REGISTRY.json').read_bytes()) == model.registry(root), 'Different effective registry')
    coverage = json.loads((root / COVERAGE).read_bytes())
    require(coverage == dict(schema_version=1, roles=[model.applied(r) for r in roles['roles']],
            new_native_family_credit=1, pending_providers=sorted(model.TARGETS-{'iq'}), whole_goal_complete=False), 'Different source-role coverage')
    proof = json.loads((root / PROOF).read_bytes())
    require(proof['status'] == 'PASS_SCOPED_NATIVE_IQ_AND_PROTEOMICS_CATALOG_ADMISSION' and proof['result'] == result
            and proof['qualification'] == filepin(root / Q), 'Different qualification/catalog result')
    rows = checksum_rows((root / model.DATA / 'SHA256SUMS').read_bytes())
    prior = checksum_rows((root / FOLDER / 'PREVIOUS-SHA256SUMS').read_bytes())
    require(len(prior) == 881 and len(rows) == 883 and set(rows) == set(prior) | {Path(COVERAGE).name, Path(PROOF).name}, 'Changed checksum population')
    allowed = {Path(n).name for n in model.META if n.startswith(model.DATA + '/')}
    require(all(rows[n] == digest or n in allowed for n, digest in prior.items()), 'Unrelated checksum refreshed')
    verify_checksums(root, artifact_root, rows)
    return result


def apply(root, private, cache, out):
    require(root.resolve() == root and private.resolve() == private and out.is_relative_to(private)
            and out.parent.resolve() == out.parent and not out.exists(), 'Canonical roots and NEW private receipt path required')
    old = before(root)
    require(all((root / n).read_bytes() == raw for n, raw in old.items()), 'Current metadata changed; preserve local work')
    prior = (root / FOLDER / 'PREVIOUS-SHA256SUMS').read_bytes()
    require((root / model.DATA / 'SHA256SUMS').read_bytes() == prior, 'Current checksums changed')
    verify_checksums(root, root, checksum_rows(prior))
    for host in ('Windows', 'Linux'):
        receipt = json.loads((private / ('native-iq-captures-' + host + '-1/COMPLETED.json')).read_bytes())
        require(len(receipt['calls']) == 22 and receipt['successful_captures'] == 4, 'Complete platform controls required')
    audit(root, cache)
    roles = json.loads((root / 'docs/literature/iq-proteomics-source-audit/ROLES.json').read_bytes())
    q = json.loads((root / Q).read_bytes())
    after = model.transition(old, roles, q)
    result = model.check(old, after, roles, q)
    require(not (root / PROOF).exists() and not (root / COVERAGE).exists(), 'Admission already exists')
    out.mkdir()
    (out / 'PLAN.json').write_bytes(encoded(dict(before={n: pin(v) for n, v in old.items()}, after={n: pin(v) for n, v in after.items()}, result=result)))
    for i, (name, raw) in enumerate(after.items()):
        target = root / name
        require(target.resolve() == target and target.read_bytes() == old[name], 'Metadata changed during application: ' + name)
        staged = out / ('staged-' + str(i)); staged.write_bytes(raw); os.replace(staged, target)
    coverage = dict(schema_version=1, roles=[model.applied(r) for r in roles['roles']],
        new_native_family_credit=1, pending_providers=sorted(model.TARGETS-{'iq'}), whole_goal_complete=False)
    (root / COVERAGE).write_bytes(encoded(coverage))
    (root / PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_SCOPED_NATIVE_IQ_AND_PROTEOMICS_CATALOG_ADMISSION',
        result=result,qualification=filepin(root / Q),original_full_paper_analyses_reexecuted=False,
        source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False)))
    (root / FOLDER / 'REGISTRY.json').write_bytes(encoded(model.registry(root)))
    rows = checksum_rows(prior)
    for n in [n for n in model.META if n.startswith(model.DATA + '/')] + [COVERAGE, PROOF]:
        rows[Path(n).name] = filepin(root / n)['sha256']
    newline = '\r\n' if prior.count(b'\n') == prior.count(b'\r\n') else '\n'
    staged = out / 'new-SHA256SUMS'; staged.write_bytes(''.join(h + '  ' + n + newline for n, h in sorted(rows.items())).encode())
    os.replace(staged, root / model.DATA / 'SHA256SUMS')
    require(check(root, root) == result, 'Readback differs')
    status = dict(schema_version=1,status='PASS_SCOPED_NATIVE_IQ_AND_PROTEOMICS_CATALOG_ADMISSION',result=result,
        checksum_entries=883,current_checksum_manifest=filepin(root / model.DATA / 'SHA256SUMS'),
        effective_provider_registry=filepin(root / FOLDER / 'REGISTRY.json'),
        raw_process_runtime_cleanup_receipts_published=False,website_reports_published=False,whole_goal_complete=False)
    (root / FOLDER / 'STATUS.json').write_bytes(encoded(status));(out / 'COMPLETED.json').write_bytes(encoded(status))
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, default=ROOT); p.add_argument('--artifact-root', type=Path)
    p.add_argument('--apply', action='store_true'); p.add_argument('--private-root', type=Path)
    p.add_argument('--cache', type=Path); p.add_argument('--out', type=Path)
    a = p.parse_args()
    try:
        if a.apply:
            require(all(x is not None for x in (a.private_root,a.cache,a.out)), 'Explicit private/cache/NEW output required')
            result = apply(a.root.absolute(),a.private_root.absolute(),a.cache.absolute(),a.out.absolute())
        else:
            result = check(a.root.absolute(),(a.artifact_root or a.root).absolute())
        print(json.dumps(dict(status='PASS_SCOPED_NATIVE_IQ_AND_PROTEOMICS_CATALOG_ADMISSION',result=result),sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        print(('SKIP3 DEPENDENT CATALOG AUDIT SKIPPED: ' if code==3 else 'FAIL2 NO CATALOG ADMISSION: ')
              +str(error)+'. Preserve all attempts/old hashes; supply exact permitted prerequisites or resolve changed inputs using NEW paths.',file=sys.stderr)
        return code


if __name__=='__main__':
    raise SystemExit(main())
