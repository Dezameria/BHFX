# Dev control: driving the dev client from files

Added 2026-11-16 (0.3.15), `impl/client/DevControl`. Lets a tool (Claude) run commands in a running **dev** client and
read the results without anyone typing, mostly to take `/bhfx shoot` pictures and read them back.

## Turning it on
- Only in a dev client (`FMLEnvironment.production` false: `runClient`); never in a built jar someone installs.
- Only when the folder `<run>/bhfx_dev/` exists when the game starts (`bhfx/run/bhfx_dev` or `BHSpells/run/bhfx_dev`).
  Delete the folder to turn it off. The log says `bhfx dev control on: reading ...`.
- While on, the game doesn't pause when its window loses focus (a paused world would hold shots forever:
  `options.pauseOnLostFocus` is set false at runtime and may be saved to `options.txt`), and a pause menu is closed
  before each line. Don't minimise the window: a minimised window draws nothing.

## Starting the game straight into a world
```
./gradlew.bat runClient -PquickPlay="New World" -Pwindow=1600x900
```
`-PquickPlay` passes Minecraft's `--quickPlaySingleplayer` (the world's folder name under `run/saves`), `-Pwindow`
the window size. Both optional.

## Files
| Path (under `<run>/bhfx_dev/`) | What |
| :--- | :--- |
| `inbox/*.txt` | Commands, one per line; files run in name order, one at a time. A started file moves to `inbox/done/`. |
| `outbox/<file>.log` | What each line did, with times (`sent: /...`, `refused (...)`, `wait 2`, start / end). |
| `status.txt` | Rewritten every second: world (or "none" on the title screen), player position and view, game and day time, camera type, open screen, window and fps, shoot busy, inbox progress, newest shot folder, `/bhfx status`. |

Lines:
- `# comment`, empty lines: skipped.
- `wait <seconds>` (0–600).
- `camera first|back|front`.
- `status`: logs the last second's fps and `/bhfx status` into the outbox (readings to compare runs by).
- `quit`: closes the game.
- A command, with or without `/`, whose first word is one of `bhfx tp teleport time weather gamemode gamerule summon
  kill effect difficulty fill setblock clear give` (`DevControl.ALLOWED`); sent as if typed. Anything else is refused.

A line waits while `/bhfx shoot` / `shoots` is busy, so shoots in one file run one after another. Nothing runs before
the player is in a world.

## Example
```
# side view of test_flow, fixed camera, noon
time set 6000
tp @s 0 -60 10 180 10
bhfx shoot bhfx:test_flow at 0 -60 0
bhfx shoots bhfx:test_vortex bhfx:test_swirl
```
Results: `screenshots/bhfx/latest.txt` / `last_batch.txt` name the folders; each folder has the shots, `sheet.png`
(all shots on one page) and `shots.txt` (per shot: requested / actual time and `/bhfx status`).

## Why files
No network port, nothing to configure, works the same in both dev folders, and the allowlist keeps it to test commands.
Decision record: [`decisions.md`](decisions.md) 2026-11-16.
