#!/usr/bin/env python3
"""Collect actual q-value/cohort references from the sealed private original FIMO engine.

No installation, downloads, synthetic replacement engine, or Java parity claim.
The small (<100 sites) single homogeneous PWM cohort additionally has exact
binomial p-values and independently computed BH q-values (upstream pi0=1).
Larger cohorts and bounded-hit cases retain actual upstream behavior separately.
"""
from __future__ import annotations
import argparse,csv,hashlib,json,math,os,random,subprocess
from pathlib import Path

def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def posix(p):
    p=Path(p).resolve();return '/cygdrive/'+p.drive[0].lower()+'/'+p.as_posix()[3:]
def rows(p):
    with Path(p).open(encoding='utf-8',newline='') as f:return list(csv.DictReader((line for line in f if line.strip() and not line.startswith('#')),delimiter='\t'))
def reported(expected,printed,digits=3):
    actual=float(printed);radius=0 if actual==0 else .5*10**(math.floor(math.log10(abs(actual)))-digits+1)
    assert abs(expected-actual)<=radius+abs(expected)*1e-12,(expected,printed,radius)
def hand_small_cohort(table):
    assert len(table)<100 and len(table)>0
    values=[]
    for row in table:
        matches=row['matched_sequence'].upper().count('A')
        p=sum(math.comb(8,k)*.25**k*.75**(8-k) for k in range(matches,9));reported(p,row['p-value']);values.append(p)
    order=sorted(range(len(values)),key=values.__getitem__);q=[0.0]*len(values);previous=1.0
    for rank in range(len(order),0,-1):
        index=order[rank-1];previous=min(previous,values[index]*len(values)/rank);q[index]=previous
    for index,row in enumerate(table):reported(q[index],row['q-value'])
    return dict(status='passed-exact-binomial-p-and-independent-BH-small-cohort',sites=len(values),pi0=1,scope='All scanned canonical sites retained; fewer than100p-values forces upstreampi0=1. This does not replace original large-cohort pi0 estimation or bounded-hit behavior.')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args()
    marker=a.home/'.jlinalg-fimo-reference.json';profile=json.loads(marker.read_text());assert profile['tool']=='FIMO' and profile['version']=='5.5.9'
    assert profile['eligibility']=='educational-research-nonprofit';assert profile['source_archive_sha256']=='0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12'
    for name,digest in profile['files'].items():
        member=(a.home/name).resolve();assert member.is_relative_to(a.home.resolve()) and sha(member)==digest,name
    a.work.mkdir(parents=True,exist_ok=False);environment=os.environ.copy();environment['PATH']=str((a.home/'bin').resolve())+';'+str(Path(os.environ['SystemRoot'])/'System32');environment['LC_ALL']='C'
    for key in ['LD_PRELOAD','LD_LIBRARY_PATH','CYGWIN','MSYSTEM','LIBRARY_PATH','MEME_ETC_DIR','MEME_BIN_DIR','MEME_DIR']:environment.pop(key,None)
    binary=(a.home/profile['executable']).resolve();rng=random.Random(273069);cases=[];reference=dict(status='running',marker_sha256=sha(marker),binary_sha256=sha(binary),producer_sha256=sha(__file__),cases=cases)
    try:
        for cohort in [5,30,300]:
            fasta=''.join('>read'+str(i)+' sample\n'+('A'*16 if i%3==0 else ''.join(rng.choices('ACGT',k=16)))+'\n' for i in range(cohort))
            fasta+='>chr4:101-116 masked\n'+'N'*16+'\n>short\nACG\n'
            for multiple in [False,True]:
                motif='MEME version 4\nALPHABET= ACGT\nstrands: + -\nBackground letter frequencies\nA .25 C .25 G .25 T .25\nMOTIF A8 polyA\nletter-probability matrix: alength= 4 w= 8 nsites= 100 E= 0\n'+'.97 .01 .01 .01\n'*8
                if multiple:motif+='\nMOTIF mixed weak\nletter-probability matrix: alength= 4 w= 4 nsites= 20\n.8 .05 .05 .1\n.05 .8 .1 .05\n.1 .05 .8 .05\n.05 .1 .05 .8\n'
                for family,threshold,cap in [('p-all','1',100000),('q-05','.05',100000),('q-01','.01',100000),('p-cap10','1',10)]:
                    name=f'n{cohort}-'+('multi-' if multiple else 'single-')+family;directory=a.work/name;directory.mkdir();mf=directory/'input.meme';fa=directory/'input.fa';mf.write_text(motif,newline='\n');fa.write_text(fasta,newline='\n')
                    runs=[]
                    for attempt in [1,2]:
                        output=directory/('original-'+str(attempt));command=[str(binary),'--oc',posix(output),'--bfile','--uniform--','--motif-pseudo','.1','--max-stored-scores',str(cap),'--thresh',threshold]
                        if family.startswith('q-'):command+=['--qv-thresh']
                        command += [posix(mf),posix(fa)]
                        r=subprocess.run(command,capture_output=True,env=environment,cwd=a.work,timeout=120);(directory/f'run{attempt}.stdout').write_bytes(r.stdout);(directory/f'run{attempt}.stderr').write_bytes(r.stderr)
                        assert r.returncode==0,(name,r.returncode,r.stderr.decode(errors='replace'));table=rows(output/'fimo.tsv')
                        for row in table:
                            assert row['q-value'] and 0<=float(row['q-value'])<=1 and 0<=float(row['p-value'])<=1,row
                            if family.startswith('q-'):assert float(row['q-value'])<=float(threshold),row
                        runs.append(dict(command=command,exit_code=r.returncode,rows=table,stderr=r.stderr.decode(),stdout_sha256=sha(directory/f'run{attempt}.stdout'),tsv_sha256=sha(output/'fimo.tsv')))
                    assert runs[0]['rows']==runs[1]['rows'],(name,'Original repeated q results differ; retain both attempts and investigate randomness before freezing')
                    item=dict(name=name,cohort_records=cohort,multiple_motifs=multiple,threshold_type='q' if family.startswith('q-') else 'p',threshold=threshold,max_stored_scores=cap,motif=motif,fasta=fasta,runs=runs)
                    if cohort==5 and not multiple and family=='p-all':item['independent_null_and_q_check']=hand_small_cohort(runs[0]['rows'])
                    if cap==10:
                        assert any(word in runs[0]['stderr'].lower() for word in ['discard','stored','incomplete','approximate']),runs[0]['stderr']
                        item['interpretation']='Stored-hit cap reached; exact upstream warning retained. Truncated result population and possibly approximate q-values must be prominent, never silently treated as complete.'
                    cases.append(item);print(name,len(runs[0]['rows']),'repeat-identical',flush=True)
        reference.update(status='passed-actual-original-q-cohort-reference-campaign',workflows=len(cases),actual_original_calls=len(cases)*2,reported_sites_first_replicates=sum(len(c['runs'][0]['rows']) for c in cases),scope='Original private unchanged scientific engine only. All repeated rows match. Independent exact binomial/BH verification is limited to the <100-sites all-retained single-motif cohort. Larger/per-motif/cohort/retained-hit behavior is original evidence, not a new Java q-value algorithm or validated project adapter.')
    except BaseException as failure:reference.update(status='failed',error=repr(failure));raise
    finally:(a.work/'verification.json').write_text(json.dumps(reference,indent=2)+'\n')
    print(json.dumps({k:reference[k] for k in ['status','workflows','actual_original_calls','reported_sites_first_replicates']}),flush=True)
if __name__=='__main__':main()
