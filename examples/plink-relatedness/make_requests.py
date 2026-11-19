"""Write explicit PLINK-relatedness requests; never run or install software."""
from pathlib import Path
import argparse
import json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bfile", type=Path, required=True)
    parser.add_argument("--runtime-home", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=False)
    limits = dict(input_bytes=50_000_000, metadata_bytes=5_000_000,
                  working_bytes=268_435_456, disk_bytes=100_000_000,
                  output_bytes=50_000_000, files=100, samples=100_000,
                  variants=2_000_000, pair_variant_operations=1_000_000_000,
                  pair_batch_size=1024, line_bytes=16384, wall_seconds=600)
    operations = {
        "KING_TABLE": dict(counts=True, rel_check=False, kinship_filter=None),
        "KING_MATRIX": dict(precision="FLOAT64"),
        "KING_CUTOFF": dict(threshold=0.177, audit_retained_pairs=True),
        "GENOME": dict(rel_check=False, unbounded=False, nudge=False,
                       ppc_gap_kb=500, min_pi_hat=None, max_pi_hat=None,
                       frequency_mode="COHORT_FOUNDERS"),
    }
    for operation, settings in operations.items():
        request = dict(schema_version=1, mode="RUN", operation=operation,
                       bfile=str(args.bfile.resolve()), runtime_home=str(args.runtime_home.resolve()),
                       out=str(output / "captures" / operation), threads=2, memory_mib=640,
                       seed=20261119, settings=settings, limits=limits)
        (output / (operation + ".json")).write_text(
            json.dumps(request, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    replay = dict(schema_version=1, mode="REPLAY", analysis=str(output / "captures/KING_TABLE"),
                  out=str(output / "captures/KING_TABLE_REPLAY"),
                  runtime_home=str(args.runtime_home.resolve()), wall_seconds=600)
    (output / "REPLAY_TABLE.json").write_text(json.dumps(replay, indent=2) + "\n", encoding="utf-8")
    print("Requests written to " + str(output) + "; choose one operation to run.")


if __name__ == "__main__":
    main()
