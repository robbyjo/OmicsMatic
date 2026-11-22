"""Original0.48.0 scientific fixtures; never obtains expected values from Java."""
import argparse,csv,datetime as dt,gzip,hashlib,json,random,subprocess
from pathlib import Path
from install_kallisto import available,LOCK
ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--home',required=True,type=Path);args=parser.parse_args();home=args.home.resolve();assert available(home),'A verified original runtime is required; run the installer where permitted'
    sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
    resources=ROOT/'src/test/resources/kallisto';resources.mkdir(parents=True,exist_ok=True)
    cache=ROOT/'build/literature-review/kallisto-runtime'/('references-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));cache.mkdir();steps=[]
    rng=random.Random(362725);sequences={name:''.join(rng.choice('ACGT') for _ in range(length)) for name,length in [('001',1000),('txB',1600),('txZero',1200)]}
    fasta=resources/'transcripts.fa';fasta.write_text(''.join('>'+name+'\n'+sequence+'\n' for name,sequence in sequences.items()))
    mates=[[],[]];complement=str.maketrans('ACGT','TGCA')
    for name,count in [('001',40),('txB',120),('txZero',0)]:
        for number in range(count):
            start=rng.randrange(0,len(sequences[name])-200);fragment=sequences[name][start:start+200]
            for mate,sequence in enumerate([fragment[:100],fragment[-100:].translate(complement)[::-1]]):mates[mate].append('@'+name+'_'+str(number)+'/'+str(mate+1)+'\n'+sequence+'\n+\n'+'I'*100+'\n')
    for number in range(10):
        for mate in range(2):mates[mate].append('@unmapped'+str(number)+'/'+str(mate+1)+'\n'+'N'*100+'\n+\n'+'I'*100+'\n')
    reads=[]
    for mate in range(2):path=resources/('read'+str(mate+1)+'.fastq.gz');path.write_bytes(gzip.compress(''.join(mates[mate]).encode(),mtime=0));reads.append(path)
    def run(name,options,expected=0):
        command=[(home/'kallisto.exe').as_posix(),*map(str,options)];process=subprocess.run(command,capture_output=True,text=True,timeout=120);log=cache/(name+'.log');log.write_text(process.stdout+process.stderr);steps.append(dict(name=name,command=command,exit_code=process.returncode,expected_exit=expected,log_sha256=sha(log)));assert process.returncode==expected,(name,process.stdout,process.stderr)
    index=cache/'targets.idx';run('index',['index','-i',index.as_posix(),'-k','31',fasta.as_posix()])
    cases=[]
    for name,extra,single in [('paired',[],False),('bootstrap17',['-b','3','--seed','17'],False),('bias',['--bias'],False),('fr',['--fr-stranded'],False),('rf',['--rf-stranded'],False),('single',['--single'],True),('single-fr',['--single','--fr-stranded'],True),('single-rf',['--single','--rf-stranded'],True),('single-overhang',['--single','--single-overhang'],True)]:
        expected=1 if name in ['rf','single-rf'] else 0
        output=cache/name;run(name,['quant','--plaintext','-i',index.as_posix(),'-o',output.as_posix(),'-l','200','-s','20','-t','1',*extra,reads[0].as_posix(),*([] if single else [reads[1].as_posix()])],expected)
        info=json.loads((output/'run_info.json').read_text())
        with (output/'abundance.tsv').open(newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
        if name in ['paired','bootstrap17','fr','single','single-fr','single-overhang']:assert [float(row['est_counts']) for row in rows]==[40,120,0],(name,rows)
        filename=name+'.tsv';(resources/filename).write_bytes((output/'abundance.tsv').read_bytes());cases.append(dict(name=name,reference_file=filename,run_info=info,expected_exit=expected,reference_sha256=sha(resources/filename)))
        if name=='bootstrap17':
            for i in range(3):path=output/('bs_abundance_'+str(i)+'.tsv');(resources/('bootstrap17-draw'+str(i)+'.tsv')).write_bytes(path.read_bytes())
    # Actual negative capability: this binary silently omits HDF5 and draws without plaintext.
    output=cache/'hdf5-capability';run('hdf5-capability',['quant','-i',index.as_posix(),'-o',output.as_posix(),'-l','200','-s','20','-b','3','--seed','17',reads[0].as_posix(),reads[1].as_posix()]);assert not (output/'abundance.h5').exists() and not list(output.glob('bs_abundance*'))
    result=dict(status='passed-original-Windows-point-and-plaintext-reference-generation',version='0.48.0',archive_sha256=LOCK['archive_sha256'],executable_sha256=sha(home/'kallisto.exe'),generator_sha256=sha(Path(__file__)),input_sha256={path.name:sha(path) for path in [fasta,*reads]},truth=dict(processed_records_or_pairs=170,pseudoaligned_unstranded=160,estimated_counts={'001':40,'txB':120,'txZero':0},fragment_mean=200,fragment_sd=20,raw_lengths={'001':1000,'txB':1600,'txZero':1200}),cases=cases,steps=steps,negative_hdf5_capability='Exit0 and n_bootstraps3 but no HDF5/draw files without --plaintext. Adapter must always request plaintext and verify every draw.',directory=str(cache),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    result['output_sha256']={p.name:sha(p) for p in resources.glob('*.tsv')};(resources/'reference-provenance.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(status=result['status'],cases=len(cases),original_program_calls=len(steps),directory=str(cache))))


if __name__=='__main__':main()
