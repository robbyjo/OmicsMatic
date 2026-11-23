from pathlib import Path
import hashlib,json,urllib.request,urllib.error
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-smartpca-catalog-255'
def get(url):
 req=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-smartpca-catalog-deployment-check','Cache-Control':'no-cache'})
 with urllib.request.urlopen(req,timeout=40) as response:return response.status,response.read()
run=json.loads(get('https://api.github.com/repos/robbyjo/OmicsMatic/actions/runs/45170572404')[1]);assert run['head_sha']=='f8bcdded2fd4198c111e1354bbdfab5f00e7eaa1'
if run['status']!='completed':
 print(json.dumps(dict(run=run['id'],status=run['status'],conclusion=run['conclusion'])));raise SystemExit(4)
jobs=json.loads(get(run['jobs_url'])[1]);checks=[]
for name,expected in [('',200),('literature-review.html',404),('literature-warnings.html',404)]:
 url='https://robbyjo.github.io/OmicsMatic/'+name
 try:code,raw=get(url)
 except urllib.error.HTTPError as error:code=error.code;raw=error.read()
 checks.append(dict(url=url,status=code,expected=expected,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()))
workflow=(T/'.github/workflows/pages.yml').read_bytes();assert b'ignore_patterns("literature-review.html", "literature-warnings.html")' in workflow
record=dict(status='PASS_AUTOMATIC_PAGES_DEPLOYMENT_REPORT_EXCLUSIONS' if run['conclusion']=='success' and all(x['status']==x['expected'] for x in checks) else 'FAILED_DEPLOYMENT_OR_LIVE_CHECK',
 run_id=run['id'],head_sha=run['head_sha'],conclusion=run['conclusion'],url=run['html_url'],jobs=[dict(name=j['name'],status=j['status'],conclusion=j['conclusion']) for j in jobs['jobs']],
 checks=checks,literature_report_exclusions_in_unchanged_workflow=True,literature_reports_deployed=False,new_release_published=False)
(O/'DEPLOYMENT.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(record,indent=2));assert record['status'].startswith('PASS')
