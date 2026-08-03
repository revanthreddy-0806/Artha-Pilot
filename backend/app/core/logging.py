"""Structured logging configuration."""
import logging
import sys
from typing import Any


class LogFormatter(logging.Formatter):
    """Console formatter with component context."""

    def format(self, record: logging.LogRecord) -> str:
        record.component = record.name.split(".")[0] if record.name else "app"
        return super().format(record)


def setup_logging(level: str = "INFO") -> None:
    """Configure root logger once for the whole application."""
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(
        LogFormatter("%(asctime)s | %(levelname)-8s | %(component)-12s | %(message)s")
    )
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level.upper())


def get_logger(name: str) -> logging.Logger:
    return logging.getLogger(name)
