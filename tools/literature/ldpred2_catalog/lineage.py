# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Current-source review with explicit historical metadata and unmodified old gates."""
from pathlib import Path
import hashlib,json,subprocess,sys,uuid,zipfile
ROOT=Path(__file__).resolve().parents[3];sys.dont_write_bytecode=True;sys.path.insert(0,str(ROOT/'tools/literature'))
from catalog_source_transition_v2.source import tree,blob_pins
from ldpred2_native.records import verify as native_readback
from ldpred2_workflow_records import verify as workflow_readback
from ldpred2_primary.erratum import transition as erratum_transition
BASE='8f45d5826472c23767a1f8bee8507c4c09e95588';PRIOR='070ddea4c6393759f244d1781dba86d6b385da37'
DATA='docs/literature/omics-2023-2026';OWN='tools/literature/ldpred2_catalog';EVIDENCE='docs/literature/ldpred2-catalog-integration';GUIDE='docs/literature/LDPRED2_CATALOG_INTEGRATION.md';PROOF=DATA+'/ldpred2-catalog-validation.json';COMPATIBILITY=DATA+'/ldpred2-reviews-shared-source-compatibility.json'
MAIN='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
META=[DATA+'/'+n for n in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
def encoded(x):return (json.dumps(x,ensure_ascii=False,indent=2)+'\n').encode()
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return dict(bytes=p.stat().st_size,sha256=h.hexdigest())
def git(*args,cwd=ROOT):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=cwd)
def read_json(p):return json.loads(p.read_bytes())
def saved_records(folder,status):
    folder=ROOT/folder;proof=read_json(folder/'STATUS.json');assert proof['status']==status
    assert {p.name for p in folder.iterdir()}==set(proof['public_files'])|{'STATUS.json'}
    for n,v in proof['public_files'].items():assert not (folder/n).is_symlink() and pin(folder/n)==v,n
    for n,v in proof['source_pins'].items():assert not (ROOT/n).is_symlink() and pin(ROOT/n)==v,n
    for archive,entries in read_json(folder/'ARCHIVE-PINS.json').items():
        with zipfile.ZipFile(folder/archive) as z:
            assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(entries)
            for n,v in entries.items():assert rawpin(z.read(n))==v,n
    return proof
def parent_state():
    folder=ROOT/'docs/literature/p074-prs-disease-erratum';proof=read_json(folder/'STATUS.json')
    assert proof['status']=='PASS_P074_AORTIC_STENOSIS_METADATA_ONLY_ERRATUM'
    for n,v in proof['sources'].items():assert pin(ROOT/n)==v,n
    for n,v in proof['evidence'].items():assert pin(folder/n)==v,n
    with zipfile.ZipFile(folder/'catalog-before.zip') as z:
        assert len(z.namelist())==8 and set(z.namelist())==set(META);historical={n:z.read(n) for n in META}
    assert {n:rawpin(raw) for n,raw in historical.items()}==proof['before']
    after=erratum_transition(historical)
    assert {n:rawpin(raw) for n,raw in after.items()}==proof['after']
    return historical,after
def main_review():
    old=git('show',BASE+':'+MAIN)
    anchor=b'            if (arguments.length > 0 && arguments[0].equals("sambamba-tools"))\n                return SambambaStreamingWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
    extra=b'            if (arguments.length > 0 && arguments[0].equals("ldpred2-tools"))\n                return LdPred2WorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
    help_line=b'              java -jar omicsmatic-<version>.jar sambamba-tools --help\n'
    assert old.count(anchor)==1 and old.count(help_line)==1
    expected=old.replace(anchor,anchor+extra,1).replace(help_line,help_line+b'              java -jar omicsmatic-<version>.jar ldpred2-tools --help\n',1)
    assert (ROOT/MAIN).read_bytes()==expected,'Unreviewed main application change'
    return dict(before=rawpin(old),after=rawpin(expected),source_changes='One direct dispatch before project configuration and one help line; no numerical or worker source changes')
def source_inventory():
    base=tree(ROOT,BASE);prior=tree(ROOT,PRIOR);blobs=blob_pins(ROOT,base);records={}
    java={n for n in base if n.startswith('src/main/java/') and prior.get(n)!=base[n]}
    expected={'src/main/java/org/jlinalg/genetics/'+n+'.java' for n in ['LdCorrelation','LdPred2','LdPred2Random','LdPred2Seeds']}|{'src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['LdPred2WorkflowCli','LdPred2WorkflowFiles','LdPred2WorkflowWorker','LdPred2WorkflowProcess','LdPred2OriginalRuntime']}
    assert java==expected,java^expected
    for n,entry in base.items():
        if n in META:continue
        actual=pin(ROOT/n);allowed=[blobs[entry['git_object']]]
        if n==MAIN:actual=main_review()['after'];allowed=[actual]
        elif actual not in allowed:
            raw=(ROOT/n).read_bytes();assert rawpin(raw.replace(b'\r\n',b'\n'))==allowed[0] and git('hash-object','--path='+n,'--',n).decode().strip()==entry['git_object'],n
            allowed.append(actual)
        records[n]=dict(**entry,allowed_file_identities=allowed)
    names={n.decode() for n in git('ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if n}
    additions={n for n in names if n not in base and not owned(n) and n not in META}
    new_files={n:dict(category=extension_category(n),**pin(ROOT/n)) for n in sorted(additions)}
    return dict(base=BASE,predecessor=PRIOR,base_files=base,files=records,new_files=new_files,reviewed_new_production_java=sorted(java),main_change=main_review())
def owned(n):return n==GUIDE or n in [PROOF,COMPATIBILITY] or n.startswith(OWN+'/') or n.startswith(EVIDENCE+'/')
def extension_category(n):
    if n.startswith(('tools/literature/ldpred2_auto_scale/','tools/literature/ldpred2_command_validation/','tools/literature/ldpred2_primary/')):return 'qualified-validation-and-primary-source-helpers'
    if n in ['docs/literature/LDPRED2_COMMAND.md','docs/literature/P074_PRS_DISEASE_ERRATUM.md','docs/literature/LDPRED2_AUTO_SCALE.md']:return 'explicit-scope-documentation'
    if n.startswith(('docs/literature/p074-prs-disease-erratum/','docs/literature/ldpred2-auto-scale-development/','docs/literature/ldpred2-command-development/')):return 'separate-qualified-saved-records'
    raise ValueError('Unreviewed new project source: '+n)
def source_guard(record,extensions=None):
    extensions={} if extensions is None else extensions;base=tree(ROOT,BASE);assert base==record['base_files'] and record['base']==BASE
    names={n.decode() for n in git('ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if n}
    required=set(record['files'])|set(record['new_files']);actual={n for n in names if not owned(n) and n not in META}
    for n,v in extensions.items():assert n not in base and n not in record['new_files'] and not owned(n) and pin(ROOT/n)==v,n
    assert actual==required|set(extensions),'Existing or unreviewed project source membership differs'
    assert set(record['files'])==set(base)-set(META)
    for n,row in record['files'].items():
        assert all(row[k]==base[n][k] for k in ['mode','kind','git_object']),n
        path=ROOT/n;assert not path.is_symlink() and path.is_file() and pin(path) in row['allowed_file_identities'],n
    for n,row in record['new_files'].items():
        assert extension_category(n)==row['category'] and pin(ROOT/n)=={k:row[k] for k in ['bytes','sha256']},n
    main_review()
    return dict(base=BASE,existing_source_files_hashed=len(record['files']),reviewed_new_files_hashed=len(record['new_files']),explicit_future_extension_files=len(extensions),old_scientific_sources_excluded=False,Git_stat_cache_used_as_content_proof=False)
def qualification(historical,before,parent,release_parent,baseline,out,rescan):
    assert parent_state()==(historical,before)
    if git('rev-parse','HEAD',cwd=parent).decode().strip()!=PRIOR or git('status','--porcelain','-uall',cwd=parent):raise ValueError('Exact clean070ddea4 predecessor checkout required')
    command=[sys.executable,'-X','utf8','-B','tools/literature/sambamba_catalog/catalog.py','--verify','--parent-checkout',str(release_parent),'--baseline-checkout',str(baseline),'--out',str(parent/'build'/('ldpred2-parent-'+out.name+'-'+uuid.uuid4().hex))]
    if rescan:command.append('--rescan-sources')
    with (out/'predecessor.stdout').open('xb') as so,(out/'predecessor.stderr').open('xb') as se:p=subprocess.run(command,cwd=parent,stdout=so,stderr=se)
    (out/'predecessor.process.json').write_bytes(encoded(dict(command=command,cwd=str(parent),exit_code=p.returncode,old_verifier_modified=False)));assert p.returncode==0,'Unchanged predecessor verifier refused'
    assert not git('status','--porcelain','-uall',cwd=parent)
    native=native_readback(root=ROOT,catalog_metadata=historical,rescan=rescan);assert native['current_project_sources_match_snapshot']
    workflow=workflow_readback(require_current=True);assert workflow['current_project_sources_match_snapshot']
    command_proof=saved_records('docs/literature/ldpred2-command-development','PASS_PACKAGED_NORMAL_LDPRED2_COMMAND')
    scale=saved_records('docs/literature/ldpred2-auto-scale-development','PASS_SCOPED_ORIGINAL_LDPRED2_AUTO_SCALE_CONTROLS')
    assert scale['accepted_chains']==3 and scale['retained_iterations']==200 and scale['burn_in']==500 and scale['variants']==1100000
    return dict(status='PASS_SEPARATE_LDPRED2_LINEAGE_AND_CURRENT_SOURCE_QUALIFICATION',native=native,workflow=workflow,normal_command=command_proof['status'],original_auto_scale=scale['status'],old_current_tree_verifiers_overridden=False,new_catalog_credit=0,whole_goal_complete=False)
