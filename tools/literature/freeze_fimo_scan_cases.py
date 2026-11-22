#!/usr/bin/env python3
"""Freeze real FIMO DNA p-scan references; never install or simulate FIMO."""
from __future__ import annotations
import argparse,csv,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path

def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def runtime_path(path,cygwin):
    path=Path(path).resolve()
    return '/cygdrive/'+path.drive[0].lower()+'/'+path.as_posix()[3:] if cygwin else str(path)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fimo',type=Path,required=True);parser.add_argument('--work',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--cygwin-bin',type=Path)
    a=parser.parse_args();a.work.mkdir(parents=True,exist_ok=False)
    env=os.environ.copy();env['LC_ALL']='C'
    if a.cygwin_bin: env['PATH']=str(a.cygwin_bin)+';'+env.get('PATH','')
    version=subprocess.run([str(a.fimo),'--version'],env=env,capture_output=True,timeout=30,check=True)
    assert version.stdout.decode().strip()=='5.5.9',version.stdout
    rng=random.Random(174103); backgrounds=[[.25]*4,[.1,.2,.3,.4],[.1,.1,.2,.6],[.0001,.0099,.4,.59]];models=[]
    for i in range(16):
        width=[1,2,3,4,8,12,24,48][i%8];matrix=[]
        for _ in range(width):
            values=[rng.random()+.01 for _ in range(4)];total=sum(values);matrix.append([v/total for v in values])
        models.append(dict(id='random'+str(i),matrix=matrix,sites=[1,20,10000][i%3],pseudo=[0,.1,10][i%3],bg=backgrounds[i%4]))
    models.extend([dict(id='constant',matrix=[[.25]*4 for _ in range(4)],sites=20,pseudo=.1,bg=backgrounds[0]),
                   dict(id='onehot',matrix=[[1,0,0,0] for _ in range(4)],sites=20,pseudo=.1,bg=backgrounds[3])])
    cases=[]
    for i,m in enumerate(models):
        width=len(m['matrix']);words=[''.join(w) for w in itertools.product('ACGT',repeat=width)] if width<=4 else [''.join(rng.choices('ACGT',k=width)) for _ in range(100)]
        words+=['A'*width,'C'*width,'G'*width,'T'*width]
        best=''.join('ACGT'[max(range(4),key=lambda b:row[b]/m['bg'][b])] for row in m['matrix']);words+=[best]
        fasta=''.join('>w'+str(k)+' sample\n'+w+'\n' for k,w in enumerate(words))
        fasta+='>chr3:101-'+str(100+2*width+1)+' region\n'+best+'N'+best.lower()+'\n>short\n'+best[:-1]+'\n>empty\n'
        fasta+='>aliases\n'+best.replace('T','U').replace('A','a')+'X.RYSWKMBDHVN\n'
        motif='MEME version 4\n\nALPHABET= ACGT\nstrands: + -\nBackground letter frequencies\n'+' '.join(b+' '+repr(p) for b,p in zip('ACGT',m['bg']))+'\n\nMOTIF '+m['id']+' alt\nletter-probability matrix: alength= 4 w= '+str(width)+' nsites= '+str(m['sites'])+' E= 0\n'+''.join(' '.join(map(repr,row))+'\n' for row in m['matrix'])
        for variant,(background,both,genomic,threshold) in enumerate([('motif',True,True,1),('motif',False,False,1),('uniform',True,False,.1),('nrdb',False,True,.01)]):
            name=m['id']+'-'+str(variant);directory=a.work/name;directory.mkdir();mf=directory/'input.meme';fa=directory/'input.fa';mf.write_text(motif,newline='\n');fa.write_text(fasta,newline='\n')
            args=['--text','--thresh',str(threshold),'--motif-pseudo',str(m['pseudo'])]
            if background!='nrdb': args+=['--bfile','--'+background+'--']
            if not both: args+=['--norc']
            if not genomic: args+=['--no-pgc']
            command=[str(a.fimo),*args,runtime_path(mf,bool(a.cygwin_bin)),runtime_path(fa,bool(a.cygwin_bin))]
            r=subprocess.run(command,env=env,capture_output=True,timeout=120);(directory/'stdout.tsv').write_bytes(r.stdout);(directory/'stderr').write_bytes(r.stderr)
            assert r.returncode==0,(name,r.returncode,r.stderr.decode(errors='replace'))
            rows=list(csv.DictReader([line for line in r.stdout.decode().splitlines() if line and not line.startswith('#')],delimiter='\t'))
            assert all(row['q-value']=='' for row in rows)
            cases.append(dict(name=name,motif=motif,fasta=fasta,options=dict(background=background,strands='both' if both else 'forward',coordinates='genomic' if genomic else 'sequence',threshold=str(threshold),pseudocount=str(m['pseudo'])),rows=rows,original_command=command,stdout_sha256=sha(directory/'stdout.tsv'),stderr_sha256=sha(directory/'stderr')))
            print(name,len(rows),flush=True)
    record=dict(schema_version=1,reference_tool='MEME FIMO',reference_version='5.5.9',binary_sha256=sha(a.fimo),generator_sha256=sha(__file__),source_archive_sha256='0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12',source_url='https://meme-suite.org/meme/meme-software/5.5.9/meme-5.5.9.tar.gz',runtime_scope='Actual unchanged scientific C sources, private Cygwin/GNU POSIX regex backend, LC_ALL=C. Backend build and all source-integrity evidence retained in research runtime. Research/nonprofit eligibility was explicitly supplied by the user. No full native-host or q-value parity claim.',cases=cases)
    payload=json.dumps(record,ensure_ascii=False,separators=(',',':')).encode();a.output.parent.mkdir(parents=True,exist_ok=True)
    with a.output.open('xb') as raw:
        with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0,filename='') as out:out.write(payload)
    print(json.dumps(dict(status='frozen',cases=len(cases),rows=sum(len(c['rows']) for c in cases),fixture_sha256=sha(a.output))),flush=True)
if __name__=='__main__':main()
