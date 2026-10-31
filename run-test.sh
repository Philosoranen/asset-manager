#!/usr/bin/env bash
#
# Starts a RuneLite test session with the Asset Manager plugin loaded.
# Any session left over from a previous run is closed first.
#
#   ./run-test.sh          close the last session and start a new one
#   ./run-test.sh --stop   just close the last session
#
# Needs a JDK 11-17. If JAVA_HOME doesn't point at one, Temurin 17 is downloaded
# into .jdk/ in this folder the first time.

set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

# Inside a Flatpak sandbox (e.g. the VS Code Flatpak's terminal) there's no X display for
# RuneLite, and sessions started outside the sandbox can't be seen. Re-run on the host.
if [[ -f /.flatpak-info ]] && command -v flatpak-spawn >/dev/null; then
	exec flatpak-spawn --host --watch-bus --directory="$PWD" "$PWD/run-test.sh" "$@"
fi

# Matches the main class as a whole argument, so an editor with the .java file open isn't matched.
CLIENT_PATTERN='[[:space:]]com\.assetmanager\.AssetManagerPluginTest([[:space:]]|$)'
JDK_DIR="$PWD/.jdk/17"
PID_FILE="$PWD/.run/gradle-run.pid"

log() { printf '\033[1;33m[run-test]\033[0m %s\n' "$*"; }

# Waits up to $1 seconds for all PIDs in the rest of the arguments to exit.
wait_for_exit() {
	local timeout=$1; shift
	for ((i = 0; i < timeout * 10; i++)); do
		local alive=0
		for pid in "$@"; do
			kill -0 "$pid" 2>/dev/null && alive=1
		done
		((alive)) || return 0
		sleep 0.1
	done
	return 1
}

# PIDs of running test clients. The client is started by Gradle, so it isn't a child of this
# script; find it by its main class, which also catches sessions started without this script.
find_clients() {
	while read -r pid; do
		# Only java processes, not shells or editors that happen to mention the class
		[[ -n $pid && $(ps -o comm= -p "$pid" 2>/dev/null) == java ]] && echo "$pid"
	done < <(pgrep -u "$(id -u)" -f "$CLIENT_PATTERN" || true)
}

# Visible main RuneLite windows of a client ("RuneLite" or "RuneLite - <name>").
main_windows() {
	command -v xdotool >/dev/null && [[ -n ${DISPLAY:-} ]] || return 0
	local w
	for w in $(xdotool search --onlyvisible --pid "$1" 2>/dev/null); do
		case "$(xdotool getwindowname "$w" 2>/dev/null)" in
			RuneLite | "RuneLite - "*) echo "$w" ;;
		esac
	done
}

# Closes running test clients. RuneLite only writes settings to disk every 5 minutes or when its
# window is closed, so ask the window to close (like clicking X) to save them. The process then
# tends to hang on exit, so it's killed once it has had time to shut down.
close_clients() {
	local pids=() pid w asked=0
	mapfile -t pids < <(find_clients)
	((${#pids[@]} > 0)) || return 1

	log "Closing RuneLite (PID ${pids[*]}) and saving its settings..."
	for pid in "${pids[@]}"; do
		for w in $(main_windows "$pid"); do
			xdotool windowquit "$w" 2>/dev/null && asked=1
		done
	done

	if ((asked)); then
		# RuneLite waits up to ~11s for plugins to finish shutting down.
		if ! wait_for_exit 15 "${pids[@]}"; then
			kill -KILL "${pids[@]}" 2>/dev/null || true
		fi
	else
		log "Couldn't find the RuneLite window, so settings changed in the last 5 minutes may not be saved."
		kill -TERM "${pids[@]}" 2>/dev/null || true
		wait_for_exit 5 "${pids[@]}" || kill -KILL "${pids[@]}" 2>/dev/null || true
	fi
	wait_for_exit 5 "${pids[@]}" || true
	log "RuneLite closed."
}

stop_previous() {
	close_clients || log "No previous RuneLite session running."

	# The previous run of this script. Its Gradle ends on its own once the client is gone;
	# if it's stuck (e.g. still building), stop it and its children.
	if [[ -f $PID_FILE ]]; then
		local old
		old=$(<"$PID_FILE")
		if [[ $old =~ ^[0-9]+$ && $old != "$$" ]] && kill -0 "$old" 2>/dev/null; then
			if ! wait_for_exit 10 "$old"; then
				log "Stopping previous run-test.sh (PID $old)."
				pkill -TERM -P "$old" 2>/dev/null || true
				kill -TERM "$old" 2>/dev/null || true
				wait_for_exit 5 "$old" || true
			fi
		fi
		rm -f "$PID_FILE"
	fi
}

# Prints the major version of the java binary given, e.g. 17.
java_major() {
	"$1" -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print (v[1] == "1" ? v[2] : v[1]); exit }'
}

usable_jdk() {
	local home=$1
	[[ -x $home/bin/java && -x $home/bin/javac ]] || return 1
	local major
	major=$(java_major "$home/bin/java")
	[[ $major =~ ^[0-9]+$ ]] && ((major >= 11 && major <= 17))
}

find_jdk() {
	if [[ -n ${JAVA_HOME:-} ]] && usable_jdk "$JAVA_HOME"; then
		return
	fi
	if usable_jdk "$JDK_DIR"; then
		export JAVA_HOME=$JDK_DIR
		return
	fi

	local arch
	case "$(uname -m)" in
		x86_64) arch=x64 ;;
		aarch64 | arm64) arch=aarch64 ;;
		*) log "Unsupported CPU architecture: $(uname -m)"; exit 1 ;;
	esac

	log "No JDK 11-17 found. Downloading Temurin 17 into .jdk/ (one time, about 190 MB)..."
	mkdir -p "$(dirname "$JDK_DIR")"
	local tmp
	tmp=$(mktemp -d "$(dirname "$JDK_DIR")/download.XXXXXX")
	curl -fL --progress-bar -o "$tmp/jdk.tar.gz" \
		"https://api.adoptium.net/v3/binary/latest/17/ga/linux/$arch/jdk/hotspot/normal/eclipse"
	mkdir -p "$tmp/jdk"
	tar xzf "$tmp/jdk.tar.gz" -C "$tmp/jdk" --strip-components=1
	rm -rf "$JDK_DIR"
	mv "$tmp/jdk" "$JDK_DIR"
	rm -rf "$tmp"
	usable_jdk "$JDK_DIR" || { log "Downloaded JDK doesn't work."; exit 1; }
	export JAVA_HOME=$JDK_DIR
}

stop_previous
if [[ ${1:-} == "--stop" ]]; then
	exit 0
fi

find_jdk
log "Using Java $(java_major "$JAVA_HOME/bin/java") from $JAVA_HOME"

mkdir -p "$(dirname "$PID_FILE")"
echo "$$" > "$PID_FILE"
# Only remove the PID file if it's still ours (a newer run may have replaced it).
trap '[[ -f $PID_FILE && $(<"$PID_FILE") == "$$" ]] && rm -f "$PID_FILE"; true' EXIT

log "Starting RuneLite with Asset Manager. Close the client window or press Ctrl+C to stop."
# --no-daemon: a Gradle daemon started elsewhere (e.g. inside a sandbox, or by an IDE) could
# launch the client with the wrong environment.
# Gradle ignores Ctrl+C so it can't kill the client before its settings are saved; the trap
# below closes the client properly instead, and Gradle then finishes by itself.
( trap '' INT; exec ./gradlew --no-daemon run ) &
gradle_pid=$!

on_interrupt() {
	if ! close_clients; then
		# Still building; nothing to save.
		pkill -TERM -P "$gradle_pid" 2>/dev/null || true
		kill -TERM "$gradle_pid" 2>/dev/null || true
	fi
}
trap on_interrupt INT TERM

# If the window is closed by hand, RuneLite saves its settings but the process then hangs on exit.
# RuneLite runs its shutdown on a thread called "RuneLite Shutdown" and waits up to ~11s for plugins,
# so once that thread has existed for 13 seconds, end the process so Gradle can finish.
shutdown_since=0
while kill -0 "$gradle_pid" 2>/dev/null; do
	sleep 1 & wait $! || true
	client=$(find_clients | head -1 || true)
	if [[ -n $client ]] && grep -qs '^RuneLite Shutdo' /proc/"$client"/task/*/comm; then
		((shutdown_since)) || shutdown_since=$SECONDS
		if ((SECONDS - shutdown_since >= 13)); then
			log "RuneLite saved its settings but didn't exit; ending it."
			kill -KILL "$client" 2>/dev/null || true
			shutdown_since=0
		fi
	fi
done
status=0
wait "$gradle_pid" || status=$?
exit "$status"
