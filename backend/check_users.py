import sqlite3

con = sqlite3.connect('chatsummarizer.db')
cur = con.cursor()
cur.execute("SELECT id, username, password_hash FROM app_user")
rows = cur.fetchall()
print(f"Row count: {len(rows)}")
for r in rows:
    print(r)
con.close()
