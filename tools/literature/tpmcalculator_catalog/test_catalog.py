# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import copy,json,re,sys,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from tpmcalculator_catalog.model import *
from tpmcalculator_catalog.catalog import qualification,load_before,load_catalog_registry,compatibility
from tpmcalculator_catalog.source import audit,PARAGRAPH_SHA
from catalog_source_integrity import reverse_delta
from hicat109_catalog.bridge import metadata_view

class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {name:(ROOT/name).read_bytes() for name in META}
        cls.after=transition(ROOT,cls.before)

    def test_one_native_credit_one_new_missing_family_and_two_reviews(self):
        result=check_transition(ROOT,self.before,self.after)
        self.assertEqual(tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','unreviewed_uses','new_scoped_integrations','missing']),(250,446,2690,1197,1493,122,265))
        counts=json.loads(self.after[META[1]])['implementation_status_counts']
        self.assertEqual((counts['native-subset-validated'],counts['upstream-adapter-validated']),(21,101))

    def test_all_prior_reviews_and_unrelated_paper_facts_are_preserved(self):
        old,new=json.loads(self.before[REVIEWS])['reviews'],json.loads(self.after[REVIEWS])['reviews']
        self.assertEqual(new[:len(old)],old);self.assertEqual(len(new)-len(old),2)
        changed=set()
        for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])):
            self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'})
            self.assertEqual([t for t in a['tools'] if t['tool_id']!='tpmcalculator'],[t for t in b['tools'] if t['tool_id'] not in {'tpmcalculator','idep'}])
            if a!=b:changed.add(a['paper_id'])
        self.assertEqual(changed,{'P050'})
        self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='tpmcalculator'],[r for r in json.loads(self.after[META[0]]) if r['id'] not in {'tpmcalculator','idep'}])

    def test_source_unknowns_literal_interface_and_separate_upstream_steps(self):
        rows={r['tool_id']:r for r in json.loads(self.after[REVIEWS])['reviews'][-2:]}
        tpm,idep=rows['tpmcalculator'],rows['idep']
        self.assertIsNone(tpm['version_reported_near_name'])
        for key in ['TPMCalculator_version','TPMCalculator_options','exact_GTF','output_selection']:self.assertIsNone(tpm['reported_settings'][key])
        self.assertEqual(tpm['reported_settings']['upstream_alignment_options_literal'],'subread-align -d 50 -D 600–sortReadsByCoordinates')
        self.assertTrue(tpm['reported_settings']['library_strandedness_is_not_a_TPMCalculator_option'])
        self.assertEqual(idep['version_reported_near_name'],'iDEP.96')
        self.assertEqual(idep['reported_settings']['settings_literal'],'default settings')
        for key in ['normalized_release','deployed_source_commit','DESeq2_version','complete_effective_defaults']:self.assertIsNone(idep['reported_settings'][key])
        self.assertTrue(idep['reported_settings']['transcript_TPMCalculator_step_is_separate'])
        self.assertEqual({r['evidence_paragraph_sha256'] for r in rows.values()},{PARAGRAPH_SHA})

    def test_complete_primary_census_and_registry(self):
        value=audit(ROOT);self.assertEqual(value['papers'],250)
        for tool in ['tpmcalculator','idep']:
            found={p['paper_id']:[a['paragraph_index'] for a in p['anchors'][tool]] for p in value['census'] if p['anchors'][tool]}
            self.assertEqual(found,{'P050':[45]})
        rows=load_catalog_registry(ROOT);self.assertEqual(len(rows),485)
        self.assertEqual(len({r['id'] for r in rows}),485)
        idep=next(r for r in rows if r['id']=='idep')
        self.assertEqual(idep['current_coverage'],'missing');self.assertIsNone(idep['existing_source'])
        self.assertIsNotNone(re.search(idep['pattern'],'the iDEP.96 web interface',re.I))

    def test_complete_payload_readback_does_not_claim_new_experiments_or_author_settings(self):
        result=qualification(ROOT)
        self.assertTrue(result['full_public_record_payloads_read_back'])
        self.assertEqual((result['actual_focused_tests'],result['actual_original_cases_per_host'],result['actual_public_failure_controls_per_host']),(141,64,9))
        self.assertEqual(result['cross_host_replays_each_direction'],5)
        self.assertFalse(result['fresh_native_calls_repeated_by_catalog_gate'])
        self.assertFalse(result['author_version_or_options_established'])

    def test_exact_review_byte_inverse_and_scientific_overlays_forbidden(self):
        inverse=compatibility(self.before,self.after)['paths'][0]
        self.assertEqual(reverse_delta(self.after[REVIEWS],inverse),self.before[REVIEWS])
        with self.assertRaises(ValueError):metadata_view(ROOT,{**self.before,PROGRAM:b'forbidden science overlay'})

    def test_credit_and_author_inferences_cannot_be_invented(self):
        for tool,key,value in [('idep','implementation_status','native-subset-validated'),('tpmcalculator','author_version_established',True),('tpmcalculator','original006_binary_executed',True),('tpmcalculator','root_dispatch_available',True)]:
            rows=json.loads(self.after[META[0]]);next(r for r in rows if r['id']==tool)[key]=value
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:encoded(rows)})
        document=json.loads(self.after[REVIEWS]);document['reviews'][-1]['reported_settings']['normalized_release']='0.96'
        with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,REVIEWS:encoded(document)})

    def test_window_exclusions_summary_and_embedded_site_are_consistent(self):
        old,new=json.loads(self.before[META[1]]),json.loads(self.after[META[1]])
        for key in ['window_start','window_end','window_extended','journals','years','user_excluded_tools','dependency_policy','goal_status']:
            self.assertEqual(old[key],new[key])
        page=self.after[META[4]].decode();embedded=json.loads(re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S)[1])
        self.assertEqual(embedded,dict(papers=json.loads(self.after[PAPERS]),tools=json.loads(self.after[META[0]]),summary=new))
        for key in [META[4],META[5]]:
            text=self.after[key].decode();self.assertEqual(text.count('id="tpmcalculator-required-actions"'),1)
            for word in ['SKIP3','FAIL2','COMPLETED.json','worker/stdout.bin','iDEP.96','1,493','122 scoped']:self.assertIn(word,text)

if __name__=='__main__':unittest.main()
