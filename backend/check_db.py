import sqlite3

con = sqlite3.connect('chatsummarizer.db')
cur = con.cursor()

print("=== TABLES ===")
cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = cur.fetchall()
print(tables)

for t in tables:
    table_name = t[0]
    print(f"\n=== {table_name} SCHEMA ===")
    cur.execute(f"PRAGMA table_info({table_name})")
    for col in cur.fetchall():
        print(col)

print("\n=== USERS TABLE ROWS (if exists) ===")
try:
    cur.execute("SELECT * FROM users")
    rows = cur.fetchall()
    print(f"Row count: {len(rows)}")
    for r in rows:
        print(r)
except Exception as e:
    print(f"Could not read users table: {e}")

con.close()
