package main

import (
    "context"
    "os"
    "time"

    "github.com/phuslu/fastdns"
)

func init() {
    if len(os.Args) > 1 && os.Args[1] == "--resolver-check" {
        if len(os.Args) != 4 {
            os.Exit(2)
        }
        ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
        defer cancel()
        resolver := fastdns.Client{Addr: os.Args[2], Timeout: 2 * time.Second}
        // Check that the delegated zone can be reached through the configured resolver.
        if _, err := resolver.LookupNS(ctx, os.Args[3]); err != nil {
            os.Exit(1)
        }
        os.Exit(0)
    }

    // Android may kill the VPN process without calling its teardown hook.
    parent := os.Getppid()
    if parent <= 1 {
        os.Exit(1)
    }
    go func() {
        ticker := time.NewTicker(time.Second)
        defer ticker.Stop()
        for range ticker.C {
            if os.Getppid() != parent {
                os.Exit(0)
            }
        }
    }()
}
