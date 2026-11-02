import socket
import time
import threading


def encode_resp(*args):
    out = f"*{len(args)}\r\n".encode()
    for a in args:
        b = str(a).encode()
        out += f"${len(b)}\r\n".encode() + b + b"\r\n"
    return out


def read_resp(sock):
    first = sock.recv(1)
    if not first:
        return None
    prefix = first.decode()
    line = b""
    while not line.endswith(b"\r\n"):
        line += sock.recv(1)
    line = line[:-2]

    if prefix == "+": return line.decode()
    elif prefix == "-": return f"ERR: {line.decode()}"
    elif prefix == ":": return int(line)
    elif prefix == "$":
        n = int(line)
        if n == -1: return None
        data = b""
        while len(data) < n + 2:
            data += sock.recv(n + 2 - len(data))
        return data[:-2].decode()
    elif prefix == "*":
        n = int(line)
        if n == -1: return None
        return [read_resp(sock) for _ in range(n)]


def blpop_client(name, key, timeout, results):
    sock = socket.create_connection(("127.0.0.1", 6379))
    req = encode_resp("BLPOP", key, str(timeout))
    sock.sendall(req)
    print(f"[{name}] BLPOP {key} {timeout} sent, waiting...")
    start = time.time()
    resp = read_resp(sock)
    elapsed = time.time() - start
    print(f"[{name}] Got after {elapsed:.2f}s: {resp}")
    results[name] = resp
    sock.close()


# Clean up
def flush():
    s = socket.create_connection(("127.0.0.1", 6379))
    s.sendall(encode_resp("LPOP", "blist", "100"))
    read_resp(s)
    s.close()


flush()

# ---- Test 1: Single client blocking, then RPUSH ----
print("\n=== Test 1: Single client blocking ===")
results = {}
t1 = threading.Thread(target=blpop_client, args=("C1", "blist", 0, results))
t1.start()
time.sleep(0.5)   # let it block

# RPUSH
s = socket.create_connection(("127.0.0.1", 6379))
s.sendall(encode_resp("RPUSH", "blist", "foo"))
print("[Pusher] RPUSH blist foo →", read_resp(s))
s.close()

t1.join(timeout=2)
print("Result:", results.get("C1"))

# ---- Test 2: Timeout ----
print("\n=== Test 2: Timeout ===")
results = {}
t2 = threading.Thread(target=blpop_client, args=("C2", "blist", 1, results))
t2.start()
t2.join(timeout=3)
print("Result:", results.get("C2"))

# ---- Test 3: FIFO with multiple clients ----
print("\n=== Test 3: FIFO multiple clients ===")
flush()
results = {}

t_c1 = threading.Thread(target=blpop_client, args=("C1", "blist", 0, results))
t_c1.start()
time.sleep(0.2)

t_c2 = threading.Thread(target=blpop_client, args=("C2", "blist", 0, results))
t_c2.start()
time.sleep(0.2)

t_c3 = threading.Thread(target=blpop_client, args=("C3", "blist", 0, results))
t_c3.start()
time.sleep(0.2)

# Push 2 elements — C1 and C2 should get them
s = socket.create_connection(("127.0.0.1", 6379))
s.sendall(encode_resp("RPUSH", "blist", "foo"))
print("[Pusher] RPUSH → ", read_resp(s))
time.sleep(0.2)
s.sendall(encode_resp("RPUSH", "blist", "bar"))
print("[Pusher] RPUSH → ", read_resp(s))
s.close()

time.sleep(1)
print("Results:", results)
print("Expected: C1 got [blist, foo], C2 got [blist, bar], C3 still waiting")