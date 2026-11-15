from pathlib import Path
import csv, json, hashlib, re, shutil, subprocess, sys, platform, html
from datetime import datetime, timezone
from docx import Document
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, Image
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pypdf import PdfReader
from PIL import Image as PILImage
import reportlab, pypdf, PIL, docx

B=Path(__file__).resolve().parents[1]
P=B.parents[1]
OLD=P/'outputs/2026-10-20T144000-0400-newer-qtl-expanded-mr-manuscript'
POP=P/'outputs/2026-10-02T173517-0400-combined-table1-publication/artifacts/revision/artifacts/participant-characteristics.csv'
A=B/'artifacts'; M=B/'metadata'; L=B/'logs'
START=datetime.now(timezone.utc).isoformat()
inputs=[]
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for x in iter(lambda:f.read(1048576),b''):h.update(x)
    return h.hexdigest()
def use(p,role):
    p=Path(p); inputs.append({'path':str(p),'role':role,'bytes':p.stat().st_size,'sha256':sha(p),'modified':datetime.fromtimestamp(p.stat().st_mtime,timezone.utc).isoformat()});return p
def js(p,x):p.write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def load(n):return json.loads(use(OLD/'artifacts'/n,'verified prior scientific summary').read_text(encoding='utf-8'))
def rows(n):return list(csv.DictReader(use(OLD/'artifacts'/n,'prior scientific result table').open(encoding='utf-8-sig'),delimiter='\t'))
S=load('analysis-summary.json'); MR=load('expanded-mr-summary.json'); MRROWS=rows('main-manuscript-mr-selection.tsv'); JHS=rows('jhs-sensitivity-summary.tsv'); COV=rows('cohort-fdr-summary.tsv')
for f in ['replication-summary.tsv','replication-membership-summary.tsv','effect-correlations.tsv','enrichment-summary.tsv','mr-eligible-chromosome-coverage.tsv']:rows(f)
use(OLD/'metadata/README.md','scientific source lineage and limitations')
old=Document(use(OLD/'artifacts/Splicing_BMI_three_whole_blood_cohorts_20261020.docx','current predecessor manuscript and inherited references'))
refs={int(m.group(1)):m.group(2) for p in old.paragraphs if (m:=re.match(r'^\[(\d+)\] (.*)',p.text))}
refs.update({32:'Homuth G, Wahl S, Muller C, et al. Extensive alterations of the whole-blood transcriptome are associated with body mass index: results of an mRNA profiling study involving two large population-based cohorts. BMC Medical Genomics. 2015;8:65. doi:10.1186/s12920-015-0141-x. https://pmc.ncbi.nlm.nih.gov/articles/PMC4608219/',33:'Joehanes R, Johnson AD, Barb JJ, et al. Gene expression analysis of whole blood, peripheral blood mononuclear cells, and lymphoblastoid cell lines from the Framingham Heart Study. Physiological Genomics. 2012;44:59-75. doi:10.1152/physiolgenomics.00130.2011. https://pmc.ncbi.nlm.nih.gov/articles/PMC3289123/',34:'Farris KM, Andersen E, Donkin I, et al. Splicing across adipocyte differentiation is highly dynamic and impacted by the metabolic phenotype. iScience. 2025;28:114119. doi:10.1016/j.isci.2025.114119. https://pmc.ncbi.nlm.nih.gov/articles/PMC12756580/',35:'Ghanem LR, Kromer A, Silverman IM, et al. Poly(C)-Binding Protein Pcbp2 Enables Differentiation of Definitive Erythropoiesis by Directing Functional Splicing of the Runx1 Transcript. Molecular and Cellular Biology. 2018;38:e00175-18. doi:10.1128/MCB.00175-18. https://pmc.ncbi.nlm.nih.gov/articles/PMC6066754/'})
# Retain full-precision native summaries; no participant-data calculation.
pops={}
for row in csv.DictReader(use(POP,'native provisional phenotype-file summaries').open(encoding='utf-8-sig')):
    if not row['dataset'].endswith('-bmi'):continue
    d=pops.setdefault(row['cohort'],{}); parts=row['field'].strip('/').split('/')
    for k in parts[:-1]:d=d.setdefault(k,{})
    v=row['value']
    try:v=json.loads(v)
    except (ValueError,TypeError):pass
    d[parts[-1]]=v
js(M/'native-population-tables.json',[pops[c] for c in ['fhs','cardia','whi']])
shutil.copy2(POP,A/'participant-characteristics-source.csv')
def parse(name):
    out=[]
    for t in (B/'scripts'/name).read_text(encoding='utf-8-sig').strip().split('\n\n'):
        t=t.strip().replace('[0,1]','0 to 1')
        if t.startswith('## '):out.append({'kind':'subheading','text':t[3:]})
        elif t.startswith('# '):out.append({'kind':'heading','text':t[2:]})
        elif t.startswith('[TABLE_'):out.append({'kind':'table','id':t[7:-1]})
        else:out.append({'kind':'paragraph','text':t})
    return out
blocks=parse('manuscript-body.txt');supp=parse('supplement.txt')
def n(v):return f'{int(v):,}'
def sci(v):return f'{float(v):.2e}'
tables={}
tr=[['Characteristic','FHS','CARDIA','WHI'],['Study role','Discovery','Replication','Replication'],['Sample compartment','Whole blood','Whole blood','Whole blood'],['Verified analysis N','Unresolved','Unresolved','Unresolved']]
tr.append(['Phenotype-file participants, N']+[n(pops[c]['N']) for c in ['fhs','cardia','whi']])
for label,key in [('Age, years; mean (SD)','Age'),('BMI, kg/m2; mean (SD)','BMI')]:
    cells=[]
    for c in ['fhs','cardia','whi']:
        t=pops[c]['continuous'][key]; val=f"{t['mean']:.2f} ({t['sd']:.2f})"
        if t['n']!=pops[c]['N']:val+=f"; n={n(t['n'])}"
        cells.append(val)
    tr.append([label]+cells)
tr.append(['Male sex, n (%)']+[f"{n(pops[c]['sex']['male_n'])} ({pops[c]['sex']['male_percent']:.2f})" for c in ['fhs','cardia','whi']])
tr.append(['BMI >30 kg/m2, n / observed N (%)']+[f"{n(pops[c]['binary']['Obesity']['case_n'])} / {n(pops[c]['binary']['Obesity']['denominator'])} ({pops[c]['binary']['Obesity']['percent']:.2f})" for c in ['fhs','cardia','whi']])
tr.append(['BMI association N, feature range']+[f"{int(float(r['n_min'])):,}-{int(float(r['n_max'])):,}" for c in ['FHS','CARDIA','WHI'] for r in COV if r['phenotype']=='BMI' and r['cohort']==c])
tables['population']={'title':'Table 1. Available participant characteristics (provisional phenotype-file populations)','rows':tr,'widths':[184,100,100,100],'note':'Pre-existing phenotype-file summaries, not verified participants in the association models. CARDIA phenotype-file N exceeds the maximum feature-specific association N. Available-observation means and sample SDs are shown; reduced denominators are printed. Age missingness: FHS 0, CARDIA 35, WHI 0; BMI missingness: FHS 2, CARDIA 73, WHI 36; sex missingness 0. Sex coding follows the documented harmonized convention; WHI is female-only. Descriptive obesity is strictly >30, unlike the inherited association protocol (>=30). Visits, membership, covariate summaries, and model-specific case/control counts remain unresolved.'}
tables['counts']={'title':'Table 2. Transcript discovery, replication, and genetic follow-up','widths':[290,97,97],'rows':[['Analysis stage','BMI','Obesity'],['Common tested universe','224,365','223,927'],['FHS discovery FDR <0.05','39,262','25,531'],['Replication in at least one validator','30,747','20,283'],['Replication in both validators','16,429','9,775'],['Fixed-effect meta-analysis FDR <0.05','61,299','48,973'],['Random-effects sensitivity FDR <0.05','29,150','22,981'],['Estimable primary MR tests','21,645','18,235'],['MR FDR <0.05 (estimable family)','3,877','150'],['MR FDR <0.05 (full selected family)','2,766','105']],'note':'Entries count transcripts or transcript tests, not unique genes or participants. Replication is reduced FDR <0.05 within FHS discoveries; concordant counts are reported in the text. MR families are outcome specific.'}
tr=[['Trait','Transcript / gene','Coefficient (95% CI)','BH FDR','I2']]
for ph in ['BMI','Obesity']:
    for x in S[ph]['top10'][:5]:tr.append([ph,x['feature_id']+'\n'+x['gene_symbol'],f"{x['beta']:.2f} ({x['ci_lower']:.2f}, {x['ci_upper']:.2f})",sci(x['adjusted_p']),f"{x['i_squared']:.1f}%"])
tables['meta']={'title':'Table 3. Leading whole-blood meta-analysis associations','rows':tr,'widths':[48,150,156,83,47],'note':'The five smallest fixed-effect P values per outcome. Estimates retain supplied scales; they are not converted to clinical units. I2 quantifies cohort heterogeneity. Exact transcript versions are retained.'}
tr=[['Trait','Transcript / gene','SNPs / method','MR estimate (95% CI)','BH FDR']]
for x in MRROWS:tr.append(['BMI' if x['phenotype']=='BMI' else 'Obesity',x['feature_id']+'\n'+x['gene_symbol'],f"{int(float(x['instruments']))} / "+('IVW' if x['method']=='IVW_FIXED' else 'Wald'),f"{float(x['estimate']):.2f} ({float(x['ci_lower']):.2f}, {float(x['ci_upper']):.2f})",sci(x['derived_bh_fdr'])])
tables['mr']={'title':'Table 4. Selected primary Mendelian randomization estimates','rows':tr,'widths':[48,147,72,142,75],'note':'Display rule: the three smallest primary P values from distinct genes per outcome plus the strongest remaining multi-instrument result from a further gene. FDR uses all estimable primary tests within each outcome. BMI coefficients use an inverse-rank-normal outcome; obesity coefficients are log odds for ICD-10 E66. The exposure retains the source ratio scale. These are not effects per typical ratio change or interchangeable clinical estimates.'}
# Abstract authored after the body and reconciled to the recorded results.
abstract={'Background':'Relative transcript abundance may reveal adiposity-associated RNA variation obscured by total gene expression. We evaluated its reproducibility across whole-blood cohorts and used genetic evidence to prioritize associated transcripts.','Methods':'FHS supplied discovery and CARDIA and WHI supplied replication. Gene-expression-adjusted summaries for 224,365 BMI and 223,927 obesity transcripts entered fixed-effect meta-analysis, with random-effects sensitivity and functional enrichment. All significant transcripts were assessed for cis-irQTL Mendelian randomization. JHS PBMC results were supplementary. Verified analysis-population totals remain unavailable.','Results':'Meta-analysis identified 61,299 BMI and 48,973 obesity associations; 30,747 and 20,283 FHS discoveries replicated in at least one validator. PCBP2 transcript ENST00000553064.6 led both traits. Median I2 among significant transcripts was 66.6% and 59.4%; random-effects analyses retained 29,150 and 22,981 associations. MR was estimable for 21,645 BMI and 18,235 obesity transcripts, with 3,877 and 150 outcome-specific FDR hits. Most significant MR findings used a single instrument. JHS replicated one available BMI discovery and no obesity discoveries.','Conclusion':'Whole-blood transcript ratios identify reproducible adiposity-associated features, with substantial variation in magnitude across cohorts. Transcript-resolved measurements in defined cell populations and independent genetic evidence could distinguish systemic signatures from specific regulatory processes.'}
TITLE='Whole-blood transcript isoform ratios associated with body mass index and obesity in three TOPMed cohorts'
reforder=[]
def renumber(t):
    def f(m):
        nums=[int(x) for x in m.group(1).split(',')]
        for x in nums:
            assert x in refs, x
            if x not in reforder:reforder.append(x)
        return '['+','.join(str(reforder.index(x)+1) for x in nums)+']'
    return re.sub(r'\[(\d+(?:,\d+)*)\]',f,t)
for bl in blocks+supp:
    if 'text' in bl:bl['text']=renumber(bl['text'])
reference_text=[f'[{i+1}] {refs[x]}' for i,x in enumerate(reforder)]
js(M/'authored-content.json',{'title':TITLE,'abstract':abstract,'blocks':blocks,'supplement':supp,'tables':tables,'references':reference_text,'author':'Codex assistant, attributed editorial draft','human_scientific_review':'pending'})
prose='Abstract\n\n'+'\n\n'.join(k+': '+v for k,v in abstract.items())+'\n\n'
for bl in blocks:
    if bl['kind']=='table':prose+=('[TABLE_1]' if bl['id']=='population' else tables[bl['id']]['title'])+'\n\n'
    else:prose+=bl['text']+'\n\n'
prose+='References\n\n'+'\n\n'.join(reference_text)+'\n\nSupplementary Methods\n\n'+'\n\n'.join(x['text'] for x in supp)
(A/'manuscript-draft.txt').write_text(prose,encoding='utf-8')
commands=[]
def run(cmd):
    t=datetime.now(timezone.utc).isoformat(); r=subprocess.run(list(map(str,cmd)),cwd=B,capture_output=True,text=True,encoding='utf-8',errors='replace');commands.append({'command':list(map(str,cmd)),'cwd':str(B),'start':t,'end':datetime.now(timezone.utc).isoformat(),'exit_code':r.returncode,'stdout':r.stdout,'stderr':r.stderr});js(L/'commands.json',commands)
    if r.returncode:raise RuntimeError(r.stdout+r.stderr)
    return r.stdout+r.stderr
classes=B/'scripts/classes';classes.mkdir(exist_ok=True)
run(['javac','-encoding','UTF-8','-cp',B/'scripts/omicsmatic-0.3.7.jar','-d',classes,B/'scripts/ManuscriptLocalBridge.java'])
run(['java','-cp',str(classes)+';'+str(B/'scripts/omicsmatic-0.3.7.jar'),'org.jlinalg.cli.ManuscriptLocalBridge',B])
for name,file in [('Body','times.ttf'),('BodyBold','timesbd.ttf'),('BodyItalic','timesi.ttf'),('Sans','arial.ttf'),('SansBold','arialbd.ttf')]:pdfmetrics.registerFont(TTFont(name,'C:/Windows/Fonts/'+file))
pdfmetrics.registerFontFamily('Body',normal='Body',bold='BodyBold',italic='BodyItalic',boldItalic='BodyBold')
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='Article',fontName='Body',fontSize=10.5,leading=14.1,spaceAfter=7.2,allowWidows=0,allowOrphans=0))
styles.add(ParagraphStyle(name='SectionA',fontName='SansBold',fontSize=14,leading=18,spaceBefore=13,spaceAfter=9,keepWithNext=True,textColor=colors.HexColor('#16384b')))
styles.add(ParagraphStyle(name='SubA',fontName='SansBold',fontSize=10.3,leading=13.5,spaceBefore=9,spaceAfter=6,keepWithNext=True))
styles.add(ParagraphStyle(name='TitleA',fontName='BodyBold',fontSize=22,leading=26,spaceAfter=16))
styles.add(ParagraphStyle(name='CaptionA',fontName='Body',fontSize=8.8,leading=11.6,spaceAfter=9))
styles.add(ParagraphStyle(name='CellA',fontName='Sans',fontSize=8,leading=10.4))
styles.add(ParagraphStyle(name='CellHead',fontName='SansBold',fontSize=8,leading=10.4,textColor=colors.white))
styles.add(ParagraphStyle(name='RefA',fontName='Body',fontSize=9,leading=11.5,spaceAfter=7))
def clean(t):return t.replace('\u2013','-').replace('\u2014','-').replace('\u2011','-').replace('\u2212','-')
def para(t,sty='Article'):return Paragraph(html.escape(clean(t)).replace('\n','<br/>'),styles[sty])
def table(k):
    q=tables[k]; rr=[[para(str(v),'CellHead' if i==0 else 'CellA') for v in r] for i,r in enumerate(q['rows'])]
    t=Table(rr,colWidths=q['widths'],repeatRows=1,hAlign='LEFT')
    t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#24475b')),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),6),('RIGHTPADDING',(0,0),(-1,-1),6),('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,colors.HexColor('#f0f4f6')]),('LINEBELOW',(0,-1),(-1,-1),.5,colors.HexColor('#9aaab4'))]))
    return [para(q['title'],'SubA'),t,Spacer(1,6),para(q['note'],'CaptionA')]
flow=[para(TITLE,'TitleA'),para('Working manuscript for author review | 14 November 2026','CaptionA'),Spacer(1,6),para('Abstract','SectionA')]
for k,v in abstract.items():flow.append(Paragraph('<b>'+k+':</b> '+html.escape(v),styles['Article']))
flow.append(PageBreak())
for bl in blocks:
    if bl['kind']=='table':flow.extend(table(bl['id']))
    else:flow.append(para(bl['text'],{'heading':'SectionA','subheading':'SubA','paragraph':'Article'}[bl['kind']]))
flow.extend([PageBreak(),*table('meta'),PageBreak(),*table('mr')])
figs=[('figure1-study-flow.png','Figure 1. Study design and analytical scope. Counts refer to transcripts, not unique participants or genes. FHS, CARDIA, and WHI form the primary whole-blood analysis; JHS PBMCs provide supplementary sensitivity. Network and mediation remain planned.'),('figure4-meta-forest-v2.png','Figure 2. Cohort-specific and pooled coefficients for the two lowest-P transcripts per outcome. Squares show cohort estimates and normal-theory 95% confidence intervals. Blue diamonds indicate fixed-effect pooling and open diamonds the DerSimonian-Laird sensitivity. Panels retain supplied coefficient scales and have different axes.'),('figure2-effect-comparisons-publication.png','Figure 3. Cohort coefficient comparisons across the complete primary transcript universe. Top panels show BMI and bottom panels obesity. No significance filter was required for inclusion. Dense central points overlap; coefficients retain their source model scale.'),('figure3-enrichment-publication-v2.png','Figure 4. Leading GO and Reactome annotations. Four lowest-FDR terms were selected within each outcome and collection, with duplicate term identifiers collapsed for display. Color encodes -log10 FDR, capped at 300; blue means a lower significance score, not a negative effect. Terms share genes and are not independent discoveries.')]
(A/'figures').mkdir(exist_ok=True)
for file,caption in figs:
    source=use(OLD/'artifacts/figures'/file,'unchanged verified scientific figure');dest=A/'figures'/file;shutil.copy2(source,dest)
    with PILImage.open(dest) as im:w,hh=im.size
    scale=min(484/w,565/hh)
    flow.extend([PageBreak(),para(caption.split('. ')[0],'SectionA'),Image(str(dest),width=w*scale,height=hh*scale),Spacer(1,10),para(caption,'CaptionA')])
flow.extend([PageBreak(),para('References','SectionA')])
for rr in reference_text:flow.append(para(rr,'RefA'))
flow.extend([PageBreak(),para('Supplementary Methods','SectionA')])
for bl in supp:flow.append(para(bl['text'],'SubA' if bl['kind']=='subheading' else 'Article'))
PDF=A/'Splicing_BMI_current_outline_20261114.pdf'
def footer(canvas,doc):
    canvas.saveState();canvas.setStrokeColor(colors.HexColor('#c9d4da'));canvas.setLineWidth(.4);canvas.line(54,41,558,41);canvas.setFont('Sans',8);canvas.setFillColor(colors.HexColor('#596b76'));canvas.drawString(54,29,'Splicing-BMI | Working manuscript');canvas.drawRightString(558,29,str(doc.page));canvas.restoreState()
document=SimpleDocTemplate(str(PDF),pagesize=(612,792),rightMargin=64,leftMargin=64,topMargin=49,bottomMargin=54,title=TITLE,author='Author team pending confirmation',subject='Splicing-BMI review manuscript; bundle '+B.name,creator='Current OmicsMatic writing template and local ReportLab PDF export')
document.build(flow,onFirstPage=footer,onLaterPages=footer)
reader=PdfReader(PDF); text='\n'.join(x.extract_text() for x in reader.pages)
checks={'abstract_once':sum(x.extract_text().count('Abstract') for x in reader.pages)==1,'main_counts_preserved':all(x in text for x in ['61,299','48,973','21,645','18,235','3,877']),'all_table_titles':all(f'Table {i}.' in text for i in range(1,5)),'all_figures':all(f'Figure {i}.' in text for i in range(1,5)),'no_native_marker': '[TABLE_1]' not in text,'corrected_source_retained':'2,433,662' in text,'provisional_table_labeled':'provisional phenotype-file populations' in text,'adverse_JHS_result_present':'none of 25,450' in text,'no_replacement_glyph':'\ufffd' not in text}
assert all(checks.values()),checks
native=(A/'native-render/manuscript.html').read_text(encoding='utf-8');review=json.loads((A/'native-render/review.json').read_text())
assert review['abstract_review']['status']=='structure_complete_review_required'
assert review['population_review']['analysis_population_verified'] is False
assert review['table_1_marker_count']==1
for c in ['fhs','cardia','whi']:assert f"{pops[c]['continuous']['BMI']['mean']:.2f}" in native
checks['current_native_renderer_and_checks']=True;checks['native_table_BMI_means_match_pdf']=True
js(L/'validation.json',{'checks':checks,'pages':len(reader.pages),'native_review':review,'visual_review':'pending','main_text_words':sum(len(x.get('text','').split()) for x in blocks),'abstract_words':sum(len(x.split()) for x in abstract.values())})
js(M/'input-provenance.json',inputs)
js(M/'software_versions.json',{'os':platform.platform(),'python':sys.version,'reportlab':reportlab.Version,'pypdf':pypdf.__version__,'Pillow':PIL.__version__,'python-docx':docx.__version__,'java':run(['java','-version']),'omicsmatic':'0.3.7 working tree packaged 14 November 2026','jar_sha256':sha(B/'scripts/omicsmatic-0.3.7.jar'),'writing_template':'2026-11-14.2','prompt_sha256':sha(B/'scripts/manuscript-writing-v1.txt'),'old_analysis_versions':'JLinAlg 0.3.6 a8d5052; Java 21.0.12.1; see predecessor metadata'})
js(M/'parameters.json',{'start':START,'scientific_predecessor':str(OLD),'participant_source':str(POP),'cohorts_primary':['FHS','CARDIA','WHI'],'JHS':'PBMC supplementary only','new_association_or_MR_runs':False,'template':'2026-11-14.2','drafting_route':'Assistant-authored application of current template, passed through current native local renderer/checks; not an end-to-end provider inference or native ledger revision','external_provider_calls_from_project':0,'random_seed':'not applicable; no new stochastic computation','pdf':'US Letter; 10.5 pt Times; 14.1 pt leading','author_review_required':True})
(A/'editorial-review.txt').write_text('Changes in this revision\n\nThe Introduction now develops adiposity, existing human evidence, the transcript-ratio measurement, blood sampling, the gap, and objectives. Results begin with a visible provisional participant table. The structured abstract was written after the body. Discussion develops replication, exact-transcript interpretation, heterogeneity/PBMC transfer, and genetic prioritization with focused experiments. Scientific caveats remain next to their implications; internal execution and field-correction details are outside the main argument. The analysis is the corrected 20 October three-cohort result, not the superseded 7 October four-cohort draft.\n\nRequired scientific completion\n\nVerify participant membership/visits and cohort covariates; resolve BMI >30 descriptive versus >=30 association coding; supply ethics/consent and author details; confirm cohort models, scales and quantification; independently appraise interpretation and citations. Network and mediation remain pending. No submission approval is implied.\n\nGeneration route\n\nThe assistant authored this draft using template 2026-11-14.2 and the current completed results. The current OmicsMatic native draft renderer generated the reading HTML, participant table, and abstract/writing reviews via the output-local bridge. ReportLab typeset the PDF, with values checked against supplied summaries. This is not a new full study-pipeline execution or an external model-provider integration test.\n',encoding='utf-8')
js(M/'reference-review.json',{'new_sources':[{'id':32,'url':'https://pmc.ncbi.nlm.nih.gov/articles/PMC4608219/','basis':'primary publisher/search metadata and retrieved study excerpts; bounded background statement'},{'id':33,'url':'https://pmc.ncbi.nlm.nih.gov/articles/PMC3289123/','basis':'retrieved primary full text; blood-compartment context'},{'id':34,'url':'https://pmc.ncbi.nlm.nih.gov/articles/PMC12756580/','basis':'published 2025 version, PubMed abstract and indexed PMC excerpts; supersedes 2023 preprint'},{'id':35,'url':'https://pmc.ncbi.nlm.nih.gov/articles/PMC6066754/','basis':'retrieved primary full text; mouse embryonic erythropoiesis, not human measured isoform validation'}],'existing_references':'Inherited from supplied predecessor manuscript; no comprehensive new systematic review claimed','public_search_date':'2026-11-14','private_data_in_public_search':False})
print(json.dumps({'pdf':str(PDF),'pages':len(reader.pages),'checks':checks,'native_abstract_status':review['abstract_review']['status']},indent=2))
