"""Repository pattern: persistence access for the User aggregate."""
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.user import User


class UserRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def get_by_email(self, email: str) -> User | None:
        return self._db.scalar(select(User).where(User.email == email.lower()))

    def get_by_id(self, user_id: int) -> User | None:
        return self._db.get(User, user_id)

    def create(self, email: str, full_name: str, password_hash: str) -> User:
        user = User(email=email.lower(), full_name=full_name, password_hash=password_hash)
        self._db.add(user)
        self._db.commit()
        self._db.refresh(user)
        return user
