# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the literal documented inputs with both builds on all three JVMs."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_cistarget_recovery import linux,DISTRO,table,numeric


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);state=dict(status='FAIL2_CISTARGET_LITERAL_EXAMPLE',implementation_credit=False,whole_goal_complete=False)
    try:
        example=ROOT/'examples/cistarget-ranked-features';before={p.relative_to(ROOT).as_posix():pin(p) for p in example.iterdir() if p.is_file()};runs=[];expected=None
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        for compiler,folder in [('javac21','workflow-build21-2'),('javac25','workflow-build25-2')]:
            built=json.loads((base/folder/'COMPLETED.json').read_bytes());artifact=ROOT/built['artifact']['path'];assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']}
            for platform,prefix in platforms.items():
                convert=linux if platform=='linux21' else lambda p:str(p.resolve());cp=(':' if platform=='linux21' else ';').join(convert(p) for p in [artifact,canonical]);home=out/(compiler+'-'+platform);home.mkdir();native=home/'native';command=[*prefix,'-cp',cp,'org.jlinalg.cli.CisTargetWorkflowCli']
                for name in ['settings','rankings','signature','annotations']:command.extend(['--'+name,convert(example/(name+('.json' if name=='settings' else '.tsv')))])
                command.extend(['--out',convert(native)]);save(home/'command.json',command);process=subprocess.run(command,capture_output=True,timeout=120);(home/'stdout').write_bytes(process.stdout);(home/'stderr').write_bytes(process.stderr);save(home/'process.json',dict(exit_code=process.returncode));assert process.returncode==0,process.stderr.decode(errors='replace')
                receipt=json.loads((native/'run.json').read_bytes());assert receipt['status']=='PASS_NATIVE_CISTARGET_WITH_WARNINGS' and (receipt['background_motifs'],receipt['present_signature_features'],receipt['selected_motifs'])==(4,3,1)
                motifs=table(native/'all-motifs.tsv');assert motifs[0]['motif']=='m0';numeric(motifs[0]['auc'],7/15,'literal analytic AUC',1e-15)
                assert table(native/'enriched-motifs.tsv')==[motifs[0]] and table(native/'signature-dispositions.tsv')[-1]['disposition']=='MISSING_FROM_DATABASE'
                assert table(native/'tf-targets.tsv') and {r['tf'] for r in table(native/'tf-targets.tsv')}=={'TF_A'}
                values={f.name:pin(f) for f in native.glob('*.tsv')}
                if expected is None:expected=values
                assert expected==values
                runs.append(dict(compiler=compiler,platform=platform,result_pins=values));print('PASS literal example '+compiler+'/'+platform,flush=True)
        assert before=={n:pin(ROOT/n) for n in before} and pin(canonical)==canonical_pin
        state.update(status='PASS_SIX_LITERAL_CISTARGET_EXAMPLE_RUNS_WITH_ANALYTIC_AUC_AND_IDENTICAL_RESULTS',runs=runs,literal_input_pins=before,canonical_executable_unchanged=canonical_pin,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable literal example admission checks')
    raise SystemExit(main())
