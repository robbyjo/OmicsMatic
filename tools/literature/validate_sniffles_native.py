# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check native Java Sniffles VCF views against every original reference record."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

JDK21=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
JAVA25=Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')
DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve()
    assert path.drive.lower()=='d:'
    return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build',type=Path,required=True)
    parser.add_argument('--reference',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    out=args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime')
    out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_NATIVE_REFERENCE_COMPARISON',implementation_credit=False,
               full_Sniffles_execution_adapter_validated=False,whole_goal_complete=False,processes=[])
    before=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    def run(name,command,expected=0):
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
            result=subprocess.run(list(map(str,command)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=120)
        record=dict(name=name,command=list(map(str,command)),exit_code=result.returncode,expected_exit=expected)
        save(out/(name+'.process.json'),record);state['processes'].append(record)
        assert result.returncode==expected,(name,'inspect retained stdout/stderr')
    try:
        build=args.build.resolve()
        proof=json.loads((build/'COMPLETED.json').read_bytes())
        assert proof['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_VCF_COMPONENT_BUILD_ONLY'
        assert all(pin(ROOT/n)==value for n,value in proof['source_pins'].items())
        jar=ROOT/proof['artifact']['path']
        assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
        reference=args.reference.resolve()
        original=json.loads((reference/'COMPLETED.json').read_bytes())
        assert original['status']=='PASS_FOURTEEN_COMPLETE_ORIGINAL_SNIFFLES_REFERENCE_CONTROLS_ONLY'
        tests=out/'test-classes';tests.mkdir()
        test_source=ROOT/'src/test/java/org/jlinalg/genetics/SnifflesVcfTest.java'
        run('compile-tests',[JDK21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',jar,'-d',tests,test_source])
        runtimes={'windows21':[str(JDK21/'java.exe')], 'windows25':[str(JAVA25)],
                  'linux21':['wsl.exe','-d',DISTRO,'--','/usr/bin/java']}
        comparisons=[];failure_controls=[]
        for platform,prefix in runtimes.items():
            cp=(linux(tests)+':'+linux(jar)) if platform=='linux21' else (str(tests)+';'+str(jar))
            run(platform+'-format-controls',[*prefix,'-cp',cp,'org.jlinalg.genetics.SnifflesVcfTest'])
            assert (out/(platform+'-format-controls.stdout')).read_text().strip()=='PASS 41 scientific VCF format/boundary controls'
            family=out/platform;family.mkdir()
            for case in original['cases']:
                assert case['status']=='PASS_COMPLETE_ORIGINAL_SYNTHETIC_SV_CALLING_REFERENCE_ONLY'
                home=reference/case['name']
                vcf=home/'original.vcf'
                expected_path=home/'complete-original-records.json'
                assert pin(vcf)==case['original_vcf'] and pin(expected_path)==case['complete_decoding']
                expected=json.loads(expected_path.read_bytes())
                target=family/case['name']
                adapt=linux if platform=='linux21' else str
                run(platform+'-'+case['name'],[*prefix,'-jar',adapt(jar),'--edition',case['edition'][1:],
                                               '--vcf',adapt(vcf),'--out',adapt(target)])
                receipt=json.loads((target/'COMPLETED.json').read_bytes())
                assert receipt['status']=='PASS_COMPLETE_NATIVE_SNIFFLES_VCF_INSPECTION_ONLY' and receipt['inspection_admitted']
                for name,value in receipt['outputs'].items():assert pin(target/name)==value
                assert json.loads((target/'header.json').read_bytes())=={k:expected[k] for k in ['metadata_lines','column_line','samples']}
                actual=[json.loads(line) for line in (target/'variants.jsonl').read_text(encoding='utf8').splitlines()]
                assert len(actual)==len(expected['records'])==case['records']
                for index,(got,want) in enumerate(zip(actual,expected['records']),1):
                    assert got['row']==index
                    assert {k:got[k] for k in want}==want,(platform,case['name'],index)
                    info={r['key']:r['value'] for r in want['info']}
                    field='RE' if case['edition']=='v1.0.7' else 'SUPPORT'
                    assert got['read_support_info_field']==field and got['read_support']==int(info[field])
                summary=json.loads((target/'summary.json').read_bytes())
                assert summary['records']==case['records'] and sum(summary['variant_types'].values())==case['records']
                assert summary['samples']==expected['samples']
                assert pin(vcf)==case['original_vcf']
                comparisons.append(dict(platform=platform,case=case['name'],complete_records=case['records'],
                                        receipt=pin(target/'COMPLETED.json'),original_vcf=case['original_vcf']))
            # Public CLI failures must retain an explicit no-admission receipt,
            # name the corrective action, and leave a prior successful output intact.
            first_case=original['cases'][0]
            first_vcf=reference/first_case['name']/'original.vcf'
            late=family/'late-malformed.vcf'
            late.write_bytes(first_vcf.read_bytes()+b'chrControl\tbroken-late-row\n')
            failures=[('late-data',first_case['edition'][1:],late),
                      ('unsupported-edition','9.9',first_vcf),
                      ('missing-input',first_case['edition'][1:],family/'does-not-exist.vcf')]
            for name,edition,vcf in failures:
                target=family/('failed-'+name)
                run(platform+'-failure-'+name,[*prefix,'-jar',adapt(jar),'--edition',edition,
                    '--vcf',adapt(vcf),'--out',adapt(target)],2)
                receipt=json.loads((target/'COMPLETED.json').read_bytes())
                assert receipt['status']=='FAIL2_SNIFFLES_NATIVE_VCF_INSPECTION' and not receipt['inspection_admitted']
                assert 'ACTION REQUIRED:' in (target/'WARNINGS.md').read_text(encoding='utf8')
                if name=='late-data':assert len((target/'variants.jsonl').read_text(encoding='utf8').splitlines())==first_case['records']
                failure_controls.append(dict(platform=platform,name=name,receipt=pin(target/'COMPLETED.json')))
            prior=family/first_case['name']
            old={p.relative_to(prior).as_posix():pin(p) for p in prior.rglob('*') if p.is_file()}
            run(platform+'-failure-existing-output',[*prefix,'-jar',adapt(jar),'--edition',first_case['edition'][1:],
                '--vcf',adapt(first_vcf),'--out',adapt(prior)],2)
            assert {p.relative_to(prior).as_posix():pin(p) for p in prior.rglob('*') if p.is_file()}==old
            failure_controls.append(dict(platform=platform,name='existing-output',original_output_unchanged=True))
        assert len(comparisons)==42 and len(failure_controls)==12
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==before
        state.update(status='PASS_SNIFFLES_NATIVE_42_COMPLETE_REFERENCE_COMPARISONS_AND_123_FORMAT_CONTROLS',
                     build=pin(build/'COMPLETED.json'),reference=pin(reference/'COMPLETED.json'),
                     artifact=proof['artifact'],comparisons=comparisons,complete_original_records_compared=sum(c['complete_records'] for c in comparisons),
                     format_controls=123,public_CLI_failure_controls=failure_controls,platforms=list(runtimes),canonical_executable_unchanged=before,
                     source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),test_source]})
    except Exception as failure:
        state['error']=repr(failure)
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original-record comparisons')
    raise SystemExit(main())
