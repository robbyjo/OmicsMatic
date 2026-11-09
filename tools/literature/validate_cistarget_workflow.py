# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate actual native file flows, all results/dispositions and refusal admission."""
import argparse,concurrent.futures,csv,json,math,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_cistarget_recovery import linux,numeric,table,DISTRO

BASE=ROOT/'build/literature-review/cistarget-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CISTARGET_NATIVE_FILE_WORKFLOW_CONTROLS',implementation_credit=False,whole_goal_complete=False)
    try:
        proof=json.loads((BASE/'recovery-validation-3/COMPLETED.json').read_bytes());assert proof['status']=='PASS_BOTH_CISTARGET_RECOVERY_BUILDS_ON_THREE_JVMS_WITH_ALL_ORIGINAL_VALUES_AND_TARGETS_ONLY'
        fixtures=BASE/'workflow-controls-1';ready=json.loads((fixtures/'COMPLETED.json').read_bytes());assert ready['cases']==16 and all(pin(fixtures/n)==v for n,v in ready['files'].items())
        cases=json.loads((fixtures/'cases.json').read_bytes());canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';artifacts={};protected=[canonical,Path(__file__).resolve(),BASE/'recovery-validation-3/COMPLETED.json',fixtures/'COMPLETED.json']
        for compiler,folder in [('javac21','workflow-build21-2'),('javac25','workflow-build25-2')]:
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert built['status']=='PASS_ISOLATED_CISTARGET_TSV_WORKFLOW_BUILD_ONLY' and all(pin(ROOT/n)==v for n,v in built['source_pins'].items())
            jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};artifacts[compiler]=jar;protected.append(jar)
        before={p.relative_to(ROOT).as_posix():pin(p) for p in protected};state['protected_inputs_before']=before
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda p:str(p.resolve())
            cp=(':' if platform=='linux21' else ';').join(convert(p) for p in [artifacts[compiler],canonical]);command=[*platforms[platform],'-Xmx768m','-cp',cp,'org.jlinalg.cli.CisTargetWorkflowCli']
            def run(name,args,wanted=0):
                receipt=home/(name+'-process');receipt.mkdir();cmd=command+args;save(receipt/'command.json',cmd);completed=subprocess.run(cmd,capture_output=True,timeout=180)
                (receipt/'stdout').write_bytes(completed.stdout);(receipt/'stderr').write_bytes(completed.stderr);save(receipt/'process.json',dict(exit_code=completed.returncode))
                assert completed.returncode==wanted,(name,completed.returncode,completed.stderr.decode(errors='replace'));return completed
            run('help',['--help']);reports=[]
            for case in cases:
                source=fixtures/case['id'];settings=json.loads((source/'settings.json').read_bytes());native=home/case['id'];args=[]
                for name in ['settings','rankings','signature','annotations']:args.extend(['--'+name,convert(source/(name+('.json' if name=='settings' else '.tsv')))])
                done=run(case['id'],args+['--out',convert(native)]);assert b'CISTARGET ANALYSIS WARNINGS' in done.stderr
                receipt=json.loads((native/'run.json').read_bytes());assert receipt['status']=='PASS_NATIVE_CISTARGET_WITH_WARNINGS'
                assert (receipt['background_motifs'],receipt['database_features'],receipt['present_signature_features'],receipt['signature_features'])==(case['motifs'],case['total_features'],case['present'],case['present']+1)
                def host_path(name):
                    if platform!='linux21':return Path(name)
                    assert name.startswith('/mnt/d/');return Path('D:/'+name[len('/mnt/d/'):])
                assert {host_path(n).resolve():v for n,v in receipt['input_sha256'].items()}=={(source/(name+('.json' if name=='settings' else '.tsv'))).resolve():pin(source/(name+('.json' if name=='settings' else '.tsv')))['sha256'] for name in ['settings','rankings','signature','annotations']}
                assert all(pin(native/n)['sha256']==v for n,v in receipt['result_sha256'].items())
                expected=ROOT/case['original_reference'];wanted=table(expected/'metrics.tsv');actual=table(native/'all-motifs.tsv');assert len(wanted)==len(actual)==case['motifs'];selected=[];maximum=0
                for x,y in zip(actual,wanted,strict=True):
                    assert x['motif']=='motif_'+str(int(float(y['motif']))) and int(x['leading_rank'])==int(float(y['leading_rank']))
                    for field in ['auc','nes','leading_recovery','leading_excess']:maximum=max(maximum,numeric(x[field],y[field],(case['id'],x['motif'],field)))
                    auc=float(y['auc']);nes=float(y['nes']);passed=math.isfinite(nes) and (auc>settings['minimum_auc'] if settings['strict_auc'] else auc>=settings['minimum_auc']) and (nes>settings['minimum_nes'] if settings['strict_nes'] else nes>=settings['minimum_nes'])
                    assert x['passes_thresholds']==str(passed).lower() and x['status']==('DEFINED_NES' if math.isfinite(nes) else 'UNDEFINED_NES')
                    if passed:selected.append(x)
                assert table(native/'enriched-motifs.tsv')==selected and receipt['selected_motifs']==len(selected)
                desired=sorted((int(r['motif']),int(r['feature']),float(r['rank']),float(r['weight'])) for r in table(expected/'targets.tsv'))
                targets=table(native/'leading-targets.tsv');observed=[(int(r['motif'].split('_')[-1]),int(r['feature'].split('_')[-1]),float(r['raw_rank']),float(r['weight'])) for r in targets];assert observed==desired
                passing={r['motif'] for r in selected};annotation=table(source/'annotations.tsv')
                tf=[dict(tf=a['tf'],motif=r['motif'],feature=r['feature'],evidence=a['evidence']) for r in targets if r['motif'] in passing for a in annotation if a['motif']==r['motif']]
                assert table(native/'tf-targets.tsv')==tf
                dispositions=table(native/'signature-dispositions.tsv');signature=table(source/'signature.tsv');assert [r['feature'] for r in dispositions]==[r['feature'] for r in signature]
                assert [r['disposition'] for r in dispositions]==['PRESENT']*case['present']+['MISSING_FROM_DATABASE']
                ad=table(native/'annotation-dispositions.tsv');assert len(ad)==len(annotation)
                for x,y in zip(ad,annotation,strict=True):assert {k:x[k] for k in y}==y and x['disposition']==('MOTIF_ABSENT_FROM_DATABASE' if y['motif']=='absent_motif' else 'MATCHED_MOTIF')
                background=table(native/'recovery-background.tsv');original=table(expected/'background.tsv');assert len(background)==len(original)
                for x,y in zip(background,original,strict=True):
                    for field in y:numeric(x[field],y[field],(case['id'],'background',field))
                with (native/'recovery-curves.tsv').open() as first,(expected/'curves.tsv').open() as second:
                    ranks=next(first).strip().split('\t')[1:];assert len(ranks)==len(original)
                    for i,(x,y) in enumerate(zip(first,second,strict=True)):
                        left=x.strip().split('\t');right=y.strip().split('\t');assert left[0]=='motif_'+str(i) and len(left)==len(right)+1
                        for u,v in zip(left[1:],right,strict=True):numeric(u,v,(case['id'],'full recovery',i))
                assert 'Required action:' in (native/'WARNINGS.md').read_text() and not list((native/'partial').iterdir())
                reports.append(dict(id=case['id'],all_motif_fields_and_exact_target_members_match=True,full_curves_and_background_match=True,all_dispositions_and_annotation_joins_match=True,selected_motifs=len(selected),maximum_metric_scaled_difference=maximum))
            # Actual CLI refusals establish no-result admission, rather than merely testing helpers.
            source=fixtures/'ctx-1';bad=home/'bad-inputs';bad.mkdir();original_settings=json.loads((source/'settings.json').read_bytes());bad_settings=dict(original_settings,minimum_present_fraction=1);save(bad/'coverage.json',bad_settings)
            bad_settings=dict(original_settings,unexpected=True);save(bad/'unknown.json',bad_settings)
            ranks=(source/'rankings.tsv').read_text();lines=ranks.splitlines();header=lines[0].split('\t');header[-1]=header[1];(bad/'duplicate-columns.tsv').write_text('\t'.join(header)+'\n'+'\n'.join(lines[1:])+'\n')
            (bad/'duplicate-motif.tsv').write_text(ranks+lines[1]+'\n');(bad/'ragged.tsv').write_text(lines[0]+'\n'+'\t'.join(lines[1].split('\t')[:-1])+'\n')
            tokens=lines[1].split('\t');tokens[lines[0].split('\t').index('feature_30')]='NaN';(bad/'invalid-unselected.tsv').write_text(lines[0]+'\n'+'\t'.join(tokens)+'\n'+'\n'.join(lines[2:])+'\n')
            (bad/'duplicate-signature.tsv').write_text((source/'signature.tsv').read_text()+'feature_0\t1\n');(bad/'nan-weight.tsv').write_text('feature\tweight\nfeature_0\tNaN\n')
            (bad/'duplicate-annotation.tsv').write_text((source/'annotations.tsv').read_text()+'motif_0\tTF_A\tdirectAnnotation\n')
            errors=[('coverage','settings',bad/'coverage.json'),('unknown-key','settings',bad/'unknown.json'),('duplicate-columns','rankings',bad/'duplicate-columns.tsv'),('duplicate-motif','rankings',bad/'duplicate-motif.tsv'),('ragged','rankings',bad/'ragged.tsv'),('invalid-cell','rankings',bad/'invalid-unselected.tsv'),('duplicate-signature','signature',bad/'duplicate-signature.tsv'),('nan-weight','signature',bad/'nan-weight.tsv'),('duplicate-annotation','annotations',bad/'duplicate-annotation.tsv')]
            for name,kind,path in errors:
                args=[]
                for key in ['settings','rankings','signature','annotations']:args.extend(['--'+key,convert(path if key==kind else source/(key+('.json' if key=='settings' else '.tsv')))])
                target=home/('refused-'+name);failed=run('refused-'+name,args+['--out',convert(target)],2)
                assert b'Required action:' in failed.stderr and not (target/'enriched-motifs.tsv').exists()
                if target.exists():assert json.loads((target/'run.json').read_bytes())['status']=='FAIL2_NO_RESULTS_ADMITTED' and 'NO RESULTS ADMITTED' in (target/'WARNINGS.md').read_text()
            # Refuse overwriting a completed run, preserving every byte.
            target=home/'ctx-1';old={f.name:pin(f) for f in target.iterdir() if f.is_file()};args=[]
            for key in ['settings','rankings','signature','annotations']:args.extend(['--'+key,convert(source/(key+('.json' if key=='settings' else '.tsv')))])
            run('refused-existing',args+['--out',convert(target)],2);assert old=={f.name:pin(f) for f in target.iterdir() if f.is_file()}
            print('PASS16 native file workflows and10 refusals '+compiler+'/'+platform,flush=True)
            return dict(compiler=compiler,platform=platform,cases=reports,actual_cli_refusals=10)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,[(c,p) for c in artifacts for p in platforms]))
        assert before=={n:pin(ROOT/n) for n in before} and all(pin(fixtures/n)==v for n,v in ready['files'].items())
        state.update(status='PASS96_NATIVE_CISTARGET_TSV_WORKFLOWS_AND60_ACTUAL_CLI_REFUSALS_ONLY',runs=runs,complete_file_workflow_comparisons=96,actual_cli_refusals=60,protected_inputs_unchanged=before,
            scope='Complete native TSV analyses, both source profiles, strict/inclusive thresholds, all motifs/curves/background/leading-target membership, missing-feature/annotation dispositions and exact annotation joins. Original Python prepared-provider and R function-boundary comparisons do not prove whole-package, original file-import or SCENIC execution.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS96_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full workflow/admission checks')
    raise SystemExit(main())
