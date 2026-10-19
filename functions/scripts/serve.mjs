// Starts the local emulators for playing on a phone: the real project id (so the app's
// google-services.json matches), and the data kept between runs in ../.emulator-data.
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";

const data = "../.emulator-data";
const args = ["firebase", "emulators:start", "--project", "mukhda-f8833", "--export-on-exit", data];
if (existsSync(`${data}/firebase-export-metadata.json`)) args.push("--import", data);
spawn("npx", args, { stdio: "inherit", shell: true }).on("exit", (code) => process.exit(code ?? 0));
