"""Build the supported device portion of the decoded local IR database."""

import json
import sqlite3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ir-recovery" / "remote-ir.sqlite"
TARGET = ROOT / "app" / "src" / "main" / "assets" / "ir-tv.sqlite"
DEVICE_IDS = ("1", "2", "5", "10", "12")  # TV, STB, IPTV, projector, box
KEYS = (
    "power", "up", "down", "left", "right", "ok", "back", "exit", "home",
    "menu", "vol+", "vol-", "mute", "ch+", "ch-", "input", "tv_av",
    "play", "pause", "stop", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
)


def main() -> None:
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.unlink(missing_ok=True)
    source = sqlite3.connect(SOURCE)
    target = sqlite3.connect(TARGET)
    target.executescript((ROOT / "ir-recovery" / "live_area.sql").read_text(encoding="utf-8"))
    target.executescript(
        """CREATE TABLE remotes (
             code_id TEXT PRIMARY KEY, device_id TEXT NOT NULL, device_name TEXT NOT NULL,
             brand_id TEXT, brand_name TEXT NOT NULL, sp_id TEXT, frequency_hz INTEGER NOT NULL,
             match_rank INTEGER NOT NULL DEFAULT 1000000);
           CREATE TABLE keys (
             code_id TEXT NOT NULL, key_id TEXT NOT NULL, pattern_us_json TEXT NOT NULL,
             PRIMARY KEY (code_id, key_id));
           CREATE INDEX keys_code ON keys(code_id);
           CREATE INDEX remotes_filter ON remotes(device_id, brand_name, match_rank);"""
    )
    key_placeholders = ",".join("?" for _ in KEYS)
    device_placeholders = ",".join("?" for _ in DEVICE_IDS)
    rows = source.execute(
        f"""SELECT w.source_table, w.remote_id, w.device_id, w.device_name,
                   w.brand_id, w.brand_name, r.sp_id, w.frequency_hz,
                   w.key_id, w.pattern_us_json
              FROM waveforms w JOIN remotes r
                ON r.source_table=w.source_table AND r.remote_id=w.remote_id
              WHERE w.device_id IN ({device_placeholders}) AND w.key_id IN ({key_placeholders})
                AND w.frequency_hz BETWEEN 30000 AND 60000""",
        (*DEVICE_IDS, *KEYS),
    )
    count = 0
    for table, remote, device_id, device, brand_id, brand, sp_id, frequency, key, pattern in rows:
        code = f"{table}:{remote}"
        target.execute(
            "INSERT OR IGNORE INTO remotes(code_id,device_id,device_name,brand_id,brand_name,sp_id,frequency_hz) VALUES (?,?,?,?,?,?,?)",
            (code, device_id, device or "其他", brand_id, brand or "未知品牌", sp_id, frequency),
        )
        target.execute("INSERT OR REPLACE INTO keys VALUES (?,?,?)", (code, key, pattern))
        count += 1
    target.commit()
    # The original matching trees provide a useful probe order even for remotes
    # whose full command set is stored in the main code table.
    rank_by_code: dict[str, int] = {}
    available = {code for (code,) in target.execute("SELECT code_id FROM remotes")}
    for tree_id, device_id, brand_id, root_index, payload in source.execute(
        "SELECT tree_id,device_id,brand_id,root_index,nodes_json FROM match_trees "
        f"WHERE device_id IN ({device_placeholders}) AND status='true' ORDER BY tree_id",
        DEVICE_IDS,
    ):
        nodes = {node["index"]: node for node in json.loads(payload)}
        root = nodes.get(root_index)
        if not root:
            continue
        for order, child_index in enumerate(root.get("children_index", [])):
            child = nodes.get(child_index, {})
            for remote_id in child.get("keysetids", []):
                for (table,) in source.execute(
                    "SELECT source_table FROM remotes WHERE remote_id=? AND device_id=? AND brand_id=?",
                    (remote_id, device_id, brand_id),
                ):
                    code = f"{table}:{remote_id}"
                    if code in available:
                        rank_by_code[code] = min(rank_by_code.get(code, 1000000), order)
    target.executemany("UPDATE remotes SET match_rank=? WHERE code_id=?", ((rank, code) for code, rank in rank_by_code.items()))
    target.execute("PRAGMA user_version=4")
    target.commit()
    target.execute("VACUUM")
    assert target.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    assert target.execute("SELECT count(*) FROM remotes").fetchone()[0] > 1000
    assert target.execute("SELECT count(*) FROM keys").fetchone()[0] > 40000
    assert target.execute("SELECT count(*) FROM remotes WHERE match_rank<1000000").fetchone()[0] > 1000
    assert target.execute("SELECT count(*) FROM live_area").fetchone()[0] == 532
    for (payload,) in target.execute("SELECT pattern_us_json FROM keys"):
        pulses = json.loads(payload)
        assert len(pulses) >= 2 and all(isinstance(n, int) and n > 0 for n in pulses)
    print(f"{target.execute('SELECT count(*) FROM remotes').fetchone()[0]} remotes, {count} keys, {TARGET.stat().st_size} bytes")


if __name__ == "__main__":
    main()
