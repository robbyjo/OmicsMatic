#!/usr/bin/env python3
"""Prepare a pinned FAVOR runtime inventory; never install dependencies or analyze study data.

Only an inventory is produced. The Java favor-annotate command still requires
fresh original/native scientific health checks before processing a study.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import struct
import subprocess
import sys
import threading
import time

ASSETS=Path(__file__).resolve().with_name('favor-runtime')
RECOVERY=('Required action: provide a permitted canonical private Linux x86_64 R4.4.3 '
          'environment matching favor-runtime/pixi.lock,its pinned GenomeInfoDbData1.2.13 '
          'archive and a Linux Java21 JDK. Use a NEW output outside the software roots. '
          'Where installation/downloads are forbidden or unavailable,SKIP dependent analysis '
          '(exit3). This preparer never installs or downloads software. Inspect WARNINGS.md '
          'and retained logs,correct the cause and create a NEW inventory. Scientific '
          'admission still requires the favor-annotate health checks.')

class Unavailable(RuntimeError):
    """Missing,forbidden,changed or unsupported software;dependent analysis skips3."""

class CapacityError(ValueError):
    """Invalid or exceeded complete intake capacity;fail2 without truncation."""

def require(condition,message):
    if not condition:raise Unavailable(str(message))

def identity(info):
    return (info.st_dev,info.st_ino,info.st_size,info.st_mtime_ns,info.st_ctime_ns)

class HashBudget:
    def __init__(self,file_bytes,total_bytes):
        self.file_bytes=file_bytes;self.total_bytes=total_bytes;self.used=0
        self.lock=threading.Lock();self.files={}
    def consume(self,count):
        with self.lock:
            if count>self.total_bytes-self.used:raise CapacityError('Complete runtime hashing byte capacity exceeded')
            self.used+=count
    def remember(self,path,before):
        with self.lock:
            old=self.files.setdefault(path,identity(before))
            require(old==identity(before),'Runtime file changed during intake: '+str(path))
    def verify(self):
        for path,before in self.files.items():
            require(identity(path.stat())==before,'Runtime file identity changed during intake: '+str(path))

_HASH_BUDGET=None
def sha(path):
    path=Path(path);before=path.stat()
    if _HASH_BUDGET is not None and before.st_size>_HASH_BUDGET.file_bytes:raise CapacityError('Complete runtime file byte capacity exceeded: '+str(path))
    digest=hashlib.sha256();count=0
    with path.open('rb') as stream:
        while True:
            part=stream.read(65536)
            if not part:break
            if _HASH_BUDGET is not None:_HASH_BUDGET.consume(len(part))
            count+=len(part);digest.update(part)
    require(count==before.st_size and identity(path.stat())==identity(before),'Runtime file changed during hashing: '+str(path))
    if _HASH_BUDGET is not None:_HASH_BUDGET.remember(path,before)
    return digest.hexdigest()

def write(path,value):
    raw=(json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf-8')
    if len(raw)>67108864:raise CapacityError('Complete runtime metadata byte capacity exceeded')
    Path(path).write_bytes(raw)

def strict_json(path):
    path=Path(path)
    if path.stat().st_size>16777216:raise CapacityError('Package/policy metadata exceeds16MiB: '+str(path))
    def pairs(rows):
        result={}
        for key,value in rows:
            if key in result:raise Unavailable('Repeated software metadata key: '+key)
            result[key]=value
        return result
    return json.loads(path.read_text(encoding='utf-8'),object_pairs_hook=pairs,parse_constant=lambda value:(_ for _ in ()).throw(Unavailable('Nonfinite software metadata')))

def package_set(rows):
    result=set();fields={'name','version','build','subdir','sha256','url'}
    for row in rows:
        require(isinstance(row,dict) and set(row)==fields,'Invalid pinned package artifact fields')
        require(all(isinstance(v,str) and v and '\n' not in v for v in row.values()),'Invalid pinned package artifact text')
        require(re.fullmatch('[a-f0-9]{64}',row['sha256']),'Invalid package SHA256')
        key=tuple(row[k] for k in ['name','version','build','subdir','sha256','url'])
        require(key not in result,'Repeated package artifact');result.add(key)
    return result

def read_policy():
    policy=strict_json(ASSETS/'runtime-policy.json')
    require(policy['schema_version']==1 and policy['profile']=='favorannotator-csv-r4.4.3-linux-x86_64','Unsupported bundled runtime policy')
    require(sha(ASSETS/'pixi.lock')==policy['build_lock_sha256'],'Pinned Pixi build lock changed')
    require(len(package_set(policy['conda_packages']))==154,'Incomplete pinned Conda artifact policy')
    return policy

def canonical_directory(supplied):
    path=Path(os.path.abspath(supplied))
    require(path.is_dir() and path.resolve()==path,'Require an existing canonical nonlinked software directory: '+str(path))
    require(str(path).isascii() and not any(c.isspace() for c in str(path)),'The original FAVOR runtime requires ASCII software paths without whitespace')
    return path

def new_output(supplied):
    path=Path(os.path.abspath(supplied))
    if path.exists() or path.is_symlink():raise ValueError('Output already exists;preserve it and use a NEW directory: '+str(path))
    if not path.parent.is_dir() or path.parent.resolve()!=path.parent or not str(path).isascii() or any(c.isspace() for c in str(path)):raise ValueError('Use a NEW ASCII output below an existing canonical parent,without whitespace')
    return path

def runtime_roots(prefix,java,documentation):
    roots={'r':prefix,'java':java.parent.parent,'host-loader':Path('/etc/ld.so.conf.d')}
    if documentation is not None:roots['java-doc']=documentation
    return roots

def preflight(options,output):
    policy=read_policy();prefix=canonical_directory(options.runtime_root)
    require((prefix/'bin/Rscript').is_file() and os.access(prefix/'bin/Rscript',os.X_OK),'Private Rscript is absent or execution is forbidden')
    require(sha(prefix/'bin/xsv')=='b57364e384b7e2321215bc9b200d8afe6931a450ac4425df8220067a93959925','Private xsv is missing or differs from pinned Linux0.13.0 binary')
    java=Path(options.java).resolve(strict=True);require(java.is_file() and os.access(java,os.X_OK),'Selected Linux Java is absent or execution is forbidden')
    require(str(java).isascii() and not any(c.isspace() for c in str(java)),'Use canonical ASCII Linux Java software path')
    source_root=canonical_directory(options.source_root or ASSETS/'upstream')
    for name,expected in policy['original_source_sha256'].items():require(sha(source_root/name)==expected,'Pinned original FAVOR script differs: '+name)
    require(options.data_archive is not None,'Supply --data-archive with the pinned GenomeInfoDbData1.2.13 source archive')
    data_archive=Path(options.data_archive).resolve(strict=True);require(sha(data_archive)==policy['GenomeInfoDbData_archive_sha256'],'GenomeInfoDbData source archive SHA256 differs')
    metadata=[]
    for file in sorted((prefix/'conda-meta').glob('*.json')):
        row=strict_json(file);metadata.append({key:row.get(key) for key in ['name','version','build','subdir','sha256','url']})
    require(package_set(metadata)==package_set(policy['conda_packages']),'Installed Conda artifacts differ from the pinned software policy')
    doc=Path(options.java_documentation).resolve(strict=True) if options.java_documentation else None
    if doc is None and Path('/usr/share/doc/openjdk-21-jre-headless').is_dir():doc=Path('/usr/share/doc/openjdk-21-jre-headless')
    for root in [prefix,java.parent.parent,Path('/etc/ld.so.conf.d'),*([doc] if doc else [])]:
        if output.is_relative_to(root) or root.is_relative_to(output):raise ValueError('Preparation output must remain outside fingerprinted software/loader roots')
    return prefix,java,doc,policy,data_archive,source_root

def build_inventory(options,O,prefix,java_executable,java_documentation,policy,data_archive,source_root):
    work = O / 'probe-work'
    work.mkdir()
    [p.mkdir() for p in [work / 'home', work / 'tmp', work / 'empty-library']]
    env = {'PATH': str(prefix / 'bin') + ':/usr/bin:/bin', 'HOME': str(work / 'home'), 'TMPDIR': str(work / 'tmp'), 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8', 'R_LIBS_USER': str(work / 'empty-library'), 'R_LIBS_SITE': str(prefix / 'lib/R/library'), 'R_ENVIRON_USER': '/dev/null', 'R_PROFILE_USER': '/dev/null'}
    probe_source = (ASSETS / 'probe.R').read_text(encoding='utf-8')
    (O / 'probe.R').write_text(probe_source, encoding='utf-8')
    shutil.copy2(ASSETS / 'FavorRuntimeJavaProbe.java', O / 'FavorRuntimeJavaProbe.java')
    p = subprocess.run([str(prefix / 'bin/Rscript'), '--vanilla', str(O / 'probe.R'), str(work), str(O / 'probe.json')], cwd=work, env=env, capture_output=True, timeout=90)
    (O / 'probe.stdout').write_bytes(p.stdout)
    (O / 'probe.stderr').write_bytes(p.stderr)
    require(p.returncode == 0, p.stderr[-3000:])
    probe = json.loads((O / 'probe.json').read_text())
    child_env = dict(env)
    child_env['LD_LIBRARY_PATH'] = probe['loader_path']
    print(json.dumps(dict(stage='R-probe', loader_path=probe['loader_path'])), flush=True)
    java = java_executable
    java_maps = {}
    for label, environment in [('clean', env), ('R-child', child_env)]:
        out = O / ('java-' + label + '.tsv')
        p = subprocess.run([str(java), '-Djava.awt.headless=true', '-Djava.io.tmpdir=' + str(work / 'tmp'), '--source', '17', str(O / 'FavorRuntimeJavaProbe.java'), str(out)], cwd=work, env=environment, capture_output=True, timeout=60)
        (O / ('java-' + label + '.stdout')).write_bytes(p.stdout)
        (O / ('java-' + label + '.stderr')).write_bytes(p.stderr)
        require(p.returncode == 0, p.stderr[-3000:])
        fields = {}
        paths = []
        for line in out.read_text().splitlines():
            key, value = line.split('\t', 1)
            if key == 'mapped':
                paths.append(value)
            else:
                fields[key] = value
        java_maps[label] = dict(fields=fields, mapped_paths=sorted(set(paths)))
    roots = runtime_roots(prefix,java,java_documentation)
    trees = {}
    total = 0
    external = set()
    broken = []
    unexpanded = set()
    for label, root in roots.items():
        require(root.is_dir() and root.resolve() == root, 'Required FAVOR runtime inventory invariant failed')
        entries = {}
        stack = [root]
        started = time.monotonic()
        while stack:
            parent = stack.pop()
            for path in sorted(parent.iterdir()):
                relative = path.relative_to(root).as_posix()
                info = path.lstat()
                mode = stat.S_IMODE(info.st_mode)
                if stat.S_ISLNK(info.st_mode):
                    real = path.resolve(strict=False)
                    record = dict(kind='symlink', mode=mode, target=os.readlink(path), resolved=real.as_posix())
                    if real.is_file():
                        record.update(target_kind='file', bytes=real.stat().st_size, sha256=sha(real))
                        total += real.stat().st_size
                    elif real.is_dir():
                        record['target_kind'] = 'directory'
                    else:
                        record['target_kind'] = 'missing'
                        broken.append(dict(root=label, path=relative, target=os.readlink(path)))
                    if not any((real.is_relative_to(r) for r in roots.values())):
                        if real.is_file():
                            external.add(real)
                        elif real.is_dir():
                            unexpanded.add(real)
                elif stat.S_ISDIR(info.st_mode):
                    record = dict(kind='directory', mode=mode)
                    stack.append(path)
                elif stat.S_ISREG(info.st_mode):
                    record = dict(kind='file', mode=mode, bytes=info.st_size, sha256=sha(path))
                    total += info.st_size
                else:
                    raise AssertionError(path)
                entries[relative] = record
                if len(entries) > options.max_entries:
                    raise CapacityError('Runtime root entry capacity exceeded: ' + label)
                if len(entries) % 4000 == 0:
                    print(json.dumps(dict(stage='tree', root=label, entries=len(entries))), flush=True)
        trees[label] = dict(sorted(entries.items()))
        print(json.dumps(dict(stage='tree-complete', root=label, entries=len(entries), seconds=time.monotonic() - started)), flush=True)
    require(not unexpanded, 'Required FAVOR runtime inventory invariant failed')
    host_paths = {Path('/bin/sh'), Path('/bin/bash'), Path('/usr/bin/mkdir'), Path('/usr/bin/ldd'), Path('/etc/ld.so.cache'), Path('/etc/ld.so.conf')}
    host_paths.update(external)
    host_paths.update((Path(p) for p in probe['tools'].values()))
    seeds = {}
    origins = {}
    role_maps = {'R': probe['mapped_paths'], **{'java-' + k: v['mapped_paths'] for k, v in java_maps.items()}}
    role_members = {role: {Path(name).resolve(strict=False) for name in names if name.startswith('/')} for role, names in role_maps.items()}

    def elf(path):
        try:
            with path.open('rb') as stream:
                return stream.read(4) == b'\x7fELF'
        except (FileNotFoundError, IsADirectoryError):
            return False

    def accept_path(name, context, role):
        if not name.startswith('/'):
            return
        require(not name.endswith(' (deleted)'), name)
        path = Path(name)
        if not path.is_file():
            return
        if not any((path.is_relative_to(root) for root in roots.values())) and (not path.is_relative_to(work)):
            host_paths.add(path)
        if elf(path):
            key = (context, path.as_posix())
            seeds[key] = path
            origins.setdefault(key, set())
            if path.resolve() in role_members[role]:
                origins[key].add(role)
    for name in probe['mapped_paths'] + probe['dll_paths'] + [str(prefix / 'bin/Rscript'), str(prefix / 'lib/R/bin/exec/R'), '/bin/sh', '/bin/bash', probe['tools']['mkdir'], str(prefix / 'bin/xsv')]:
        accept_path(name, 'R-child', 'R')
    for context, data in java_maps.items():
        for name in data['mapped_paths'] + [str(java)]:
            accept_path(name, context, 'java-' + context)
    traces = O / 'loader-traces'
    traces.mkdir()

    def soname(path):
        with path.open('rb') as stream:
            header = stream.read(64)
            if len(header) < 64 or header[:6] != b'\x7fELF\x02\x01':
                return path.name
            offset = struct.unpack_from('<Q', header, 32)[0]
            size, count = struct.unpack_from('<HH', header, 54)
            require(size >= 56 and count <= 1024, 'Required FAVOR runtime inventory invariant failed')
            segments = []
            dynamic = None
            for i in range(count):
                stream.seek(offset + i * size)
                fields = struct.unpack('<IIQQQQQQ', stream.read(56))
                kind, flags, file_offset, address, physical, file_size, memory_size, alignment = fields
                if kind == 1:
                    segments.append((address, file_offset, file_size))
                if kind == 2:
                    dynamic = (file_offset, file_size)
            if dynamic is None:
                return path.name
            require(dynamic[1] <= 1048576, 'Required FAVOR runtime inventory invariant failed')
            stream.seek(dynamic[0])
            raw = stream.read(dynamic[1])
            table = name = None
            for i in range(0, len(raw) - 15, 16):
                tag, value = struct.unpack_from('<QQ', raw, i)
                if tag == 0:
                    break
                if tag == 5:
                    table = value
                if tag == 14:
                    name = value
            if table is None or name is None:
                return path.name
            for address, file_offset, file_size in segments:
                if address <= table < address + file_size:
                    stream.seek(file_offset + table - address + name)
                    return stream.read(4096).split(b'\x00', 1)[0].decode('utf-8')
            raise AssertionError(('unmapped ELF string table', path))
    observed = {}
    for role, paths in role_maps.items():
        table = {}
        for name in paths:
            path = Path(name)
            if path.is_file() and elf(path):
                table.setdefault(soname(path), set()).add(path.resolve())
        observed[role] = table

    def ldd(key):
        context, name = key
        environment = child_env if context == 'R-child' else env
        p = subprocess.run(['/usr/bin/ldd', name], env=environment, capture_output=True, timeout=30)
        identifier = hashlib.sha256((context + '\n' + name).encode()).hexdigest()
        (traces / (identifier + '.stdout')).write_bytes(p.stdout)
        (traces / (identifier + '.stderr')).write_bytes(p.stderr)
        text = (p.stdout + p.stderr).decode('utf-8')
        static = 'statically linked' in text or 'not a dynamic executable' in text
        bindings = []
        for line in text.splitlines():
            line = line.strip()
            if line.startswith('linux-vdso.'):
                continue
            if '=>' in line:
                library, target = line.split('=>', 1)
                path = target.strip().split(' (', 1)[0]
                library = library.strip()
            elif line.startswith('/'):
                path = line.split(' (', 1)[0]
                library = Path(path).name
            else:
                continue
            candidates = set()
            for role in origins[key]:
                candidates.update(observed[role].get(library, set()))
            require(len(candidates) <= 1, ('ambiguous observed SONAME', key, library, candidates))
            recorded_path = path
            if candidates:
                resolved = next(iter(candidates))
                resolution = 'observed-already-loaded'
                path = resolved.as_posix()
            else:
                require(path != 'not found', (context, name, library, text))
                resolved = Path(path).resolve(strict=True)
                resolution = 'ldd'
            require(resolved.is_file(), 'Required FAVOR runtime inventory invariant failed')
            bindings.append(dict(library=library, path=path, real_path=resolved.as_posix(), bytes=resolved.stat().st_size, sha256=sha(resolved), resolution=resolution, standalone_ldd_path=recorded_path, observed_process_roles=sorted(origins[key])))
        require(static or bindings, (name, text))
        return (context + '\n' + name, dict(context=context, object=name, static=static, bindings=bindings, trace=identifier), bindings)
    closure = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for key, record, bindings in pool.map(ldd, sorted(seeds)):
            closure[key] = record
            for binding in bindings:
                path = Path(binding['path'])
                if not any((path.is_relative_to(root) for root in roots.values())):
                    host_paths.add(path)
    print(json.dumps(dict(stage='loader-closure', objects=len(closure))), flush=True)
    host = {}
    for path in sorted(host_paths):
        real = path.resolve(strict=True)
        host[path.as_posix()] = dict(real_path=real.as_posix(), bytes=real.stat().st_size, sha256=sha(real))
    absent = [p for p in ['/etc/ld.so.preload'] if not Path(p).exists()]
    require(len(absent) == 1, 'Required FAVOR runtime inventory invariant failed')
    lock = ASSETS / 'pixi.lock'
    require(sha(lock) == policy['build_lock_sha256'], 'Bundled FAVOR build lock differs from pinned policy')
    locked = {}
    current = None
    for line in lock.read_text().splitlines():
        if line.startswith('- conda: '):
            current = line[len('- conda: '):]
            require(current not in locked, 'Required FAVOR runtime inventory invariant failed')
            locked[current] = None
        elif current and line.startswith('  sha256: '):
            value = line[len('  sha256: '):]
            require(re.fullmatch('[a-f0-9]{64}', value), 'Required FAVOR runtime inventory invariant failed')
            require(locked[current] is None, 'Required FAVOR runtime inventory invariant failed')
            locked[current] = value
    require(len(locked) == 154 and all(locked.values()), 'Required FAVOR runtime inventory invariant failed')
    packages = []
    for path in sorted((prefix / 'conda-meta').glob('*.json')):
        d = json.loads(path.read_text())
        record = {key: d.get(key) for key in ['name', 'version', 'build', 'subdir', 'sha256', 'url']}
        require(locked[record['url']] == record['sha256'], 'Required FAVOR runtime inventory invariant failed')
        packages.append(record)
    require(len(packages) == 154 and {p['url'] for p in packages} == set(locked), 'Required FAVOR runtime inventory invariant failed')
    profile = dict(schema_version=1, status='inventory-only-not-admitted', profile='favorannotator-csv-r4.4.3-linux-x86_64', roots={k: v.as_posix() for k, v in roots.items()}, trees=trees, host_files=host, loader_bindings=closure, required_absent_paths=absent, unexpanded_external_directories=[], declared_broken_symlinks=broken, R_probe=probe, java_probes=java_maps, conda_packages=packages, conda_packages_count=154, conda_artifacts_match_pinned_build_lock=True, build_lock_sha256=sha(lock), GenomeInfoDbData_archive_sha256=policy['GenomeInfoDbData_archive_sha256'])
    require(len(host) <= 2048 and len(closure) <= 4096, 'Host/loader inventory exceeds supported Java capacity')
    if sum(map(len,trees.values()))>options.max_entries:raise CapacityError('Complete provider entry capacity exceeded')
    require(package_set(packages) == package_set(policy['conda_packages']), 'Installed package artifacts differ from bundled source policy')
    require({p['name']: p['version'] for p in probe['packages']} == policy['R_packages'], 'Loaded R package versions differ from pinned source policy')
    require(probe['R'].startswith('R version ' + policy['R_version'] + ' '), 'R version differs from pinned source policy')
    require(all((p['path'] == str(prefix / 'lib/R/library' / p['name']) for p in probe['packages'])), 'R packages were loaded outside the selected private library')
    require(all((p['fields']['version'].startswith('21.') and p['fields']['home'] == str(java.parent.parent) for p in java_maps.values())), 'Require a Linux Java21 JDK for the source probe and supervisor')
    _HASH_BUDGET.verify()
    write(O / 'inventory.json', profile)
    summary = dict(status=profile['status'], entries={k: len(v) for k, v in trees.items()}, bytes_hashed=total, host_files=len(host), loader_objects=len(closure), already_loaded_bindings=sum((b['resolution'] == 'observed-already-loaded' for c in closure.values() for b in c['bindings'])), standalone_missing_resolved_from_actual_maps=sum((b['standalone_ldd_path'] == 'not found' for c in closure.values() for b in c['bindings'])), loader_contexts=['clean', 'R-child'], unexpanded_external_directories=[], broken_links=broken, conda_packages=154, conda_artifacts_match_pinned_build_lock=True, inventory_sha256=sha(O / 'inventory.json'), probe_sha256=sha(O / 'probe.json'), scope='Read-only complete software inventory plus small synthetic package IO only. No dependency installation,study-data analysis or scientific runtime admission. Run favor-annotate for fresh original/native scientific health checks.')
    write(O / 'observed.json', summary)
    print(json.dumps(summary, indent=2), flush=True)
    summary.update(data_archive_sha256=sha(data_archive), data_archive_path=str(data_archive), source_root=str(source_root), source_files_sha256=policy['original_source_sha256'], effective_capacity={'entries': options.max_entries, 'file_bytes': options.max_file_bytes, 'total_hashed_bytes': options.max_total_bytes}, software_installation_performed=False, scientific_runtime_admitted=False)
    write(O / 'observed.json', summary)
    return summary

class Parser(argparse.ArgumentParser):
    def error(self,message):
        self.print_usage(sys.stderr);self.exit(2,'FAVOR PREPARATION FAILED: '+message+'\nRequired action: correct the named options and use --help;existing outputs are retained.\n')

def positive(value):
    try:number=int(value)
    except ValueError as invalid:raise argparse.ArgumentTypeError('Require canonical positive integer') from invalid
    if not re.fullmatch('[1-9][0-9]*',value) or number>4503599627370496:raise argparse.ArgumentTypeError('Require bounded positive integer capacity')
    return number

def main(argv=None):
    global _HASH_BUDGET
    parser=Parser(description=__doc__,epilog=RECOVERY)
    parser.add_argument('--runtime-root',required=True,help='Installed private R/Conda prefix,not its parent Pixi project')
    parser.add_argument('--java',default='/usr/bin/java',help='Linux Java21 JDK executable')
    parser.add_argument('--java-documentation',help='Optional canonical Java documentation root if external links need inventory')
    parser.add_argument('--source-root',help='Pinned source checkout;default is the bundled unmodified offline scripts')
    parser.add_argument('--data-archive',help='Cached GenomeInfoDbData1.2.13.tar.gz,pinned by SHA256')
    parser.add_argument('--out',required=True,help='NEW canonical ASCII output outside software roots')
    parser.add_argument('--max-entries',type=positive,default=100000)
    parser.add_argument('--max-file-bytes',type=positive,default=1073741824)
    parser.add_argument('--max-total-bytes',type=positive,default=21474836480)
    options=parser.parse_args(argv);output=None;created=False
    try:
        output=new_output(options.out)
        require(sys.platform.startswith('linux') and platform.machine() in ['x86_64','amd64'],'Runtime preparation requires Linux x86_64;on Windows use Python inside WSL')
        if options.max_entries>1000000:raise CapacityError('Entry capacity exceeds the supported Java fingerprint limit')
        _HASH_BUDGET=HashBudget(options.max_file_bytes,options.max_total_bytes)
        output.mkdir();created=True
        (output/'WARNINGS.md').write_text('# FAVOR RUNTIME PREPARATION IN PROGRESS\n\nNo scientific runtime or analysis is admitted.\n\n'+RECOVERY+'\n',encoding='utf-8')
        values=preflight(options,output);summary=build_inventory(options,output,*values)
        write(output/'preparation.json',dict(status='inventory-prepared-not-scientifically-admitted',exit_code=0,inventory_sha256=summary['inventory_sha256'],source_root=summary['source_root'],runtime_policy_sha256=sha(ASSETS/'runtime-policy.json'),setup_inputs={'data_archive_sha256':summary['data_archive_sha256']},required_action='Use this new inventory and its SHA256 with favor-annotate;fresh scientific health remains mandatory.'))
        (output/'WARNINGS.md').write_text('# FAVOR INVENTORY PREPARED - SCIENTIFIC ADMISSION STILL REQUIRED\n\nNo study-data analysis,installation or network request was performed. This software fingerprint is specific to the current host and becomes unusable after recorded files or the kernel change. Keep the entire inventory/probe/loader-trace bundle.\n\nRequired action: provide inventory.json and its SHA256 to favor-annotate. Its fresh fixed original/native scientific health must pass before analysis. Preserve old snapshots and failed outputs;after changes create a NEW inventory.\n',encoding='utf-8')
        print('FAVOR INVENTORY PREPARED: '+str(output/'inventory.json')+' SHA256='+summary['inventory_sha256']+';scientific admission pending',flush=True);return 0
    except Exception as failure:
        code=3 if isinstance(failure,(Unavailable,OSError,subprocess.SubprocessError)) else 2
        status='skipped-runtime-unavailable' if code==3 else 'failed'
        message='FAVOR PREPARATION '+('SKIPPED' if code==3 else 'FAILED')+' (exit'+str(code)+'): '+str(failure)+'\n'+RECOVERY
        print(message,file=sys.stderr)
        if created:
            try:
                write(output/'failure.json',dict(status=status,exit_code=code,cause=str(failure),required_action=RECOVERY,scientific_runtime_admitted=False))
                (output/'WARNINGS.md').write_text('# '+message+'\n\nNo completed inventory or dependent analysis is admitted. Retain all partial diagnostics.\n',encoding='utf-8')
            except OSError as publication:print('FAVOR diagnostic publication also failed: '+str(publication),file=sys.stderr)
        return code
    finally:_HASH_BUDGET=None

if __name__=='__main__':raise SystemExit(main())
