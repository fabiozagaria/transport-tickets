"""Run Spring MVC/Security tests and the domain/codec regressions with Maven."""
import pathlib
import subprocess
subprocess.run(['mvn', '-B', 'test'], cwd=pathlib.Path(__file__).resolve().parents[1], check=True)
