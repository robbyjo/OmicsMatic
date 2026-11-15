#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare private controls from the pinned upstream synthetic test fixture."""
from pathlib import Path
import argparse
import copy
import csv
import hashlib
import json
import shutil
import sqlite3


def save(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def prepare(source, out):
    out.mkdir(parents=True, exist_ok=False)
    matches = list((source / "tests").rglob("counts_collinear.csv"))
    if len(matches) != 1:
        raise ValueError("Exact original synthetic counts_collinear.csv is required")
    counts = out / "counts.csv"
    shutil.copyfile(matches[0], counts)
    with counts.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.reader(stream))
    if len(rows) != 201 or len(rows[0]) != 10:
        raise ValueError("Expected original 200-gene9-sample synthetic fixture")
    design = out / "design.csv"
    with design.open("w", encoding="utf-8", newline="") as stream:
        w = csv.writer(stream, lineterminator="\n")
        w.writerow(["Factor", *rows[0][1:]])
        w.writerow(["Condition", *["A"]*3, *["B"]*3, *["C"]*3])
        w.writerow(["Batch", *["One"]*3, *["Two"]*3, *["Three"]*3])
    reference = out / "synthetic-reference"
    (reference / "demo").mkdir(parents=True)
    (reference / "db").mkdir()
    shutil.copyfile(counts, reference / "demo/demo-counts.csv")
    (reference / "demo/demo_data_info.csv").write_text(
        "ID,expression,design,type,name\n1,demo-counts.csv,,1,Synthetic control only\n", encoding="utf-8", newline="\n")
    with sqlite3.connect(reference / "demo/orgInfo.db") as db:
        db.execute("CREATE TABLE orgInfo (id INTEGER, file TEXT, name TEXT, top INTEGER, ensembl_dataset TEXT)")
        db.execute("INSERT INTO orgInfo VALUES (244, 'synthetic.db', 'Synthetic control species', 1, 'synthetic_not_a_biological_reference')")
    with sqlite3.connect(reference / "db/synthetic.db") as db:
        db.execute("CREATE TABLE mapping (id TEXT, ens TEXT, idType INTEGER)")
        # First20 input genes collapse in pairs; last5 remain unmapped. An
        # extra one-to-many row tests original first-row retention unchanged.
        mappings = [(row[0].upper(), "SYNTH_ENSEMBL_%04d" % (i//2 if i < 20 else i), 1)
                    for i, row in enumerate(rows[1:196])]
        db.executemany("INSERT INTO mapping VALUES (?,?,?)", mappings)
        db.execute("INSERT INTO mapping VALUES (?, ?, 1)", (rows[1][0].upper(), "SYNTH_SECOND_MAPPING"))
    base = dict(schema=1, profile="original-idep244-r461-windows",
                operation="counts-differential-expression", expression=str(counts.resolve()), design=None,
                load=dict(max_group_name_length=30),
                mapping=dict(mode="disabled", multiple_map="max_sd"),
                preprocess=dict(missing_value="geneMedian", min_counts=0.5,
                    n_min_samples_count=1, counts_transform=1, counts_log_start=4),
                analysis=dict(fdr=0.05, min_fold_change=2, comparisons=None, factors=None,
                    interactions=None, block_factors=None, reference_levels=None,
                    threshold_wald_test=False, independent_filtering=True),
                rng=dict(kind=["Mersenne-Twister", "Inversion", "Rejection"], seed=24405),
                locale="English_United States.utf8",
                limits=dict(input_bytes=128*1024*1024, bundle_bytes=256*1024*1024,
                    files=20000, wall_seconds=180, vector_heap_mib=4096))
    requests = {"disabled": copy.deepcopy(base)}
    mapped = copy.deepcopy(base)
    mapped["mapping"] = dict(mode="database", multiple_map="max_sd",
                            reference_dir=str(reference.resolve()), organism_id="244", max_sample_ids=100)
    requests["mapped"] = mapped
    designed = copy.deepcopy(base)
    designed["design"] = str(design.resolve())
    designed["analysis"].update(factors=["Condition"], comparisons=["Condition: B vs. A", "Condition: C vs. A"],
                                reference_levels=["Condition:A"])
    requests["designed"] = designed
    collinear = copy.deepcopy(designed)
    collinear["analysis"]["block_factors"] = ["Batch"]
    requests["original-rank-error"] = collinear
    no_reference = copy.deepcopy(mapped)
    no_reference["mapping"]["organism_id"] = "245"
    requests["missing-reference"] = no_reference
    zero = out / "all-zero.csv"
    with zero.open("w", encoding="utf-8", newline="") as stream:
        w = csv.writer(stream, lineterminator="\n")
        w.writerow(rows[0])
        for row in rows[1:]:
            w.writerow([row[0], *[0]*9])
    invalid = copy.deepcopy(base)
    invalid["expression"] = str(zero.resolve())
    requests["original-no-counts"] = invalid
    for name, request in requests.items():
        save(out / (name + ".json"), request)
    save(out / "CASES.json", dict(status="PREPARED_PRIVATE_ORIGINAL_SYNTHETIC_INPUTS_NOT_ANALYSIS",
        cases=list(requests), original_fixture_sha256=hashlib.sha256(counts.read_bytes()).hexdigest(),
        source=str(matches[0]), synthetic_reference_only=True,
        original_fixture_not_relicensed_as_project_code=True, catalog_credit_added=0))
    return list(requests)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    a = p.parse_args()
    print(prepare(a.source, a.out))
