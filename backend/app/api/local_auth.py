from typing import Annotated

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field

from app.api.auth import create_access_token
from app.config import get_settings
from app.database import DbSession
from app.schemas.email import EmailAddress
from app.schemas.user import UserSyncResponse
from app.services.local_auth import authenticate, set_password, verify_password
from app.utils.auth import CurrentUser
from app.utils.rate_limit import rate_limit_by_ip, rate_limit_by_user

router = APIRouter(prefix="/auth", tags=["Local authentication"])
Password = Annotated[str, Field(min_length=12, max_length=128)]


class LoginRequest(BaseModel):
    email: EmailAddress
    password: str = Field(min_length=1, max_length=128)


class LoginResponse(UserSyncResponse):
    token_type: str = "bearer"
    expires_in: int = 604800


class PasswordChange(BaseModel):
    current_password: str = Field(min_length=1, max_length=128)
    new_password: Password


def require_local() -> None:
    if not get_settings().local_auth_enabled:
        raise HTTPException(404, "Local authentication is disabled")


@router.post("/login", response_model=LoginResponse)
async def login(data: LoginRequest, request: Request, db: DbSession):
    require_local()
    await rate_limit_by_ip(request, "local_login", 20, 60)
    user, identity = await authenticate(db, data.email, data.password)
    return LoginResponse(
        id=user.id,
        email=user.email,
        display_name=user.display_name,
        is_new_user=False,
        onboarding_completed=user.onboarding_completed,
        access_token=create_access_token(user.id, identity.id),
    )


@router.post("/password", status_code=204)
async def change_password(data: PasswordChange, db: DbSession, user: CurrentUser):
    require_local()
    await rate_limit_by_user(user.id, "change_password", 5, 60)
    if (
        not await verify_password(data.current_password, user.password_hash)
        or not user.password_hash
    ):
        raise HTTPException(401, "Invalid email or password")
    await set_password(db, user, data.new_password)
