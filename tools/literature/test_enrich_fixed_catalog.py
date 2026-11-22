"""Historical extraction events survive registry expansions without hiding changes."""
import unittest
from enrich_fixed_catalog import preserve_selection_history

class SelectionHistoryTest(unittest.TestCase):
    def test_unchanged_primary_retains_its_original_selection_but_keeps_new_review(self):
        old={'tools':[dict(tool_id='example',evidence_paragraph_index=4,evidence_paragraph_sha256='abc',evidence_selection='highest-scored-eligible-method-sentence',review_status='candidate')]}
        new={'tools':[dict(tool_id='example',evidence_paragraph_index=4,evidence_paragraph_sha256='abc',evidence_selection='individually-reviewed-paragraph',review_status='reviewed',use_summary='Corrected review')]}
        preserve_selection_history(old,new)
        self.assertEqual('highest-scored-eligible-method-sentence',new['tools'][0]['evidence_selection'])
        self.assertEqual('reviewed',new['tools'][0]['review_status']);self.assertEqual('Corrected review',new['tools'][0]['use_summary'])

    def test_moved_primary_new_tools_and_missing_history_keep_new_selection(self):
        old={'tools':[dict(tool_id='example',evidence_paragraph_index=4,evidence_paragraph_sha256='abc',evidence_selection='old')]}
        for field,value in [('evidence_paragraph_index',5),('evidence_paragraph_sha256','different'),('tool_id','new')]:
            tool=dict(tool_id='example',evidence_paragraph_index=4,evidence_paragraph_sha256='abc',evidence_selection='new');tool[field]=value
            preserve_selection_history(old,{'tools':[tool]});self.assertEqual('new',tool['evidence_selection'])
        tool=dict(tool_id='example',evidence_paragraph_index=4,evidence_paragraph_sha256='abc',evidence_selection='new')
        preserve_selection_history({'tools':[dict(tool_id='example')]},{'tools':[tool]});self.assertEqual('new',tool['evidence_selection'])

if __name__=='__main__':unittest.main()
