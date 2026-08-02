import importlib.util
import sys
from pathlib import Path
from typing import Callable

from .decorator import _registered


class HandlerError(Exception):
    pass


def load_handler(path: str) -> Callable:
    file = Path(path).expanduser().resolve()
    if not file.is_file():
        raise HandlerError(f"handler file not found: {file}")

    parent = str(file.parent)
    if parent not in sys.path:
        sys.path.insert(0, parent)

    _registered.clear()

    spec = importlib.util.spec_from_file_location("confident_handler_module", file)
    if spec is None or spec.loader is None:
        raise HandlerError(f"could not import handler file: {file}")
    module = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(module)
    except Exception as e:
        raise HandlerError(f"handler file failed to import: {e}") from e

    if len(_registered) == 0:
        raise HandlerError(
            f"no @handler function found in {file.name} — decorate exactly one "
            "function with @handler from confident_agent"
        )
    if len(_registered) > 1:
        names = ", ".join(fn.__name__ for fn in _registered)
        raise HandlerError(
            f"multiple @handler functions found in {file.name} ({names}) — "
            "exactly one is allowed per agent"
        )
    return _registered[0]
