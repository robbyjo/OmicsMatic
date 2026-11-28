"""Verify the bounded original Hi-C workflow checkpoint without changing catalog credit."""
import argparse, csv, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = 'docs/literature/nfcore-hic210-validation'


def read(path):
    return json.loads(Path(path).read_bytes())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(path):
    digest = hashlib.sha256()
    size = 0
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(8388608), b''):
            digest.update(chunk)
            size += len(chunk)
    return dict(bytes=size, sha256=digest.hexdigest())


def verify(root, private=None):
    q = read(root / DATA / 'QUALIFICATION.json')
    require(q['status'] == 'PASS_ORIGINAL_HIC210_COMPLETE_SELECTED_GRAPH_AND_NATIVE_RAW_MAP_CONTRACTS', 'Checkpoint is not complete')
    require(q['new_family_credit'] == q['new_source_reviews'] == 0 and not q['whole_goal_complete'], 'Component scope or catalog credit differs')
    require(q['complete_selected_original_graph_executed'] and not q['independent_all_fits_certified'] and not q['whole_paper_reproduced'], 'Scientific claim exceeds qualification')
    for name, expected in q['sources'].items():
        relative = Path(name)
        require(not relative.is_absolute() and '..' not in relative.parts, 'Invalid source member')
        require(pin(root / relative) == expected, 'Changed qualified source: ' + name)
    dispatcher = q['global_dispatcher']
    require(dispatcher['path'] == 'src/main/java/org/jlinalg/cli/JLinAlgCli.java', 'Dispatcher source binding differs')
    current_dispatch = (root / dispatcher['path']).read_text(encoding='utf-8')
    route = 'if (arguments.length > 0 && arguments[0].equals("nfcore-hic210-tools"))\n                return NfcoreHic210ToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);'
    require(route in current_dispatch.replace('\r\n', '\n'), 'Required Hi-C command dispatch is absent or changed')
    profile = read(root / 'src/main/resources/org/jlinalg/omics/nfcore-hic210/SOURCE-PROFILE.json')
    images = read(root / 'src/main/resources/org/jlinalg/omics/nfcore-hic210/IMAGES.json')
    require(profile['source_commit'] == images['source_commit'] == 'fe4ac656317d24c37e81e7940a526ed9ea812f8e', 'Original source revision differs')
    require(len(profile['files']) == 126 and sum(v['executable'] for v in profile['files'].values()) == 17, 'Full original source census differs')
    require(len(images['images']) == 14 and len(images['aliases']) == 30 and 'BOWTIE2_ALIGN_TRIMMED' in images['aliases'], 'Original image/import closure differs')
    require(q['catalog_checkpoint'] == dict(papers=250, scoped_families=185, native=37, upstream=148, reviewed_uses=1936, pending_uses=805, observations=2741), 'Catalog checkpoint differs')
    require(q['packaged_tests'] == dict(tests=26, failures=0, errors=0, skipped=0), 'Packaged regression tests failed')
    require(q['original_control']['tasks'] == 38 and q['original_control']['all_complete'] and q['original_control']['input_read_pairs'] == 500000, 'Full original control differs')
    require(q['new_cohort']['samples'] == 2 and q['new_cohort']['technical_libraries'] == 3 and q['new_cohort']['input_read_pairs'] == 1500000 and q['new_cohort']['all_tasks_complete'], 'Entire new-cohort control differs')
    require(q['portable_verification_hosts'] == ['Linux', 'Windows'] and q['refrozen_raw_pixel_corruption_refused'], 'Saved semantic replay/refusal not qualified')
    if private is not None:
        for name, expected in q['private_evidence'].items():
            relative = Path(name)
            require(not relative.is_absolute() and '..' not in relative.parts, 'Invalid private evidence member')
            require(pin(private / relative) == expected, 'Changed retained evidence: ' + name)
        build = read(private / 'project-build-Windows-4/COMPLETED.json')
        require(build['producer'] == q['producer'] and build['totals'] == q['packaged_tests'], 'Actual packaged producer/tests differ')
        producing_sources = read(private / 'project-build-Windows-4/SOURCES.json')['files']
        require(producing_sources[dispatcher['path']] == dispatcher['producing_source_pin'], 'Frozen producer dispatcher source changed')
        compatibility = read(private / 'baseline-compatibility-1/COMPLETED.json')
        require(compatibility['changed'] == ['org/jlinalg/cli/JLinAlgCli.class'] and compatibility['unchanged'] == 21483, 'Pre-existing packaged classes changed')
        for name, count in [('original-completion-1/trace.tsv', 38), ('typed-verification-1/portable-session/engine/attempts/000001/trace.tsv', q['new_cohort']['tasks'])]:
            with (private / name).open(encoding='utf-8', newline='') as stream:
                rows = list(csv.DictReader(stream, delimiter='\t'))
            require(len(rows) == count and all(v['status'] == 'COMPLETED' and v['exit'] == '0' and v['attempt'] == '1' for v in rows), 'Incomplete/retried original graph: ' + name)
        audit = read(private / 'typed-verification-1/portable-session/attempts/000001/audit.json')
        require(audit['materialization']['samples'] == 2 and audit['materialization']['technical_libraries'] == 3, 'Captured cohort population differs')
        science = audit['scientific']
        require(science['reference_contigs'] == 236 and science['reference_bases'] == 11375394 and sum(v['input_read_pairs'] for v in science['samples']) == 1500000, 'Complete reference/FASTQ population differs')
        require(science['all_source_pairs_and_selected_raw_cooler_pixels_recomputed'] and science['source_missing_weight_markers_preserved'] and not science['scientific_identifiers_repaired'], 'Native scientific scope differs')
        for host in ['typed-verification-1', 'typed-verification-Windows-1/complete-portable-positive']:
            require(read(private / host / 'RESULT.json')['exit_code'] == 0, 'Typed saved verification failed: ' + host)
        failure = private / 'typed-verification-Windows-1/refrozen-raw-pixel-semantic-failure'
        require(read(failure / 'RESULT.json')['exit_code'] == 2 and 'Original raw contact count differs from complete native pair reconstruction' in (failure / 'stderr.log').read_text(), 'Refrozen raw-pixel corruption was not semantically refused')
        refusal = read(private / 'typed-failure-controls-1/COMPLETED.json')
        require({v['case']: v['exit_code'] for v in refusal['cases']} == {'missing-engine': 3, 'invalid-capacity': 2, 'unqualified-engine-edition': 3, 'missing-exact-images': 3, 'changed-original-source': 2}, 'Exact prerequisite/source refusals differ')
        require(all(v['no_scientific_engine_launched'] for v in refusal['cases']), 'A refusal control unexpectedly launched science')
    return dict(status=q['status'], original_source_files=126, exact_images=14, new_family_credit=0, private_evidence_checked=private is not None, whole_goal_complete=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--private', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root.resolve(), args.private.resolve() if args.private else None), indent=2))
    except Exception as failure:
        raise SystemExit('FAIL2: no complete Hi-C checkpoint; preserve evidence and correct the named cause: ' + str(failure))
