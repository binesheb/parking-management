from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from pathlib import Path
import json, time

DATA = Path("data/state.json")
DATA.parent.mkdir(parents=True, exist_ok=True)
app = FastAPI(title="Parking Management")

def load():
    if not DATA.exists():
        state={"site_name":"Palarivattom","total_capacity":50,"occupied":0,"events":[]}
        save(state)
    return json.loads(DATA.read_text())

def save(state):
    DATA.write_text(json.dumps(state, indent=2))

def status(state):
    return {"site_name":state["site_name"],"total_capacity":state["total_capacity"],"occupied":state["occupied"],"available":state["total_capacity"]-state["occupied"],"status":"full" if state["total_capacity"]-state["occupied"]==0 else "available"}

class Event(BaseModel):
    type: str
    source: str = "controller-01"

@app.get("/api/status")
def get_status(): return status(load())

@app.get("/api/events")
def get_events(): return load()["events"][-50:]

@app.post("/api/event")
def post_event(event: Event):
    s=load(); t=event.type.upper()
    if t=="CAR_IN":
        if s["occupied"]>=s["total_capacity"]: raise HTTPException(409,"Parking is full")
        s["occupied"]+=1
    elif t=="CAR_OUT":
        if s["occupied"]<=0: raise HTTPException(409,"No occupied slots")
        s["occupied"]-=1
    elif t=="SLOT_PLUS": s["total_capacity"]+=1
    elif t=="SLOT_MINUS":
        if s["total_capacity"]<=s["occupied"]: raise HTTPException(409,"Cannot reduce capacity below occupied")
        s["total_capacity"]-=1
    else: raise HTTPException(400,"Unknown event")
    s["events"].append({"type":t,"source":event.source,"timestamp":int(time.time())})
    save(s); return status(s)
