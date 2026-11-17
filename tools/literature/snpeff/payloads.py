# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently inspect generated manifest payloads and bind retained current verifications."""
from pathlib import Path,PurePosixPath
import base64,hashlib,json,re
from .qualification import pin,write

JARS={'5.2':'3d630f73332cff635f64301d06e8cc3c12889c794903b8b9be3a82227e47a291','3.1i-r354-source':'d29e6257ee81795f895c8c1f94c10f9be00a616ce30cc1a662b36498d7ac6b27'}

def properties(path):
    """Only generated scalar/manifest properties, not arbitrary user or upstream configs."""
    raw=Path(path).read_bytes();assert len(raw)<=64*1024*1024
    result={}
    for line in raw.decode('ascii').splitlines():
        if not line or line.startswith(('#','!')):continue
        key,separator,value=line.partition('=')
        assert separator and key not in result and re.fullmatch(r'[A-Za-z0-9_.-]+',key) and '\\' not in value
        result[key]=value
    return result

def bundle(path):
    path=Path(path);assert path.is_dir() and not path.is_symlink()
    marker=properties(path/'COMPLETED.properties')
    assert marker['profile']=='original-snpeff-annotation-v1' and marker['original_engine_executed']=='true'
    assert marker['edition'] in JARS and marker['genuine_replay'] in ['true','false']
    manifest=pin(path/'manifest.properties');assert manifest['sha256']==marker['manifest_sha256']
    encoded=properties(path/'manifest.properties');expected={}
    for key,value in encoded.items():
        kind,separator,tag=key.partition('.');assert separator and kind in ['sha256','bytes']
        name=base64.urlsafe_b64decode(tag+'='*((-len(tag))%4)).decode('utf-8')
        assert base64.urlsafe_b64encode(name.encode()).decode().rstrip('=')==tag
        plain=name[:-1] if name.endswith('/') else name;relative=PurePosixPath(plain)
        assert not relative.is_absolute() and '..' not in relative.parts and relative.as_posix()==plain and '\\' not in plain
        expected.setdefault(name,{})[kind]=value
    observed={}
    for target in path.rglob('*'):
        assert not target.is_symlink() and not (hasattr(target,'is_junction') and target.is_junction())
        name=target.relative_to(path).as_posix()
        if target.is_dir():observed[name+'/']=dict(bytes='0',sha256='directory')
        else:
            assert target.is_file()
            if name not in ['manifest.properties','COMPLETED.properties']:
                current=pin(target);observed[name]=dict(bytes=str(current['bytes']),sha256=current['sha256'])
    assert observed==expected,'Captured payload membership/content changed: '+str(path)
    assert pin(path/'runtime/snpeff.jar')['sha256']==JARS[marker['edition']]
    assert properties(path/'PROCESS-FINISHED.properties')['exit_code']=='0'
    assert int(marker['input_records'])>0 and int(marker['annotation_rows'])>0 and int(marker['diagnostic_rows'])>=0
    if marker['genuine_replay']=='true':
        parent=properties(path/'PARENT.properties');assert re.fullmatch(r'[a-f0-9]{64}',parent['parent_manifest_sha256'])
        for name in ['original.stdout','annotations.tsv','diagnostics.tsv']:assert pin(path/name)['sha256']==parent[name+'.expected_sha256']
    if marker.get('observed_diagnostics_summary')=='true':assert (path/'SOURCE-DIAGNOSTICS.tsv').is_file()
    return dict(manifest=manifest,completion=pin(path/'COMPLETED.properties'),edition=marker['edition'],runtime_sha256=JARS[marker['edition']],input_records=int(marker['input_records']),annotation_rows=int(marker['annotation_rows']),diagnostic_rows=int(marker['diagnostic_rows']),genuine_replay=marker['genuine_replay']=='true',payload_entries=len(observed),raw=pin(path/'original.stdout'),annotations=pin(path/'annotations.tsv'),diagnostics=pin(path/'diagnostics.tsv'))

def inspect(folder,workflow_root):
    folder,workflow_root=map(Path,[folder,workflow_root])
    source={path.name:path for path in (workflow_root/'development-evidence-1/bundles').iterdir()}
    source['p002-published-reference']=workflow_root/'p002-control-1/run'
    snapshots={name:bundle(path) for name,path in sorted(source.items())}
    verifications={}
    for name,path in sorted(source.items()):
        result=folder/('verify-'+name);marker=properties(result/'VERIFIED.properties')
        assert marker['status']=='PASS_PORTABLE_INTEGRITY_AND_NATIVE_FORMAT_NO_ENGINE_EXECUTION'
        assert marker['source_manifest_sha256']==snapshots[name]['manifest']['sha256']
        assert int(marker['annotation_rows'])==snapshots[name]['annotation_rows'] and int(marker['diagnostic_rows'])==snapshots[name]['diagnostic_rows']
        assert pin(result/'annotations.tsv')==snapshots[name]['annotations'] and pin(result/'diagnostics.tsv')==snapshots[name]['diagnostics']
        verifications[name]=dict(marker=pin(result/'VERIFIED.properties'),table=pin(result/'annotations.tsv'),diagnostics=pin(result/'diagnostics.tsv'))
    for label,parent in [('human','human-original'),('p002','p002-published-reference')]:
        name='replay-'+label;snapshots[name]=bundle(folder/name);assert snapshots[name]['genuine_replay']
        assert all(snapshots[name][key]==snapshots[parent][key] for key in ['raw','annotations','diagnostics'])
        parent_marker=properties(folder/name/'PARENT.properties');assert parent_marker['parent_manifest_sha256']==snapshots[parent]['manifest']['sha256']
        result=folder/('verify-'+name);marker=properties(result/'VERIFIED.properties')
        assert marker['status']=='PASS_PORTABLE_INTEGRITY_AND_NATIVE_FORMAT_NO_ENGINE_EXECUTION' and marker['source_manifest_sha256']==snapshots[name]['manifest']['sha256']
        assert pin(result/'annotations.tsv')==snapshots[name]['annotations'] and pin(result/'diagnostics.tsv')==snapshots[name]['diagnostics']
        verifications[name]=dict(marker=pin(result/'VERIFIED.properties'),table=pin(result/'annotations.tsv'),diagnostics=pin(result/'diagnostics.tsv'))
    assert (snapshots['p002-published-reference']['input_records'],snapshots['p002-published-reference']['annotation_rows'],snapshots['p002-published-reference']['diagnostic_rows'])==(67,78,0)
    assert (snapshots['human-original']['input_records'],snapshots['human-original']['annotation_rows'],snapshots['human-original']['diagnostic_rows'])==(969,11905,1871)
    return dict(status='PASS_ALL_CAPTURED_PAYLOADS_CURRENT_NATIVE_VERIFICATIONS_AND_GENUINE_REPLAY_LINKS',bundles=snapshots,verifications=verifications,source_files_modified=False,cryptographic_authenticity_claimed=False)
