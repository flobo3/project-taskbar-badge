#!/usr/bin/env python3
"""Build script for Project Taskbar Badge plugin (no Gradle required)."""

import glob
import os
import shutil
import subprocess
import sys
import zipfile


def find_javac(ide_home: str) -> str:
    candidate = os.path.join(ide_home, "jbr", "bin", "javac.exe" if os.name == "nt" else "javac")
    if os.path.isfile(candidate):
        return candidate
    found = shutil.which("javac")
    if found:
        return found
    raise RuntimeError("javac not found. Pass IDE home as IDE_HOME env var or install JDK 17+.")


def main() -> None:
    root = os.path.dirname(os.path.abspath(__file__))
    ide_home = os.environ.get("IDE_HOME")
    if not ide_home:
        if len(sys.argv) > 1:
            ide_home = sys.argv[1]
        else:
            raise RuntimeError("Set IDE_HOME env var or pass path to JetBrains IDE directory as first argument.")

    lib_dir = os.path.join(ide_home, "lib")
    if not os.path.isdir(lib_dir):
        raise RuntimeError(f"IDE lib directory not found: {lib_dir}")

    version = "1.1.0"
    out_dir = os.path.join(root, "out")
    dist_dir = os.path.join(root, "dist")
    shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(dist_dir, exist_ok=True)

    javac = find_javac(ide_home)
    sources = glob.glob(os.path.join(root, "src", "com", "github", "flobo3", "taskbarbadge", "*.java"))
    classpath = os.path.join(lib_dir, "*")

    subprocess.check_call([javac, "--release", "17", "-cp", classpath, "-d", out_dir] + sources)

    jar_path = os.path.join(dist_dir, f"project-taskbar-badge-{version}.jar")
    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(os.path.join(root, "resources", "META-INF", "plugin.xml"), "META-INF/plugin.xml")
        zf.write(os.path.join(root, "resources", "META-INF", "pluginIcon.svg"), "META-INF/pluginIcon.svg")
        for cur_root, _, files in os.walk(out_dir):
            for fname in files:
                full = os.path.join(cur_root, fname)
                rel = os.path.relpath(full, out_dir).replace(os.sep, "/")
                zf.write(full, rel)

    zip_path = os.path.join(dist_dir, f"project-taskbar-badge-{version}.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(jar_path, f"project-taskbar-badge/lib/project-taskbar-badge-{version}.jar")

    print("Built:", zip_path)


if __name__ == "__main__":
    main()
