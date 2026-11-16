#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inventory the exact release/development source transition; not catalog admission."""
from pathlib import Path,PurePosixPath
import argparse,base64,hashlib,json,os,subprocess,sys
MODULE=Path(__file__).resolve().parent;ROOT=MODULE.parents[2]
sys.dont_write_bytecode=True
sys.path.insert(0,str(MODULE.parent))
from catalog_source_transition.audit_source import META

PARENT='1a9ff82adbf53e7b36fad065a9b45b1ab1c018f6'
TARGET='a97510234fa2fb992341d5d6ad9bf2741204a639'
OWN='tools/literature/catalog_source_transition_v2'
GUIDE='docs/literature/APPLICATION_SOURCE_TRANSITION_20261116.md'
EVIDENCE='docs/literature/application-source-transition-20261116'
def git(root,*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=os.fspath(root),stderr=subprocess.PIPE)
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
    digest=hashlib.sha256();size=0
    with Path(path).open('rb') as stream:
        for raw in iter(lambda:stream.read(1<<20),b''):digest.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=digest.hexdigest())
def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def tree(root,commit):
    result={}
    for entry in git(root,'ls-tree','-rz','--full-tree',commit).split(b'\0'):
        if not entry:continue
        information,name=entry.split(b'\t',1);mode,kind,oid=information.decode().split()
        if kind!='blob' or mode not in ['100644','100755']:raise ValueError('Nonregular tracked object needs separate source review: '+name.decode())
        result[name.decode('utf-8')]=dict(mode=mode,kind=kind,git_object=oid)
    return result
def blob_pins(root,entries):
    result={}
    process=subprocess.Popen(['git','--no-replace-objects','cat-file','--batch'],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    try:
        for oid in sorted({row['git_object'] for row in entries.values()}):
            process.stdin.write((oid+'\n').encode());process.stdin.flush();header=process.stdout.readline().decode().strip().split()
            if len(header)!=3 or header[0]!=oid or header[1]!='blob':raise ValueError('Exact Git blob unavailable: '+oid)
            size=int(header[2]);digest=hashlib.sha256();remaining=size
            while remaining:
                raw=process.stdout.read(min(1<<20,remaining))
                if not raw:raise EOFError('Incomplete exact Git blob')
                digest.update(raw);remaining-=len(raw)
            if process.stdout.read(1)!=b'\n':raise ValueError('Git batch framing differs')
            result[oid]=dict(bytes=size,sha256=digest.hexdigest())
        process.stdin.close()
        if process.wait(timeout=30):raise ValueError('Git batch failed')
    finally:
        if process.poll() is None:process.kill();process.wait()
    return result
def category(name):
    if name=='src/main/java/org/jlinalg/cli/JLinAlgCli.java':return 'main-cli-dispatch-and-help'
    if name.startswith('src/main/java/') and Path(name).stem in ['GeNorm','GeNormCli']:return 'native-genorm-development'
    if name.startswith('src/main/java/') and Path(name).stem.startswith(('Sambamba','BamBai','BamIndexStream')):return 'original-sambamba-and-native-bam-index-development'
    if name.startswith(('src/test/java/','src/test/resources/')):return 'regression-test-source-and-synthetic-fixtures'
    if name in ['build.gradle','.github/workflows/release.yml']:return 'release-build-and-workflow'
    if name=='.gitattributes':return 'explicit-release-receipt-byte-preservation'
    if name.startswith(('docs/','site/')) or name in ['README.md','RELEASE_NOTES.md','CHANGELOG.md']:return 'documentation-website-and-frozen-release-records'
    if name.startswith(('tools/literature/bai_validation/','tools/literature/genorm_native/','tools/literature/sambamba_')):return 'new-development-helpers-and-record-readers'
    if name.startswith(('examples/sambamba-','examples/genorm-')):return 'explicit-development-request-examples'
    return 'UNREVIEWED'
def inventory(root,out):
    if git(root,'rev-parse','HEAD').decode().strip()!=TARGET:raise ValueError('Exact declared target checkout required')
    if git(root,'diff','--name-only','HEAD'):raise ValueError('Tracked target sources changed')
    out.mkdir(parents=True,exist_ok=False);before=tree(root,PARENT);after=tree(root,TARGET);blobs=blob_pins(root,after);current={}
    for name,entry in after.items():
        identity=pin(root/name);blob=blobs[entry['git_object']];allowed=[blob];conversion=None
        if identity!=blob:
            raw=(root/name).read_bytes()
            if rawpin(raw.replace(b'\r\n',b'\n'))!=blob or git(root,'hash-object','--path='+name,'--',name).decode().strip()!=entry['git_object']:
                raise ValueError('Working file has more than an explicit exact CRLF checkout conversion: '+name)
            allowed.append(identity);conversion='Only this exact captured checkout and Git blob are allowed; no file rewritten'
        current[name]=dict(**entry,allowed_file_identities=allowed,captured_working_identity=identity,checkout_conversion=conversion)
    changes=[]
    for name in sorted(set(before)|set(after)):
        if before.get(name)==after.get(name):continue
        changes.append(dict(path=name,status='A' if name not in before else 'D' if name not in after else 'M',category=category(name),before=before.get(name),after=after.get(name)))
    for name in META:
        if before[name]!=after[name]:raise ValueError('Predecessor metadata changed before new catalog transition: '+name)
    main=[row for row in changes if row['path'].startswith('src/main/java/')]
    if [row['path'] for row in main if row['status']!='A']!=['src/main/java/org/jlinalg/cli/JLinAlgCli.java']:raise ValueError('Unreviewed pre-existing production Java change')
    save(out/'TARGET-TREE.json',dict(parent=PARENT,target=TARGET,files=current))
    save(out/'CHANGE-INVENTORY.json',dict(status='UNADMITTED_EXACT_SOURCE_INVENTORY_ONLY',parent=PARENT,target=TARGET,files=changes,unknown=[row['path'] for row in changes if row['category']=='UNREVIEWED'],
        main_java_added=sum(row['status']=='A' for row in main),main_java_modified=sum(row['status']=='M' for row in main),catalog_metadata_pins={name:pin(root/name) for name in META}))
    (out/'complete-transition.patch').write_bytes(git(root,'diff','--binary',PARENT,TARGET))
    print(json.dumps(dict(status='UNADMITTED_EXACT_SOURCE_INVENTORY_CREATED',target_files=len(current),changed_files=len(changes),main_java_added=sum(row['status']=='A' for row in main),main_java_modified=sum(row['status']=='M' for row in main),unknown=[row['path'] for row in changes if row['category']=='UNREVIEWED'])),flush=True)
def source_guard(root,target_tree,extensions=None):
    root=Path(root).resolve();expected=tree(root,TARGET)
    if target_tree['target']!=TARGET or target_tree['parent']!=PARENT or set(expected)!=set(target_tree['files']):raise ValueError('Complete declared target tree differs')
    extensions={} if extensions is None else extensions
    for name,identity in extensions.items():
        path=PurePosixPath(name)
        if not name or '\\' in name or ':' in name or path.as_posix()!=name or path.is_absolute() or '..' in path.parts or name in expected or any(name.casefold()==old.casefold() for old in expected):
            raise ValueError('Extensions must name only exact NEW files; existing source cannot be excluded')
        actual_path=root/name
        if actual_path.is_symlink() or not actual_path.is_file() or not actual_path.resolve().is_relative_to(root) or pin(actual_path)!=identity:
            raise ValueError('Declared new consumer source identity differs: '+name)
    def owned(name):return name==GUIDE or name==EVIDENCE or name.startswith(EVIDENCE+'/') or name==OWN or name.startswith(OWN+'/')
    names={name.decode('utf-8') for name in git(root,'ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if name}
    actual={name for name in names if not owned(name) and name not in META and name not in extensions}
    required={name for name in expected if not owned(name) and name not in META}
    if actual!=required:raise ValueError('Unreviewed source membership differs: missing='+str(sorted(required-actual)[:8])+'; added='+str(sorted(actual-required)[:8]))
    for name in sorted(required):
        record=target_tree['files'][name]
        if any(record[key]!=expected[name][key] for key in ['git_object','mode','kind']):raise ValueError('Source target Git identity differs: '+name)
        path=root/name
        if path.is_symlink() or not path.is_file() or pin(path) not in record['allowed_file_identities']:raise ValueError('Unreviewed actual source bytes differ: '+name)
    return dict(target=TARGET,actual_source_files_checked=len(required),declared_new_consumer_files_checked=len(extensions),actual_bytes_hashed=True,Git_stat_cache_used_as_content_proof=False,old_scientific_files_excluded=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();inventory(ROOT,a.out.resolve())
