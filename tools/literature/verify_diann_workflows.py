"""Verify frozen DIA-NN source/catalog and retained evidence; never refresh expected hashes."""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import diann_catalog as catalog
import diann_catalog_model as model
from gemma_primary import require

ROOT = Path(__file__).resolve().parents[2]
FOLDER = 'docs/literature/diann-validation'


class ByteAudit:
    """Read each physical hardlinked file once per pass; recheck content at the end."""
    def __init__(self):
        self.memo = {}
        self.observed = {}

    @staticmethod
    def identity(path):
        require(path.is_file() and not path.is_symlink() and path.resolve() == path.absolute(),
                'Require canonical regular evidence file: ' + str(path))
        value = path.stat()
        return (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns, value.st_ctime_ns)

    def pin(self, path):
        path = Path(path)
        before = self.identity(path)
        # A zero inode does not establish a shared physical identity.
        key = before if before[1] else (str(path), *before)
        if key not in self.memo:
            with path.open('rb') as stream:
                self.memo[key] = dict(bytes=before[2], sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
        require(self.identity(path) == before, 'Evidence changed during read: ' + str(path))
        self.observed[path] = before
        return self.memo[key]

    def unchanged(self):
        final = ByteAudit()
        for path, expected in self.observed.items():
            require(self.identity(path) == expected, 'Evidence changed during audit: ' + str(path))
            key = expected if expected[1] else (str(path), *expected)
            require(final.pin(path) == self.memo[key], 'Evidence changed during audit: ' + str(path))
        # Stat-only checks are insufficient: same-length writes can share a
        # filesystem timestamp, including ordinary rapid Windows writes.
        for path, expected in final.observed.items():
            require(final.identity(path) == expected, 'Evidence changed during final read: ' + str(path))


def safe_member(name):
    return (isinstance(name, str) and bool(name) and '\\' not in name and ':' not in name
            and not name.startswith('/') and all(part not in ('', '.', '..') for part in name.split('/')))


def audit(root, private, source, artifact=None):
    root, private, source = (Path(value).absolute() for value in (root, private, source))
    folder = root / FOLDER
    state = json.loads((folder / 'STATUS.json').read_bytes())
    require(state['status'] == 'VALIDATED_SCOPED_ORIGINAL_DIA_NN_AND_CATALOG'
            and state['whole_goal_complete'] is False, 'Different or expanded checkpoint scope')
    artifact = Path(artifact).absolute() if artifact is not None else root
    reader = ByteAudit()
    for name, key in (('SOURCES.json', 'sources'), ('PRIVATE-INDEX.json', 'private_index'),
                      ('QUALIFICATION.json', 'qualification')):
        require(reader.pin(folder / name) == state[key], 'Changed checkpoint declaration: ' + name)
    counts = []
    source_rows = None
    for manifest, base in (('SOURCES.json', source), ('PRIVATE-INDEX.json', private)):
        rows = json.loads((folder / manifest).read_bytes())
        require(isinstance(rows, dict), 'Expected a named evidence index')
        for name, expected in rows.items():
            require(safe_member(name), 'Unsafe evidence member')
            require(reader.pin(base / name) == expected, 'Changed frozen ' + manifest + ' member: ' + name)
        counts.append(len(rows))
        if manifest == 'SOURCES.json':
            source_rows = rows
    require(counts == [state['source_files'], state['private_evidence_files']], 'Different frozen evidence populations')
    # The actual Python entrypoint and imported transition code must match the
    # selected context, including when an archived context is requested.
    code_folder = Path(__file__).absolute().parent
    for module in tuple(sys.modules.values()):
        name = getattr(module, '__file__', None)
        if name is None:
            continue
        path = Path(name).absolute()
        if path.suffix == '.py' and path.parent == code_folder:
            require(reader.pin(path) == source_rows['tools/literature/' + path.name],
                    'Running verifier differs; use the exact archived entrypoint and imports: ' + path.name)
    q = json.loads((source / catalog.Q).read_bytes())
    model.qualified(q)
    require(reader.pin(source / catalog.Q) == state['qualification'], 'Different source-context qualification')
    catalog.private_controls(source, private, q)
    result = catalog.check(source, artifact)
    require(result == state['catalog_result'], 'Different catalog admission')
    reader.unchanged()
    return dict(status='PASS_FROZEN_DIA_NN_WORKFLOW_SOURCE_AND_PRIVATE_EVIDENCE',
        source_files=counts[0], private_evidence_files=counts[1], physical_files_read=len(reader.memo),
        producer=q['producer'], catalog_result=result, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--private-root', type=Path, required=True)
    parser.add_argument('--source-context', type=Path)
    parser.add_argument('--artifact-root', type=Path, help='Unchanged historical artifacts outside an archived source context')
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.root, args.private_root, args.source_context or args.root, args.artifact_root), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT SAVED AUDIT SKIPPED: ' if code == 3 else 'FAIL2 NO SAVED AUDIT ADMITTED: ')
              + str(error) + '. Required action: preserve attempts and old hashes; restore exact permitted '
              'sources, producing JAR and private evidence. Use the frozen entrypoint for historical contexts.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
