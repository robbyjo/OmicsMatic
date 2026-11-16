#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read the native PoPS workflow extension, without reinterpreting older evidence."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,subprocess,sys,zipfile,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
DOC='docs/literature/pops-workflow-development'
BASE='bcf69fcdf773682ff4920dbc484622ddc1bf0fca'
MODIFIED={'src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/main/java/org/jlinalg/cli/PopsWorkflowInputs.java','docs/literature/POPS_WORKFLOW_DEVELOPMENT.md'}
META={DOC+'/'+n for n in ['.gitattributes','SOURCE-PINS.json','EVIDENCE-PINS.json','PRIVATE-RECORDS.json','CHANGE-REVIEW.json','STATUS.json','VALIDATION-RECORDS.zip','WARNINGS.md']}
SOURCES={'src/main/java/org/jlinalg/genetics/'+n+'.java' for n in ['Pops','PopsMath','PopsCovariance','PopsEigenvalues','PopsOrder','PopsBlockWhitening','PopsChunked']}
SOURCES|={'src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['PopsWorkflowInputs','PopsWorkflowFiles','PopsWorkflowNpy','PopsWorkflowCli','PopsWorkflowCode','PopsWorkflowProcess','PopsWorkflowResults','PopsWorkflowWorker','JLinAlgCli']}
SOURCES|={'src/test/java/org/jlinalg/genetics/'+n+'.java' for n in ['PopsOriginalTest','PopsEigenvaluesTest','PopsChunkedTest']}
SOURCES|={'src/test/java/org/jlinalg/cli/'+n+'.java' for n in ['PopsWorkflowCliTest','PopsWorkflowNpyTest']}
SOURCES|={'tools/literature/pops_workflow/'+n for n in ['reference_inputs.py','PopsWorkflowJUnitReplay.java','example_fixture.py','records.py']}
SOURCES|={'docs/literature/POPS_WORKFLOWS.md','docs/literature/POPS_WORKFLOW_DEVELOPMENT.md','src/test/resources/org/jlinalg/cli/pops-workflow-inputs.zip','examples/pops-workflow/synthetic-inputs.zip'}
if not __debug__:raise RuntimeError('Scientific/source assertions must remain enabled')
class MissingPrerequisite(Exception):pass
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
    size=0;h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for raw in iter(lambda:stream.read(1048576),b''):size+=len(raw);h.update(raw)
    return dict(bytes=size,sha256=h.hexdigest())
def read(path):return json.loads(Path(path).read_bytes())
def git(*args):
    result=subprocess.run(['git','--no-replace-objects',*args],cwd=ROOT,capture_output=True)
    if result.returncode:raise MissingPrerequisite('Required genuine Git baseline unavailable: '+repr(args)+'. Use a complete checkout containing '+BASE+' and this extension; fetch the named baseline if permitted, then retry NEW verification output. No source overlay or hash refresh is accepted.')
    return result.stdout
def blob(raw):return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
def matches(raw,oid):return blob(raw)==oid or (b'\0' not in raw and b'\r\n' in raw and blob(raw.replace(b'\r\n',b'\n'))==oid)
def properties(raw):return dict(line.split('=',1) for line in raw.decode('utf-8').splitlines() if line and not line.startswith('#') and '=' in line)
def source_guard(sources,review):
    git('cat-file','-e',BASE+'^{tree}');tree={}
    for row in git('ls-tree','-rz','--full-tree',BASE).split(b'\0'):
        if not row:continue
        head,name=row.split(b'\t',1);mode,kind,oid=head.decode().split();assert kind=='blob' and mode in ['100644','100755'];tree[name.decode()]=oid
    assert len(tree)==13483,len(tree)
    actual=set(git('ls-files','-z').decode().strip('\0').split('\0'))|set(git('ls-files','--others','--exclude-standard','-z').decode().strip('\0').split('\0'));actual.discard('')
    expected=set(tree)|set(sources)|META;assert actual==expected,('Source file membership differs',sorted(actual-expected)[:10],sorted(expected-actual)[:10])
    assert set(review['modified_existing_files'])==MODIFIED
    for name,oid in tree.items():
        raw=(ROOT/name).read_bytes()
        if name in MODIFIED:assert pin(ROOT/name)==review['modified_existing_files'][name]['after'] and blob(git('show',BASE+':'+name))==oid,name
        else:assert matches(raw,oid),'Unreviewed baseline change: '+name
    for name,identity in sources.items():assert pin(ROOT/name)==identity,'Source pin differs: '+name
    name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';old=git('show',BASE+':'+name).decode();new=(ROOT/name).read_text()
    anchor='            if (arguments.length > 0 && arguments[0].equals("ldpred2-tools"))\n                return LdPred2WorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);'
    extra='\n            if (arguments.length > 0 && arguments[0].equals("pops-tools"))\n                return PopsWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);'
    usage='              java -jar omicsmatic-<version>.jar ldpred2-tools --help'
    assert old.count(anchor)==old.count(usage)==1
    assert old.replace(anchor,anchor+extra).replace(usage,usage+'\n              java -jar omicsmatic-<version>.jar pops-tools --help')==new,'Main changes exceed explicit dispatcher/help additions'
    return len(tree)
def verify(private_base=None):
    status=read(ROOT/DOC/'STATUS.json');assert status['baseline_commit']==BASE and status['whole_goal_complete'] is False and status['new_catalog_credit']==0
    for key,file in [('source_pins','SOURCE-PINS.json'),('evidence_pins','EVIDENCE-PINS.json')]:assert pin(ROOT/DOC/file)==status[key]
    sources=read(ROOT/DOC/'SOURCE-PINS.json')['files'];assert set(sources)==SOURCES
    evidence=read(ROOT/DOC/'EVIDENCE-PINS.json')
    for name,identity in evidence['files'].items():assert pin(ROOT/name)==identity,name
    review=read(ROOT/DOC/'CHANGE-REVIEW.json');baseline_files=source_guard(sources,review)
    with zipfile.ZipFile(ROOT/DOC/'VALIDATION-RECORDS.zip') as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
        assert set(z.namelist())==set(evidence['archive_members'])
        for name,identity in evidence['archive_members'].items():assert not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts and rawpin(z.read(name))==identity,name
        windows=json.loads(z.read('java-workflow-2/VALIDATION.json'));assert windows['totals']==dict(tests=219,failures=0,errors=0,skipped=0)
        assert status['candidate_jar']==windows['jar']
        for name in ['java-workflow-2/PROCESS.json','linux-driver-2/PROCESS.json']:assert json.loads(z.read(name))['exit_code']==0 and json.loads(z.read(name))['sources_unchanged']
        linux=properties(z.read('linux-tests-2/COMPLETED.properties'));assert linux['status']=='PASS_COMPLETE_POPS_CONTROLS_ON_LINUX' and linux['tests_found']==linux['tests_succeeded']=='219' and linux['tests_failed']==linux['tests_skipped']==linux['tests_aborted']=='0'
        expected={'org.jlinalg.cli.PopsWorkflowCliTest':25,'org.jlinalg.cli.PopsWorkflowNpyTest':134,'org.jlinalg.genetics.PopsChunkedTest':43,'org.jlinalg.genetics.PopsOriginalTest':16,'org.jlinalg.genetics.PopsEigenvaluesTest':1}
        with zipfile.ZipFile(io.BytesIO(z.read('java-workflow-2/test-results.zip'))) as tests:
            actual={}
            for name in tests.namelist():
                x=ET.fromstring(tests.read(name));actual[x.attrib['name']]=int(x.attrib['tests']);assert all(x.attrib[k]=='0' for k in ['failures','errors','skipped'])
            assert actual==expected
        with zipfile.ZipFile(io.BytesIO(z.read('java-workflow-2/as-run-sources.zip'))) as saved:
            for name in saved.namelist():assert rawpin(saved.read(name))==pin(ROOT/name),'Actual tested source differs: '+name
        tutorial=json.loads(z.read('full-tutorial-2/COMPLETED.json'));assert tutorial['status']=='PASS_COMPLETE_OFFICIAL_POPS_TUTORIAL_TWO_BATCH_WIDTHS_AND_CROSS_OS_GENUINE_REPLAY'
        assert [tutorial[k] for k in ['prediction_genes','target_genes','training_genes','features','selected_features']]==[18383,16980,16752,508,258]
        assert tutorial['batch_widths']==[128,1] and tutorial['subsampled'] is False and tutorial['all_three_scientific_result_inventories_identical'] and tutorial['original_source_and_inputs_unchanged']
        assert max(tutorial[k] for k in ['maximum_absolute_prediction_error','maximum_absolute_marginal_error','maximum_absolute_coefficient_error'])<5e-8
        for name in ['windows-batch-128','windows-batch-1','linux-replay']:assert json.loads(z.read('full-tutorial-2/'+name+'.process.json'))['exit_code']==0
        reference=json.loads(z.read('inputs-reference-1/COMPLETED.json'));assert reference['saved_original_cases']==13 and reference['fresh_original_format_cases']==6 and reference['npy_matrix_cases']==132
        provenance=json.loads(z.read('inputs-reference-1/files/SOURCE.json'));assert provenance['numpy']=='1.19.5' and provenance['scipy']=='1.5.2' and all(r['exit_code']==0 for r in provenance['fresh_original_format_cases'])
        controls=json.loads(z.read('package-controls-2/COMPLETED.json'));assert len(controls['controls'])==7 and all(r['exit_code']==r['expected'] for r in controls['controls'])
        continuity=json.loads(z.read('CLASS-CONTINUITY-2.json'));assert set(continuity['modified_existing_classes'])=={'org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/PopsWorkflowInputs.class','org/jlinalg/cli/PopsWorkflowInputs$Magma.class'}
        assert continuity['debug_only_changed_existing_classes']==['org/jlinalg/cli/PopsWorkflowInputs$Magma.class']
        import re
        def disassembly(raw):return '\n'.join(re.sub(r'line \d+:','line DEBUG:',line) for line in raw.decode('utf-8').splitlines() if not line.lstrip().startswith(('Classfile ','Last modified ','SHA-256 checksum ')))
        assert disassembly(z.read('RETAINED-PACKAGING-GUARD/Magma-before.javap.txt'))==disassembly(z.read('RETAINED-PACKAGING-GUARD/Magma-after.javap.txt'))
        assert all(n.startswith('org/jlinalg/cli/PopsWorkflow') and n.endswith('.class') for n in continuity['new_classes'])
    summary=read(ROOT/'docs/literature/omics-2023-2026/summary.json');assert summary['papers']==250 and summary['paper_tool_observations']==2691 and summary['implementation_status_counts']['missing']==263
    private_count=0
    if private_base is not None:
        base=Path(private_base).resolve();private=read(ROOT/DOC/'PRIVATE-RECORDS.json')
        if not base.is_dir():raise MissingPrerequisite('Named private PoPS workflow records missing at '+str(base)+'. Restore the original complete named run directories listed in PRIVATE-RECORDS.json, or omit --private-base for public saved-record verification. No private readback was performed.')
        actual=set()
        for top in private['included_directories']:
            folder=base/top
            if not folder.is_dir():raise MissingPrerequisite('Named private directory missing: '+str(folder)+'. Restore exact original records or omit --private-base; no dependent readback is admitted.')
            for p in folder.rglob('*'):
                assert not p.is_symlink(),'Private aliases not qualified: '+str(p)
                if p.is_file():actual.add(p.relative_to(base).as_posix())
        for name in private['included_driver_files']:
            if not (base/name).is_file():raise MissingPrerequisite('Restore missing private driver record: '+str(base/name))
            actual.add(name)
        assert actual==set(private['files']),('Named private file membership differs',sorted(actual-set(private['files']))[:10],sorted(set(private['files'])-actual)[:10])
        for name,identity in private['files'].items():assert pin(base/name)==identity,'Private bytes differ: '+name
        private_count=len(actual)
    return dict(status='PASS_NATIVE_POPS_WORKFLOW_EXTENSION_AND_SAVED_SCIENTIFIC_RECORDS',baseline_commit=BASE,baseline_files_checked=baseline_files,reviewed_existing_file_changes=3,tests_each_platform=219,original_successful_cases=17,expected_original_failures=2,full_tutorial_prediction_genes=18383,full_tutorial_selected_features=258,full_tutorial_subsampled=False,cross_os_genuine_replay=True,complete_named_private_files_checked=private_count,current_catalog_changed=False,new_catalog_credit=0,fresh_scientific_execution_performed=False,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--private-base',type=Path);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
    try:result=verify(a.private_base);name='COMPLETED.json';exit_code=0
    except MissingPrerequisite as e:result=dict(status='SKIP3_POPS_WORKFLOW_RECORD_PREREQUISITE_MISSING',reason=str(e),dependent_verification_skipped=True);name='SKIPPED.json';exit_code=3
    except Exception as e:result=dict(status='FAIL2_POPS_WORKFLOW_RECORDS_UNADMITTED',reason=repr(e),required_action='Retain exact sources, inputs, records and failures. Resolve the named difference and retry NEW output; do not refresh fingerprints or substitute runtimes.');name='FAILURE.json';exit_code=2
    (a.out/name).write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result),flush=True);return exit_code

if __name__=='__main__':raise SystemExit(main())
