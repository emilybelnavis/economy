# Banking artwork

A6-05 resources live under `assets/economy/{textures,models}/.../banking/`; the shipped `banking/art_manifest.json` and source `tools/banking-assets/resource_inventory.json` enumerate exact identifiers. [Editable sources, license, generation and integration instructions](../../tools/banking-assets/README.md) cover every resource family.

`BankingArtModels` derives 373 standalone model roots from `InstitutionCatalog` (74 cards, 69 hardware item models and 230 hardware cells). NeoForge's client RegisterAdditional event loads these without item/block registration. Parent traversal covers 442 model nodes. Approved networks and available tiers determine variants; art does not add products. Tier 2 has II/double trim, debit DB/rectangular badge and credit CR/arrow badge. Bank names, native currency and distinct motifs support recognition without color alone. Full names are localized; short original pixel lettering fits the artwork.

ATM/teller geometry and UVs match the original detailed four-cell sources exactly (right/base/upper_right/upper, north-front). Kiosks use original two-cell slender pedestal geometry. Inventory models preserve the appropriate original terminal geometry. All physical bindings, support/drop behavior and migration remain A6-05-01 work. Existing generic machines still use their old resources.

The Collection Agent has a 64×64 skin and `CollectionAgentGeometry.create()` humanoid layer definition, standard bones plus body-attached folio/satchel. A6-11 owns renderer/model-layer/entity registration, animation and behavior. The current package spawns nothing.

No schema, journal, service packets, worker/lifecycle, financial authorization or settlement contract changes. A resource identifier cannot establish an institution relationship, issued card, account, facility or permission. Legacy collectible IDs and resources stay intact.
