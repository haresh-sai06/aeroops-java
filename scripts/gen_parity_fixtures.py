"""Generate golden predictions from the original Python AeroOps API (aeroops-ml) for Java parity tests.
Usage: PYTHONPATH=../aeroops-ml/src AEROOPS_MODEL_DIR=../aeroops-ml/models python scripts/gen_parity_fixtures.py"""
import json, random, datetime as dt
from aeroops.serving.api import FlightRequest, delay_model, delay_frame
random.seed(42)
model, meta, snap = delay_model()
cats = meta["categories"]
out = []
for i in range(500):
    pick = lambda c: random.choice(cats[c]) if random.random() > 0.03 else "ZZZ"
    r = dict(carrier=pick("carrier"), origin=pick("origin"), dest=pick("dest"),
             flight_date=str(dt.date(2024, 4, 1) + dt.timedelta(days=random.randint(0, 29))),
             dep_hour=random.randint(0, 23), distance=round(random.uniform(80, 2800), 1))
    if random.random() > 0.3:
        r["prev_leg_arr_delay"] = round(random.uniform(-30, 180), 1)
        r["prev_leg_dep_delay"] = round(random.uniform(-15, 180), 1)
        r["leg_of_day"] = random.randint(1, 6)
    x, unknown = delay_frame(FlightRequest(**r), meta, snap)
    out.append({"request": r, "probability": float(model.predict_proba(x)[0, 1]), "unknown": unknown})
json.dump(out, open("src/test/resources/parity_fixtures.json", "w"), indent=1)
print("wrote", len(out))
