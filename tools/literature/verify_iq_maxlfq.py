"""Verify frozen iq MaxLFQ/catalog sources and retained private evidence without resealing."""
from pathlib import Path
import argparse
import hashlib
import json
import sys
from gemma_primary import require
from iq_catalog import check

ROOT = Path(__file__).resolve().parents[2]
FOLDER = 'docs/literature/iq-maxlfq-validation'


def filepin(path):
    require(path.is_file() and not path.is_symlink() and path.resolve() == path.absolute(), 'Require canonical regular file: ' + str(path))
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def audit(root, private, source):
    root, private, source = (Path(x).absolute() for x in (root,private,source))
    folder = root / FOLDER
    state = json.loads((folder/'STATUS.json').read_bytes())
    require(filepin(folder/'SOURCES.json') == state['sources'] and filepin(folder/'PRIVATE-INDEX.json') == state['private_index']
            and filepin(folder/'QUALIFICATION.json') == state['qualification'], 'Changed checkpoint declarations')
    counts = []
    for manifest, base in (('SOURCES.json',source),('PRIVATE-INDEX.json',private)):
        rows = json.loads((folder/manifest).read_bytes())
        for name, expected in rows.items():
            require(name and '\\' not in name and not name.startswith('/') and ':' not in name
                    and all(p not in ('','.','..') for p in name.split('/')), 'Unsafe evidence member')
            require(filepin(base/name) == expected, 'Changed frozen ' + manifest + ' member: ' + name)
        counts.append(len(rows))
    require(counts == [state['source_files'],state['private_evidence_files']], 'Changed frozen populations')
    q = json.loads((folder/'QUALIFICATION.json').read_bytes())
    require(filepin(root/q['producer_private_path']) == q['producer'], 'Restore exact producer; never refresh saved hashes')
    result = check(root, root)
    require(result == state['catalog_result'], 'Different catalog admission')
    return dict(status='PASS_FROZEN_NATIVE_IQ_SOURCE_AND_PRIVATE_EVIDENCE',source_files=counts[0],
                private_evidence_files=counts[1],producer=q['producer'],catalog_result=result,whole_goal_complete=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT)
    p.add_argument('--private-root',type=Path,required=True);p.add_argument('--source-context',type=Path)
    a=p.parse_args()
    try:
        print(json.dumps(audit(a.root,a.private_root,a.source_context or a.root),sort_keys=True));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        print(('SKIP3 DEPENDENT SAVED AUDIT SKIPPED: 'if code==3 else'FAIL2 NO SAVED AUDIT ADMITTED: ')+str(error)
              +'. Required action: preserve attempts and old hashes; restore the exact permitted source, producer and private evidence.',file=sys.stderr)
        return code


if __name__=='__main__':
    raise SystemExit(main())
