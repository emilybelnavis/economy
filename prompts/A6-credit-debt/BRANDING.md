# Original banking and card art brief

Status: **approved original-art brief for execution, 2026-11-04; CHF cash artwork delivered by A6-03-01; institution/network/hardware/agent artwork delivered by A6-05; physical binding delivered by A6-05-01; issued debit cards delivered by A6-06; credit facilities and agent behavior await their owning packages**. The user approved the listed fictional institution/network names, home-currency recommendations, palettes, motifs and CHF denomination set. See [execution preparation](EXECUTION.md). Real organizations are inspiration supplied by the user, not in-game providers, affiliations or API integrations. Create original logos, palettes, layouts and typography; do not trace their marks, card layouts, slogans or trade dress. Keep provenance/license records. A6-01 assigns stable machine IDs from this approved roster before dependent asset work.

## Institution roster

| Inspiration | Approved fictional institution | Home currency | Services | Original visual direction |
| --- | --- | --- | --- | --- |
| Royal Bank of Canada | Crownstone Bank | CAD | Tier 1 + Tier 2 | Slate/amber stepped mountain |
| Tangerine | Orchard Direct | CAD | Tier 1 | Apricot/ink orchard rows |
| Bank of Nova Scotia | Tidecrest Bank | CAD | Tier 1 | Seafoam/plum tide bars |
| Chase | Crosswind Bank | USD | Tier 1 | Rust/cream crossing paths |
| Citibank | Lantern Union | USD | Tier 1 + Tier 2 | Indigo/copper lantern grid |
| Wells Fargo | Mesa Trail Bank | USD | Tier 1 | Sand/teal horizon terraces |
| Barclays | Asterbridge Bank | GBP | Tier 1 + Tier 2 | Violet/silver bridge arches |
| Lloyds | Greenhaven Bank | GBP | Tier 1 | Moss/coral leaf lattice |
| BNP Paribas | Meridian Grove | EUR | Tier 1 + Tier 2 | Cobalt/ochre branching meridians |
| DKB | Slate Harbor Bank | EUR | Tier 1 | Charcoal/lime harbor posts |
| Danske Bank | Fjordlight Bank | DKK | Tier 1 | Ice-blue/brick stepped fjord |
| HSBC | Harbor Meridian | HKD | Tier 1 + Tier 2 | Petrol/gold harbor rings |
| Bank of China | Jade Terrace Bank | CNY | Tier 1 | Jade/ivory terrace bands |
| ANZ | Southern Arc Bank | AUD | Tier 1 | Ochre/navy offset arcs |
| Bank of New Zealand | Fernreach Bank | NZD | Tier 1 | Plum/sage fern geometry |
| Japan Post Bank | Cedar Parcel Bank | JPY | Tier 1 | Cedar/cream folded square |
| Zurich Cantonal Bank | Lakeward Cantonal Bank | CHF | Tier 1 | Lake blue/limestone stepped shoreline |
| Neue Bank AG | Newhaven Bank | CHF | Tier 1 | Burgundy/pearl rising square lattice |
| Bank of America | Atlas Reserve | USD | Tier 2 only | Graphite/copper contour tiles |
| Deutsche Bank | Obsidian Capital Bank | EUR | Tier 2 only | Obsidian/pearl split pillar |
| UBS | Alpine Vault | CHF | Tier 2 only | Aubergine/silver alpine facets |
| Sumitomo Mitsui Financial Group | Kintsugi Trust | JPY | Tier 2 only | Ink/bronze joined lines |
| Macquarie Group | Mariner Crest | AUD | Tier 2 only | Deep teal/ivory sail folds |

CHF is now explicitly approved. Alpine Vault base currency is approved as CHF; its earlier currency blocker no longer requires currency expansion. Currency is product metadata, never guessed from artwork. Exactly 18 Tier 1 institutions, five hybrid providers within those 18, and five additional Tier 2 specialists.

## Networks and deliverables

| Inspiration | Approved network | Purchase APR | Cash-advance APR | Original motif |
| --- | --- | --- | --- | --- |
| American Express | Keystone | 21.99% | 23.99% | Stacked offset tiles |
| Mastercard | Orbit | 19.99% | 21.99% | Single broken orbital line, no overlapping circles |
| Visa | Wayline | 19.99% | 21.99% | Path through three square nodes |
| Discover | Ember | 19.99% | 21.99% | Small angular spark constellation |

A6-05 produces, only when invoked: 23 issuer marks; four network marks; approved issuer bank-card variants and credit-card variants using an explicit issuer/network compatibility matrix; Tier 2 treatment for all ten eligible providers; readable menu icons; original Collection Agent texture/model concept and implementation-ready resource inventory for A6-11. Avoid the unapproved 23×4 Cartesian product. Use one coherent card template with issuer, product, network and tier independently legible; do not use color alone for tier. Actual balances, card IDs, player names and limits belong in server-provided UI, not baked textures. No real card numbers/security credentials.

## Branded branch hardware and CHF cash

A6-05 designs institution-branded ATM, teller station and branch-kiosk textures for every approved bank (23 brands). Replace generic ATM color choices with bank identities; teller stations use the same approved brand palette, mark and labels. Use the approved branding palettes; do not copy real logos or trade dress. Use legible issuer name/icon and currency label rather than relying only on color. The ATM/teller currency is the selected bank base currency from the server catalog; player recoloring cannot change currency or issuer. Branch kiosks have a distinct silhouette and account/card/application icons so they are distinguishable from deposit/withdrawal stations. A6-05-01 implements blocks/binding/migration and interaction; art alone grants no institution identity.

A6-03-01 owns original CHF notes/coins, models, item IDs, localization and tab/catalog integration. Approved game denomination set: notes 10, 20, 50, 100, 200, 1,000; coins 0.05, 0.10, 0.20, 0.50, 1, 2, 5 CHF. Use this approved set when A6-03-01 is executed. Use original Alpine/river/geometric designs, clear value/CHF labels and visually distinct coin sizes/materials. Keep editable sources/provenance; do not reproduce official note artwork. Cash identity and value come from the authoritative catalog, not rendered digits.

Preserve existing terminal item/block IDs and all placed machines; A6-05-01 must explicitly map legacy variants or gate them for brand selection. Do not silently repurpose a color into a different currency or orphan existing terminal settings/ownership.

Prefer code/vector-authored pixel assets and existing project resource conventions. If raster generation becomes useful, follow the image-generation skill then; asset generation belongs to the selected approved implementation package; this preparation step generates no art. Preserve existing eight collectible IDs and assets. Supply item/model/resource/localization keys, source files, license/provenance, small-scale previews and missing-resource checks as the future deliverable. Tier 2 visuals confer no financial permissions.
