"""Complete GEMMA catalog/source/workflow readback with explicitly retained artifacts.

Raw receipts stay under a NEW private output directory. No original analysis,
software installation, source-version repair or scientific model is executed.
"""
from pathlib import Path
import argparse
import json
import sys
import gemma_catalog
import gemma_catalog_model
import verify_gemma_workflows
from gemma_primary import encoded, require
from verify_gemma_workflows import pin, member

ROOT = Path(__file__).resolve().parents[2]
FOLDER = 'docs/literature/gemma-catalog-checkpoint'
STATUS = 'PASS_GEMMA_CATALOG_SOURCE_CLOSURE_AND_COMPLETE_SAVED_CONTRACTS'

def read(path):
    return json.loads(Path(path).read_bytes())

def verify(root, private, artifact_root, producer, predecessor, java, cache, out):
    root, private, artifact_root, out = map(lambda p: Path(p).absolute(), (root, private, artifact_root, out))
    require(all(p.resolve() == p and p.is_dir() for p in (root, private, artifact_root)),
            'Require canonical source, private evidence and explicit artifact roots')
    require(out.parent.resolve() == out.parent and out.is_relative_to(private) and not out.exists(),
            'Use a NEW private catalog receipt directory')
    folder = root / FOLDER
    status, sources, extra = [read(folder / name) for name in ('STATUS.json', 'SOURCES.json', 'PRIVATE-INDEX.json')]
    require(status['status'] == STATUS and status['whole_goal_complete'] is False
            and status['new_family_credit'] == 1 and status['website_reports_published'] is False,
            'Different/expanded catalog checkpoint status')
    require(status['sources'] == pin(folder / 'SOURCES.json') and status['private_index'] == pin(folder / 'PRIVATE-INDEX.json'),
            'Catalog source/private index changed')
    require(len(sources) == status['source_files'] and len(extra) == status['private_catalog_files']
            and 0 < len(sources) <= 300 and 0 < len(extra) <= 10000, 'Changed source/evidence populations')
    workflow_index = read(root / 'docs/literature/gemma-workflow-checkpoint/PRIVATE-INDEX.json')
    protected = {n.split('/')[0] for n in set(workflow_index) | set(extra)}
    require(out.relative_to(private).parts[0] not in protected, 'New receipts cannot be placed inside retained evidence')
    for name, identity in sources.items():
        require(pin(root / member(name)) == identity, 'Source changed; restore the exact physical archived context: ' + name)
    for module, name in ((sys.modules[__name__], 'gemma_catalog_checkpoint.py'),
                         (gemma_catalog, 'gemma_catalog.py'), (gemma_catalog_model, 'gemma_catalog_model.py')):
        require(pin(Path(module.__file__)) == sources['tools/literature/' + name], 'Executing catalog helper differs from archived source')
    for name, identity in extra.items():
        require(pin(private / member(name)) == identity, 'Retained catalog evidence changed: ' + name)
    result = gemma_catalog.check(root, artifact_root)
    require(result == status['result'], 'Current catalog differs from the single-family/four-review admission')
    admission = read(root / 'docs/literature/gemma-catalog-admission/STATUS.json')
    require(admission['result'] == result and admission['checksum_entries'] == 879
            and admission['current_checksum_manifest'] == pin(root / gemma_catalog_model.DATA / 'SHA256SUMS'),
            'Catalog admission/checksum closure differs')
    require(admission['current_metadata'] == {n: pin(root / n) for n in gemma_catalog_model.META},
            'Current canonical metadata differs from admitted snapshot')
    ui = read(private / 'ui-installed-1/COMPLETED.json')
    require(ui['unique_profile_processes_after'] == 0 and ui['no_OS_process_termination'] is True
            and ui['page_result']['paper_roles_checked'] == ['P102', 'P171', 'P176', 'P248']
            and ui['page_result']['integrations'] == 163 and ui['page_result']['reviewed'] == 1776
            and ui['page_result']['prior_version_renderers_preserved'] is True
            and ui['page_result']['P176_literal_0_983_resolved'] is False
            and ui['page_result']['page_errors'] == [], 'Missing complete scoped renderer/closure evidence')
    for name in ('catalog-tests-Windows-2/COMPLETED.json', 'catalog-tests-Linux-2/COMPLETED.json'):
        require(read(private / name)['tests'] == 6, 'Catalog integration/legacy-checksum tests are absent')
    out.mkdir()
    workflow = verify_gemma_workflows.verify(root, private, producer, predecessor, java, cache, out / 'workflow')
    require(workflow['saved_captures_checked'] == 60 and workflow['saved_refusals_checked'] == 17,
            'Complete selected workflow populations were not read back')
    for name, identity in sources.items():
        require(pin(root / name) == identity, 'Source changed during complete catalog readback')
    for name, identity in extra.items():
        require(pin(private / name) == identity, 'Catalog evidence changed during readback')
    receipt = dict(status=STATUS, result=result, workflow_readback=workflow,
        public_source_and_metadata_files=len(sources), private_catalog_files_checked=len(extra),
        catalog_manifest_entries=879, source_code_or_expected_hashes_refreshed=False,
        website_reports_published=False, original_analysis_reexecuted=False, model_refit=False,
        whole_goal_complete=False)
    (out / 'COMPLETED.json').write_bytes(encoded(receipt))
    return receipt

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    for name in ('private-root', 'artifact-root', 'producer', 'predecessor-producer', 'java', 'cache', 'out'):
        parser.add_argument('--' + name, type=Path, required=True)
    a = parser.parse_args()
    try:
        print(json.dumps(verify(a.root, a.private_root, a.artifact_root, a.producer,
            a.predecessor_producer, a.java, a.cache, a.out), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT CATALOG READBACK SKIPPED: ' if code == 3 else 'FAIL2 NO CATALOG READBACK ADMITTED: ')
            + str(error) + '. Required action: preserve historical source, results and attempts; '
            'restore the named exact permitted artifact/producer or use the matching archived context '
            'and a NEW private receipt path. No original executable was run or model refit.', file=sys.stderr)
        return code

if __name__ == '__main__':
    raise SystemExit(main())
