# Roadmap

Economy's planned direction, for players who want to see what's coming. These are development goals, with no promised release dates; scope and order may change as the systems are built and playtested.

## Available foundation

The current alpha includes multiple currencies, bank accounts, physical cash, player shops, bank-branded terminals, issued cards, fixed unsecured loans, credit reporting, collections and optional automatic payments. See the [README](README.md) for getting started and the [changelog](CHANGELOG.md) for delivered changes.

## A7 — Village Banking Package

- Give every newly generated vanilla village a bank, with compact branches in small villages, a larger branch and possible second in medium villages, and two or three distinct branches in large villages.
- Guarantee a server-currency institution that supports normal primary accounts; add eligible foreign institutions for variety, including at least one in large villages when available.
- Choose institutions from the existing 23 banks without changing their native currencies. Configure generated branches and supported services automatically.
- Add banker NPCs who explain banking and guide players to supported menus, with branch staffing based on building size. Branches and money persist independently of their employees; terminals work without bankers.
- Add a two-wide, two-high banker desk with a monitor and ledger, and broaden the branch kiosk into a two-wide, two-high service console.

Guaranteed placement needs a deliberate layout/terrain strategy, not just random village-pool entries. Existing explored villages, player-built villages and modded villages need separate compatibility plans. Existing kiosk upgrades must avoid overwriting neighboring construction.

## A8 — Economic simulation

- Extend the world economy with scheduled economic simulation.
- Connect simulation-driven prime rates to banking while preserving the agreed terms of existing fixed-rate loans.

## A9 — Basic item valuation

- Configure base resource values and explicit item overrides.
- Derive reference costs from supported recipes, with clear handling of alternatives, output quantities, cycles and unavailable inputs.
- Provide currency and valuation APIs with versioned provenance explaining each value.

These are reference values. They do not add commodity backing, guaranteed redemption, automatic shop prices or collateral eligibility. Currency creation, finite bank funding and the existing exchange-rate model remain separate systems.

## A10 — Core integration

- Integrate the village banking, simulation and item valuation foundations with clear player explanations and navigation.
- Review screens at more window sizes and GUI scales.
- Expand multiplayer testing across network conditions and external clients.
- Exercise longer combined gameplay flows, from borrowing and repayment through default and Collection Agent encounters.

This carries forward the former A8 player experience and broader playtesting scope. A8 economic simulation carries forward the former A7 scope. These phases are planned, not delivered.

## B2 — Securities and portfolio valuation

- Add securities holdings and current market valuations.
- Include supported investments in net worth and banking qualification, alongside existing assets and debts.

## B4 — Trading

- Add order execution and trading interfaces beyond the current decorative Stock Trading Computer.
- Settle trades, holdings, cash and banking fees together.
- Apply the planned account-tier trading fees: 0.25% for Tier 1 and zero for Tier 2.

Trading depends on the securities and valuation foundations.

## B6 — Expanded lending

- Add variable-rate loans with clear rate-reset rules.
- Add collateral-backed products with ownership checks, valuation and collateral handling on repayment or default.

These products need their supporting rate and asset systems before they can become available.

Playtesting and polish can accompany other work; the phase labels describe scope rather than a release calendar. The B-series features retain their own prerequisites.
