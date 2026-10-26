"""Freeze actual pinned seqtk outputs; native Java analysis does not require seqtk.

Build unmodified v1.3/v1.4/v1.5 archives first using the documented private
toolchain recipe. This helper executes only the explicitly supplied local builds.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ARCHIVES = {'1.3':'5a1687d65690f2f7fa3f998d47c3c5037e792f17ce119dab52fff3cfdca1e563',
            '1.4':'d124604ec24f29ed14ce127426ab90e0f3a2c0280c80d1a3ff8b1c09feede19c',
            '1.5':'384aa1e3cecf4f70403839d586cbb29d469b7c6f773a64bc5af48a6e4b8220a6'}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--reference-root', type=Path, default=ROOT/'build/literature-review/seqtk-reference')
    parser.add_argument('--toolchain', type=Path, default=ROOT/'build/omics-bwa-toolchain')
    parser.add_argument('--out', type=Path, default=ROOT/'src/test/resources/seqtk/reference.json')
    args = parser.parse_args(); work = args.reference_root.resolve(); fixtures = work/'fixtures'; fixtures.mkdir(exist_ok=True)
    environment = os.environ.copy(); environment['PATH'] = str(args.toolchain.resolve()/'usr/bin')+os.pathsep+environment.get('PATH','')
    provenance = {}; binaries = {}
    for version, expected in ARCHIVES.items():
        archive = work/('seqtk-v'+version+'.tar.gz'); assert digest(archive) == expected, archive
        source = work/('seqtk-'+version); binary = source/('seqtk.exe' if os.name == 'nt' else 'seqtk')
        probe = subprocess.run([str(binary)], env=environment, capture_output=True, text=True, timeout=30)
        assert 'Version: '+version+'-' in probe.stderr
        binaries[version] = binary
        provenance[version] = {'source_url':'https://github.com/lh3/seqtk/archive/refs/tags/v'+version+'.tar.gz',
            'archive_sha256':expected, 'source_files_sha256':{n:digest(source/n) for n in ['seqtk.c','kseq.h','khash.h','Makefile','LICENSE']},
            'binary_sha256':digest(binary), 'version_probe':probe.stderr.split('Version: ',1)[1].splitlines()[0]}
    attempts = []

    def reference(name, command, input_text, extra=None, versions=('1.3','1.4','1.5')):
        inp = fixtures/(name+('.fa' if command[0] in ['subseq','telo'] else '.fq')); inp.write_text(input_text, newline='\n')
        commands = [*command, str(inp)]
        if extra is not None:
            path = fixtures/(name+'.bed'); path.write_text(extra, newline='\n'); commands.append(str(path))
        outputs = {}
        for version in versions:
            argv = [str(binaries[version]), *commands]
            if command[0] == 'sample': argv = [str(binaries[version]), *command[:-1], str(inp), command[-1]]
            result = subprocess.run(argv, env=environment, capture_output=True, text=True, timeout=30)
            assert result.returncode == 0, (argv, result.stderr)
            outputs[version] = result.stdout
            attempts.append({'name':name,'version':version,'command':argv,'exit_code':result.returncode,
                'stdout_sha256':hashlib.sha256(result.stdout.encode()).hexdigest(),'stderr':result.stderr})
        assert len(set(outputs.values())) == 1, (name, outputs)
        return next(iter(outputs.values()))

    records = ''.join('@read'+str(i)+' comment '+str(i)+'\nACGTN\n+\nIHFED\n' for i in range(2049))
    samples = []
    for seed in [0,1,11,100,2147483648,4294967297]:
        for mode, value in [('fraction',0),('fraction',.25),('fraction',.5),('fraction',.999),('count',1),('count',7),('count',511),('count',3000)]:
            args_ = ['sample','-s'+str(seed)]
            if mode == 'count':args_.append('-2')
            args_.append(str(value)); name = 'sample-'+str(seed)+'-'+mode+'-'+str(value)
            result = reference(name,args_,records)
            ids = [int(line.split(' ',1)[0][5:]) for line in result.splitlines()[::4]]
            samples.append({'seed':seed,'mode':mode,'value':value,'records':2049,'indices':ids})
    rng = random.Random(1909); trim = []
    profiles = ['I'*80,'!'*80,'!'*10+'I'*60+'!'*10,'5'*80,'!'*20+'I'*5+'!'*55,
                'I'*8,'I'*3+'!'*30+'J'*7+'!'*40]
    profiles += [''.join(chr(rng.randrange(33,74)) for _ in range(rng.randrange(35,145))) for _ in range(24)]
    configs = [{'error_rate':.05,'minimum':30,'left':0,'right':0,'length':0},
               {'error_rate':.1,'minimum':5,'left':0,'right':0,'length':0},
               {'error_rate':0,'minimum':0,'left':0,'right':0,'length':0},
               {'error_rate':.05,'minimum':30,'left':0,'right':0,'length':50},
               {'error_rate':.05,'minimum':30,'left':5,'right':10,'length':0}]
    for i, quality in enumerate(profiles):
        for j, options in enumerate(configs):
            sequence = ''.join('ACGTRYswN'[k%9] for k in range(len(quality)))
            text = '@r'+str(i)+' original comment\n'+sequence+'\n+\n'+quality+'\n'
            cmd = ['trimfq','-q',str(options['error_rate']),'-l',str(options['minimum']),'-b',str(options['left']),'-e',str(options['right']),'-L',str(options['length'])]
            output = reference('trim-'+str(i)+'-'+str(j),cmd,text)
            lines = output.splitlines(); assert len(lines) == 4, (i,j,output)
            trim.append({'sequence':sequence,'quality':quality,'options':options,'expected_sequence':lines[1],'expected_quality':lines[3]})
    chromosomes = {'chrA':'CCCTAA'*100+'N'*2200+'TTAGGG'*100,
                   'chrB':'AGTC'*50+'CCCTAA'*100+'AGTC'*50,
                   'chrC':'AGCT'*800+'TTAGGG'*80,
                   'chrD':'ccctaa'*70+'RYN'*10+'ccctaa'*40+'N'*3000+'ttaggg'*90,
                   'short':'CCCTAA','alternate':'ACG'*70+'N'*3000+'CGT'*80}
    fasta = ''.join('>'+name+'\n'+'\n'.join(seq[i:i+57] for i in range(0,len(seq),57))+'\n' for name,seq in chromosomes.items())
    telomeres = []
    for motif,penalty,drop,score in [('CCCTAA',1,2000,300),('CCCTAA',2,30,30),('CCCTAA',0,2000,50),('ACG',1,50,30)]:
        output = reference('telo-'+motif+'-'+str(penalty),['telo','-m',motif,'-p',str(penalty),'-d',str(drop),'-s',str(score)],fasta,versions=('1.4','1.5'))
        telomeres.append({'motif':motif,'penalty':penalty,'drop':drop,'score':score,'bed4':output})
    bed = 'chrA\t0\t20\nchrA\t599\t620\nchrC\t3195\t3220\nchrD\t60\t150\n'
    extracted = reference('subseq',['subseq','-l','60'],fasta,extra=bed)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    record = {'format_version':1,'generated_utc':dt.datetime.now(dt.timezone.utc).isoformat(),
        'source_provenance':provenance,'toolchain_lock_sha256':digest(ROOT/'tools/literature/msys2-bwa-build-lock.json'),
        'sampling':samples,'trimming':trim,'chromosomes':chromosomes,'telomeres':telomeres,
        'subsequence':{'bed3':bed,'expected_fasta':extracted},
        'scope':'Actual unmodified pinned upstream programs, equal output across three versions where exposed; telo checked against tags 1.4/1.5. No dirty or unpublished-paper-version equivalence.'}
    args.out.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    (work/'reference-attempts.json').write_text(json.dumps(attempts,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'sampling':len(samples),'trimming':len(trim),'telo_settings':len(telomeres),'actual_upstream_invocations':len(attempts),'fixture_sha256':digest(args.out)}))


if __name__ == '__main__':main()
