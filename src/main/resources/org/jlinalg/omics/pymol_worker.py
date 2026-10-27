"""Trusted typed PyMOL operations. No user commands, Python, PML or sessions.

Invoked only after Java admits the pinned private runtime. Coordinates are Angstrom.
Selections use exact atom fields in a single explicitly chosen source state.
"""
import hashlib
import json
import math
from pathlib import Path
import sys
import traceback

sys.path.insert(0, sys.argv[1])
import pymol2


def obj(value, allowed, required=()):
    if not isinstance(value, dict) or set(value) - set(allowed) or set(required) - set(value):
        raise ValueError("Unknown/missing object fields: " + repr(value)[:300])
    return value


def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("Integer outside declared bound")
    return value


def number(value, low=-1e9, high=1e9):
    if type(value) not in (int, float) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError("Nonfinite/out-of-bound number")
    return value


def text(value, maximum=256):
    if not isinstance(value, str) or len(value) > maximum or any(ord(c) < 32 for c in value):
        raise ValueError("Invalid text field")
    return value


def identifier(value):
    import re
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,47}", text(value)):
        raise ValueError("Require an ASCII identifier starting with a letter")
    return value


def write(path, value):
    path.write_text(json.dumps(value, allow_nan=False, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class Workflow:
    fields = ("chain", "segi", "resi", "resn", "name", "alt", "element")

    def __init__(self, cmd, recipe, folder):
        self.c, self.recipe, self.folder = cmd, recipe, folder
        self.objects, self.loaded, self.results = {}, [], []
        self.maximum = integer(recipe["max_atoms"], 1, 100000)
        self.ledger_atoms = 0

    def atoms(self, key):
        model = self.c.get_model(self.objects[key], state=1)
        if not 0 < len(model.atom) <= self.maximum:
            raise ValueError("Object is empty or exceeds atom bound")
        atoms = []
        for atom in model.atom:
            row = dict(index=atom.index, id=atom.id, name=atom.name, resn=atom.resn,
                       resi=atom.resi, chain=atom.chain, segi=atom.segi, alt=atom.alt,
                       element=atom.symbol, hetatm=atom.hetatm, b=atom.b, q=atom.q,
                       coord=list(atom.coord))
            for value in row["coord"]:
                number(value)
            for field in self.fields:
                text(row[field])
            atoms.append(row)
        bonds = []
        for bond in model.bond:
            bonds.append([atoms[bond.index[0]]["index"], atoms[bond.index[1]]["index"], bond.order])
        return dict(atoms=atoms, bonds=bonds, states=self.c.count_states(self.objects[key]))

    def snapshot(self, stem):
        paths = {}
        for key in self.objects:
            ledger = self.atoms(key)
            self.ledger_atoms += len(ledger["atoms"])
            if self.ledger_atoms > 1000000:
                raise ValueError("Workflow exceeds one million atom-ledger entries; split it into smaller workflows")
            name = stem + "-" + key + ".json"
            write(self.folder / name, ledger)
            paths[key] = name
        return paths

    def select(self, value, name, exactly=None):
        spec = obj(value, ("structure",) + self.fields, ("structure",))
        key = spec["structure"]
        if key not in self.objects:
            raise ValueError("Unknown structure: " + str(key))
        criteria = {}
        for field in self.fields:
            if field in spec:
                values = spec[field] if isinstance(spec[field], list) else [spec[field]]
                if not 1 <= len(values) <= 10000:
                    raise ValueError("Empty/oversized exact-value selection")
                criteria[field] = {text(v).upper() if field == "element" else text(v) for v in values}
        atoms = [a for a in self.atoms(key)["atoms"] if all((a[k].upper() if k == "element" else a[k]) in v for k, v in criteria.items())]
        if not atoms or (exactly is not None and len(atoms) != exactly):
            raise ValueError("Selection requires " + str(exactly or "at least one") + " atom(s); found " + str(len(atoms)))
        # The expression contains generated object names and integer indices only.
        indices = "+".join(str(a["index"]) for a in atoms)
        self.c.select(name, self.objects[key] + " and index " + indices, quiet=1)
        return key, atoms

    def load(self):
        for item in self.recipe["structures"]:
            key = identifier(item["id"])
            if key in self.objects:
                raise ValueError("Duplicate structure identifier")
            path = self.folder / item["staged_path"]
            if hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
                raise ValueError("Staged structure changed")
            source, chosen = "jl_source_" + key, "jl_object_" + key
            namespace = item["cif_namespace"]
            if namespace not in ("author", "label") or item["format"] not in ("pdb", "cif"):
                raise ValueError("Unknown format/CIF identifier namespace")
            self.c.set("cif_use_auth", int(namespace == "author"))
            self.c.load(str(path), source, format=item["format"], discrete=1, quiet=1)
            count = self.c.count_states(source)
            state = integer(item["state"], 1, count)
            self.c.create(chosen, source, source_state=state, target_state=1, discrete=0, quiet=1)
            self.c.delete(source)
            self.objects[key] = chosen
            if sum(len(self.atoms(k)["atoms"]) for k in self.objects) > self.maximum:
                raise ValueError("Combined structures exceed --max-atoms")
            self.loaded.append(dict(id=key, source_states=count, selected_state_ordinal=state,
                                    cif_namespace=namespace, sha256=item["sha256"]))
        return self.snapshot("loaded")

    def measure(self, step):
        obj(step, ("id", "operation", "kind", "atoms"), ("kind", "atoms"))
        kind = step["kind"]
        count = {"distance": 2, "angle": 3, "dihedral": 4}.get(kind)
        if count is None or not isinstance(step["atoms"], list) or len(step["atoms"]) != count:
            raise ValueError("Wrong measurement kind/atom count")
        names, atoms = [], []
        for i, spec in enumerate(step["atoms"]):
            name = "jl_atom_" + str(i)
            key, rows = self.select(spec, name, 1)
            names.append(name)
            atoms.append(dict(structure=key, index=rows[0]["index"]))
        function = {"distance": self.c.get_distance, "angle": self.c.get_angle,
                    "dihedral": self.c.get_dihedral}[kind]
        value = function(*names, state=1, quiet=1)
        return dict(kind=kind, value=number(value), atoms=atoms,
                    unit="angstrom" if kind == "distance" else "degree")

    def fit(self, step):
        obj(step, ("id", "operation", "mobile", "target", "cutoff", "cycles", "gap", "extend", "max_gap", "max_skip"), ("mobile", "target"))
        mobile, ma = self.select(step["mobile"], "jl_mobile")
        target, ta = self.select(step["target"], "jl_target")
        if mobile == target or min(len(ma), len(ta)) < 3:
            raise ValueError("Alignment requires distinct objects with at least three selected atoms each")
        operation = step["operation"]
        parameters = dict(cutoff=number(step.get("cutoff", 2.0), .001, 1000),
                          cycles=integer(step.get("cycles", 5), 0, 100),
                          gap=number(step.get("gap", -10.0 if operation == "align" else -1.5), -1000, 0),
                          extend=number(step.get("extend", -.5 if operation == "align" else -.7), -1000, 0),
                          max_gap=integer(step.get("max_gap", 50), 0, 1000),
                          max_skip=integer(step.get("max_skip", 0), 0, 1000),
                          mobile_state=1, target_state=1, transform=1, reset=0, matrix="BLOSUM62")
        if operation == "super":
            parameters.update(seq=0, radius=12., scale=17., base=.65, coord=0., expect=6., window=3, ante=-1.)
        value = getattr(self.c, operation)("jl_mobile", "jl_target", object="jl_alignment", quiet=1, **parameters)
        raw = self.c.get_raw_alignment("jl_alignment")
        pairs = []
        for column in raw:
            if len(column) != 2:
                raise ValueError("Expected two-object inlier correspondence")
            mapping = dict(column)
            if set(mapping) != {self.objects[mobile], self.objects[target]}:
                raise ValueError("Unexpected alignment correspondence object")
            pairs.append([mapping[self.objects[mobile]], mapping[self.objects[target]]])
        self.c.delete("jl_alignment")
        return dict(mobile=mobile, target=target, parameters=parameters, original_summary=list(value),
                    inlier_pairs=pairs, mobile_selected_indices=[a["index"] for a in ma],
                    target_selected_indices=[a["index"] for a in ta])

    def bond(self, step):
        op = step["operation"]
        obj(step, ("id", "operation", "atoms", "order") if op == "bond" else ("id", "operation", "atoms"), ("atoms",))
        if not isinstance(step["atoms"], list) or len(step["atoms"]) != 2:
            raise ValueError("Bond edit requires two distinct atoms in the same object")
        a, aa = self.select(step["atoms"][0], "jl_bond_a", 1)
        b, bb = self.select(step["atoms"][1], "jl_bond_b", 1)
        if a != b or aa[0]["index"] == bb[0]["index"]:
            raise ValueError("Bond edit requires two distinct atoms in the same object")
        order = integer(step.get("order", 1), 1, 3)
        existing = [p for p in self.atoms(a)["bonds"] if set(p[:2]) == {aa[0]["index"], bb[0]["index"]}]
        if (op == "bond" and existing) or (op == "unbond" and len(existing) != 1):
            raise ValueError("Requested graph edge already exists or is absent")
        if op == "bond":
            self.c.bond("jl_bond_a", "jl_bond_b", order=order, quiet=1)
        else:
            self.c.unbond("jl_bond_a", "jl_bond_b", quiet=1)
        return dict(structure=a, indices=[aa[0]["index"], bb[0]["index"]],
                    order=order if op == "bond" else existing[0][2])

    def torsion(self, step):
        obj(step, ("id", "operation", "atoms", "degrees"), ("atoms", "degrees"))
        if not isinstance(step["atoms"], list) or len(step["atoms"]) != 4:
            raise ValueError("Torsion edit requires four atoms")
        keys, atoms, names = [], [], []
        for i, spec in enumerate(step["atoms"]):
            name = "jl_torsion_" + str(i)
            key, rows = self.select(spec, name, 1)
            keys.append(key)
            atoms.append(rows[0]["index"])
            names.append(name)
        if len(set(keys)) != 1 or len(set(atoms)) != 4:
            raise ValueError("Torsion requires four distinct atoms in one object")
        degrees = number(step["degrees"], -180, 180)
        before = self.c.get_dihedral(*names, state=1, quiet=1)
        self.c.set_dihedral(*names, degrees, state=1, quiet=1)
        after = self.c.get_dihedral(*names, state=1, quiet=1)
        return dict(structure=keys[0], indices=atoms, requested_degrees=degrees,
                    original_before=before, original_after=after)

    def mutate(self, step):
        obj(step, ("id", "operation", "residue", "to", "library", "rotamer", "hydrogens", "max_backbone_shift"),
            ("residue", "to", "library", "rotamer", "hydrogens", "max_backbone_shift"))
        key, atoms = self.select(step["residue"], "jl_mutation_pick")
        residues = {(a["segi"], a["chain"], a["resi"]) for a in atoms}
        if len(residues) != 1 or any(a["alt"] for a in atoms):
            raise ValueError("Mutation requires one whole residue without alternate conformers")
        selected = {a["index"] for a in atoms}
        original_model = self.atoms(key)
        entire = {a["index"] for a in original_model["atoms"] if (a["segi"], a["chain"], a["resi"]) in residues}
        if selected != entire:
            raise ValueError("Mutation selector must include the entire residue")
        by_index = {a["index"]: a for a in atoms}
        for a, b, _ in original_model["bonds"]:
            if (a in selected) != (b in selected) and by_index[a if a in selected else b]["name"] not in ("N", "C"):
                raise ValueError("Mutation of a covalently linked side chain requires an explicit unbond step first")
        target = step["to"]
        if target not in "ALA ARG ASN ASP CYS GLN GLU GLY HIS ILE LEU LYS MET PHE PRO SER THR TRP TYR VAL".split():
            raise ValueError("Unsupported canonical target amino acid")
        if step["library"] != "backbone-independent" or step["hydrogens"] not in ("none", "auto"):
            raise ValueError("Require explicit backbone-independent library and none|auto hydrogens")
        number(step["max_backbone_shift"], 0, 1)
        if {a["resn"] for a in atoms} == {target}:
            raise ValueError("Mutation target equals the current residue")
        self.c.wizard("mutagenesis")
        wizard = self.c.get_wizard()
        wizard.set_dep("ind")
        wizard.set_hyd(step["hydrogens"])
        wizard.set_mode(target)
        library_available = target in wizard.ind_library
        if not library_available and target not in ("ALA", "GLY"):
            raise ValueError("Original backbone-independent rotamer library has no entry for this target")
        # Upstream deletes this named selection. Passing an expression deletes the object.
        wizard.do_select("jl_mutation_pick")
        count = self.c.count_states("mutation")
        rotamer = integer(step["rotamer"], 1, count)
        self.c.frame(rotamer)
        wizard.apply()
        self.c.set_wizard()
        self.c.frame(1)
        return dict(structure=key, residue=list(next(iter(residues))), original_resn=sorted({a["resn"] for a in atoms}),
                    target_resn=target, library=step["library"], hydrogens=step["hydrogens"],
                    selected_rotamer=rotamer, available_rotamers=count,
                    conformation_source="original backbone-independent library" if library_available else "original ALA/GLY canonical fragment; no side-chain torsion library needed",
                    scope="Original library conformation; no energy minimization, folding or stability inference")

    def render(self, step):
        obj(step, ("id", "operation", "layers", "focus", "buffer", "width", "height", "background", "view"),
            ("layers", "focus", "buffer", "width", "height", "background"))
        c = self.c
        c.hide("everything", "all")
        layers = step["layers"]
        if not isinstance(layers, list) or not 1 <= len(layers) <= 32:
            raise ValueError("Require 1..32 render layers")
        for i, layer in enumerate(layers):
            obj(layer, ("selection", "representation", "color"), ("selection", "representation", "color"))
            if layer["representation"] not in ("cartoon", "sticks", "surface", "spheres", "lines", "ribbon"):
                raise ValueError("Unsupported explicit representation")
            self.select(layer["selection"], "jl_layer")
            color = self.rgb(layer["color"])
            c.set_color("jl_color_" + str(i), color)
            c.color("jl_color_" + str(i), "jl_layer", quiet=1)
            c.show(layer["representation"], "jl_layer")
        self.select(step["focus"], "jl_focus")
        padding = number(step["buffer"], 0, 1000)
        width, height = integer(step["width"], 100, 4000), integer(step["height"], 100, 4000)
        c.set_color("jl_background", self.rgb(step["background"]))
        c.bg_color("jl_background")
        c.set("ray_opaque_background", 1)
        c.set("orthoscopic", 1)
        if "view" in step:
            if not isinstance(step["view"], list) or len(step["view"]) != 18:
                raise ValueError("Camera view requires eighteen finite values")
            c.set_view([number(v) for v in step["view"]])
        else:
            c.orient("jl_focus", state=1)
            c.zoom("jl_focus", buffer=padding, state=1, complete=1)
        image = step["id"] + ".png"
        c.png(str(self.folder / image), width=width, height=height, ray=1, quiet=1)
        return dict(image=image, width=width, height=height, view=list(c.get_view()),
                    focus=step["focus"], buffer_angstrom=padding, layers=layers, background=step["background"],
                    framing="explicit supplied view" if "view" in step else "orient + zoom; caller must inspect clipping and representation")

    @staticmethod
    def rgb(value):
        if not isinstance(value, list) or len(value) != 3:
            raise ValueError("Color requires three explicit RGB numbers in [0,1]")
        return [number(v, 0, 1) for v in value]

    def run(self):
        initial = self.load()
        seen = set()
        for i, step in enumerate(self.recipe["steps"]):
            key = identifier(step.get("id"))
            if key in seen:
                raise ValueError("Duplicate step identifier")
            seen.add(key)
            op = step.get("operation")
            before = self.snapshot("s%02d-before" % i)
            if op == "measure":
                result = self.measure(step)
            elif op in ("align", "super"):
                result = self.fit(step)
            elif op in ("bond", "unbond"):
                result = self.bond(step)
            elif op == "set_dihedral":
                result = self.torsion(step)
            elif op == "mutate":
                result = self.mutate(step)
            elif op == "render":
                result = self.render(step)
            else:
                raise ValueError("Unsupported typed operation: " + str(op))
            after = self.snapshot("s%02d-after" % i)
            self.results.append(dict(id=key, operation=op, before=before, after=after, result=result))
            write(self.folder / "progress.json", dict(status="in-progress-not-yet-audited", steps=self.results))
        exports = {}
        final_ledgers = self.snapshot("final")
        for key, name in self.objects.items():
            path = self.folder / ("final-" + key + ".cif")
            self.c.save(str(path), name, state=1, format="cif", quiet=1)
            exports[key] = path.name
        return dict(status="original-execution-completed-requires-java-audit", schema_version=1,
                    pymol_version=list(self.c.get_version()), loaded=self.loaded, initial=initial,
                    steps=self.results, exports=exports, final_ledgers=final_ledgers,
                    export_scope="Original mmCIF: coordinates rounded to 0.001 Angstrom, B/occupancy to 0.01; no bond categories. Full-precision atom and bond ledgers are authoritative for edited graphs.",
                    coordinate_units="angstrom", angular_units="degree")


def main():
    folder = Path(sys.argv[3])
    recipe = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    obj(recipe, ("schema_version", "structures", "steps", "max_atoms", "threads"),
        ("schema_version", "structures", "steps", "max_atoms", "threads"))
    if recipe["schema_version"] != 1 or not isinstance(recipe["structures"], list) or not 1 <= len(recipe["structures"]) <= 8:
        raise ValueError("Unsupported schema/structure count")
    if not isinstance(recipe["steps"], list) or not 1 <= len(recipe["steps"]) <= 32:
        raise ValueError("Require 1..32 typed steps")
    with pymol2.PyMOL() as instance:
        c = instance.cmd
        c.set("max_threads", integer(recipe["threads"], 1, 64))
        c.set("retain_order", 1)
        c.set("pdb_retain_ids", 1)
        c.set("auto_zoom", 0)
        result = Workflow(c, recipe, folder).run()
        write(folder / "original-results.json", result)


try:
    main()
except Exception as failure:
    traceback.print_exc()
    write(Path(sys.argv[3]) / "original-failure.json", dict(status="failed-original-execution", error=type(failure).__name__ + ": " + str(failure)))
    sys.exit(2)
