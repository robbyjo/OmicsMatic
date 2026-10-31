"""Reconstruct exact historical bytes for reviewed dispatcher/catalog changes only.

Scientific source paths are excluded. This checks a recorded source relationship;
it does not certify a changed scientific method or authenticate the repository.
"""
import base64
import gzip
import hashlib
import io
import json
from pathlib import Path

ALLOWED_PATHS = frozenset({
    '.gitattributes',
    'src/main/java/org/jlinalg/cli/JLinAlgCli.java',
    'tools/literature/render_review.py',
    'tools/literature/reviewed-uses.json',
    'tools/literature/repeat_catalog.py',
    'tools/literature/check_review_ui.cjs',
    'tools/literature/check_repeat_review_ui.cjs',
})


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def reverse_delta(raw, row):
    if digest(raw) != row['after_sha256']:
        raise ValueError('Current shared source differs from its exact reviewed after state')
    lines = raw.splitlines(keepends=True)
    last = len(lines) + 1
    for edit in reversed(row['edits']):
        start, end = edit['after_start_line'], edit['after_end_line']
        if not 0 <= start <= end <= len(lines) or end > last:
            raise ValueError('Overlapping or invalid shared-source inverse coordinates')
        if digest(b''.join(lines[start:end])) != edit['after_chunk_sha256']:
            raise ValueError('Shared-source inverse chunk differs')
        length = edit['before_bytes']
        if not 0 <= length <= 33554432:
            raise ValueError('Shared-source inverse exceeds decoded capacity')
        compressed = base64.b64decode(edit['before_gzip_base64'], validate=True)
        with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
            before = stream.read(length + 1)
        if len(before) != length or digest(before) != edit['before_chunk_sha256']:
            raise ValueError('Shared-source inverse original bytes differ')
        lines[start:end] = before.splitlines(keepends=True)
        last = start
    restored = b''.join(lines)
    if len(restored) != row['before_bytes'] or digest(restored) != row['before_sha256']:
        raise ValueError('Reconstructed historical source hash differs')
    return restored


def verify_source(root, data, relative, expected_sha256):
    root, data = Path(root).resolve(), Path(data)
    path = (root / relative).resolve()
    path.relative_to(root)
    raw = path.read_bytes()
    if digest(raw) == expected_sha256:
        return
    if relative not in ALLOWED_PATHS:
        raise ValueError('Changed scientific/unreviewed source cannot use catalog compatibility: ' + relative)
    documents = []
    for item in sorted(data.glob('*-shared-source-compatibility.json')):
        if item.stat().st_size > 16777216:
            raise ValueError('Shared-source compatibility document exceeds capacity')
        document = json.loads(item.read_text(encoding='utf-8'))
        if document.get('status') != 'passed-dispatch-catalog-only-source-deltas' or document.get('schema_version') != 1:
            raise ValueError('Unreviewed shared-source compatibility document')
        documents.append(document)
    visited = set()
    for _ in range(64):
        actual = digest(raw)
        if actual == expected_sha256:
            return
        if actual in visited:
            raise ValueError('Cyclic shared-source inverse chain')
        visited.add(actual)
        candidates = [row for document in documents for row in document['paths']
                      if row['path'] == relative and row['after_sha256'] == actual]
        if len(candidates) != 1:
            raise ValueError('Missing/ambiguous exact shared-source inverse: ' + relative)
        raw = reverse_delta(raw, candidates[0])
    raise ValueError('Shared-source inverse chain exceeds capacity')
