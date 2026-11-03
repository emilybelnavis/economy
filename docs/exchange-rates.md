# Exchange-rate defaults

Snapshot date: 2026-10-30. Rates are CAD per one major unit of foreign currency.
The mod never contacts a rate service during play. Server administrators can edit all rates
in `config/economy/banking.toml`; restart after editing.

| Currency | CAD per major unit |
| --- | ---: |
| CAD | 1 |
| EUR | 1.5582 |
| GBP | 1.8699 |
| AUD | 0.9813 |
| NZD | 0.7865 |
| USD | 1.3960 |
| HKD | 0.1779 |
| JPY | 0.008870 |
| CNY | 0.2098 |
| DKK | 0.20881622293 |

CAD is the reference value 1. All other currencies except DKK use the
[Bank of Canada daily averages](https://www.bankofcanada.ca/valet/observations/group/FX_RATES_DAILY/json?start_date=2026-10-30&end_date=2026-10-30).
DKK is derived from [Danmarks Nationalbank](https://www.nationalbanken.dk/en/what-we-do/stable-prices-monetary-policy-and-the-danish-economy/exchange-rates):
478.89 DKK per 100 CAD, so 1 DKK = 100 / 478.89 CAD, rounded to 11 decimal places.
These sources have different daily observation methods; this is a fixed gameplay snapshot.

For every pair A to B, one major unit of A buys `CAD_rate[A] / CAD_rate[B]` major units of B.
For example, USD 20 deposits CAD 27.92. JPY 1,000 deposits CAD 8.87.
Conversion uses integer minor-unit amounts and BigDecimal rates, rounding down only at the
final account unit. Accounts use 100 minor units per major unit except JPY, which uses 1.
No exchange fee is charged. Withdrawals dispense the account currency without conversion.
