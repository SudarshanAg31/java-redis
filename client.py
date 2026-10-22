import socket
import sys
import time


def encode_resp(*args):
    """Encode a command as RESP array of bulk strings."""
    out = f"*{len(args)}\r\n".encode()
    for a in args:
        a = str(a)
        b = a.encode('utf-8')
        out += f"${len(b)}\r\n".encode() + b + b"\r\n"
    return out


def decode_resp(sock):
    """Read one RESP response from socket."""
    first = sock.recv(1)
    if not first:
        return ("CLOSED", None)

    prefix = first.decode()
    line = b""
    while not line.endswith(b"\r\n"):
        line += sock.recv(1)
    line = line[:-2]  # strip \r\n

    if prefix == "+":
        return ("SIMPLE", line.decode())
    elif prefix == "-":
        return ("ERROR", line.decode())
    elif prefix == ":":
        return ("INTEGER", int(line))
    elif prefix == "$":
        length = int(line)
        if length == -1:
            return ("NULL", None)
        data = b""
        while len(data) < length + 2:
            data += sock.recv(length + 2 - len(data))
        return ("BULK", data[:-2].decode())
    elif prefix == "*":
        count = int(line)
        if count == -1:
            return ("NULL_ARRAY", None)
        items = [decode_resp(sock) for _ in range(count)]
        return ("ARRAY", items)
    else:
        return ("UNKNOWN", prefix + line.decode())


def send(sock, *args):
    """Send command, print request and response."""
    req = encode_resp(*args)
    print(f">>> {list(args)}")
    print(f"    raw req: {req!r}")
    sock.sendall(req)
    resp = decode_resp(sock)
    print(f"<<< {resp}")
    print()
    return resp


def main():
    host, port = "127.0.0.1", 6379
    if len(sys.argv) > 1:
        port = int(sys.argv[1])

    sock = socket.create_connection((host, port))
    print(f"Connected to {host}:{port}\n")

    # ---------- PING ----------
    send(sock, "PING")

    # ---------- ECHO ----------
    send(sock, "ECHO", "hello")
    send(sock, "ECHO", "hello world")
    send(sock, "ECHO", "hello", "world")   # should error

    # ---------- SET / GET ----------
    send(sock, "SET", "name", "Alice")
    send(sock, "GET", "name")
    send(sock, "GET", "missing")

    # ---------- SET with PX ----------
    send(sock, "SET", "foo", "bar", "PX", "100")
    send(sock, "GET", "foo")
    time.sleep(0.2)
    send(sock, "GET", "foo")   # should be nil after 200ms

    # ---------- SET with EX ----------
    send(sock, "SET", "tmp", "value", "EX", "1")
    send(sock, "GET", "tmp")
    time.sleep(1.5)
    send(sock, "GET", "tmp")   # should be nil

    # ---------- RPUSH single ----------
    send(sock, "RPUSH", "list_key", "foo")
    send(sock, "RPUSH", "list_key", "bar")
    send(sock, "RPUSH", "list_key", "baz")

    # ---------- RPUSH multiple ----------
    send(sock, "RPUSH", "list2", "a", "b", "c")
    send(sock, "RPUSH", "list2", "d", "e")

    # ---------- Errors ----------
    send(sock, "RPUSH", "list_key")           # no value
    send(sock, "RPUSH")                       # no args
    send(sock, "GET")                          # no key
    send(sock, "SET", "k", "v", "PX", "abc")  # invalid integer
    send(sock, "SET", "k", "v", "PX", "-5")   # negative time
    send(sock, "SET", "k", "v", "XX", "100")  # unknown option
    send(sock, "FOOBAR")                       # unknown command
    # ---------- LRANGE tests ----------
    send(sock, "RPUSH", "my_list", "a", "b", "c", "d", "e")   # create

    send(sock, "LRANGE", "my_list", "0", "1")     # → ["a","b"]
    send(sock, "LRANGE", "my_list", "2", "4")     # → ["c","d","e"]
    send(sock, "LRANGE", "my_list", "0", "4")     # → ["a","b","c","d","e"]
    send(sock, "LRANGE", "my_list", "2", "2")     # → ["c"]
    send(sock, "LRANGE", "my_list", "0", "0")     # → ["a"]

    # Edge cases
    send(sock, "LRANGE", "missing", "0", "5")     # → *0 (empty array)
    send(sock, "LRANGE", "my_list", "5", "10")    # → *0 (start OOB)
    send(sock, "LRANGE", "my_list", "10", "15")   # → *0 (start OOB)
    send(sock, "LRANGE", "my_list", "2", "100")   # → ["c","d","e"] (stop clamped)
    send(sock, "LRANGE", "my_list", "3", "2")     # → *0 (start > stop)

    # Error cases
    send(sock, "LRANGE", "my_list")               # → error (args)
    send(sock, "LRANGE", "my_list", "0")          # → error (args)
    send(sock, "LRANGE", "my_list", "abc", "2")   # → error (not integer)
    send(sock, "LRANGE", "my_list", "-1", "2")    # → error (negative)
    send(sock, "RPUSH", "neg_list", "a", "b", "c", "d", "e")

    send(sock, "LRANGE", "neg_list", "-2", "-1")    # → [d, e]
    send(sock, "LRANGE", "neg_list", "0", "-3")     # → [a, b, c]
    send(sock, "LRANGE", "neg_list", "2", "-1")     # → [c, d, e]
    send(sock, "LRANGE", "neg_list", "-5", "-1")    # → [a, b, c, d, e]  (full list)
    send(sock, "LRANGE", "neg_list", "-5", "-5")    # → [a]
    send(sock, "LRANGE", "neg_list", "-1", "-1")    # → [e]

    # Out of range negative
    send(sock, "LRANGE", "neg_list", "-6", "-1")    # → [a, b, c, d, e]  (start clamped to 0)
    send(sock, "LRANGE", "neg_list", "0", "-6")     # → []               (stop still negative → empty)
    send(sock, "LRANGE", "neg_list", "-6", "-6")    # → []               (both -6 → start 0, stop -1 → empty)

    # Mixed
    send(sock, "LRANGE", "neg_list", "-100", "100") # → [a, b, c, d, e]
    send(sock, "LRANGE", "neg_list", "-3", "-2")    # → [c, d]

    sock.close()


if __name__ == "__main__":
    main()