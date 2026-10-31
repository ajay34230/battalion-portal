# Critical logic engine

Implemented: "Compare approaches" asks the local model for two approaches with complexity, risks, a recommendation and five test cases (normal, boundary, failure). This is *advice from a small model* and may be wrong.
The workflow's checks that are real: tests written into the project can be executed via Run (Python/Node) and the actual output is shown; the fix loop only claims success after checks pass.
Not built: automatic algorithm research, independent second-implementation validation, complexity measurement. Do not trust generated algorithms without running their tests.
