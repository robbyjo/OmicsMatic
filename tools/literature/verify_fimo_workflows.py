#!/usr/bin/env python3
"""Exercise the packaged original FIMO adapter with actual frozen P/q references.

No installs or downloads. Genuine original programs are launched by the JAR;
corrupted-profile cases are negative admission controls and are never scientific
reference engines. Expected incomplete upstream runs must be quarantined.
"""
from __future__ import annotations
import argparse,csv,ctypes,gzip,hashlib,json,os,re,shutil,subprocess,time
from pathlib import Path

P_SHA='ea3243dda675ce226dd1af8a2b66b2a27d0bf2d03bb9ad6ba591fb0ebb35f442'
Q_SHA='de6f921ed259135e7165932fd300bba0024b723de8a1b7577424d3f478cc4880'
MARKER='.jlinalg-fimo.json'
def sha(path):
    with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def table(path):
    with Path(path).open(encoding='utf-8',newline='') as f:return list(csv.DictReader((line for line in f if line.strip() and not line.startswith('#')),delimiter='\t'))
def load_reference(path,expected):
    assert sha(path)==expected,path
    return json.loads(gzip.decompress(Path(path).read_bytes()))
def same_rows(expected,observed):
    assert len(expected)==len(observed),(len(expected),len(observed))
    for index,(a,b) in enumerate(zip(expected,observed)):
        assert a==b,(index,a,b)
def process_alive(pid):
    if os.name!='nt':raise RuntimeError('Actual timeout liveness check in this profile is Windows-only')
    from ctypes import wintypes
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE
    kernel.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)];kernel.GetExitCodeProcess.restype=wintypes.BOOL
    kernel.CloseHandle.argtypes=[wintypes.HANDLE];kernel.CloseHandle.restype=wintypes.BOOL
    handle=kernel.OpenProcess(0x1000,False,pid)
    if not handle:
        error=ctypes.get_last_error()
        if error in (87,1168):return False
        raise OSError(error,'Cannot verify termination of owned original process')
    try:
        code=wintypes.DWORD()
        if not kernel.GetExitCodeProcess(handle,ctypes.byref(code)):raise ctypes.WinError(ctypes.get_last_error())
        return code.value==259
    finally:kernel.CloseHandle(handle)

class Campaign:
    def __init__(self,args):self.a=args;self.record=dict(status='running',jar_sha256=sha(args.jar),producer_sha256=sha(__file__),reference_p_sha256=sha(args.p_reference),reference_q_sha256=sha(args.q_reference),home=str(args.home.resolve()),cases=[],controls=[])
    def invoke(self,name,motif,fasta,options,expected,code=0,cap=False,java=None,group='cases',binary_fasta=None):
        work=self.a.work/name;work.mkdir(parents=True,exist_ok=False);unicode=name.startswith('control-unicode-');mf=work/('motifs-研究.meme' if unicode else 'motifs.meme');fa=work/('input-研究.fa' if unicode else 'input.fa');mf.write_text(motif,newline='\n')
        if binary_fasta is None:fa.write_text(fasta,newline='\n')
        else:fa.write_bytes(binary_fasta)
        output=work/('analysis-研究' if unicode else 'analysis');recipe=work/'options.json';values=dict(home=str(self.a.home.resolve()),motifs=str(mf.resolve()),fasta=str(fa.resolve()),out=str(output.resolve()));values.update(options)
        recipe.write_text(json.dumps(values,ensure_ascii=False),encoding='utf-8');command=[java or self.a.java,'-jar',str(self.a.jar.resolve()),'fimo-tools','--options-file',str(recipe.resolve())]
        start=time.monotonic();r=subprocess.run(command,capture_output=True,timeout=240);(work/'stdout').write_bytes(r.stdout);(work/'stderr').write_bytes(r.stderr)
        item=dict(name=name,exit_code=r.returncode,expected_exit_code=code,seconds=time.monotonic()-start,command=command);self.record[group].append(item)
        assert r.returncode==code,(name,r.returncode,r.stderr.decode(errors='replace'))
        if code==0:
            rows=table(output/'results.tsv');same_rows(expected,rows);item['compared_sites']=len(rows);item['results_sha256']=sha(output/'results.tsv')
            assert json.loads((output/'analysis-status.json').read_text())['status'] in ('completed','completed-with-qc-warnings')
        else:
            assert not (output/'results.tsv').exists(),name
            assert 'Required action' in (output/'WARNINGS.md').read_text(),name
            assert json.loads((output/'analysis-status.json').read_text())['status']==('skipped-dependency-unavailable' if code==3 else 'failed')
            for product in ['sequence-metadata.tsv','models.tsv','upstream','working']:assert not (output/product).exists(),(name,product)
            if cap:
                assert 'INCOMPLETE ORIGINAL FIMO OUTPUT' in (output/'WARNINGS.md').read_text(),name
                rows=table(output/'partial/upstream/fimo.tsv');same_rows(expected,rows);item['compared_partial_sites']=len(rows)
                assert 'Reached max stored scores' in (output/'fimo.stderr').read_text(),name
            if code==3:assert not (output/'version.stdout').exists(),name
        item.update(status='passed',manifest_sha256=sha(output/'manifest.yaml'))
        print(name,'passed',code,flush=True);return output,item
    def reference_cases(self,p,q):
        for c in q['cases']:
            options=dict(background='uniform',strands='both',coordinates='genomic',**{'threshold-type':c['threshold_type'],'threshold':c['threshold'],'maximum-stored-scores':str(c['max_stored_scores'])})
            self.invoke('q-'+c['name'],c['motif'],c['fasta'],options,c['expected_rows'],2 if c['upstream_truncated'] else 0,c['upstream_truncated'])
        for c in p['cases']:
            options=dict(c['options'],format='text',**{'threshold-type':'p'})
            self.invoke('p-'+c['name'],c['motif'],c['fasta'],options,c['rows'])
    def controls(self,base,q_filtered):
        common=dict(background='uniform',strands='both',coordinates='genomic',**{'threshold-type':'p','threshold':'1'})
        invalid=self.a.work/'invalid-profiles';invalid.mkdir();changes=[]
        for label in ['changed-binary','extra-dll','undeclared-use']:
            home=invalid/label;shutil.copytree(self.a.home,home)
            if label=='changed-binary':(home/'bin/fimo.exe').write_bytes(b'changed binary; negative control, never admitted')
            elif label=='extra-dll':(home/'bin/unlisted.dll').write_bytes(b'negative control')
            else:
                declaration=home/'provenance/intended-use.json';data=json.loads(declaration.read_text());data['intended_use']='not-declared';declaration.write_text(json.dumps(data));marker=home/MARKER;record=json.loads(marker.read_text());record['files']['provenance/intended-use.json']=sha(declaration);marker.write_text(json.dumps(record))
            changes.append((label,{'home':str(home.resolve())},3,base['motif'],base['fasta'],None))
        changes += [('missing-runtime',{'home':str((invalid/'absent').resolve())},3,base['motif'],base['fasta'],None),
          ('q-text',{'format':'text','threshold-type':'q'},2,base['motif'],base['fasta'],None),
          ('text-cap',{'format':'text','maximum-stored-scores':'10'},2,base['motif'],base['fasta'],None),
          ('unknown-motif',{'motif-id':'ABSENT'},2,base['motif'],base['fasta'],None),
          ('invalid-motif-probability',{},2,base['motif'].replace('.97 .01 .01 .01','1.2 .01 .01 .01'),base['fasta'],None),
          ('decompression-failure',{},2,base['motif'],'',b'\x1f\x8bcorrupt'),
          ('input-byte-limit',{'maximum-input-bytes':'2'},2,base['motif'],base['fasta'],None),
          ('residue-limit',{'maximum-residues':'2'},2,base['motif'],base['fasta'],None),
          ('sequence-limit',{'maximum-sequences':'1'},2,base['motif'],base['fasta'],None)]
        for label,updates,code,motif,fasta,binary in changes:self.invoke('control-'+label,motif,fasta,dict(common,**updates),[],code,group='controls',binary_fasta=binary)
        no_q=[dict(row,**{'q-value':''}) for row in base['expected_rows']]
        output,_=self.invoke('control-original-no-q-placeholders',base['motif'],base['fasta'],dict(common,**{'q-values':'false'}),no_q,group='controls')
        assert 'zero placeholders' in (output/'WARNINGS.md').read_text()
        output,_=self.invoke('control-complete-empty-full',base['motif'],base['fasta'],dict(common,threshold='1e-30'),[],group='controls')
        assert 'upstream_headerless_empty_table: true' in (output/'session.yaml').read_text()
        # A real original engine is deliberately given a long scan and a one-second
        # analysis limit; verify its recorded PID is gone after owned-process cleanup.
        wide='MEME version 4\nALPHABET= ACGT\nMOTIF wide\nletter-probability matrix: alength= 4 w= 512 nsites= 100\n'+'.97 .01 .01 .01\n'*512
        output,item=self.invoke('control-actual-timeout',wide,'>long\n'+'A'*64_000_000+'\n',dict(common,timeout='1'),[],2,group='controls')
        text=(output/'session.yaml').read_text();assert 'timed_out: true' in text and 'alive_after_cleanup: false' in text
        assert 'exceeded --timeout' in (output/'WARNINGS.md').read_text();pids=re.findall(r'^\s*pid:\s*(\d+)\s*$',text,re.M);assert len(pids)>=2,text[-4000:]
        assert not process_alive(int(pids[-1]));item['terminated_original_pid']=int(pids[-1])
        # The permitted installer-created profile is copied intact under Unicode;
        # both actual Java runtimes must launch this exact original executable.
        unicode_home=self.a.work/'runtime-研究';shutil.copytree(self.a.home,unicode_home)
        for index,java in enumerate([self.a.java]+([self.a.secondary_java] if self.a.secondary_java else [])):
            options=dict(common,home=str(unicode_home.resolve()),**{'threshold-type':'q','threshold':'.01'})
            expected=q_filtered['expected_rows']
            output,item=self.invoke('control-unicode-'+str(index),base['motif'],base['fasta'],options,expected,java=java,group='controls')
            before={p.relative_to(output).as_posix():sha(p) for p in output.rglob('*') if p.is_file()}
            recipe=output.parent/'options.json';r=subprocess.run([java,'-jar',str(self.a.jar.resolve()),'fimo-tools','--options-file',str(recipe.resolve())],capture_output=True,timeout=120)
            assert r.returncode==2 and b'Output already exists' in r.stderr
            assert before=={p.relative_to(output).as_posix():sha(p) for p in output.rglob('*') if p.is_file()};item['existing_output_retry_exit']=r.returncode

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--home',type=Path,required=True);p.add_argument('--java',default='java');p.add_argument('--secondary-java')
    p.add_argument('--p-reference',type=Path,required=True);p.add_argument('--q-reference',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args();a.work.mkdir(parents=True,exist_ok=False)
    p_reference=load_reference(a.p_reference,P_SHA);q_reference=load_reference(a.q_reference,Q_SHA);campaign=Campaign(a);started=time.monotonic()
    try:
        campaign.reference_cases(p_reference,q_reference);base=next(c for c in q_reference['cases'] if c['name']=='n5-single-p-all');filtered=next(c for c in q_reference['cases'] if c['name']=='n5-single-q-01');campaign.controls(base,filtered)
        assert sha(a.jar)==campaign.record['jar_sha256'];campaign.record.update(status='passed',reference_workflows=len(campaign.record['cases']),compared_complete_sites=sum(c.get('compared_sites',0) for c in campaign.record['cases']),compared_quarantined_sites=sum(c.get('compared_partial_sites',0) for c in campaign.record['cases']),control_checks=len(campaign.record['controls']),seconds=time.monotonic()-started)
    except BaseException as failure:campaign.record.update(status='failed',error=repr(failure),seconds=time.monotonic()-started);raise
    finally:(a.work/'verification.json').write_text(json.dumps(campaign.record,indent=2)+'\n')
    print(json.dumps({k:campaign.record[k] for k in ['status','reference_workflows','compared_complete_sites','compared_quarantined_sites','control_checks','jar_sha256','seconds']}),flush=True)
if __name__=='__main__':main()
