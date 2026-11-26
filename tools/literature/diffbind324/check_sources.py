"""Check every original source archive before preparing a permitted DiffBind runtime."""
from pathlib import Path
import csv,hashlib,sys

def check(cache):
    cache=cache.resolve(strict=True)
    with Path(__file__).with_name('source-plan.csv').open(encoding='utf-8',newline='')as stream:plan=list(csv.DictReader(stream))
    if len(plan)!=136 or len({p['package']for p in plan})!=136:raise ValueError('Invalid complete 136-package source plan')
    total=0
    for index,row in enumerate(plan,1):
        name=row['filename']
        if int(row['order'])!=index or Path(name).name!=name or '/'in name or '\\'in name:raise ValueError('Invalid ordered archive namespace')
        path=cache/name
        if path.is_symlink()or not path.is_file():raise FileNotFoundError('Complete original archive unavailable: '+str(path))
        before=path.stat();sha=hashlib.sha256();md5=hashlib.md5();size=0
        with path.open('rb')as stream:
            for block in iter(lambda:stream.read(1048576),b''):sha.update(block);md5.update(block);size+=len(block)
        after=path.stat()
        if(before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns):raise ValueError('Source archive changed during verification: '+name)
        if(size,sha.hexdigest(),md5.hexdigest())!=(int(row['bytes']),row['sha256'],row['repository_md5']):raise ValueError('Original source bytes differ: '+name)
        total+=size
    return len(plan),total

if __name__=='__main__':
    try:
        if len(sys.argv)!=2:raise ValueError('Usage: python check_sources.py EXISTING_SOURCE_CACHE')
        count,total=check(Path(sys.argv[1]));print('PASS_ALL_ORIGINAL_DIFFBIND_RUNTIME_SOURCES',count,'archives',total,'bytes. Metadata/source verification only; no software was installed and no analysis was performed.')
    except Exception as error:
        missing=isinstance(error,(FileNotFoundError,PermissionError));code=3 if missing else 2
        print(('SKIP3 DEPENDENT RUNTIME PREPARATION SKIPPED: 'if missing else'FAIL2 NO VERIFIED RUNTIME SOURCE SET: ')+str(error)+'. Required action: obtain the exact permitted originals from source-plan.csv, preserve failed attempts, and rerun this read-only check. If download or installation is not permitted, skip dependent analysis.',file=sys.stderr);raise SystemExit(code)
