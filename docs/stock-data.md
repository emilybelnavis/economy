# Stock catalog provenance

`src/main/resources/data/economy/finance/stocks.json` contains exactly the first 100
companies, in rank order, on the [US500 2026 public-company list](https://us500.com/fortune-500-companies).
The source labels itself a public-company list derived from filings, not the full
public/private Fortune list. The snapshot begins Amazon, Walmart, UnitedHealth
Group, Apple, Alphabet, CVS Health and ends Qualcomm. Company identities and source
ranks remain visible; the original Minecraft-style display names are fictional.

## Historical prices

The baseline is the December 31, 2025 US market close, the final business/trading
day of 2025. Each record links to the exact Yahoo Finance chart request used.
The importer reads `indicators.quote.close`, **not** `indicators.adjclose`; it
validates the exchange-local trading date and the reported USD currency. Source
float serialization noise is rounded to the equity's cent precision. The values
are historical quoted closes rather than dividend-adjusted total-return values.
Yahoo can revise historical data, including split treatment; refreshes should be
reviewed against the committed snapshot rather than assumed identical.

The [Bank of Canada daily USD/CAD observation](https://www.bankofcanada.ca/valet/observations/FXUSDCAD/json?start_date=2025-12-31&end_date=2025-12-31)
is **1.3706 CAD per USD** for December 31, 2025. It is a daily average FX rate,
not an exchange-closing FX rate. CAD minor units are calculated with decimal
arithmetic as `ROUND_HALF_UP(usdClose × 1.3706 × 100)`, once, to whole cents.
Every stock has `currency: USD`, its actual `priceDate`, and its exact price URL;
the catalog root records the FX observation URL and rate.

The committed import has **100 priced records and 0 unavailable records**.
A failed future fetch retains the rank, identity and source URL with null prices,
`status: unavailable`, and an explanatory `reason`. Consumers must disable buying
and selling such records; null is never a zero-price or a fabricated fallback.

## Share-class choices

The catalog uses one US dollar listed common equity per company. Alphabet uses
voting Class A `GOOGL`; Berkshire Hathaway uses accessible Class B `BRK-B` (Yahoo's
notation for BRK.B), rather than Class A. Comcast uses `CMCSA`, Dell uses `DELL`,
Tyson uses `TSN`, and Ingram Micro Holding uses `INGM`. Fannie Mae `FNMA` and
Freddie Mac `FMCC` are OTC common stocks and remain in their source ranks.
Galaxy Digital uses its US-listed `GLXY`; Energy Transfer `ET`, Enterprise Products
Partners `EPD` and Plains GP Holdings `PAGP` are the listed securities corresponding
to those source companies. The explicit complete mapping lives in the importer;
it never guesses tickers from company names or substitutes a parent or competitor.

## Reproduction

From the repository root, with Python 3.9+ and internet access:

```sh
python3 tools/import_stock_prices.py --cache /tmp/economy-stock-prices-20251231
```

Only the Python standard library is required. Omit `--cache` for a fresh fetch;
use a new empty cache directory to retain a new set of raw responses. Existing
cached responses are intentionally reused. The cache stays outside version
control; no copied website or full external dataset is committed. Requests cover
only the target trading day. The source-list mapping is a reviewed fixed snapshot,
so a later change to the website cannot silently change ranks or constituents.

## Runtime denomination and chart

CAD values are the immutable imported baseline. Initial world selection converts market prices
into the selected world currency. Explicit operator world changes rebase prices and current
chart display values atomically while preserving original records and source denominations.
The chart records in-game daily prices, presents at most14days, and carries forward only a
previously recorded value. New worlds have no invented pre-history.
