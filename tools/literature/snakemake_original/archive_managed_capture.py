#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Archive completed native capture evidence with shared blobs, avoiding per-case mounted-filesystem copies."""
from pathlib import Path
import argparse,hashlib,json,shutil,sys,traceback,zipfile
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):digest.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=digest.hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--native',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-archiver.py');source=a.native;assert sys.platform=='linux' and source==source.resolve() and not str(source).startswith('/mnt/');completed=json.loads((source/'COMPLETED.json').read_bytes());assert len(completed['cases'])==18 and completed['original_software_unchanged'] and not completed['user_analysis_admitted'];before=pin(source/'COMPLETED.json');archive=out/'managed-capture-evidence.zip';refs={};blobs={}
    try:
        with zipfile.ZipFile(archive,'x',allowZip64=True) as z:
            def add(path,name,blob=False):
                assert path.is_file() and not path.is_symlink() and not Path(name).is_absolute() and '..' not in Path(name).parts;value=pin(path)
                if name in refs:assert refs[name]['pin']==value;return
                member='objects/'+path.name if blob else 'metadata/'+name
                if blob:
                    assert value['sha256']==path.name
                    if path.name in blobs:assert blobs[path.name]==value
                    else:z.write(path,member,compress_type=zipfile.ZIP_STORED);blobs[path.name]=value
                else:z.write(path,member,compress_type=zipfile.ZIP_DEFLATED,compresslevel=3)
                refs[name]=dict(member=member,pin=value)
            for path in source.iterdir():
                if path.is_file():add(path,path.name)
            for path in (source/'as-run').rglob('*'):
                if path.is_file():add(path,'as-run/'+path.relative_to(source/'as-run').as_posix())
            for row in completed['cases']:
                case=source/'cases'/row['case'];prefix='cases/'+row['case']+'/'
                for path in case.iterdir():
                    if path.is_file():add(path,prefix+path.name)
                execution=case/'execution'
                for path in execution.iterdir():
                    if path.is_file():add(path,prefix+path.name)
                for directory in ['objects','helpers','events','process','runtime','deployment','policy-records']:
                    for path in (execution/directory).rglob('*'):
                        if path.is_file():add(path,prefix+directory+'/'+path.relative_to(execution/directory).as_posix(),directory=='objects')
            index=dict(schema_version=1,scope='Development captured-execution evidence only. Metadata paths and virtual per-case blob files share immutable archive objects; this is not a managed analysis bundle.',source_native=str(source),source_completed=before,files=refs,blobs=blobs)
            z.writestr('INDEX.json',json.dumps(index,indent=2)+'\n',compress_type=zipfile.ZIP_DEFLATED,compresslevel=3)
        assert pin(source/'COMPLETED.json')==before
        write(out/'ARCHIVED.json',dict(status='ARCHIVED_COMPLETE_NATIVE_MANAGED_CAPTURE_DEVELOPMENT_EVIDENCE',archive=pin(archive),source_completed=before,metadata_and_virtual_blob_references=len(refs),unique_blobs=len(blobs),workflow_runs_repeated=False,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_COMPACT_MANAGED_CAPTURE_ARCHIVE '+str(len(refs))+' REFERENCES '+str(len(blobs))+' BLOBS',flush=True)
    except BaseException as error:write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain the partial archive and every native execution. Resolve the archival cause and retry a NEW archive path. Do not rerun analyses merely because export failed.'));raise
if __name__=='__main__':main()
