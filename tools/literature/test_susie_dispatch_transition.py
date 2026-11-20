"""Reject unreviewed dispatcher/native changes while reconstructing exact preceding bytes."""
from pathlib import Path
import unittest
from susie_dispatch_transition import BEFORE,DISPATCH,ROUTE,HELP,ANCHOR,HELP_ANCHOR,dispatcher_before,pin
from scanpy_dispatch_transition import dispatcher_before as scanpy_before

ROOT=Path(__file__).resolve().parents[2]


class SusieDispatcherTransitionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.after=(ROOT/DISPATCH).read_bytes()

    def test_actual_delta_is_exactly_three_additive_lines(self):
        before=dispatcher_before(self.after);self.assertEqual(pin(before),BEFORE)
        self.assertEqual(self.after,before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1))
        self.assertEqual(len(self.after.splitlines())-len(before.splitlines()),3)

    def test_preceding_scanpy_inverse_still_accepts_the_exact_reconstructed_source(self):
        before=dispatcher_before(self.after);self.assertEqual(len(scanpy_before(before)),54455)

    def test_missing_route_or_help_is_rejected(self):
        for block in [ROUTE,HELP]:
            with self.subTest(block=block),self.assertRaises(ValueError):dispatcher_before(self.after.replace(block,b'',1))

    def test_duplicate_route_or_help_is_rejected(self):
        for block in [ROUTE,HELP]:
            with self.subTest(block=block),self.assertRaises(ValueError):dispatcher_before(self.after.replace(block,block+block,1))

    def test_relocated_route_is_rejected_even_with_unchanged_predecessor(self):
        before=dispatcher_before(self.after)
        changed=before.replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1).replace(ANCHOR,ANCHOR+ROUTE,1)
        with self.assertRaises(ValueError):dispatcher_before(changed)

    def test_native_susie_routing_cannot_be_renamed(self):
        changed=self.after.replace(b'arguments[0].equals("susie")',b'arguments[0].equals("susie-renamed")',1)
        self.assertNotEqual(changed,self.after)
        with self.assertRaises(ValueError):dispatcher_before(changed)

    def test_old_native_target_cannot_be_replaced(self):
        changed=self.after.replace(b'return FineMappingCli.run(',b'return RegressionCli.run(',1)
        self.assertNotEqual(changed,self.after)
        with self.assertRaises(ValueError):dispatcher_before(changed)

    def test_new_route_must_call_the_actual_original_workflow(self):
        changed=self.after.replace(b'return SusieWorkflowCli.run(',b'return ScanpyWorkflowCli.run(',1)
        with self.assertRaises(ValueError):dispatcher_before(changed)


if __name__=='__main__':unittest.main()
