//! `mortar`: an AI agent in your terminal that builds in your Minecraft world.

use std::env;
use std::process::ExitCode;

const HELP: &str = "\
mortar 1.11.2
Describe it. Mortar builds it in your Minecraft world.

Usage:
  mortar                    start a session for the world you're in
  mortar \"<request>\"        run one turn and exit
  mortar login              set a model key, or pick a local model
  mortar doctor             check the companion, the game version and the model

Options:
  --model <name>            any model id your key allows, or ollama/<model>
  --mode <ask|auto>         ask before placing (default), or place right away
  --horizon                 use Minecraft on Rust through horizon-ctl
  -V, --version             print the version
  -h, --help                print this help
";

fn main() -> ExitCode {
    let args: Vec<String> = env::args().skip(1).collect();
    match args.first().map(String::as_str) {
        Some("-V" | "--version") => {
            println!("mortar {}", env!("CARGO_PKG_VERSION"));
            ExitCode::SUCCESS
        }
        Some("-h" | "--help") | None => {
            print!("{HELP}");
            ExitCode::SUCCESS
        }
        Some(other) => {
            eprintln!("mortar: unknown command `{other}`. Run `mortar --help`.");
            ExitCode::from(2)
        }
    }
}
