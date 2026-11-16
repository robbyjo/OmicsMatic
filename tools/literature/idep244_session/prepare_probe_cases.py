#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create explicit synthetic zero-column controls from a retained CSV fixture."""
from pathlib import Path
import argparse
import csv
import io
import shutil
from Transport import pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--expression", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    a = p.parse_args()
    if a.out.exists():
        raise FileExistsError("Keep old fixtures and use a NEW directory")
    before = pin(a.expression)
    with a.expression.open(encoding="utf-8", newline="") as f:
        rows = list(csv.reader(f))
    if len(rows) != 201 or len(rows[0]) != 10 or any(len(r) != 10 for r in rows):
        raise ValueError("This declared source control requires the 200-gene / 9-sample original CSV")
    a.out.mkdir()
    shutil.copyfile(a.expression, a.out / "counts.csv")
    for name, columns in [("one-zero.csv", [9]), ("all-zero.csv", list(range(1, 10)))]:
        edited = [list(r) for r in rows]
        for row in edited[1:]:
            for column in columns:
                row[column] = "0"
        with (a.out / name).open("x", encoding="utf-8", newline="") as f:
            csv.writer(f, lineterminator="\n").writerows(edited)
    if pin(a.expression) != before:
        raise ValueError("Original input changed")
    save(a.out / "FIXTURES.json", dict(source=str(a.expression.resolve()), source_pin=before,
        source_unchanged=True, rows=200, samples=9,
        changes={"counts.csv": "Exact source bytes", "one-zero.csv": "Last sample counts explicitly replaced by zero",
                 "all-zero.csv": "All nine samples explicitly replaced by zero"},
        fixture_files={n: pin(a.out / n) for n in ["counts.csv", "one-zero.csv", "all-zero.csv"]},
        biological_validity_or_author_data_claim=False))


if __name__ == "__main__":
    main()
