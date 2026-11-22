#!/usr/bin/env python3
"""Exercise actual packaged original IGV across explicit local scientific profiles.

Requires a pre-admitted runtime;never installs or downloads. Assertions check the
native complete-value audit and exact original identities,not mocked versions.
"""
from __future__ import annotations
import argparse,copy,datetime as dt,hashlib,importlib.util,json,os,subprocess,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',required=True);parser.add_argument('--java25');parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args(argv);out=args.out.absolute();out.mkdir(parents=True,exist_ok=False);runs=[];record=dict(status='running',started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),jar_sha256=sha(args.jar),worker_sha256=sha(ROOT/'src/main/resources/org/jlinalg/omics/IgvWorker.java'),runtime_marker_sha256=sha(args.home/'.jlinalg-igv.json'),runs=runs)
    spec=importlib.util.spec_from_file_location('igv_example',ROOT/'examples/igv-tools/run-workflow.py');example=importlib.util.module_from_spec(spec);spec.loader.exec_module(example);base=example.recipe()
    def run(name,recipe,expected=0,java=None,options=None,environment=None):
        views=out/(name+'-views.json');views.write_text(json.dumps(recipe,ensure_ascii=False),encoding='utf-8');destination=out/name;opts=dict(recipe=str(views),home=str(args.home.absolute()),out=str(destination));opts.update(options or {});carrier=out/(name+'-options.json');carrier.write_text(json.dumps(opts,ensure_ascii=False),encoding='utf-8');command=[java or args.java,'-Xmx1g','-jar',str(args.jar.absolute()),'igv-tools','--options-file',str(carrier)];r=subprocess.run(command,cwd=ROOT,env=environment,capture_output=True,timeout=240);(out/(name+'-cli.stdout')).write_bytes(r.stdout);(out/(name+'-cli.stderr')).write_bytes(r.stderr);assert r.returncode==expected,(name,r.returncode,r.stderr.decode(errors='replace'));session=json.loads((destination/'session.json').read_text());assert 'Required action:' in (destination/'WARNINGS.md').read_text();entry=dict(case=name,exit_code=r.returncode,status=session['status'],session_sha256=sha(destination/'session.json'),cli_stderr_sha256=sha(out/(name+'-cli.stderr')))
        if expected==0:
            audit=json.loads((destination/'native-audit.json').read_text());assert audit['status']=='passed-native-complete-value-audit';assert len(audit['views'])==len(recipe['views']);assert session['runtime_actual_probe']['java_feature']==21;assert not session['igv-render_alive_after_cleanup'];entry.update(views=len(audit['views']),native_audit_sha256=sha(destination/'native-audit.json'),reference_bases=sum(v['reference_bases_compared'] for v in audit['views']),base_counts=sum(t.get('integer_counts_compared',0) for v in audit['views'] for t in v['tracks']),selected_read_comparisons=sum(t.get('selected_alignments_compared',0) for v in audit['views'] for t in v['tracks']))
            # The transaction has moved:all relative original session resources
            # must still resolve to actual published input bytes.
            import xml.etree.ElementTree as ET
            for view in recipe['views']:
                file=destination/(view['id']+'.session.xml');root=ET.parse(file).getroot();assert (file.parent/root.attrib['genome']).is_file()
                for resource in root.findall('./Resources/Resource'):assert (file.parent/resource.attrib['path']).is_file() and 'index' not in resource.attrib
        else:
            assert not (destination/'results.json').exists() and not (destination/'native-audit.json').exists() and not any(destination.glob('*.png'));assert (destination/'partial').is_dir()
        runs.append(entry);print(name,'passed',flush=True);return session
    try:
        run('all-five-input-types',base)
        all_flags=copy.deepcopy(base);all_flags['sequence']=False;all_flags['reads'].update(minimum_mapq=0,duplicates=True,failed=True,secondary=True,supplementary=True,soft_clips=False,junctions=False,deletions_in_coverage=False,color_by='base');run('include-all-flags-no-deletion-coverage',all_flags)
        paired=copy.deepcopy(base);paired['reads']['pairs']=True;run('paired-strand-view',paired)
        for display in ['collapsed','squished']:
            value=copy.deepcopy(base)
            for track in value['tracks']:track['display']=display
            run(display+'-tracks',value)
        empty=copy.deepcopy(base);empty['views']=[dict(id='empty',contig='chr1',start=700,end=780)];run('no-reads-no-SEG-window',empty)
        for kind in ['bedgraph','variable','fixed']:
            value=copy.deepcopy(base);reference=out/(kind+'-reference.fa');reference.write_text('>chr1\n'+'ACGT'*750+'\n',encoding='ascii');value['reference']=str(reference);value['views']=[dict(id='raw',contig='chr1',start=0,end=600)];value.pop('reads');track=copy.deepcopy(base['tracks'][2]);track['file']=str(ROOT/'src/test/resources/igv/bigwig'/(kind+'-3.bw'));track['range']=[-105,105];value['tracks']=[track];run('original-'+kind+'-BigWig',value)
        for identity in ['many-raw-blocks','many-chromosomes']:
            source=ROOT/'src/test/resources/igv/bigwig';metadata=json.loads((source/'provenance.json').read_text());expected=next(r for r in metadata['cases'] if r['case']==identity);reference=out/(identity+'-reference.fa')
            with reference.open('w',encoding='ascii',newline='\n') as stream:
                for contig,length in expected['header']:stream.write('>'+contig+'\n'+('ACGT'*((length+3)//4))[:length]+'\n')
            value=copy.deepcopy(base);value['reference']=str(reference);value.pop('reads');track=copy.deepcopy(base['tracks'][2]);track['file']=str(source/expected['file']);track['range']=[-105,105];value['tracks']=[track];indices=[0,len(expected['header'])//2,len(expected['header'])-1] if len(expected['header'])>1 else [0];value['views']=[dict(id='region'+str(i),contig=expected['header'][i][0],start=0,end=min(600,expected['header'][i][1])) for i in indices];run(identity,value)
        bad=copy.deepcopy(base);bad['views'][0]['contig']='1';run('contig-mismatch-fails',bad,2)
        bad=copy.deepcopy(base);bad['reads'].pop('overlapping_mates');run('missing-overlap-policy-fails',bad,2)
        bad=copy.deepcopy(base);bad['reads']['downsample']=True;run('downsampling-forbidden',bad,2)
        run('no-runtime-skips',base,3,options={'home':str(out/'unavailable-home')})
        session=run('owned-real-worker-timeout',base,2,options={'timeout':'1'});assert session.get('igv-render_timed_out') and session['igv-render_alive_after_cleanup'] is False
        if args.java25:run('Java25-父进程-实际Java21',base,java=args.java25)
        assert sha(args.home/'.jlinalg-igv.json')==record['runtime_marker_sha256'];record.update(status='passed-actual-packaged-original-IGV-workflows',cases=len(runs),complete_reference_bases=sum(r.get('reference_bases',0) for r in runs),complete_integer_count_comparisons=sum(r.get('base_counts',0) for r in runs),selected_read_comparisons=sum(r.get('selected_read_comparisons',0) for r in runs))
    except Exception as failure:record.update(status='failed-retained',error=repr(failure));raise
    finally:record['completed_utc']=dt.datetime.now(dt.timezone.utc).isoformat();(out/'verification.json').write_text(json.dumps(record,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps({key:record[key] for key in ['status','cases','complete_reference_bases','complete_integer_count_comparisons','selected_read_comparisons']}));return 0
if __name__=='__main__':raise SystemExit(main())
