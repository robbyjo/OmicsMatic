#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Linux original-runtime replay refusals and deterministic late-recording controls."""
from pathlib import Path
import argparse,gzip,hashlib,importlib.util,json,shutil,struct,subprocess,sys,time

ROOT=Path(__file__).resolve().parents[3]

def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def replace(path,key,value):
    lines=path.read_text(encoding='utf-8').splitlines();found=0
    for i,line in enumerate(lines):
        if line.startswith(key+'='):lines[i]=key+'='+value;found+=1
    if found!=1:raise ValueError('Expected one literal control key: '+key)
    path.write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
def reseal(root):
    marker=root/'COMPLETED.properties'
    fixed=[line for line in marker.read_text().splitlines() if not line.startswith('member.')]
    entries=[]
    for p in sorted(root.rglob('*')):
        if p==marker:continue
        if p.is_symlink():raise ValueError('Control refuses symlinks')
        entries.append('member.'+p.relative_to(root).as_posix()+'='+('DIRECTORY' if p.is_dir() else pin(p)['sha256']))
    marker.write_text('\n'.join(fixed+entries)+'\n',encoding='utf-8',newline='\n')
def request(path,bam,binary,**changes):
    values=dict(bam=str(bam.resolve()),sambamba=str(binary.resolve()),edition='0.8.2',action='mark',threads='1',compression='6',
        hash_table_size='1024',overflow_list_size='64',sort_buffer_mb='16',io_buffer_mb='1',wall_ms='60000',address_space_mb='1024',work_mb='256',max_files='128')
    values.update(changes);path.write_text('\n'.join(k+'='+v for k,v in values.items())+'\n',encoding='utf-8',newline='\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['jar','base','binary','out']:parser.add_argument('--'+key,type=Path,required=True)
    a=parser.parse_args()
    if sys.platform!='linux':raise FileNotFoundError('Use the approved Linux/WSL Python and JDK')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);base=a.base.resolve();before=inventory(base);candidate=pin(a.jar)
    classes=out/'probe-classes';classes.mkdir()
    checks=[]
    def run(where,label,command,expected,needle=None):
        started=time.monotonic()
        with (where/(label+'.stdout')).open('xb') as so,(where/(label+'.stderr')).open('xb') as se:
            r=subprocess.run(list(map(str,command)),stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
        row=dict(case=label,command=list(map(str,command)),exit_code=r.returncode,expected_exit_code=expected,seconds=round(time.monotonic()-started,2))
        save(where/(label+'.process.json'),row)
        if r.returncode!=expected:raise RuntimeError(str(row)+' '+(where/(label+'.stderr')).read_text(errors='replace')[-2500:])
        if needle is not None:
            text=(where/(label+'.stdout')).read_text(errors='replace')+(where/(label+'.stderr')).read_text(errors='replace')
            if needle not in text:raise ValueError('Control did not reach the expected cause: '+label+' '+text[-2500:])
        checks.append(row);print('PASS',label,flush=True);return row
    run(out,'compile-probe',['/usr/bin/javac','--release','17','-cp',a.jar,'-d',classes,Path(__file__).with_name('QualificationProbe.java')],0)
    common=['/usr/bin/java','-Xmx2g','-cp',str(a.jar.resolve())+':'+str(classes),'org.jlinalg.cli.SambambaWorkflowCli']
    probe=common[:-1]+['org.jlinalg.cli.QualificationProbe']
    run(out,'positive-saved-read',[*probe,'read',base],0,'PASS_SAVED_PAYLOAD_COHERENCE_ONLY')
    def negative(name,mutate,needle,seal=True):
        where=out/name;where.mkdir();bundle=where/'bundle';shutil.copytree(base,bundle)
        mutate(bundle,where)
        if seal:reseal(bundle)
        old=inventory(bundle)
        run(where,name,[*common,'--verify',bundle,'--out',where/'verification'],2,needle)
        assert inventory(bundle)==old and not (where/'verification/COMPLETED.properties').exists()
        assert not (where/'verification/version.pid.properties').exists(),name+' unexpectedly executed the runtime'
    negative('raw-hash-mismatch',lambda b,w:replace(b/'effective.properties','threads','2'),'member identity differs',False)
    negative('resealed-request-contradiction',lambda b,w:replace(b/'effective.properties','threads','2'),'Original/effective request contradiction: threads')
    negative('resealed-implementation-contradiction',lambda b,w:replace(b/'implementation.properties','sha256.org.jlinalg.cli.SambambaWorkflowCli','0'*64),'implementation classes differ')
    negative('resealed-source-argv-contradiction',lambda b,w:replace(b/'mark.pid.properties','source_argv.3','2'),'command contradicts request: source_argv.3')
    negative('resealed-extra-file',lambda b,w:(b/'unreviewed.txt').write_text('Unexpected payload must not be admitted\n'),'unexpected/missing files')
    negative('resealed-missing-decisions',lambda b,w:(b/'decisions.tsv').rename(w/'retained-decisions.tsv'),'decisions.tsv')
    # This counterfeit retains correct transitions, counts, header CL and request provenance.
    # Only genuine re-execution can reject its swapped high/low quality winners.
    where=out/'resealed-counterfeit-classification';where.mkdir();bundle=where/'bundle';shutil.copytree(base,bundle)
    spec=importlib.util.spec_from_file_location('independent_bgzf_writer',ROOT/'tools/literature/sambamba_validation/make_cases.py')
    writer=importlib.util.module_from_spec(spec);spec.loader.exec_module(writer)
    raw=bytearray(gzip.decompress((bundle/'marked.bam').read_bytes()));text=struct.unpack_from('<i',raw,4)[0];at=8+text
    references=struct.unpack_from('<i',raw,at)[0];at+=4
    for _ in range(references):n=struct.unpack_from('<i',raw,at)[0];at+=4+n+4
    count=0
    while at<len(raw):
        length=struct.unpack_from('<i',raw,at)[0];flag=struct.unpack_from('<H',raw,at+18)[0]
        struct.pack_into('<H',raw,at+18,flag^1024);at+=4+length;count+=1
    assert count==4 and at==len(raw),'Require the exact four-record paired-quality control'
    (bundle/'marked.bam').write_bytes(writer.bgzf(raw))
    lines=(bundle/'decisions.tsv').read_text().splitlines();changed=[lines[0]]
    for line in lines[1:]:
        fields=line.split('\t');fields[3]=str(int(fields[3])^1024);changed.append('\t'.join(fields))
    (bundle/'decisions.tsv').write_text('\n'.join(changed)+'\n');reseal(bundle);counterfeit=inventory(bundle)
    run(where,'counterfeit-passes-saved-payload',[*probe,'read',bundle],0,'PASS_SAVED_PAYLOAD_COHERENCE_ONLY')
    run(where,'counterfeit-rejected-by-original',[*common,'--verify',bundle,'--out',where/'verification'],2,'Actual original replay record differs')
    assert (where/'verification/mark.process.properties').is_file() and 'exit_code=0' in (where/'verification/mark.process.properties').read_text()
    assert not (where/'verification/COMPLETED.properties').exists() and inventory(bundle)==counterfeit
    # Exercise a real exception after the production completion write and readback.
    where=out/'late-console-failure';where.mkdir();req=where/'request.properties';request(req,base/'input.bam',a.binary)
    run(where,'late-console-failure',[*probe,'late-console','--request',req,'--out',where/'analysis'],2,'CONTROLLED_FAILURE_AFTER_COMPLETION marker_sha256=')
    analysis=where/'analysis';assert not (analysis/'COMPLETED.properties').exists()
    invalidated=list(analysis.glob('COMPLETION-INVALIDATED-*.properties'));assert len(invalidated)==1
    assert 'status=completed' in invalidated[0].read_text() and 'CONTROLLED_FINAL_CONSOLE_RECORDING_FAILURE' in (analysis/'FAILURE.txt').read_text()
    # Verify from captured inputs after retaining the original files under new names.
    where=out/'moved-originals';where.mkdir();req=where/'request.properties';source=where/'source.bam';shutil.copy2(base/'input.bam',source)
    request(req,source,a.binary,action='remove');run(where,'capture-before-move',[*common,'--request',req,'--out',where/'analysis'],0)
    req.rename(where/'retained-request.properties');source.rename(where/'retained-source.bam')
    run(where,'captured-replay-after-move',[*common,'--verify',where/'analysis','--out',where/'verification'],0)
    assert not req.exists() and not source.exists() and (where/'verification/COMPLETED.properties').is_file()
    for name,binary,changes,expected,needle in [
      ('missing-runtime',out/'not-installed',{},3,'executable unavailable'),
      ('wrong-runtime-edition',a.binary,dict(edition='0.6.6'),2,'executable identity differs')]:
        where=out/name;where.mkdir();req=where/'request.properties';request(req,base/'input.bam',binary,**changes)
        run(where,name,[*common,'--request',req,'--out',where/'analysis'],expected,needle)
        assert not (where/'analysis/COMPLETED.properties').exists() and not (where/'analysis/version.pid.properties').exists()
    assert inventory(base)==before and pin(a.jar)==candidate
    save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_REPLAY_ADVERSARIAL_AND_LATE_RECORDING_CONTROLS',checks=checks,
        candidate=candidate,source_bundle_preserved=True,counterfeit_saved_payload_passed=True,counterfeit_original_replay_rejected=True,
        late_verified_completion_invalidated=True,captured_input_replay_after_original_move=True,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_ALL_ADVERSARIAL_AND_LATE_RECORDING_CONTROLS',flush=True)
if __name__=='__main__':
    try:main()
    except FileNotFoundError as e:print('SKIP3_ADVERSARIAL_VALIDATION_PREREQUISITE',repr(e),flush=True);raise SystemExit(3)
    except Exception as e:print('FAIL2_ADVERSARIAL_VALIDATION_UNADMITTED',repr(e),flush=True);raise
