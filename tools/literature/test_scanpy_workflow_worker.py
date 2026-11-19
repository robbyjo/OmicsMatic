#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real worker intake/semantic refusals; run with the exact permitted profile Python.

This suite imports the worker and installed original packages. It is not a
substitute for original-model/reference or Java process/cross-host qualification.
"""
from pathlib import Path
import importlib.util
import json
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
RESOURCE = ROOT/"src/main/resources/org/jlinalg/omics/scanpy-workflow"
spec = importlib.util.spec_from_file_location("omics_scanpy_worker", RESOURCE/"worker.py")
w = importlib.util.module_from_spec(spec);spec.loader.exec_module(w)


class WorkerControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.runtime = w.runtime(json.loads((RESOURCE/"SOURCE-PROFILE.json").read_bytes()))

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="scanpy-worker-control-")
        self.addCleanup(self.directory.cleanup);self.root = Path(self.directory.name)
        self.limits = dict(maximum_cells=1000, maximum_features=1000, maximum_entries=10000,
                           maximum_dense_entries=10000, maximum_work=1000000, maximum_working_bytes=10000000)

    def matrix(self):
        return w.ad.AnnData(w.np.array([[1., 2.], [3., 4.], [2., 1.]]),
                            obs=w.pd.DataFrame(index=["001", "002", "003"]),var=w.pd.DataFrame(index=["gene-A", "gene-B"]))

    def test_all_random_operations_require_a_literal_seed(self):
        for operation in w.RANDOM:
            with self.subTest(operation=operation):
                with self.assertRaises(ValueError):w.validate_step(dict(operation=operation,params={}))
                with self.assertRaises(ValueError):w.validate_step(dict(operation=operation,params=dict(rng=True)))

    def test_no_historical_spelling_or_algorithm_repair(self):
        for operation in ["sc.ppl.log1p", "pp.harmony_integrate", "rank_gene_group", "kmeans"]:
            with self.subTest(operation=operation):
                with self.assertRaises(ValueError):w.validate_step(dict(operation=operation))

    def test_unknown_parameter_is_rejected_before_source_call(self):
        with self.assertRaises(ValueError):w.validate_step(dict(operation="pp.log1p",params=dict(fake_option=True)))

    def test_marker_defaults_are_not_inferred_from_a_method_mention(self):
        with self.assertRaises(ValueError):w.validate_step(dict(operation="tl.rank_genes_groups",params=dict(groupby="celltype")))

    def test_hvg_flavor_and_input_declaration_are_required(self):
        with self.assertRaises(ValueError):w.validate_step(dict(operation="pp.highly_variable_genes",params=dict(n_top_genes=2000)))
        with self.assertRaises(ValueError):w.validate_step(dict(operation="pp.highly_variable_genes",params=dict(flavor="seurat")))

    def test_forceatlas_does_not_silently_switch_to_fruchterman_reingold(self):
        with self.assertRaises(ValueError):w.validate_step(dict(operation="tl.draw_graph",params=dict(rng=1,layout="fa")))

    def test_filter_has_exactly_one_non_null_criterion(self):
        with self.assertRaises(ValueError):w.validate_step(dict(operation="pp.filter_cells",params=dict(min_counts=1,min_genes=1)))
        w.validate_step(dict(operation="pp.filter_cells",params=dict(min_counts=1,max_counts=None)))

    def test_numeric_looking_ids_are_preserved(self):
        path=self.root/"cells";path.write_text("cell\tgroup\n001\t001\n002\t002\n")
        frame=w.typed_frame(path,"cell",dict(group=dict(levels=["001","002"],ordered=True)),10)
        self.assertEqual(list(frame.index),["001","002"]);self.assertEqual(list(frame["group"].cat.categories),["001","002"])

    def test_unknown_category_is_not_converted_to_missing(self):
        path=self.root/"cells";path.write_text("cell\tgroup\n001\tunknown\n")
        with self.assertRaises(ValueError):w.typed_frame(path,"cell",dict(group=dict(levels=["known"])),10)

    def test_duplicate_market_coordinates_are_not_summed(self):
        path=self.root/"matrix";text="%%MatrixMarket matrix coordinate real general\n2 2 2\n1 1 2\n1 1 3\n";path.write_text(text)
        with self.assertRaises(ValueError):w.read_market(path,(2,2),self.limits)
        self.assertEqual(path.read_text(),text)

    def test_valid_unsorted_neighbor_coordinates_remain_unsorted(self):
        value=w.sp.csr_matrix((w.np.array([2.,1.,3.]),w.np.array([1,0,0]),w.np.array([0,2,3])),shape=(2,2))
        before=(value.data.copy(),value.indices.copy(),value.indptr.copy());self.assertFalse(value.has_sorted_indices)
        w.numeric_check(value,"neighbors",self.limits)
        for original,after in zip(before,[value.data,value.indices,value.indptr]):w.np.testing.assert_array_equal(original,after)

    def test_sparse_duplicates_are_refused_without_sort_or_sum(self):
        value=w.sp.csr_matrix((w.np.array([2.,1.]),w.np.array([0,0]),w.np.array([0,2,2])),shape=(2,2))
        with self.assertRaises(ValueError):w.numeric_check(value,"duplicate",self.limits)
        self.assertEqual(value.data.tolist(),[2.,1.])

    def test_and_exclusion_is_not_replaced_with_or_exclusion(self):
        frame=w.pd.DataFrame(dict(genes=[200,200,500,500],umi=[300,700,300,700]))
        leaves=[dict(field="genes",operator="lt",value=300),dict(field="umi",operator="lt",value=500)]
        self.assertEqual(w.predicate(frame,{"not":{"all":leaves}}).tolist(),[False,True,True,True])
        self.assertEqual(w.predicate(frame,{"not":{"any":leaves}}).tolist(),[False,False,False,True])

    def test_literal_inequality_boundary_is_preserved(self):
        frame=w.pd.DataFrame(dict(value=[1.,2.,3.]))
        self.assertEqual(w.predicate(frame,dict(field="value",operator="lt",value=2.)).tolist(),[True,False,False])
        self.assertEqual(w.predicate(frame,dict(field="value",operator="le",value=2.)).tolist(),[True,True,False])

    def test_missing_predicate_values_do_not_silently_become_false(self):
        frame=w.pd.DataFrame(dict(value=[1.,float("nan")]))
        with self.assertRaises(ValueError):w.predicate(frame,dict(field="value",operator="gt",value=0))

    def test_finite_matrix_does_not_hide_undefined_statistics(self):
        data=self.matrix();data.var["undefined_std"]=[1.,float("nan")]
        with self.assertRaises(ArithmeticError):w.check_data(data,self.limits)

    def test_capacity_is_checked_before_densification(self):
        data=self.matrix();before=data.X.copy();self.limits["maximum_dense_entries"]=2
        with self.assertRaises(ValueError):w.run_step(data,dict(operation="pp.scale",params={}),self.limits)
        w.np.testing.assert_array_equal(before,data.X)

    def test_raw_and_layer_overwrites_are_refused(self):
        data=self.matrix();data.layers["counts"]=data.X.copy()
        with self.assertRaises(ValueError):w.run_step(data,dict(operation="copy_layer",params=dict(name="counts")),self.limits)
        data.raw=data.copy()
        with self.assertRaises(ValueError):w.run_step(data,dict(operation="snapshot_raw",params={}),self.limits)

    def test_h5ad_preserves_declared_storage_and_dtype(self):
        data=self.matrix();data.X=data.X.astype("float32");path=self.root/"matrix";data.write_h5ad(path)
        restored=w.read_input(self.root,dict(input=dict(format="h5ad",matrix="matrix"),limits=self.limits))
        self.assertEqual(str(restored.X.dtype),"float32");w.np.testing.assert_array_equal(data.X,restored.X)

    def test_h5ad_external_links_do_not_escape_the_captured_input(self):
        path=self.root/"matrix"
        with w.h5py.File(path,"w") as handle:handle["X"]=w.h5py.ExternalLink("not-captured.h5ad","/X")
        with self.assertRaisesRegex(ValueError,"self-contained"):
            w.read_input(self.root,dict(input=dict(format="h5ad",matrix="matrix"),limits=self.limits))

    def test_h5ad_null_parameter_remains_none_without_fabricating_values(self):
        data=self.matrix();data.uns['unset_source_parameter']=None;path=self.root/'matrix';data.write_h5ad(path)
        restored=w.read_input(self.root,dict(input=dict(format='h5ad',matrix='matrix'),limits=self.limits))
        self.assertIsNone(restored.uns['unset_source_parameter']);w.np.testing.assert_array_equal(data.X,restored.X)


if __name__ == "__main__":unittest.main(verbosity=2)
