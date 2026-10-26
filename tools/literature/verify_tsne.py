"""Freeze independent exact t-SNE affinity, objective and gradient references.

Uses installed scikit-learn/SciPy; neither dependency is required by Java tests
or by the native implementation. No reference optimizer coordinates are asserted.
"""
import hashlib
import inspect
import json
from pathlib import Path

import numpy as np
import scipy
from scipy.spatial.distance import pdist, squareform
import sklearn
from sklearn.manifold import _t_sne

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/test/resources/tsne"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(3807)
    x = rng.normal(size=(12, 4)) * np.array([1, 2, 0.5, 3])
    y = rng.normal(size=(12, 2))
    perplexity = 4.5
    p = _t_sne._joint_probabilities(squareform(pdist(x, "sqeuclidean")), perplexity, 0)
    kl, gradient = _t_sne._kl_divergence(y.ravel(), p, 1, len(x), 2)
    arrays = {"observations.tsv": x, "probabilities.tsv": squareform(p),
              "coordinates.tsv": y, "gradient.tsv": gradient.reshape(-1, 2)}
    for name, array in arrays.items():
        np.savetxt(OUT / name, array, delimiter="\t", fmt="%.17g")
    module = Path(inspect.getfile(_t_sne))
    report = {"reference": "scikit-learn exact _joint_probabilities and _kl_divergence",
              "primary_source": "https://github.com/scikit-learn/scikit-learn/blob/1.8.0/sklearn/manifold/_t_sne.py",
              "method_source": "https://www.jmlr.org/papers/v9/vandermaaten08a.html",
              "sklearn": sklearn.__version__, "numpy": np.__version__, "scipy": scipy.__version__,
              "module_sha256": hashlib.sha256(module.read_bytes()).hexdigest(),
              "perplexity": perplexity, "kl_divergence": float(kl),
              "affinity_tolerance": 2e-6, "objective_gradient_tolerance": 2e-12,
              "scope": "Frozen exact affinities and analytical objective/gradient at supplied coordinates; no optimizer-coordinate parity claim",
              "files": {name: hashlib.sha256((OUT / name).read_bytes()).hexdigest() for name in arrays}}
    (OUT / "provenance.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
