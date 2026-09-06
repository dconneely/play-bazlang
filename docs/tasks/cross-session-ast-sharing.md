# Cross-session AST sharing (forward-looking design note)

## Context

[ast-immutable-ref-caches.md](ast-immutable-ref-caches.md) gave every variable/array AST node a
`final int id`, assigned at lowering time by a `VarIdAllocator`, scoped to one `EvalState`'s
lifetime. That removes the _mutable-field_ barrier to sharing a lowered `ProgramLine`'s `Stmt` list
across multiple `EvalState` instances, but doesn't itself let two different sessions execute the
same lowered AST - each `EvalState` still lowers its own program independently, minting its own id
space in the process. This note captures the further design that would actually enable that, for
whenever a concrete need shows up (a debugger running several isolated programme instances from one
cached parse; the "Resolve the threading model" `PLAN.md` item's true concurrent-execution case).

**Not scheduled.** No `PLAN.md` entry currently points here - this is a design sketch to save
re-deriving the reasoning later, not committed work.

## The core problem

Two independently-created `EvalState`s lowering the _same source text_ will not, in general, assign
the same id to the same variable name - lowering is lazy and per-line, triggered by whichever line
executes first, and execution order (`GOTO`, `GOSUB`) can differ run to run. For a lowered AST to be
genuinely shareable, the id assignment needs to happen exactly once, independent of which
`EvalState` (if any) is executing at the time.

## Sketch

Move the `VarIdAllocator` (name -> id assignment) up from `EvalState`/`VariableStore` to `Program`

- the object that already owns every `ProgramLine` for one loaded programme. `Program` would own the
  id-assignment _table_ (a growing name-to-id map, minted incrementally as never-before-lowered
  lines get lowered over the programme's execution lifetime) but not the _values_ those ids index -
  value storage (the actual `NumVarRef`/`NumArrayRef`/`StrVarRef` arrays) would move to a
  lighter-weight per-`EvalState` structure, sized/grown to match `Program`'s current id count.

This decouples two things that are conflated today:

- **A compiled programme** (`Program` + its lowered `Stmt` lists + its id table) - reusable,
  effectively read-mostly once warmed up, safe to reference from multiple sessions concurrently as
  long as nothing mutates a `ProgramLine`'s cached `Stmt` list while another session might be
  executing it (still true today; unaffected by this change).
- **One session's variable values** (a `VariableStore`-shaped array of `Ref`s, indexed by the shared
  `Program`'s ids) - genuinely per-`EvalState`, freely divergent between sessions running the same
  compiled programme.

Today `Program` is constructed fresh inside each `EvalState`
(`private final Program program = new Program();`), so this also needs loosening the 1:1
`Program`-`EvalState` relationship itself - e.g. allowing a `Program` to be constructed
independently and attached to (or shared by) more than one `EvalState`. That's a bigger structural
change than the id-scoping alone, which is why it's split out here rather than folded into the main
item.

## Open questions, not yet resolved

- Whether `RENUM`/`EDIT` replacing a `ProgramLine` (discarding its cached `Stmt` list) should reuse
  existing ids for names it already knows, or whether a compiled `Program`'s id table should only
  ever grow, never reconcile - affects whether two sessions sharing a `Program` need to coordinate
  around a `RENUM`/`EDIT` happening in one of them.
- Whether "the same compiled `Program` executing concurrently in two threads" is actually the target
  shape, or whether the real motivating use case (multiple debugger-managed programme instances) is
  better served by sequential reuse (never two `EvalState`s executing the same `Program` at the
  literal same instant) - the latter needs none of the thread-safety care the former would.
- How this interacts with the AST-node caching `ProgramLine.getFlattenedStatements` already relies
  on: a shared `Program`'s cached `Stmt` list only helps if all sharing sessions actually reach the
  same lines, which isn't guaranteed by construction (different sessions could plausibly diverge
  early via different `INPUT`/`GOTO` paths).

## Status

Not started. Revisit if a concrete use case for concurrent/shared execution appears.
