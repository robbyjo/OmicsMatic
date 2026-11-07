# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain author-linked ATAC motif scripts at an exact Git commit; never execute them."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
REPO = 'haolongli87/ptges3_resources'


def fetch(url, limit=8_000_000):
    request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-primary-source-audit', 'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(request, timeout=60) as response:
        raw = response.read(limit + 1)
    if len(raw) > limit:
        raise ValueError('Author source exceeds declared capture capacity')
    return raw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    commit_raw = fetch('https://api.github.com/repos/' + REPO + '/commits/main')
    commit = json.loads(commit_raw)['sha']
    tree_raw = fetch('https://api.github.com/repos/' + REPO + '/git/trees/' + commit + '?recursive=1')
    tree = json.loads(tree_raw)
    assert not tree.get('truncated')
    (out / 'commit.json').write_bytes(commit_raw)
    (out / 'tree.json').write_bytes(tree_raw)
    files = []
    for row in tree['tree']:
        name = row['path']
        if row['type'] != 'blob' or not name.startswith('atacseq_sgPTGES3/scripts/'):
            continue
        assert '..' not in Path(name).parts and row.get('size', 0) <= 2_000_000
        raw = fetch('https://raw.githubusercontent.com/' + REPO + '/' + commit + '/' + urllib.parse.quote(name))
        assert len(raw) == row['size'] and hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == row['sha']
        target = out / 'author-source' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
        files.append(dict(path=name, bytes=len(raw), git_blob_sha1=row['sha'], sha256=hashlib.sha256(raw).hexdigest()))
    assert files
    proof = dict(status='captured-author-linked-P133-ATAC-code-not-executed', paper_id='P133', repository=REPO, commit=commit, files=files,
                 scope='A mutable branch was resolved to this exact commit at capture. These scripts are separately retained code evidence, not proof that historical runs, module files, inputs or external installations match this state.',
                 script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), catalog_changed=False, scientific_analysis_run=False)
    (out / 'COMPLETED.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(dict(status=proof['status'], commit=commit, files=len(files))))


if __name__ == '__main__':
    main()
