# Bundled judge driver — keep in sync

`UniversalPythonDriver.py` here is a **verbatim copy** of:

    services/judge-service/src/main/resources/drivers/UniversalPythonDriver.py

The AI Docker image only ships the `AI/` tree, so the generator cannot reach the
judge-service path at runtime. `generator/nodes/expected_verifier.py` loads this
bundled copy to execute the analyzer's reference solution and derive canonical
`expectedOutput` values (byte-compatible with all four Universal*Driver files).

If you change the judge driver, re-copy it here:

    cp services/judge-service/src/main/resources/drivers/UniversalPythonDriver.py \
       AI/generator/drivers/UniversalPythonDriver.py

The two files must be byte-identical (md5 match). A drift means generated
expected outputs no longer match what the judge actually produces.
