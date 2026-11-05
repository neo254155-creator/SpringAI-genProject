#!/usr/bin/env python3
"""Build the current runtime classpath and run safe live connection checks."""
import os
import json
from pathlib import Path
import subprocess
import sys


def main():
    options = sys.argv[1:]
    if any(option not in ("--database-only", "--include-ai") for option in options) or len(options) > 1:
        print("Usage: verify-live-connections.py [--database-only | --include-ai]")
        return 2
    project = Path(__file__).resolve().parent.parent
    classpath_file = project / "target" / "live-check-classpath.txt"
    wrapper = (["cmd", "/c", str(project / "mvnw.cmd")] if os.name == "nt"
               else ["sh", str(project / "mvnw")])
    result = subprocess.run(wrapper + ["-q", "-DskipTests", "compile", "dependency:build-classpath",
                            "-DincludeScope=runtime", "-Dmdep.outputFile=" + str(classpath_file)], cwd=project)
    if result.returncode:
        print("Could not build the runtime classpath; live checks were not executed.")
        return result.returncode
    classpath = str(project / "target" / "classes") + os.pathsep + classpath_file.read_text().strip()
    # Java argument files also work when the Windows dependency classpath exceeds 32 KiB.
    arguments = ["--class-path", classpath, str(project / "scripts" / "VerifyLiveConnections.java"), *options]
    args_file = project / "target" / "live-check.args"
    args_file.write_text("\n".join(json.dumps(argument, ensure_ascii=False) for argument in arguments), encoding="utf-8")
    return subprocess.run(["java", "@" + str(args_file)], cwd=project).returncode


if __name__ == "__main__":
    try:
        sys.exit(main())
    except FileNotFoundError:
        print("A required executable or file is missing. Check Python, Java 21, the Maven wrapper, and dependencies.")
        sys.exit(1)
