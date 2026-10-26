"""Freeze hash-verified original synthetic report fixtures from the retained pilots."""
from pathlib import Path
import gzip
import hashlib
import io
import json

ROOT = Path(__file__).resolve().parents[2]
P = ROOT / 'build/literature-review/plink-runtime'
DEST = ROOT / 'src/test/resources/org/jlinalg/genetics/plink-associations'
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
load = lambda p: json.loads(Path(p).read_text(encoding='utf-8'))


def main():
    pilot = P / 'original-operation-pilot-1'
    separated = P / 'original-firth-contracts-2'
    ordinary_proof = load(pilot / 'verification.json')
    separated_proof = load(separated / 'verification.json')
    assert ordinary_proof['status'] == 'passed-original-process-contracts;project-adapter-pending'
    assert separated_proof['status'].startswith('passed-original-separation')
    existing = load(DEST / 'provenance.json') if (DEST / 'provenance.json').exists() else None
    if existing:
        for name, value in existing['files_sha256'].items():
            assert sha(DEST / name) == value, 'Existing reference changed; preserve it and investigate'
    DEST.mkdir(parents=True, exist_ok=True)
    files = {}

    def freeze(name, body):
        stream = io.BytesIO()
        with gzip.GzipFile(filename='', mode='wb', fileobj=stream, mtime=0) as output:
            output.write(body)
        target = DEST / name
        if target.exists():
            assert target.read_bytes() == stream.getvalue(), 'Refuse to overwrite a differing reference'
        else:
            target.write_bytes(stream.getvalue())
        files[name] = sha(target)

    rows = ['variant\ta1\ta2']
    for line in (pilot / 'inputs/known.bim').read_text().splitlines():
        fields = line.split()
        rows.append('\t'.join([fields[1], fields[4], fields[5]]))
    freeze('variants.tsv.gz', ('\n'.join(rows) + '\n').encode())
    freeze('input-design.json.gz', (pilot / 'design.json').read_bytes())
    cases = []
    for engine, operation, model, suffix in [
        ('plink1', 'linear', 'LINEAR', 'assoc.linear'),
        ('plink1', 'logistic', 'LOGISTIC', 'assoc.logistic'),
        ('plink2', 'linear', 'LINEAR', 'QT.glm.linear'),
        ('plink2', 'logistic', 'LOGISTIC', 'CC.glm.logistic'),
        ('plink2', 'firth-fallback', 'FIRTH_FALLBACK', 'CC.glm.logistic.hybrid')]:
        path = pilot / (engine + '-' + operation) / ('result.' + suffix)
        step = next(s for s in ordinary_proof['steps'] if s['engine'] == engine and s['operation'] == operation)
        assert step['exit_code'] == 0 and sha(path) == step['output_files_sha256'][path.name]
        name = engine + '-' + operation + '.tsv.gz'
        freeze(name, path.read_bytes())
        cases.append(dict(name=name, engine=engine.upper(), model=model, rows=96,
                          upstream_errors=0, source_sha256=sha(path), original_command=step['command']))
    for operation, model in [('no-firth', 'LOGISTIC'), ('firth-fallback', 'FIRTH_FALLBACK'), ('firth', 'FIRTH')]:
        path = next((separated / operation).glob('result.CC.glm.*'))
        step = next(s for s in separated_proof['steps'] if s['mode'] == operation)
        assert step['exit_code'] == 0 and sha(path) == step['output_sha256'][path.name]
        name = 'separated-' + operation + '.tsv.gz'
        freeze(name, path.read_bytes())
        cases.append(dict(name=name, engine='PLINK2', model=model, rows=96,
                          upstream_errors=step['unreportable'], source_sha256=sha(path), original_command=step['command']))
    originals = load(P / 'downloaded-reference-1/verification.json')
    record = dict(scope='Actual original report-import fixtures only: synthetic128samples/96variants, explicit quantitative/logistic/Firth modes, actual A1/OMITTED pairs and finite-but-UNFINISHED fields. Input design is included. No native Firth coefficient comparison, original engine reimplementation, project upstream CLI or paper replay claim.',
                  cases=cases, files_sha256=files, samples=128, original_rows=768,
                  producer_sha256=sha(__file__),
                  original_profiles=[{k: e[k] for k in ['name', 'url', 'archive_sha256', 'version_stdout', 'members']} for e in originals['entries']],
                  pilot_proof_sha256=sha(pilot / 'verification.json'),
                  separation_proof_sha256=sha(separated / 'verification.json'),
                  independent_ordinary_math_proof_sha256=sha(P / 'original-operation-math-3/verification.json'))
    if existing:
        record['history'] = existing.get('history', []) + [{k: v for k, v in existing.items() if k != 'history'}]
    (DEST / 'provenance.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(status='frozen-actual-original-import-references', reports=len(cases), original_rows=768, files=len(files) + 1)))


if __name__ == '__main__':
    main()
