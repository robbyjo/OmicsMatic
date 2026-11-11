# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Map source-reported FUMA uses to scoped commands, required inputs and explicit gaps."""
import json
from fuma_catalog import verify

CAPABILITIES={
 'loci':dict(command='fuma-tools --method loci',scope='Two-stage supplied-reference selection and complete interval-component loci; bounded reference bundles replay before admission.',inputs=['GWAS P/alleles','declared reference identity, MAF/LD and coverage','explicit thresholds, regions, ties and merge rule']),
 'reference':dict(command='fuma-tools --method prepare-reference / replay-reference',scope='Autosomal diploid hardcall MAF/LD, explicit founder/sample selection, marginal exclusions, windows and complete portable capture/replay.',inputs=['BED/BIM/FAM','selected variant/sample IDs','assembly/population/release','MAF/floor/window policies']),
 'mapping':dict(command='fuma-tools --method map-genes',scope='Supplied positional/eQTL/chromatin mapping, score/state/binary filtering and separate declaration/flag/link ledgers.',inputs=['selected candidate variants','gene annotation','chosen eQTL/interaction/score resources and explicit filters']),
 'annotation':dict(command='fuma-tools --method annotate-variants',scope='Supplied consequence/score/state/reference/LD postprocessing. Annotation prediction/acquisition is not performed.',inputs=['supplied variant annotations/consequences','explicit reference, release and missing-resource policies']),
 'gene-set':dict(command='fuma-tools --method gene-set',scope='GENE2FUNC supplied-annotation/GMT overrepresentation with explicit background, correction families and original multiplicity choices.',inputs=['query/background IDs','gene annotation','complete intended GMT families','correction/minimum-overlap/cutoff']),
 'expression-preparation':dict(command='fuma-tools --method prepare-expression',scope='Sample-level TPM/RPKM raw eligibility, cap50/log2(x+1), label means, sample-centered means and unweighted MAGMA Average.',inputs=['sample expression TSV/GCT1.2','explicit sample/label mapping and order','units and resource/namespace declarations']),
 'expression':dict(command='fuma-tools --method expression',scope='Select supplied/prepared exact-ID expression matrices and test independently supplied DEG memberships; no DEG generation or clustering.',inputs=['symbols/query/background','log2/norm resources','complete supplied DEG memberships']),
 'gene-fit':dict(command='magma-tools --method gene',scope='Separately managed original MAGMA1.10 gene fitting with explicit summary/raw model and input contracts.',inputs=['permitted managed MAGMA runtime','separate genome-wide GWAS/genotypes/N','gene annotation/build/reference/model']),
 'gene-window':dict(command='magma-tools --method annotate-native',scope='Native explicit strand-aware gene-window SNP mapping; no build inference or identifier conversion.',inputs=['SNP coordinates','gene coordinates/strands','declared build and upstream/downstream windows']),
 'gene-set-fit':dict(command='magma-tools --method sets / gsa',scope='Original MAGMA1.10 set fitting using explicit sealed gene results and complete supplied set/covariate families.',inputs=['managed runtime','sealed gene fit','supplied set memberships and model choices']),
 'property-fit':dict(command='magma-tools --method properties',scope='Original MAGMA1.10 gene-property fitting; explicit hidden Average conditioning and target/correction families.',inputs=['managed runtime','sealed gene fit','matching gene-ID expression properties','conditioning/direction/missingness choices']),
 'transcript-sites':dict(command='fuma-tools --method transcript-sites',scope='Explicit protein-coding TSS/TES variant mapping and P/window thresholds.',inputs=['variant P/coordinates','transcript sites/strands/biotypes','declared window and boundary policies']),
 'report':dict(command='fuma-tools --method magma-report',scope='Supplied gene/set result interpretation; explicit source Holm/Pbon, missingness and placeholders.',inputs=['fitted result tables','gene annotation','declared report correction/display choices']),
 'novelty':dict(command='fuma-tools --method locus-novelty',scope='Supplied-reference distance novelty with explicit lead coverage, identities and boundaries.',inputs=['lead variants','declared prior-association reference and coverage','distance/identity policy']),
 'known':dict(command='fuma-tools --method known-associations',scope='Explicit complete-family baseline association selection/union and retained contributor/group ledgers.',inputs=['baseline association fits','declared families, gene/trait keys, correction and selection rules']),
 'fisher':dict(command='fuma-tools --method fisher-enrichment',scope='Count/group Fisher testing with explicit orientation, planned families, overlap and correction policies.',inputs=['observed counts or sealed association groups','complete planned comparisons','correction, empty/missing and overlap choices']),
}

PAPER_METHODS={
 'P019':['gene-window','gene-fit','known','fisher'],
 'P072':['reference','loci','annotation','gene-fit','gene-set-fit','expression-preparation','property-fit','gene-set'],
 'P088':['reference','loci','annotation','gene-fit','gene-set-fit','gene-set'],
 'P105':['gene-window','gene-fit','gene-set-fit','expression-preparation','property-fit'],
 'P107':['gene-window','gene-fit','gene-set-fit','report'],
 'P123':['reference','loci','novelty'],
 'P130':['transcript-sites','gene-window','gene-fit','gene-set-fit'],
 'P131':['reference','loci','gene-window','gene-fit'],
 'P135':['annotation','mapping'],
 'P140':['reference','loci'],
 'P149':['gene-set'],
 'P150':['reference','loci','mapping','gene-window','gene-fit'],
 'P162':['reference','loci','gene-fit','gene-set-fit','expression-preparation','property-fit'],
 'P243':[],
}


def role_coverage(root,data):
    proof,source=verify(root,data);reviews=json.loads((root/'tools/literature/fuma-use-reviews.json').read_text(encoding='utf-8'))
    by_id={r['paper_id']:r for r in reviews};assert set(by_id)==set(PAPER_METHODS)
    output=[]
    for row in source['evidence']:
        paper=row['paper_id'];review=by_id[paper]['review'];methods=PAPER_METHODS[paper]
        output.append(dict(paper_id=paper,doi=row['doi'],pmcid=row['pmcid'],primary_paragraph=row['primary_paragraph'],source_XML_sha256=row['source_XML_sha256'],primary_paragraph_sha256=row['primary_paragraph_sha256'],reported_FUMA_release=row['reported_FUMA_release'],reviewed_use=review['use_summary'],source_scope_note=review['implementation_scope_note'],supporting_evidence=review['supporting_evidence'],command_scope='scoped-methods-available' if methods else 'source-method-unspecified',capability_ids=methods,source_settings_inferred=False,whole_paper_reproduced=False))
    return dict(status='passed-fourteen-FUMA-roles-with-scoped-native-availability',reviewed_roles=14,prior_usage_reviews_preserved=775,supporting_pointers_preserved=230,source_role_scope='Commands map the described method groups; they do not establish a paper-specific executable call. P243 does not enumerate a method, so no precise FUMA command is attributed.',capabilities=CAPABILITIES,papers=output,missing_functions=['automatic variant/eQTL/interaction acquisition or prediction','DEG generation and t-tests','heatmap clustering','unexposed hosted FUMA modules'],required_inputs_are_not_missing_methods=True,unreported_author_settings_are_not_defaults=True,historical_or_whole_paper_equivalence_required_for_scoped_availability=False,native_runtime='Java17+',optional_original_fit_runtime='managed unmodified MAGMA1.10 on its supported host; missing/forbidden runtime skips dependent fitting with recovery',historical_version_note='Reported MAGMA1.08 use remains distinct from the available1.10 adapter; no substitution or historical reproduction is inferred.',proof='../fuma-expression-preparation-2/VALIDATION.json',candidate=proof['artifact'],catalog_status='native-subset-validated',whole_goal_complete=False)
