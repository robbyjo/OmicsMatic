# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check original Feather file import, lossless export, native analyses and explicit skips."""
import argparse,csv,hashlib,json,math,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_cistarget_recovery import linux,numeric,table,DISTRO

BASE=ROOT/'build/literature-review/cistarget-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='SKIP3_CISTARGET_ORIGINAL_FEATHER_REFERENCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False)
    try:
        import numpy as np
        import pyarrow.feather as feather
        from ctxcore.genesig import GeneSignature
        from ctxcore.rnkdb import FeatherRankingDatabase
        from ctxcore.recovery import recovery,enrichment4features,leading_edge
        state['status']='FAIL2_CISTARGET_COMPLETE_FEATHER_FILE_CONTROLS'
        intake=BASE/'source-intake-1';original=intake/'ctxcore-original/resources/tests/hg19-tss-centered-10kb-10species.mc9nr.genes_vs_motifs.rankings.feather'
        original_pin=pin(original);assert original_pin==json.loads((intake/'ctxcore-members.json').read_bytes())['resources/tests/'+original.name]
        source_table=feather.read_table(original);converted=out/'lossless-v2.genes_vs_motifs.rankings.feather';feather.write_feather(source_table,converted,version=2,compression='uncompressed')
        assert feather.read_table(converted).equals(source_table)
        database=FeatherRankingDatabase(str(converted),'original-five-motif-fixture-lossless-v2');full=database.load_full();features=list(full.columns);motifs=list(full.index)
        assert len(features)==22284 and len(motifs)==5
        # This declared source-rank-derived numerical fixture is not a biological signature.
        genes=list(dict.fromkeys([*full.iloc[0].sort_values().index[:40],*full.iloc[1].sort_values().index[:20],*full.iloc[2].sort_values().index[:10],*reversed(features[-15:]),*features[1000:1100:5]]));weights=[0 if i%7==0 else 1+(i%3)/4 for i in range(len(genes))]
        signature=out/'signature.tsv';annotations=out/'annotations.tsv'
        with signature.open('x',encoding='utf8',newline='') as f:csv.writer(f,delimiter='\t',lineterminator='\n').writerows([['feature','weight'],*zip(genes,weights,strict=True),['not_in_reference_database',1]])
        annotations.write_text('motif\ttf\tevidence\n',encoding='utf8')
        gs=GeneSignature('source-fixture',dict([*zip(genes,weights,strict=True),('not_in_reference_database',1)]));prepared=database.load(gs);w=np.array([gs[g] for g in prepared.columns])
        curves,auc=recovery(prepared,len(features),w,2000,.01);enrichment=enrichment4features(database,gs,2000,.01)
        assert np.array_equal(auc,enrichment[('Enrichment','AUC')].to_numpy(),equal_nan=True)
        nes=enrichment[('Enrichment','NES')].to_numpy();background=curves.mean(axis=0)+2*curves.std(axis=0);reference=[];targets=[]
        assert np.count_nonzero(auc)>0 and auc.std()>1e-8 and np.all(np.isfinite(nes)), 'Positive source-file fixture must exercise nonzero, nonconstant enrichment'
        for i,motif in enumerate(prepared.index):
            hits,rank=leading_edge(curves[i],background,prepared.iloc[i].to_numpy(),np.asarray(prepared.columns,dtype=object),w)
            reference.append(dict(motif=motif,auc=float(auc[i]),nes=float(nes[i]),leading_rank=int(rank),leading_recovery=float(curves[i,rank]),leading_excess=float(curves[i,rank]-background[rank])))
            targets.extend(dict(motif=motif,feature=str(feature),weight=float(weight)) for feature,weight in hits)
        assert targets,'Positive source-file fixture must exercise actual leading targets'
        save(out/'original-public-file-reference.json',dict(motifs=reference,targets_in_original_rank_order=targets,feature_order=list(prepared.columns),full_database_features=len(features),all_background_motifs=list(prepared.index),source_v1=original_pin,lossless_v2=pin(converted),historical_paper_database=False))
        np.savetxt(out/'original-recovery.tsv',curves,fmt='%.17g',delimiter='\t');np.savetxt(out/'original-background.tsv',np.stack([curves.mean(axis=0),curves.std(axis=0),background],axis=1),fmt='%.17g',delimiter='\t')
        settings=out/'settings.json';save(settings,dict(schema_version=1,profile='CTXCORE_0_2_0',auc_parameter=.01,recovery_bound=2000,minimum_auc=0,minimum_nes=0,strict_auc=True,strict_nes=True,minimum_present_fraction=.5,write_recovery_curves=True))
        def run(name,command,expected=0):
            home=out/(name+'-process');home.mkdir();save(home/'command.json',command);process=subprocess.run(command,capture_output=True,timeout=180)
            (home/'stdout').write_bytes(process.stdout);(home/'stderr').write_bytes(process.stderr);save(home/'process.json',dict(exit_code=process.returncode));assert process.returncode==expected,(name,process.returncode,process.stderr.decode(errors='replace'))
        exporter=ROOT/'tools/literature/cistarget_feather_to_tsv.py';export_pins={};exports=[]
        for name,source in [('original-v1',original),('lossless-v2',converted)]:
            dest=out/(name+'-export');run(name+'-conversion',[sys.executable,'-I',str(exporter),'--database',str(source),'--out',str(dest),'--max-cells','200000'])
            receipt=json.loads((dest/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_COMPLETE_FEATHER_RANKINGS_TO_TSV_ONLY' and receipt['output_pin']==pin(dest/'rankings.tsv')
            with (dest/'rankings.tsv').open(encoding='utf8') as stream:
                reader=csv.reader(stream,delimiter='\t');header=next(reader);assert header[0]=='motif' and header[1:]==[c for c in source_table.column_names if c!='motifs'];count=0
                for row in reader:
                    motif=row[0];assert motif in full.index;values=[int(v) for v in row[1:]];assert values==list(full.loc[motif,header[1:]]);count+=len(values)
                assert count==len(motifs)*len(features)
            export_pins[name]=pin(dest/'rankings.tsv');exports.append(dest/'rankings.tsv')
        assert export_pins['original-v1']==export_pins['lossless-v2']
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']};runs=[];canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        for compiler,folder in [('javac21','workflow-build21-2'),('javac25','workflow-build25-2')]:
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes());artifact=ROOT/built['artifact']['path'];assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']}
            for platform,prefix in platforms.items():
                convert=linux if platform=='linux21' else lambda p:str(p.resolve());cp=(':' if platform=='linux21' else ';').join(convert(p) for p in [artifact,canonical]);dest=out/(compiler+'-'+platform)
                args=[*prefix,'-cp',cp,'org.jlinalg.cli.CisTargetWorkflowCli']
                for key,path in [('settings',settings),('rankings',exports[0]),('signature',signature),('annotations',annotations)]:args.extend(['--'+key,convert(path)])
                run(compiler+'-'+platform,args+['--out',convert(dest)]);actual={r['motif']:r for r in table(dest/'all-motifs.tsv')};assert set(actual)==set(prepared.index)
                for expected in reference:
                    row=actual[expected['motif']]
                    for field in ['auc','nes','leading_recovery','leading_excess']:numeric(row[field],expected[field],('original Feather',expected['motif'],field))
                    assert int(row['leading_rank'])==expected['leading_rank']
                observed=table(dest/'leading-targets.tsv');assert sorted((r['motif'],r['feature'],float(r['weight'])) for r in observed)==sorted((r['motif'],r['feature'],r['weight']) for r in targets)
                actual_curves=table(dest/'recovery-curves.tsv');assert len(actual_curves)==len(prepared.index)
                for row in actual_curves:
                    index=list(prepared.index).index(row['motif']);assert np.allclose([float(v) for k,v in row.items() if k!='motif'],curves[index],rtol=2e-12,atol=2e-12)
                actual_bg=table(dest/'recovery-background.tsv');assert np.allclose([[float(r[f]) for f in ['mean','sd','mean_plus_two_sd']] for r in actual_bg],np.stack([curves.mean(axis=0),curves.std(axis=0),background],axis=1),rtol=2e-12,atol=2e-12)
                assert table(dest/'signature-dispositions.tsv')[-1]['disposition']=='MISSING_FROM_DATABASE' and not table(dest/'tf-targets.tsv')
                receipt=json.loads((dest/'run.json').read_bytes());assert receipt['status']=='PASS_NATIVE_CISTARGET_WITH_WARNINGS' and all(pin(dest/n)['sha256']==v for n,v in receipt['result_sha256'].items())
                runs.append(dict(compiler=compiler,platform=platform,all_original_public_Feather_function_values_and_memberships_match=True,all_native_output_hashes_match=True))
        # Actual missing-runtime execution, not a mocked import: isolated Python -S has no site packages.
        skipped=out/'missing-runtime';run('missing-runtime',[sys.executable,'-I','-S',str(exporter),'--database',str(original),'--out',str(skipped),'--max-cells','200000'],3)
        assert json.loads((skipped/'COMPLETED.json').read_bytes())['status']=='SKIP3_FEATHER_CONVERSION_RUNTIME_UNAVAILABLE' and not (skipped/'rankings.tsv').exists() and 'Skip this conversion' in (skipped/'WARNINGS.md').read_text()
        capacity=out/'capacity-refused';run('capacity-refused',[sys.executable,'-I',str(exporter),'--database',str(original),'--out',str(capacity),'--max-cells','10'],2)
        assert json.loads((capacity/'COMPLETED.json').read_bytes())['status']=='FAIL2_FEATHER_CONVERSION_NOT_ADMITTED' and not (capacity/'rankings.tsv').exists()
        assert pin(original)==original_pin and pin(canonical)==canonical_pin and pin(converted)==json.loads((out/'original-public-file-reference.json').read_bytes())['lossless_v2']
        state.update(status='PASS_ORIGINAL_PUBLIC_FEATHER_REFERENCE_TWO_LOSSLESS_EXPORTS_SIX_NATIVE_FLOWS_AND_ACTUAL_SKIP_REFUSAL_ONLY',
            original_source_file_unchanged=original_pin,source_file_features=22284,source_file_motifs=5,lossless_version_conversion='V1 source retained; V2 copy is a labeled transformation, not a historical database',
            export_pins=export_pins,runs=runs,actual_missing_runtime_skips=1,actual_capacity_refusals=1,canonical_executable_unchanged=canonical_pin,
            source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),exporter]},
            scope='Original ctxcore0.2.0 public FeatherRankingDatabase and enrichment/recovery/leading-edge calls on the complete unmodified five-motif reference fixture after a byte-value-preserving V1-to-V2 file transformation. Java consumes the lossless complete TSV export. This fixture is not any selected paper database; no scores/interval overlap/SCENIC network fitting or full historical paper analysis is established.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-file and no-result checks')
    raise SystemExit(main())
