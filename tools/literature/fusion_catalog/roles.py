"""Current FUSION roles, distinguishing software, shared methods and homonyms."""
from pathlib import Path
import hashlib
import json
import re
import zipfile
from fusion_catalog import primary as p

POSITIVE = ['P112', 'P120', 'P131', 'P181', 'P183', 'P238']
SUMMARIES = {
    'P112': 'Performed CanUD TWAS with adult and fetal cortex expression models and European 1000 Genomes reference LD. A weighted gene burden test combined SNP-trait associations with expression weights. Reference model filters included nominal cis-heritability P<0.01 and five-fold prediction R2>0.01 at nominal P<0.01; transcriptome-wide significance was P<2.5e-6.',
    'P120': 'Performed hypothyroidism TWAS with six GTEx v8 tissues (hypothalamus, pituitary, thyroid, spleen, whole blood and pancreas), followed by internal COLOC. The paper considered gene associations at P<2.97e-6 and reported shared-causal evidence at PP4>0.75 for genes within sentinel-variant windows.',
    'P131': 'Performed univariate FUSION TWAS with reported defaults and 45 European GTEx v8 tissue weights before Genomic SEM T-SEM. FUSION colocalization with the same weights was integrated with T-SEM; PP4>0.80 identified colocalized genes. The reported Genomic SEM0.0.5 and ACAT0.91 versions belong to separate packages.',
    'P181': 'Trained transcriptomic prediction models for DDP/rDDP phenotypes and tested multimodal xTWAS across 114 GWAS traits using FUSION with reported defaults. Pantry KDP xTWAS was a comparison. Associations used P<5e-8; subsequent coloc-package analyses used PP4>0.8 and are distinct from the FUSION invocation.',
    'P183': 'Trained prediction weights for lncRNAs and protein-coding genes using BSLMM, LASSO, top1 and elastic net, five-fold cross-validation and heritability-significance threshold0.01. Used the weights for TWAS of 14 traits with Bonferroni P<0.05/m and European 1KGP3 LD. FUSION-TWAS was explicitly not performed for circRNAs.',
    'P238': 'Compared SpliTWAS with LeafCutter paired with FUSION. The Methods also describe an ImpG-Summary-style weighted summary-association algorithm shared with FUSION, using European 1000 Genomes LD. That shared formula does not establish execution of original FUSION scripts; the actual named software comparator is supported by a separate Results paragraph.',
}
LIMITS = {
    'P112': 'Reference training filters and association thresholds are paper settings; exact FUSION edition, executable environment and complete caller options are unreported. Current synthetic qualification is not the CanUD study or a reconstruction of its cortex models.',
    'P120': 'The paper association threshold and PP4 filter do not identify the original coloc_P trigger, GWAS sample-size argument, priors or complete caller command. Do not infer them. Current source/profile is not an established historical paper edition.',
    'P131': 'Genomic SEM and ACAT versions are not FUSION versions. Current availability covers FUSION training/association/colocalization and postprocessing; it does not reproduce the external Genomic SEM T-SEM model or infer unstated historical FUSION options.',
    'P181': 'Prepared DDP/rDDP and Pantry phenotype construction are separate upstream tasks. The coloc follow-up is separately named; do not assert it was the internal FUSION COLOC mode. Exact FUSION edition and complete caller settings remain unknown.',
    'P183': 'Use the European 1KGP3 panel for the stated FUSION role; the 8,652-person HRS panel belongs to SMR/OPERA. Do not claim circRNA FUSION analysis. The four model names and five-fold CV are reported settings, not proof that the current pinned master runtime reproduces the study.',
    'P238': 'Keep shared algorithm, actual LeafCutter/FUSION comparison and background discussion distinct. No exact FUSION edition, prediction-model/caller settings or complete author comparison execution is established by these paragraphs.',
}


def author_audit(folder):
    folder = Path(folder)
    record_raw = (folder / 'zenodo-8323001.json').read_bytes()
    record = json.loads(record_raw)
    p.require(record['id'] == 8323002 and len(record['files']) == 1, 'Wrong manuscript version record')
    item = record['files'][0]
    raw = (folder / 'manuscript-v1.0.0.zip').read_bytes()
    p.require(len(raw) == item['size'] == 77379 and
              'md5:' + hashlib.md5(raw).hexdigest() == item['checksum'] == 'md5:458e661a4214f5f3de053c93f88cd438',
              'Author manuscript archive differs')
    with zipfile.ZipFile(folder / 'manuscript-v1.0.0.zip') as archive:
        names = archive.namelist()
        p.require(len(names) == len(set(names)) == 38 and
                  all(n.startswith('bhattacharya-a-bt-isotwas_manu_scripts-c0e9da9/') and '..' not in Path(n).parts for n in names),
                  'Changed/unsafe author archive population')
        texts = {n: archive.read(n) for n in names if not n.endswith('/')}
    witnesses = []
    for suffix in ['Real Data/Model Training/trainAdultModels.R',
                   'Real Data/Model Training/trainDevelopmentalModels.R',
                   'Real Data/Model Training/trainGTExModels.R',
                   'Real Data/Model Training/trainPECLCWeights.R']:
        name = next(n for n in texts if n.endswith('/' + suffix))
        lines = texts[name].decode('utf-8-sig').splitlines()
        functions = {fn: [i for i, line in enumerate(lines, 1) if re.search(r'\b' + fn + r'\s*\(', line)]
                     for fn in ['univariate_elasticnet', 'univariate_blup', 'univariate_susie']}
        imports = [i for i, line in enumerate(lines, 1) if re.search(r'\b(?:library|require)\s*\(\s*isotwas\s*\)', line)]
        p.require(imports and all(functions.values()), 'Missing isoTWAS baseline witnesses: ' + suffix)
        witnesses.append(dict(path=suffix, source=p.pin(texts[name]), isotwas_import_lines=imports, function_call_lines=functions))
    original_commands = []
    for name, content in texts.items():
        try:
            text = content.decode('utf-8-sig')
        except UnicodeDecodeError:
            continue
        if re.search(r'FUSION\.(?:assoc_test|compute_weights|post_process)\.R|gusevlab/fusion_twas', text, re.I):
            original_commands.append(name)
    p.require(not original_commands, 'New original FUSION invocation requires individual review')
    return dict(concept_doi='10.5281/zenodo.8323001', version_record=8323002,
                version='v1.0.0', archive=p.pin(raw), published_md5=item['checksum'],
                archive_URL=item['links']['self'], archive_root_revision_prefix='c0e9da9',
                record=p.pin(record_raw), record_license=record['metadata']['license'],
                license_file_present=any(re.search(r'(^|/)(LICENSE|COPYING)', n, re.I) for n in texts),
                manuscript_members={n.split('/', 1)[1]: p.pin(v) for n, v in sorted(texts.items())},
                inspected_baseline_training_scripts=witnesses, original_FUSION_command_matches=original_commands,
                source_or_archive_redistributed=False, author_code_executed=False,
                conclusion='The archived manuscript scripts call isoTWAS univariate elastic-net, BLUP and SuSiE functions. This supports a FUSION-labelled method baseline, not established execution of the original FUSION scripts. Exact installed isoTWAS edition remains unestablished.')


def audit(cache, author_folder, before):
    census = p.audit(cache)
    frozen = json.loads((p.ROOT / 'docs/literature/fusion-source-development/CENSUS.json').read_bytes())
    p.require(census == frozen, 'Current full-250 primary source census differs')
    author = author_audit(author_folder)
    papers = json.loads(before)
    candidates = {r['paper_id']: r for r in census['candidates']}
    roles = []
    for paper_id in POSITIVE:
        source = candidates[paper_id]
        paper = next(row for row in papers if row['paper_id'] == paper_id)
        uses = [u for u in paper['tools'] if u['tool_id'] == 'fusion']
        p.require(len(uses) == 1 and paper['xml_sha256'] == source['xml_sha256'], 'Changed positive source observation')
        use = uses[0]
        anchor = next(a for a in source['anchors'] if a['paragraph_index'] == use['evidence_paragraph_index'])
        p.require(anchor['paragraph_sha256'] == use['evidence_paragraph_sha256'] and
                  anchor['section'] == use['evidence_section'], 'Changed extraction anchor')
        roles.append(dict(paper_id=paper_id, pmcid=paper['pmcid'], doi=paper['doi'],
                          xml_sha256=source['xml_sha256'], summary=SUMMARIES[paper_id], scope=LIMITS[paper_id],
                          source_pointers=source['anchors'], original_observation=use,
                          usage_context='software-comparator-and-shared-method-description' if paper_id == 'P238' else 'analysis-method-description',
                          reported_FUSION_edition=None, exact_author_runtime_or_command_established=False,
                          whole_paper_reproduced=False))
    excluded_paper = next(row for row in papers if row['paper_id'] == 'P212')
    excluded = [u for u in excluded_paper['tools'] if u['tool_id'] == 'fusion']
    p.require(len(excluded) == 1, 'Changed excluded cohort observation')
    baseline_paper = next(row for row in papers if row['paper_id'] == 'P108')
    p.require(not any(u['tool_id'] == 'fusion' for u in baseline_paper['tools']), 'Changed baseline membership')
    return dict(schema_version=1, status='PASS_FULL250_FUSION_ROLES_WITH_AUTHOR_BASELINE_AND_COHORT_EXCLUSION',
                primary_census=p.pin((p.ROOT / 'docs/literature/fusion-source-development/CENSUS.json').read_bytes()),
                primary_XML_freshly_checked=250, roles=roles,
                excluded_observations=[dict(paper_id='P212', pmcid=excluded_paper['pmcid'],
                    reason=candidates['P212']['source_summary'], original_observation=excluded[0],
                    source_pointers=candidates['P212']['anchors'], positive_tool_use_credit=0)],
                additional_method_baselines=[dict(paper_id='P108', pmcid=baseline_paper['pmcid'],
                    source_pointers=candidates['P108']['anchors'], author_review=author,
                    positive_original_FUSION_tool_use_credit=0, original_upstream_execution_established=False)],
                other_homonyms=[candidates[k] for k in ['P110', 'P150']],
                author_code_executed=False, canonical_modified=False, whole_goal_complete=False)
