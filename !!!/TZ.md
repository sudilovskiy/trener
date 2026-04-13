Markdown
## Status

The project is **not complete yet**.

Codex is still needed, but the refactor can now move in a **more aggressive completion-oriented mode** while still preserving behavior.

## Next Codex task

Perform **naming cleanup for the extracted analytics files and nearby helper names**, then remove **clearly unused local code** only where compile-safe.

## Execution mode

Work more aggressively than before, but still within the hard safety boundaries:

- finish the started analytics-area cleanup decisively
- batch obvious safe renames together
- batch obvious dead-code removals together
- avoid micro-iterations when the risk is low and the intent is clear
- still verify build/compile after the change set

## Objective

Complete the analytics-area refactor so that this area is no longer half-transitioned.

The result should be:

- consistent file names
- consistent helper names
- no temporary/refactor-transition naming leftovers
- no clearly unused analytics-local code
- no behavior changes

## Strict constraints

- Do **not** change behavior
- Do **not** change UX
- Do **not** change chart rendering
- Do **not** change chart calculations
- Do **not** change navigation
- Do **not** change BLE logic
- Do **not** change database code
- Do **not** touch `!!!`

## Scope

Focus on:

- extracted analytics/chart files
- nearby helpers created or touched during the split from `ExerciseAnalytics.kt`
- local analytics-related names still inconsistent, vague, duplicated, or transitional
- clearly unused analytics-local/private code only

## Naming goals

Bring naming to a clean, final state:

- file names should reflect actual responsibility
- composable names should be explicit
- helper names should describe purpose, not implementation accident
- remove vague names such as temporary, generic, shortened, or transitional labels
- keep terminology consistent across exercise analytics and weight analytics areas

## Good rename targets

Rename aggressively where the intent is obvious and safe, such as:

- ambiguous file names
- generic helper names like `Utils`, `Helpers`, `Data`, `ChartStuff`, `Common`
- unclear composable names
- short or cryptic private helper names
- transitional names created during extraction
- inconsistent pairs where one file/component uses a clearer naming scheme than another

## Removal rules

After naming cleanup, remove only code that is **clearly unused and compile-safe**, for example:

- private functions with no references
- dead local constants
- obsolete wrapper helpers left behind after extraction
- duplicate helper variants no longer called
- debug-only analytics-local code that is no longer needed
- unused imports and local temporary variables

## Do not remove

- anything referenced indirectly unless usage is confirmed
- anything that affects chart behavior
- anything in BLE paths
- anything outside the current analytics/local cleanup area unless required for compile correctness
- anything uncertain

## Working approach

1. Normalize analytics-related file names to a final naming scheme
2. Normalize nearby composable/helper/function names
3. Update references/imports consistently
4. Identify clearly unused local code introduced or exposed by the extraction
5. Remove only what is provably unused
6. Run compile/build verification
7. Report any remaining hotspots that still prevent declaring the refactor complete

## Decision rule

Be assertive:

- if a rename is clearly beneficial and behavior-neutral, do it now
- if dead code is clearly unused and compile-safe, remove it now
- do not leave obvious cleanup half-done just to stay overly conservative
- however, do not cross into behavioral refactoring

## Verification required

After this step, verify:

- build/compile passes
- analytics screen still opens correctly
- weight chart behavior is unchanged
- exercise chart behavior is unchanged
- labels/values/ranges remain unchanged
- no missing reference/import/package issues
- `!!!` remains untouched

## Deliverable

Return a concise implementation summary with:

1. which files were renamed
2. which key helpers/composables/functions were renamed
3. which clearly unused local items were removed
4. what was intentionally preserved
5. build/compile result
6. whether any safe refactor work still remains before completion can be declared

## Completion rule

If, after this step, there is no meaningful remaining Codex-safe refactor work that fits the original scope without risking behavior change, treat the project as complete and output:

`DONE_MARKER = "[[CODEFLOW_AUTOMATOR2_PROJECT_DONE_9F1C6E2B]]"`

Otherwise, do not output it yet.

[[[PROGRESS 96%]]]