# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record actual original getBM XML, source splitting/coercions and full returned frames using an explicit synthetic HTTP server."""
import argparse,concurrent.futures,json,re,subprocess,threading,urllib.parse
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save

def cases():
    def f(name,kind,values):return dict(name=name,type=kind,values=values)
    def c(name,filters,**extra):return dict(id=name,filters=filters,unique_rows=True,check_filters=True,**extra)
    result=[c('unfiltered',[]),c('character',[f('gene','CHARACTER',['SYN_B','SYN_A','SYN_B'])]),c('source-quoting',[f('gene','CHARACTER',['A"B',"A'B",'alpha-\u03b1','A&B','x<y','x>y','x,y','line\nbreak','tab\tinside',''])]),c('boolean-true',[f('with_flag','LOGICAL',['TRUE'])]),c('boolean-false',[f('with_flag','LOGICAL',['FALSE'])]),c('boolean-NA',[f('with_flag','LOGICAL',['NA'])]),c('numeric-truncation',[f('numeric_filter','NUMERIC',['1.9','-1.9','0.0','-0.0','2147483647','2147483647.5','-2147483647.9'])]),c('numeric-range-missing',[f('numeric_filter','NUMERIC',['2147483648','-2147483648','-2147483648.9','1e100','Inf','-Inf','NaN','NA'])]),c('logical-nonboolean',[f('logical_text','LOGICAL',['TRUE','FALSE','NA'])]),c('two-ordered-filters',[f('secondary','CHARACTER',['B2','B1']),f('gene','CHARACTER',['G2','G1'])]),c('unknown-filter-explicit',[f('not_known','CHARACTER',['G1'])]),c('unknown-filter-refused',[f('not_known','CHARACTER',['G1'])]),c('invalid-attribute',[f('gene','CHARACTER',['G1'])],attributes=['no_attribute']),c('boolean-numeric-refused',[f('with_flag','NUMERIC',['1'])]),c('boolean-vector-refused',[f('with_flag','LOGICAL',['TRUE','FALSE'])]),c('empty-single-values-refused',[f('gene','CHARACTER',[])])]
    result[10]['check_filters']=False
    result.append(c('duplicates-allowed',[f('gene','CHARACTER',['G1','G1'])]));result[-1]['unique_rows']=False
    for count in [4999,5000,5001,9999,10000,10001,15000]:result.append(c('boundary-'+str(count),[f('gene','CHARACTER',['G'+str(i) for i in range(count)])]))
    result.append(c('cartesian-order',[f('gene','CHARACTER',['G'+str(i) for i in range(5001)]),f('secondary','CHARACTER',['S'+str(i) for i in range(10000)])]))
    result.append(c('duplicate-chunk-cache',[f('gene','CHARACTER',['SAME']*10000)]))
    result.append(c('three-attributes',[f('gene','CHARACTER',['G1'])],attributes=['gene','symbol','metric']))
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/biomart-runtime') and not out.exists();out.mkdir(parents=True);setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_ORIGINAL_BIOMART_PACKAGES_AND_SOURCE_BODIES_ONLY' and setup['preexisting_libraries_unchanged'];sources=[Path(__file__).resolve(),ROOT/'tools/literature/collect_biomart_query_reference.R'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources}
    for source in sources:(out/source.name).write_bytes(source.read_bytes())
    controls=cases();save(out/'cases.json',controls);records=[];lock=threading.Lock()
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def do_POST(self):
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=2097152:self.send_error(413);return
            raw=self.rfile.read(length);query=urllib.parse.parse_qs(raw.decode('ascii'),keep_blank_values=True,strict_parsing=True)['query'];assert len(query)==1;xml=query[0];parts=urllib.parse.urlsplit(self.path).path.strip('/').split('/');assert len(parts)==2;version,case=parts
            attributes=re.findall(r'<Attribute name = [\'\"]([^\'\"]+)[\'\"]/>',xml);mapping={'gene':('Gene ID','SYN_GENE'),'symbol':('Gene symbol','synthetic-alpha'),'metric':('Numeric metric','3.25')};selected=[mapping.get(n,('Invalid attribute','invalid')) for n in attributes];response=('\t'.join(v[0] for v in selected)+'\n'+'\t'.join(v[1] for v in selected)+'\n').encode('utf8')
            with lock:index=len(records)+1;records.append(dict(index=index,version=version,case=case,path=self.path,headers=dict(self.headers.items()),query=xml,request_body=raw.decode('ascii'),synthetic_response=response.decode('utf8')))
            self.send_response(200);self.send_header('Content-Type','text/plain; charset=UTF-8');self.send_header('Content-Length',str(len(response)));self.end_headers();self.wfile.write(response)
    server=ThreadingHTTPServer(('127.0.0.1',0),Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();service='http://127.0.0.1:'+str(server.server_address[1]);state=dict(status='FAIL2_ORIGINAL_BIOMART_QUERY_REFERENCE_INCOMPLETE',source_pins=pins,protocol_cases=len(controls),implementation_credit=False,biological_annotation_validated=False)
    try:
        for version in ['2.68.0','2.60.1']:
            env=environment();temporary=out/('temp-'+version);temporary.mkdir();env.update(TMPDIR=str(temporary),TMP=str(temporary),TEMP=str(temporary));command=[str(RSCRIPT),'--vanilla',str(sources[1]),str(out),str(a.setup.resolve()),version,service,*setup['versions'][version]['libraries']]
            with (out/(version+'.stdout')).open('xb') as first,(out/(version+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=600,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(version+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(version,r.returncode);report=json.loads((out/(version+'-COMPLETED.json')).read_bytes());assert len(report['cases'])==len(controls);print(version,'actual original public getBM protocol cases recorded:',len(controls),flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items());state['status']='passed-original-public-getBM-protocol-reference-capture-only'
    except Exception as error:state['error']=repr(error)
    finally:server.shutdown();server.server_close();thread.join(timeout=10);save(out/'http-records.json',records);save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original public-operation references')
    raise SystemExit(main())
