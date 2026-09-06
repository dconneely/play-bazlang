# True concurrent execution of a shared Program

## Context

A compiled `Program` can now be handed from one `EvalState` to a later one - see `Program`'s class
Javadoc, `EvalState(Program)`, and `com.davidconneely.bazlang.exec.CrossSessionSharingTest` - so a
second session reuses the first's lowering work (cached `Stmt` lists, variable ids) instead of
re-parsing. That work deliberately scoped itself to **sequential reuse**: sessions take turns: one
`EvalState` at a time over a given `Program`, never two executing it simultaneously. This note
captures what's still missing for genuine concurrent execution - two `EvalState`s running the same
`Program`'s statements on separate threads at the same instant - since that's a real further step,
not a side effect of the reuse work already done.

**Not scheduled.** No `PLAN.md` entry currently points anywhere else - this is a design note to save
re-deriving the gap later, not committed work.

## What's already safe

`Program`'s id tables (`numVarId`/`numArrayId`/`strVarId` and their reverse lookups) are backed by a
`ConcurrentHashMap` plus a `ReentrantLock`-guarded name list - minting an id is atomic as a whole,
so two threads can't race to assign two different ids to the same new name. This was deliberately
built this way from the start (see `Program.IdTable`'s Javadoc) precisely so this piece wouldn't
need revisiting here.

## What's still missing

- **`ProgramLine.cachedFlatStatements`'s lazy-init is a plain, unsynchronized field.** Two threads
  reaching a not-yet-lowered line of the same `Program` at the same moment have no visibility
  guarantee under the Java Memory Model - one could observe a stale or partially-published
  reference. Needs to become a `volatile` field, an `AtomicReference`, or a lock-guarded check
  (matching the `ReentrantLock` convention `Program.IdTable`/`JavaSoundSpeaker`/`PlaySequencer`
  already use).
- **Whether concurrent execution is even the right target shape.** The motivating use case so far (a
  debugger managing several isolated programme instances) is arguably better served by strictly
  sequential reuse - never two `EvalState`s executing the same `Program` at the literal same
  instant - which needs none of the above. Revisit only once a concrete need for actual concurrency
  (not just reuse) appears.
- **`EvalState`'s own mutable execution state** (`forLoops`, the GOSUB return stack, the programme
  counter, the `DATA` pointer) is already per-session, so unaffected by sharing a `Program` - noted
  here only so a future reader doesn't have to re-derive that it's already fine.

## Status

Not started. Revisit only if a concrete need for true concurrent (not just sequential) execution of
a shared `Program` appears.
