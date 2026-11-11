# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the actual historical counters with edition-specific capabilities; never substitute releases."""
import argparse
import concurrent.futures
import json
import subprocess
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,DISTRO,linux,pin
from collect_htseq_count_controls import specifications


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--setup',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();setup=args.setup.resolve()
    assert out.is_relative_to(BASE) and setup.is_relative_to(BASE) and not out.exists();out.mkdir()
    status=json.loads((setup/'COMPLETED.json').read_bytes())
    assert status['status']=='completed-private-historical-HTSeq-reference-setup'
    data=BASE/'count-original-1/data'
    assert json.loads((BASE/'count-original-1/COMPLETED.json').read_bytes())['original_SAM_GTF_count_executed']
    # Real Python2 command-line arguments are bytes. JSON produces unicode;
    # passing those directly makes old HTSeq treat a filename as an iterable
    # of characters. Encode the transport, never alter original count source.
    bootstrap='import json,random,runpy,sys\nrandom.seed(4381)\narguments=json.loads(open(sys.argv[1]).read())\nif sys.version_info[0]==2:arguments=[value.encode("utf8") for value in arguments]\nsys.argv=["htseq-count"]+arguments\nrunpy.run_module("HTSeq.scripts.count",run_name="__main__")\n'
    (out/'bootstrap.py').write_text(bootstrap,encoding='utf8',newline='\n')
    cases=[]
    for profile in status['profiles']:
        version=profile['version']
        assert profile['status']=='installed-original-historical-HTSeq'
        for original in specifications():
            case=dict(original)
            case['native_audit']=False
            if version in ['0.5.3','0.6.0']:
                if case['nonunique']!='none' or case['id'].endswith(('score-secondary','score-supplementary','score-both')):continue
                # These releases expose no ignore flags; the source scores those
                # records. This is a declared original-edition control, not a
                # silent replacement of a user's requested filtering policy.
                case.update(secondary='score',supplementary='score',source_flags_always_scored=True)
            if version=='0.5.3' and (case['filename'].endswith('.bam') or case['order']!='name'):continue
            if version=='0.11.1' and case['nonunique'] in ['fraction','random']:continue
            cases.append((profile,case))
        cases.append((profile,dict(specifications()[0],id='source-default-minimum-mapq',use_default_minimum=True,
            secondary='score' if version in ['0.5.3','0.6.0'] else 'ignore',
            supplementary='score' if version in ['0.5.3','0.6.0'] else 'ignore',minimum_mapq=0 if version=='0.5.3' else 10)))
    def run(item):
        profile,spec=item;version=profile['version'];folder=out/(version+'-'+spec['id']);folder.mkdir()
        alignment=data/spec['filename'];annotation=data/spec.get('annotation_filename','features.gtf')
        arguments=['-s',spec['stranded'],'-m',spec['overlap'],'-t','exon','-i','gene_id']
        if not spec.get('use_default_minimum'):arguments.extend(['-a',str(spec['minimum_mapq'])])
        if version!='0.5.3':arguments.extend(['-f','bam' if alignment.suffix=='.bam' else 'sam','-r',spec['order']])
        if version not in ['0.5.3','0.6.0']:
            arguments.extend(['--nonunique',spec['nonunique'],'--secondary-alignments',spec['secondary'],
                '--supplementary-alignments',spec['supplementary']])
        arguments.extend([linux(alignment),linux(annotation)])
        save(folder/'arguments.json',arguments);save(folder/'config.json',spec)
        argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/env','PYTHONHASHSEED=0','PYTHONDONTWRITEBYTECODE=1','PYTHONNOUSERSITE=1',
            'OPENBLAS_NUM_THREADS=1','OMP_NUM_THREADS=1',profile['home']+'/bin/python','-s','-B',linux(out/'bootstrap.py'),linux(folder/'arguments.json')]
        with (folder/'original-count.stdout').open('xb') as stdout,(folder/'original-count.stderr').open('xb') as stderr:
            result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(folder/'process.json',dict(command=argv,exit_code=result.returncode))
        parsed=None
        if result.returncode==0:
            rows=[line.split('\t') for line in (folder/'original-count.stdout').read_text(encoding='utf8').splitlines()]
            names=['no_feature','ambiguous','too_low_aQual','not_aligned','alignment_not_unique']
            if version!='0.5.3':names=['__'+n for n in names]
            assert len(rows)>=5 and all(len(r)==2 for r in rows)
            assert [r[0] for r in rows[-5:]]==names
            parsed=dict(genes=rows[:-5],summary=rows[-5:]);save(folder/'source-count-table.json',parsed)
        return dict(version=version,id=spec['id'],config=spec,exit_code=result.returncode,counts=parsed,
            original_edition_only=True,counting_source_translated=False)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(run,cases))
    per_version={version:dict(original_calls=sum(r['version']==version for r in results),
        successful=sum(r['version']==version and r['exit_code']==0 for r in results),
        retained_failures=sum(r['version']==version and r['exit_code']!=0 for r in results)) for version in {r['version'] for r in results}}
    assert all(r['successful']>0 for r in per_version.values()),'No positive counting health for a profile; inspect transport/source failures before admission'
    save(out/'COMPLETED.json',dict(schema_version=1,status='collected-actual-original-historical-HTSeq-counts',
        setup_metadata=pin((setup/'COMPLETED.json').read_bytes()),script_pin=pin(Path(__file__).read_bytes()),cases=results,
        per_version=per_version,shared_or_system_python_modified=False,source_flags_version_specific=True,
        historical_whole_environment_reproduced=False,independent_native_parser_or_pairing=False,implementation_credit=False))
    print(json.dumps(dict(status='collected-historical-original-counts',per_version=per_version)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact historical counter checks')
    main()
