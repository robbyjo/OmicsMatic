# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete runtime inventory with bounded parallel file hashing; preserve every directory, byte and symbolic-link record."""
import concurrent.futures,hashlib,json,os
from pathlib import Path

def inventory(root,progress=None,workers=8):
    root=Path(root);result={};pending=[root];files=[]
    while pending:
        directory=pending.pop()
        with os.scandir(directory) as entries:
            for entry in entries:
                path=Path(entry.path);name=path.relative_to(root).as_posix()
                if entry.is_symlink():files.append((name,path,True))
                elif entry.is_dir(follow_symlinks=False):result[name]=dict(kind='directory');pending.append(path)
                elif entry.is_file(follow_symlinks=False):files.append((name,path,False))
                else:raise ValueError('Special/nonregular runtime member: '+str(path))
    def fingerprint(path):
        before=path.stat();sha=hashlib.sha256();size=0
        with path.open('rb') as stream:
            while block:=stream.read(1048576):size+=len(block);sha.update(block)
        after=path.stat();assert (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns)==(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns) and size==after.st_size,'Runtime file changed during complete hash: '+str(path)
        return dict(bytes=size,sha256=sha.hexdigest())
    def capture(item):
        name,path,linked=item
        if not linked:return name,dict(kind='file',**fingerprint(path))
        target=os.readlink(path);resolved=path.resolve(strict=True);row=dict(kind='symlink',target=target,resolved=str(resolved),target_kind='directory' if resolved.is_dir() else 'file')
        if resolved.is_file():row['content']=fingerprint(resolved)
        return name,row
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        for count,(name,row) in enumerate(pool.map(capture,sorted(files)),1):
            assert name not in result;result[name]=row
            if progress and (count%1000==0 or count==len(files)):
                progress.write_text(json.dumps(dict(root=str(root),complete_file_records=count,total_file_records=len(files),directory_records=len(result)-count,workers=workers))+'\n',encoding='utf8')
    return dict(sorted(result.items()))
