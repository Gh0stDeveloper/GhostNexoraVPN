"""Transfer a TCP message through a local DNSTT server."""
import concurrent.futures
from pathlib import Path
import socket
import subprocess
import sys
import tempfile

client, server = sys.argv[1:]
domain = "t.example.test"
message = b"ghostnexora-dnstt-smoke"


def echo(listener):
    connection, _ = listener.accept()
    with connection:
        connection.settimeout(15)
        connection.sendall(connection.recv(1024))


with tempfile.TemporaryDirectory() as keys, socket.socket() as listener:
    private, public = Path(keys) / "private.key", Path(keys) / "public.key"
    subprocess.run(
        [server, "-gen-key", "-privkey-file", private, "-pubkey-file", public],
        check=True, stdout=subprocess.DEVNULL, timeout=8,
    )
    listener.bind(("127.0.0.1", 0))
    listener.settimeout(40)
    listener.listen(1)
    with socket.socket(type=socket.SOCK_DGRAM) as reservation:
        reservation.bind(("127.0.0.1", 0))
        resolver_port = reservation.getsockname()[1]

    dns_endpoint = f"127.0.0.1:{resolver_port}"
    backend_endpoint = f"127.0.0.1:{listener.getsockname()[1]}"
    server_command = [server, "-privkey-file", private, "-udp", dns_endpoint, domain, backend_endpoint]
    client_command = [client, "-pubkey-file", public, "-udp", dns_endpoint, "-utls", "none", domain, "127.0.0.1:0"]
    with subprocess.Popen(server_command) as server_process, subprocess.Popen(
        client_command, stdout=subprocess.PIPE, text=True,
    ) as client_process, concurrent.futures.ThreadPoolExecutor(2) as executor:
        try:
            ready = executor.submit(client_process.stdout.readline).result(timeout=10)
            port = int(ready.removeprefix("GNX_DNSTT_PORT="))
            received = executor.submit(echo, listener)
            with socket.create_connection(("127.0.0.1", port), timeout=32) as connection:
                connection.sendall(message)
                with connection.makefile("rb") as stream:
                    assert stream.read(len(message)) == message
            received.result(timeout=4)
        finally:
            client_process.terminate()
            server_process.terminate()

print("DNSTT transferred TCP payload over the loopback DNS server.")
