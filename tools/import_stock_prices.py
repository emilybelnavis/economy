#!/usr/bin/env python3
"""Rebuild the fixed 2026 US500 top-100 catalog from historical USD closes."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
import json
from pathlib import Path
import re
import urllib.request
from zoneinfo import ZoneInfo

SOURCE = 'https://us500.com/fortune-500-companies'
DATE = '2025-12-31'
FX_URL = 'https://www.bankofcanada.ca/valet/observations/FXUSDCAD/json?start_date=2025-12-31&end_date=2025-12-31'
# Fixed rank order from the source's 2026 public-company list; do not sort by ticker.
MAPPING = '''Amazon|AMZN|Mineazon
Walmart|WMT|Wallmart
UnitedHealth Group|UNH|UnitedHearts Group
Apple|AAPL|Golden Apple
Alphabet|GOOGL|Alphablock
CVS Health|CVS|CVS Healing
Berkshire Hathaway|BRK-B|Birchshire Hathaway
McKesson|MCK|McPotion
ExxonMobil Holdings|XOM|ExxonMagma Holdings
Cencora|COR|Cencoral
Microsoft|MSFT|Minecrosoft
JPMorgan Chase|JPM|JPMorgan Chest
Costco Wholesale|COST|Cobbleco Wholesale
Cigna Group|CI|Signa Shield Group
Cardinal Health|CAH|Cardinal Hearts
Nvidia|NVDA|Envidia Emeralds
Meta Platforms|META|Meta Portals
Elevance Health|ELV|Elevance Healing
Centene|CNC|Centenether
Bank of America|BAC|Bank of Overworld
Chevron|CVX|Chevredstone
Ford Motor|F|Forge Motor
General Motors|GM|General Minecarts
Citigroup|C|Cityblock Group
Home Depot|HD|Home Deepslate
Fannie Mae|FNMA|Fannie Mine
Kroger|KR|Cropger
Verizon Communications|VZ|Verizone Signals
Phillips 66|PSX|Picklips 66
Marathon Petroleum|MPC|Marathon Lava
StoneX Group|SNEX|StoneXP Group
Freddie Mac|FMCC|Freddie Map
Humana|HUM|Humanapple
AT&T|T|ATandTorch
Goldman Sachs Group|GS|Goldman Stacks Group
Comcast|CMCSA|Cobblecast
Wells Fargo|WFC|Wells Farmland
Morgan Stanley|MS|Morgan Stoneley
Valero Energy|VLO|Valore Energy
Dell Technologies|DELL|Delve Technologies
Target|TGT|Target Block
Tesla|TSLA|Teslava
Walt Disney|DIS|Walt Disnether
Johnson & Johnson|JNJ|Johnson and Potions
PepsiCo|PEP|PepsiCocoa
Boeing|BA|Boating
United Parcel Service|UPS|United Parcel Shulkers
RTX|RTX|RedstoneTX
FedEx|FDX|FedChest
Progressive|PGR|Progressive Protection
Lowe's|LOW|Lode's
Energy Transfer|ET|Energy Hopper
Procter & Gamble|PG|Procter and Gravel
Sysco|SYY|Seedsco
American Express|AXP|Overworld Express
Albertsons|ACI|Albertspawns
Archer Daniels Midland|ADM|Archer Daniels Farmland
MetLife|MET|MobLife
HCA Healthcare|HCA|HCA Heartcare
Lockheed Martin|LMT|Blockheed Martin
Capital One Financial|COF|Capital Ore Financial
Allstate|ALL|Allbiome
Caterpillar|CAT|Cavepillar
International Business Machines|IBM|International Block Machines
Eli Lilly|LLY|Elytra Lily
Merck|MRK|Merckraft
Broadcom|AVGO|Broadcobble
Delta Air Lines|DAL|Delta Elytra Lines
Pfizer|PFE|Pfixer Potions
TD Synnex|SNX|TD Synnether
ConocoPhillips|COP|ConocoPicklips
Galaxy Digital|GLXY|Galaxy Redstone
AbbVie|ABBV|AbbVine
Prudential Financial|PRU|Prudential Emeralds
TJX|TJX|TJXP
Performance Food Group|PFGC|Performance Feast Group
United Airlines Holdings|UAL|United Elytra Holdings
Oracle|ORCL|Oreacle
Cisco Systems|CSCO|Disco Redstone Systems
HP|HPQ|HPick
Charter Communications|CHTR|Charter Beacons
American Airlines Group|AAL|Overworld Elytra Group
Tyson Foods|TSN|Tyson Stews
Intel|INTC|Ingotel
Enterprise Products Partners|EPD|Enterprise Potions Partners
Ingram Micro Holding|INGM|Ingram Minecro Holding
General Dynamics|GD|General Dynamites
Uber Technologies|UBER|Uber Minecarts
Travelers|TRV|Travelers Totems
Bristol-Myers Squibb|BMY|Bristol-Miners Squid
Coca-Cola|KO|Cocoa-Cola
Nike|NKE|Nethike
GE Aerospace|GE|GE Elytraspace
Deere|DE|Deepslate Deere
Plains GP Holdings|PAGP|Plains Grass Partners
Molina Healthcare|MOH|Molina Heartcare
Netflix|NFLX|Netherflix
Thermo Fisher Scientific|TMO|Thermo Fisher Alchemy
Abbott Laboratories|ABT|Abbot Brewing Laboratories
Qualcomm|QCOM|Quartzcomm'''


def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (historical educational catalog)'})
    with urllib.request.urlopen(req, timeout=40) as response:
        return json.load(response)


def price_url(ticker):
    return f'https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?period1=1767139200&period2=1767225600&interval=1d'


def stock(args):
    rank, line, fx, cache = args
    company, ticker, name = line.split('|')
    url = price_url(ticker)
    row = dict(id=re.sub('[^a-z0-9]+', '_', name.lower()).strip('_'), name=name,
               rank=rank, company=company, ticker=ticker, currency='USD', priceDate=DATE,
               usdClose=None, baseCadMinor=None, priceSource=url, status='unavailable', reason=None)
    try:
        file = cache / f'{ticker}.json' if cache else None
        raw = json.loads(file.read_text()) if file and file.exists() else fetch(url)
        if file and not file.exists():
            file.write_text(json.dumps(raw, indent=2) + '\n')
        result = raw['chart']['result'][0]
        if result['meta']['currency'] != 'USD':
            raise ValueError('Source currency is not USD')
        prices = result['indicators']['quote'][0]['close']
        matches = [value for stamp, value in zip(result['timestamp'], prices)
                   if datetime.fromtimestamp(stamp, ZoneInfo('America/New_York')).date().isoformat() == DATE
                   and value is not None]
        if len(matches) != 1:
            raise ValueError('No unique close on requested trading date')
        # Yahoo emits binary float noise; listed US equity closes resolve to cents.
        close = Decimal(str(matches[0])).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
        if close <= 0:
            raise ValueError('Nonpositive close')
        row.update(usdClose=str(close), baseCadMinor=int((close * fx * 100).quantize(Decimal('1'), rounding=ROUND_HALF_UP)), status='priced')
    except Exception as exc:
        row['reason'] = f'Historical close unavailable: {type(exc).__name__}: {exc}'
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, help='Optional raw response cache outside the repository')
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'src/main/resources/data/economy/finance/stocks.json')
    opts = parser.parse_args()
    if opts.cache:
        opts.cache.mkdir(parents=True, exist_ok=True)
    fx_file = opts.cache / 'FXUSDCAD.json' if opts.cache else None
    raw_fx = json.loads(fx_file.read_text()) if fx_file and fx_file.exists() else fetch(FX_URL)
    if fx_file and not fx_file.exists():
        fx_file.write_text(json.dumps(raw_fx, indent=2) + '\n')
    observation = raw_fx['observations'][0]
    assert observation['d'] == DATE
    fx = Decimal(observation['FXUSDCAD']['v'])
    assert fx == Decimal('1.3706'), 'Historical FX observation changed; review before importing'
    lines = MAPPING.splitlines()
    assert len(lines) == 100
    with ThreadPoolExecutor(max_workers=6) as pool:
        rows = list(pool.map(stock, [(rank, line, fx, opts.cache) for rank, line in enumerate(lines, 1)]))
    assert len({row['id'] for row in rows}) == 100
    payload = dict(schema=1, sourceList=SOURCE, sourceListEdition='2026', priceDate=DATE,
                   usdCad=str(fx), fxSource=FX_URL, stocks=rows)
    opts.output.parent.mkdir(parents=True, exist_ok=True)
    opts.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + '\n')
    missing = [row['ticker'] for row in rows if row['status'] != 'priced']
    print(f'Wrote {len(rows)} stocks; {len(missing)} unavailable: {", ".join(missing)}')


if __name__ == '__main__':
    main()
