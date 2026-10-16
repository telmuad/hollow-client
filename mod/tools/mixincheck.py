"""Check Hollow's mixins against the Minecraft jar Loom set up: target classes, injected method names/descriptors,
INVOKE/FIELD targets, @Shadow members and @Accessor/@Invoker names. Run it after a Minecraft version bump (see
VERSION_BUMP.md), from the mod folder, after `gradlew genSources`:

    python tools/mixincheck.py

Optional arguments: <mixin source dir> <minecraft jar>. It needs `javap` (part of the JDK) on PATH.

Known false alarms: a call to a method the owner inherits (for example `LocalPlayer.turn`, declared in Entity, or
`SubmitNodeCollector.submitModel`, declared in a parent interface) is reported as missing; confirm those with
`javap -c` on the target class. Handler parameter lists and ordinals are not checked: the in-game tests catch those.
"""
import functools
import pathlib
import re
import subprocess
import sys

def find_jar() -> str:
    props = dict(line.split("=", 1) for line in pathlib.Path("gradle.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    version = props["minecraft_version"].strip()
    jars = sorted(pathlib.Path(".gradle/loom-cache/minecraftMaven/net/minecraft").glob(f"*/{version}/*-{version}.jar"))
    if not jars:
        sys.exit(f"No Minecraft {version} jar in .gradle/loom-cache yet: run gradlew genSources first.")
    return str(jars[-1])


mixin_dir = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java/dev/telmuad/hollowclient/mixin")
jar = sys.argv[2] if len(sys.argv) > 2 else find_jar()


@functools.lru_cache(maxsize=None)
def javap_own(cls: str) -> tuple[set[str], set[str], set[str], tuple[str, ...]]:
    """(method names, method name+descriptor, field names, parent types) declared by one class."""
    r = subprocess.run(["javap", "-p", "-s", "-cp", jar, cls], capture_output=True, text=True)
    if r.returncode != 0:
        return set(), set(), set(), ()
    names, descs, fields = set(), set(), set()
    parents: list[str] = []
    lines = r.stdout.splitlines()
    for line in lines:
        h = re.search(r"(?:class|interface) [\w.$]+(?:<.*?>)? (extends [^{]+?)?(implements [^{]+?)?\s*\{", line)
        if h:
            for part in (h.group(1) or "", h.group(2) or ""):
                for t in re.sub(r"<[^<>]*(<[^<>]*>)*[^<>]*>", "", part.replace("extends", "").replace("implements", "")).split(","):
                    if t.strip():
                        parents.append(t.strip())
            break
    for i, line in enumerate(lines):
        m = re.search(r"\s([\w$<>]+)\((.*)\)", line)
        desc = lines[i + 1].strip() if i + 1 < len(lines) else ""
        if m and desc.startswith("descriptor:"):
            n = m.group(1)
            if n == cls.split(".")[-1]:
                n = "<init>"
            names.add(n)
            descs.add(n + desc.split(":", 1)[1].strip())
        elif desc.startswith("descriptor:") and line.strip().endswith(";") and "(" not in line:
            f = re.search(r"([\w$]+);$", line.strip())
            if f:
                fields.add(f.group(1))
    if "static {}" in r.stdout:
        names.add("<clinit>")
    return names, descs, fields, tuple(parents)


@functools.lru_cache(maxsize=None)
def javap(cls: str) -> tuple[set[str], set[str], set[str]]:
    """Members of a class including the ones it inherits from Minecraft parents (empty if the class is missing)."""
    names, descs, fields, parents = javap_own(cls)
    names, descs, fields = set(names), set(descs), set(fields)
    for p in parents:
        if p.startswith(("net.minecraft.", "com.mojang.")):
            n2, d2, f2 = javap(p)
            names |= n2
            descs |= d2
            fields |= f2
    return names, descs, fields


def resolve(simple: str, imports: dict[str, str], pkg: str) -> str:
    outer = simple.split(".")[0]
    if outer in imports:
        base = imports[outer]
        rest = simple.split(".")[1:]
        return base + "".join("$" + p for p in rest)
    return simple


problems = 0
for path in sorted(mixin_dir.glob("*.java")):
    src = path.read_text(encoding="utf-8")
    imports = {i.split(".")[-1]: i for i in re.findall(r"^import ([\w.]+);", src, re.M)}
    targets = re.findall(r"@Mixin\(\s*(?:value\s*=\s*)?\{?([^)}]+)\}?", src)
    classes = []
    for t in targets:
        for part in re.findall(r"([\w.]+)\.class", t):
            classes.append(resolve(part, imports, ""))
        for part in re.findall(r'"([\w./$]+)"', t):
            classes.append(part.replace("/", "."))
    if not classes:
        continue
    out = []
    for cls in classes:
        names, descs, fields = javap(cls)
        if not names:
            out.append(f"target class missing: {cls}")
            continue
        for mm in re.findall(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', src):
            for meth in re.findall(r'"([^"]+)"', mm):
                if "(" in meth:
                    if meth not in descs and not any(d.startswith(meth) for d in descs):
                        out.append(f"{cls}: no method {meth}")
                else:
                    if meth not in names and not meth.startswith("lambda$"):
                        out.append(f"{cls}: no method named {meth}")
        for sm in re.finditer(r"@Shadow[^;{]*?\s([\w$]+)\s*[;(]", src):
            n = sm.group(1)
            if n not in names and n not in fields:
                out.append(f"{cls}: @Shadow {n} not found")
        for am in re.finditer(r'@(Accessor|Invoker)\(\s*"([^"]+)"', src):
            n = am.group(2)
            if n not in names and n not in fields:
                out.append(f"{cls}: @{am.group(1)} {n} not found")
    for tm in re.findall(r'target\s*=\s*"L([\w/$]+);([\w$<>]+)(:?)([^"]*)"', src):
        owner, name, colon, desc = tm
        oc = owner.replace("/", ".")
        if not oc.startswith(("net.minecraft.", "com.mojang.")):
            continue
        names, descs, fields = javap(oc)
        if not names and not fields:
            out.append(f"INVOKE/FIELD owner missing: {oc}")
        elif colon:
            if name not in fields:
                out.append(f"{oc}: no field {name}")
        elif (name + desc) not in descs:
            close = sorted(d for d in descs if d.startswith(name + "("))
            out.append(f"{oc}: no {name}{desc}" + (f"  (have: {close[:3]})" if close else ""))
    if out:
        problems += len(out)
        print(f"== {path.name}")
        for o in out:
            print("   " + o)
print("problems:", problems)
