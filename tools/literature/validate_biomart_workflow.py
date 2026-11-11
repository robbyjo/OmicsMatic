# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Full original-biomaRt adapter controls: real endpoints, complete independent original dataframes, typed states, protocol failures and atomic admission."""
import argparse,copy,json,subprocess,threading,time,urllib.parse,re
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save
from validate_mashr_native import JAVA21
from prepare_biomart_workflow_example import example

def run(command,home,env=None,timeout=900):
    with (home/'stdout').open('xb') as first,(home/'stderr').open('xb') as second:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=timeout,creationflags=subprocess.CREATE_NO_WINDOW)
    save(home/'process.json',dict(command=command,exit_code=r.returncode));return r.returncode

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--runtime268',required=True,type=Path);p.add_argument('--runtime260',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--live',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='compiled-BioMart-workflow-not-analysis-admission';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};source=[Path(__file__).resolve(),ROOT/'tools/literature/compare_biomart_workflow.R',ROOT/'tools/literature/prepare_biomart_workflow_example.py'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in source};pins.update(build['source_pins']);state=dict(status='FAIL2_BIOMART_WORKFLOW_VALIDATION_INCOMPLETE',artifact=build['artifact'],source_pins=pins,protocol=[],live=[],implementation_credit=False,whole_goal_complete=False);records=[];lock=threading.Lock();mode={'name':'healthy'}
    for name in pins:
        f=out/'source-snapshot'/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((ROOT/name).read_bytes())
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def respond(self,data,status=200,kind='text/plain; charset=UTF-8'):
            raw=data.encode('utf8');self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(raw)));self.end_headers()
            try:self.wfile.write(raw)
            except (BrokenPipeError,ConnectionResetError):pass
        def do_GET(self):
            params=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query);kind=params.get('type',[''])[0]
            with lock:records.append(dict(method='GET',path=self.path,mode=mode['name']))
            if mode['name']=='metadata-unavailable':self.respond('Explicit synthetic unavailable service',503);return
            if kind=='registry':self.respond('<MartRegistry><MartURLLocation database="synthetic_db" default="1" displayName="Synthetic protocol reference 1" host="127.0.0.1" includeDatasets="" martUser="" name="synthetic_mart" path="/biomart/martservice" port="'+str(self.server.server_address[1])+'" serverVirtualSchema="default" visible="1"/></MartRegistry>');return
            if kind=='datasets':self.respond('TableSet\tsynthetic_dataset\tSynthetic dataset\t1\tsynthetic-assembly\t200\t50000\tdefault\t2026-11-07\n');return
            if kind=='attributes':self.respond(''.join(n+'\t'+d+'\tFull '+d+'\tfeature_page\n' for n,d in [('gene','Gene ID'),('symbol','Gene symbol'),('metric','Numeric metric'),('flag','Logical flag')]));return
            if kind=='filters':self.respond(''.join(n+'\tSynthetic filter\t[]\tFull synthetic filter\t\t'+t+'\t=\n' for n,t in [('gene','text'),('secondary','text'),('with_flag','boolean_list'),('numeric_filter','text')]));return
            self.respond('Unknown explicit protocol metadata type',400)
        def do_POST(self):
            length=int(self.headers.get('Content-Length','0'));assert 0<length<=8<<20;raw=self.rfile.read(length);xml=urllib.parse.parse_qs(raw.decode('ascii'),keep_blank_values=True,strict_parsing=True)['query'][0]
            with lock:records.append(dict(method='POST',path=self.path,mode=mode['name'],query=xml,form=raw.decode('ascii')))
            if 'FAIL_LATE' in xml:self.respond('Query ERROR: explicitly requested synthetic late failure');return
            if 'SLOW' in xml:time.sleep(10)
            if 'REDIRECT' in xml:self.send_response(302);self.send_header('Location','http://127.0.0.1:1/forbidden');self.send_header('Content-Length','0');self.end_headers();return
            attrs=re.findall(r'<Attribute name = [\'\"]([^\'\"]+)[\'\"]/>',xml);names={'gene':'Gene ID','symbol':'Gene symbol','metric':'Numeric metric','flag':'Logical flag'};headers='\t'.join(names[n] for n in attrs)
            if 'FALLBACK' in xml:
                if 'formatter="HTML"' in xml or "formatter='HTML'" in xml:self.respond('<html><table><tr>'+''.join('<th>'+names[n]+'</th>' for n in attrs)+'</tr><tr>'+''.join('<td>HTML_'+n+'</td>' for n in attrs)+'</tr></table></html>');return
                self.respond(headers+'\n'+'\t'.join('x' for n in attrs)+'\n'+'\t'.join('y' for n in attrs[:-1])+'\n');return
            if 'EMPTY_RESULT' in xml:self.respond(headers+'\n');return
            values={'gene':['SYN_1','SYN_2','SYN_1','SYN_4','SYN_5','SYN_6'],'symbol':['alpha-α','beta','NA','','#literal','last'],'metric':['1','NA','NaN','Inf','-Inf','3.25'],'flag':['TRUE','FALSE','TRUE','FALSE','TRUE','FALSE']};response=headers+'\n'+'\n'.join('\t'.join(values[n][i] for n in attrs) for i in range(6))+'\n';self.respond(response)
    server=ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True;thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();port=server.server_address[1]
    def case(id,token='TYPED',attrs=None,**kwargs):return dict(id=id,attributes=attrs or ['gene','symbol','metric','flag'],filters=[dict(name='gene',type='CHARACTER',values=[token])],unique_rows=True,check_filters=True,**kwargs)
    def protocol(manifest):
        r=example(manifest,out/'unused','GRCh37');r['endpoint']=dict(base_url='http://127.0.0.1',port=port,mart='synthetic_mart',registry_version='Synthetic protocol reference 1',database='synthetic_db',dataset='synthetic_dataset',assembly='synthetic-assembly',virtual_schema='default');r['queries']=[case('typed'),case('three-cycle',attrs=['metric','gene','symbol']),case('empty','EMPTY_RESULT',attrs=['gene','symbol']),case('boolean',attrs=['gene','symbol']),case('numeric',attrs=['gene','symbol']),case('quote',attrs=['gene','symbol']),case('cached-duplicate-chunks',attrs=['gene','symbol'])];r['queries'][3]['filters']=[dict(name='with_flag',type='LOGICAL',values=['TRUE'])];r['queries'][4]['filters']=[dict(name='numeric_filter',type='NUMERIC',values=['1.9','-1.9','Inf','NaN','NA'])];r['queries'][5]['filters'][0]['values']=['A"B','A&B','alpha-α','x,y'];r['queries'][6]['filters'][0]['values']=['SAME']*10000;return r
    def execute(name,r,expected,compare=False,java=None):
        home=out/name;home.mkdir();r=copy.deepcopy(r);r['out']=str(home/'analysis');save(home/'request.json',r);command=[str(java or JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.BiomartWorkflowCli','--request',str(home/'request.json')];code=run(command,home);assert code==expected,(name,code,(home/'stderr').read_text(errors='replace')[-5000:]);analysis=home/'analysis';report=json.loads((analysis/'analysis-status.json').read_bytes()) if analysis.exists() else None
        if expected==0:
            assert report['result_admitted'] and report['status']=='PASS_ORIGINAL_BIOMART_WORKFLOW';assert {p.name for p in (analysis/'results').iterdir()}=={q['id'] for q in r['queries']}
            if compare:
                reference=home/'independent-reference';reference.mkdir();temp=reference/'private-temp';temp.mkdir();env=environment();env.update(TMPDIR=str(temp),TMP=str(temp),TEMP=str(temp),BIOMART_CACHE=str(reference/'private-cache'));runtime=json.loads(Path(r['runtime_manifest']).read_bytes());rc=run([runtime['rscript'],'--vanilla',str(ROOT/'tools/literature/compare_biomart_workflow.R'),str(home/'request.json'),str(reference),*runtime['libraries']],reference,env);assert rc==0,(name,'independent original comparison failed',(reference/'stderr').read_text(errors='replace'));proof=json.loads((reference/'COMPLETED.json').read_bytes());assert proof['status']=='PASS_COMPLETE_INDEPENDENT_ORIGINAL_DATAFRAMES_AND_TYPED_STATES'
        else:
            assert report and not report['result_admitted'] and not (analysis/'results').exists();assert 'REQUIRED ACTION:' in (analysis/'WARNINGS.md').read_text();assert 'SKIP3' in report['status'] if expected==3 else 'FAIL2' in report['status']
        for receipt in analysis.rglob('process.json'):
            value=json.loads(receipt.read_bytes())
            if 'owned_live_after_cleanup' in value:assert not value['owned_live_after_cleanup'],(name,receipt)
        print(name,'exit',code,'admitted',report['result_admitted'],flush=True);return dict(name=name,exit_code=code,admitted=report['result_admitted'],complete_independent_comparison=compare,queries=len(r['queries']))
    try:
        for version,manifest in [('268',a.runtime268.resolve()),('260',a.runtime260.resolve())]:
            r=protocol(manifest);state['protocol'].append(execute('protocol-'+version,r,0,True))
            fallback=copy.deepcopy(r);fallback['queries']=[case('fallback','FALLBACK_TSV',attrs=['gene','symbol','metric'])];fallback['allow_original_html_fallback']=True;state['protocol'].append(execute('fallback-'+version,fallback,0,True))
            fallback['allow_original_html_fallback']=False;state['protocol'].append(execute('fallback-refused-'+version,fallback,2))
        r=protocol(a.runtime268.resolve());r['queries']=[case('first'),case('second','FAIL_LATE')];state['protocol'].append(execute('late-query-error-atomic',r,2));assert (out/'late-query-error-atomic/analysis/worker/first/original-result.rds').exists()
        r=protocol(a.runtime268.resolve());r['queries']=[case('first'),case('second','REDIRECT')];state['protocol'].append(execute('query-redirect-refused-atomic',r,2))
        r=protocol(a.runtime268.resolve());r['queries']=[case('only')];r['endpoint']['assembly']='incorrect-explicit-assembly';state['protocol'].append(execute('assembly-mismatch',r,3));r['endpoint']['assembly']='synthetic-assembly';r['endpoint']['registry_version']='incorrect-explicit-release';state['protocol'].append(execute('release-mismatch',r,3))
        r=protocol(a.runtime268.resolve());r['runtime_manifest']=str(out/'missing-runtime.json');state['protocol'].append(execute('missing-runtime',r,3))
        mode['name']='metadata-unavailable';r=protocol(a.runtime268.resolve());r['http_timeout_seconds']=1;r['timeout_seconds']=10;state['protocol'].append(execute('metadata-unavailable',r,3));mode['name']='healthy'
        r=protocol(a.runtime268.resolve());r['queries']=[case('first'),case('second','SLOW')];r['timeout_seconds']=8;r['http_timeout_seconds']=60;state['protocol'].append(execute('owned-timeout',r,2));assert (out/'owned-timeout/analysis/worker/first/original-result.rds').exists()
        if a.live:
            for version,manifest in [('268',a.runtime268.resolve()),('260',a.runtime260.resolve())]:
                for profile in ['GRCh37','GRCh38-115','GRCh38-116']:state['live'].append(execute('live-'+version+'-'+profile,example(manifest,out/'unused',profile),0,True))
        assert all(pin(ROOT/n)==v for n,v in pins.items());state['status']='passed-complete-biomaRt-workflow-controls-and-independent-original-comparisons';state['admitted_live_analysis_profiles']=len(state['live']);state['protocol_control_count']=len(state['protocol'])
    except Exception as error:state['error']=repr(error);print('FAIL2',repr(error),flush=True)
    finally:server.shutdown();server.server_close();thread.join(10);save(out/'synthetic-server-records.json',records);save(out/'COMPLETED.json',state)
    print(state['status']);return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete adapter controls')
    raise SystemExit(main())
