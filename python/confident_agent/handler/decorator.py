from typing import Callable, List

_registered: List[Callable] = []


def handler(fn: Callable) -> Callable:
    _registered.append(fn)
    return fn
