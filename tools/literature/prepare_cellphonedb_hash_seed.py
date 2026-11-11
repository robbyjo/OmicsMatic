# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create an additive, auditable launcher revision; never rewrite first-generation evidence."""
import hashlib,json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OLD=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow'
NEW=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-hash-seed'
JAVA=ROOT/'src/main/java/org/jlinalg/cli'
PROBES=['','Spyrrow ligand','caf\u00e9','ligand\u0100','cell \U0001f9ec','#123']
STARTUP='''
# BEGIN additive effective-interpreter-startup gate, before scientific imports.
def _write_python_startup():
    request=json.loads(Path(sys.argv[2]).read_bytes());seed=request['python_hash_seed']
    if type(seed) is not int or not 0<=seed<=4294967295:raise RuntimeError('Invalid declared Python hash seed')
    flags={name:getattr(sys.flags,name) for name in ['ignore_environment','isolated','safe_path','no_user_site','hash_randomization','optimize','dont_write_bytecode']}
    expected=dict(ignore_environment=0,isolated=0,safe_path=True,no_user_site=1,hash_randomization=0 if seed==0 else 1,optimize=0,dont_write_bytecode=1)
    if flags!=expected or os.environ.get('PYTHONHASHSEED')!=str(seed):raise RuntimeError('Effective Python startup does not honor the explicit hash seed and safe launch flags; no analysis admitted')
    if sys.version_info[:3]!=(3,11,17) or (sys.hash_info.algorithm,sys.hash_info.width,sys.hash_info.hash_bits,sys.hash_info.seed_bits)!=('siphash13',64,64,128):raise RuntimeError('Unexpected bound Python hash implementation')
    probes=PROBES_LITERAL
    receipt=dict(schema_version=1,runner_revision=2,requested_seed=seed,environment_seed=os.environ['PYTHONHASHSEED'],flags=flags,python_executable=sys.executable,python_version='.'.join(map(str,sys.version_info[:3])),hash_algorithm=sys.hash_info.algorithm,hash_width=sys.hash_info.width,hash_bits=sys.hash_info.hash_bits,seed_bits=sys.hash_info.seed_bits,hashes=[dict(text=text,hash=hash(text)) for text in probes],before_scientific_imports=True)
    with (Path(sys.argv[1])/'python-startup.json').open('xb') as stream:stream.write((json.dumps(receipt,indent=2)+'\\n').encode('utf8'))
if __name__=='__main__':_write_python_startup()
# END additive effective-interpreter-startup gate.
'''.replace('PROBES_LITERAL',repr(PROBES))

def replace_once(text,old,new):
    assert text.count(old)==1,old
    return text.replace(old,new)

def generated():
    runtime=(JAVA/'CellPhoneDbWorkflowRuntime.java').read_text(encoding='utf8').replace('CellPhoneDbWorkflowRuntime','CellPhoneDbWorkflowRuntimeV2')
    old='static byte[] resource(String name)throws IOException {'
    new='static byte[] resource(String name)throws IOException {NextflowFiles.member(name);try(var override=CellPhoneDbWorkflowRuntimeV2.class.getResourceAsStream("/org/jlinalg/omics/cellphonedb-hash-seed/"+name)){if(override!=null){byte[] raw=override.readNBytes(33554433);require(raw.length<=33554432,"Packaged launcher revision member capacity exceeded");return raw;}}return CellPhoneDbWorkflowRuntime.resource(name);}\n    private static byte[] legacyResourceUnused(String name)throws IOException {'
    runtime=replace_once(runtime,old,new)
    # Remove the now unused duplicate resource reader instead of changing its source.
    start=runtime.index('    private static byte[] legacyResourceUnused(');end=runtime.index('\n',start)
    runtime=runtime[:start]+runtime[end+1:]
    runtime=replace_once(runtime,'return report;','Path startupPath=home.resolve("python-startup.json");var startupPin=NextflowFiles.pin(startupPath,65536,()->{});var startupAudit=CellPhoneDbPythonStartup.verify(startupPath,request.pythonHashSeed(),runtime.python().toString());require(startupPin.equals(NextflowFiles.pin(startupPath,65536,()->{})),"Python startup receipt changed during admission audit");report.put("python_startup_audit",startupAudit);report.put("python_startup_pin",pinObject(startupPin));return report;')
    cli=(JAVA/'CellPhoneDbWorkflowCli.java').read_text(encoding='utf8').replace('CellPhoneDbWorkflowCli','CellPhoneDbWorkflowCliV2').replace('CellPhoneDbWorkflowRuntime','CellPhoneDbWorkflowRuntimeV2').replace('CellPhoneDbSetupCli','CellPhoneDbSetupCliV2')
    notice='Runner revision2 verifies effective Python hash startup before original scientific imports and audits the receipt before whole-batch admission. First-generation hash-seed receipts are not upgraded; rerun with NEW paths when controlled hash ordering is required. '
    cli=replace_once(cli,'static final String ACTION="','static final String ACTION="'+notice)
    cli=replace_once(cli,'"pickle_deserialized",false));error.println','"pickle_deserialized",false,"python_startup",report.get("python_startup_audit")));error.println')
    cli=replace_once(cli,'state.put("status","PASS_ORIGINAL_CELLPHONEDB_WORKFLOWS");','require(NextflowFiles.fingerprint(report.get("python_startup_pin"),65536).equals(NextflowFiles.pin(out.resolve("worker/python-startup.json"),65536,()->{})),"Python startup receipt changed during native audits; no batch admitted");state.put("runner_revision",2);state.put("python_startup_audit",report.get("python_startup_audit"));state.put("python_startup_pin",report.get("python_startup_pin"));state.put("status","PASS_ORIGINAL_CELLPHONEDB_WORKFLOWS");')
    setup=(JAVA/'CellPhoneDbSetupCli.java').read_text(encoding='utf8').replace('CellPhoneDbSetupCli','CellPhoneDbSetupCliV2').replace('CellPhoneDbWorkflowRuntime','CellPhoneDbWorkflowRuntimeV2')
    worker=replace_once((OLD/'worker.py').read_text(encoding='utf8'),'from pathlib import Path\n','from pathlib import Path\n'+STARTUP)
    supervisor=replace_once((OLD/'supervisor.py').read_text(encoding='utf8'),"command=[runtime['runtime_python'],'-I','-B',","command=[runtime['runtime_python'],'-B','-P','-s',")
    guide='''# CellPhoneDB launcher revision2: effective Python hash seed

Use `org.jlinalg.cli.CellPhoneDbWorkflowCliV2 --request request.json` and
`org.jlinalg.cli.CellPhoneDbSetupCliV2 --export NEW_DIRECTORY` with this additive development JAR.
The request schema and exact original5.0.1 runtime installation are unchanged.
An already validated exact runtime receipt can be reused; never refresh its hashes.

The worker uses sanitized `-B -P -s` startup. `-I` ignored PYTHONHASHSEED in the
first-generation runner. Revision2 checks effective flags and the declared seed
before NumPy/pandas/original scientific imports and writes `worker/python-startup.json`.
Native Java verifies the receipt and independently checks all seed0 probe hashes.
Nonzero seeds use the bound interpreter startup check, not a native hash-secret port.
Original NumPy/permutation seeds and entropy-seeded full-method subsampling remain
separate. A hash seed does not make every method or multiprocessing output deterministic.

Require exit0 AND `analysis-status.json` result_admitted=true. Read `WARNINGS.md`,
the startup receipt, native audit, source/input dispositions and original logs.
Missing/unavailable dependencies: SKIP3, skip analysis. Use the separate explicit
setup process only if installation is permitted. Otherwise keep analysis skipped.
Any startup, original-method, audit, source, timeout or capacity failure: FAIL2 for
the entire batch; earlier outputs remain provisional. Preserve the complete failed
directory, correct the named cause and retry NEW output paths.
Do not relabel first-generation runs or edit their verifiers/proofs.

The unchanged original setup instructions, exact dependency locks, legal notices,
runtime health checks and recovery instructions follow below.

'''+(OLD/'SETUP.md').read_text(encoding='utf8')
    result={JAVA/'CellPhoneDbWorkflowRuntimeV2.java':runtime.encode(),JAVA/'CellPhoneDbWorkflowCliV2.java':cli.encode(),JAVA/'CellPhoneDbSetupCliV2.java':setup.encode()}
    for name,text in [('worker.py',worker),('supervisor.py',supervisor),('SETUP.md',guide),('.gitattributes','* -text whitespace=-blank-at-eol,-blank-at-eof,-space-before-tab,cr-at-eol\n')]:result[NEW/name]=text.encode()
    files={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for path,raw in result.items() if path.parent==NEW and (name:=path.name) in ['worker.py','supervisor.py','SETUP.md']}
    files['codec.py']=json.loads((OLD/'worker-profile.json').read_bytes())['files']['codec.py']
    profile=dict(schema_version=1,edition='5.0.1',runner_revision=2,files=files,pickle_deserialized=False,original_namespace_patched=False)
    result[NEW/'worker-profile.json']=(json.dumps(profile,indent=2)+'\n').encode()
    return result

def verify():
    result=generated()
    for path,raw in result.items():assert path.read_bytes()==raw,'Additive derivation differs: '+str(path)
    return {path.relative_to(ROOT).as_posix():dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for path,raw in result.items()}

if __name__=='__main__':
    for path,raw in generated().items():
        path.parent.mkdir(parents=True,exist_ok=True)
        if path.exists():assert path.read_bytes()==raw,'Never overwrite an existing revision'
        else:path.write_bytes(raw)
    print(json.dumps(verify(),indent=2))
