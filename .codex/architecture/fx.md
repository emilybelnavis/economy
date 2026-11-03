# FX publications and policy

The A2-05 chooser can bind `FxService.OPEN` behind a trusted selection
boundary. The handler captures one UTC day on the storage worker, then
records the opening denomination (the publication's reference), complete
rebased rates, and default policy in a single operation. Opening uses the
operation UUID as both update and policy identity. Journal deduplication
retains the committed result across midnight and uncertain responses.

The FX clock derives its origin from the opening publication. Its own
monotonic wall observation begins on activation, so preselection clock
observations cannot become FX debt. It prepares the previous complete
publication and effective policy on storage, calculates from detached
inputs, and revalidates them before commit. Each daily envelope records the
economic completion and the matching FX publication. The SQLite day indexes
update in that transaction. The unique publication-day index prevents
duplicate day records. Both indexes derive directly from recorded outcomes
and can be rebuilt by SQLite without running a simulator.

## Version 1 numerical contract

Each rate is permanent-reference major units per currency major unit.
Opening divides the historical CAD inputs by the selected currency's
historical CAD input and publishes at scale 12, half-even. The reference
is fixed at 1. The other currencies use a 3,000 ppm draw radius, 100,000
ppm pull, and 50,000 ppm band by default. Trend is zero. A new policy starts
from yesterday's publication and clamps to its inward-rounded bounds.
Baselines must be representable at scale 12. Variation and pull each
accept 0–1,000,000 ppm and bounds accept 0–999,999 ppm. The entire band
must lie in [0.000000000001, 1000000].

Sampler input is ASCII `economy:fx-draw:v1` followed by NUL, the signed
A1 seed as big-endian i64, the three ASCII code bytes and a big-endian i32
counter starting at 0. SHA-256 supplies its first unsigned big-endian
32-bit word. For span = 2 × radius + 1, reject words at or above
floor(2^32 / span) × span, increment the counter and hash again. Map an
accepted word to word % span − radius. Golden vectors include seed 0 / CAD
/ 3000 → 2982 and seed 720 / CAD / 1000000 → −624559 after one rejection.

## Policy and reads

An authenticated in-game operator can save a full version by editing one
floating currency. The worker selects the next UTC day. Multiple edits
for that day merge against the most recently committed policy for that day,
and the final saved version applies as a whole. A published day is immutable.
The command uses the caller's operation UUID for journal recovery and
rechecks the live player before feedback.

`FxService.latest`, `publication`, and `policy` expose bounded storage-worker
lookups for diagnostics and later quote/history work. `Publication.conversionRates`
detaches one publication for A2-02. Startup verifies exact record types and
versions and checks that daily publication bytes match the clock outcome.
See [storage](storage.md) and [clock and recovery](recovery-clock.md).
