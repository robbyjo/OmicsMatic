#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Archive a closed standalone workflow/replay bundle without reading any literal software payload links."""
from pathlib import Path
import argparse, hashlib, json, shutil, traceback, zipfile

ROOT=Path(__file__).resolve().parents[3]
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')

def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):digest.update(block);size+=len(block)
    return dict(bytes=size,sha256=digest.hexdigest())

def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();source=args.source.resolve();out=args.out.resolve()
    assert out.is_relative_to(ROOT/'build') and not source.is_relative_to(out) and not out.is_relative_to(source)
    out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-archiver.py')
    try:
        assert (source/'COMPLETED.json').is_file() and not (source/'FAILURE.json').exists()
        manifest=json.loads((source/'manifest.json').read_bytes());marker=json.loads((source/'COMPLETED.json').read_bytes())
        assert marker['manifest']==pin(source/'manifest.json')
        files={**manifest['files'],'manifest.json':pin(source/'manifest.json'),'COMPLETED.json':pin(source/'COMPLETED.json')}
        observed={path.relative_to(source).as_posix():pin(path) for path in source.rglob('*') if path.is_file()}
        directories=sorted(path.relative_to(source).as_posix() for path in source.rglob('*') if path.is_dir())
        assert files==observed and directories==sorted(manifest['directories'])
        archive=out/'bundle.zip'
        with zipfile.ZipFile(archive,'x',allowZip64=True) as target:
            for name in directories:
                path=source/name;assert not path.is_symlink();target.write(path,name+'/')
            for name,expected in sorted(files.items()):
                path=source/name;assert not path.is_symlink() and path.is_file() and pin(path)==expected
                target.write(path,name,compress_type=zipfile.ZIP_STORED if '/objects/' in '/'+name else zipfile.ZIP_DEFLATED,compresslevel=None if '/objects/' in '/'+name else 3)
                assert pin(path)==expected
        assert files=={path.relative_to(source).as_posix():pin(path) for path in source.rglob('*') if path.is_file()}
        write(out/'ARCHIVED.json',dict(status='PASS_ARCHIVED_STANDALONE_CLOSED_WORKFLOW_BUNDLE_NOT_PUBLIC_ANALYSIS_ADMISSION',archive=pin(archive),source=str(source),source_kind=marker['kind'],source_manifest=marker['manifest'],files=files,directories=directories,workflow_executed=False,user_analysis_admitted=False,whole_goal_complete=False))
        print('PASS_ARCHIVED_STANDALONE_BUNDLE '+str(len(files))+' FILES',flush=True)
    except BaseException as error:
        write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Preserve the original bundle and partial archive. Resolve the archival error and retry a NEW archive path; do not rerun analysis because archival failed.'));raise

if __name__=='__main__':main()
