"""Read-only complete GEMMA development-checkpoint verification; private evidence required.

This does not execute GEMMA, install software or refit associations. Raw receipts
are written only to a NEW directory beneath the supplied private evidence root.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import stat
import subprocess
import sys
import zipfile
import gemma_primary
import collect_omics_review
from gemma_primary import require, encoded

ROOT = Path(__file__).resolve().parents[2]
FOLDER = 'docs/literature/gemma-workflow-checkpoint'
STATUS = 'PASS_GEMMA_NATIVE_KINSHIP_ORIGINAL_UNIVARIATE_AND_PORTABLE_CAPTURE'

def pin(path):
    path = Path(path).absolute()
    before = path.stat(follow_symlinks=False)
    require(stat.S_ISREG(before.st_mode) and path.resolve() == path, 'Require a canonical regular evidence file: ' + str(path))
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    after = path.stat(follow_symlinks=False)
    require((before.st_size, before.st_mtime_ns, before.st_ino) ==
            (after.st_size, after.st_mtime_ns, after.st_ino), 'Evidence changed during reading')
    return dict(bytes=after.st_size, sha256=digest)

def member(name):
    p = PurePosixPath(name)
    require(isinstance(name, str) and name and '\\' not in name and ':' not in name
            and not p.is_absolute() and all(s not in ('', '.', '..') for s in name.split('/')),
            'Unsafe source/evidence member')
    return name

def read(path):
    return json.loads(Path(path).read_bytes())

def require_qualification(q):
    require(q['status'] == STATUS and q['source_editions'] == ['0.98.1', '0.98.3', '0.98.5']
            and q['native_modes'] == ['gk1', 'gk2']
            and q['original_modes'] == ['gk1', 'gk2', 'lmm1', 'lmm2', 'lmm3', 'lmm4'],
            'Changed original source-edition/mode contract')
    expected = dict(admitted_captures=60, native_captures=24, original_captures=36,
                    Java_tests_windows=32, Java_tests_linux=32, full_primary_XML_checked=250,
                    source_roles=4, eligible_method_paragraphs=6, association_tables=24,
                    independent_association_rows=588, workflow_refusals=22,
                    saved_refusals_windows=17, saved_refusals_linux=17,
                    Windows_prerequisite_refusals=5, setup_refusals=5,
                    final_export_original_health_controls=9, old_genetics_classes_unchanged=681)
    for key, value in expected.items():
        require(type(q.get(key)) is int and q[key] == value, 'Incomplete qualification population: ' + key)
    for key in ('optimizer_convergence_certified', 'whole_tool_qualified', 'whole_paper_reproduced', 'whole_goal_complete'):
        require(q.get(key) is False, 'Expanded qualification claim: ' + key)
    require(q['new_family_credit'] == 0 and q['P176_literal_0_983_resolved'] is False,
            'Workflow component does not grant catalog credit or resolve P176')

def command(java, producer, args, out, expected):
    out.mkdir()
    argv = [str(java), '-Xmx512m', '-jar', str(producer), 'gemma-tools', *map(str, args)]
    (out / 'COMMAND.json').write_bytes(encoded(dict(argv=argv, expected_exit=expected)))
    with (out / 'stdout').open('xb') as stdout, (out / 'stderr').open('xb') as stderr:
        child = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
        (out / 'PROCESS.json').write_bytes(encoded(dict(owned_pid=child.pid)))
        try:
            code = child.wait(timeout=180)
        except BaseException:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    child.kill(); child.wait()
            raise
    (out / 'RESULT.json').write_bytes(encoded(dict(exit_code=code, expected_exit=expected)))
    require(code == expected, 'Captured-contract reader exit differs; inspect retained ' + str(out))
    if expected:
        text = (out / 'stderr').read_text(encoding='utf-8')
        require('FAIL2 NO COMPLETE RESULT' in text and 'Required action:' in text, 'Failure lacks prominent required action')

def verify(root, private, producer, predecessor, java, cache, out):
    root, private, producer, predecessor, out = map(lambda p: Path(p).absolute(), (root, private, producer, predecessor, out))
    require(root.resolve() == root and private.resolve() == private and private.is_dir(), 'Require canonical source/private roots')
    require(out.parent.resolve() == out.parent and out.is_relative_to(private) and not out.exists(),
            'Use a NEW receipt directory beneath retained private evidence')
    out.mkdir()
    folder = root / FOLDER
    status, sources, index, q, validation = [read(folder / name) for name in
        ('STATUS.json', 'SOURCES.json', 'PRIVATE-INDEX.json', 'QUALIFICATION.json', 'VALIDATION.json')]
    require_qualification(q)
    require(status['status'] == STATUS and status['new_family_credit'] == 0,
            'Different workflow checkpoint status')
    require(status['sources'] == pin(folder / 'SOURCES.json') and status['private_index'] == pin(folder / 'PRIVATE-INDEX.json'),
            'Changed checkpoint source/evidence index')
    require(status['source_files'] == len(sources) and status['private_files'] == len(index), 'Changed index populations')
    require(0 < len(sources) <= 200 and 0 < len(index) <= 50000, 'Source/evidence namespace exceeds checkpoint capacity')
    for name, identity in sources.items():
        require(pin(root / member(name)) == identity, 'Published source changed; restore exact archived context: ' + name)
    for module, name in ((sys.modules[__name__], 'tools/literature/verify_gemma_workflows.py'),
                         (gemma_primary, 'tools/literature/gemma_primary.py'),
                         (collect_omics_review, 'tools/literature/collect_omics_review.py')):
        require(pin(Path(module.__file__)) == sources[name], 'Executing helper differs from the archived source context')
    for name, identity in index.items():
        require(pin(private / member(name)) == identity, 'Retained complete evidence changed: ' + name)
    require(pin(producer) == q['producer'] and pin(predecessor) == q['predecessor_producer'], 'Restore the exact producing/predecessor JARs')
    producing_context = private / member(q['producing_context'])
    require(producing_context.name == 'SOURCE-CONTEXT.json', 'Require the original producing-context index')
    producing = read(producing_context)
    for name, identity in producing['files'].items():
        require(pin(producing_context.parent / 'source' / member(name)) == identity, 'Original producing source snapshot changed')
        if name.startswith('src/'):
            require(pin(root / name) == identity, 'Published Java/resources/tests differ from producing source')
    source_audit = gemma_primary.audit(root, cache)
    require(read(private / member(validation['independent_report']))['rows_checked'] == 588,
            'Complete independent GLS/likelihood/test-statistic evidence is absent')
    linux_tests = read(private / member(validation['linux_junit']))
    require(linux_tests['tests'] == 32 and linux_tests['exact_producer'] == q['producer'],
            'Actual Linux JUnit evidence for this producer is absent')
    # Independently compare every retained common member, rather than trusting a count receipt.
    with zipfile.ZipFile(predecessor) as old, zipfile.ZipFile(producer) as new:
        old_names, new_names = set(old.namelist()), set(new.namelist())
        require(not old_names - new_names, 'A predecessor JAR member was lost')
        changed = sorted(n for n in old_names if old.read(n) != new.read(n))
        added = sorted(new_names - old_names)
        require(changed == ['org/jlinalg/cli/JLinAlgCli.class'] and len(added) == 21,
                'Unexpected packaged behavior/member changes')
        require(len([n for n in old_names if n.startswith('org/jlinalg/genetics/') and n.endswith('.class')]) == 681,
                'Changed predecessor genetics-class population')
        require(all(n.startswith(('org/jlinalg/cli/Gemma', 'org/jlinalg/cli/gemma-setup/',
                                  'org/jlinalg/genetics/GemmaKinship')) for n in added), 'Unexpected new JAR member')
    captures = validation['captures']
    expected = set()
    for cohort in ('baseline', 'qc'):
        for edition in q['source_editions']:
            for engine, modes in (('native', q['native_modes']), ('original', q['original_modes'])):
                for mode in modes:
                    expected.add(('Linux', cohort, edition, engine, mode))
            for mode in q['native_modes']:
                expected.add(('Windows', cohort, edition, 'native', mode))
    actual = {(r['produced_on'], r['cohort'], r['edition'], r['engine'], r['mode']) for r in captures}
    require(actual == expected and len(captures) == len(expected) == 60, 'Incomplete/duplicated qualified capture population')
    for i, row in enumerate(captures):
        path = private / member(row['path'])
        request = read(path / 'request.json')
        require((request['edition'], request['engine'], request['mode']) == (row['edition'], row['engine'], row['mode']),
                'Capture source contract differs from declared population')
        require(pin(path / 'COMPLETED.json') == row['completed'] and pin(path / 'manifest.json') == row['manifest'],
                'Complete capture guard/manifest changed')
        command(java, producer, ['--verify', path], out / ('capture-' + format(i, '03d')), 0)
    refusal_folder = member(validation['saved_refusals'])
    refusals = read(private / refusal_folder / 'COMPLETED-Windows.json')['records']
    require(len(refusals) == len({r['case'] for r in refusals}) == 17, 'Incomplete saved-result refusal population')
    for i, row in enumerate(refusals):
        command(java, producer, ['--verify', private / refusal_folder / 'cases' / member(row['case'])],
                out / ('refusal-' + format(i, '03d')), 2)
    # Include final consistency checks around the real reader invocations.
    require(pin(producer) == q['producer'], 'Producer changed during readback')
    for name, identity in sources.items():
        require(pin(root / name) == identity, 'Source changed during readback')
    for name, identity in index.items():
        require(pin(private / name) == identity, 'Retained evidence changed during readback')
    result = dict(status=STATUS, source_files_checked=len(sources), private_files_checked=len(index),
                  primary_XML_checked=source_audit['primary_XML_checked'], saved_captures_checked=60,
                  saved_refusals_checked=17, independent_association_rows_retained=588,
                  original_executable_reexecuted=False, model_refit=False, new_family_credit=0,
                  whole_paper_reproduced=False, whole_goal_complete=False)
    (out / 'COMPLETED.json').write_bytes(encoded(result))
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    for name in ('private-root', 'producer', 'predecessor-producer', 'java', 'cache', 'out'):
        parser.add_argument('--' + name, type=Path, required=True)
    a = parser.parse_args()
    try:
        print(json.dumps(verify(a.root, a.private_root, a.producer, a.predecessor_producer,
                                a.java, a.cache, a.out), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT READBACK SKIPPED: ' if code == 3 else 'FAIL2 NO READBACK ADMITTED: ')
              + str(error) + '. Required action: restore the named exact permitted source, evidence or producer; '
              'preserve every attempt and use a NEW private receipt path. No source runtime was run or model refit.', file=sys.stderr)
        return code

if __name__ == '__main__':
    raise SystemExit(main())
