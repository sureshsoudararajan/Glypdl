import subprocess
import threading
import gi
gi.require_version('GLib', '2.0')
from gi.repository import GLib

def run_async(args: list[str], callback, error_callback=None, env=None):
    def worker():
        try:
            result = subprocess.run(
                args,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env,
                check=True
            )
            GLib.idle_add(callback, result.stdout)
        except subprocess.CalledProcessError as e:
            if error_callback:
                GLib.idle_add(error_callback, e.stderr)
        except Exception as e:
            if error_callback:
                GLib.idle_add(error_callback, str(e))

    thread = threading.Thread(target=worker, daemon=True)
    thread.start()

def run_and_stream(args: list[str], on_line, on_done, on_error=None, env=None):
    def worker():
        try:
            proc = subprocess.Popen(
                args,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env,
                bufsize=1
            )
            
            if proc.stdout:
                for line in proc.stdout:
                    GLib.idle_add(on_line, line)
            
            proc.wait()
            
            if proc.returncode != 0:
                stderr_output = proc.stderr.read() if proc.stderr else ""
                if on_error:
                    GLib.idle_add(on_error, stderr_output)
            
            GLib.idle_add(on_done)
            
        except Exception as e:
            if on_error:
                GLib.idle_add(on_error, str(e))
                
    thread = threading.Thread(target=worker, daemon=True)
    thread.start()

def kill_process(proc: subprocess.Popen):
    if proc and proc.poll() is None:
        try:
            proc.terminate()
            proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            proc.kill()
        except Exception:
            pass
