# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pin original managed Salmon runtime archives and complete contained payloads."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import tarfile

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    proof=json.loads((ROOT/'docs/literature/salmon-native-development/VALIDATION.json').read_bytes());capture=ROOT/'build/literature-review/salmon-runtime/upstream-capture-1'
    source_path=ROOT/'tools/literature/salmon-runtime-lock.json';resource=ROOT/'src/main/resources/org/jlinalg/omics/salmon-runtime-lock.json';assert not source_path.exists() and not resource.exists()
    startup={r['version']:r for r in proof['original_startup_validation']['releases']};profiles={}
    for release in proof['official_capture']:
        version=release['version'];archive=next(r for r in release['files'] if '/releases/download/' in r['url']);name=archive['url'].rsplit('/',1)[1];local=capture/version/name
        assert pin(local)=={k:archive[k] for k in ('bytes','sha256')}
        members={};prefix=None
        with tarfile.open(local) as t:
            for m in t.getmembers():
                path=PurePosixPath(m.name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in m.name
                if prefix is None:prefix=path.parts[0]
                assert path.parts[0]==prefix and not m.mode&0o6000
                if len(path.parts)==1:assert m.isdir();continue
                relative=PurePosixPath(*path.parts[1:]).as_posix();assert relative not in members
                expected=startup[version]['payload'][relative]
                if m.isdir():
                    assert expected['kind']=='directory';row=dict(expected)
                    if expected['mode'] != m.mode&0o777:
                        assert version in {'1.0.0','1.1.0'} and relative in {'bin','lib'} and m.mode&0o777==0o775 and expected['mode']==0o755
                        row['archive_mode']=m.mode&0o777
                elif m.isfile():
                    assert expected['kind']=='file' and expected['bytes']==m.size
                    row=dict(expected)
                    if expected['mode'] != m.mode&0o777:
                        # Python's safe data filter removes group-write on the
                        # unused sample archive in these two original releases.
                        # Keep both modes; no program/library bytes or modes differ.
                        assert version in {'1.0.0','1.1.0'} and relative=='sample_data.tgz' and m.mode&0o777==0o664 and expected['mode']==0o644
                        row['archive_mode']=m.mode&0o777
                elif m.islnk():
                    link=PurePosixPath(m.linkname);assert not link.is_absolute() and '..' not in link.parts and link.parts[0]==prefix
                    target=PurePosixPath(*link.parts[1:]).as_posix();assert expected['kind']=='file'
                    row=dict(kind='hardlink',target=target,mode=expected['mode'],bytes=expected['bytes'],sha256=expected['sha256'])
                elif m.issym():
                    assert expected['kind']=='symlink' and expected['target']==m.linkname;row=dict(expected)
                else:raise AssertionError('Special runtime archive member')
                members[relative]=row
        assert set(members)==set(startup[version]['payload']) and all(r['kind'] in ['directory','file','hardlink','symlink'] for r in members.values())
        for relative,row in members.items():
            if row['kind']=='hardlink':assert row['target'] in members and members[row['target']]['sha256']==row['sha256']
        source=next(r for r in release['files'] if 'codeload.github.com' in r['url'])
        profiles['linux-x86_64-salmon-'+version]=dict(version=version,archive=dict(filename=name,**archive),archive_root=prefix,members=members,
            source_archive=source,license='GPL-3.0-or-later',permission_normalizations=[dict(path=name,archive_mode=row['archive_mode'],materialized_mode=row['mode']) for name,row in members.items() if 'archive_mode' in row],runtime_platform='Linux x86_64; original dynamically linked release; separate current-host and scientific health admission required')
    assert len(profiles)==5
    raw=(json.dumps(dict(schema_version=1,tool='Salmon',profiles=profiles,scope='Exact original archives and complete runtime payload only; this lock does not admit a host or user analysis.'),indent=2)+'\n').encode()
    source_path.write_bytes(raw);resource.parent.mkdir(parents=True,exist_ok=True);resource.write_bytes(raw)
    print(json.dumps(dict(profiles=len(profiles),bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())))


if __name__=='__main__':main()
