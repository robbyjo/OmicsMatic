"""Read saved synthetic SCENIC development evidence; does not rerun or qualify SCENIC."""
from pathlib import Path, PurePosixPath
import argparse
import csv
import hashlib
import io
import json
import math
import zipfile


def require(value, message):
    if not value:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def verify(root):
    folder = root / 'docs/literature/scenic-network-handover-20261123'
    state = json.loads((folder / 'STATUS.json').read_bytes())
    raw_manifest = (folder / 'FILES.json').read_bytes()
    archive = (folder / 'evidence.zip').read_bytes()
    require(state['status'] == 'SCENIC_NETWORK_DEVELOPMENT_HANDOVER_NOT_QUALIFICATION', 'Wrong status')
    require(state['manifest'] == pin(raw_manifest), 'Manifest changed')
    require(state['archive'] == pin(archive), 'Archive changed')
    manifest = json.loads(raw_manifest)
    require(state['files'] == len(manifest) == 37, 'Saved member count changed')
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        names = z.namelist()
        require(len(names) == len(set(names)) and set(names) == set(manifest), 'Archive member set changed')
        require(sum(i.file_size for i in z.infolist()) < 2_000_000, 'Unexpected uncompressed size')
        for name in names:
            path = PurePosixPath(name)
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in name, 'Unsafe member path')
            require(pin(z.read(name)) == manifest[name], 'Member changed: ' + name)
        obj = lambda name: json.loads(z.read(name))
        result = obj('original-smoke-2/COMPLETED.json')
        require(result == state['latest_original_probe'], 'Probe summary differs')
        require(result['adjacency_rows'] == 412 and result['activity_shape'] == [96, 4], 'Probe dimensions differ')
        require([r['step'] for r in result['steps']] == ['grn', 'ctx', 'aucell'], 'Probe stage differs')
        require(all(r['exit_code'] == 0 and r['seconds'] > 0 for r in result['steps']), 'Probe did not complete')
        require(obj('original-smoke-2/PROGRESS.json') == result['steps'], 'Progress differs')
        require(obj('original-smoke-2/SOURCE-BEFORE.json') == obj('original-smoke-2/SOURCE-AFTER.json'), 'Saved original source identities differ')
        require(len(obj('original-smoke-2/SOURCE-BEFORE.json')) == result['source_files_unchanged'] == 10, 'Source scope differs')
        for run in ('original-smoke-1', 'original-smoke-2'):
            require(obj(run + '/FIXTURE.json')['artificial_data'] is True, 'Non-synthetic fixture')
        failed = obj('original-smoke-1/FAILURE.json')
        require(failed['step'] == 'grn' and failed['exit_code'] != 0 and failed['analysis_admitted'] is False, 'First failure lost')
        require(b"No module named 'pyscenic.aucell'" in z.read('original-smoke-1/grn.stderr'), 'Original failure console lost')
        require(b"'-m','pyscenic.cli.arboreto_with_multiprocessing'" in z.read('helpers/smoke_original_scenic_20261123_296.py'), 'Corrected module launch lost')
        table = list(csv.reader(io.StringIO(z.read('original-smoke-2/activity.csv').decode('utf-8'))))
        require(len(table) == 97 and len(table[0]) == 5, 'Saved activity dimensions differ')
        require(all(len(row) == 5 and all(math.isfinite(float(v)) for v in row[1:]) for row in table[1:]), 'Saved activity contains invalid cells')
        edges = list(csv.DictReader(io.StringIO(z.read('original-smoke-2/adjacencies.tsv').decode('utf-8')), delimiter='\t'))
        require(len(edges) == 412 and all(set(row) == {'TF', 'target', 'importance'} for row in edges), 'Saved adjacency dimensions differ')
        roles = obj('SOURCE-ROLES-DRAFT.json')
        require(len(roles['roles']) == 5 and roles['new_reviews'] == roles['new_family_credit'] == 0 and roles['canonical_modified'] is False, 'Draft catalog scope differs')
        require(roles['missed_provider_candidates'][0]['paper_id'] == 'P153', 'Missed-provider candidate lost')
    c = state['catalog']
    require((c['papers'], c['scoped_families'], c['native_families'], c['upstream_families'], c['including_existing_adapters']) == (250, 154, 29, 125, 157), 'Family counts differ')
    require((c['reviewed_uses'], c['pending_uses'], c['positive_observations'], c['named_tools'], c['missing'], c['method_overlap']) == (1697, 1025, 2722, 446, 258, 19), 'Review counts differ')
    require((c['window_start'], c['window_end']) == ('2023-10-23', '2026-10-23'), 'Frozen cohort changed')
    require(c['journals'] == {'Nature': 71, 'Nat Genet': 104, 'Am J Hum Genet': 75}, 'Journal counts differ')
    for record in (state, result):
        require(record['new_family_credit'] == record['new_reviews'] == 0 and record['whole_goal_complete'] is False, 'Development falsely credited')
    require(state['native_component_qualified'] is True and state['full_scenic_workflow_qualified'] is False and state['catalog_admitted'] is False and result['whole_tool_qualified'] is False, 'Qualification scope changed')
    require(state['local_retention']['worktrees'] == 19 and state['local_retention']['machine_transfer_complete'] is False, 'Transfer scope changed')
    return dict(status=state['status'], files=37, original_probe_stages_returned=3,
                adjacency_rows=412, activity_shape=[96, 4], failed_probe_retained=True,
                original_analysis_reexecuted=False, full_scenic_workflow_qualified=False,
                new_family_credit=0, new_reviews=0, whole_goal_complete=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root), indent=2))
    except Exception as error:
        raise SystemExit('FAIL2: SCENIC handover evidence check failed: ' + repr(error) + '. Restore published bytes; retain changed or failed evidence. Do not refresh historical hashes to bypass a failure.') from error
