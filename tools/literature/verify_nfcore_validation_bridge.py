# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the bounded original nf-validation execution checkpoint and its scope."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QUALIFICATION = 'docs/literature/nfcore-validation/QUALIFICATION.json'


def pin(path):
    digest = hashlib.sha256()
    size = 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            size += len(block)
            digest.update(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def read(path):
    return json.loads(Path(path).read_bytes())


def require(ok, message):
    if not ok:
        raise ValueError(message)


def contained(root, name):
    path = root / name
    require(not Path(name).is_absolute() and path.resolve().is_relative_to(root.resolve()) and not path.is_symlink(), 'Evidence path escapes its declared root')
    return path


def verify(root=ROOT, private=None):
    qualification = read(root / QUALIFICATION)
    require(qualification['status'] == 'QUALIFIED_ORIGINAL_PLUGIN_EXECUTION_COMPONENT_ONLY', 'Wrong checkpoint claim')
    require(qualification['new_family_credit'] == 0 and qualification['whole_goal_complete'] is False and qualification['whole_paper_reproduced'] is False and qualification['nfcore_family_qualified'] is False, 'Component overclaims a tool family or paper')
    require(qualification['catalog_totals'] == dict(scoped_families=181,native=36,upstream=145,reviewed_uses=1858,pending_uses=876,missing_labels=247), 'Catalog totals changed without a separate admission')
    for name, identity in qualification['public_sources'].items():
        require(pin(contained(root, name)) == identity, 'Qualified source differs: ' + name)
    plugin = read(root / 'src/main/resources/org/jlinalg/omics/nfcore-validation/PLUGIN-PROFILE.json')
    engine = read(root / 'src/main/resources/org/jlinalg/omics/nfcore-validation/ENGINE-PROFILE.json')
    require(plugin['id'] == 'nf-validation' and plugin['version'] == '1.1.3' and len(plugin['files']) == 47, 'Original plugin closure differs')
    require(engine['version'] == '24.04.4' and engine['packaging'] == 'one' and len(engine['files']) == 53 and len(engine['dependencies']) == 51, 'Standard engine closure differs')
    validation = qualification['validation']
    require(validation['java_contract_tests'] == 8 and validation['direct_original_exits'] == [0,1,1] and validation['bridge_exits'] == [0,2,2] and validation['cold_tasks'] == 1 and validation['cached_tasks'] == 1 and validation['missing_prerequisite_exits'] == [3,3], 'Original/control task or refusal population differs')
    require(validation['portable_final_jar_verifications'] == 4 and validation['final_producer_changed_class_members'] == ['org/jlinalg/cli/NfcoreValidationToolsCli.class'], 'Final producer verification differs')
    if private is not None:
        for name, identity in qualification['private_evidence'].items():
            require(pin(contained(private, name)) == identity, 'Private evidence differs: ' + name)
        build = read(private / 'project-build-Windows-6/COMPLETED.json')
        require(build['totals'] == dict(tests=8,failures=0,errors=0,skipped=0), 'Contract build failed or incomplete')
        original = read(private / 'public-reference-1/COMPLETED.json')
        require([x['exit_code'] for x in original['controls']] == [0,1,1], 'Direct original control differs')
        control = read(private / 'plugin-controls-3/COMPLETED.json')
        require([x['exit_code'] for x in control['controls']] == [0,0,0,2,2], 'Bridge installation/execution controls differ')
        require((private / 'plugin-controls-3/valid-run/workspace/published/validated.tsv').read_bytes() == b'sample_A\t0.05\n', 'Complete control output differs')
        require(read(private / 'plugin-resume-control-1/COMPLETED.json')['cached_original_task'] is True, 'Original cache resume unproven')
        final = read(private / 'final-producer-check-1/COMPLETED.json')
        require(len(final['verifications']) == 4 and all(x['exit_code'] == 0 for x in final['verifications']), 'Both-host saved verification failed')
    return dict(status='PASS_BOUNDED_ORIGINAL_NF_VALIDATION_COMPONENT',new_family_credit=0,nfcore_family_qualified=False,whole_goal_complete=False,private_evidence_checked=private is not None)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--private', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root.resolve(), args.private.resolve() if args.private else None), indent=2))
    except Exception as error:
        raise SystemExit('FAIL2: no complete component qualification; preserve evidence and correct the named cause: ' + str(error))
