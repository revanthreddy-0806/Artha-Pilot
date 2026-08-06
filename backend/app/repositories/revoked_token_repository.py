"""Repository pattern: persistence access for revoked refresh tokens."""
from datetime import datetime, timezone

from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.models.revoked_token import RevokedToken


class RevokedTokenRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def is_revoked(self, jti: str) -> bool:
        return self._db.get(RevokedToken, jti) is not None

    def revoke(self, jti: str, user_id: int, expires_at: datetime) -> None:
        self._db.add(RevokedToken(jti=jti, user_id=user_id, expires_at=expires_at))
        self._db.commit()

    def rollback(self) -> None:
        self._db.rollback()

    def prune_expired(self) -> int:
        result = self._db.execute(
            delete(RevokedToken).where(RevokedToken.expires_at < datetime.now(timezone.utc))
        )
        self._db.commit()
        return result.rowcount or 0
