"""Current command discovery cannot create science, source-review or family credit."""
from pathlib import Path
import json
import re
import subprocess
import unittest
import susie_dispatch_catalog as model


class SusieDispatchCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',model.BASE+':'+n],cwd=model.ROOT) for n in model.META}
        cls.after=model.transition(cls.before)

    def test_counts_and_all_paper_review_summary_bytes_are_unchanged(self):
        result=model.check(self.before,self.after)
        self.assertEqual((result['integrations'],result['reviewed_uses'],result['pending_uses']),(138,1503,1201))
        self.assertEqual((result['new_family_credit'],result['new_review_records']),(0,0))
        for n in [model.META[0],model.META[2],model.META[5]]:self.assertEqual(self.before[n],self.after[n])

    def test_other_tools_and_retained_susie_scope_fields_are_exact(self):
        a=json.loads(self.before[model.META[1]]);b=json.loads(self.after[model.META[1]])
        self.assertEqual([r for r in a if r['id']!='susie'],[r for r in b if r['id']!='susie'])
        x,y=next(r for r in a if r['id']=='susie'),next(r for r in b if r['id']=='susie')
        self.assertEqual({k:v for k,v in x.items() if k not in model.CHANGED_FIELDS},
                         {k:v for k,v in y.items() if k not in model.CHANGED_FIELDS})

    def test_both_global_and_standalone_entrypoints_are_available(self):
        row=next(r for r in json.loads(self.after[model.META[1]]) if r['id']=='susie')
        self.assertTrue(row['global_CLI_dispatch_available']);self.assertEqual(len(row['qualified_component_entrypoints']),4)
        self.assertEqual(row['qualified_component_entrypoints'][:2],['susie-tools --request RUN/VERIFY/REPLAY','susie-tools --verify'])
        self.assertIn('SusieWorkflowCli',row['qualified_component_entrypoints'][2])
        self.assertTrue(row['source_build_required']);self.assertFalse(row['released_binary_contains_workflow_adapter'])

    def test_source_roles_and_every_declared_gap_are_unchanged(self):
        old=next(r for r in json.loads(self.before[model.META[1]]) if r['id']=='susie')
        new=next(r for r in json.loads(self.after[model.META[1]]) if r['id']=='susie')
        self.assertEqual(old['paper_role_qualification'],new['paper_role_qualification']);self.assertEqual(new['source_roles_reviewed'],20)
        for r in new['paper_role_qualification'].values():
            self.assertFalse(r['whole_paper_reproduced']);self.assertFalse(r['author_runtime_executed'])
            self.assertFalse(r['historical_package_parity']);self.assertFalse(r['full_reported_role_qualified'])

    def test_original_owner_and_scientific_source_are_checked_without_overlay(self):
        report=model.retained_catalog(self.before)
        self.assertFalse(report['source_lookup_overlay']);self.assertFalse(report['old_fingerprints_refreshed'])
        self.assertFalse(report['original_owner_executed_here']);self.assertFalse(report['scientific_models_run'])

    def test_unknown_or_already_changed_before_bytes_are_refused(self):
        before=dict(self.before);rows=json.loads(before[model.META[1]])
        next(r for r in rows if r['id']=='susie')['global_CLI_dispatch_available']=True
        before[model.META[1]]=model.encoded(rows)
        with self.assertRaises(ValueError):model.transition(before)

    def test_unrelated_or_scientific_inventory_edits_are_refused(self):
        for target in ['other','science']:
            after=dict(self.after);rows=json.loads(after[model.META[1]])
            if target=='other':rows[0]['name']='changed'
            else:next(r for r in rows if r['id']=='susie')['whole_tool_qualified']=True
            after[model.META[1]]=model.encoded(rows)
            with self.subTest(target=target),self.assertRaises(ValueError):model.check(self.before,after)

    def test_review_or_summary_credit_cannot_be_added(self):
        for n in [model.META[2],model.META[5]]:
            after=dict(self.after);value=json.loads(after[n])
            if n==model.META[2]:value['paper_tool_observations']+=1
            else:value['reviews'].append(dict(value['reviews'][0]))
            after[n]=model.encoded(value)
            with self.subTest(name=n),self.assertRaises(ValueError):model.check(self.before,after)

    def test_embedded_html_data_matches_all_actual_metadata(self):
        m=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',self.after[model.META[6]].decode(),re.S)
        self.assertEqual(json.loads(m[1]),dict(papers=json.loads(self.after[model.META[0]]),tools=json.loads(self.after[model.META[1]]),summary=json.loads(self.after[model.META[2]])))

    def test_current_notice_is_prominent_and_has_no_unescaped_placeholder(self):
        for n in model.META[6:]:
            text=self.after[n].decode();self.assertLess(text.index('id="susie-global-command-notice"'),text.index('id="susie-catalog-notice"'))
            self.assertIn('Warnings and required actions:',text)
        self.assertNotIn('<current-source-built-jar>',model.NOTICE)
        self.assertIn('0.11.43',model.NOTICE);self.assertIn('SKIP3',model.NOTICE);self.assertIn('FAIL2',model.NOTICE)


if __name__=='__main__':unittest.main()
