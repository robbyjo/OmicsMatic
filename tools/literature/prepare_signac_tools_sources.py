# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare source/notice resources and the explicit Signac workflow discovery index."""
import shutil
from setup_deseq2_reference import ROOT,pin,save


def main():
    base=ROOT/'build/literature-review/signac-runtime';out=ROOT/'src/main/resources/org/jlinalg/omics/signac-normalize'
    index=ROOT/'src/main/resources/org/jlinalg/omics/signac-tools';assert not out.exists() and not index.exists();out.mkdir();index.mkdir()
    for p in [out,index]:(p/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n')
    versions=['1.6.0','1.7.0','1.9.0','1.14.0','1.17.1']
    for v in versions:shutil.copyfile(base/('source-intake-1/'+v+'/source/Signac/R/preprocessing.R'),out/('preprocessing-'+v+'.R'))
    shutil.copyfile(ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi/MIT-LICENSE.txt',out/'MIT-LICENSE.txt')
    save(out/'SOURCE-PROFILES.json',dict(schema_version=1,profiles=['Signac-'+v for v in versions],
         scope='Native retained RunTFIDF methods1-4 and FindTopFeatures metadata; explicit selection uses the current1.17.1 assay-wrapper contract. Full historical R/Seurat assay wrappers are not executed.',
         feature_selection_contract='1.17.1-package-assay-wrapper',
         native_validation_parent=pin(ROOT/'docs/literature/signac-normalization-development/VALIDATION.json'),
         files={p.name:pin(p) for p in out.iterdir() if p.is_file() and p.name!='.gitattributes'},execution_authentication=False))
    workflows=[
      ('normalize','NORMALIZE / TOP_FEATURES / VERIFY','Java','SignacNormalizationCli','examples/signac-normalize/README.md'),
      ('lsi','FIT / VERIFY','declared R/Signac/SVD dependencies','SignacLsiRequestCli','examples/signac-lsi/README.md'),
      ('fragments','FEATURE_MATRIX / COUNT_FRAGMENTS / NUCLEOSOME_SIGNAL / BINARIZE_CSC / VERIFY','Java','SignacFragmentsRequestCli','examples/signac-fragments/README.md'),
      ('annotations','TSS_FAST / TSS_PROFILE / GENE_ACTIVITY / VERIFY','Java','SignacAnnotationsRequestCli','examples/signac-annotations/README.md'),
      ('motifs','ENRICH / VERIFY','Java; explicit supplied motif/query/background populations','SignacMotifsRequestCli','examples/signac-motifs/README.md'),
      ('scan','FIT / VERIFY','declared R/motifmatchr/reference/motif dependencies','SignacScanRequestCli','examples/signac-scan/README.md'),
      ('links','FIT / VERIFY','declared R/Signac background dependencies','SignacLinksRequestCli','examples/signac-links/README.md'),
      ('markers','PAIR / ALL / VERIFY','declared current Seurat/R statistical profile','SeuratDifferentialCli','examples/seurat-differential/README.md'),
      ('peaks','POOLED / GROUPED / VERIFY','Linux Java + exact private MACS/Python for calling; Java-only VERIFY','SignacPeaksCli','examples/signac-peaks/README.md'),
      ('coverage','COVERAGE / VERIFY','full Java desktop/font/PNG runtime','SignacCoverageCli','examples/signac-coverage/README.md'),
      ('chromvar','BACKGROUND / DEVIATIONS / VERIFY','existing chromvar-tools contracts; R background or native deviations','ChromVarToolsCli','examples/chromvar-background/README.md')]
    save(index/'WORKFLOWS.json',dict(schema_version=1,engine='scoped-Signac-workflow-router',
         workflows=[dict(name=n,modes=m,requirements=d,delegate='org.jlinalg.cli.'+c,example=e) for n,m,d,c,e in workflows],
         explicit_workflow_for_VERIFY=True,reinterprets_request_paths_or_scientific_settings=False,analysis_installs_software=False,
         missing_or_forbidden_requirements='SKIP3: skip dependent analysis; provide the permitted exact profile where allowed, then retry into NEW outputs.',
         failures='FAIL2: no result admitted, including partial files; preserve attempts and correct the named cause before NEW outputs.',
         historical_whole_packages=False,whole_paper_reproduction=False,execution_authentication=False,
         duplicate_chromVAR_implementation_credit=False))
    print('Prepared native normalization sources and11 explicit Signac workflow routes')


if __name__=='__main__':main()
