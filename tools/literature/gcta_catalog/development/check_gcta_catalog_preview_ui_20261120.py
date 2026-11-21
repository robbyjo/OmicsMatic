from pathlib import Path
import functools,http.server,json,subprocess,threading,urllib.parse
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';B=T/'build/gcta-catalog-1';O=B/'preview-ui-1';O.mkdir();browser=O/'browser';browser.mkdir();assert json.loads((B/'plan-1/COMPLETED.json').read_bytes())['status']=='PASS_GCTA_PLAN_WITHOUT_CANONICAL_MUTATION'
node=Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node');assert (node/'bin/node.exe').is_file() and (node/'node_modules/playwright').is_dir()
log=(O/'server.log').open('x',encoding='utf-8')
class Handler(http.server.SimpleHTTPRequestHandler):
    def log_message(self,format,*args):log.write(format%args+'\n');log.flush()
    def translate_path(self,url):
        primary=Path(super().translate_path(url))
        if primary.is_file():return str(primary)
        relative=urllib.parse.unquote(urllib.parse.urlsplit(url).path).lstrip('/');fallback=(T/'site'/relative).resolve()
        return str(fallback) if fallback.is_relative_to((T/'site').resolve()) and fallback.is_file() else str(primary)
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Handler,directory=str(B/'plan-1/preview/site')));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
command=[str(node/'bin/node.exe'),str(T/'tools/literature/check_gcta_catalog_ui.cjs'),str(node/'node_modules/playwright'),str(browser),'http://127.0.0.1:'+str(server.server_port)]
try:
    with (O/'stdout').open('xb') as stdout,(O/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
    (O/'PROCESS.json').write_text(json.dumps(dict(command=command,exit_code=p.returncode,preview_reports_with_unchanged_local_site_assets=True),indent=2)+'\n',encoding='utf-8',newline='\n');print((O/'stdout').read_text(errors='replace'),(O/'stderr').read_text(errors='replace')[-3500:],flush=True);assert p.returncode==0,'Inspect retained preview browser evidence'
finally:server.shutdown();server.server_close();thread.join(timeout=10);log.close()
