#!/usr/bin/env python3
"""Build the current runtime classpath and run safe live connection checks."""
import os
from pathlib import Path
import subprocess
import sys


def main():
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
    return subprocess.run(["java", "--class-path", classpath, str(project / "scripts" / "VerifyLiveConnections.java")],
                          cwd=project).returncode


if __name__ == "__main__":
    try:
        sys.exit(main())
    except FileNotFoundError:
        print("A required executable or file is missing. Check Python, Java 21, the Maven wrapper, and dependencies.")
        sys.exit(1)
