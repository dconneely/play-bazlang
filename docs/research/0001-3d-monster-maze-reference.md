# How did the original ZX81 *3D Monster Maze* generate its maze, drive Rex, and render its view?

**Confidence:** medium

Multiple secondary sources agree with each other on the mechanics below, several citing the original
Z80 disassembly directly, but this project has not verified any of it against that disassembly
itself.

## Finding

*3D Monster Maze* (1981/1982, Malcolm Evans, J.K. Greye Software) generates its maze by a randomised
passage-carving walk starting from the player's position, drives its monster (Rex) with simple
axis-priority pursuit rather than pathfinding, and renders its first-person view as six fixed depth
segments using half-block and quadrant Unicode-equivalent (ZX81 block-graphics) characters - not
ray-tracing or 3D projection. The status text, scoring, and win/lose text are fixed strings keyed off
Rex's distance and line of sight to the player.

`app-bazlang/src/example/bas/monster.bas` (446 lines, added 2026-07-06) contains a substantial
implementation attempt - including the ringmaster introduction text and tables that look like
`DISTCOL`/`DISTWALL` analogues - but it is incomplete and still has a rendering bug in the maze view
(confirmed 2026-08-22). See `PLAN.md`.

## Evidence

- **Wikipedia overview** - <https://en.wikipedia.org/wiki/3D_Monster_Maze> - general background,
  release history, reception.
- **Disassembly and analysis** -
  <http://www.fruitcake.plus.com/Sinclair/ZX81/Disassemblies/MonsterMaze.htm> - the primary technical
  source. Origin of the maze generation algorithm and Rex AI described below.
- **Making of article** - <https://www.sockmonsters.com/TheMakingOf3DMonsterMaze.html> - development
  history, not mechanics.
- **Game mechanics (detailed)** -
  <https://softtangouk.wixsite.com/soft-tango-uk/3d-monster-maze> - corroborates the disassembly's
  maze/Rex mechanics independently; also linked twice under slightly different titles in the
  original source material ("3D Monster Maze Dissected" is the same page).
- **PET port devlog** -
  <https://tynemouth.itch.io/pet-3d-monster-maze/devlog/316119/remaking-3d-monster-maze-for-the-commodore-pet>
  and **PET port article** -
  <http://blog.tynemouthsoftware.co.uk/2022/07/remaking-3d-monster-maze-for-the-commodore-pet.html>,
  a working reimplementation with its own rendering simplifications (see "Dead ends").
- **VIC-20 port article** -
  <http://blog.tynemouthsoftware.co.uk/2023/07/3d-monster-maze-for-vic20.html> - another port,
  same author.
- **Jupiter Ace port (Forth + Z80 asm)** -
  <https://github.com/markgbeckett/jupiter_ace/tree/master/3d_monster_maze> - `3dmm_viewer.asm`
  contains readable, commented rendering logic; the character-mapping table below is sourced from it.
- **Screenshots / colour reference** - <https://thekingofgrabs.com/2018/07/31/3d-monster-maze-zx81/>,
  <https://www.zx-gaming.co.uk/games/monstermaze/default.htm>,
  <https://www.retrogamesnow.co.uk/3d-monster-maze-for-sinclair-zx81/>.
- **Program analysis** - <https://www.timexsinclair.com/computer_media/3d-monster-maze/index.html>.
- **Colour/mood pieces, not technical** -
  <https://kimimithegameeatingshemonster.com/2023/06/09/3d-monster-maze-horror-begins-here/>.

### Maze structure and generation

18 columns x 16 rows (inner 16x14 playable). Player starts in the SE corner (row 16, col 2) facing
west; the exit sits in the northern third, forming a cul-de-sac with only one entrance. Generation
starts at the player's position and repeatedly picks a random direction and length (1-6), carving one
cell at a time and stopping early on reaching a cell with no wall perpendicular to the current
direction - this prevents side-by-side corridors while still allowing crossings, and enforces that
each passage is exactly one cell wide (no 2x2 empty rooms).

### Rex AI

Moves toward the player at half speed while the player is moving, quarter speed while stationary.
Computes the N-S and W-E position difference and tries to close the larger one first, falling back
to the other axis if blocked - axis-priority pursuit, not pathfinding. Only ever seen face-on
(always approaching the player), so sprites never need drawing from behind.

Status messages, exact wording from the disassembly, keyed on Rex's distance and line of sight
(suppressed once Rex is visible on screen):

| Message | Condition |
| --- | --- |
| "REX LIES IN WAIT" | not visible, could not move |
| "HE IS HUNTING YOU" | > 8 positions from player |
| "FOOTSTEPS APPROACHING" | 7-8 positions from player |
| "REX HAS SEEN YOU" | 3-6 positions + line of sight |
| "RUN HE IS BEHIND YOU" | < 3 positions behind + line of sight |
| "RUN HE IS BESIDE YOU" | < 3 positions to the side + line of sight |

### Scoring and win/lose

5 points per move while Rex is tracking; 200-point bonus for escaping. Win: reach the exit ->
"ANOTHER VICTIM ESCAPES REX" -> new maze, score carried forward. Lose: Rex catches the player ->
posthumous points + "SENTENCED TO ROAM THE MAZE FOREVER", with a 50% appeal (press A) either
generating a fresh game (accepted) or replaying the same maze (rejected).

### Rendering

21-column * 20-row view, centre column 10, rendered in 6 fixed depth segments (0 = beside the
player, 5 = furthest) - not by 3D projection or ray tracing. `DISTCOL` gives each segment's left-half
column start: `0, 1, 4, 6, 8, 9, 10` (the right side mirrors the left: column `20-N` mirrors column
`N`). `DISTWALL` gives the wall height (row of the top diagonal) per column:
`1, 4, 4, 4, 6, 6, 8, 8, 9, 10`; the solid middle section's height is `18 - 2*wallHeight`. Each wall
column draws top spaces, a top diagonal, a solid middle, a bottom diagonal, then bottom spaces;
passages show chequerboard instead of solid wall. Walls draw first, passages drawn over them.

Character mapping (from the Jupiter Ace port's `3dmm_viewer.asm`):

| Role | Character |
| --- | --- |
| Left wall top diagonal | `▙` (U+2599) |
| Left wall bottom diagonal | `▛` (U+259B) |
| Right wall top diagonal | `▟` (U+259F) |
| Right wall bottom diagonal | `▜` (U+259C) |
| Solid wall middle | `█` (U+2588) |
| Chequerboard (facing walls, passages) | `▒` (U+2592) |

Rex has 10 sprite frames (5 distances * 2 walking animations); each larger frame fully obscures the
previous one, so no erase pass is needed - it is superimposed directly on the existing view.

## Dead ends

Two rendering approaches were tried in earlier BazLang attempts and did not produce a good result.
Confirmed still current as of 2026-08-22 against `monster.bas` in its present state:

- **Simple rectangular walls at each depth** - functional but coarse; does not capture the original's
  smooth perspective lines.
- **A direct port of the `DISTCOL`/`DISTWALL` tables into BASIC loops** - proved difficult to
  translate correctly and produced broken rendering.

The PET port's own simplifications, not yet tried here, are worth considering as a middle ground:
using a single character for the furthest wall's centre instead of six, and building each frame into
a buffer string before printing it (BazLang equivalent: build strings before `PRINT`ing, rather than
plotting cell-by-cell) rather than attempting the original's exact segment table.

## Open questions

- Has real ZX81 Sinclair BASIC's original disassembly been checked directly by this project, rather
  than relying on secondary sources agreeing with each other? Not yet - that would raise this note's
  confidence from medium to high.
- What exactly is wrong with `monster.bas`'s maze-view rendering - closer to the "coarse rectangular
  walls" dead end, the "broken `DISTCOL`/`DISTWALL` port" one, both, or something else not yet
  described here? Worth pinning down precisely when the fix in `PLAN.md` is picked up, since it
  changes which of this note's two logged dead ends (or the untried PET-port simplification) is the
  right starting point.
