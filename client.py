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
    # ---------- LPUSH ----------
    send(sock, "LPUSH", "lpush_list", "c")
    # Expected: (INTEGER, 1)

    send(sock, "LPUSH", "lpush_list", "b", "a")
    # Expected: (INTEGER, 3)

    send(sock, "LRANGE", "lpush_list", "0", "-1")
    # Expected: [a, b, c]

    # Fresh list with multiple
    send(sock, "LPUSH", "lpush2", "x", "y", "z")
    # Expected: (INTEGER, 3)

    send(sock, "LRANGE", "lpush2", "0", "-1")
    # Expected: [z, y, x]   (reversed!)

    # Mix LPUSH and RPUSH
    send(sock, "LPUSH", "mixed", "b")
    send(sock, "LPUSH", "mixed", "a")
    send(sock, "RPUSH", "mixed", "c")
    send(sock, "RPUSH", "mixed", "d")
    # List is now [a, b, c, d]

    send(sock, "LRANGE", "mixed", "0", "-1")
    # Expected: [a, b, c, d]

    # Errors
    send(sock, "LPUSH", "key")          # error (no values)
    send(sock, "LPUSH")                  # error (no args)
    # ---------- LLEN ----------
    send(sock, "RPUSH", "llen_list", "a", "b", "c", "d")
    # Expected: (INTEGER, 4)

    send(sock, "LLEN", "llen_list")
    # Expected: (INTEGER, 4)

    send(sock, "LPUSH", "llen_list", "x")
    # Expected: (INTEGER, 5)

    send(sock, "LLEN", "llen_list")
    # Expected: (INTEGER, 5)

    send(sock, "LLEN", "missing_list")
    # Expected: (INTEGER, 0)

    # Errors
    send(sock, "LLEN")                     # error (no key)
    send(sock, "LLEN", "key1", "key2")     # error (too many args)
    
    # ---------- LPOP ----------
    send(sock, "RPUSH", "lpop_list", "one", "two", "three", "four", "five")
    # Expected: (INTEGER, 5)

    send(sock, "LPOP", "lpop_list")
    # Expected: (BULK, "one")

    send(sock, "LRANGE", "lpop_list", "0", "-1")
    # Expected: ["two", "three", "four", "five"]

    send(sock, "LPOP", "lpop_list")
    # Expected: (BULK, "two")

    send(sock, "LPOP", "lpop_list")
    # Expected: (BULK, "three")

    send(sock, "LLEN", "lpop_list")
    # Expected: (INTEGER, 2)

    send(sock, "LPOP", "lpop_list")
    # Expected: (BULK, "four")

    send(sock, "LPOP", "lpop_list")
    # Expected: (BULK, "five")

    send(sock, "LPOP", "lpop_list")
    # Expected: (NULL, None)          ← empty now

    send(sock, "LLEN", "lpop_list")
    # Expected: (INTEGER, 0)

    # Missing list
    send(sock, "LPOP", "never_existed")
    # Expected: (NULL, None)

    # Errors
    send(sock, "LPOP")
    # Expected: error

    send(sock, "LPOP", "key1", "key2")
    # Expected: error
    # ---------- LPOP with count ----------
    send(sock, "RPUSH", "pop_list", "one", "two", "three", "four", "five")
    # Expected: (INTEGER, 5)

    send(sock, "LPOP", "pop_list", "2")
    # Expected: (ARRAY, [one, two])

    send(sock, "LRANGE", "pop_list", "0", "-1")
    # Expected: [three, four, five]

    send(sock, "LPOP", "pop_list", "1")
    # Expected: (ARRAY, [three])

    send(sock, "LRANGE", "pop_list", "0", "-1")
    # Expected: [four, five]

    # Count > length
    send(sock, "LPOP", "pop_list", "100")
    # Expected: (ARRAY, [four, five])

    send(sock, "LRANGE", "pop_list", "0", "-1")
    # Expected: []   (empty)

    # Missing list with count
    send(sock, "LPOP", "missing_with_count", "5")
    # Expected: (ARRAY, [])

    # Count = 0
    send(sock, "RPUSH", "zero_list", "a", "b")
    send(sock, "LPOP", "zero_list", "0")
    # Expected: (ARRAY, [])
    send(sock, "LRANGE", "zero_list", "0", "-1")
    # Expected: [a, b]   (unchanged)

    # Errors
    send(sock, "LPOP", "list", "-1")
    # Expected: error (negative)

    send(sock, "LPOP", "list", "abc")
    # Expected: error (not integer)

    send(sock, "LPOP")
    # Expected: error (no args)

    send(sock, "LPOP", "k", "2", "extra")
    # Expected: error (too many args)
    
    # ---------- TYPE ----------
    send(sock, "SET", "type_test", "hello")
    # +OK

    send(sock, "TYPE", "type_test")
    # ("SIMPLE", "string")

    send(sock, "TYPE", "missing")
    # ("SIMPLE", "none")

    send(sock, "RPUSH", "type_list", "a", "b")
    # :2

    send(sock, "TYPE", "type_list")
    # ("SIMPLE", "list")

    # Expired key
    send(sock, "SET", "type_exp", "x", "PX", "100")
    send(sock, "TYPE", "type_exp")
    # ("SIMPLE", "string")   — still valid

    time.sleep(0.2)
    send(sock, "TYPE", "type_exp")
    # ("SIMPLE", "none")     — expired

    # Errors
    send(sock, "TYPE")
    # ERROR

    send(sock, "TYPE", "k1", "k2")
    # ERROR
    
    # ---------- XADD / TYPE ----------

    # Create stream
    send(sock, "XADD", "stream_key", "0-1", "foo", "bar")
    # Expected: (BULK, "0-1")

    send(sock, "TYPE", "stream_key")
    # Expected: (SIMPLE, "stream")

    # Multiple entries
    send(sock, "XADD", "stream_key", "0-2", "temp", "36", "humidity", "95")
    # Expected: (BULK, "0-2")

    send(sock, "XADD", "stream_key", "0-3", "x", "y")
    # Expected: (BULK, "0-3")

    send(sock, "TYPE", "stream_key")
    # Expected: (SIMPLE, "stream")

    # Long ID
    send(sock, "XADD", "other_stream", "1526919030474-0", "temperature", "36")
    # Expected: (BULK, "1526919030474-0")

    send(sock, "TYPE", "other_stream")
    # Expected: (SIMPLE, "stream")

    # Errors
    send(sock, "XADD", "stream", "0-4", "field")
    # Expected: error (odd number of fields)

    send(sock, "XADD", "stream", "0-4")
    # Expected: error (too few args)

    send(sock, "XADD", "stream")
    # Expected: error

    send(sock, "XADD")
    # Expected: error
    
    # Setup
    # (ensure stream doesn't exist — flush or use new key)

    # Test 1: Empty stream, time=0
    send(sock, "XADD", "s1", "0-*", "foo", "bar")
    # Expected: (BULK, "0-1")

    # Test 2: Empty stream, time=5
    send(sock, "XADD", "s2", "5-*", "foo", "bar")
    # Expected: (BULK, "5-0")

    # Test 3: Same time, increment seq
    send(sock, "XADD", "s2", "5-*", "bar", "baz")
    # Expected: (BULK, "5-1")

    # Test 4: Again
    send(sock, "XADD", "s2", "5-*", "x", "y")
    # Expected: (BULK, "5-2")

    # Test 5: New bigger time
    send(sock, "XADD", "s2", "10-*", "a", "b")
    # Expected: (BULK, "10-0")

    # Test 6: Invalid — smaller time
    send(sock, "XADD", "s2", "3-*", "c", "d")
    # Expected: error "equal or smaller"

    # Test 7: Same time as last, seq +1
    send(sock, "XADD", "s2", "10-*", "e", "f")
    # Expected: (BULK, "10-1")

    # Test 8: Explicit ID still works
    send(sock, "XADD", "s2", "15-5", "g", "h")
    # Expected: (BULK, "15-5")

    # Test 9: Auto-seq after explicit
    send(sock, "XADD", "s2", "15-*", "i", "j")
    # Expected: (BULK, "15-6")  (last time=15, seq=5, next=6)

    # Test 10: time=0 after 0-1
    send(sock, "XADD", "s1", "0-*", "a", "b")
    # Expected: (BULK, "0-2")  (last 0-1, next 0-2)

    # Test 11: Odd fields still error
    send(sock, "XADD", "s3", "1-*", "field")
    # Expected: error

    # Test 12: Invalid format
    send(sock, "XADD", "s3", "abc-*", "a", "b")
    # Expected: error "Invalid stream ID"

    # Test 1: Empty stream, auto ID
    send(sock, "XADD", "stream1", "*", "foo", "bar")
    # Expected: bulk string like "1735000000000-0"
    # Can't check exact value (time-dependent), but should end with "-0"

    # Test 2: Another auto ID (later)
    time.sleep(0.002)   # 2ms
    send(sock, "XADD", "stream1", "*", "bar", "baz")
    # Expected: bulk string ending with "-0", time larger

    # Test 3: Mixed — auto, then explicit, then auto
    send(sock, "XADD", "stream2", "*", "a", "b")       # auto
    send(sock, "XADD", "stream2", "9999999999999-0", "c", "d")  # explicit future
    send(sock, "XADD", "stream2", "*", "e", "f")       # auto — clock skew
    # Expected: "9999999999999-1"  (because now < 9999999999999)

    # Test 4: Auto after 5-* (previous stage)
    send(sock, "XADD", "stream3", "5-*", "x", "y")     # → "5-0"
    send(sock, "XADD", "stream3", "*", "a", "b")       # → now-0 (now >> 5)
    # Expected: "now-0" (current time)

    # Test 5: Auto after 5-5
    send(sock, "XADD", "stream3", "5-5", "c", "d")     # → "5-5"
    send(sock, "XADD", "stream3", "*", "e", "f")       # → now-0
    # Expected: "now-0"
            
    # Setup
    send(sock, "XADD", "xr_stream", "0-1", "foo", "bar")
    send(sock, "XADD", "xr_stream", "0-2", "bar", "baz")
    send(sock, "XADD", "xr_stream", "0-3", "baz", "foo")

    # Full range
    send(sock, "XRANGE", "xr_stream", "0-1", "0-3")
    # Expected: [[0-1, [foo, bar]], [0-2, [bar, baz]], [0-3, [baz, foo]]]

    # Partial range
    send(sock, "XRANGE", "xr_stream", "0-2", "0-3")
    # Expected: [[0-2, [bar, baz]], [0-3, [baz, foo]]]

    # Exact match
    send(sock, "XRANGE", "xr_stream", "0-2", "0-2")
    # Expected: [[0-2, [bar, baz]]]

    # No match
    send(sock, "XRANGE", "xr_stream", "5-0", "10-0")
    # Expected: []

    # Partial IDs
    send(sock, "XRANGE", "xr_stream", "0", "0")
    # Expected: all 3 entries

    send(sock, "XRANGE", "xr_stream", "0-2", "0")
    # Expected: entries 0-2 and 0-3

    # Missing stream
    send(sock, "XRANGE", "nonexistent", "0-0", "0-0")
    # Expected: []

    # Errors
    send(sock, "XRANGE", "xr_stream")                  # no start/end
    send(sock, "XRANGE", "xr_stream", "0-1")           # no end
    send(sock, "XRANGE", "xr_stream", "abc", "0-1")    # invalid start
    send(sock, "XRANGE", "xr_stream", "0-1", "abc")    # invalid end
        # Setup
    send(sock, "XADD", "xr", "0-1", "foo", "bar")
    send(sock, "XADD", "xr", "0-2", "bar", "baz")
    send(sock, "XADD", "xr", "0-3", "baz", "foo")

    # From start to 0-2
    send(sock, "XRANGE", "xr", "-", "0-2")
    # Expected: [[0-1, ...], [0-2, ...]]

    # From start to end
    send(sock, "XRANGE", "xr", "-", "0-3")
    # Expected: all 3 entries

    # From start to 0-1
    send(sock, "XRANGE", "xr", "-", "0-1")
    # Expected: [[0-1, ...]]

    # From start to missing
    send(sock, "XRANGE", "xr", "-", "0")
    # Expected: all 3 entries (end "0" → (0, MAX))

    # Empty stream
    send(sock, "XRANGE", "missing", "-", "0-5")
    # Expected: []

    # From start, exact single entry
    send(sock, "XRANGE", "xr", "-", "0-1")
    # Expected: [[0-1, ["foo", "bar"]]]
    # Setup
        
    send(sock, "XADD", "xr", "0-1", "foo", "bar")
    send(sock, "XADD", "xr", "0-2", "bar", "baz")
    send(sock, "XADD", "xr", "0-3", "baz", "foo")

    # From 0-2 to end
    send(sock, "XRANGE", "xr", "0-2", "+")
    # Expected: [[0-2, ...], [0-3, ...]]

    # From 0-1 to end
    send(sock, "XRANGE", "xr", "0-1", "+")
    # Expected: all 3 entries

    # From start to end (full stream)
    send(sock, "XRANGE", "xr", "-", "+")
    # Expected: all 3 entries

    # From start to start (single entry via + end but start = end)
    send(sock, "XRANGE", "xr", "0-2", "+")
    # Expected: [[0-2, ...], [0-3, ...]]

    # From after end (no results)
    send(sock, "XRANGE", "xr", "5-0", "+")
    # Expected: []

    # Missing stream
    send(sock, "XRANGE", "missing", "0-0", "+")
    # Expected: []

    # Full stream, missing
    send(sock, "XRANGE", "missing", "-", "+")
    # Expected: []

    # Partial start with + end
    send(sock, "XRANGE", "xr", "0", "+")
    # Expected: all 3 entries (start "0" → (0, 0))
    
    # Setup
    send(sock, "XADD", "xr", "0-1", "foo", "bar")
    send(sock, "XADD", "xr", "0-2", "bar", "baz")
    send(sock, "XADD", "xr", "0-3", "baz", "foo")

    # From beginning
    send(sock, "XREAD", "STREAMS", "xr", "0-0")
    # Expected: [["xr", [["0-1", ...], ["0-2", ...], ["0-3", ...]]]]

    # Exclusive — skip first
    send(sock, "XREAD", "STREAMS", "xr", "0-1")
    # Expected: [["xr", [["0-2", ...], ["0-3", ...]]]]

    # Skip two
    send(sock, "XREAD", "STREAMS", "xr", "0-2")
    # Expected: [["xr", [["0-3", ...]]]]

    # Skip all
    send(sock, "XREAD", "STREAMS", "xr", "0-3")
    # Expected: (nil)  ← null array

    # Way past
    send(sock, "XREAD", "STREAMS", "xr", "999-0")
    # Expected: (nil)

    # Missing stream
    send(sock, "XREAD", "STREAMS", "missing", "0-0")
    # Expected: (nil)

    # Case-insensitive STREAMS keyword
    send(sock, "XREAD", "streams", "xr", "0-0")
    # Expected: works (case-insensitive)

    # Errors
    send(sock, "XREAD")                         # no args
    send(sock, "XREAD", "STREAMS")               # missing key/id
    send(sock, "XREAD", "STREAMS", "xr")         # missing id
    send(sock, "XREAD", "WRONG", "xr", "0-0")    # wrong keyword
    send(sock, "XREAD", "STREAMS", "xr", "abc")  # invalid ID
    
    sock.close()

if __name__ == "__main__":
    main()