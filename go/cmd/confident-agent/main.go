package main

import (
	"fmt"
	"os"

	"github.com/joho/godotenv"

	confidentagent "github.com/confident-ai/confident-agent/go"
)

func main() {
	godotenv.Load()

	if err := confidentagent.Run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
